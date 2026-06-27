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
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tfcode.comparetout.MainActivity.CHANNEL_ID
import com.tfcode.comparetout.R
import com.tfcode.comparetout.SimulatorLauncher
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.scenario.HeatPumpWeatherCache
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Fetches real ERA5 outdoor weather from the Copernicus CDS for a scenario's heat pump and caches it as the
 * raw time-series CSV the sim already parses (Phase 6 of `plans/hp/plan.md`). Modelled on
 * [PVGISDirectFetchWorker], but the CDS request is **asynchronous** (submit → poll → download), so this worker
 * drives that queue.
 *
 * The location is the heat pump's stored lat/lon (the wizard pre-fills it from the PV system when present);
 * the period is the scenario's sim-grid span — 2001 for PVGIS/synthetic scenarios, the importer's real year(s)
 * otherwise — so the cached weather lands on the same UTC millis grid as the PV/load. The output file path is
 * the shared contract in [HeatPumpWeatherCache]; [com.tfcode.comparetout.scenario.SimulationWorker] reads the
 * same path.
 *
 * **First-fetch tuning (the one thing not verifiable without a CDS account — see plan §6):** the CDS process
 * endpoints, the auth header name, and whether the download is a bare CSV or a ZIP-wrapped CSV are pinned to
 * the best-known protocol below. Run one real fetch and adjust the [Cds] constants / [extractCsv] if the live
 * service differs. The CSV *columns* are already handled by `CsvWeatherProvider`.
 */
class HeatPumpWeatherFetchWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val notifier by lazy { NotificationManagerCompat.from(applicationContext) }

    override fun doWork(): Result {
        val scenarioID = inputData.getLong("scenarioID", 0L)
        if (scenarioID == 0L) return Result.failure()

        val app = applicationContext as Application
        val repository = ToutcRepository(app)

        val components = repository.getScenarioComponentsForScenarioID(scenarioID) ?: return Result.success()
        val hp = components.heatPumps?.firstOrNull() ?: return Result.success() // no HP ⇒ nothing to fetch
        if (hp.weatherSource != "cds") return Result.success()                   // sample asset ⇒ no fetch

        // Credentials (stored encrypted in DataStore by Data Source Management, Phase 5.5b).
        val toutc = applicationContext as TOUTCApplication
        val token = decryptOrNull(toutc.getStringValueFromDataStore(CDS_KEY_KEY))
        if (token == null) {
            // The wizard gates CDS on a token, so this is unusual. Don't fail the chain (that would cancel the
            // queued sim) — notify and let the sim run on the sample asset.
            finish("Heat-pump weather: no CDS token — using sample weather")
            return Result.success()
        }
        val base = (decryptOrNull(toutc.getStringValueFromDataStore(CDS_URL_KEY)) ?: "")
            .ifBlank { Cds.DEFAULT_BASE_URL }.trimEnd('/')

        // Location + period → the shared cache path. The grid span derives the calendar period (both modes).
        val grid = repository.getSimulationInputNoSolar(scenarioID)
        if (grid.isEmpty()) {
            // We run AFTER GenerateMissingLoadDataWorker in the chain, so its load rows are already committed —
            // an empty grid here means the scenario genuinely has no load data, in which case the sim won't use
            // weather anyway. Don't block the chained sim with retries; skip (it falls back to the sample asset).
            finish("Heat-pump weather: no load data — using sample weather")
            return Result.success()
        }
        val gridMillis = HeatPumpWeatherCache.gridMillis(grid)
        val span = HeatPumpWeatherCache.spanMillis(gridMillis)
        // When PV came from a historical local import (AlphaESS / Home Assistant), fetch ERA5 for that REAL
        // year and realign it onto the 2001 grid below; otherwise the period is the load grid's own span
        // (2001 for PVGIS/synthetic). pvPeriod is null unless a panel carries a non-2001 range, so PVGIS and
        // legacy scenarios are byte-identical to before. The sim keys the same cache the same way.
        val pvPeriod = HeatPumpWeatherCache.pvSourcePeriod(components.panels)
        val startIso = pvPeriod?.get(0) ?: HeatPumpWeatherCache.isoDate(span[0])
        val endIso = pvPeriod?.get(1) ?: HeatPumpWeatherCache.isoDate(span[1])
        val cacheFile = HeatPumpWeatherCache.cacheFile(
            applicationContext, hp.latitude, hp.longitude, startIso, endIso
        )
        // Whether this period is the user's real PV-source year (→ aligned onto the 2001 grid below) or the
        // 2001 reference year itself. Surfaced in the notifications so the download → align → simulate order
        // is visible (a bare "2001" otherwise reads as if the real fetch was skipped).
        val historical = pvPeriod != null
        val periodLabel = if (historical) "$startIso → $endIso (your PV-source year)"
                          else "2001 reference year"

        if (cacheFile.exists() && cacheFile.length() > 0) {
            finish("Heat-pump weather already cached: $periodLabel — simulating")
            // The sim skips CDS scenarios whose weather is missing, so a recompute that finds the cache
            // already present still needs a nudge to run now that the data is available.
            SimulatorLauncher.simulateIfNeeded(applicationContext)
            return Result.success() // immutable — reuse
        }

        progress("Downloading ERA5 weather: $periodLabel…", indeterminate = true)
        return try {
            var csv = fetchCds(base, token, hp.latitude, hp.longitude, startIso, endIso)
            // Realign a real-year historical fetch onto the 2001 grid the PV/load sit on (no-op for the
            // grid-period path, where pvPeriod is null).
            if (historical) {
                progress("Aligning $startIso → $endIso weather to the 2001 simulation grid…",
                    indeterminate = true)
                csv = HeatPumpWeatherCache.remapWeatherTo2001(csv)
            }
            writeAtomic(cacheFile, csv)
            // Flag the credentials as known-good now that a fetch succeeded (Phase 5.5 deferred validation here).
            toutc.putStringValueIntoDataStore(CDS_GOOD_KEY, "True")
            finish(if (historical)
                       "Weather ready: $startIso → $endIso aligned to 2001 grid — simulating"
                   else "Weather ready: 2001 reference year — simulating")
            // Real weather has landed — re-run the simulation so the scenario the sim skipped now completes.
            SimulatorLauncher.simulateIfNeeded(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "CDS weather fetch failed for scenario $scenarioID (attempt $runAttemptCount)", e)
            if (runAttemptCount >= Cds.MAX_RETRIES) {
                // Give up gracefully so the queued sim still runs (on the sample asset) rather than stalling.
                finish("Heat-pump weather fetch failed — using sample weather")
                Result.success()
            } else {
                progress("Heat-pump weather: fetch failed, retrying…", indeterminate = true)
                Result.retry()
            }
        }
    }

    /** Submit the time-series request, poll the async job to completion, download + extract the CSV. */
    private fun fetchCds(
        base: String, token: String,
        latitude: Double, longitude: Double, startIso: String, endIso: String
    ): String {
        // 1) SUBMIT — OGC API Processes "execution" of the point time-series dataset.
        val body = JsonObject().apply {
            add("inputs", JsonObject().apply {
                add("variable", Gson().toJsonTree(
                    listOf("2m_temperature", "10m_u_component_of_wind", "10m_v_component_of_wind")
                ))
                add("location", JsonObject().apply {
                    // Snap to the ERA5 0.25° node — off-grid points can return "Not Found" (ECMWF forum),
                    // and the cache file (HeatPumpWeatherCache.cacheFile) is keyed on the same snapped node.
                    addProperty("latitude", HeatPumpWeatherCache.snapToEra5Grid(latitude))
                    addProperty("longitude", HeatPumpWeatherCache.snapToEra5Grid(longitude))
                })
                add("date", Gson().toJsonTree(listOf("$startIso/$endIso")))
                addProperty("data_format", "csv")
            })
        }
        progress("Heat-pump weather: submitting request to CDS…", indeterminate = true)
        val submit = postJson("$base/retrieve/v1/processes/${Cds.DATASET}/execution", token, body.toString())
        var jobId = submit.get("jobID")?.asString
            ?: throw IllegalStateException("CDS submit returned no jobID: $submit")
        var status = submit.get("status")?.asString ?: "accepted"

        // 2) POLL — bounded; CDS queues then runs. Single-point/year jobs are usually quick. Surface each
        // poll so the (potentially minutes-long) async wait is visible, not a silent hang.
        var attempt = 0
        while (status != "successful") {
            if (status == "failed" || status == "dismissed") {
                throw IllegalStateException("CDS job $jobId $status")
            }
            if (attempt >= Cds.MAX_POLLS) {
                throw IllegalStateException("CDS job $jobId still '$status' after ${Cds.MAX_POLLS} polls")
            }
            attempt++
            progress("Heat-pump weather: $status at CDS (check $attempt/${Cds.MAX_POLLS})…",
                indeterminate = true)
            Thread.sleep(Cds.POLL_INTERVAL_MS)
            val job = getJson("$base/retrieve/v1/jobs/$jobId", token)
            status = job.get("status")?.asString ?: status
            job.get("jobID")?.asString?.let { jobId = it }
        }

        // 3) RESULTS — resolve the asset href, then download the payload.
        progress("Heat-pump weather: downloading…", indeterminate = true)
        val results = getJson("$base/retrieve/v1/jobs/$jobId/results", token)
        val href = results.getAsJsonObject("asset")
            ?.getAsJsonObject("value")?.get("href")?.asString
            ?: throw IllegalStateException("CDS results carried no asset href: $results")
        val payload = download(href, token)
        return extractCsv(payload)
    }

    // --- Notifications (mirrors SimulationWorker: same CHANNEL_ID + icon, low-priority/silent) -------------

    /** Ongoing progress note (indeterminate bar) for the submit/poll/download phases. */
    private fun progress(text: String, indeterminate: Boolean) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Heat pump weather")
            .setContentText(text)
            .setSmallIcon(R.drawable.housetick)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, indeterminate)
            .build()
        runCatching { notifier.notify(NOTIFICATION_ID, n) }
    }

    /** Terminal note (no bar, auto-dismisses) for success / fallback. */
    private fun finish(text: String) {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Heat pump weather")
            .setContentText(text)
            .setSmallIcon(R.drawable.housetick)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setTimeoutAfter(8000)
            .build()
        runCatching { notifier.notify(NOTIFICATION_ID, n) }
    }

    // --- HTTP helpers (HttpURLConnection, mirroring PVGISDirectFetchWorker — no extra deps) ----------------

    private fun postJson(url: String, token: String, json: String): JsonObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty(Cds.AUTH_HEADER, token)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        c.outputStream.use { it.write(json.toByteArray()) }
        return readJson(c)
    }

    private fun getJson(url: String, token: String): JsonObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty(Cds.AUTH_HEADER, token)
            setRequestProperty("Accept", "application/json")
        }
        return readJson(c)
    }

    private fun readJson(c: HttpURLConnection): JsonObject {
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("CDS HTTP $code: $text")
            return JsonParser.parseString(text).asJsonObject
        } finally {
            c.disconnect()
        }
    }

    private fun download(url: String, token: String): ByteArray {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            // Asset hrefs are often presigned object-store URLs; the token is harmless if ignored.
            setRequestProperty(Cds.AUTH_HEADER, token)
        }
        return try {
            if (c.responseCode !in 200..299) {
                throw IllegalStateException("CDS download HTTP ${c.responseCode}")
            }
            c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /**
     * The download is either a bare CSV or a ZIP wrapping a single CSV (CDS commonly zips even CSV output).
     * Detect the ZIP magic and extract the first `.csv` entry; otherwise return the bytes as text.
     */
    private fun extractCsv(payload: ByteArray): String {
        val isZip = payload.size >= 2 && payload[0] == 'P'.code.toByte() && payload[1] == 'K'.code.toByte()
        if (!isZip) return String(payload)
        ZipInputStream(ByteArrayInputStream(payload)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".csv", ignoreCase = true)) {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        throw IllegalStateException("CDS ZIP payload contained no .csv entry")
    }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IllegalStateException("could not finalise ${target.name}")
        }
    }

    private fun decryptOrNull(s: String?): String? {
        if (s.isNullOrEmpty()) return null
        return runCatching { TOUTCApplication.decryptString(s) }.getOrNull()?.ifBlank { null }
    }

    /** CDS protocol constants — the part to confirm on the first real fetch (plan §6). */
    private object Cds {
        const val DEFAULT_BASE_URL = "https://cds.climate.copernicus.eu/api"
        const val DATASET = "reanalysis-era5-single-levels-timeseries"
        const val AUTH_HEADER = "PRIVATE-TOKEN"
        const val POLL_INTERVAL_MS = 10_000L
        const val MAX_POLLS = 90 // ~15 min ceiling before WorkManager retry takes over
        const val MAX_RETRIES = 3 // give up (fall back to sample) after this many worker attempts
    }

    companion object {
        private const val TAG = "HeatPumpWeather"
        private const val NOTIFICATION_ID = 4242 // distinct from SimulationWorker's id (1)
        private const val CDS_URL_KEY = "cds_url"
        private const val CDS_KEY_KEY = "cds_key"
        private const val CDS_GOOD_KEY = "cds_cred_good"
        // Enqueued two ways: (1) as a chained step in SimulatorLauncher.simulateWithWeatherFetch on wizard
        // save (GenerateLoad → weather → Simulate → Cost), and (2) standalone, unique-per-scenario, by
        // SimulationWorker when a recompute hits a CDS scenario whose weather isn't cached. The old "never
        // standalone" race is gone: the sim now SKIPS a CDS scenario with missing weather (it never simulates
        // it on the sample asset), and this worker re-runs the sim (simulateIfNeeded) once the download lands.
    }
}
