package com.tfcode.comparetout.ui2

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.ToutcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val KEY_ACTIVE_TYPE        = "active_type"           // "sim" | "ds"
private const val KEY_ACTIVE_SIM         = "active_simulation_id"
private const val KEY_ACTIVE_DS_SYSSN    = "active_ds_syssn"
private const val KEY_ACTIVE_DS_IMPORTER = "active_ds_importer"
private const val KEY_ACTIVE_DS_START    = "active_ds_start"
private const val KEY_ACTIVE_DS_END      = "active_ds_end"

@HiltViewModel
class UI2SharedViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ToutcRepository,
    private val sampleDataLoader: SampleDataLoader
) : ViewModel() {

    sealed class ActiveSelection {
        object None : ActiveSelection()
        data class Simulation(val id: Long) : ActiveSelection()
        data class DataSource(
            val sysSn: String,
            val importerType: ComparisonUIViewModel.Importer,
            val startDate: String,
            val endDate: String
        ) : ActiveSelection()
    }

    private val _activeSelection = MutableStateFlow<ActiveSelection>(ActiveSelection.None)
    val activeSelection = _activeSelection.asStateFlow()

    // True once an explicit selection has been requested this session (e.g.
    // launched from an importer notification). Guards the async init restore
    // below from racing in and overwriting it with the last-persisted value.
    @Volatile private var explicitSelectionRequested = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as TOUTCApplication
            val stored = app.dataStore
                .data()
                .firstOrError()
                .map { prefs ->
                    listOf(
                        prefs[stringPreferencesKey(KEY_ACTIVE_TYPE)]        ?: "",
                        prefs[stringPreferencesKey(KEY_ACTIVE_SIM)]         ?: "",
                        prefs[stringPreferencesKey(KEY_ACTIVE_DS_SYSSN)]    ?: "",
                        prefs[stringPreferencesKey(KEY_ACTIVE_DS_IMPORTER)] ?: "",
                        prefs[stringPreferencesKey(KEY_ACTIVE_DS_START)]    ?: "",
                        prefs[stringPreferencesKey(KEY_ACTIVE_DS_END)]      ?: ""
                    )
                }
                .onErrorReturnItem(List(6) { "" })
                .blockingGet()

            // A notification-driven selection beat us here — don't clobber it.
            if (explicitSelectionRequested) return@launch

            if (stored[0] == "ds") {
                val sysSn    = stored[2]
                val typStr   = stored[3]
                val start    = stored[4]
                val end      = stored[5]
                if (sysSn.isBlank() || typStr.isBlank() || start.isBlank() || end.isBlank()) return@launch
                val importer = runCatching { ComparisonUIViewModel.Importer.valueOf(typStr) }.getOrNull()
                    ?: return@launch
                // Validate that the persisted DS still exists before adopting it.
                // AlphaESS has a sync date-range lookup; ESBN/HA fall through to
                // the reactive observer in the date-ranges combine below — if the
                // sysSn isn't in any of the three lists when it next emits, the
                // selection will be cleared then.
                if (importer == ComparisonUIViewModel.Importer.ALPHAESS) {
                    val exists = runCatching { repository.getDateRange(sysSn) != null }.getOrDefault(false)
                    if (!exists) {
                        clearActiveSelection()
                        return@launch
                    }
                }
                _activeSelection.value = ActiveSelection.DataSource(sysSn, importer, start, end)
            } else {
                val id = stored[1].toLongOrNull() ?: return@launch
                // Validate that the persisted scenario still exists. Check the
                // Scenario row itself, not the ScenarioComponents wrapper —
                // ScenarioDAO.getScenarioComponentsForScenarioID always returns
                // a non-null wrapper (its sub-queries fall back to empty lists
                // for a missing id), so a wrapper-non-null check would let
                // every deleted id pass and re-pin a ghost selection.
                val exists = runCatching {
                    repository.getScenarioComponentsForScenarioID(id)?.scenario != null
                }.getOrDefault(false)
                if (!exists) {
                    clearActiveSelection()
                    return@launch
                }
                _activeSelection.value = ActiveSelection.Simulation(id)
            }
        }

        // After the sample-data loader seeds a scenario, point the dashboard at
        // it automatically. SampleDataLoader emits the new scenarioId on its
        // SharedFlow (replay=0, extraBuffer=1) only on a fresh load — later VM
        // recreations won't replay, so an explicit user navigation after the
        // initial seed isn't clobbered.
        viewModelScope.launch {
            sampleDataLoader.selectScenario.collect { id -> setActiveSimulationId(id) }
        }

        // Keep an active data-source selection's date range current. The range
        // is captured when a source is picked, but a later fetch (manual,
        // daily, or notification-driven) extends the data — the InverterDateRange
        // table then emits and we refresh the selection so the dashboard and
        // graphs, which both observe activeSelection, recompute over the full
        // window instead of the stale range they were opened with.
        //
        // Also acts as the deletion guard for data-source selections: if the
        // active sysSn vanishes from all three date-range tables (e.g. the user
        // wiped all data for it from the Data Source Management screen), the
        // saved dashboard subject is cleared rather than left pointing at a
        // gone source.
        viewModelScope.launch {
            combine(
                repository.liveDateRanges.asFlow(),
                repository.esbnLiveDateRanges.asFlow(),
                repository.haLiveDateRanges.asFlow(),
                _activeSelection
            ) { alpha, esbn, ha, sel ->
                // sysSn -> (start, finish). Match the Simulations list's dedup
                // priority: alpha wins over esbn over ha for a shared sysSn.
                val ranges = buildMap<String, Pair<String?, String?>> {
                    ha.orEmpty().forEach   { put(it.sysSn, it.startDate to it.finishDate) }
                    esbn.orEmpty().forEach { put(it.sysSn, it.startDate to it.finishDate) }
                    alpha.orEmpty().forEach { put(it.sysSn, it.startDate to it.finishDate) }
                }
                ranges to sel
            }.collect { (ranges, sel) ->
                if (sel !is ActiveSelection.DataSource) return@collect
                if (sel.sysSn !in ranges.keys) {
                    clearActiveSelection()
                    return@collect
                }
                val (start, finish) = ranges[sel.sysSn] ?: return@collect
                if (start.isNullOrBlank() || finish.isNullOrBlank()) return@collect
                if (start != sel.startDate || finish != sel.endDate) {
                    setActiveDataSource(sel.sysSn, sel.importerType, start, finish)
                }
            }
        }

        // Deletion guard for simulation selections: when the scenarios list
        // emits without the active sim id, the saved dashboard subject is
        // cleared. Verified with a sync DB read because Room's LiveData can
        // lag the table on a fresh insert-then-select (SampleDataLoader path)
        // — without the verify, the freshly-selected scenario would be cleared
        // before its row had propagated to this observer.
        viewModelScope.launch {
            combine(repository.allScenarios.asFlow(), _activeSelection) { scenarios, sel ->
                scenarios.orEmpty() to sel
            }.collect { (scenarios, sel) ->
                if (sel !is ActiveSelection.Simulation) return@collect
                if (scenarios.any { it.scenarioIndex == sel.id }) return@collect
                val exists = withContext(Dispatchers.IO) {
                    runCatching { repository.getScenarioComponentsForScenarioID(sel.id)?.scenario != null }
                        .getOrDefault(false)
                }
                if (!exists) clearActiveSelection()
            }
        }
    }

    /**
     * Drop the saved dashboard subject — sets [activeSelection] to
     * [ActiveSelection.None] and blanks the persisted DataStore keys so the
     * next app launch starts unpinned. Invoked by the deletion guards in
     * `init {}` when the subject's underlying data has gone away; the dashboard
     * fragment reacts to [ActiveSelection.None] by clearing its accordion state
     * and showing the "pick a subject" empty card.
     */
    private fun clearActiveSelection() {
        _activeSelection.value = ActiveSelection.None
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as TOUTCApplication
            app.putStringValueIntoDataStore(KEY_ACTIVE_TYPE,        "")
            app.putStringValueIntoDataStore(KEY_ACTIVE_SIM,         "")
            app.putStringValueIntoDataStore(KEY_ACTIVE_DS_SYSSN,    "")
            app.putStringValueIntoDataStore(KEY_ACTIVE_DS_IMPORTER, "")
            app.putStringValueIntoDataStore(KEY_ACTIVE_DS_START,    "")
            app.putStringValueIntoDataStore(KEY_ACTIVE_DS_END,      "")
        }
    }

    fun setActiveSimulationId(id: Long) {
        _activeSelection.value = ActiveSelection.Simulation(id)
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as TOUTCApplication
            app.putStringValueIntoDataStore(KEY_ACTIVE_TYPE, "sim")
            app.putStringValueIntoDataStore(KEY_ACTIVE_SIM, id.toString())
        }
    }

    fun setActiveDataSource(
        sysSn: String,
        importerType: ComparisonUIViewModel.Importer,
        startDate: String,
        endDate: String
    ) {
        _activeSelection.value = ActiveSelection.DataSource(sysSn, importerType, startDate, endDate)
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as TOUTCApplication
            app.putStringValueIntoDataStore(KEY_ACTIVE_TYPE,        "ds")
            app.putStringValueIntoDataStore(KEY_ACTIVE_DS_SYSSN,    sysSn)
            app.putStringValueIntoDataStore(KEY_ACTIVE_DS_IMPORTER, importerType.name)
            app.putStringValueIntoDataStore(KEY_ACTIVE_DS_START,    startDate)
            app.putStringValueIntoDataStore(KEY_ACTIVE_DS_END,      endDate)
        }
    }

    /**
     * Select a data source given only its SN + importer — the entry point used
     * when an importer notification launches the app. The data's date span is
     * resolved from the DB so the dashboard can land directly on that source;
     * if there's no data yet, the selection is left untouched rather than
     * pointing the dashboard at an empty range.
     */
    fun selectDataSourceBySn(sysSn: String, importerType: ComparisonUIViewModel.Importer) {
        explicitSelectionRequested = true
        viewModelScope.launch(Dispatchers.IO) {
            val range = runCatching { repository.getDateRange(sysSn) }.getOrNull()
            val start = range?.startDate
            val end = range?.finishDate
            if (start.isNullOrBlank() || end.isNullOrBlank()) return@launch
            withContext(Dispatchers.Main) {
                setActiveDataSource(sysSn, importerType, start, end)
            }
        }
    }
}
