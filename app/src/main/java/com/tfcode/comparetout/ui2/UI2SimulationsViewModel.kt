package com.tfcode.comparetout.ui2

import android.util.Log
import androidx.lifecycle.ViewModel
import com.tfcode.comparetout.model.ToutcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UI2SimulationsViewModel @Inject constructor(
    private val repository: ToutcRepository
) : ViewModel() {

    init {
        Log.d("UI2", "UI2SimulationsViewModel created, repository=$repository")
    }

    val scenarios = repository.getAllScenarios().also {
        Log.d("UI2", "UI2SimulationsViewModel: scenarios LiveData instance=$it")
    }
}
