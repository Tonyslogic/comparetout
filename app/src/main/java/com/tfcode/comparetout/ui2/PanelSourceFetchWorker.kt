package com.tfcode.comparetout.ui2

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.scenario.PanelData
import com.tfcode.comparetout.scenario.sim.SimTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Background worker that fetches a PV time series from an existing
 * AlphaESS-style data source, optionally applies kWp scaling and
 * azimuth-factoring conversion (see [SolarConversionUtils]), and
 * saves the result as [PanelData] for the target panel.
 *
 * Shows progress via the shared "TOUTC-PROGRESS" notification channel,
 * mirroring [com.tfcode.comparetout.scenario.panel.PVGISLoader].
 */
class PanelSourceFetchWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.i(TAG, "doWork() entered")
        val panelId = inputData.getLong(KEY_PANEL_ID, 0L)
        val sysSn   = inputData.getString(KEY_SYS_SN).orEmpty()
        val from    = inputData.getString(KEY_FROM).orEmpty()
        val to      = inputData.getString(KEY_TO).orEmpty()
        if (panelId == 0L || sysSn.isBlank() || from.isBlank() || to.isBlank()) {
            Log.w(TAG, "Missing required input: panelId=$panelId sysSn='$sysSn' from='$from' to='$to'")
            return Result.failure()
        }

        val sourceKwp        = inputData.getDouble(KEY_SOURCE_KWP, 0.0)
        val targetKwp        = inputData.getDouble(KEY_TARGET_KWP, 0.0)
        val useAzimuthFactor = inputData.getBoolean(KEY_USE_AZIMUTH, false)
        val sourceAz         = inputData.getDouble(KEY_SOURCE_AZ, -1.0)
        val targetAz         = inputData.getDouble(KEY_TARGET_AZ, -1.0)
        val lat              = inputData.getDouble(KEY_LAT, 0.0)
        val lon              = inputData.getDouble(KEY_LON, 0.0)
        val tilt             = inputData.getDouble(KEY_TILT, 0.0)
        val panelLabel       = inputData.getString(KEY_PANEL_LABEL).orEmpty()
        Log.i(TAG, "Job for panelId=$panelId label='$panelLabel' sysSn='$sysSn' $from→$to " +
            "srcKwp=$sourceKwp tgtKwp=$targetKwp useAz=$useAzimuthFactor srcAz=$sourceAz tgtAz=$targetAz " +
            "lat=$lat lon=$lon tilt=$tilt")

        @SuppressLint("InlinedApi")  // permission string is inlined; runtime check returns DENIED pre-API 33 which is fine
        val hasPostPerm = ActivityCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPostPerm) {
            Log.w(TAG, "POST_NOTIFICATIONS permission NOT granted — notifications will be suppressed but work will still run.")
        } else {
            Log.i(TAG, "POST_NOTIFICATIONS permission OK")
        }

        val repo = ToutcRepository(applicationContext as Application)
        val notificationId = NOTIFICATION_ID_BASE + (panelId.toInt() and 0x7FFFFF)
        Log.i(TAG, "notificationId=$notificationId channel=${MainActivity.CHANNEL_ID}")
        val mgr = NotificationManagerCompat.from(applicationContext)
        val builder = baseNotification(panelLabel)

        try {
            updateProgress(mgr, builder, notificationId, "Reading source data…", 5)
            var samples = repo.getAlphaESSTransformedData(sysSn, from, to)
                .map { SolarConversionUtils.PvSample(it.date, it.minute, it.pv) }
            Log.i(TAG, "Fetched ${samples.size} rows from source")

            if (samples.isEmpty()) {
                Log.w(TAG, "No rows found — aborting")
                completeNotification(mgr, builder, notificationId,
                    "No data found for source ${friendlyRange(from, to)}", success = false)
                return Result.success()
            }

            updateProgress(mgr, builder, notificationId, "Scaling kWp…", 25)
            if (sourceKwp > 0.0 && targetKwp > 0.0
                && kotlin.math.abs(sourceKwp - targetKwp) > 1e-6) {
                samples = SolarConversionUtils.scaleByKwp(samples, sourceKwp, targetKwp)
            }

            if (useAzimuthFactor && sourceAz in 0.0..360.0 && targetAz in 0.0..360.0
                && kotlin.math.abs(sourceAz - targetAz) > 0.5) {
                updateProgress(mgr, builder, notificationId,
                    "Re-orienting ${sourceAz.toInt()}° → ${targetAz.toInt()}°…", 40)
                samples = SolarConversionUtils.convertAzimuth(
                    rows = samples,
                    lat = lat, lon = lon, tiltDeg = tilt,
                    sourceAzDeg = sourceAz, targetAzDeg = targetAz
                )
            }

            updateProgress(mgr, builder, notificationId, "Writing panel data…", 80)
            val out = ArrayList<PanelData>(samples.size)
            val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            samples.filter { it.pv > 0 }.forEach { row ->
                val parts = row.minute.split(":")
                val hh = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val mm = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val sourceDate = LocalDate.parse(row.date, dateFmt)
                // Remap the source instant onto the synthetic 2001 UTC grid (keep month/day/HH:mm) so PV shares
                // one UTC axis with the 2001 load and merges row-for-row by millis. 2001 is non-leap, so drop
                // 29 Feb; this also wraps a source period that crosses a year boundary onto a single 2001 year.
                if (sourceDate.monthValue == 2 && sourceDate.dayOfMonth == 29) return@forEach
                val mapped = LocalDateTime.of(2001, sourceDate.monthValue, sourceDate.dayOfMonth, hh, mm)
                val pd = PanelData()
                pd.panelID = panelId
                pd.date = mapped.format(dateFmt)
                pd.minute = row.minute
                pd.mod = hh * 60 + mm
                pd.dow = mapped.dayOfWeek.value
                pd.do2001 = mapped.dayOfYear
                pd.millisSinceEpoch = SimTime.toEpochMillis(mapped, ZoneOffset.UTC)
                pd.pv = row.pv
                out.add(pd)
            }
            if (out.isNotEmpty()) repo.savePanelData(out)
            Log.i(TAG, "Saved ${out.size} PanelData rows for panelId=$panelId")

            val totalKwh = out.sumOf { it.pv }
            completeNotification(mgr, builder, notificationId,
                "Done · ${"%.0f".format(totalKwh)} kWh saved", success = true)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            completeNotification(mgr, builder, notificationId,
                "Failed: ${e.message ?: "unknown error"}", success = false)
            return Result.retry()
        }
    }

    private fun baseNotification(panelLabel: String): NotificationCompat.Builder {
        val title = if (panelLabel.isBlank()) applicationContext.getString(R.string.panel_source_notification_title)
                    else applicationContext.getString(R.string.panel_source_notification_title) + " · " + panelLabel
        return NotificationCompat.Builder(applicationContext, MainActivity.CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_baseline_save_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
    }

    private fun updateProgress(
        mgr: NotificationManagerCompat,
        builder: NotificationCompat.Builder,
        notificationId: Int,
        text: String,
        progress: Int
    ) {
        Log.i(TAG, "Progress $progress%: $text")
        builder.setContentText(text).setProgress(100, progress, false)
        notifySafely(mgr, notificationId, builder)
    }

    private fun completeNotification(
        mgr: NotificationManagerCompat,
        builder: NotificationCompat.Builder,
        notificationId: Int,
        text: String,
        success: Boolean
    ) {
        Log.i(TAG, "Complete (success=$success): $text")
        builder.setContentText(text)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setTimeoutAfter(if (success) 15_000L else 60_000L)
        notifySafely(mgr, notificationId, builder)
    }

    private fun notifySafely(
        mgr: NotificationManagerCompat,
        notificationId: Int,
        builder: NotificationCompat.Builder
    ) {
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Skipping notify(id=$notificationId) — POST_NOTIFICATIONS denied")
            return
        }
        try {
            mgr.notify(notificationId, builder.build())
            Log.d(TAG, "notify(id=$notificationId) posted")
        } catch (t: Throwable) {
            Log.e(TAG, "notify(id=$notificationId) threw", t)
        }
    }

    private fun friendlyRange(from: String, to: String): String = "$from → $to"

    companion object {
        const val TAG = "PanelSrcFetch"
        private const val NOTIFICATION_ID_BASE = 0x73_70_00_00  // arbitrary, avoid clash

        const val KEY_PANEL_ID     = "panelId"
        const val KEY_SYS_SN       = "sysSn"
        const val KEY_FROM         = "from"
        const val KEY_TO           = "to"
        const val KEY_SOURCE_KWP   = "sourceKwp"
        const val KEY_TARGET_KWP   = "targetKwp"
        const val KEY_USE_AZIMUTH  = "useAzimuth"
        const val KEY_SOURCE_AZ    = "sourceAz"
        const val KEY_TARGET_AZ    = "targetAz"
        const val KEY_LAT          = "lat"
        const val KEY_LON          = "lon"
        const val KEY_TILT         = "tilt"
        const val KEY_PANEL_LABEL  = "panelLabel"

        fun enqueue(
            context: Context,
            panelId: Long,
            sysSn: String,
            from: String,
            to: String,
            sourceKwp: Double,
            targetKwp: Double,
            useAzimuthFactor: Boolean,
            sourceAzDeg: Double,
            targetAzDeg: Double,
            lat: Double,
            lon: Double,
            tiltDeg: Double,
            panelLabel: String
        ) {
            val data = Data.Builder()
                .putLong(KEY_PANEL_ID, panelId)
                .putString(KEY_SYS_SN, sysSn)
                .putString(KEY_FROM, from)
                .putString(KEY_TO, to)
                .putDouble(KEY_SOURCE_KWP, sourceKwp)
                .putDouble(KEY_TARGET_KWP, targetKwp)
                .putBoolean(KEY_USE_AZIMUTH, useAzimuthFactor)
                .putDouble(KEY_SOURCE_AZ, sourceAzDeg)
                .putDouble(KEY_TARGET_AZ, targetAzDeg)
                .putDouble(KEY_LAT, lat)
                .putDouble(KEY_LON, lon)
                .putDouble(KEY_TILT, tiltDeg)
                .putString(KEY_PANEL_LABEL, panelLabel)
                .build()
            val request = OneTimeWorkRequestBuilder<PanelSourceFetchWorker>()
                .addTag("panel_source_$panelId")
                .setInputData(data)
                .build()
            Log.i(TAG, "enqueue panelId=$panelId sysSn='$sysSn' $from→$to " +
                "useAz=$useAzimuthFactor srcAz=$sourceAzDeg→tgtAz=$targetAzDeg " +
                "srcKwp=$sourceKwp tgtKwp=$targetKwp")
            WorkManager.getInstance(context)
                .enqueueUniqueWork("PANEL_SOURCE_$panelId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
