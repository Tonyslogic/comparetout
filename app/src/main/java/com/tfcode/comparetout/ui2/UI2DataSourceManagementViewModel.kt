package com.tfcode.comparetout.ui2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
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
import com.tfcode.comparetout.importers.alphaess.AlphaESSMigrationWorker
import com.tfcode.comparetout.importers.alphaess.CatchUpWorker
import com.tfcode.comparetout.importers.alphaess.DailyWorker
import com.tfcode.comparetout.importers.alphaess.ExportWorker
import com.tfcode.comparetout.importers.alphaess.ImportWorker
import com.tfcode.comparetout.importers.alphaess.OpenAlphaESSClient
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse
import com.tfcode.comparetout.importers.esbn.ESBNExportWorker
import com.tfcode.comparetout.importers.esbn.ESBNImportWorker
import com.tfcode.comparetout.importers.homeassistant.EnergySensors
import com.tfcode.comparetout.importers.homeassistant.HACatchupWorker
import com.tfcode.comparetout.importers.octopus.OctopusCatchUpWorker
import com.tfcode.comparetout.importers.octopus.OctopusCsvImportWorker
import com.tfcode.comparetout.importers.octopus.OctopusRestClient
import com.tfcode.comparetout.importers.octopus.OctopusSystem
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformMeta
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.scenario.HeatPumpWeatherCache
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

private const val ESBN_SYSTEM_LIST_KEY = "esbn_system_list"
private const val ESBN_SELECTED_KEY = "esbn_system_previously_selected"

private const val OCTOPUS_ACCOUNT_KEY = "octopus_account"
private const val OCTOPUS_API_KEY_KEY = "octopus_api_key"
private const val OCTOPUS_GOOD_KEY = "octopus_cred_good"
private const val OCTOPUS_SYSTEM_LIST_KEY = "octopus_system_list"
private const val OCTOPUS_SELECTED_KEY = "octopus_system_previously_selected"

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

