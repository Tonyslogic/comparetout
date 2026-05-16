package com.tfcode.comparetout.ui2

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.model.ToutcRepository
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

data class PeriodTotals(val load: Double, val buy: Double, val feed: Double, val pv: Double)

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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UI2DashboardViewModel @Inject constructor(
    private val repository: ToutcRepository
) : ViewModel() {

    init {
        Log.d("UI2", "UI2DashboardViewModel created")
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

    fun setActiveSimulationId(id: Long) {
        Log.d("UI2", "UI2DashboardViewModel.setActiveSimulationId($id)")
        _activeItem.value = ActiveDashboardItem.Simulation(id)
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
        _activeItem.value = ActiveDashboardItem.DataSource(sysSn, importerType, startDate, endDate)
        val item = ActiveDashboardItem.DataSource(sysSn, importerType, startDate, endDate)
        viewModelScope.launch(Dispatchers.IO) {
            val sharedTotals = fetchTotals(DataSourcePeriod.ALL, LocalDate.now(), item)
            _exploreTotals.value     = sharedTotals
            _usageTotals.value       = sharedTotals
            _usageDistribution.value = fetchDistribution(DataSourcePeriod.ALL, LocalDate.now(), item)
            _tariffCostings.value    = fetchCostings(DataSourcePeriod.ALL, LocalDate.now(), item)
        }
    }

    fun setExplorePeriod(period: DataSourcePeriod) {
        val anchor = LocalDate.now()
        _explorePeriod.value = period
        _exploreAnchor.value = anchor
        _exploreTotals.value = null
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        viewModelScope.launch(Dispatchers.IO) { _exploreTotals.value = fetchTotals(period, anchor, item) }
    }

    fun navigateExplore(forward: Boolean) {
        val period = _explorePeriod.value
        if (period == DataSourcePeriod.ALL) return
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        val anchor = moveAnchor(_exploreAnchor.value, period, forward, item) ?: return
        _exploreAnchor.value = anchor
        _exploreTotals.value = null
        viewModelScope.launch(Dispatchers.IO) { _exploreTotals.value = fetchTotals(period, anchor, item) }
    }

    fun setUsagePeriod(period: DataSourcePeriod) {
        val anchor = LocalDate.now()
        _usagePeriod.value       = period
        _usageAnchor.value       = anchor
        _usageTotals.value       = null
        _usageDistribution.value = null
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _usageTotals.value       = fetchTotals(period, anchor, item)
            _usageDistribution.value = fetchDistribution(period, anchor, item)
        }
    }

    fun navigateUsage(forward: Boolean) {
        val period = _usagePeriod.value
        if (period == DataSourcePeriod.ALL) return
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        val anchor = moveAnchor(_usageAnchor.value, period, forward, item) ?: return
        _usageAnchor.value       = anchor
        _usageTotals.value       = null
        _usageDistribution.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _usageTotals.value       = fetchTotals(period, anchor, item)
            _usageDistribution.value = fetchDistribution(period, anchor, item)
        }
    }

    fun setTariffPeriod(period: DataSourcePeriod) {
        val anchor = LocalDate.now()
        _tariffPeriod.value = period
        _tariffAnchor.value = anchor
        _tariffCostings.value = null
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        viewModelScope.launch(Dispatchers.IO) { _tariffCostings.value = fetchCostings(period, anchor, item) }
    }

    fun navigateTariff(forward: Boolean) {
        val period = _tariffPeriod.value
        if (period == DataSourcePeriod.ALL) return
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        val anchor = moveAnchor(_tariffAnchor.value, period, forward, item) ?: return
        _tariffAnchor.value = anchor
        _tariffCostings.value = null
        viewModelScope.launch(Dispatchers.IO) { _tariffCostings.value = fetchCostings(period, anchor, item) }
    }

    private fun moveAnchor(
        current: LocalDate,
        period: DataSourcePeriod,
        forward: Boolean,
        item: ActiveDashboardItem.DataSource
    ): LocalDate? {
        val step = if (forward) 1L else -1L
        val newAnchor = when (period) {
            DataSourcePeriod.YESTERDAY -> current.plusDays(step)
            DataSourcePeriod.MONTH     -> current.plusMonths(step)
            DataSourcePeriod.YEAR      -> current.plusYears(step)
            DataSourcePeriod.ALL       -> return null
        }
        return newAnchor.coerceIn(
            LocalDate.parse(item.startDate, FMT),
            LocalDate.parse(item.endDate, FMT)
        )
    }

    private suspend fun fetchTotals(
        period: DataSourcePeriod,
        anchor: LocalDate,
        item: ActiveDashboardItem.DataSource
    ): PeriodTotals {
        val (from, to) = anchorDateRange(period, anchor, item.startDate, item.endDate)
        val rows = repository.getSumDOY(item.sysSn, from, to)
        return PeriodTotals(
            load = rows.sumOf { it.load },
            buy  = rows.sumOf { it.buy },
            feed = rows.sumOf { it.feed },
            pv   = rows.sumOf { it.pv }
        )
    }

    private suspend fun fetchCostings(
        period: DataSourcePeriod,
        anchor: LocalDate,
        item: ActiveDashboardItem.DataSource
    ): List<DataSourceCostingRow> {
        val (from, to) = anchorDateRange(period, anchor, item.startDate, item.endDate)
        val days = LocalDate.parse(to, FMT).toEpochDay() - LocalDate.parse(from, FMT).toEpochDay() + 1
        return computeDataSourceCostings(item.sysSn, from, to, days)
    }

    private suspend fun fetchDistribution(
        period: DataSourcePeriod,
        anchor: LocalDate,
        item: ActiveDashboardItem.DataSource
    ): UsageDistribution? {
        val (from, to) = anchorDateRange(period, anchor, item.startDate, item.endDate)
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

    private fun anchorDateRange(
        period: DataSourcePeriod,
        anchor: LocalDate,
        dataStart: String,
        dataEnd: String
    ): Pair<String, String> = when (period) {
        DataSourcePeriod.YESTERDAY -> anchor.format(FMT).let { it to it }
        DataSourcePeriod.MONTH     -> {
            val start = anchor.withDayOfMonth(1)
            start.format(FMT) to start.plusMonths(1).minusDays(1).format(FMT)
        }
        DataSourcePeriod.YEAR      ->
            LocalDate.of(anchor.year, 1, 1).format(FMT) to
            LocalDate.of(anchor.year, 12, 31).format(FMT)
        DataSourcePeriod.ALL       -> dataStart to dataEnd
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
