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
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
            val localZone = ZoneOffset.systemDefault()

            for (pp in pvGISData.hourlies.hourlies) {
                val saharaTime = LocalDateTime.parse(pp.time, pvGisFormat)
                var active = saharaTime.atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(localZone)
                    .toLocalDateTime()
                    .minusHours(1)
                var shift = false
                repeat(12) {
                    if (active.year != saharaTime.year) {
                        active = active.plusYears(1)
                        shift = true
                    }
                    val row = PanelData()
                    row.panelID = panelID
                    row.date = active.format(dateFormat)
                    row.minute = active.format(minFormat)
                    row.mod = active.hour * 60 + active.minute
                    row.dow = active.dayOfWeek.value
                    row.do2001 = active.dayOfYear
                    row.pv = (pp.gi / 12.0 / MAGIC_NUMBER) * panel.panelCount * panel.panelkWp
                    panelDataList.add(row)
                    if (shift) { active = active.minusYears(1); shift = false }
                    active = active.plusMinutes(5)
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
