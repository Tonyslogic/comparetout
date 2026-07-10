package com.tfcode.comparetout.ui2

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.importers.IntervalRow
import com.tfcode.comparetout.model.scenario.ScenarioBarChartData
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.ScenarioLineGraphData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class UI2GraphsViewModel @Inject constructor(
    private val repository: ToutcRepository
) : ViewModel() {

    enum class DisplayScale(@StringRes val fullLabelRes: Int) {
        HOUR(R.string.ui2_wiz_hour),
        DOY(R.string.ui2_cmp_day),
        WEEK(R.string.ui2_week),
        MNTH(R.string.ui2_cmp_month),
        YEAR(R.string.ui2_cmp_year)
    }

    enum class StepSize(@StringRes val fullLabelRes: Int) {
        DAY(R.string.ui2_cmp_day),
        WEEK(R.string.ui2_week),
        MONTH(R.string.ui2_cmp_month),
        YEAR(R.string.ui2_cmp_year)
    }

    enum class Calculation(@StringRes val labelRes: Int) {
        SUM(R.string.ui2_graphs_calc_sum), AVG(R.string.ui2_dash_avg)
    }

    enum class GraphType(@StringRes val labelRes: Int) {
        BAR(R.string.ui2_chart_bar), LINE(R.string.ui2_chart_line), AREA(R.string.ui2_chart_area),
        PIE(R.string.ui2_chart_pie), TABLE(R.string.ui2_chart_table), SANKEY(R.string.ui2_chart_sankey)
    }

    enum class FilterSeries(@StringRes val labelRes: Int) {
        LOAD(R.string.ui2_graphs_load), FEED(R.string.ui2_graphs_export),
        BUY(R.string.ui2_series_import), PV(R.string.ui2_graphs_solar),
        PV2BAT(R.string.ui2_series_pv2bat), PV2LOAD(R.string.ui2_series_pv2load),
        BAT2LOAD(R.string.ui2_series_bat2load), GRID2BAT(R.string.ui2_series_grid2bat),
        EV_SCHEDULE(R.string.ui2_series_ev_sched), EV_DIVERT(R.string.ui2_series_ev_divert),
        // Measured device draws (zero on simulation rows): EV_ACTUAL from the
        // AlphaESS v2 transform or an HA-classified EV charger; HW/HP_ACTUAL from
        // HA-classified hot-water / heat-pump devices (v14 columns).
        EV_ACTUAL(R.string.ui2_series_ev_actual),
        HW_ACTUAL(R.string.ui2_series_hw_actual), HP_ACTUAL(R.string.ui2_series_hp_actual),
        HW_SCHEDULE(R.string.ui2_series_hw_sched), HW_DIVERT(R.string.ui2_series_hw_divert),
        BAT2GRID(R.string.ui2_series_bat2grid), BAT_CHARGE(R.string.ui2_series_bat_chg),
        BAT_DISCHARGE(R.string.ui2_series_bat_dis),
        HEAT_PUMP(R.string.ui2_series_hp_load), HEAT_PUMP_BACKUP(R.string.ui2_series_hp_backup),
        HEAT_PUMP_HEAT(R.string.ui2_series_hp_heat),
        HEAT_PUMP_COP(R.string.ui2_graphs_hp_cop), HEAT_PUMP_TEMP(R.string.ui2_graphs_outdoor_temp),
        HEAT_PUMP_WIND(R.string.ui2_graphs_wind_ms)
    }

    data class GraphState(
        val scenarioId: Long = 0L,
        val scenarioName: String = "",
        val from: String = "",
        val to: String = "",
        val dataStartDate: String = "",
        val dataEndDate: String = "",
        val displayScale: DisplayScale = DisplayScale.DOY,
        val stepSize: StepSize = StepSize.MONTH,
        val calculation: Calculation = Calculation.SUM,
        val graphType: GraphType = GraphType.BAR,
        val activeFilters: Set<FilterSeries> = CORE_FILTERS,
        val components: ScenarioComponents? = null,
        val intervalData: List<IntervalRow> = emptyList(),
        val singleDayBarData: List<ScenarioBarChartData> = emptyList(),
        val lineData: List<ScenarioLineGraphData> = emptyList(),
        val isLoading: Boolean = false,
        // Data source mode — non-null when showing importer data instead of a simulation
        val importerType: ComparisonUIViewModel.Importer? = null,
        val dataSysSn: String = ""
    ) {
        val isDataSourceMode: Boolean get() =
            importerType != null && importerType != ComparisonUIViewModel.Importer.SIMULATION

        val isSingleDay: Boolean get() = from.isNotEmpty() && from == to
        val hasBattery: Boolean get() =
            components?.scenario?.isHasBatteries == true || components?.batteries?.isNotEmpty() == true
        val hasHW: Boolean get() =
            components?.scenario?.isHasHWSystem == true || components?.hwSystem != null
        val hasHeatPump: Boolean get() =
            components?.scenario?.isHasHeatPump == true || components?.heatPumps?.isNotEmpty() == true
        // True when the loaded interval data contains actual battery charge/discharge rows
        val hasBatteryData: Boolean get() =
            isDataSourceMode && intervalData.any { it.batCharge > 0.0 || it.batDischarge > 0.0 }
        // True when the v2 AlphaESS transform has populated actual EV-charger usage.
        // Gates the EV chip in the data-source mode filter group below.
        val hasEvActualData: Boolean get() =
            isDataSourceMode && intervalData.any { it.evActual > 0.0 }
        // HA device slices (v14) — gate the HW/HP actual chips the same way.
        val hasHwActualData: Boolean get() =
            isDataSourceMode && intervalData.any { it.hwActual > 0.0 }
        val hasHpActualData: Boolean get() =
            isDataSourceMode && intervalData.any { it.hpActual > 0.0 }
        // Line fab only applies to simulation mode
        val showLineFab: Boolean get() = !isDataSourceMode && isSingleDay && (hasBattery || hasHW || hasHeatPump)

        val availableFilters: Set<FilterSeries>
            get() {
                if (isDataSourceMode) {
                    return when (importerType) {
                        // Meter-only sources: import/export series only.
                        ComparisonUIViewModel.Importer.ESBNHDF,
                        ComparisonUIViewModel.Importer.OCTOPUS -> ESBN_FILTERS
                        else -> {
                            val set = CORE_FILTERS.toMutableSet()
                            if (hasBatteryData) {
                                if (importerType == ComparisonUIViewModel.Importer.HOME_ASSISTANT
                                    || importerType == ComparisonUIViewModel.Importer.SOLIS) {
                                    // HA and Solis store only the signed battery charge — no
                                    // pv2bat/bat2load/... flow decomposition to offer.
                                    set.add(FilterSeries.BAT_CHARGE)
                                    set.add(FilterSeries.BAT_DISCHARGE)
                                } else {
                                    set.addAll(BATTERY_FILTERS)
                                }
                            }
                            if (hasEvActualData) set.add(FilterSeries.EV_ACTUAL)
                            if (hasHwActualData) set.add(FilterSeries.HW_ACTUAL)
                            if (hasHpActualData) set.add(FilterSeries.HP_ACTUAL)
                            set
                        }
                    }
                }
                val sc = components ?: return CORE_FILTERS
                val set = CORE_FILTERS.toMutableSet()
                val batteryPresent = sc.scenario?.isHasBatteries == true || sc.batteries?.isNotEmpty() == true
                val evPresent = sc.scenario?.isHasEVCharges == true || sc.scenario?.isHasEVDivert == true ||
                        sc.evCharges?.isNotEmpty() == true || sc.evDiverts?.isNotEmpty() == true
                val hwPresent = sc.scenario?.isHasHWSystem == true || sc.hwSystem != null
                val heatPumpPresent = sc.scenario?.isHasHeatPump == true || sc.heatPumps?.isNotEmpty() == true
                if (batteryPresent) set.addAll(BATTERY_FILTERS)
                if (evPresent) set.addAll(EV_FILTERS)
                if (hwPresent) set.addAll(HW_FILTERS)
                if (heatPumpPresent) set.addAll(HEAT_PUMP_FILTERS)
                return set
            }
    }

    companion object {
        val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val CORE_FILTERS = setOf(
            FilterSeries.LOAD, FilterSeries.FEED, FilterSeries.BUY, FilterSeries.PV
        )
        val ESBN_FILTERS = setOf(FilterSeries.FEED, FilterSeries.BUY)
        val BATTERY_FILTERS = setOf(
            FilterSeries.PV2BAT, FilterSeries.PV2LOAD, FilterSeries.BAT2LOAD,
            FilterSeries.GRID2BAT, FilterSeries.BAT2GRID,
            FilterSeries.BAT_CHARGE, FilterSeries.BAT_DISCHARGE
        )
        val EV_FILTERS = setOf(FilterSeries.EV_SCHEDULE, FilterSeries.EV_DIVERT, FilterSeries.EV_ACTUAL)
        val HW_FILTERS = setOf(FilterSeries.HW_SCHEDULE, FilterSeries.HW_DIVERT)
        val HEAT_PUMP_FILTERS = setOf(
            FilterSeries.HEAT_PUMP, FilterSeries.HEAT_PUMP_BACKUP, FilterSeries.HEAT_PUMP_HEAT,
            FilterSeries.HEAT_PUMP_COP, FilterSeries.HEAT_PUMP_TEMP, FilterSeries.HEAT_PUMP_WIND)
    }

    private val _state = MutableStateFlow(GraphState())
    val state: StateFlow<GraphState> = _state.asStateFlow()

    fun initialize(scenarioId: Long) {
        if (_state.value.scenarioId == scenarioId && _state.value.components != null) return
        Log.d("UI2Graphs", "initialize($scenarioId)")
        _state.update { it.copy(scenarioId = scenarioId, isLoading = true, importerType = null, dataSysSn = "",
            graphType = GraphType.SANKEY, activeFilters = CORE_FILTERS) }
        viewModelScope.launch(Dispatchers.IO) {
            val components = repository.getScenarioComponentsForScenarioID(scenarioId)
            val name = components?.scenario?.scenarioName ?: ""
            val dateRange = repository.getSimDateRanges(scenarioId.toString())
            val startDate = dateRange?.startDate ?: ""
            val endDate = dateRange?.finishDate ?: ""
            val today = LocalDate.now()
            val dataEnd = if (endDate.isNotEmpty()) {
                try { LocalDate.parse(endDate, FMT).coerceAtMost(today) } catch (e: Exception) { today }
            } else today
            val dataStart = if (startDate.isNotEmpty()) {
                try { LocalDate.parse(startDate, FMT) } catch (e: Exception) { dataEnd.minusMonths(1) }
            } else dataEnd.minusMonths(1)
            // Default to the full simulation range (the whole 2001 grid) rather than just the last month, so
            // Explore opens on the full year by default — the user can narrow with the step controls. The
            // DOY display scale aggregates this to ~365 daily bars. Mirrors the data-source path below.
            val from = dataStart.format(FMT)
            val to = dataEnd.format(FMT)
            _state.update {
                it.copy(
                    components = components, scenarioName = name,
                    dataStartDate = startDate, dataEndDate = endDate,
                    from = from, to = to
                )
            }
            fetchData()
        }
    }

    fun initializeDataSource(
        sysSn: String,
        importerType: ComparisonUIViewModel.Importer,
        startDate: String,
        endDate: String
    ) {
        // Re-initialise when the source OR its available date range changes —
        // a fetch that extends the data must pull the graphs onto the new
        // window rather than short-circuiting on a same-SN check.
        if (_state.value.dataSysSn == sysSn && _state.value.importerType == importerType &&
            _state.value.dataStartDate == startDate && _state.value.dataEndDate == endDate &&
            !_state.value.isLoading) return
        Log.d("UI2Graphs", "initializeDataSource($sysSn, $importerType)")
        val initFilters = if (importerType == ComparisonUIViewModel.Importer.ESBNHDF ||
            importerType == ComparisonUIViewModel.Importer.OCTOPUS) ESBN_FILTERS else CORE_FILTERS
        _state.update { it.copy(dataSysSn = sysSn, importerType = importerType, scenarioId = 0L,
            scenarioName = sysSn, components = null, isLoading = true,
            displayScale = DisplayScale.HOUR, activeFilters = initFilters) }
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val dataEnd = if (endDate.isNotEmpty()) {
                try { LocalDate.parse(endDate, FMT).coerceAtMost(today) } catch (e: Exception) { today }
            } else today
            val dataStart = if (startDate.isNotEmpty()) {
                try { LocalDate.parse(startDate, FMT) } catch (e: Exception) { dataEnd.minusMonths(1) }
            } else dataEnd.minusMonths(1)
            // Use the full available range for data sources
            val from = dataStart.format(FMT)
            val to = dataEnd.format(FMT)
            _state.update { it.copy(dataStartDate = startDate, dataEndDate = endDate, from = from, to = to) }
            fetchData()
        }
    }

    fun stepBack() {
        val s = _state.value
        val fromDate = LocalDate.parse(s.from, FMT)
        val toDate = LocalDate.parse(s.to, FMT)
        val (newFrom, newTo) = when (s.stepSize) {
            StepSize.DAY   -> Pair(fromDate.minusDays(1), toDate.minusDays(1))
            StepSize.WEEK  -> Pair(fromDate.minusWeeks(1), toDate.minusWeeks(1))
            StepSize.MONTH -> monthStep(fromDate, toDate, -1)
            StepSize.YEAR  -> Pair(fromDate.minusYears(1), toDate.minusYears(1))
        }
        _state.update { it.copy(from = newFrom.format(FMT), to = newTo.format(FMT)) }
        viewModelScope.launch(Dispatchers.IO) { fetchData() }
    }

    fun stepForward() {
        val s = _state.value
        val fromDate = LocalDate.parse(s.from, FMT)
        val toDate = LocalDate.parse(s.to, FMT)
        val (newFrom, newTo) = when (s.stepSize) {
            StepSize.DAY   -> Pair(fromDate.plusDays(1), toDate.plusDays(1))
            StepSize.WEEK  -> Pair(fromDate.plusWeeks(1), toDate.plusWeeks(1))
            StepSize.MONTH -> monthStep(fromDate, toDate, 1)
            StepSize.YEAR  -> Pair(fromDate.plusYears(1), toDate.plusYears(1))
        }
        _state.update { it.copy(from = newFrom.format(FMT), to = newTo.format(FMT)) }
        viewModelScope.launch(Dispatchers.IO) { fetchData() }
    }

    /**
     * Step a MONTH-sized window by [months]. When the current selection already covers a whole calendar month
     * (1st → last day of the same month) the result snaps to the next/previous whole month, so a varying month
     * length is honoured: Feb 1–28 → Mar 1–31 → Apr 1–30. Any other (partial) selection is just shifted by a
     * month, preserving the existing behaviour.
     */
    private fun monthStep(from: LocalDate, to: LocalDate, months: Long): Pair<LocalDate, LocalDate> {
        val wholeMonth = from.dayOfMonth == 1 && to.dayOfMonth == to.lengthOfMonth() &&
            from.year == to.year && from.monthValue == to.monthValue
        return if (wholeMonth) {
            val nf = from.plusMonths(months)
            nf to nf.withDayOfMonth(nf.lengthOfMonth())
        } else {
            Pair(from.plusMonths(months), to.plusMonths(months))
        }
    }

    fun setDateRange(from: String, to: String) {
        _state.update { it.copy(from = from, to = to) }
        viewModelScope.launch(Dispatchers.IO) { fetchData() }
    }

    fun setDisplayScale(scale: DisplayScale) {
        _state.update { it.copy(displayScale = scale) }
        viewModelScope.launch(Dispatchers.IO) { fetchData() }
    }

    fun setStepSize(size: StepSize) {
        _state.update { it.copy(stepSize = size) }
    }

    fun setCalculation(calc: Calculation) {
        _state.update { it.copy(calculation = calc) }
        viewModelScope.launch(Dispatchers.IO) { fetchData() }
    }

    fun setGraphType(type: GraphType) {
        _state.update { it.copy(graphType = type) }
    }

    fun toggleFilter(series: FilterSeries) {
        _state.update { s ->
            val current = s.activeFilters.toMutableSet()
            if (series in current) current.remove(series) else current.add(series)
            s.copy(activeFilters = current)
        }
    }

    private suspend fun fetchData() = withContext(Dispatchers.IO) {
        val s = _state.value
        val from = s.from
        val to = s.to
        if (from.isEmpty() || to.isEmpty()) return@withContext

        _state.update { it.copy(isLoading = true) }

        if (s.isDataSourceMode) {
            val sysSn = s.dataSysSn
            Log.d("UI2Graphs", "fetchData (DS): sysSn=$sysSn from=$from to=$to scale=${s.displayScale} calc=${s.calculation}")
            val rows: List<IntervalRow> = when (s.calculation) {
                Calculation.SUM -> when (s.displayScale) {
                    DisplayScale.HOUR  -> repository.getSumHour(sysSn, from, to)
                    DisplayScale.DOY   -> repository.getSumDOY(sysSn, from, to)
                    DisplayScale.WEEK  -> repository.getSumDOW(sysSn, from, to)
                    DisplayScale.MNTH  -> repository.getSumMonth(sysSn, from, to)
                    DisplayScale.YEAR  -> repository.getSumYear(sysSn, from, to)
                }
                Calculation.AVG -> when (s.displayScale) {
                    DisplayScale.HOUR  -> repository.getAvgHour(sysSn, from, to)
                    DisplayScale.DOY   -> repository.getAvgDOY(sysSn, from, to)
                    DisplayScale.WEEK  -> repository.getAvgDOW(sysSn, from, to)
                    DisplayScale.MNTH  -> repository.getAvgMonth(sysSn, from, to)
                    DisplayScale.YEAR  -> repository.getAvgYear(sysSn, from, to)
                }
            }
            Log.d("UI2Graphs", "fetchData (DS): got ${rows.size} rows")
            _state.update { it.copy(intervalData = rows, singleDayBarData = emptyList(), lineData = emptyList(), isLoading = false) }
            return@withContext
        }

        // Simulation mode
        val id = s.scenarioId
        if (id == 0L) return@withContext
        Log.d("UI2Graphs", "fetchData: id=$id from=$from to=$to scale=${s.displayScale} calc=${s.calculation}")

        val dayOfYear = runCatching { LocalDate.parse(from, FMT).dayOfYear }.getOrElse { 1 }

        if (s.isSingleDay && s.displayScale == DisplayScale.HOUR) {
            val barData  = repository.getBarData(id, dayOfYear)
            val lineData = repository.getLineData(id, dayOfYear)
            _state.update { it.copy(singleDayBarData = barData, lineData = lineData, intervalData = emptyList(), isLoading = false) }
        } else {
            val idStr = id.toString()
            val rows: List<IntervalRow> = when (s.calculation) {
                Calculation.SUM -> when (s.displayScale) {
                    DisplayScale.HOUR  -> repository.getSimSumHour(idStr, from, to)
                    DisplayScale.DOY   -> repository.getSimSumDOY(idStr, from, to)
                    DisplayScale.WEEK  -> repository.getSimSumDOW(idStr, from, to)
                    DisplayScale.MNTH  -> repository.getSimSumMonth(idStr, from, to)
                    DisplayScale.YEAR  -> repository.getSimSumYear(idStr, from, to)
                }
                Calculation.AVG -> when (s.displayScale) {
                    DisplayScale.HOUR  -> repository.getSimAvgHour(idStr, from, to)
                    DisplayScale.DOY   -> repository.getSimAvgDOY(idStr, from, to)
                    DisplayScale.WEEK  -> repository.getSimAvgDOW(idStr, from, to)
                    DisplayScale.MNTH  -> repository.getSimAvgMonth(idStr, from, to)
                    DisplayScale.YEAR  -> repository.getSimAvgYear(idStr, from, to)
                }
            }
            val lineData = if (s.isSingleDay) repository.getLineData(id, dayOfYear) else emptyList()
            Log.d("UI2Graphs", "fetchData: got ${rows.size} rows, lineData=${lineData.size}")
            _state.update { it.copy(intervalData = rows, singleDayBarData = emptyList(), lineData = lineData, isLoading = false) }
        }
    }
}
