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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.importers.homeassistant.HABackfillWorker
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Daily-kWh overlay data for the look-before-you-overwrite preview (OQ-7). */
data class HaBackfillPreview(
    val series: String,
    val labels: List<String>,
    val source: List<Double>,
    val ha: List<Double>
)

/** A backfill candidate — any non-HA source with stored history. */
data class HaBackfillSource(
    val sysSn: String,
    val importer: ComparisonUIViewModel.Importer,
    val startDate: String,
    val finishDate: String
)

/**
 * Drives the standalone Backfill-Home-Assistant wizard ([UI2HaBackfillActivity]).
 * Pushes another source's stored history into HA hourly statistics via
 * [HABackfillWorker]; commit is gated on the graphical HA-vs-source preview
 * (plans/ha/design.md, Enhancement 2 — moved out of the Data Source Management
 * accordion into its own flow).
 */
@HiltViewModel
class UI2HaBackfillViewModel @Inject constructor(
    application: Application,
    private val repository: ToutcRepository
) : AndroidViewModel(application) {

    private val app: TOUTCApplication
        get() = getApplication<Application>() as TOUTCApplication
    private val wm: WorkManager
        get() = WorkManager.getInstance(getApplication())

    // ── selectable sources — same date-range feeds the Compare catalogue uses ──

    private val _sources = MediatorLiveData<List<HaBackfillSource>>()
    val sources: LiveData<List<HaBackfillSource>> = _sources

    private val _haReady = MutableLiveData(false)
    /** Host + token stored and decryptable — the worker can authenticate. */
    val haReady: LiveData<Boolean> = _haReady

    private val _haveSensors = MutableLiveData(false)
    /** Discovered sensors present — required to fix the user's real statistics. */
    val haveSensors: LiveData<Boolean> = _haveSensors

    private val _preview = MutableLiveData<HaBackfillPreview?>(null)
    val preview: LiveData<HaBackfillPreview?> = _preview

    private val _toast = MutableLiveData<Toast?>(null)
    val toast: LiveData<Toast?> = _toast

    init {
        val alphaRanges = repository.liveDateRanges
        val esbnRanges = repository.esbnLiveDateRanges
        fun rebuild() {
            val seen = mutableSetOf<String>()
            val out = mutableListOf<HaBackfillSource>()
            alphaRanges.value?.forEach { r ->
                if (seen.add(r.sysSn)) out.add(
                    HaBackfillSource(r.sysSn, ComparisonUIViewModel.Importer.ALPHAESS,
                        r.startDate, r.finishDate))
            }
            esbnRanges.value?.forEach { r ->
                // HA is the target, never a source; classify the rest via the registry.
                if (r.sysSn == "HomeAssistant") return@forEach
                if (seen.add(r.sysSn)) out.add(
                    HaBackfillSource(r.sysSn, ComparisonUIViewModel.Importer.forSysSn(r.sysSn),
                        r.startDate, r.finishDate))
            }
            _sources.postValue(out)
        }
        _sources.addSource(alphaRanges) { rebuild() }
        _sources.addSource(esbnRanges) { rebuild() }

        viewModelScope.launch(Dispatchers.IO) {
            _haReady.postValue(
                decryptOrNull(app.getStringValueFromDataStore(HA_HOST_KEY)) != null &&
                        decryptOrNull(app.getStringValueFromDataStore(HA_TOKEN_KEY)) != null
            )
            val sensors = app.getStringValueFromDataStore(HA_SENSORS_KEY)
            _haveSensors.postValue(!sensors.isNullOrBlank() && sensors != "{}")
        }
    }

    fun clearPreview() {
        _preview.postValue(null)
    }

    private fun seriesExtractor(series: String): ((AlphaESSTransformedData) -> Double)? =
        when (series) {
            "buy" -> { r -> r.buy }
            "feed" -> { r -> r.feed }
            "pv" -> { r -> r.pv }
            "charge" -> { r -> maxOf(0.0, r.charge) }
            "discharge" -> { r -> maxOf(0.0, -r.charge) }
            else -> null
        }

    /** Build the HA-vs-source daily overlay for one series over the candidate range. */
    fun previewBackfill(sourceSysSn: String, from: LocalDate, to: LocalDate, series: String) {
        val extractor = seriesExtractor(series) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            fun daily(sysSn: String): Map<String, Double> {
                val perDay = HashMap<String, Double>()
                repository.getAlphaESSTransformedData(sysSn, from.toString(), to.toString())
                    .forEach { r -> perDay.merge(r.date, extractor(r)) { a, b -> a + b } }
                return perDay
            }
            val sourceDaily = daily(sourceSysSn)
            val haDaily = daily("HomeAssistant")
            val labels = generateSequence(from) { d -> if (d < to) d.plusDays(1) else null }
                .map { it.toString() }.toList()
            _preview.postValue(HaBackfillPreview(
                series = series,
                labels = labels,
                source = labels.map { sourceDaily[it] ?: 0.0 },
                ha = labels.map { haDaily[it] ?: 0.0 }
            ))
        }
    }

    /**
     * Enqueue the backfill worker. [external]=false targets the user's real sensor
     * statistics (fix missing/bad HA data — needs the discovered sensor ids);
     * true writes app-owned comparetout:* series alongside.
     */
    fun startBackfill(sourceSysSn: String, from: LocalDate, to: LocalDate,
                      external: Boolean, series: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val host = decryptOrNull(app.getStringValueFromDataStore(HA_HOST_KEY))
            val token = decryptOrNull(app.getStringValueFromDataStore(HA_TOKEN_KEY))
            val sensorsRaw = app.getStringValueFromDataStore(HA_SENSORS_KEY)
            if (host == null || token == null) {
                _toast.postValue(Toast("Set Home Assistant credentials first"))
                return@launch
            }
            if (!external && sensorsRaw.isNullOrBlank()) {
                _toast.postValue(Toast("Discover sensors first — they name the statistics to fix"))
                return@launch
            }
            if (series.isEmpty()) {
                _toast.postValue(Toast("Pick at least one series to backfill"))
                return@launch
            }
            val input = Data.Builder()
                .putString(HABackfillWorker.KEY_HOST, host)
                .putString(HABackfillWorker.KEY_TOKEN, token)
                .putString(HABackfillWorker.KEY_SOURCE_SYS_SN, sourceSysSn)
                .putString(HABackfillWorker.KEY_FROM, from.toString())
                .putString(HABackfillWorker.KEY_TO, to.toString())
                .putBoolean(HABackfillWorker.KEY_TARGET_EXTERNAL, external)
                .putString(HABackfillWorker.KEY_SENSORS, sensorsRaw ?: "")
                .putString(HABackfillWorker.KEY_SERIES, series.joinToString(","))
                .build()
            val req = OneTimeWorkRequest.Builder(HABackfillWorker::class.java)
                .setInputData(input)
                .addTag("HABackfill")
                .build()
            wm.pruneWork()
            wm.beginUniqueWork("HABackfill", ExistingWorkPolicy.KEEP, req).enqueue()
            _toast.postValue(Toast("Home Assistant backfill started"))
        }
    }

    private fun decryptOrNull(s: String?): String? {
        if (s.isNullOrEmpty() || s == "null") return null
        return runCatching { TOUTCApplication.decryptString(s) }.getOrNull()
    }

    companion object {
        // Mirror the file-private constants in UI2DataSourceManagementViewModel —
        // the DataStore contents are the shared contract.
        private const val HA_HOST_KEY = "ha_host"
        private const val HA_TOKEN_KEY = "ha_token"
        private const val HA_SENSORS_KEY = "ha_sensors"
    }
}
