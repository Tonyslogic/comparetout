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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.importers.esbn.ImportESBNOverview
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

    /** Where the yearly usage comes from. */
    enum class UsageMode { STANDARD, HDF }

    data class UiState(
        val annualKwh: String = "4200",
        val usageMode: UsageMode = UsageMode.STANDARD,
        val hasSolar: Boolean = true,
        val solarKwp: Double = 7.0,           // adjustable in 0.5 kWp steps
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

    /** Progress of importing + reading an ESBN HDF file. */
    sealed class HdfState {
        data object None : HdfState()
        data object Importing : HdfState()
        data class Ready(val annualKwh: Int) : HdfState()
    }

    private val _hdfState = MutableStateFlow<HdfState>(HdfState.None)
    val hdfState: StateFlow<HdfState> = _hdfState.asStateFlow()

    private var derived: SimpleScenarioLoader.UsageSource.Derived? = null

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        refreshPlanCount()
        viewModelScope.launch {
            // Restore the remembered inputs (full set, incl. usage mode) before
            // anything else; fall back to reading them off an existing scenario.
            val restored = withContext(Dispatchers.IO) { readPersistedInputs() }
            if (restored != null) {
                _uiState.value = restored
                // Re-hydrate the HDF profile from the still-imported data so a
                // restored "My ESBN data" selection can Calculate without re-import.
                if (restored.usageMode == UsageMode.HDF) tryDeriveHdf(announce = false)
                pollExistingResult()
            } else {
                loadExisting()
            }
            // Persist any later edits (debounced) so they survive app restarts.
            _uiState.collectLatest { state ->
                delay(400)
                withContext(Dispatchers.IO) { writePersistedInputs(state) }
            }
        }
    }

    // ── Input setters ─────────────────────────────────────────────────────
    fun setAnnualKwh(v: String) = _uiState.update { it.copy(annualKwh = v.filter { c -> c.isDigit() }) }
    fun setHasSolar(v: Boolean) = _uiState.update { it.copy(hasSolar = v) }
    fun setAzimuth(v: String) = _uiState.update { it.copy(azimuth = v.filter { c -> c.isDigit() }) }
    fun setBatteryKwh(v: Int) = _uiState.update { it.copy(batteryKwh = v) }
    fun setNightCharge(v: Boolean) = _uiState.update { it.copy(nightCharge = v) }
    fun setLocation(lat: Double, lon: Double) =
        _uiState.update { it.copy(locationLat = lat, locationLon = lon) }

    fun incSolarKwp() = _uiState.update { it.copy(solarKwp = (it.solarKwp + 0.5).coerceAtMost(20.0)) }
    fun decSolarKwp() = _uiState.update { it.copy(solarKwp = (it.solarKwp - 0.5).coerceAtLeast(0.5)) }

    fun setUsageMode(mode: UsageMode) {
        _uiState.update { it.copy(usageMode = mode) }
        // Entering HDF mode: silently surface data imported in a prior session.
        if (mode == UsageMode.HDF && _hdfState.value == HdfState.None) {
            viewModelScope.launch { tryDeriveHdf(announce = false) }
        }
    }

    /** The import worker has been kicked off — drive the spinner. */
    fun markHdfImporting() { _hdfState.value = HdfState.Importing }

    /** The import worker finished — read the file's totals + usage pattern in.
     * Only announce when a fresh import just completed (state was Importing), so
     * re-observing a past success on screen re-entry stays quiet. */
    fun onHdfImported() {
        val wasImporting = _hdfState.value is HdfState.Importing
        viewModelScope.launch { tryDeriveHdf(announce = wasImporting) }
    }

    /** The import worker failed. */
    fun onHdfImportFailed() {
        _hdfState.value = HdfState.None
        _events.tryEmit("Import failed — check the file and try again")
    }

    private suspend fun tryDeriveHdf(announce: Boolean) {
        val agg = withContext(Dispatchers.IO) { aggregateBestEsbn() }
        if (agg == null) {
            derived = null
            _hdfState.value = HdfState.None
            if (announce) _events.tryEmit("No usable data found in that file")
            return
        }
        val (d, annual) = agg
        derived = d
        val a = annual.toInt()
        _hdfState.value = HdfState.Ready(a)
        _uiState.update { it.copy(annualKwh = a.toString()) }
        if (announce) _events.tryEmit("Imported ~$a kWh/year from your ESBN data")
    }

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
        val usage = if (s.usageMode == UsageMode.HDF) {
            derived ?: run {
                _events.tryEmit("Import your ESBN data first, or use the standard profile")
                return
            }
        } else {
            SimpleScenarioLoader.UsageSource.Slp(StandardLoadProfiles.URBAN_SMART)
        }
        val inputs = SimpleScenarioLoader.SimpleInputs(
            annualKwh = annual,
            usage = usage,
            hasSolar = s.hasSolar,
            solarKwp = s.solarKwp,
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
     * The ESBN import worker keys rows by the MPRN it reads from the HDF, not by
     * the SN we passed. Discover the candidate MPRNs the importer recorded (plus
     * our fallback label) and aggregate whichever actually has data.
     */
    private fun candidateEsbnSysSns(): List<String> {
        val out = LinkedHashSet<String>()
        runCatching {
            val json = app.getStringValueFromDataStore(ImportESBNOverview.ESBN_SYSTEM_LIST_KEY)
            if (json.isNotBlank()) {
                Gson().fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
                    ?.let { out.addAll(it) }
            }
        }
        runCatching {
            val prev = app.getStringValueFromDataStore(ImportESBNOverview.ESBN_PREVIOUS_SELECTED_KEY)
            if (prev.isNotBlank()) out.add(prev)
        }
        out.add(SIMPLE_ESBN_SYSTEM_SN)   // fallback when the file carried no MPRN
        return out.toList()
    }

    private fun aggregateBestEsbn(): Pair<SimpleScenarioLoader.UsageSource.Derived, Double>? {
        var best: Pair<SimpleScenarioLoader.UsageSource.Derived, Double>? = null
        for (sn in candidateEsbnSysSns()) {
            val agg = aggregateEsbn(sn) ?: continue
            if (best == null || agg.second > best.second) best = agg
        }
        return best
    }

    /**
     * Aggregate imported rows for [sysSn] into hourly/day-of-week/monthly
     * distributions plus the yearly usage total. The total is the **actual sum
     * of the most recent ~12 months** in the file (not a scaled estimate); only
     * when the file holds less than a year do we scale the partial data up to a
     * year. Distributions are taken over the same window. Independent of any
     * LiveData timing.
     */
    private fun aggregateEsbn(
        sysSn: String
    ): Pair<SimpleScenarioLoader.UsageSource.Derived, Double>? {
        val rows = runCatching {
            repository.getAlphaESSTransformedData(sysSn, "1970-01-01", "2999-12-31")
        }.getOrNull().orEmpty()
        if (rows.isEmpty()) return null

        val maxDate = rows.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .maxOrNull() ?: return null
        // Most recent single-year window: (maxDate - 1 year, maxDate] inclusive.
        val windowStart = maxDate.minusYears(1).plusDays(1)

        val hourly = DoubleArray(24)
        val daily = DoubleArray(7)
        val monthly = DoubleArray(12)
        var total = 0.0
        var earliestInWindow: LocalDate? = null
        rows.forEach { r ->
            val e = r.buy                       // ESBN stores consumption as "buy"
            if (e <= 0.0) return@forEach
            val date = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@forEach
            if (date.isBefore(windowStart) || date.isAfter(maxDate)) return@forEach
            val hour = r.minute.substringBefore(":").toIntOrNull() ?: return@forEach
            hourly[hour] += e
            daily[date.dayOfWeek.value - 1] += e
            monthly[date.monthValue - 1] += e
            total += e
            val cur = earliestInWindow
            if (cur == null || date.isBefore(cur)) earliestInWindow = date
        }
        val earliest = earliestInWindow ?: return null
        if (total <= 0.0) return null

        val h = hourly.map { it / total * 100.0 }
        val d = daily.map { it / total * 100.0 }
        val m = monthly.map { it / total * 100.0 }
        // A full year present → the window total IS the annual usage. Less than a
        // year of data → scale the partial total up to a year.
        val daysInWindow = ChronoUnit.DAYS.between(earliest, maxDate) + 1
        val annual = if (daysInWindow >= 365) total else total * (365.0 / daysInWindow)
        return SimpleScenarioLoader.UsageSource.Derived(h, d, m, "ESBN HDF") to annual
    }

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

    /** Reflect an existing simple scenario's inputs + cost back into the UI.
     * Fallback for installs predating input persistence. */
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
                    solarKwp = panel?.let { p -> (p.panelCount * p.panelkWp) / 1000.0 } ?: it.solarKwp,
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

    /** Re-read just the cost for the already-restored simple scenario. */
    private fun pollExistingResult() {
        viewModelScope.launch {
            val id = withStoredId() ?: return@launch
            pollForResult(id, attempts = 1)
        }
    }

    private fun readPersistedInputs(): UiState? = runCatching {
        val json = app.getStringValueFromDataStore(SIMPLE_INPUTS_KEY)
        if (json.isBlank()) null else Gson().fromJson(json, UiState::class.java)
    }.getOrNull()

    private fun writePersistedInputs(state: UiState) {
        runCatching { app.putStringValueIntoDataStore(SIMPLE_INPUTS_KEY, Gson().toJson(state)) }
    }
}
