package com.tfcode.comparetout.ui2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.importers.CredentialStore
import com.tfcode.comparetout.importers.alphaess.AlphaESSMigrationWorker
import com.tfcode.comparetout.importers.alphaess.CatchUpWorker
import com.tfcode.comparetout.importers.alphaess.DailyWorker
import com.tfcode.comparetout.importers.alphaess.ExportWorker
import com.tfcode.comparetout.importers.alphaess.ImportWorker
import com.tfcode.comparetout.importers.alphaess.OpenAlphaESSClient
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse
import com.tfcode.comparetout.importers.esbn.ESBNCatchUpWorker
import com.tfcode.comparetout.importers.esbn.ESBNExportWorker
import com.tfcode.comparetout.importers.esbn.ESBNHDFClient
import com.tfcode.comparetout.importers.esbn.ESBNImportWorker
import com.tfcode.comparetout.importers.esbn.responses.ESBNAuthException
import com.tfcode.comparetout.importers.esbn.responses.ESBNVerificationException
import com.tfcode.comparetout.importers.homeassistant.DeviceSensor
import com.tfcode.comparetout.importers.homeassistant.EnergySensors
import com.tfcode.comparetout.importers.homeassistant.HACatchupWorker
import com.tfcode.comparetout.importers.octopus.OctopusCatchUpWorker
import com.tfcode.comparetout.importers.octopus.OctopusCsvImportWorker
import com.tfcode.comparetout.importers.octopus.OctopusRestClient
import com.tfcode.comparetout.importers.octopus.OctopusSystem
import com.tfcode.comparetout.importers.fusionsolar.FusionSolarAuthException
import com.tfcode.comparetout.importers.fusionsolar.FusionSolarCaptchaRequiredException
import com.tfcode.comparetout.importers.fusionsolar.FusionSolarCatchUpWorker
import com.tfcode.comparetout.importers.fusionsolar.FusionSolarClient
import com.tfcode.comparetout.importers.fusionsolar.FusionSolarDataMassager
import com.tfcode.comparetout.importers.solis.SolisCatchUpWorker
import com.tfcode.comparetout.importers.solis.SolisCloudAuthException
import com.tfcode.comparetout.importers.solis.SolisCloudClient
import com.tfcode.comparetout.importers.solis.SolisCloudClockSkewException
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformMeta
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.scenario.HeatPumpWeatherCache
import com.tfcode.comparetout.dynamic.DynamicPriceCache
import com.tfcode.comparetout.dynamic.SemopxRateSource
import com.tfcode.comparetout.scenario.PvgisCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/*
 * Data-source management UI models — extracted verbatim from
 * UI2DataSourceManagementViewModel.kt (mega-refactor B6a). Imports inherited;
 * unused are cosmetic.
 */

/**
 * One SolisCloud plant from the credential probe, persisted as JSON under
 * [SOLIS_SYSTEM_LIST_KEY]. The id is the sysSn minus the "Solis-" prefix
 * (String — 19-digit ids exceed double precision); [money] is the plant's
 * currency code, echoed into stationDay requests.
 */
data class SolisStation(
    val id: String,
    val name: String,
    val money: String?
)

/**
 * One FusionSolar plant from the credential probe, persisted as JSON under
 * [FUSIONSOLAR_SYSTEM_LIST_KEY]. [dn] is the raw portal identifier
 * ("NE=…") every data call takes; [sysSn] is the storage namespace
 * ("FusionSolar-…", dn with the NE= prefix stripped).
 */
data class FusionSolarStation(
    val dn: String,
    val name: String,
    val sysSn: String
)

/** One configured system within a source — SN/MPRN + the date range we have. */
data class ManagedSystem(
    val sysSn: String,
    val dateRange: String?,
    val startDate: String?,   // raw yyyy-MM-dd, for date-picker bounds
    val endDate: String?,
    val scheduled: Boolean,
    val fetching: Boolean = false,
    val progress: String? = null
)

/**
 * Live snapshot of WorkManager state for one system. [running] covers the
 * one-shot catch-up tag (`sysSn`); [scheduled] covers the periodic daily
 * tag (`sysSn + "daily"`). Together they decide what the row should say
 * and which button to show.
 */
data class FetchStatus(
    val running: Boolean,
    val scheduled: Boolean,
    val progress: String?
)

/** Top-level state for one of the three accordions. */
data class SourceState(
    val importer: ComparisonUIViewModel.Importer,
    val credentialsConfigured: Boolean,
    val credentialsKnownGood: Boolean,
    val systems: List<ManagedSystem>,
    val selectedSn: String?
)

/**
 * One cached weather/PV dataset the user can inspect or delete. For PVGIS this
 * is one fetched panel's PV series (the cache is per-panel `PanelData` in the
 * DB — the UI2 fetch worker writes straight to the DB, not to files); [id] is
 * the panelID. For CDS it is one downloaded ERA5 CSV (one per location+period)
 * in [HeatPumpWeatherCache.CACHE_DIR]; [id] is the file name.
 */
data class WeatherCacheEntry(
    val id: String,
    val name: String,
    val detail: String?
)

/**
 * State for a weather/PV data source (PVGIS, CDS). [entries] are the cached
 * datasets (per-panel for PVGIS). [credentialsConfigured] is always true for
 * PVGIS (anonymous) and `false` until set for CDS; [credentialsKnownGood]
 * stays false until a real fetch validates them (no probe in Phase 5.5).
 */
data class WeatherSourceState(
    val entries: List<WeatherCacheEntry>,
    val credentialsConfigured: Boolean = false,
    val credentialsKnownGood: Boolean = false
)

/** One "Individual devices" entry, editable in the HA sensors accordion. */
data class HADeviceRow(
    val statId: String,
    val label: String,
    val role: String,   // DeviceSensor.Role name; "OTHER" = ignore
    val adjust: Boolean // remove this device's energy from the load at import
)

/** Discovered HA energy sensors for the sensors accordion. */
data class HASensorSnapshot(
    val grid: List<String>,
    val gridExports: List<String>,
    val solar: List<String>,
    val batteries: List<Pair<String?, String?>>, // (charging, discharging)
    val devices: List<HADeviceRow> = emptyList(),
    val serverRange: String? = null // "start ↔ end" the HA recorder holds; null until probed
)

/**
 * UI state for the AlphaESS add-inverter (bind SN) flow
 * (plans/source/alpha.md §3). One value describes what the sheet shows:
 * [busy] while a POST is in flight; [codeRequested] once getVerificationCode
 * succeeded WITHOUT an in-band code (email path — show the code field);
 * [boundSn] when the bind landed, with [alreadyBound] for the 6003
 * "you have bound this SN" outcome (treated as success). [checkCodeError]
 * (6004) and [rateLimited] (6053) are the actionable failures.
 */
data class AlphaBindUiState(
    val busy: Boolean = false,
    val codeRequested: Boolean = false,
    val boundSn: String? = null,
    val alreadyBound: Boolean = false,
    val checkCodeError: Boolean = false,
    val rateLimited: Boolean = false,
    val error: String? = null
)

/** One-shot user feedback (snackbar-style). */
