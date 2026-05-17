package com.tfcode.comparetout.ui2

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.importers.InverterDateRange
import com.tfcode.comparetout.model.scenario.Scenario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UI2SimulationsViewModel @Inject constructor(
    private val repository: ToutcRepository
) : ViewModel() {

    sealed class SimListItem {
        data class Simulation(
            val scenario: Scenario,
            val bestCostPerYear: Double? = null,
            val dataSourceName: String? = null
        ) : SimListItem()

        data class DataSource(
            val sysSn: String,
            val importerType: ComparisonUIViewModel.Importer,
            val startDate: String,
            val finishDate: String
        ) : SimListItem() {
            val displayTypeName: String get() = when (importerType) {
                ComparisonUIViewModel.Importer.ALPHAESS -> "AlphaESS"
                ComparisonUIViewModel.Importer.ESBNHDF -> "ESBN"
                ComparisonUIViewModel.Importer.HOME_ASSISTANT -> "Home Assistant"
                else -> importerType.name
            }
        }
    }

    private val _scenarios    = MutableStateFlow<List<Scenario>>(emptyList())
    private val _enrichments  = MutableStateFlow<Map<Long, Pair<Double?, String?>>>(emptyMap())
    private val _alphaSources = MutableStateFlow<List<InverterDateRange>>(emptyList())
    private val _esbnSources  = MutableStateFlow<List<InverterDateRange>>(emptyList())
    private val _haSources    = MutableStateFlow<List<InverterDateRange>>(emptyList())

    val items: StateFlow<List<SimListItem>> = combine(
        _scenarios, _enrichments, _alphaSources, _esbnSources, _haSources
    ) { scenarios, enrichments, alpha, esbn, ha ->
        val simItems = scenarios.map { scenario ->
            val (cost, dsName) = enrichments[scenario.scenarioIndex] ?: Pair(null, null)
            SimListItem.Simulation(scenario, cost, dsName)
        }
        // Deduplicate data sources by sysSn — alpha takes priority, then esbn, then ha.
        // getESBNLiveDateRanges and getHALiveDateRanges query the same underlying table so
        // every AlphaESS sysSn would otherwise appear three times.
        // HA always stores sysSn = "HomeAssistant" (hardcoded in HACatchupWorker), so we
        // detect it by name to avoid misclassifying it as ESBN.
        val seen = mutableSetOf<String>()
        val dsItems = buildList<SimListItem.DataSource> {
            alpha.forEach { if (seen.add(it.sysSn)) add(SimListItem.DataSource(it.sysSn, ComparisonUIViewModel.Importer.ALPHAESS,        it.startDate, it.finishDate)) }
            esbn.forEach  { r ->
                val type = if (r.sysSn == "HomeAssistant") ComparisonUIViewModel.Importer.HOME_ASSISTANT
                           else ComparisonUIViewModel.Importer.ESBNHDF
                if (seen.add(r.sysSn)) add(SimListItem.DataSource(r.sysSn, type, r.startDate, r.finishDate))
            }
            ha.forEach    { if (seen.add(it.sysSn)) add(SimListItem.DataSource(it.sysSn, ComparisonUIViewModel.Importer.HOME_ASSISTANT,    it.startDate, it.finishDate)) }
        }
        simItems + dsItems
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        Log.d("UI2", "UI2SimulationsViewModel created")
        viewModelScope.launch(Dispatchers.Main) {
            repository.getAllScenarios().asFlow().collect { list ->
                _scenarios.value = list ?: emptyList()
                enrichScenarios(list ?: emptyList())
            }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.getLiveDateRanges().asFlow().collect { list ->
                _alphaSources.value = list ?: emptyList()
            }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.getESBNLiveDateRanges().asFlow().collect { list ->
                _esbnSources.value = list ?: emptyList()
            }
        }
        viewModelScope.launch(Dispatchers.Main) {
            repository.getHALiveDateRanges().asFlow().collect { list ->
                _haSources.value = list ?: emptyList()
            }
        }
    }

    private fun enrichScenarios(scenarios: List<Scenario>) {
        viewModelScope.launch(Dispatchers.IO) {
            scenarios.forEach { scenario ->
                val id = scenario.scenarioIndex
                if (_enrichments.value.containsKey(id)) return@forEach
                val cost   = runCatching { repository.getBestCostingForScenario(id)?.net?.let { it / 100.0 } }.getOrNull()
                val dsName = runCatching {
                    repository.getScenarioComponentsForScenarioID(id)?.loadProfile?.distributionSource
                }.getOrNull()
                _enrichments.update { it + (id to Pair(cost, dsName)) }
            }
        }
    }

    /**
     * Look up a scenario whose load-profile distributionSource matches the given sysSn.
     * "Custom" is the default for all manually-created scenarios and is excluded.
     */
    fun findScenarioIdForDataSource(sysSn: String): Long? {
        val enrichments = _enrichments.value
        return _scenarios.value.firstOrNull { scenario ->
            val (_, dsName) = enrichments[scenario.scenarioIndex] ?: return@firstOrNull false
            !dsName.isNullOrBlank() && dsName != "Custom" &&
                    (dsName == sysSn ||
                            dsName.contains(sysSn, ignoreCase = true) ||
                            sysSn.contains(dsName, ignoreCase = true))
        }?.scenarioIndex
    }

    fun deleteSimulation(scenarioId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteScenario(scenarioId)
            _enrichments.update { it - scenarioId.toLong() }
        }
    }
}
