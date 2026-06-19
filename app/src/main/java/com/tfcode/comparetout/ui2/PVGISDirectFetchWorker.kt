package com.tfcode.comparetout.ui2

import android.app.Application
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.scenario.pgvis.PvGISData
import com.tfcode.comparetout.model.scenario.PanelData
import com.tfcode.comparetout.scenario.sim.SimTime
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class PVGISDirectFetchWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val panelID = inputData.getLong("panelID", 0L)
        if (panelID == 0L) return Result.failure()

        val repository = ToutcRepository(applicationContext as Application)
        val panel = repository.getPanelForID(panelID) ?: return Result.failure()

        val df = DecimalFormat("#.000")
        val lat = df.format(panel.latitude)
        val lon = df.format(panel.longitude)
        var az = panel.azimuth
        if (az > 180) az = 360 - az

        val url = U1 + lat + U2 + lon + U3 + panel.slope + U4 + az +
            U5 + panel.slope + U6 + az

        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val pvGISData = Gson().fromJson(json, PvGISData::class.java)
            val panelDataList = ArrayList<PanelData>()
            val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val minFormat = DateTimeFormatter.ofPattern("HH:mm")
            val pvGisFormat = DateTimeFormatter.ofPattern("yyyyMMdd:HHmm")

            for (pp in pvGISData.hourlies.hourlies) {
                // PVGIS timestamps are UTC (SARAH/ERA5 per the PVGIS docs) but carry a native minute offset
                // (PVGIS-SARAH2 stamps the hourly value at :11 past the hour). Truncate to the top of the hour
                // BEFORE expanding into the twelve :00,:05,…,:55 slots so the PV rows sit on EXACTLY the same
                // :00-based 2001 grid as the load: the sim merges PV onto the load by UTC millis, and an off-grid
                // stamp (:11,:16,…) lands on instants the load never has, silently dropping all PV. Then remap
                // onto the synthetic 2001 grid (keep month/day/hour). Mirrors PVGISLoader.mapHourlyTo2001Rows.
                val utc = LocalDateTime.parse(pp.time, pvGisFormat).truncatedTo(ChronoUnit.HOURS)
                val pvPerInterval = (pp.gi / 12.0 / MAGIC_NUMBER) * panel.panelCount * panel.panelkWp
                for (i in 0 until 12) {
                    val slot = utc.plusMinutes(5L * i)
                    // 2001 is non-leap: drop Feb 29 so the PV row count stays equal to the load's 105120.
                    if (slot.monthValue == 2 && slot.dayOfMonth == 29) continue
                    val mapped = slot.withYear(2001)
                    val row = PanelData()
                    row.panelID = panelID
                    row.date = mapped.format(dateFormat)
                    row.minute = mapped.format(minFormat)
                    row.mod = mapped.hour * 60 + mapped.minute
                    row.dow = mapped.dayOfWeek.value
                    row.do2001 = mapped.dayOfYear
                    row.millisSinceEpoch = SimTime.toEpochMillis(mapped, ZoneOffset.UTC)
                    row.pv = pvPerInterval
                    panelDataList.add(row)
                }
            }

            repository.savePanelData(panelDataList)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val MAGIC_NUMBER = 919821.0
        private const val U1 = "https://re.jrc.ec.europa.eu/api/v5_2/seriescalc?lat="
        private const val U2 = "&lon="
        private const val U3 = "&raddatabase=PVGIS-SARAH2&browser=1&outputformat=json" +
            "&userhorizon=&usehorizon=1&angle="
        private const val U4 = "&aspect="
        private const val U5 = "&startyear=2019&endyear=2019&mountingplace=" +
            "&optimalinclination=0&optimalangles=0&js=1" +
            "&select_database_hourly=PVGIS-SARAH2&hstartyear=2019&hendyear=2019" +
            "&trackingtype=0&hourlyangle="
        private const val U6 = "&hourlyaspect="

        fun enqueue(context: Context, panelId: Long) {
            val data = Data.Builder().putLong("panelID", panelId).build()
            val request = OneTimeWorkRequestBuilder<PVGISDirectFetchWorker>()
                .addTag("pvgis_direct_$panelId")
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("PVGIS_$panelId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
