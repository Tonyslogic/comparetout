/*
 * Copyright (c) 2026. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.ui2

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Drives the single simple-mode screen: holds the handful of inputs, kicks off
 * [SimpleScenarioLoader.buildAndRun], and surfaces the resulting cost once the
 * background simulation + costing finish.
 */
@HiltViewModel
class UI2SimpleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ToutcRepository,
    private val loader: SimpleScenarioLoader,
    private val downloader: PricePlanDownloader
) : ViewModel() {

    enum class Status { IDLE, BUILDING, SIMULATING, READY, ERROR }

    data class UiState(
        val annualKwh: String = "4200",
        val hasSolar: Boolean = true,
        val azimuth: String = "180",          // degrees; 180 = due south
        val locationLat: Double? = null,
        val locationLon: Double? = null,
        val batteryKwh: Int = 0,              // 0 / 5 / 10 / 15
        val nightCharge: Boolean = false
    )

    data class Result(
        val annualNetEuro: Double,
        val planName: String,
        val annualBuyEuro: Double,
        val annualSellEuro: Double
    )

    private val app get() = context.applicationContext as TOUTCApplication

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _result = MutableStateFlow<Result?>(null)
    val result: StateFlow<Result?> = _result.asStateFlow()

    private val _planCount = MutableStateFlow(0)
    val planCount: StateFlow<Int> = _planCount.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        refreshPlanCount()
        loadExisting()
    }

    // ── Input setters ─────────────────────────────────────────────────────
    fun setAnnualKwh(v: String) = _uiState.update { it.copy(annualKwh = v.filter { c -> c.isDigit() }) }
    fun setHasSolar(v: Boolean) = _uiState.update { it.copy(hasSolar = v) }
    fun setAzimuth(v: String) = _uiState.update { it.copy(azimuth = v.filter { c -> c.isDigit() }) }
    fun setBatteryKwh(v: Int) = _uiState.update { it.copy(batteryKwh = v) }
    fun setNightCharge(v: Boolean) = _uiState.update { it.copy(nightCharge = v) }
    fun setLocation(lat: Double, lon: Double) =
        _uiState.update { it.copy(locationLat = lat, locationLon = lon) }

    // ── Actions ───────────────────────────────────────────────────────────

    fun calculate() {
        val s = _uiState.value
        val annual = s.annualKwh.toDoubleOrNull()
        if (annual == null || annual <= 0.0) {
            _events.tryEmit("Enter your annual electricity usage (kWh)")
            return
        }
        if (s.hasSolar && (s.locationLat == null || s.locationLon == null)) {
            _events.tryEmit("Tap “Use my location” so we can estimate your solar")
            return
        }
        val inputs = SimpleScenarioLoader.SimpleInputs(
            annualKwh = annual,
            usage = SimpleScenarioLoader.UsageSource.Slp(StandardLoadProfiles.URBAN_SMART),
            hasSolar = s.hasSolar,
            latitude = s.locationLat ?: 0.0,
            longitude = s.locationLon ?: 0.0,
            azimuthDegrees = s.azimuth.toIntOrNull() ?: 180,
            batteryKwh = s.batteryKwh.toDouble(),
            nightCharge = s.nightCharge
        )
        _status.value = Status.BUILDING
        _result.value = null
        viewModelScope.launch {
            when (val r = loader.buildAndRun(inputs)) {
                is SimpleScenarioLoader.Result.Built -> {
                    _status.value = Status.SIMULATING
                    pollForResult(r.scenarioId)
                }
                is SimpleScenarioLoader.Result.Failed -> {
                    _status.value = Status.ERROR
                    _events.tryEmit("Couldn’t build: ${r.error.message ?: "unknown error"}")
                }
            }
        }
    }

    /** Re-read the cost for the current simple scenario (e.g. after a manual wait). */
    fun refreshResult() {
        val id = withStoredId() ?: return
        viewModelScope.launch { pollForResult(id, attempts = 1) }
    }

    fun downloadTariffs() {
        viewModelScope.launch {
            val msg = when (val r = downloader.download()) {
                is PricePlanDownloader.Result.Loaded ->
                    "Downloaded ${r.added} tariff${if (r.added == 1) "" else "s"} · " +
                        "community-maintained, may be out of date"
                is PricePlanDownloader.Result.Empty -> "No tariffs found in the published list"
                is PricePlanDownloader.Result.NoNetwork -> "No connection — couldn’t download tariffs"
                is PricePlanDownloader.Result.Failed ->
                    "Couldn’t download tariffs: ${r.error.message ?: "unknown error"}"
            }
            refreshPlanCount()
            _events.tryEmit(msg)
            // If a scenario is already built, its cost can now be computed.
            withStoredId()?.let { pollForResult(it, attempts = 1) }
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private fun refreshPlanCount() {
        viewModelScope.launch {
            _planCount.value = withContext(Dispatchers.IO) {
                repository.allPricePlansNow?.size ?: 0
            }
        }
    }

    private fun withStoredId(): Long? = storedSimpleScenarioId(app)

    /**
     * Poll the best costing for [id] until it appears (costing is produced by a
     * background worker after the simulation). With no price plans there can be
     * no costing, so we don't wait — the UI prompts to download tariffs instead.
     */
    private suspend fun pollForResult(id: Long, attempts: Int = 45) {
        if (_planCount.value <= 0) {
            _status.value = Status.READY
            return
        }
        repeat(attempts) {
            val costing = withContext(Dispatchers.IO) {
                runCatching { repository.getBestCostingForScenario(id) }.getOrNull()
            }
            if (costing != null) {
                _result.value = Result(
                    annualNetEuro = costing.net / 100.0,
                    planName = costing.fullPlanName ?: "",
                    annualBuyEuro = costing.buy / 100.0,
                    annualSellEuro = costing.sell / 100.0
                )
                _status.value = Status.READY
                return
            }
            if (attempts > 1) delay(2_000)
        }
        // Timed out waiting — leave SIMULATING; the user can refresh shortly.
        if (attempts > 1) _events.tryEmit("Still simulating — tap Refresh in a moment")
    }

    /** Reflect an existing simple scenario's inputs + cost back into the UI. */
    private fun loadExisting() {
        viewModelScope.launch {
            val id = withStoredId() ?: return@launch
            val comps = withContext(Dispatchers.IO) {
                runCatching { repository.getScenarioComponentsForScenarioID(id) }.getOrNull()
            } ?: return@launch
            val lp = comps.loadProfile
            val panel = comps.panels?.firstOrNull()
            val battery = comps.batteries?.firstOrNull()
            _uiState.update {
                it.copy(
                    annualKwh = lp?.annualUsage?.toInt()?.toString() ?: it.annualKwh,
                    hasSolar = panel != null,
                    azimuth = panel?.azimuth?.toString() ?: it.azimuth,
                    locationLat = panel?.latitude,
                    locationLon = panel?.longitude,
                    batteryKwh = battery?.batterySize?.toInt() ?: 0,
                    nightCharge = !comps.loadShifts.isNullOrEmpty()
                )
            }
            pollForResult(id, attempts = 1)
        }
    }
}