/** Discovered HA energy sensors for the read-only summary panel. */
data class HASensorSnapshot(
    val grid: List<String>,
    val gridExports: List<String>,
    val solar: List<String>,
    val batteries: List<Pair<String?, String?>> // (charging, discharging)
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
    private val _haSensors = MutableLiveData<HASensorSnapshot?>()
    val haSensors: LiveData<HASensorSnapshot?> = _haSensors
    private val _pvgis = MutableLiveData<WeatherSourceState>()
    val pvgis: LiveData<WeatherSourceState> = _pvgis
    private val _cds = MutableLiveData<WeatherSourceState>()
    val cds: LiveData<WeatherSourceState> = _cds
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
            _alpha.postValue(a)
            _ha.postValue(h)
            _esbn.postValue(e)
            _octopus.postValue(o)
            _haSensors.postValue(readHASensors())
            // PVGIS state is driven by the panelDataSummary observer, not here.
            _cds.postValue(buildCdsState())
            // (Re-)attach WorkManager observers for the union of all SNs
            // so the live "fetching" indicator stays accurate as systems
            // appear and disappear from the lists.
            val sns = (a.systems + h.systems + e.systems + o.systems).map { it.sysSn }.toSet()
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
            val appId = decryptOrNull(app.getStringValueFromDataStore(ALPHA_APP_ID_KEY))
            val appSecret = decryptOrNull(app.getStringValueFromDataStore(ALPHA_APP_SECRET_KEY))
            if (appId == null || appSecret == null) {
                _toast.postValue(Toast("Set AlphaESS credentials first"))
                return@launch
            }
            val date = dateFmt.format(toDate(startDate))
            val catchInput = Data.Builder()
                .putString(CatchUpWorker.KEY_APP_ID, appId)
                .putString(CatchUpWorker.KEY_APP_SECRET, appSecret)
                .putString(CatchUpWorker.KEY_SYSTEM_SN, sysSn)
                .putString(CatchUpWorker.KEY_START_DATE, date)
                .build()
            val catchReq = OneTimeWorkRequest.Builder(CatchUpWorker::class.java)
                .setInputData(catchInput)
                .addTag(sysSn)
                .build()
            wm.pruneWork()
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND, catchReq).enqueue()

            val dailyInput = Data.Builder()
                .putString(DailyWorker.KEY_APP_ID, appId)
                .putString(DailyWorker.KEY_APP_SECRET, appSecret)
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
            batteries = parsed.batteries.orEmpty().map { it.batteryCharging to it.batteryDischarging }
        )
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
                    app.putStringValueIntoDataStore(HA_SENSORS_KEY, gson.toJson(outcome.sensors))
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
     * Start a single HA catch-up plus the daily refresh. Same worker class
     * (HACatchupWorker) the legacy screen schedules.
     */
    fun fetchHA(startDate: LocalDateTime) {
        viewModelScope.launch(Dispatchers.IO) {
            val host = decryptOrNull(app.getStringValueFromDataStore(HA_HOST_KEY))
            val token = decryptOrNull(app.getStringValueFromDataStore(HA_TOKEN_KEY))
            val sensorsRaw = app.getStringValueFromDataStore(HA_SENSORS_KEY)
            if (host == null || token == null) {
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
                .putString(HACatchupWorker.KEY_HOST, host)
                .putString(HACatchupWorker.KEY_TOKEN, token)
                .putString(HACatchupWorker.KEY_START_DATE, date)
                .putString(HACatchupWorker.KEY_SENSORS, sensorsRaw)
                .build()
            val catchReq = OneTimeWorkRequest.Builder(HACatchupWorker::class.java)
                .setInputData(catchInput)
                .addTag(sysSn)
                .build()
            wm.pruneWork()
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND, catchReq).enqueue()

            val dailyInput = Data.Builder()
                .putString(HACatchupWorker.KEY_HOST, host)
                .putString(HACatchupWorker.KEY_TOKEN, token)
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

    // ── ESBN ────────────────────────────────────────────────────────────

    private fun buildEsbnState(): SourceState {
        val raw = app.getStringValueFromDataStore(ESBN_SYSTEM_LIST_KEY)
        val sns: List<String> = if (raw.isNullOrBlank()) emptyList()
        else runCatching {
            gson.fromJson<List<String>>(raw, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        }.getOrDefault(emptyList())
        val selected = app.getStringValueFromDataStore(ESBN_SELECTED_KEY)?.ifEmpty { null }
        return SourceState(
            importer = ComparisonUIViewModel.Importer.ESBNHDF,
            credentialsConfigured = true, // ESBN no longer uses credentials
            credentialsKnownGood = true,
            systems = sns.map { sn -> systemRow(sn).copy(scheduled = false) },
            selectedSn = selected
        )
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
            val account = decryptOrNull(app.getStringValueFromDataStore(OCTOPUS_ACCOUNT_KEY))
            val apiKey = decryptOrNull(app.getStringValueFromDataStore(OCTOPUS_API_KEY_KEY))
            if (account == null || apiKey == null) {
                _toast.postValue(Toast("Set Octopus credentials first"))
                return@launch
            }
            val date = dateFmt.format(toDate(startDate))
            val catchInput = Data.Builder()
                .putString(OctopusCatchUpWorker.KEY_APP_ID, account)
                .putString(OctopusCatchUpWorker.KEY_APP_SECRET, apiKey)
                .putString(OctopusCatchUpWorker.KEY_SYSTEM_SN, sysSn)
                .putString(OctopusCatchUpWorker.KEY_START_DATE, date)
                .build()
            val catchReq = OneTimeWorkRequest.Builder(OctopusCatchUpWorker::class.java)
                .setInputData(catchInput)
                .addTag(sysSn)
                .build()
            wm.pruneWork()
            wm.beginUniqueWork(sysSn, ExistingWorkPolicy.APPEND, catchReq).enqueue()

            // Daily incremental sync: the same worker resumes from the latest
            // stored date when no start date is supplied.
            val dailyInput = Data.Builder()
                .putString(OctopusCatchUpWorker.KEY_APP_ID, account)
                .putString(OctopusCatchUpWorker.KEY_APP_SECRET, apiKey)
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
                    }
                    ComparisonUIViewModel.Importer.ESBNHDF -> {
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
    }
}
