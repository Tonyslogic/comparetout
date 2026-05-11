package com.tfcode.comparetout.ui2

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.SimKPIs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

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

    private val _activeItem = MutableStateFlow<ActiveDashboardItem?>(null)

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
        _activeItem.value = ActiveDashboardItem.DataSource(sysSn, importerType, startDate, endDate)
    }

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
                    Log.d("UI2", "fetched — scenarioName=${scenarioComponents?.scenario?.scenarioName} net=${bestCosting?.net}")
                    DashboardData(scenarioComponents, bestCosting, simKPIs, hasPanelData)
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
    val dataSourceInfo: DashboardDataSourceInfo? = null
)
