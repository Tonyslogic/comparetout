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
import com.tfcode.comparetout.importers.CredentialStore
import com.tfcode.comparetout.importers.homeassistant.HABackfillWorker
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformedData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Look-before-you-overwrite preview (OQ-7): every selected series, bucketed
 * daily across the range — or hourly when the range is a single day, at which
 * point [hourStarts] carries the epoch-millis hour keys the worker backfills by.
 * [discrepancies] indexes the buckets where HA and the source disagree.
 */
data class HaBackfillPreview(
    val seriesKeys: List<String>,
    val labels: List<String>,
    val hourly: Boolean,
    val hourStarts: List<Long>,
    val source: Map<String, List<Double>>,
    val ha: Map<String, List<Double>>,
    val discrepancies: List<Int>
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

    private val _previewLoading = MutableLiveData(false)
    /** A preview query is in flight — selection changes recompute automatically. */
    val previewLoading: LiveData<Boolean> = _previewLoading

    /** Monotonic ticket so a stale (superseded) preview result never lands. */
    @Volatile private var previewGeneration = 0L

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
        previewGeneration++
        _preview.postValue(null)
        _previewLoading.postValue(false)
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

    /**
     * Build the HA-vs-source overlay for the selected series over the candidate
     * range — daily buckets, or hourly when [hourly] (single-day range) so the
     * user can drill into and repair one specific hour.
     */
    fun previewBackfill(sourceSysSn: String, from: LocalDate, to: LocalDate,
                        seriesKeys: List<String>, hourly: Boolean) {
        val extractors = seriesKeys.mapNotNull { k -> seriesExtractor(k)?.let { k to it } }
        if (extractors.isEmpty()) {
            clearPreview()
            return
        }
        val generation = ++previewGeneration
        _previewLoading.postValue(true)
        viewModelScope.launch(Dispatchers.IO) {
            val sourceRows = repository.getAlphaESSTransformedData(
                sourceSysSn, from.toString(), to.toString())
            val haRows = repository.getAlphaESSTransformedData(
                "HomeAssistant", from.toString(), to.toString())

            val labels: List<String>
            val hourStarts: List<Long>
            val sourceBySeries: Map<String, List<Double>>
            val haBySeries: Map<String, List<Double>>
            if (hourly) {
                // Hour buckets keyed the same way the worker buckets them
                // (UTC-hour-aligned millis), labelled in the user's zone.
                val zone = UserTimezoneStore.resolvedZone(app)
                val starts = mutableListOf<Long>()
                val hourLabels = mutableListOf<String>()
                var t = from.atStartOfDay(zone)
                while (t.toLocalDate() == from) {
                    // Snap to the containing UTC hour so the key matches the
                    // worker's bucketing even for non-whole-hour zone offsets.
                    val ms = t.toInstant().toEpochMilli()
                    starts.add(ms - ms % HOUR_MILLIS)
                    hourLabels.add(String.format(java.util.Locale.UK, "%02d:00", t.hour))
                    t = t.plusHours(1)
                }
                fun buckets(rows: List<AlphaESSTransformedData>,
                            extractor: (AlphaESSTransformedData) -> Double): List<Double> {
                    val perHour = HashMap<Long, Double>()
                    rows.forEach { r ->
                        val millis = r.millisSinceEpoch ?: return@forEach
                        perHour.merge(millis - millis % HOUR_MILLIS, extractor(r)) { a, b -> a + b }
                    }
                    return starts.map { perHour[it] ?: 0.0 }
                }
                labels = hourLabels
                hourStarts = starts
                sourceBySeries = extractors.associate { (k, e) -> k to buckets(sourceRows, e) }
                haBySeries = extractors.associate { (k, e) -> k to buckets(haRows, e) }
            } else {
                val days = generateSequence(from) { d -> if (d < to) d.plusDays(1) else null }
                    .map { it.toString() }.toList()
                fun buckets(rows: List<AlphaESSTransformedData>,
                            extractor: (AlphaESSTransformedData) -> Double): List<Double> {
                    val perDay = HashMap<String, Double>()
                    rows.forEach { r -> perDay.merge(r.date, extractor(r)) { a, b -> a + b } }
                    return days.map { perDay[it] ?: 0.0 }
                }
                labels = days
                hourStarts = emptyList()
                sourceBySeries = extractors.associate { (k, e) -> k to buckets(sourceRows, e) }
                haBySeries = extractors.associate { (k, e) -> k to buckets(haRows, e) }
            }

            val discrepancies = labels.indices.filter { i ->
                extractors.any { (k, _) ->
                    val s = sourceBySeries[k]?.get(i) ?: 0.0
                    val h = haBySeries[k]?.get(i) ?: 0.0
                    kotlin.math.abs(s - h) > maxOf(DISCREPANCY_FLOOR_KWH,
                        DISCREPANCY_FRACTION * maxOf(kotlin.math.abs(s), kotlin.math.abs(h)))
                }
            }

            if (generation != previewGeneration) return@launch // superseded
            _preview.postValue(HaBackfillPreview(
                seriesKeys = extractors.map { it.first },
                labels = labels,
                hourly = hourly,
                hourStarts = hourStarts,
                source = sourceBySeries,
                ha = haBySeries,
                discrepancies = discrepancies
            ))
            _previewLoading.postValue(false)
        }
    }

    /**
     * Enqueue the backfill worker. [external]=false targets the user's real sensor
     * statistics (fix missing/bad HA data — needs the discovered sensor ids);
     * true writes app-owned comparetout:* series alongside. A non-null [hourStart]
     * (epoch-millis, UTC-hour-aligned — from [HaBackfillPreview.hourStarts])
     * narrows the write to that single hour.
     */
    fun startBackfill(sourceSysSn: String, from: LocalDate, to: LocalDate,
                      external: Boolean, series: List<String>, hourStart: Long? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val sensorsRaw = app.getStringValueFromDataStore(HA_SENSORS_KEY)
            // Presence check only — secrets never enter worker Data
            // (plans/source/security.md §1); HABackfillWorker resolves them from
            // the encrypted DataStore via CredentialStore itself.
            if (CredentialStore.get(app, CredentialStore.Source.HOME_ASSISTANT) == null) {
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
                .putString(HABackfillWorker.KEY_SOURCE_SYS_SN, sourceSysSn)
                .putString(HABackfillWorker.KEY_FROM, from.toString())
                .putString(HABackfillWorker.KEY_TO, to.toString())
                .putBoolean(HABackfillWorker.KEY_TARGET_EXTERNAL, external)
                .putString(HABackfillWorker.KEY_SENSORS, sensorsRaw ?: "")
                .putString(HABackfillWorker.KEY_SERIES, series.joinToString(","))
                .putLong(HABackfillWorker.KEY_HOUR, hourStart ?: -1L)
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

        private const val HOUR_MILLIS = 3_600_000L
        // A bucket is flagged as a discrepancy when HA and the source differ by
        // more than 5%, with a 0.1 kWh floor so near-zero noise never flags.
        private const val DISCREPANCY_FLOOR_KWH = 0.1
        private const val DISCREPANCY_FRACTION = 0.05
    }
}
