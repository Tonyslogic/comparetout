package com.tfcode.comparetout.ui2

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.TOUTCApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_ACTIVE_SIM = "active_simulation_id"

@HiltViewModel
class UI2SharedViewModel @Inject constructor(
    @ApplicationContext private val context: Context
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

    // Convenience for code that only cares about simulation ID
    val activeSimulationId = _activeSelection
        .map { (it as? ActiveSelection.Simulation)?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as TOUTCApplication
            val idStr = app.getDataStore()
                .data()
                .firstOrError()
                .map { prefs -> prefs[stringPreferencesKey(KEY_ACTIVE_SIM)] ?: "" }
                .onErrorReturnItem("")
                .blockingGet()
            idStr.toLongOrNull()?.let { _activeSelection.value = ActiveSelection.Simulation(it) }
        }
    }

    fun setActiveSimulationId(id: Long) {
        _activeSelection.value = ActiveSelection.Simulation(id)
        viewModelScope.launch(Dispatchers.IO) {
            (context.applicationContext as TOUTCApplication)
                .putStringValueIntoDataStore(KEY_ACTIVE_SIM, id.toString())
        }
    }

    fun setActiveDataSource(
        sysSn: String,
        importerType: ComparisonUIViewModel.Importer,
        startDate: String,
        endDate: String
    ) {
        _activeSelection.value = ActiveSelection.DataSource(sysSn, importerType, startDate, endDate)
    }
}
