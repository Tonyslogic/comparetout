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
import com.tfcode.comparetout.model.ToutcRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val _haSensors = MutableLiveData<HASensorSnapshot?>()
    val haSensors: LiveData<HASensorSnapshot?> = _haSensors
    private val _busy = MutableLiveData(false)
    val busy: LiveData<Boolean> = _busy
    private val _toast = MutableLiveData<Toast?>()
    val toast: LiveData<Toast?> = _toast

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

    init {
        refresh()
    }

    /** Re-read every persisted source state from DataStore + DB date ranges. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val a = buildAlphaState()
            val h = buildHAState()
            val e = buildEsbnState()
            _alpha.postValue(a)
            _ha.postValue(h)
            _esbn.postValue(e)
            _haSensors.postValue(readHASensors())
            // (Re-)attach WorkManager observers for the union of all SNs
            // so the live "fetching" indicator stays accurate as systems
            // appear and disappear from the lists.
            val sns = (a.systems + h.systems + e.systems).map { it.sysSn }.toSet()
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

    // ── Common — deletion ──────────────────────────────────────────────

    /**
     * Delete every reading for [sysSn]. Same DAO call the legacy "Delete all data"
     * dialog uses; works for AlphaESS, HA, and ESBN since the import schema is
     * shared.
     */
    fun deleteAllData(sysSn: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAlphaESSDataForSN(sysSn)
            _toast.postValue(Toast("Data deleted for $sysSn"))
            _alpha.postValue(buildAlphaState())
            _ha.postValue(buildHAState())
            _esbn.postValue(buildEsbnState())
        }
    }

    fun deleteRange(sysSn: String, from: LocalDateTime, to: LocalDateTime) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteInverterDatesBySN(sysSn, from, to)
            _toast.postValue(Toast("Range deleted for $sysSn"))
            _alpha.postValue(buildAlphaState())
            _ha.postValue(buildHAState())
            _esbn.postValue(buildEsbnState())
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
