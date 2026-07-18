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

// ──────────────────────────────────────────────────────────────────────────
// UI2 Data Source Management ViewModel
//
// Wraps the legacy DataStore keys + WorkManager triggers so the UI2 Compose
// screen can present a clean accordion-per-source surface without dragging
// in the Fragment lifecycle that the legacy ImportOverviewFragment uses.
//
// Every preference key here matches the one the legacy fragment writes —
// the two UIs share the same persisted state, so switching between them
// never loses configuration.
// ──────────────────────────────────────────────────────────────────────────

private const val ALPHA_APP_ID_KEY = "app_id"
private const val ALPHA_APP_SECRET_KEY = "app_secret"
private const val ALPHA_GOOD_KEY = "cred_good"
private const val ALPHA_SYSTEM_LIST_KEY = "system_list"
private const val ALPHA_SELECTED_KEY = "system_previously_selected"

private const val HA_HOST_KEY = "ha_host"
private const val HA_TOKEN_KEY = "ha_token"
private const val HA_GOOD_KEY = "ha_cred_good"
private const val HA_SYSTEM_LIST_KEY = "ha_system_list"
private const val HA_SELECTED_KEY = "ha_system_previously_selected"
private const val HA_SENSORS_KEY = "ha_sensors"
// "start ↔ end" of the statistics the HA server holds — refreshed by every
// (re)discovery; UI2-only (no legacy key to mirror).
private const val HA_SERVER_RANGE_KEY = "ha_server_range"

// The credential keys the legacy ESBN dialog wrote (ImportESBNOverview) — the
// UI2 experimental cloud-sync strip reuses them so CredentialStore.Source.ESBN
// resolves state saved by any app version.
private const val ESBN_USER_KEY = "esbn_user_id"
private const val ESBN_PASSWORD_KEY = "esbn_password"
private const val ESBN_GOOD_KEY = "esbn_cred_good"
private const val ESBN_SYSTEM_LIST_KEY = "esbn_system_list"
private const val ESBN_SELECTED_KEY = "esbn_system_previously_selected"

private const val OCTOPUS_ACCOUNT_KEY = "octopus_account"
private const val OCTOPUS_API_KEY_KEY = "octopus_api_key"
private const val OCTOPUS_GOOD_KEY = "octopus_cred_good"
private const val OCTOPUS_SYSTEM_LIST_KEY = "octopus_system_list"
private const val OCTOPUS_SELECTED_KEY = "octopus_system_previously_selected"

private const val SOLIS_KEY_ID_KEY = "solis_key_id"
private const val SOLIS_SECRET_KEY = "solis_secret"
private const val SOLIS_GOOD_KEY = "solis_cred_good"
private const val SOLIS_SYSTEM_LIST_KEY = "solis_station_list"
private const val SOLIS_SELECTED_KEY = "solis_selected"

// Weather/PV "data sources" (Phase 5.5). PVGIS is anonymous (no credentials);
// CDS stores an encrypted Personal Access Token mirroring AlphaESS/HA. The
// PVGIS "cache" is the per-panel `PanelData` already in the DB (the UI2 fetch
// worker writes straight to the DB), so there is no file/table to manage here
// beyond what `removeOldPanelData` already deletes.
private const val CDS_URL_KEY = "cds_url"
private const val CDS_KEY_KEY = "cds_key"
private const val CDS_GOOD_KEY = "cds_cred_good"
/** Default CDS API endpoint, prefilled in the credential dialog (user can override). */
const val CDS_DEFAULT_URL = "https://cds.climate.copernicus.eu/api"

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
data class Toast(val message: String, val tag: Long = System.nanoTime())

