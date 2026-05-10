package com.tfcode.comparetout.ui2

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.tfcode.comparetout.model.ToutcRepository
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
        Log.d("UI2", "UI2DashboardViewModel created, repository=$repository")
    }

    private val _activeSimulationId = MutableStateFlow<Long?>(null)

    fun setActiveSimulationId(id: Long) {
        Log.d("UI2", "UI2DashboardViewModel.setActiveSimulationId($id)")
        _activeSimulationId.value = id
    }

    val dashboardData = _activeSimulationId.flatMapLatest { id ->
        Log.d("UI2", "UI2DashboardViewModel: flatMapLatest triggered with id=$id")
        if (id == null) {
            MutableStateFlow(DashboardData(null, null, null))
        } else {
            flow {
                Log.d("UI2", "UI2DashboardViewModel: fetching data for id=$id on IO")
                emit(withContext(Dispatchers.IO) {
                    val scenarioComponents = repository.getScenarioComponentsForScenarioID(id)
                    val bestCosting = repository.getBestCostingForScenario(id)
                    val simKPIs = repository.getSimKPIsForScenario(id)
                    val hasPanelData = repository.checkForMissingPanelData(id)
                    Log.d("UI2", "UI2DashboardViewModel: fetched — scenarioName=${scenarioComponents?.scenario?.scenarioName} net=${bestCosting?.net} hasPanelData=$hasPanelData")
                    DashboardData(scenarioComponents, bestCosting, simKPIs, hasPanelData)
                })
            }
        }
    }.asLiveData()
}

data class DashboardData(
    val scenarioComponents: com.tfcode.comparetout.model.scenario.ScenarioComponents?,
    val bestCosting: com.tfcode.comparetout.model.costings.Costings?,
    val simKPIs: com.tfcode.comparetout.model.scenario.SimKPIs?,
    val hasPanelData: Boolean = false
)
