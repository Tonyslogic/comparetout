package com.tfcode.comparetout.ui2

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.ToutcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    private val repository: ToutcRepository
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

    // Convenience for code that only cares about simulation ID
    val activeSimulationId = _activeSelection
        .map { (it as? ActiveSelection.Simulation)?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as TOUTCApplication
            val stored = app.getDataStore()
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
                _activeSelection.value = ActiveSelection.DataSource(sysSn, importer, start, end)
            } else {
                stored[1].toLongOrNull()?.let { _activeSelection.value = ActiveSelection.Simulation(it) }
            }
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
