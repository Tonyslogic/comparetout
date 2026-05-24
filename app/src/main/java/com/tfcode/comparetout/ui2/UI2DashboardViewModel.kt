package com.tfcode.comparetout.ui2

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.importers.IntervalRow
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.costings.SubTotals
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.SimKPIs
import com.tfcode.comparetout.util.RateLookup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class DataSourcePeriod(val label: String) {
    YESTERDAY("D"), MONTH("M"), YEAR("Y"), ALL("*")
}

data class PeriodTotals(
    val load: Double, val buy: Double, val feed: Double, val pv: Double,
    val charging: Double = 0.0, val discharging: Double = 0.0
)

data class UsageDistribution(
    val hourly: List<Double>,   // 24 values, % of total import
    val daily: List<Double>,    // 7 values, % (0=Sun…6=Sat)
    val monthly: List<Double>   // 12 values, % (Jan…Dec)
)

data class DataSourceCostingRow(
    val planName: String,
    val pricePlanId: Long,
    val net: Double,    // cents
    val buy: Double,    // cents
    val sell: Double,   // cents
    val fixed: Double,  // € pre-computed: standingCharges × days/365
    val subTotals: SubTotals?
)

/**
 * Headline KPI numbers for the selected period — mirrors the legacy
 * ImportKeyStatsFragment "Self consumption / sufficiency" table.
 */
