package com.tfcode.comparetout.ui2

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.TOUTCApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val KEY_ACTIVE_SIM = "active_simulation_id"

@HiltViewModel
class UI2SharedViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _activeSimulationId = MutableStateFlow<Long?>(null)
    val activeSimulationId = _activeSimulationId.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val app = context.applicationContext as TOUTCApplication
            val idStr = app.getDataStore()
                .data()
                .firstOrError()
                .map { prefs -> prefs[stringPreferencesKey(KEY_ACTIVE_SIM)] ?: "" }
                .onErrorReturnItem("")
                .blockingGet()
            idStr.toLongOrNull()?.let { _activeSimulationId.value = it }
        }
    }

    fun setActiveSimulationId(id: Long) {
        _activeSimulationId.value = id
        viewModelScope.launch(Dispatchers.IO) {
            (context.applicationContext as TOUTCApplication)
                .putStringValueIntoDataStore(KEY_ACTIVE_SIM, id.toString())
        }
    }
}