@HiltViewModel
class UI2DataSourceManagementViewModel @Inject constructor(
    application: Application,
    private val repository: ToutcRepository
) : AndroidViewModel(application) {

    private val app: TOUTCApplication
        get() = getApplication<Application>() as TOUTCApplication
    private val wm: WorkManager
        get() = WorkManager.getInstance(getApplication())
    private val gson = Gson()
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val _alpha = MutableLiveData<SourceState>()
    val alpha: LiveData<SourceState> = _alpha
    private val _ha = MutableLiveData<SourceState>()
    val ha: LiveData<SourceState> = _ha
    private val _esbn = MutableLiveData<SourceState>()
    val esbn: LiveData<SourceState> = _esbn
    private val _octopus = MutableLiveData<SourceState>()
    val octopus: LiveData<SourceState> = _octopus
    private val _solis = MutableLiveData<SourceState>()
    val solis: LiveData<SourceState> = _solis
    private val _haSensors = MutableLiveData<HASensorSnapshot?>()
    val haSensors: LiveData<HASensorSnapshot?> = _haSensors
    private val _pvgis = MutableLiveData<WeatherSourceState>()
    val pvgis: LiveData<WeatherSourceState> = _pvgis
    private val _cds = MutableLiveData<WeatherSourceState>()
    val cds: LiveData<WeatherSourceState> = _cds
    private val _prices = MutableLiveData<WeatherSourceState>()
    val prices: LiveData<WeatherSourceState> = _prices
    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy
    private val _toast = MutableLiveData<Toast?>()
    val toast: LiveData<Toast?> = _toast

    /**
     * Live snapshot of the per-SN AlphaESS transform stamps. The UI compares
     * `(meta.transformVersion ?: 1) < AlphaESSTransformMeta.TRANSFORM_VERSION_CURRENT`
     * to decide whether to surface the Migrate button for a given system.
     * SNs missing from the list are pre-v2 (legacy) and treated as stale.
     */
    val alphaTransformMeta: LiveData<List<AlphaESSTransformMeta>> =
        repository.getAllAlphaESSTransformMetaLive()

    // ── WorkManager observer plumbing ──────────────────────────────────
    //
    // For each known SN we want a live view of two tags: the one-shot
    // catch-up (tagged with the SN itself, includes ImportWorker file imports
    // too) and the daily periodic (tag = SN + "daily"). The activity sees a
    // single _fetchStatus map keyed by SN; SystemRow merges that into the
    // ManagedSystem row state.
    //
    // We use observeForever because we have no LifecycleOwner here — the
    // observers are detached in onCleared(). Don't introduce a leak path
    // here without revisiting that.
    private val _fetchStatus = MutableLiveData<Map<String, FetchStatus>>(emptyMap())
    val fetchStatus: LiveData<Map<String, FetchStatus>> = _fetchStatus
    private val onceObservers = mutableMapOf<String, Observer<List<WorkInfo>>>()
    private val dailyObservers = mutableMapOf<String, Observer<List<WorkInfo>>>()
    private val onceData = mutableMapOf<String, List<WorkInfo>>()
    private val dailyData = mutableMapOf<String, List<WorkInfo>>()

    // The PVGIS cache is the on-disk pvgis-cache dir (PvgisCache). We can't observe files, but a completed
    // fetch writes the cache file AND saves PanelData, so observing the (Room LiveData) PV summary is a cheap
    // change-signal: when it fires we rebuild the cache-file list so new entries appear without a manual
    // refresh. The summary VALUE is ignored. Detached in onCleared().
    private val panelSummaryObserver = Observer<List<PanelPVSummary>> {
        rebuildPvgisFromCache()
    }

    init {
        refresh()
        repository.panelDataSummary.observeForever(panelSummaryObserver)
    }

    /** Re-read every persisted source state from DataStore + DB date ranges. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val a = buildAlphaState()
            val h = buildHAState()
            val e = buildEsbnState()
            val o = buildOctopusState()
            val s = buildSolisState()
            _alpha.postValue(a)
            _ha.postValue(h)
            _esbn.postValue(e)
            _octopus.postValue(o)
            _solis.postValue(s)
            _haSensors.postValue(readHASensors())
            // PVGIS state is driven by the panelDataSummary observer, not here.
            _cds.postValue(buildCdsState())
            _prices.postValue(buildPriceCacheState())
            // (Re-)attach WorkManager observers for the union of all SNs
            // so the live "fetching" indicator stays accurate as systems
            // appear and disappear from the lists.
            val sns = (a.systems + h.systems + e.systems + o.systems + s.systems)
                .map { it.sysSn }.toSet()
            withContext(Dispatchers.Main) { syncWorkObservers(sns) }
        }
    }

    private fun syncWorkObservers(sysSns: Set<String>) {
        val toAdd = sysSns - onceObservers.keys
        val toRemove = onceObservers.keys - sysSns
        for (sn in toAdd) attachWorkObserver(sn)
        for (sn in toRemove) detachWorkObserver(sn)
        emitFetchStatus()
    }

    private fun attachWorkObserver(sysSn: String) {
        val onceObs = Observer<List<WorkInfo>> {
            onceData[sysSn] = it
            emitFetchStatus()
        }
        val dailyObs = Observer<List<WorkInfo>> {
            dailyData[sysSn] = it
            emitFetchStatus()
        }
        wm.getWorkInfosByTagLiveData(sysSn).observeForever(onceObs)
        wm.getWorkInfosByTagLiveData(sysSn + "daily").observeForever(dailyObs)
        onceObservers[sysSn] = onceObs
        dailyObservers[sysSn] = dailyObs
    }

    private fun detachWorkObserver(sysSn: String) {
        onceObservers.remove(sysSn)?.let {
            wm.getWorkInfosByTagLiveData(sysSn).removeObserver(it)
        }
        dailyObservers.remove(sysSn)?.let {
            wm.getWorkInfosByTagLiveData(sysSn + "daily").removeObserver(it)
        }
        onceData.remove(sysSn)
        dailyData.remove(sysSn)
    }

    /** Recompute the [fetchStatus] map from the latest WorkInfos. */
    private fun emitFetchStatus() {
        val map = HashMap<String, FetchStatus>(onceObservers.size)
        for (sn in onceObservers.keys) {
            val once = onceData[sn].orEmpty()
            val daily = dailyData[sn].orEmpty()
            val running = once.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            val scheduled = daily.any { !it.state.isFinished }
            // The legacy CatchUpWorker publishes progress under a "PROGRESS"
            // data key. If present, surface the first line as a short status.
            val progress = once.firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?.progress?.getString(PROGRESS_KEY)
            map[sn] = FetchStatus(running, scheduled, progress)
        }
        _fetchStatus.value = map
    }

    override fun onCleared() {
        super.onCleared()
        val sns = onceObservers.keys.toList()
        for (sn in sns) detachWorkObserver(sn)
        repository.panelDataSummary.removeObserver(panelSummaryObserver)
    }

    // ── AlphaESS ────────────────────────────────────────────────────────

    private fun buildAlphaState(): SourceState {
        val rawList = app.getStringValueFromDataStore(ALPHA_SYSTEM_LIST_KEY)
        val sns = parseAlphaSystemList(rawList)
        val good = app.getStringValueFromDataStore(ALPHA_GOOD_KEY) != "False"
        val configured = app.getStringValueFromDataStore(ALPHA_APP_ID_KEY).orEmpty().isNotEmpty()
        val selected = app.getStringValueFromDataStore(ALPHA_SELECTED_KEY)?.ifEmpty { null }
        return SourceState(
            importer = ComparisonUIViewModel.Importer.ALPHAESS,
            credentialsConfigured = configured,
            credentialsKnownGood = configured && good,
            systems = sns.map { sn -> systemRow(sn) },
            selectedSn = selected
        )
    }

    private fun parseAlphaSystemList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val parsed = gson.fromJson(raw, GetEssListResponse::class.java)
            parsed?.data?.map { it.sysSn }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * Update AlphaESS credentials. Encrypts before storing — same scheme as
     * legacy — and immediately re-queries the cloud for the list of SNs so
     * the UI shows them right after the user hits Save.
     */
    fun setAlphaCredentials(appId: String, appSecret: String) {
        if (appId.isBlank() || appSecret.isBlank()) {
            _toast.postValue(Toast("AppID and AppSecret are required"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _busy.postValue(true)
            try {
                val encId = TOUTCApplication.encryptString(appId)
                val encSecret = TOUTCApplication.encryptString(appSecret)
                app.putStringValueIntoDataStore(ALPHA_APP_ID_KEY, encId)
                app.putStringValueIntoDataStore(ALPHA_APP_SECRET_KEY, encSecret)
                // Probe the AlphaESS cloud to refresh the SN list.
                val client = OpenAlphaESSClient(appId, appSecret)
                val response = client.essList
                if (response == null || response.data == null) {
                    app.putStringValueIntoDataStore(ALPHA_GOOD_KEY, "False")
                    _toast.postValue(Toast("Could not reach AlphaESS"))
                } else {
                    app.putStringValueIntoDataStore(ALPHA_GOOD_KEY, "True")
                    app.putStringValueIntoDataStore(ALPHA_SYSTEM_LIST_KEY, gson.toJson(response))
                    _toast.postValue(Toast("Credentials saved (${response.data.size} system(s))"))
                }
            } catch (t: Throwable) {
                app.putStringValueIntoDataStore(ALPHA_GOOD_KEY, "False")
                _toast.postValue(Toast(t.message ?: "AlphaESS error"))
            } finally {
                _alpha.postValue(buildAlphaState())
                _busy.postValue(false)
            }
        }
    }

    fun selectAlphaSystem(sysSn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.putStringValueIntoDataStore(ALPHA_SELECTED_KEY, sysSn)
            _alpha.postValue(buildAlphaState())
        }
    }

    /**
     * Start AlphaESS fetch (catch-up + periodic daily). Mirrors the legacy
     * `startWorkers(serialNumber, startDate)` flow, using the same tags so
     * the legacy screen sees the same in-flight work if the user opens it.
     */
    fun fetchAlpha(sysSn: String, startDate: LocalDateTime) {
        viewModelScope.launch(Dispatchers.IO) {
            // Presence check only — secrets never enter worker Data
            // (plans/source/security.md §1); the workers resolve them from the
            // encrypted DataStore via CredentialStore themselves.
            if (CredentialStore.get(app, CredentialStore.Source.ALPHAESS) == null) {
                _toast.postValue(Toast("Set AlphaESS credentials first"))
                return@launch
            }
            val date = dateFmt.format(toDate(startDate))
            val catchInput = Data.Builder()
                .putString(CatchUpWorker.KEY_SYSTEM_SN, sysSn)
                .putString(CatchUpWorker.KEY_START_DATE, date)
                .build()
            val catchReq = OneTimeWorkRequest.Builder(CatchUpWorker::class.java)
                .setInputData(catchInput)
                .addTag(sysSn)
                .build()
            wm.pruneWork()
            // APPEND_OR_REPLACE, not APPEND: APPEND chains onto the existing unique
            // work even when it FAILED/CANCELLED, so one bad run silently strands
            // every later fetch (no worker, no notification, no data).
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND_OR_REPLACE, catchReq).enqueue()

            val dailyInput = Data.Builder()
                .putString(DailyWorker.KEY_SYSTEM_SN, sysSn)
                .build()
            val initialDelayHours = (25 - LocalDateTime.now().hour).toLong()
            val dailyReq = PeriodicWorkRequest.Builder(DailyWorker::class.java, 1, TimeUnit.DAYS)
                .setInputData(dailyInput)
                .setInitialDelay(initialDelayHours, TimeUnit.HOURS)
                .addTag(sysSn + "daily")
                .build()
            wm.enqueueUniquePeriodicWork(sysSn + "daily", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, dailyReq)
            _toast.postValue(Toast("Fetch started for $sysSn"))
            _alpha.postValue(buildAlphaState())
        }
    }

    /**
     * Ingest a previously-exported AlphaESS JSON file for [sysSn]. Same
     * worker the legacy ImportAlphaActivity drives — tagged with the SN so
     * the row's status line picks up the live progress.
     */
    fun importAlphaFile(sysSn: String, uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val input = Data.Builder()
                .putString(ImportWorker.KEY_SYSTEM_SN, sysSn)
                .putString(ImportWorker.KEY_URI, uri)
                .build()
            val req = OneTimeWorkRequest.Builder(ImportWorker::class.java)
                .setInputData(input)
                .addTag(sysSn)
                .build()
            wm.pruneWork()
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND, req).enqueue()
            _toast.postValue(Toast("Importing $sysSn…"))
            _alpha.postValue(buildAlphaState())
        }
    }

    /**
     * Export AlphaESS raw energy + power for [sysSn] to a JSON file under
     * the user-picked folder tree URI. Same worker the legacy screen drives;
     * the file round-trips through [importAlphaFile].
     */
    fun exportAlpha(sysSn: String, folderUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val input = Data.Builder()
                .putString(ExportWorker.KEY_SYSTEM_SN, sysSn)
                .putString(ExportWorker.KEY_FOLDER, folderUri)
                .build()
            val req = OneTimeWorkRequest.Builder(ExportWorker::class.java)
                .setInputData(input)
                .addTag(sysSn + "Export")
                .build()
            wm.pruneWork()
            // Distinct unique-work name from "Fetch" so an export can sit
            // alongside a running fetch without one cancelling the other.
            wm.beginUniqueWork(sysSn + "Export",
                ExistingWorkPolicy.APPEND, req).enqueue()
            _toast.postValue(Toast("Exporting $sysSn…"))
        }
    }

    /**
     * Export the per-MPRN ESBN data as an HDF-compatible CSV under the
     * user-picked folder tree URI. The file round-trips through the
     * section-level "Import HDF file" affordance.
     */
    fun exportEsbn(mprn: String, folderUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val input = Data.Builder()
                .putString(ESBNExportWorker.KEY_SYSTEM_SN, mprn)
                .putString(ESBNExportWorker.KEY_FOLDER, folderUri)
                .build()
            val req = OneTimeWorkRequest.Builder(ESBNExportWorker::class.java)
                .setInputData(input)
                .addTag(mprn + "Export")
                .build()
            wm.pruneWork()
            wm.beginUniqueWork(mprn + "Export",
                ExistingWorkPolicy.APPEND, req).enqueue()
            _toast.postValue(Toast("Exporting $mprn…"))
        }
    }

    fun cancelFetch(sysSn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            wm.cancelAllWorkByTag(sysSn)
            wm.cancelAllWorkByTag(sysSn + "daily")
            _toast.postValue(Toast("Fetch cancelled for $sysSn"))
            _alpha.postValue(buildAlphaState())
            _ha.postValue(buildHAState())
            _esbn.postValue(buildEsbnState())
            _solis.postValue(buildSolisState())
        }
    }

    /**
     * Re-run the v2 AlphaESS transform across [sysSn]'s historical raw data.
     * Foregrounded worker (notification ID 12) with the publishProgress
     * pattern. KEEP policy means re-tapping the button while one is running
     * is a silent no-op. On completion the worker stamps
     * [AlphaESSTransformMeta.TRANSFORM_VERSION_CURRENT] which (via
     * [alphaTransformMeta]) removes the button from the row.
     */
    fun runMigration(sysSn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val input = Data.Builder()
                .putString(AlphaESSMigrationWorker.KEY_SYSTEM_SN, sysSn)
                .build()
            val req = OneTimeWorkRequest.Builder(AlphaESSMigrationWorker::class.java)
                .setInputData(input)
                .addTag(sysSn + "Migrate")
                .build()
            wm.pruneWork()
            wm.beginUniqueWork(sysSn + "Migrate", ExistingWorkPolicy.KEEP, req).enqueue()
            _toast.postValue(Toast("Migrating $sysSn…"))
        }
    }

    // ── AlphaESS add-inverter (bind SN) — plans/source/alpha.md ─────────
    //
    // The portal's own "Add SN" flow is broken by its email-confirmation
    // step, so the sheet drives the registration API directly:
    // getVerificationCode (which returns the code in-band and/or emails it)
    // then bindSn. Non-200 envelope codes come back from the client rather
    // than as exceptions because the sheet branches on them.

    private val _alphaBind = MutableLiveData(AlphaBindUiState())
    val alphaBind: LiveData<AlphaBindUiState> = _alphaBind

    /** Clear the bind-flow state (sheet dismissed or reopened). */
    fun alphaBindReset() {
        _alphaBind.postValue(AlphaBindUiState())
    }

    /**
     * Sheet step 2: validate SN + CheckCode and request a verification code.
     * When the code comes back in-band the email leg (the broken part of
     * the portal flow) is skipped entirely and the bind fires immediately.
     */
    fun alphaRequestVerification(sysSn: String, checkCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val creds = CredentialStore.get(app, CredentialStore.Source.ALPHAESS)
            if (creds == null) {
                _toast.postValue(Toast("Set AlphaESS credentials first"))
                return@launch
            }
            _alphaBind.postValue(AlphaBindUiState(busy = true))
            try {
                val client = OpenAlphaESSClient(creds.first, creds.second)
                val resp = client.getVerificationCode(sysSn.trim(), checkCode.trim())
                when (resp.code) {
                    200 -> {
                        val inBand = resp.inBandCode()
                        if (inBand != null) {
                            bindAndFinish(client, sysSn.trim(), inBand, codeRequested = false)
                        } else {
                            _alphaBind.postValue(AlphaBindUiState(codeRequested = true))
                        }
                    }
                    // 6002 (field-observed): the Open API only issues codes for
                    // SNs AlphaESS already links to the user's consumer account —
                    // bind attaches an owned SN to the appId, it can't claim an
                    // orphaned one. Registering via myAlpha/AlphaCloud comes first.
                    6002 -> _alphaBind.postValue(AlphaBindUiState(error = SN_NOT_LINKED))
                    6003 -> finishBound(sysSn.trim(), alreadyBound = true)
                    6004 -> _alphaBind.postValue(AlphaBindUiState(checkCodeError = true))
                    6053 -> _alphaBind.postValue(AlphaBindUiState(rateLimited = true))
                    else -> _alphaBind.postValue(AlphaBindUiState(
                        error = "AlphaESS error ${resp.code}: ${resp.msg}"))
                }
            } catch (t: Throwable) {
                _alphaBind.postValue(AlphaBindUiState(error = t.message ?: "AlphaESS error"))
            }
        }
    }

    /** Sheet step 2b→3: bind with the code the user typed from the email. */
    fun alphaBindWithCode(sysSn: String, code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val creds = CredentialStore.get(app, CredentialStore.Source.ALPHAESS)
            if (creds == null) {
                _toast.postValue(Toast("Set AlphaESS credentials first"))
                return@launch
            }
            _alphaBind.postValue(AlphaBindUiState(busy = true, codeRequested = true))
            try {
                bindAndFinish(OpenAlphaESSClient(creds.first, creds.second),
                    sysSn.trim(), code.trim(), codeRequested = true)
            } catch (t: Throwable) {
                _alphaBind.postValue(AlphaBindUiState(codeRequested = true,
                    error = t.message ?: "AlphaESS error"))
            }
        }
    }

    /**
     * bindSn + outcome mapping. [codeRequested] is carried through failure
     * states so the email-path code field stays visible for another try.
     */
    private fun bindAndFinish(client: OpenAlphaESSClient, sysSn: String, code: String,
                              codeRequested: Boolean) {
        val resp = client.bindSn(sysSn, code)
        when (resp.code) {
            200 -> finishBound(sysSn, alreadyBound = false)
            6002 -> _alphaBind.postValue(AlphaBindUiState(
                codeRequested = codeRequested, error = SN_NOT_LINKED))
            6003 -> finishBound(sysSn, alreadyBound = true)
            6053 -> _alphaBind.postValue(AlphaBindUiState(
                codeRequested = codeRequested, rateLimited = true))
            else -> _alphaBind.postValue(AlphaBindUiState(codeRequested = codeRequested,
                error = "AlphaESS error ${resp.code}: ${resp.msg}"))
        }
    }

    /**
     * Post-bind: re-probe getEssList (the existing credential-probe path)
     * so the new SN appears in the SystemList — everything downstream
     * (fetch, daily worker, wizard, Compare) behaves exactly as today.
     * The bind itself already succeeded, so a failed probe only means the
     * list refreshes on the next credential save/open instead.
     */
    private fun finishBound(sysSn: String, alreadyBound: Boolean) {
        runCatching {
            val creds = CredentialStore.get(app, CredentialStore.Source.ALPHAESS)
                ?: return@runCatching
            val response = OpenAlphaESSClient(creds.first, creds.second).essList
            if (response?.data != null) {
                app.putStringValueIntoDataStore(ALPHA_GOOD_KEY, "True")
                app.putStringValueIntoDataStore(ALPHA_SYSTEM_LIST_KEY, gson.toJson(response))
                _toast.postValue(Toast("${response.data.size} system(s) on the account"))
            }
        }
        _alphaBind.postValue(AlphaBindUiState(boundSn = sysSn, alreadyBound = alreadyBound))
        _alpha.postValue(buildAlphaState())
    }

    // ── Home Assistant ──────────────────────────────────────────────────

    private fun buildHAState(): SourceState {
        val raw = app.getStringValueFromDataStore(HA_SYSTEM_LIST_KEY)
        val parsed: List<String> = if (raw.isNullOrBlank()) emptyList()
        else runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        }.getOrDefault(emptyList())
        val good = app.getStringValueFromDataStore(HA_GOOD_KEY) != "False"
        val configured = app.getStringValueFromDataStore(HA_HOST_KEY).orEmpty().isNotEmpty()
        val selected = app.getStringValueFromDataStore(HA_SELECTED_KEY)?.ifEmpty { null }
        // The persisted HA list is only populated *after* a successful sensor
        // discovery. Before that, surface a single synthetic "HomeAssistant"
        // row whenever credentials exist so the user can still see Fetch /
        // Delete affordances. The actual fetch path validates that sensors
        // have been discovered and toasts otherwise.
        val sns = if (parsed.isEmpty() && configured) listOf("HomeAssistant") else parsed
        return SourceState(
            importer = ComparisonUIViewModel.Importer.HOME_ASSISTANT,
            credentialsConfigured = configured,
            credentialsKnownGood = configured && good,
            systems = sns.map { sn -> systemRow(sn) },
            selectedSn = selected
        )
    }

    private fun readHASensors(): HASensorSnapshot? {
        val raw = app.getStringValueFromDataStore(HA_SENSORS_KEY) ?: return null
        if (raw.isBlank() || raw == "{}") return null
        val parsed = runCatching { gson.fromJson(raw, EnergySensors::class.java) }
            .getOrNull() ?: return null
        return HASensorSnapshot(
            grid = parsed.gridImports.orEmpty(),
            gridExports = parsed.gridExports.orEmpty(),
            solar = parsed.solarGeneration.orEmpty(),
            batteries = parsed.batteries.orEmpty().map { it.batteryCharging to it.batteryDischarging },
            devices = parsed.devices.orEmpty().mapNotNull { d ->
                d.statId?.let { id ->
                    HADeviceRow(id, d.label ?: id, d.role?.name ?: "OTHER", d.adjust)
                }
            },
            serverRange = app.getStringValueFromDataStore(HA_SERVER_RANGE_KEY)?.ifBlank { null }
        )
    }

    /**
     * Persist one device's classification (role + adjust flag) into the stored
     * ha_sensors JSON. Takes effect on the next fetch — already-imported rows keep
     * the slices they were ingested with.
     */
    fun setHaDeviceClassification(statId: String, roleName: String, adjust: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val raw = app.getStringValueFromDataStore(HA_SENSORS_KEY) ?: return@launch
            val parsed = runCatching { gson.fromJson(raw, EnergySensors::class.java) }
                .getOrNull() ?: return@launch
            val device = parsed.devices.orEmpty().firstOrNull { it.statId == statId }
                ?: return@launch
            device.role = runCatching { DeviceSensor.Role.valueOf(roleName) }
                .getOrDefault(DeviceSensor.Role.OTHER)
            device.adjust = adjust
            app.putStringValueIntoDataStore(HA_SENSORS_KEY, gson.toJson(parsed))
            _haSensors.postValue(readHASensors())
        }
    }

    /**
     * Re-run the WebSocket discovery flow against the supplied host/token.
     * Stores credentials (encrypted) + the discovered sensor list. The "HomeAssistant"
     * pseudo-SN is added to the system list so the rest of the app can address it.
     */
    fun discoverHA(host: String, token: String) {
        if (host.isBlank() || token.isBlank()) {
            _toast.postValue(Toast("Host URL and token are required"))
            return
        }
        viewModelScope.launch {
            _busy.postValue(true)
            val outcome = withContext(Dispatchers.IO) { discoverHASensors(host, token) }
            withContext(Dispatchers.IO) {
                val encHost = TOUTCApplication.encryptString(host)
                val encToken = TOUTCApplication.encryptString(token)
                app.putStringValueIntoDataStore(HA_HOST_KEY, encHost)
                app.putStringValueIntoDataStore(HA_TOKEN_KEY, encToken)
                if (outcome.sensors != null) {
                    app.putStringValueIntoDataStore(HA_GOOD_KEY, "True")
                    // Re-discovery must not wipe device classifications the user already made
                    // (legacy sensor dialog): keep the prior DeviceSensor for re-discovered ids.
                    val prior = runCatching {
                        gson.fromJson(app.getStringValueFromDataStore(HA_SENSORS_KEY),
                            EnergySensors::class.java)
                    }.getOrNull()?.devices.orEmpty().associateBy { it.statId }
                    outcome.sensors.devices = outcome.sensors.devices.orEmpty()
                        .map { prior[it.statId] ?: it }
                    app.putStringValueIntoDataStore(HA_SENSORS_KEY, gson.toJson(outcome.sensors))
                    // Best-effort server range: keep the previous value when the probe
                    // came back empty rather than blanking a known-good answer.
                    if (outcome.serverStart != null && outcome.serverEnd != null) {
                        app.putStringValueIntoDataStore(HA_SERVER_RANGE_KEY,
                            "${outcome.serverStart} ↔ ${outcome.serverEnd}")
                    }
                    // Ensure the canonical HA system entry exists.
                    val existing = runCatching {
                        gson.fromJson<List<String>>(
                            app.getStringValueFromDataStore(HA_SYSTEM_LIST_KEY) ?: "[]",
                            object : TypeToken<List<String>>() {}.type
                        ) ?: emptyList()
                    }.getOrDefault(emptyList())
                    if ("HomeAssistant" !in existing) {
                        app.putStringValueIntoDataStore(
                            HA_SYSTEM_LIST_KEY,
                            gson.toJson(existing + "HomeAssistant")
                        )
                    }
                    _toast.postValue(Toast("HA connected · ${outcome.sensors.gridImports.orEmpty().size}↓/${outcome.sensors.gridExports.orEmpty().size}↑ grid, ${outcome.sensors.solarGeneration.orEmpty().size} solar, ${outcome.sensors.batteries.orEmpty().size} battery"))
                } else {
                    app.putStringValueIntoDataStore(HA_GOOD_KEY, "False")
                    _toast.postValue(Toast(outcome.error ?: "Discovery failed"))
                }
                _haSensors.postValue(readHASensors())
                _ha.postValue(buildHAState())
            }
            _busy.postValue(false)
        }
    }

    /**
     * Re-run sensor discovery (and the server-range probe) with the stored
     * credentials — the "Re-discover" button must not make the user retype a
     * long-lived token that is already saved. Falls back to a toast when the
     * stored credentials are missing or unreadable (edit via the credential strip).
     */
    fun rediscoverHA() {
        viewModelScope.launch(Dispatchers.IO) {
            val host = decryptOrNull(app.getStringValueFromDataStore(HA_HOST_KEY))
            val token = decryptOrNull(app.getStringValueFromDataStore(HA_TOKEN_KEY))
            if (host == null || token == null) {
                _toast.postValue(Toast("Set Home Assistant credentials first"))
                return@launch
            }
            discoverHA(host, token)
        }
    }

    /**
     * Start a single HA catch-up plus the daily refresh. Same worker class
     * (HACatchupWorker) the legacy screen schedules.
     */
    fun fetchHA(startDate: LocalDateTime) {
        viewModelScope.launch(Dispatchers.IO) {
            val sensorsRaw = app.getStringValueFromDataStore(HA_SENSORS_KEY)
            // Presence check only — secrets never enter worker Data
            // (plans/source/security.md §1); the workers resolve them from the
            // encrypted DataStore via CredentialStore themselves.
            if (CredentialStore.get(app, CredentialStore.Source.HOME_ASSISTANT) == null) {
                _toast.postValue(Toast("Set Home Assistant credentials first"))
                return@launch
            }
            if (sensorsRaw.isNullOrBlank()) {
                _toast.postValue(Toast("Discover sensors first"))
                return@launch
            }
            val date = dateFmt.format(toDate(startDate))
            val sysSn = "HomeAssistant"
            val catchInput = Data.Builder()
                .putString(HACatchupWorker.KEY_START_DATE, date)
                .putString(HACatchupWorker.KEY_SENSORS, sensorsRaw)
                .build()
            val catchReq = OneTimeWorkRequest.Builder(HACatchupWorker::class.java)
                .setInputData(catchInput)
                .addTag(sysSn)
                .build()
            wm.pruneWork()
            // APPEND_OR_REPLACE, not APPEND: APPEND chains onto the existing unique
            // work even when it FAILED/CANCELLED, so one bad run silently strands
            // every later fetch (no worker, no notification, no data).
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND_OR_REPLACE, catchReq).enqueue()

            val dailyInput = Data.Builder()
                .putString(HACatchupWorker.KEY_SENSORS, sensorsRaw)
                .build()
            val initialDelayHours = (25 - LocalDateTime.now().hour).toLong()
            val dailyReq = PeriodicWorkRequest.Builder(HACatchupWorker::class.java, 1, TimeUnit.DAYS)
                .setInputData(dailyInput)
                .setInitialDelay(initialDelayHours, TimeUnit.HOURS)
                .addTag(sysSn + "daily")
                .build()
            wm.enqueueUniquePeriodicWork(sysSn + "daily", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, dailyReq)
            _toast.postValue(Toast("Home Assistant fetch started"))
            _ha.postValue(buildHAState())
        }
    }

    // HA backfill moved to its own wizard — see UI2HaBackfillActivity /
    // UI2HaBackfillViewModel (plans/ha/design.md, Enhancement 2).

    // ── ESBN ────────────────────────────────────────────────────────────

    private fun buildEsbnState(): SourceState {
        val raw = app.getStringValueFromDataStore(ESBN_SYSTEM_LIST_KEY)
        val sns: List<String> = if (raw.isNullOrBlank()) emptyList()
        else runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        }.getOrDefault(emptyList())
        val good = app.getStringValueFromDataStore(ESBN_GOOD_KEY) != "False"
        val configured = app.getStringValueFromDataStore(ESBN_USER_KEY).orEmpty().isNotEmpty()
        val selected = app.getStringValueFromDataStore(ESBN_SELECTED_KEY)?.ifEmpty { null }
        return SourceState(
            importer = ComparisonUIViewModel.Importer.ESBNHDF,
            credentialsConfigured = configured,
            credentialsKnownGood = configured && good,
            systems = sns.map { sn -> systemRow(sn) },
            selectedSn = selected
        )
    }

    /**
     * Update ESB Networks credentials for the EXPERIMENTAL cloud sync
     * (plans/source/esbn.md). Encrypts before storing, then probes with one
     * real login + MPRN discovery. The probe IS a login — it spends 1 of the
     * ~2-logins-per-IP-per-day budget (§3), so it runs once on save and is
     * never repeated on screen entry. Auth, verification/rate-limit, and
     * network failures get distinct toasts because their fixes differ.
     */
    fun setEsbnCredentials(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _toast.postValue(Toast("Email and password are required"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _busy.postValue(true)
            try {
                app.putStringValueIntoDataStore(ESBN_USER_KEY,
                    TOUTCApplication.encryptString(username.trim()))
                app.putStringValueIntoDataStore(ESBN_PASSWORD_KEY,
                    TOUTCApplication.encryptString(password.trim()))
                val client = ESBNHDFClient(username.trim(), password.trim())
                val mprns = client.fetchMPRNs()
                if (mprns.isEmpty()) {
                    // Logged in, but the Outages scrape found nothing — keep the
                    // credentials marked good; the file fallback still works.
                    app.putStringValueIntoDataStore(ESBN_GOOD_KEY, "True")
                    _toast.postValue(Toast("Signed in, but no MPRN found — use 'Import HDF file'"))
                } else {
                    app.putStringValueIntoDataStore(ESBN_GOOD_KEY, "True")
                    // Merge with MPRNs already known from file imports.
                    val existing = buildEsbnState().systems.map { it.sysSn }
                    val merged = (existing + mprns).distinct()
                    app.putStringValueIntoDataStore(ESBN_SYSTEM_LIST_KEY, gson.toJson(merged))
                    if (merged.size == 1) {
                        app.putStringValueIntoDataStore(ESBN_SELECTED_KEY, merged[0])
                    }
                    _toast.postValue(Toast("Credentials saved (${mprns.size} MPRN(s))"))
                }
            } catch (e: ESBNVerificationException) {
                app.putStringValueIntoDataStore(ESBN_GOOD_KEY, "False")
                _toast.postValue(Toast(e.message ?: ESBNVerificationException.DEFAULT_MESSAGE))
            } catch (e: ESBNAuthException) {
                app.putStringValueIntoDataStore(ESBN_GOOD_KEY, "False")
                _toast.postValue(Toast("ESB Networks rejected the sign-in — check email and password"))
            } catch (t: Throwable) {
                app.putStringValueIntoDataStore(ESBN_GOOD_KEY, "False")
                _toast.postValue(Toast(t.message ?: "ESB Networks error"))
            } finally {
                _esbn.postValue(buildEsbnState())
                _busy.postValue(false)
            }
        }
    }

    fun selectEsbnSystem(mprn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.putStringValueIntoDataStore(ESBN_SELECTED_KEY, mprn)
            _esbn.postValue(buildEsbnState())
        }
    }

    /**
     * Start the experimental ESBN cloud fetch: one-shot catch-up now, plus a
     * WEEKLY periodic refresh (not daily — each run spends one login from
     * ESB's ~2-logins-per-IP-per-day budget, and HDF data is day-granular
     * history, so weekly staleness is fine; plans/source/esbn.md §3). No
     * start date: the HDF download is always the full history and stores are
     * idempotent.
     */
    fun fetchEsbn(mprn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Presence check only — secrets never enter worker Data
            // (plans/source/security.md §1); the worker resolves them from the
            // encrypted DataStore via CredentialStore itself.
            if (CredentialStore.get(app, CredentialStore.Source.ESBN) == null) {
                _toast.postValue(Toast("Set ESB Networks credentials first"))
                return@launch
            }
            val catchInput = Data.Builder()
                .putString(ESBNCatchUpWorker.KEY_SYSTEM_SN, mprn)
                .build()
            val catchReq = OneTimeWorkRequest.Builder(ESBNCatchUpWorker::class.java)
                .setInputData(catchInput)
                .addTag(mprn)
                .build()
            wm.pruneWork()
            // APPEND_OR_REPLACE, not APPEND: APPEND chains onto the existing unique
            // work even when it FAILED/CANCELLED, so one bad run silently strands
            // every later fetch (no worker, no notification, no data).
            wm.beginUniqueWork(mprn, ExistingWorkPolicy.APPEND_OR_REPLACE, catchReq).enqueue()

            // Weekly refresh under the unique name mprn + "weekly". The WORK TAG
            // stays mprn + "daily" — the shared scheduled-indicator observers,
            // isScheduled() and cancelFetch() all key off that tag; "daily" here
            // means "the periodic auto-sync", whatever its period.
            val weeklyInput = Data.Builder()
                .putString(ESBNCatchUpWorker.KEY_SYSTEM_SN, mprn)
                .build()
            val weeklyReq = PeriodicWorkRequest.Builder(ESBNCatchUpWorker::class.java, 7, TimeUnit.DAYS)
                .setInputData(weeklyInput)
                // ~25h so the first automatic run lands the day after setup —
                // never the same day as the catch-up login (budget headroom).
                .setInitialDelay((25 - LocalDateTime.now().hour).toLong(), TimeUnit.HOURS)
                .addTag(mprn + "daily")
                .build()
            wm.enqueueUniquePeriodicWork(mprn + "weekly",
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, weeklyReq)
            _toast.postValue(Toast("Fetch started for $mprn · auto-sync weekly"))
            _esbn.postValue(buildEsbnState())
        }
    }

    /**
     * Enqueue ESBN HDF file ingestion. The MPRN is read from the file itself
     * by ESBNImportWorker; the systemSn passed here is the legacy convention
     * of using "ESBN" as a placeholder tag so cancellation works the same way.
     */
    fun importEsbnFile(uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val tag = "ESBN"
            val input = Data.Builder()
                .putString(ESBNImportWorker.KEY_SYSTEM_SN, tag)
                .putString(ESBNImportWorker.KEY_URI, uri)
                .build()
            val req = OneTimeWorkRequest.Builder(ESBNImportWorker::class.java)
                .setInputData(input)
                .addTag(tag + "Import")
                .build()
            wm.pruneWork()
            wm.beginUniqueWork(tag, ExistingWorkPolicy.APPEND, req).enqueue()
            _toast.postValue(Toast("Importing HDF file…"))
            // Date ranges + system list are populated by the worker on completion;
            // refresh shortly after to pick them up.
            _esbn.postValue(buildEsbnState())
        }
    }

    // ── Octopus Energy ──────────────────────────────────────────────────

    private fun buildOctopusState(): SourceState {
        val raw = app.getStringValueFromDataStore(OCTOPUS_SYSTEM_LIST_KEY)
        val sns: List<String> = if (raw.isNullOrBlank()) emptyList()
        else runCatching {
            val parsed: List<OctopusSystem>? =
                gson.fromJson(raw, object : TypeToken<List<OctopusSystem>>() {}.type)
            parsed?.map { it.sysSn }.orEmpty()
        }.getOrDefault(emptyList())
        val good = app.getStringValueFromDataStore(OCTOPUS_GOOD_KEY) != "False"
        val configured = app.getStringValueFromDataStore(OCTOPUS_API_KEY_KEY).orEmpty().isNotEmpty()
        val selected = app.getStringValueFromDataStore(OCTOPUS_SELECTED_KEY)?.ifEmpty { null }
        return SourceState(
            importer = ComparisonUIViewModel.Importer.OCTOPUS,
            credentialsConfigured = configured,
            credentialsKnownGood = configured && good,
            systems = sns.map { sn -> systemRow(sn) },
            selectedSn = selected
        )
    }

    /**
     * Update Octopus credentials (account number + API key). Encrypts before
     * storing — same scheme as legacy — and immediately probes /accounts/ to
     * auto-discover the MPAN list.
     */
    fun setOctopusCredentials(account: String, apiKey: String) {
        if (account.isBlank() || apiKey.isBlank()) {
            _toast.postValue(Toast("Account number and API key are required"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _busy.postValue(true)
            try {
                val encAccount = TOUTCApplication.encryptString(account.trim())
                val encKey = TOUTCApplication.encryptString(apiKey.trim())
                app.putStringValueIntoDataStore(OCTOPUS_ACCOUNT_KEY, encAccount)
                app.putStringValueIntoDataStore(OCTOPUS_API_KEY_KEY, encKey)
                // Probe the account to discover meter points.
                val client = OctopusRestClient(apiKey.trim())
                val systems = OctopusSystem.fromAccount(client.getAccount(account.trim()))
                if (systems.isEmpty()) {
                    app.putStringValueIntoDataStore(OCTOPUS_GOOD_KEY, "False")
                    _toast.postValue(Toast("No electricity meter points found on $account"))
                } else {
                    app.putStringValueIntoDataStore(OCTOPUS_GOOD_KEY, "True")
                    app.putStringValueIntoDataStore(OCTOPUS_SYSTEM_LIST_KEY, gson.toJson(systems))
                    if (systems.size == 1) {
                        app.putStringValueIntoDataStore(OCTOPUS_SELECTED_KEY, systems[0].sysSn)
                    }
                    _toast.postValue(Toast("Credentials saved (${systems.size} meter point(s))"))
                }
            } catch (t: Throwable) {
                app.putStringValueIntoDataStore(OCTOPUS_GOOD_KEY, "False")
                _toast.postValue(Toast(t.message ?: "Octopus error"))
            } finally {
                _octopus.postValue(buildOctopusState())
                _busy.postValue(false)
            }
        }
    }

    fun selectOctopusSystem(sysSn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.putStringValueIntoDataStore(OCTOPUS_SELECTED_KEY, sysSn)
            _octopus.postValue(buildOctopusState())
        }
    }

    /**
     * Start Octopus fetch (catch-up + periodic daily). Mirrors the legacy
     * `startWorkers(serialNumber, startDate)` flow, using the same tags so
     * the legacy screen sees the same in-flight work if the user opens it.
     */
    fun fetchOctopus(sysSn: String, startDate: LocalDateTime) {
        viewModelScope.launch(Dispatchers.IO) {
            // Presence check only — secrets never enter worker Data
            // (plans/source/security.md §1); the workers resolve them from the
            // encrypted DataStore via CredentialStore themselves.
            if (CredentialStore.get(app, CredentialStore.Source.OCTOPUS) == null) {
                _toast.postValue(Toast("Set Octopus credentials first"))
                return@launch
            }
            val date = dateFmt.format(toDate(startDate))
            val catchInput = Data.Builder()
                .putString(OctopusCatchUpWorker.KEY_SYSTEM_SN, sysSn)
                .putString(OctopusCatchUpWorker.KEY_START_DATE, date)
                .build()
            val catchReq = OneTimeWorkRequest.Builder(OctopusCatchUpWorker::class.java)
                .setInputData(catchInput)
                .addTag(sysSn)
                .build()
            wm.pruneWork()
            // APPEND_OR_REPLACE, not APPEND: APPEND chains onto the existing unique
            // work even when it FAILED/CANCELLED, so one bad run silently strands
            // every later fetch (no worker, no notification, no data).
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND_OR_REPLACE, catchReq).enqueue()

            // Daily incremental sync: the same worker resumes from the latest
            // stored date when no start date is supplied.
            val dailyInput = Data.Builder()
                .putString(OctopusCatchUpWorker.KEY_SYSTEM_SN, sysSn)
                .build()
            val initialDelayHours = (25 - LocalDateTime.now().hour).toLong()
            val dailyReq = PeriodicWorkRequest.Builder(OctopusCatchUpWorker::class.java, 1, TimeUnit.DAYS)
                .setInputData(dailyInput)
                .setInitialDelay(initialDelayHours, TimeUnit.HOURS)
                .addTag(sysSn + "daily")
                .build()
            wm.enqueueUniquePeriodicWork(sysSn + "daily", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, dailyReq)
            _toast.postValue(Toast("Fetch started for $sysSn"))
            _octopus.postValue(buildOctopusState())
        }
    }

    /**
     * Ingest an Octopus consumption CSV (the dashboard download) for [sysSn] —
     * the offline fallback for users without an API key.
     */
    fun importOctopusFile(sysSn: String, uri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val input = Data.Builder()
                .putString(OctopusCsvImportWorker.KEY_SYSTEM_SN, sysSn)
                .putString(OctopusCsvImportWorker.KEY_URI, uri)
                .build()
            val req = OneTimeWorkRequest.Builder(OctopusCsvImportWorker::class.java)
                .setInputData(input)
                .addTag(sysSn + "Import")
                .build()
            wm.pruneWork()
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND, req).enqueue()
            _toast.postValue(Toast("Importing Octopus CSV…"))
            _octopus.postValue(buildOctopusState())
        }
    }

    // ── Solis Cloud ─────────────────────────────────────────────────────

    private fun parseSolisStations(raw: String?): List<SolisStation> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<SolisStation>>(raw,
                object : TypeToken<List<SolisStation>>() {}.type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun buildSolisState(): SourceState {
        val stations = parseSolisStations(app.getStringValueFromDataStore(SOLIS_SYSTEM_LIST_KEY))
        val good = app.getStringValueFromDataStore(SOLIS_GOOD_KEY) != "False"
        val configured = app.getStringValueFromDataStore(SOLIS_KEY_ID_KEY).orEmpty().isNotEmpty()
        val selected = app.getStringValueFromDataStore(SOLIS_SELECTED_KEY)?.ifEmpty { null }
        return SourceState(
            importer = ComparisonUIViewModel.Importer.SOLIS,
            credentialsConfigured = configured,
            credentialsKnownGood = configured && good,
            systems = stations.map { systemRow("Solis-" + it.id) },
            selectedSn = selected
        )
    }

    /**
     * Update SolisCloud credentials (API KeyID + secret from soliscloud.com →
     * API Management). Encrypts before storing and probes userStationList to
     * discover the plants — clock-skew and auth failures get distinct toasts
     * because their fixes differ (device clock vs re-entered key).
     */
    fun setSolisCredentials(keyId: String, secret: String) {
        if (keyId.isBlank() || secret.isBlank()) {
            _toast.postValue(Toast("API Key ID and secret are required"))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _busy.postValue(true)
            try {
                app.putStringValueIntoDataStore(SOLIS_KEY_ID_KEY,
                    TOUTCApplication.encryptString(keyId.trim()))
                app.putStringValueIntoDataStore(SOLIS_SECRET_KEY,
                    TOUTCApplication.encryptString(secret.trim()))
                val client = SolisCloudClient(keyId.trim(), secret.trim())
                val stations = client.stationList.mapNotNull { s ->
                    s.id?.let { SolisStation(it, s.stationName ?: it, s.money) }
                }
                if (stations.isEmpty()) {
                    app.putStringValueIntoDataStore(SOLIS_GOOD_KEY, "False")
                    _toast.postValue(Toast("No stations found on this SolisCloud account"))
                } else {
                    app.putStringValueIntoDataStore(SOLIS_GOOD_KEY, "True")
                    app.putStringValueIntoDataStore(SOLIS_SYSTEM_LIST_KEY, gson.toJson(stations))
                    if (stations.size == 1) {
                        app.putStringValueIntoDataStore(SOLIS_SELECTED_KEY,
                            "Solis-" + stations[0].id)
                    }
                    _toast.postValue(Toast("Credentials saved (${stations.size} station(s))"))
                }
            } catch (e: SolisCloudClockSkewException) {
                app.putStringValueIntoDataStore(SOLIS_GOOD_KEY, "False")
                _toast.postValue(Toast("SolisCloud rejected the request time — check the device clock"))
            } catch (e: SolisCloudAuthException) {
                app.putStringValueIntoDataStore(SOLIS_GOOD_KEY, "False")
                _toast.postValue(Toast("SolisCloud rejected the credentials — check Key ID and secret"))
            } catch (t: Throwable) {
                app.putStringValueIntoDataStore(SOLIS_GOOD_KEY, "False")
                _toast.postValue(Toast(t.message ?: "SolisCloud error"))
            } finally {
                _solis.postValue(buildSolisState())
                _busy.postValue(false)
            }
        }
    }

    fun selectSolisStation(sysSn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.putStringValueIntoDataStore(SOLIS_SELECTED_KEY, sysSn)
            _solis.postValue(buildSolisState())
        }
    }

    /**
     * Start Solis fetch (catch-up + periodic daily). Same tag conventions as
     * the other importers so the shared fetch indicator / cancel paths apply.
     * The workers carry EXPONENTIAL backoff criteria because SolisCloudClient
     * surfaces transient exhaustion as Result.retry().
     */
    fun fetchSolis(sysSn: String, startDate: LocalDateTime) {
        viewModelScope.launch(Dispatchers.IO) {
            // Presence check only — secrets never enter worker Data
            // (plans/source/security.md §1); the workers resolve them from the
            // encrypted DataStore via CredentialStore themselves.
            if (CredentialStore.get(app, CredentialStore.Source.SOLIS) == null) {
                _toast.postValue(Toast("Set SolisCloud credentials first"))
                return@launch
            }
            val station = parseSolisStations(app.getStringValueFromDataStore(SOLIS_SYSTEM_LIST_KEY))
                .firstOrNull { "Solis-" + it.id == sysSn }
            if (station == null) {
                _toast.postValue(Toast("Unknown Solis station $sysSn"))
                return@launch
            }
            val date = dateFmt.format(toDate(startDate))
            val catchInput = Data.Builder()
                .putString(SolisCatchUpWorker.KEY_STATION_ID, station.id)
                .putString(SolisCatchUpWorker.KEY_STATION_NAME, station.name)
                .putString(SolisCatchUpWorker.KEY_CURRENCY, station.money)
                .putString(SolisCatchUpWorker.KEY_START_DATE, date)
                .build()
            val catchReq = OneTimeWorkRequest.Builder(SolisCatchUpWorker::class.java)
                .setInputData(catchInput)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(sysSn)
                .build()
            wm.pruneWork()
            // APPEND_OR_REPLACE, not APPEND: APPEND chains onto the existing unique
            // work even when it FAILED/CANCELLED, so one bad run silently strands
            // every later fetch (no worker, no notification, no data).
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND_OR_REPLACE, catchReq).enqueue()

            // Daily incremental sync: no start date ⇒ the worker fetches yesterday.
            val dailyInput = Data.Builder()
                .putString(SolisCatchUpWorker.KEY_STATION_ID, station.id)
                .putString(SolisCatchUpWorker.KEY_STATION_NAME, station.name)
                .putString(SolisCatchUpWorker.KEY_CURRENCY, station.money)
                .build()
            val initialDelayHours = (25 - LocalDateTime.now().hour).toLong()
            val dailyReq = PeriodicWorkRequest.Builder(SolisCatchUpWorker::class.java, 1, TimeUnit.DAYS)
                .setInputData(dailyInput)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInitialDelay(initialDelayHours, TimeUnit.HOURS)
                .addTag(sysSn + "daily")
                .build()
            wm.enqueueUniquePeriodicWork(sysSn + "daily", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, dailyReq)
            _toast.postValue(Toast("Fetch started for ${station.name}"))
            _solis.postValue(buildSolisState())
        }
    }

    // ── Common — deletion ──────────────────────────────────────────────

    /**
     * Delete every reading for [sysSn]. Same DAO call the legacy "Delete all data"
     * dialog uses; works for AlphaESS, HA, ESBN, and Octopus since the import
     * schema is shared.
     */
    fun deleteAllData(sysSn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAlphaESSDataForSN(sysSn)
            _toast.postValue(Toast("Data deleted for $sysSn"))
            _alpha.postValue(buildAlphaState())
            _ha.postValue(buildHAState())
            _esbn.postValue(buildEsbnState())
            _octopus.postValue(buildOctopusState())
            _solis.postValue(buildSolisState())
        }
    }

    fun deleteRange(sysSn: String, from: LocalDateTime, to: LocalDateTime) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteInverterDatesBySN(sysSn, from, to)
            _toast.postValue(Toast("Range deleted for $sysSn"))
            _alpha.postValue(buildAlphaState())
            _ha.postValue(buildHAState())
            _esbn.postValue(buildEsbnState())
            _octopus.postValue(buildOctopusState())
            _solis.postValue(buildSolisState())
        }
    }

    /**
     * Remove an entire source: cancel any in-flight/scheduled work, delete every
     * reading for all of its systems, AND clear its credentials + system list
     * from DataStore. Distinct from [deleteAllData], which only drops the
     * readings and keeps the source configured. Clearing a key is done by
     * storing the empty string — `buildXState()` treats blank/empty as
     * "not configured / no systems".
     */
    fun deleteEntireSource(importer: ComparisonUIViewModel.Importer) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.postValue(true)
            try {
                val sns = when (importer) {
                    ComparisonUIViewModel.Importer.ALPHAESS -> buildAlphaState().systems
                    ComparisonUIViewModel.Importer.HOME_ASSISTANT -> buildHAState().systems
                    ComparisonUIViewModel.Importer.ESBNHDF -> buildEsbnState().systems
                    ComparisonUIViewModel.Importer.OCTOPUS -> buildOctopusState().systems
                    ComparisonUIViewModel.Importer.SOLIS -> buildSolisState().systems
                    else -> emptyList()
                }.map { it.sysSn }
                sns.forEach { sn ->
                    wm.cancelAllWorkByTag(sn)
                    wm.cancelAllWorkByTag(sn + "daily")
                    repository.clearAlphaESSDataForSN(sn)
                }
                when (importer) {
                    ComparisonUIViewModel.Importer.ALPHAESS -> {
                        app.putStringValueIntoDataStore(ALPHA_APP_ID_KEY, "")
                        app.putStringValueIntoDataStore(ALPHA_APP_SECRET_KEY, "")
                        app.putStringValueIntoDataStore(ALPHA_GOOD_KEY, "")
                        app.putStringValueIntoDataStore(ALPHA_SYSTEM_LIST_KEY, "")
                        app.putStringValueIntoDataStore(ALPHA_SELECTED_KEY, "")
                    }
                    ComparisonUIViewModel.Importer.HOME_ASSISTANT -> {
                        app.putStringValueIntoDataStore(HA_HOST_KEY, "")
                        app.putStringValueIntoDataStore(HA_TOKEN_KEY, "")
                        app.putStringValueIntoDataStore(HA_GOOD_KEY, "")
                        app.putStringValueIntoDataStore(HA_SYSTEM_LIST_KEY, "")
                        app.putStringValueIntoDataStore(HA_SELECTED_KEY, "")
                        app.putStringValueIntoDataStore(HA_SENSORS_KEY, "")
                        app.putStringValueIntoDataStore(HA_SERVER_RANGE_KEY, "")
                    }
                    ComparisonUIViewModel.Importer.ESBNHDF -> {
                        app.putStringValueIntoDataStore(ESBN_USER_KEY, "")
                        app.putStringValueIntoDataStore(ESBN_PASSWORD_KEY, "")
                        app.putStringValueIntoDataStore(ESBN_GOOD_KEY, "")
                        app.putStringValueIntoDataStore(ESBN_SYSTEM_LIST_KEY, "")
                        app.putStringValueIntoDataStore(ESBN_SELECTED_KEY, "")
                    }
                    ComparisonUIViewModel.Importer.OCTOPUS -> {
                        app.putStringValueIntoDataStore(OCTOPUS_ACCOUNT_KEY, "")
                        app.putStringValueIntoDataStore(OCTOPUS_API_KEY_KEY, "")
                        app.putStringValueIntoDataStore(OCTOPUS_GOOD_KEY, "")
                        app.putStringValueIntoDataStore(OCTOPUS_SYSTEM_LIST_KEY, "")
                        app.putStringValueIntoDataStore(OCTOPUS_SELECTED_KEY, "")
                    }
                    ComparisonUIViewModel.Importer.SOLIS -> {
                        app.putStringValueIntoDataStore(SOLIS_KEY_ID_KEY, "")
                        app.putStringValueIntoDataStore(SOLIS_SECRET_KEY, "")
                        app.putStringValueIntoDataStore(SOLIS_GOOD_KEY, "")
                        app.putStringValueIntoDataStore(SOLIS_SYSTEM_LIST_KEY, "")
                        app.putStringValueIntoDataStore(SOLIS_SELECTED_KEY, "")
                    }
                    else -> {}
                }
                _toast.postValue(Toast("Source removed"))
            } catch (t: Throwable) {
                _toast.postValue(Toast(t.message ?: "Could not remove source"))
            } finally {
                _alpha.postValue(buildAlphaState())
                _ha.postValue(buildHAState())
                _esbn.postValue(buildEsbnState())
                _octopus.postValue(buildOctopusState())
                _solis.postValue(buildSolisState())
                _haSensors.postValue(readHASensors())
                _busy.postValue(false)
            }
        }
    }

    // ── Weather/PV data sources (PVGIS, CDS) — Phase 5.5 ────────────────
    //
    // PVGIS data is cached as per-panel `PanelData` in the DB (the UI2 fetch
    // worker writes straight there), so the "cache" we list/delete is those
    // rows — surfaced via the `panelDataSummary` LiveData and removed with
    // `removeOldPanelData`. CDS only stores credentials in Phase 5.5; its
    // cached weather (the `heatpumpweather` series) arrives in Phase 6.

    /**
     * Rebuild the PVGIS state from the on-disk cache — one entry per `pvgis_{lat}_{lon}_{slope}_{az}_{loss}`
     * file (the true cache), each showing the scenarios it feeds. A single cached download serves every
     * panel that shares the location/orientation/loss (it is re-scaled per array size), so this no longer
     * shows one duplicated row per panel. [WeatherCacheEntry.id] is the file name so [deletePvCacheEntry]
     * can delete it directly (mirrors the CDS cache view).
     */
    private fun rebuildPvgisFromCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = PvgisCache.listCacheFiles(app).mapNotNull { f ->
                val k = PvgisCache.parse(f.name) ?: return@mapNotNull null
                val scenarios = runCatching {
                    repository.getScenarioNamesAtLocation(k.latitude, k.longitude, k.azimuth, k.slope)
                }.getOrDefault(emptyList())
                val name = "%.3f, %.3f · %d°/%d°".format(k.latitude, k.longitude, k.slope, k.azimuth)
                val detail = buildString {
                    append("loss ${k.lossPct}%")
                    append(" · ")
                    append(
                        if (scenarios.isEmpty()) "no scenarios"
                        else "${scenarios.size} scenario(s): ${scenarios.joinToString(", ")}"
                    )
                    append(" · ${"%.0f".format(f.length() / 1024.0)} kB")
                }
                WeatherCacheEntry(f.name, name, detail)
            }.sortedBy { it.name }
            _pvgis.postValue(WeatherSourceState(
                entries = entries,
                credentialsConfigured = true,   // PVGIS is anonymous
                credentialsKnownGood = true
            ))
        }
    }

    private fun buildCdsState(): WeatherSourceState {
        val configured = decryptOrNull(app.getStringValueFromDataStore(CDS_KEY_KEY)) != null
        // Validity is unknown until the first real fetch flips CDS_GOOD_KEY to
        // "True" (Phase 6). Never surface a fake "last check OK".
        val good = app.getStringValueFromDataStore(CDS_GOOD_KEY) == "True"
        return WeatherSourceState(
            entries = listCdsCacheEntries(),
            credentialsConfigured = configured,
            credentialsKnownGood = configured && good
        )
    }

    /**
     * The cached CDS downloads on disk — one `cds_{lat}_{lon}_{start}_{end}.csv` per fetched
     * (location, period) in [HeatPumpWeatherCache.CACHE_DIR]. [WeatherCacheEntry.id] is the file name so
     * [deleteCdsCacheEntry] can delete it directly.
     */
    private fun listCdsCacheEntries(): List<WeatherCacheEntry> {
        val dir = File(app.filesDir, HeatPumpWeatherCache.CACHE_DIR)
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("cds_") && f.name.endsWith(".csv") }
            ?: return emptyList()
        return files.map { f ->
            // cds_{lat}_{lon}_{start}_{end}.csv → "lat, lon" + "start → end" + size
            val parts = f.name.removePrefix("cds_").removeSuffix(".csv").split("_")
            val name = if (parts.size >= 2) "${parts[0]}, ${parts[1]}" else f.name
            val detail = buildString {
                if (parts.size >= 4) append("${parts[2]} → ${parts[3]}")
                append(" · ${"%.0f".format(f.length() / 1024.0)} kB")
            }.trim().removePrefix("· ")
            WeatherCacheEntry(f.name, name, detail)
        }.sortedBy { it.name }
    }

    /** Persist CDS credentials (encrypted), mirroring the AlphaESS/HA pattern. No probe. */
    fun setCdsCredentials(url: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app.putStringValueIntoDataStore(CDS_URL_KEY, TOUTCApplication.encryptString(url))
            app.putStringValueIntoDataStore(CDS_KEY_KEY, TOUTCApplication.encryptString(token))
            // Unknown until a real fetch validates it (Phase 6).
            app.putStringValueIntoDataStore(CDS_GOOD_KEY, "")
            _cds.postValue(buildCdsState())
            _toast.postValue(Toast("CDS credentials saved"))
        }
    }

    /** Wipe CDS credentials and every cached download. */
    fun removeCdsSource() {
        viewModelScope.launch(Dispatchers.IO) {
            app.putStringValueIntoDataStore(CDS_URL_KEY, "")
            app.putStringValueIntoDataStore(CDS_KEY_KEY, "")
            app.putStringValueIntoDataStore(CDS_GOOD_KEY, "")
            cdsCacheDir().listFiles()?.forEach { it.delete() }
            _cds.postValue(buildCdsState())
            _toast.postValue(Toast("CDS source removed"))
        }
    }

    /** Delete one cached CDS download (by file name). It re-fetches on the next sim that needs it. */
    fun deleteCdsCacheEntry(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(cdsCacheDir(), fileName).takeIf { it.isFile }?.delete()
            _cds.postValue(buildCdsState())
            _toast.postValue(Toast("CDS weather deleted · re-fetched on next save"))
        }
    }

    /** Delete every cached CDS download (credentials retained). */
    fun deleteAllCdsCache() {
        viewModelScope.launch(Dispatchers.IO) {
            cdsCacheDir().listFiles()?.forEach { it.delete() }
            _cds.postValue(buildCdsState())
            _toast.postValue(Toast("CDS cache cleared"))
        }
    }

    private fun cdsCacheDir(): File = File(app.filesDir, HeatPumpWeatherCache.CACHE_DIR)

    /**
     * The cached wholesale price months behind dynamic tariff plans — files named
     * `<marketId>_<year>_<month>.json` in [DynamicPriceCache.CACHE_DIR], grouped into
     * one entry per (market, year) since that is the unit a plan is generated from.
     * [WeatherCacheEntry.id] is `<marketId>_<year>`; the detail names the dynamic
     * plans built on that market year (mirrors PVGIS naming its scenarios).
     * Deleting cache never touches a materialised plan's generated rates — the
     * months are simply re-downloaded on the next generate/regenerate.
     */
    private fun buildPriceCacheState(): WeatherSourceState {
        val files = DynamicPriceCache.cacheDir(app)
            .listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray()
        val groups = files.mapNotNull { f ->
            // Market ids ("ISEM-DAM", "GB-AGILE-C") carry no underscore, but split
            // from the right anyway so one would survive: ..._<year>_<month>.json
            val parts = f.name.removeSuffix(".json").split("_")
            if (parts.size < 3) return@mapNotNull null
            val market = parts.subList(0, parts.size - 2).joinToString("_")
            val year = parts[parts.size - 2]
            "${market}_$year" to f
        }.groupBy({ it.first }, { it.second })
        val plans = runCatching { repository.allPricePlansNow.orEmpty() }
            .getOrDefault(emptyList())
        val entries = groups.map { (id, fs) ->
            val year = id.substringAfterLast('_')
            val market = id.substringBeforeLast('_')
            val planNames = plans.filter {
                it.dynamicTerms?.market == market && it.dynamicTerms?.year?.toString() == year
            }.map { it.planName }
            val detail = buildString {
                append("${fs.size} month(s)")
                append(" · ")
                append(
                    if (planNames.isEmpty()) "no plans"
                    else "${planNames.size} plan(s): ${planNames.joinToString(", ")}"
                )
                append(" · ${"%.0f".format(fs.sumOf { it.length() } / 1024.0)} kB")
            }
            WeatherCacheEntry(id, "$market · $year", detail)
        }.sortedBy { it.name }
        // The SEMOpx look-back workbook (downloaded only when a month is absent
        // from the daily feeds) lives beside the month chunks — list it as its
        // own deletable row. [WeatherCacheEntry.id] is the exact file name.
        val workbook = File(DynamicPriceCache.cacheDir(app), SemopxRateSource.LOOKBACK_FILE_NAME)
        val withWorkbook = if (workbook.isFile) {
            entries + WeatherCacheEntry(
                workbook.name,
                "SEMOpx look-back workbook",
                "fills months the daily feeds no longer publish · " +
                        "%.1f".format(workbook.length() / (1024.0 * 1024.0)) + " MB"
            )
        } else entries
        return WeatherSourceState(
            entries = withWorkbook,
            credentialsConfigured = true,   // public price feeds, no account
            credentialsKnownGood = true
        )
    }

    /** Delete one cached market year (all its month files) or the look-back
     *  workbook (exact file). Plans built on the data keep their generated
     *  rates; whatever is deleted re-downloads on the next generate. */
    fun deletePriceCacheEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = DynamicPriceCache.cacheDir(app)
            if (id.endsWith(".xlsx")) {
                File(dir, id).takeIf { it.isFile }?.delete()
                _toast.postValue(Toast("Workbook deleted · re-downloaded when a month needs it"))
            } else {
                dir.listFiles { f -> f.isFile && f.name.startsWith("${id}_") && f.name.endsWith(".json") }
                    ?.forEach { it.delete() }
                _toast.postValue(Toast("Cached prices deleted · re-downloaded on next generate"))
            }
            _prices.postValue(buildPriceCacheState())
        }
    }

    /** Delete every cached wholesale price file (month chunks + workbook). */
    fun deleteAllPriceCache() {
        viewModelScope.launch(Dispatchers.IO) {
            DynamicPriceCache.cacheDir(app)
                .listFiles { f -> f.isFile }
                ?.forEach { it.delete() }
            _prices.postValue(buildPriceCacheState())
            _toast.postValue(Toast("Price cache cleared"))
        }
    }

    /** Delete one cached PVGIS download (by file name). Re-fetched on the next save that needs it; the
     *  derived paneldata is left in place (delete-file-only). */
    fun deletePvCacheEntry(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(PvgisCache.cacheDir(app), fileName).takeIf { it.isFile }?.delete()
            rebuildPvgisFromCache()
            _toast.postValue(Toast("PVGIS data deleted · re-fetched on next save"))
        }
    }

    /** Delete every cached PVGIS download. */
    fun deleteAllPvgisCache() {
        viewModelScope.launch(Dispatchers.IO) {
            PvgisCache.listCacheFiles(app).forEach { it.delete() }
            rebuildPvgisFromCache()
            _toast.postValue(Toast("PVGIS cache cleared"))
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun decryptOrNull(s: String?): String? {
        if (s.isNullOrEmpty() || s == "null") return null
        return runCatching { TOUTCApplication.decryptString(s) }.getOrNull()
    }

    /**
     * Build one [ManagedSystem] row, joining the persisted SN with whatever
     * data range the DAO knows about and the WorkManager schedule status.
     */
    private fun systemRow(sysSn: String): ManagedSystem {
        val (display, start, end) = runCatching {
            val r = repository.getDateRange(sysSn)
            if (r == null || r.startDate.isNullOrEmpty()) Triple(null, null, null)
            else Triple("${r.startDate} ↔ ${r.finishDate}", r.startDate, r.finishDate)
        }.getOrDefault(Triple(null, null, null))
        return ManagedSystem(
            sysSn = sysSn,
            dateRange = display,
            startDate = start,
            endDate = end,
            scheduled = isScheduled(sysSn)
        )
    }

    /** Is a daily periodic worker currently scheduled for [sysSn]? */
    private fun isScheduled(sysSn: String): Boolean = runCatching {
        val infos = wm.getWorkInfosByTag(sysSn + "daily").get()
        infos.any { !it.state.isFinished }
    }.getOrDefault(false)

    private fun toDate(ldt: LocalDateTime): Date =
        Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant())

    fun acknowledgeToast() {
        _toast.value = null
    }

    private companion object {
        // Mirrors AbstractGenerationWorker / CatchUpWorker / DailyWorker which
        // all publish progress under this key in the WorkInfo.progress Data
        // bag (legacy convention).
        const val PROGRESS_KEY = "PROGRESS"

        // Envelope 6002 from the bind flow (plans/source/alpha.md as-built):
        // AlphaESS has no consumer-account link for the SN, which the app
        // cannot create — only the myAlpha/AlphaCloud registration can.
        const val SN_NOT_LINKED = "AlphaESS doesn't link this serial number to " +
                "your account. Register the inverter to your AlphaESS " +
                "(myAlpha/AlphaCloud) account first, then add it here."
    }
}