data class KpiSummary(
    val selfConsumption: Double,    // (PV − Feed) / PV × 100
    val selfSufficiency: Double,    // (PV − Feed) / Load × 100
    val maxSelfSufficiency: Double, // PV / Load × 100
    val pv: Double,                 // kWh
    val feed: Double,               // kWh
    val load: Double                // kWh — for "% covered" context, not shown directly
) {
    companion object {
        val Empty = KpiSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/** One row in the monthly key-stats table — best / worst / average PV for a month. */
data class KpiMonthRow(
    val monthLabel: String,    // "YY-MM"
    val monthNumber: Int,      // 1..12 (for the J/F/M/… filter)
    val pvTotal: Double,
    // The DB renders these as "<value> on <dd>" — keep the full string so the
    // user can see which day of the month was best/worst.
    val best: String,
    val worst: String,
    val average: Double
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UI2DashboardViewModel @Inject constructor(
    private val repository: ToutcRepository,
    favouritePlanStore: FavouritePlanStore
) : ViewModel() {

    /** Plan-id the user has marked as their current supplier plan, or null. */
    val favouritePlanId = favouritePlanStore.id.asLiveData()

    init {
        Log.d("UI2", "UI2DashboardViewModel created")
        viewModelScope.launch(Dispatchers.IO) { favouritePlanStore.ensureLoaded() }
    }

    sealed class ActiveDashboardItem {
        data class Simulation(val id: Long) : ActiveDashboardItem()
        data class DataSource(
            val sysSn: String,
            val importerType: ComparisonUIViewModel.Importer,
            val startDate: String,
            val endDate: String
        ) : ActiveDashboardItem()
    }

    private val FMT     = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val ROWFMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val _activeItem = MutableStateFlow<ActiveDashboardItem?>(null)

    // ── PV System accordion (data source mode) ───────────────────────────────
    private val _pvPeriod    = MutableStateFlow(DataSourcePeriod.ALL)
    private val _pvAnchor    = MutableStateFlow(LocalDate.now())
    private val _pvChartData = MutableStateFlow<List<Pair<String, Double>>?>(null)
    val pvPeriod    = _pvPeriod.asLiveData()
    val pvAnchor    = _pvAnchor.asLiveData()
    val pvChartData = _pvChartData.asLiveData()

    // ── Explore Data accordion ────────────────────────────────────────────────
    private val _explorePeriod = MutableStateFlow(DataSourcePeriod.ALL)
    private val _exploreAnchor = MutableStateFlow(LocalDate.now())
    private val _exploreTotals = MutableStateFlow<PeriodTotals?>(null)
    val explorePeriod = _explorePeriod.asLiveData()
    val exploreAnchor = _exploreAnchor.asLiveData()
    val exploreTotals = _exploreTotals.asLiveData()

    // ── Usage Data accordion ──────────────────────────────────────────────────
    private val _usagePeriod        = MutableStateFlow(DataSourcePeriod.ALL)
    private val _usageAnchor        = MutableStateFlow(LocalDate.now())
    private val _usageTotals        = MutableStateFlow<PeriodTotals?>(null)
    private val _usageDistribution  = MutableStateFlow<UsageDistribution?>(null)
    val usagePeriod        = _usagePeriod.asLiveData()
    val usageAnchor        = _usageAnchor.asLiveData()
    val usageTotals        = _usageTotals.asLiveData()
    val usageDistribution  = _usageDistribution.asLiveData()

    // ── Tariff Plan accordion ─────────────────────────────────────────────────
    private val _tariffPeriod   = MutableStateFlow(DataSourcePeriod.ALL)
    private val _tariffAnchor   = MutableStateFlow(LocalDate.now())
    private val _tariffCostings = MutableStateFlow<List<DataSourceCostingRow>?>(null)
    val tariffPeriod   = _tariffPeriod.asLiveData()
    val tariffAnchor   = _tariffAnchor.asLiveData()
    val tariffCostings = _tariffCostings.asLiveData()

    // ── Scenario Tariff Plan accordion (period-filterable annual costing) ────
    // Default to ALL — the whole simulation year, matching the legacy
    // dashboard's behaviour. The user can dial in a month / year via the picker.
    private val _scenarioTariffPeriod   = MutableStateFlow(DataSourcePeriod.ALL)
    private val _scenarioTariffAnchor   = MutableStateFlow(LocalDate.now())
    private val _scenarioTariffCostings = MutableStateFlow<List<DataSourceCostingRow>?>(null)
    val scenarioTariffPeriod   = _scenarioTariffPeriod.asLiveData()
    val scenarioTariffAnchor   = _scenarioTariffAnchor.asLiveData()
    val scenarioTariffCostings = _scenarioTariffCostings.asLiveData()

    // ── Bounds of the underlying data — used by both KPI and scenario Tariff
    // pickers as their cosmetic min/max for chevron-clamping. Resolved on
    // setActive*, then read by the dashboard.
    private val _dataBounds = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    val dataBounds = _dataBounds.asLiveData()

    // ── KPI accordion ─────────────────────────────────────────────────────────
    // Defaults: MONTH @ current month for the range picker, current month for
    // the J/F/M/A/… filter — matches what the user asked for.
    private val _kpiPeriod      = MutableStateFlow(DataSourcePeriod.MONTH)
    private val _kpiAnchor      = MutableStateFlow(LocalDate.now())
    private val _kpiMonthFilter = MutableStateFlow(LocalDate.now().monthValue)  // 0=all, 1..12
    private val _kpiSummary     = MutableStateFlow<KpiSummary?>(null)
    private val _kpiMonths      = MutableStateFlow<List<KpiMonthRow>?>(null)
    val kpiPeriod      = _kpiPeriod.asLiveData()
    val kpiAnchor      = _kpiAnchor.asLiveData()
    val kpiMonthFilter = _kpiMonthFilter.asLiveData()
    val kpiSummary     = _kpiSummary.asLiveData()
    val kpiMonths      = _kpiMonths.asLiveData()

    fun setActiveSimulationId(id: Long) {
        Log.d("UI2", "UI2DashboardViewModel.setActiveSimulationId($id)")
        _activeItem.value = ActiveDashboardItem.Simulation(id)
        _dataBounds.value = null
        // The actual sim year is read from the DB on the IO thread below; until
        // then null out the pickers' data so the UI shows a spinner rather than
        // a stale anchor.
        _kpiSummary.value = null
        _kpiMonths.value = null
        _scenarioTariffPeriod.value = DataSourcePeriod.ALL
        _scenarioTariffCostings.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val bounds = kpiSourceDateRange()
            val baseYear = bounds?.first?.year ?: 2001
            val now = LocalDate.now()
            // Anchor inside the sim's year — keep the current month so the user
            // lands on a familiar slice of the year, and clamp the day so months
            // shorter than today's day-of-month (e.g. Feb) don't throw.
            val month = now.monthValue
            val day = now.dayOfMonth.coerceAtMost(28)
            val simAnchor = LocalDate.of(baseYear, month, day)
            withContext(Dispatchers.Main) {
                _dataBounds.value = bounds
                _kpiPeriod.value = DataSourcePeriod.MONTH
                _kpiAnchor.value = simAnchor
                _kpiMonthFilter.value = month
                _scenarioTariffAnchor.value = simAnchor
            }
            recomputeKpis()
            recomputeScenarioTariff()
        }
    }

    fun setActiveDataSource(
        sysSn: String,
        importerType: ComparisonUIViewModel.Importer,
        startDate: String,
        endDate: String
    ) {
        Log.d("UI2", "UI2DashboardViewModel.setActiveDataSource($sysSn)")
        _explorePeriod.value  = DataSourcePeriod.ALL
        _exploreAnchor.value  = LocalDate.now()
        _exploreTotals.value  = null
        _usagePeriod.value       = DataSourcePeriod.ALL
        _usageAnchor.value       = LocalDate.now()
        _usageTotals.value       = null
        _usageDistribution.value = null
        _tariffPeriod.value   = DataSourcePeriod.ALL
        _tariffAnchor.value   = LocalDate.now()
        _tariffCostings.value = null
        _pvPeriod.value    = DataSourcePeriod.ALL
        _pvAnchor.value    = LocalDate.now()
        _pvChartData.value = null
        resetKpiDefaults()
        _activeItem.value = ActiveDashboardItem.DataSource(sysSn, importerType, startDate, endDate)
        _dataBounds.value = runCatching {
            LocalDate.parse(startDate, FMT) to LocalDate.parse(endDate, FMT)
        }.getOrNull()
        val item = ActiveDashboardItem.DataSource(sysSn, importerType, startDate, endDate)
        viewModelScope.launch(Dispatchers.IO) {
            val sharedTotals = fetchTotals(DataSourcePeriod.ALL, LocalDate.now(), false, item)
            _exploreTotals.value     = sharedTotals
            _usageTotals.value       = sharedTotals
            _usageDistribution.value = fetchDistribution(DataSourcePeriod.ALL, LocalDate.now(), false, item)
            _tariffCostings.value    = fetchCostings(DataSourcePeriod.ALL, LocalDate.now(), false, item)
            if (importerType != ComparisonUIViewModel.Importer.ESBNHDF) {
                _pvChartData.value = fetchPvChartData(DataSourcePeriod.ALL, LocalDate.now(), false, item)
            } else {
                _pvChartData.value = emptyList()
            }
            recomputeKpis()
        }
    }

    private fun resetKpiDefaults() {
        _kpiPeriod.value = DataSourcePeriod.MONTH
        _kpiAnchor.value = LocalDate.now()
        _kpiMonthFilter.value = LocalDate.now().monthValue
        _kpiSummary.value = null
        _kpiMonths.value = null
    }

    // ── KPI accordion: setters ────────────────────────────────────────────────
    fun setKpiPeriod(period: DataSourcePeriod, anchor: LocalDate) {
        _kpiPeriod.value = period
        _kpiAnchor.value = anchor
        _kpiSummary.value = null
        _kpiMonths.value = null
        viewModelScope.launch(Dispatchers.IO) { recomputeKpis() }
    }

    fun navigateKpi(forward: Boolean) {
        val period = _kpiPeriod.value
        if (period == DataSourcePeriod.ALL) return
        _kpiSummary.value = null
        _kpiMonths.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val (start, end) = kpiSourceDateRange() ?: return@launch
            val anchor = stepAnchor(_kpiAnchor.value, period, forward, start, end)
            withContext(Dispatchers.Main) { _kpiAnchor.value = anchor }
            recomputeKpis()
        }
    }

    fun setKpiMonthFilter(m: Int) {
        _kpiMonthFilter.value = m
    }

    // ── Scenario Tariff Plan setters ─────────────────────────────────────────
    fun setScenarioTariffPeriod(period: DataSourcePeriod, anchor: LocalDate) {
        _scenarioTariffPeriod.value = period
        _scenarioTariffAnchor.value = anchor
        _scenarioTariffCostings.value = null
        viewModelScope.launch(Dispatchers.IO) { recomputeScenarioTariff() }
    }

    fun navigateScenarioTariff(forward: Boolean) {
        val period = _scenarioTariffPeriod.value
        if (period == DataSourcePeriod.ALL) return
        _scenarioTariffCostings.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val (start, end) = kpiSourceDateRange() ?: return@launch
            val anchor = stepAnchor(_scenarioTariffAnchor.value, period, forward, start, end)
            withContext(Dispatchers.Main) { _scenarioTariffAnchor.value = anchor }
            recomputeScenarioTariff()
        }
    }

    // The [anchor] is computed by PeriodSelector via transitionAnchor() so the
    // selection keeps its context across D/M/Y/* changes. [advanced] is supplied
    // by the enclosing tab/panel/accordion (default Basic when there is none).
    fun setExplorePeriod(period: DataSourcePeriod, anchor: LocalDate, advanced: Boolean) {
        _explorePeriod.value = period
        _exploreAnchor.value = anchor
        _exploreTotals.value = null
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        viewModelScope.launch(Dispatchers.IO) { _exploreTotals.value = fetchTotals(period, anchor, advanced, item) }
    }

    fun navigateExplore(forward: Boolean, advanced: Boolean) {
        val period = _explorePeriod.value
        if (period == DataSourcePeriod.ALL) return
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        val anchor = moveAnchor(_exploreAnchor.value, period, forward, item) ?: return
        _exploreAnchor.value = anchor
        _exploreTotals.value = null
        viewModelScope.launch(Dispatchers.IO) { _exploreTotals.value = fetchTotals(period, anchor, advanced, item) }
    }

    fun setUsagePeriod(period: DataSourcePeriod, anchor: LocalDate, advanced: Boolean) {
        _usagePeriod.value       = period
        _usageAnchor.value       = anchor
        _usageTotals.value       = null
        _usageDistribution.value = null
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _usageTotals.value       = fetchTotals(period, anchor, advanced, item)
            _usageDistribution.value = fetchDistribution(period, anchor, advanced, item)
        }
    }

    fun navigateUsage(forward: Boolean, advanced: Boolean) {
        val period = _usagePeriod.value
        if (period == DataSourcePeriod.ALL) return
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        val anchor = moveAnchor(_usageAnchor.value, period, forward, item) ?: return
        _usageAnchor.value       = anchor
        _usageTotals.value       = null
        _usageDistribution.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _usageTotals.value       = fetchTotals(period, anchor, advanced, item)
            _usageDistribution.value = fetchDistribution(period, anchor, advanced, item)
        }
    }

    fun setTariffPeriod(period: DataSourcePeriod, anchor: LocalDate, advanced: Boolean) {
        _tariffPeriod.value = period
        _tariffAnchor.value = anchor
        _tariffCostings.value = null
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        viewModelScope.launch(Dispatchers.IO) { _tariffCostings.value = fetchCostings(period, anchor, advanced, item) }
    }

    fun navigateTariff(forward: Boolean, advanced: Boolean) {
        val period = _tariffPeriod.value
        if (period == DataSourcePeriod.ALL) return
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        val anchor = moveAnchor(_tariffAnchor.value, period, forward, item) ?: return
        _tariffAnchor.value = anchor
        _tariffCostings.value = null
        viewModelScope.launch(Dispatchers.IO) { _tariffCostings.value = fetchCostings(period, anchor, advanced, item) }
    }

    fun setPvPeriod(period: DataSourcePeriod, anchor: LocalDate, advanced: Boolean) {
        _pvPeriod.value    = period
        _pvAnchor.value    = anchor
        _pvChartData.value = null
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        viewModelScope.launch(Dispatchers.IO) { _pvChartData.value = fetchPvChartData(period, anchor, advanced, item) }
    }

    fun navigatePv(forward: Boolean, advanced: Boolean) {
        val period = _pvPeriod.value
        if (period == DataSourcePeriod.ALL) return
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        val anchor = moveAnchor(_pvAnchor.value, period, forward, item) ?: return
        _pvAnchor.value    = anchor
        _pvChartData.value = null
        viewModelScope.launch(Dispatchers.IO) { _pvChartData.value = fetchPvChartData(period, anchor, advanced, item) }
    }

    private suspend fun fetchPvChartData(
        period: DataSourcePeriod,
        anchor: LocalDate,
        advanced: Boolean,
        item: ActiveDashboardItem.DataSource
    ): List<Pair<String, Double>> {
        val (from, to) = anchorDateRange(period, anchor, advanced, item.startDate, item.endDate)
        return when (period) {
            DataSourcePeriod.YESTERDAY -> {
                repository.getSumHour(item.sysSn, from, to)
                    .filter { it.pv > 0 }
                    .mapNotNull { row ->
                        val h = row.interval.toIntOrNull() ?: return@mapNotNull null
                        "%02d:00".format(h) to row.pv
                    }
            }
            DataSourcePeriod.MONTH -> {
                val yearForDoy = LocalDate.parse(from, FMT).year
                repository.getSumDOY(item.sysSn, from, to)
                    .filter { it.pv > 0 }
                    .mapNotNull { row ->
                        val doy = row.interval.toIntOrNull() ?: return@mapNotNull null
                        val dom = runCatching { LocalDate.ofYearDay(yearForDoy, doy).dayOfMonth }
                            .getOrNull() ?: return@mapNotNull null
                        dom.toString() to row.pv
                    }
            }
            DataSourcePeriod.YEAR -> {
                val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                repository.getSumMonth(item.sysSn, from, to)
                    .filter { it.pv > 0 }
                    .mapNotNull { row ->
                        val s = row.interval ?: return@mapNotNull null
                        if (s.length != 6) return@mapNotNull null
                        val month = s.drop(4).toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
                        monthNames[month - 1] to row.pv
                    }
            }
            DataSourcePeriod.ALL ->
                buildPvMonthly(repository.getSumMonth(item.sysSn, from, to))
        }
    }

    private fun moveAnchor(
        current: LocalDate,
        period: DataSourcePeriod,
        forward: Boolean,
        item: ActiveDashboardItem.DataSource
    ): LocalDate? {
        if (period == DataSourcePeriod.ALL) return null
        return stepAnchor(
            current, period, forward,
            LocalDate.parse(item.startDate, FMT),
            LocalDate.parse(item.endDate, FMT)
        )
    }

    private suspend fun fetchTotals(
        period: DataSourcePeriod,
        anchor: LocalDate,
        advanced: Boolean,
        item: ActiveDashboardItem.DataSource
    ): PeriodTotals {
        val (from, to) = anchorDateRange(period, anchor, advanced, item.startDate, item.endDate)
        val rows = repository.getSumDOY(item.sysSn, from, to)
        return PeriodTotals(
            load        = rows.sumOf { it.load },
            buy         = rows.sumOf { it.buy },
            feed        = rows.sumOf { it.feed },
            pv          = rows.sumOf { it.pv },
            charging    = rows.sumOf { it.batCharge },
            discharging = rows.sumOf { it.batDischarge }
        )
    }

    private suspend fun fetchCostings(
        period: DataSourcePeriod,
        anchor: LocalDate,
        advanced: Boolean,
        item: ActiveDashboardItem.DataSource
    ): List<DataSourceCostingRow> {
        val (from, to) = anchorDateRange(period, anchor, advanced, item.startDate, item.endDate)
        val days = LocalDate.parse(to, FMT).toEpochDay() - LocalDate.parse(from, FMT).toEpochDay() + 1
        return computeDataSourceCostings(item.sysSn, from, to, days)
    }

    private suspend fun fetchDistribution(
        period: DataSourcePeriod,
        anchor: LocalDate,
        advanced: Boolean,
        item: ActiveDashboardItem.DataSource
    ): UsageDistribution? {
        val (from, to) = anchorDateRange(period, anchor, advanced, item.startDate, item.endDate)
        val rows = repository.getSelectedAlphaESSData(item.sysSn, from, to)
        if (rows.isEmpty()) return null
        val hourly   = DoubleArray(24)
        val dow      = DoubleArray(7)
        val monthly  = DoubleArray(12)
        rows.forEach { row ->
            val ldt = LocalDateTime.parse(row.dateTime, ROWFMT)
            hourly[ldt.hour] += row.buy
            val d = ldt.dayOfWeek.value.let { if (it == 7) 0 else it }
            dow[d] += row.buy
            monthly[ldt.monthValue - 1] += row.buy
        }
        fun normalize(arr: DoubleArray): List<Double> {
            val total = arr.sum()
            return if (total <= 0.0) arr.toList() else arr.map { it / total * 100.0 }
        }
        return UsageDistribution(normalize(hourly), normalize(dow), normalize(monthly))
    }

    // Delegates to the shared periodDateRange() in PeriodSelector.kt so the
    // fetch range and the selector's own label can never drift apart.
    private fun anchorDateRange(
        period: DataSourcePeriod,
        anchor: LocalDate,
        advanced: Boolean,
        dataStart: String,
        dataEnd: String
    ): Pair<String, String> {
        val (from, to) = periodDateRange(
            period, anchor, advanced,
            LocalDate.parse(dataStart, FMT), LocalDate.parse(dataEnd, FMT))
        return from.format(FMT) to to.format(FMT)
    }

    private fun computeDataSourceCostings(
        sysSn: String,
        from: String,
        to: String,
        days: Long
    ): List<DataSourceCostingRow> {
        val hourlyData = repository.getSelectedAlphaESSData(sysSn, from, to)
        val plans      = repository.getAllPricePlansNow()

        return plans.map { plan ->
            val dayRates  = repository.getAllDayRatesForPricePlanID(plan.pricePlanIndex)
            val lookup    = RateLookup(plan, dayRates)
            lookup.setStartDOY(LocalDate.parse(from, FMT).dayOfYear)

            val subTotals = SubTotals()
            var buy = 0.0; var sell = 0.0

            hourlyData.forEach { row ->
                val ldt   = LocalDateTime.parse(row.dateTime, ROWFMT)
                val doy   = ldt.dayOfYear
                val mod   = ldt.hour * 60 + ldt.minute
                val dow   = ldt.dayOfWeek.value.let { if (it == 7) 0 else it } // 7=Sun→0
                val price = lookup.getRate(doy, mod, dow, row.buy)
                buy  += price * row.buy
                sell += plan.feed * row.feed
                subTotals.addToPrice(price, row.buy)
            }

            val fixed = plan.standingCharges * (days / 365.0)
            val net   = (buy - sell) + (fixed * 100.0)
            DataSourceCostingRow(
                planName   = "${plan.supplier}:${plan.planName}",
                pricePlanId = plan.pricePlanIndex,
                net        = net,
                buy        = buy,
                sell       = sell,
                fixed      = fixed,
                subTotals  = subTotals
            )
        }.sortedBy { it.net }
    }

    private fun buildPvMonthly(rows: List<IntervalRow>): List<Pair<String, Double>> {
        val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return rows
            .filter { it.pv > 0 }
            .mapNotNull { row ->
                val s = row.interval ?: return@mapNotNull null
                if (s.length != 6) return@mapNotNull null
                val year  = s.take(4).toIntOrNull() ?: return@mapNotNull null
                val month = s.drop(4).toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
                "${monthNames[month - 1]} ${year.toString().drop(2)}" to row.pv
            }
    }

    val panelPVSummary = repository.getPanelDataSummary()

    val dashboardData = _activeItem.flatMapLatest { item ->
        when (item) {
            is ActiveDashboardItem.Simulation -> flow {
                val id = item.id
                Log.d("UI2", "UI2DashboardViewModel: fetching simulation data for id=$id")
                emit(withContext(Dispatchers.IO) {
                    val scenarioComponents = repository.getScenarioComponentsForScenarioID(id)
                    val bestCosting        = repository.getBestCostingForScenario(id)
                    val simKPIs            = repository.getSimKPIsForScenario(id)
                    val hasPanelData       = repository.checkForMissingPanelData(id)
                    val allCostings        = repository.getAllCostingsForScenario(id)
                    val plans              = repository.getAllPricePlansNow()
                    val planChargesMap     = plans.associate { it.pricePlanIndex to it.standingCharges }
                    val dateRange          = repository.getSimDateRanges(id.toString())
                    val simDays = runCatching {
                        LocalDate.parse(dateRange!!.finishDate).toEpochDay() -
                            LocalDate.parse(dateRange.startDate).toEpochDay() + 1
                    }.getOrDefault(365L)
                    Log.d("UI2", "fetched — scenarioName=${scenarioComponents?.scenario?.scenarioName} net=${bestCosting?.net} plans=${allCostings.size}")
                    DashboardData(scenarioComponents, bestCosting, simKPIs, hasPanelData,
                        allCostings = allCostings, planStandingCharges = planChargesMap, simDays = simDays)
                })
            }
            is ActiveDashboardItem.DataSource -> flow {
                emit(DashboardData(
                    scenarioComponents = null,
                    bestCosting        = null,
                    simKPIs            = null,
                    hasPanelData       = false,
                    dataSourceInfo     = DashboardDataSourceInfo(
                        item.sysSn, item.importerType, item.startDate, item.endDate
                    )
                ))
            }
            null -> MutableStateFlow(DashboardData(null, null, null))
        }
    }.asLiveData()

    // ── KPI computation ───────────────────────────────────────────────────────

    /**
     * Resolve the underlying data's full [start..end] date span for the active
     * item — needed both to clamp the anchor and to bound the period picker.
     */
    private suspend fun kpiSourceDateRange(): Pair<LocalDate, LocalDate>? {
        val item = _activeItem.value ?: return null
        return when (item) {
            is ActiveDashboardItem.DataSource -> withContext(Dispatchers.IO) {
                LocalDate.parse(item.startDate, FMT) to LocalDate.parse(item.endDate, FMT)
            }
            is ActiveDashboardItem.Simulation -> withContext(Dispatchers.IO) {
                val r = repository.getSimDateRanges(item.id.toString()) ?: return@withContext null
                LocalDate.parse(r.startDate, FMT) to LocalDate.parse(r.finishDate, FMT)
            }
        }
    }

    /**
     * Recompute the KPI summary + monthly table for the current period / anchor.
     * Caller must already be on the IO dispatcher.
     */
    private suspend fun recomputeKpis() {
        val item = _activeItem.value ?: return
        val bounds = kpiSourceDateRange() ?: run {
            _kpiSummary.value = KpiSummary.Empty
            _kpiMonths.value = emptyList()
            return
        }
        val (from, to) = periodDateRange(
            _kpiPeriod.value, _kpiAnchor.value, advanced = false, bounds.first, bounds.second
        )
        val fromStr = from.format(FMT)
        val toStr = to.format(FMT)

        when (item) {
            is ActiveDashboardItem.DataSource -> {
                if (item.importerType == ComparisonUIViewModel.Importer.ESBNHDF) {
                    // ESBN has no PV / load — KPIs would be nonsense.
                    _kpiSummary.value = null
                    _kpiMonths.value = null
                    return
                }
                val k = repository.getKPIs(fromStr, toStr, item.sysSn)
                _kpiSummary.value = if (k == null) KpiSummary.Empty else KpiSummary(
                    selfConsumption    = (k.selfConsumption    ?: 0.0).coerceFiniteOr0(),
                    selfSufficiency    = (k.selfSufficiency    ?: 0.0).coerceFiniteOr0(),
                    maxSelfSufficiency = (k.maxSelfSufficiency ?: 0.0).coerceFiniteOr0(),
                    pv                 = k.pv   ?: 0.0,
                    feed               = k.feed ?: 0.0,
                    load               = 0.0
                )
                // Whole-source key stats: same query the legacy import fragment runs.
                val stats = when (item.importerType) {
                    ComparisonUIViewModel.Importer.ALPHAESS        ->
                        repository.getKeyStats(item.startDate, item.endDate, item.sysSn)
                    ComparisonUIViewModel.Importer.HOME_ASSISTANT  ->
                        repository.getHAKeyStats(item.startDate, item.endDate, item.sysSn)
                    else -> emptyList()
                }
                _kpiMonths.value = stats.orEmpty().mapNotNull { row ->
                    val label = row.month ?: return@mapNotNull null   // "YY-MM"
                    val mNum = label.substringAfter('-', "").toIntOrNull() ?: return@mapNotNull null
                    KpiMonthRow(
                        monthLabel = label,
                        monthNumber = mNum,
                        pvTotal = row.total.parseLeadingDouble(),
                        best    = row.best.orEmpty(),     // "<value> on <dd>" from DB
                        worst   = row.worst.orEmpty(),    // "<value> on <dd>" from DB
                        average = row.average.parseLeadingDouble()
                    )
                }
            }
            is ActiveDashboardItem.Simulation -> {
                // Sim data is daily — aggregate per-day rows ourselves so the same
                // KPIs work whether the user picked Day / Month / Year / All.
                val rows = repository.getSimSumDOY(item.id.toString(), fromStr, toStr)
                val pv = rows.sumOf { it.pv }
                val feed = rows.sumOf { it.feed }
                val load = rows.sumOf { it.load }
                _kpiSummary.value = KpiSummary(
                    selfConsumption    = if (pv > 0) ((pv - feed) / pv) * 100 else 0.0,
                    selfSufficiency    = if (load > 0) ((load - rows.sumOf { it.buy }) / load) * 100 else 0.0,
                    maxSelfSufficiency = if (load > 0) (pv / load) * 100 else 0.0,
                    pv = pv, feed = feed, load = load
                )
                // The monthly table is uncoupled from the range picker — always
                // build it from the full simulation span so the J/F/M/A/… filter
                // can show every month the sim covers, not just the period slice.
                val fullRows = repository.getSimSumDOY(
                    item.id.toString(),
                    bounds.first.format(FMT),
                    bounds.second.format(FMT)
                )
                _kpiMonths.value = buildMonthRowsFromDoy(fullRows, bounds.first.year)
            }
        }
    }

    /** Group day-of-year rows by calendar month → KpiMonthRow (best/worst/avg). */
    private fun buildMonthRowsFromDoy(
        rows: List<IntervalRow>, baseYear: Int
    ): List<KpiMonthRow> {
        // The interval string is the day-of-year for sumDOY. Roll back to a real
        // date in [baseYear] so we can format a "YY-MM" label, and remember which
        // day of the month produced the best/worst value.
        data class Sample(val day: Int, val pv: Double)
        val byMonth = mutableMapOf<Int, MutableList<Sample>>()
        for (r in rows) {
            val doy = r.interval.toIntOrNull() ?: continue
            val d = runCatching { LocalDate.ofYearDay(baseYear, doy) }.getOrNull() ?: continue
            byMonth.getOrPut(d.monthValue) { mutableListOf() }.add(Sample(d.dayOfMonth, r.pv))
        }
        val yy = "%02d".format(baseYear % 100)
        return byMonth.toSortedMap().map { (month, samples) ->
            val bestSample  = samples.maxByOrNull { it.pv }
            val worstSample = samples.minByOrNull { it.pv }
            val totalPv = samples.sumOf { it.pv }
            KpiMonthRow(
                monthLabel = "$yy-${"%02d".format(month)}",
                monthNumber = month,
                pvTotal = totalPv,
                best    = bestSample?.let { "%.2f on %02d".format(it.pv, it.day) } ?: "—",
                worst   = worstSample?.let { "%.2f on %02d".format(it.pv, it.day) } ?: "—",
                average = if (samples.isEmpty()) 0.0 else totalPv / samples.size
            )
        }
    }

    /**
     * Recompute the scenario Tariff Plan costings for the current period / anchor.
     * Aggregates the simulation's per-hour buy/feed totals across the chosen
     * window and applies each price plan's RateLookup at a representative day
     * inside the period. That's close enough for a dashboard widget and is
     * orders of magnitude cheaper than re-running the costing worker.
     */
    private suspend fun recomputeScenarioTariff() = withContext(Dispatchers.IO) {
        val item = _activeItem.value as? ActiveDashboardItem.Simulation ?: run {
            _scenarioTariffCostings.value = null; return@withContext
        }
        val bounds = kpiSourceDateRange() ?: run {
            _scenarioTariffCostings.value = emptyList(); return@withContext
        }
        val (from, to) = periodDateRange(
            _scenarioTariffPeriod.value, _scenarioTariffAnchor.value, advanced = false,
            bounds.first, bounds.second
        )
        val fromStr = from.format(FMT)
        val toStr   = to.format(FMT)
        val days = (to.toEpochDay() - from.toEpochDay() + 1).coerceAtLeast(1)
        val midDay = from.plusDays(days / 2)

        val hourRows = repository.getSimSumHour(item.id.toString(), fromStr, toStr)
        val plans    = repository.getAllPricePlansNow().orEmpty()

        val rows = plans.map { plan ->
            val dayRates  = repository.getAllDayRatesForPricePlanID(plan.pricePlanIndex)
            val lookup    = RateLookup(plan, dayRates)
            lookup.setStartDOY(midDay.dayOfYear)
            val subTotals = SubTotals()
            var buy = 0.0
            var sell = 0.0
            hourRows.forEach { row ->
                val h = row.interval.toIntOrNull() ?: return@forEach
                val mod = h * 60
                val dow = midDay.dayOfWeek.value.let { if (it == 7) 0 else it }
                val rate = lookup.getRate(midDay.dayOfYear, mod, dow, row.buy)
                buy  += rate * row.buy
                sell += plan.feed * row.feed
                subTotals.addToPrice(rate, row.buy)
            }
            val fixed = plan.standingCharges * (days / 365.0)
            DataSourceCostingRow(
                planName    = "${plan.supplier} · ${plan.planName}",
                pricePlanId = plan.pricePlanIndex,
                net         = (buy - sell) / 100.0 + fixed,
                buy         = buy / 100.0,
                sell        = sell / 100.0,
                fixed       = fixed,
                subTotals   = subTotals
            )
        }.sortedBy { it.net }
        _scenarioTariffCostings.value = rows
    }

    private fun Double.coerceFiniteOr0(): Double = if (this.isFinite()) this else 0.0
    private fun String?.parseLeadingDouble(): Double {
        if (this == null) return 0.0
        // legacy "value on dd" → take the leading numeric portion
        val s = this.takeWhile { it.isDigit() || it == '.' || it == '-' }
        return s.toDoubleOrNull() ?: 0.0
    }
}

data class DashboardDataSourceInfo(
    val sysSn: String,
    val importerType: ComparisonUIViewModel.Importer,
    val startDate: String,
    val endDate: String
) {
    val displayTypeName: String get() = when (importerType) {
        ComparisonUIViewModel.Importer.ALPHAESS        -> "AlphaESS"
        ComparisonUIViewModel.Importer.ESBNHDF         -> "ESBN"
        ComparisonUIViewModel.Importer.HOME_ASSISTANT  -> "Home Assistant"
        else -> importerType.name
    }
}

data class DashboardData(
    val scenarioComponents: ScenarioComponents?,
    val bestCosting: Costings?,
    val simKPIs: SimKPIs?,
    val hasPanelData: Boolean = false,
    val dataSourceInfo: DashboardDataSourceInfo? = null,
    val allCostings: List<Costings> = emptyList(),
    val planStandingCharges: Map<Long, Double> = emptyMap(),
    val simDays: Long = 365L
)
