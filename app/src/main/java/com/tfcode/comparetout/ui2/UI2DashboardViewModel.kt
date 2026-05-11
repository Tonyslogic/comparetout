package com.tfcode.comparetout.ui2

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.SimKPIs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class DataSourcePeriod(val label: String) {
    YESTERDAY("D"), MONTH("M"), YEAR("Y"), ALL("*")
}

data class PeriodTotals(val load: Double, val buy: Double, val feed: Double, val pv: Double)

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

    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val _activeItem = MutableStateFlow<ActiveDashboardItem?>(null)
    private val _selectedPeriod = MutableStateFlow<DataSourcePeriod?>(null)
    private val _periodTotals = MutableStateFlow<PeriodTotals?>(null)

    val selectedPeriod = _selectedPeriod.asLiveData()
    val periodTotals = _periodTotals.asLiveData()

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
        _selectedPeriod.value = null
        _periodTotals.value = null
        _activeItem.value = ActiveDashboardItem.DataSource(sysSn, importerType, startDate, endDate)
    }

    fun setDataSourcePeriod(period: DataSourcePeriod) {
        _selectedPeriod.value = period
        _periodTotals.value = null
        val item = _activeItem.value as? ActiveDashboardItem.DataSource ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val (from, to) = periodDateRange(period, item.startDate, item.endDate)
            val rows = repository.getSumDOY(item.sysSn, from, to)
            _periodTotals.value = PeriodTotals(
                load = rows.sumOf { it.load },
                buy  = rows.sumOf { it.buy },
                feed = rows.sumOf { it.feed },
                pv   = rows.sumOf { it.pv }
            )
        }
    }

    private fun periodDateRange(period: DataSourcePeriod, dataStart: String, dataEnd: String): Pair<String, String> {
        val today = LocalDate.now()
        return when (period) {
            DataSourcePeriod.YESTERDAY -> today.minusDays(1).format(FMT).let { it to it }
            DataSourcePeriod.MONTH     -> today.withDayOfMonth(1).format(FMT) to today.format(FMT)
            DataSourcePeriod.YEAR      -> today.withDayOfYear(1).format(FMT) to today.format(FMT)
            DataSourcePeriod.ALL       -> dataStart to dataEnd
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
                    val bestCosting = repository.getBestCostingForScenario(id)
                    val simKPIs = repository.getSimKPIsForScenario(id)
                    val hasPanelData = repository.checkForMissingPanelData(id)
                    val allCostings = repository.getAllCostingsForScenario(id)
                    val plans = repository.getAllPricePlansNow()
                    val planChargesMap = plans.associate { it.pricePlanIndex to it.standingCharges }
                    val dateRange = repository.getSimDateRanges(id.toString())
                    val simDays = if (dateRange != null) {
                        LocalDate.parse(dateRange.finishDate).toEpochDay() -
                            LocalDate.parse(dateRange.startDate).toEpochDay() + 1
                    } else 365L
                    Log.d("UI2", "fetched — scenarioName=${scenarioComponents?.scenario?.scenarioName} net=${bestCosting?.net} plans=${allCostings.size}")
                    DashboardData(scenarioComponents, bestCosting, simKPIs, hasPanelData,
                        allCostings = allCostings, planStandingCharges = planChargesMap, simDays = simDays)
                })
            }
            is ActiveDashboardItem.DataSource -> flow {
                emit(DashboardData(
                    scenarioComponents = null,
                    bestCosting = null,
                    simKPIs = null,
                    hasPanelData = false,
                    dataSourceInfo = DashboardDataSourceInfo(
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
        ComparisonUIViewModel.Importer.ALPHAESS -> "AlphaESS"
        ComparisonUIViewModel.Importer.ESBNHDF -> "ESBN"
        ComparisonUIViewModel.Importer.HOME_ASSISTANT -> "Home Assistant"
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
