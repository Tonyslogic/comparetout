package com.tfcode.comparetout.ui2

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.R
import com.tfcode.comparetout.SimulatorLauncher
import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfiles
import com.tfcode.comparetout.model.SnapshotExporter
import com.tfcode.comparetout.model.SnapshotImporter
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.scenario.Scenario
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────
// Import / Export — drawer-launched screen for app-wide data flow.
//
// Phase D1 scope: bulk JSON export *and* import for plans and scenarios.
// Export rows are the verbatim legacy MainActivity bulk operations (just
// re-homed from a toolbar submenu into this screen). Import rows are the
// inverse: paste or pick a file, preview what was parsed, optionally
// clobber matching DB rows, then insert via the same repository paths
// legacy already uses.
//
// "All comparisons CSV" is intentionally absent — UI2 always computes
// comparisons against the *current selection*, so a global all-pairs CSV
// has no analogue. Per-panel CSV/JSON share lives on the Compare tab itself.
//
// The DB-snapshot export rows below (Everything / Selection) build a real
// SQLite file via [SnapshotExporter] and share it through the FileProvider
// declared in AndroidManifest.xml. Import-side handling is a later phase.
// ──────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2ImportExportActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                ImportExportScreen(onClose = { finish() })
            }
        }
    }
}

/** Outcome of a bulk import — used for the success snackbar. */
data class ImportOutcome(val replaced: Int, val added: Int) {
    val total: Int get() = replaced + added
    /** [noun] is the already-pluralised item word (e.g. from R.plurals.ui2_noun_plan). */
    fun summary(context: android.content.Context, noun: String): String = when {
        total == 0    -> context.getString(R.string.ui2_ie_nothing_to_import)
        replaced == 0 -> context.getString(R.string.ui2_ie_imported_n, added, noun)
        added == 0    -> context.getString(R.string.ui2_ie_replaced_n, replaced, noun)
        else          -> context.getString(R.string.ui2_ie_imported_mixed, total, noun, replaced, added)
    }
}

/** A picker row for one of the data sources held in the AlphaESS tables. */
data class ExportSourceRow(
    val sysSn: String,
    val kind: SourceKind,
    val startDate: String,
    val finishDate: String
) {
    enum class SourceKind(val label: String) {
        ALPHAESS("AlphaESS"),
        ESBN_OR_HA("ESBN / HA")
    }
}

@HiltViewModel
class UI2ImportExportViewModel @Inject constructor(
    application: Application,
    private val repository: ToutcRepository
) : AndroidViewModel(application) {

    private val snapshotExporter = SnapshotExporter(application)
    private val snapshotImporter = SnapshotImporter(application)

    // ── export (JSON paths — unchanged from Phase C-lite) ───────────────────

    suspend fun allPlansJson(): String? = withContext(Dispatchers.IO) {
        val map = repository.allPricePlansForExport ?: return@withContext null
        if (map.isEmpty()) null else JsonTools.createPricePlanJson(map)
    }

    suspend fun allScenariosJson(): String? = withContext(Dispatchers.IO) {
        val list = repository.allScenariosForExport ?: return@withContext null
        if (list.isEmpty()) null else JsonTools.createScenarioList(list)
    }

    // ── DB snapshot export ──────────────────────────────────────────────────

    /** Lightweight rows for the picker sheet — derived from the live LiveData. */
    val scenarios = repository.allScenarios.asFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Sources are AlphaESS systems (raw-energy rows present) plus ESBN/HA
     *  entries (only transformed rows present). Both AlphaESS DAOs use
     *  identical SQL for ESBN and HA — the rows can't be distinguished by
     *  data alone, so they share a single "ESBN / HA" bucket in the picker. */
    val sources = combine(
        repository.liveDateRanges.asFlow(),
        repository.esbnLiveDateRanges.asFlow()
    ) { alpha, transformed ->
        val alphaSns = alpha.map { it.sysSn }.toHashSet()
        val alphaRows = alpha.map {
            ExportSourceRow(it.sysSn, ExportSourceRow.SourceKind.ALPHAESS, it.startDate, it.finishDate)
        }
        val esbnHaRows = transformed
            .filter { it.sysSn !in alphaSns }
            .map {
                ExportSourceRow(it.sysSn, ExportSourceRow.SourceKind.ESBN_OR_HA, it.startDate, it.finishDate)
            }
        (alphaRows + esbnHaRows).sortedBy { it.sysSn }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Build a SQLite snapshot and return a content:// URI the caller can hand
     * to a system share intent. `null` on failure (caller surfaces a snackbar).
     */
    suspend fun buildSnapshot(
        scope: SnapshotExporter.Scope,
        includeOutputs: Boolean,
        filenameSuffix: String
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val dir = File(app.cacheDir, "exports").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val target = File(dir, "eco-power-optimiser-$filenameSuffix-$stamp.db")
            snapshotExporter.buildSnapshot(scope, includeOutputs, target)
            FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", target)
        }.getOrNull()
    }

    // ── DB snapshot import ─────────────────────────────────────────────────

    suspend fun stageAndValidate(uri: Uri): SnapshotImporter.Validation = withContext(Dispatchers.IO) {
        val staged = runCatching { snapshotImporter.stage(uri) }.getOrElse { e ->
            return@withContext SnapshotImporter.Validation.FileError(
                e.message ?: getApplication<Application>().getString(R.string.ui2_ie_could_not_read_file)
            )
        }
        val result = runCatching { snapshotImporter.validate(staged) }.getOrElse { e ->
            SnapshotImporter.Validation.FileError(
                e.message ?: getApplication<Application>().getString(R.string.ui2_ie_could_not_validate_file)
            )
        }
        // Drop the staging file whenever the user is going to bail at this
        // step — either because validation failed, or because the integrity
        // check came back with errors and we won't be committing. The
        // Confirm branch keeps the file around until commit / cancel.
        when (result) {
            is SnapshotImporter.Validation.FileError -> staged.delete()
            is SnapshotImporter.Validation.Opened -> if (!result.ok) staged.delete()
        }
        result
    }

    suspend fun commitImport(
        staged: SnapshotImporter.Staged,
        replaceExisting: Boolean
    ): SnapshotImporter.CommitResult? = withContext(Dispatchers.IO) {
        runCatching { snapshotImporter.commit(staged, replaceExisting) }.getOrNull()
    }

    fun discardStaged(staged: SnapshotImporter.Staged) {
        staged.delete()
    }

    // ── import ──────────────────────────────────────────────────────────────
    //
    // The two `*FromList` methods walk a parsed list and dispatch into the
    // same repository inserts the legacy MainActivity uses. The boolean
    // `clobber` flag is the legacy "Replace plans with the same name?" toggle
    // — same semantics as PricePlanDAO.addNewPricePlanWithDayRates(... clobber).

    suspend fun importPlansFromList(
        list: List<PricePlanJsonFile>,
        clobber: Boolean
    ): ImportOutcome = withContext(Dispatchers.IO) {
        var replaced = 0
        var added = 0
        // Snapshot existing plan names so we can report how many rows the
        // clobber path actually overwrote, without dragging that count out of
        // the DAO layer.
        val existingNames: Set<String> =
            repository.allPricePlansNow?.map { it.planName }?.toSet().orEmpty()
        list.forEach { pp ->
            val plan = JsonTools.createPricePlan(pp)
            val drs = ArrayList<DayRate>()
            pp.rates?.forEach { drj -> drs.add(JsonTools.createDayRate(drj)) }
            // Repository.insert queues onto ToutcDB.databaseWriteExecutor —
            // it's the same path legacy MainActivity uses for bulk import.
            repository.insert(plan, drs, clobber)
            if (plan.planName in existingNames) replaced += 1 else added += 1
            // Terms-only dynamic plans land pending — auto-materialise.
            DynamicTariffWorker.maybeEnqueuePendingImport(getApplication(), pp)
        }
        // Imported plans flipped the costing-readiness flags; kick the recompute (UI2 has no nav observer).
        SimulatorLauncher.simulateIfNeeded(getApplication())
        ImportOutcome(replaced, added)
    }

    suspend fun importScenariosFromList(
        list: List<ScenarioJsonFile>,
        clobber: Boolean
    ): ImportOutcome = withContext(Dispatchers.IO) {
        var replaced = 0
        var added = 0
        val existingNames: Set<String> =
            repository.allScenariosForExport
                ?.map { it.scenario.scenarioName }?.toSet().orEmpty()
        val components = JsonTools.createScenarioComponentList(ArrayList(list))
        components.forEach { sc ->
            repository.insertScenario(sc, clobber)
            if (sc.scenario.scenarioName in existingNames) replaced += 1 else added += 1
        }
        // Imported scenarios need simulation + costing; kick the recompute (UI2 has no nav observer).
        SimulatorLauncher.simulateIfNeeded(getApplication())
        ImportOutcome(replaced, added)
    }
}

/** A parsed import payload waiting on the user's clobber decision. The count
 *  label and item noun are resolved from plurals resources at the call sites. */
private sealed class PendingImport {
    abstract val size: Int
    data class Plans(val list: List<PricePlanJsonFile>) : PendingImport() {
        override val size get() = list.size
    }
    data class Scenarios(val list: List<ScenarioJsonFile>) : PendingImport() {
        override val size get() = list.size
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportExportScreen(
    viewModel: UI2ImportExportViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (showHints, toggleShowHints) = rememberShowHints()
    var showDrawer by remember { mutableStateOf(false) }
    var workingLabel by remember { mutableStateOf<String?>(null) }

    // Import flow state — separate from export because they have different
    // lifecycles (export = build → share; import = pick → preview → confirm).
    var importTarget by remember { mutableStateOf<ImportTarget?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    // DB snapshot picker visibility — shows the scenarios + sources + outputs sheet.
    var showSnapshotPicker by remember { mutableStateOf(false) }
    // Pending DB snapshot import — either an error to show or a validated
    // staging file waiting for the user's Replace toggle and confirm.
    var snapshotImportState by remember { mutableStateOf<SnapshotImportState?>(null) }
    // Result message shown in a blocking dialog (not a transient snackbar) when the imported DB carried data
    // sources — those need an explicit acknowledgement because the user must restart the app to see them.
    var snapshotSourcesResult by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val snapshotPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || workingLabel != null) return@rememberLauncherForActivityResult
        workingLabel = "snapshot-import"
        scope.launch {
            val v = viewModel.stageAndValidate(uri)
            workingLabel = null
            snapshotImportState = SnapshotImportState.from(v)
        }
    }

    fun commitSnapshotImport(staged: SnapshotImporter.Staged, replaceExisting: Boolean) {
        if (workingLabel != null) return
        workingLabel = "snapshot-import"
        snapshotImportState = null
        scope.launch {
            val result = viewModel.commitImport(staged, replaceExisting)
            workingLabel = null
            val msg = result?.summary(context)
                ?: context.getString(R.string.ui2_ie_snapshot_import_failed)
            // When the imported DB included data sources, the user must restart to see them — a snackbar is too
            // easy to miss, so require an explicit OK. Everything else stays a snackbar.
            if (result != null && result.sourcesTouched > 0) {
                snapshotSourcesResult = msg
            } else {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    fun runExport(label: String, subject: String, format: ShareFormat, build: suspend () -> String?) {
        if (workingLabel != null) return
        workingLabel = label
        scope.launch {
            val payload = runCatching { build() }.getOrNull()
            workingLabel = null
            if (!payload.isNullOrEmpty()) {
                context.shareText(payload, format, subject)
            }
        }
    }

    fun runSnapshotExport(
        label: String,
        scopeKind: SnapshotExporter.Scope,
        includeOutputs: Boolean,
        filenameSuffix: String,
        subject: String
    ) {
        if (workingLabel != null) return
        workingLabel = label
        scope.launch {
            val uri = viewModel.buildSnapshot(scopeKind, includeOutputs, filenameSuffix)
            workingLabel = null
            if (uri != null) {
                context.shareSnapshot(uri, subject)
            } else {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.ui2_ie_snapshot_export_failed))
            }
        }
    }

    fun commitImport(clobber: Boolean) {
        val import = pendingImport ?: return
        pendingImport = null
        scope.launch {
            val outcome = runCatching {
                when (import) {
                    is PendingImport.Plans     -> viewModel.importPlansFromList(import.list, clobber)
                    is PendingImport.Scenarios -> viewModel.importScenariosFromList(import.list, clobber)
                }
            }.getOrNull()
            val noun = context.resources.getQuantityString(
                when (import) {
                    is PendingImport.Plans     -> R.plurals.ui2_noun_plan
                    is PendingImport.Scenarios -> R.plurals.ui2_noun_scenario
                }, import.size)
            val msg = outcome?.summary(context, noun)
                ?: context.getString(R.string.ui2_import_failed)
            snackbarHostState.showSnackbar(msg)
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui2_drawer_import_export)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.ui2_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu,
                            contentDescription = stringResource(R.string.ui2_menu))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showHints) {
                    item("hint") { HintCard() }
                }
                item("export_header") {
                    SectionLabel(stringResource(R.string.ui2_ie_export), Icons.Default.FileDownload)
                }
                item("export_plans") {
                    val title = stringResource(R.string.ui2_ie_all_plans)
                    DataRow(
                        title = title,
                        format = "JSON",
                        helpText = stringResource(R.string.ui2_ie_help_all_plans),
                        actionIcon = Icons.Default.Share,
                        actionTint = MaterialTheme.colorScheme.primary,
                        busy = workingLabel == "plans",
                        anyBusy = workingLabel != null,
                        onClick = {
                            runExport("plans", title, ShareFormat.JSON) {
                                viewModel.allPlansJson()
                            }
                        }
                    )
                }
                if (AppProfiles.current.hasScenarios) item("export_scenarios") {
                    val title = stringResource(R.string.ui2_ie_all_scenarios)
                    DataRow(
                        title = title,
                        format = "JSON",
                        helpText = stringResource(R.string.ui2_ie_help_all_scenarios),
                        actionIcon = Icons.Default.Share,
                        actionTint = MaterialTheme.colorScheme.primary,
                        busy = workingLabel == "scenarios",
                        anyBusy = workingLabel != null,
                        onClick = {
                            runExport("scenarios", title, ShareFormat.JSON) {
                                viewModel.allScenariosJson()
                            }
                        }
                    )
                }
                item("export_db_all") {
                    val subject = stringResource(R.string.ui2_ie_snapshot_subject)
                    DataRow(
                        title = stringResource(R.string.ui2_ie_everything_db),
                        format = "SQLite",
                        helpText = stringResource(R.string.ui2_ie_help_everything),
                        actionIcon = Icons.Default.Backup,
                        actionTint = MaterialTheme.colorScheme.primary,
                        busy = workingLabel == "snapshot-all",
                        anyBusy = workingLabel != null,
                        onClick = {
                            runSnapshotExport(
                                label = "snapshot-all",
                                scopeKind = SnapshotExporter.Scope.Everything,
                                includeOutputs = true,
                                filenameSuffix = "all",
                                subject = subject
                            )
                        }
                    )
                }
                item("export_db_selection") {
                    DataRow(
                        title = stringResource(R.string.ui2_ie_selection_db),
                        format = "SQLite",
                        helpText = stringResource(R.string.ui2_ie_help_selection),
                        actionIcon = Icons.Default.Backup,
                        actionTint = MaterialTheme.colorScheme.primary,
                        busy = workingLabel == "snapshot-selection",
                        anyBusy = workingLabel != null,
                        onClick = { showSnapshotPicker = true }
                    )
                }

                item("import_header") {
                    SectionLabel(stringResource(R.string.ui2_ie_import), Icons.Default.FileUpload)
                }
                item("import_plans") {
                    DataRow(
                        title = stringResource(R.string.ui2_ie_import_plans),
                        format = "JSON",
                        helpText = stringResource(R.string.ui2_ie_help_import_plans),
                        actionIcon = Icons.Default.FileUpload,
                        actionTint = MaterialTheme.colorScheme.tertiary,
                        busy = false,
                        anyBusy = false,
                        onClick = { importTarget = ImportTarget.PLANS }
                    )
                }
                if (AppProfiles.current.hasScenarios) item("import_scenarios") {
                    DataRow(
                        title = stringResource(R.string.ui2_ie_import_scenarios),
                        format = "JSON",
                        helpText = stringResource(R.string.ui2_ie_help_import_scenarios),
                        actionIcon = Icons.Default.FileUpload,
                        actionTint = MaterialTheme.colorScheme.tertiary,
                        busy = false,
                        anyBusy = false,
                        onClick = { importTarget = ImportTarget.SCENARIOS }
                    )
                }
                item("import_db") {
                    DataRow(
                        title = stringResource(R.string.ui2_ie_import_db),
                        format = "SQLite",
                        helpText = stringResource(R.string.ui2_ie_help_import_db),
                        actionIcon = Icons.Default.FileUpload,
                        actionTint = MaterialTheme.colorScheme.tertiary,
                        busy = workingLabel == "snapshot-import",
                        anyBusy = workingLabel != null,
                        onClick = {
                            // Accept anything; some launchers don't honour the
                            // application/vnd.sqlite3 filter. We re-validate the
                            // content via Room before doing anything destructive.
                            snapshotPicker.launch(arrayOf("*/*"))
                        }
                    )
                }

                item("comparenote") {
                    if (showHints) ComparisonResultsNote()
                }
            }

            AnimatedVisibility(visible = showDrawer, enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showDrawer = false })
            }
            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInHorizontally(tween(220)) { it },
                exit = slideOutHorizontally(tween(220)) { it },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(280.dp)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxSize()) {
                    UI2DrawerContent(
                        showHints = showHints,
                        onShowHintsChange = { if (it != showHints) toggleShowHints() },
                        onSwitchLegacy = { showDrawer = false; onClose() },
                        onClose = { showDrawer = false }
                    )
                }
            }
        }
    }

    // ── Import sheet — generic; the target enum picks the parser ─────────────
    importTarget?.let { target ->
        when (target) {
            ImportTarget.PLANS -> UI2ImportSheet(
                title = stringResource(R.string.ui2_ie_import_plans),
                hint = stringResource(R.string.ui2_ie_import_hint_plans),
                applyLabel = stringResource(R.string.ui2_continue),
                communityFeeds = RegionProfiles.communityFeedChoices(),
                // The global edition has no baked region — ask whose tariffs.
                communitySelectRegion = RegionProfiles.current.isGlobal,
                llmPrompt = PricePlanDownloader.LLM_PROMPT,
                parse = { parsePricePlansJson(context, it) },
                onApply = {
                    pendingImport = PendingImport.Plans(it)
                    importTarget = null
                },
                onDismiss = { importTarget = null }
            )
            ImportTarget.SCENARIOS -> UI2ImportSheet(
                title = stringResource(R.string.ui2_ie_import_scenarios),
                hint = stringResource(R.string.ui2_ie_import_hint_scenarios),
                applyLabel = stringResource(R.string.ui2_continue),
                parse = { parseScenariosJson(context, it) },
                onApply = {
                    pendingImport = PendingImport.Scenarios(it)
                    importTarget = null
                },
                onDismiss = { importTarget = null }
            )
        }
    }

    // ── Clobber dialog — matches the legacy "Replace existing entries?" prompt ─
    pendingImport?.let { p ->
        val countLabel = when (p) {
            is PendingImport.Plans ->
                pluralStringResource(R.plurals.ui2_ie_count_plans, p.size, p.size)
            is PendingImport.Scenarios ->
                pluralStringResource(R.plurals.ui2_ie_count_scenarios, p.size, p.size)
        }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.ui2_import_count_title, countLabel)) },
            text = {
                Text(
                    stringResource(when (p) {
                        is PendingImport.Plans -> R.string.ui2_clobber_body_plan
                        is PendingImport.Scenarios -> R.string.ui2_clobber_body_scenario
                    }),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { commitImport(clobber = true) }) {
                    Text(stringResource(R.string.ui2_replace_existing))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { commitImport(clobber = false) }) {
                        Text(stringResource(R.string.ui2_keep_both))
                    }
                    TextButton(onClick = { pendingImport = null }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                }
            }
        )
    }

    // ── Snapshot import — error card or preview + confirm dialog ──────────
    snapshotImportState?.let { state ->
        when (state) {
            is SnapshotImportState.Error -> {
                AlertDialog(
                    onDismissRequest = { snapshotImportState = null },
                    title = { Text(stringResource(R.string.ui2_ie_snapshot_rejected)) },
                    text = {
                        Column {
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            if (state.details.isNotEmpty()) {
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    stringResource(R.string.ui2_ie_details),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                state.details.take(5).forEach { line ->
                                    Text(
                                        "• $line",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.details.size > 5) {
                                    Text(
                                        stringResource(R.string.ui2_ie_more_details, state.details.size - 5),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { snapshotImportState = null }) {
                            Text(stringResource(R.string.dialog_ok))
                        }
                    }
                )
            }
            is SnapshotImportState.Confirm -> {
                SnapshotPreviewDialog(
                    summary = state.summary,
                    onCancel = {
                        viewModel.discardStaged(state.staged)
                        snapshotImportState = null
                    },
                    onConfirm = { replaceExisting ->
                        commitSnapshotImport(state.staged, replaceExisting)
                    }
                )
            }
        }
    }

    // ── Snapshot import completed WITH data sources — explicit acknowledgement ───
    // Sources are written outside Room's change tracking, so the data-source lists only refresh after a
    // restart. A snackbar is too easy to miss, so block on an OK the user must tap.
    snapshotSourcesResult?.let { msg ->
        AlertDialog(
            onDismissRequest = { snapshotSourcesResult = null },
            title = { Text(stringResource(R.string.ui2_ie_import_complete)) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { snapshotSourcesResult = null }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            }
        )
    }

    // ── Snapshot picker — choose scenarios + sources + outputs toggle ──────
    if (showSnapshotPicker) {
        val scenarios by viewModel.scenarios.collectAsState()
        val sources by viewModel.sources.collectAsState()
        val subject = stringResource(R.string.ui2_ie_snapshot_subject)
        SnapshotPickerDialog(
            scenarios = scenarios,
            sources = sources,
            onDismiss = { showSnapshotPicker = false },
            onConfirm = { selectedScenarioIds, selectedSysSns, includeOutputs ->
                showSnapshotPicker = false
                val nScenarios = selectedScenarioIds.size
                val nSources = selectedSysSns.size
                val suffix = buildString {
                    if (nScenarios > 0) append("${nScenarios}sims")
                    if (nScenarios > 0 && nSources > 0) append("-")
                    if (nSources > 0) append("${nSources}sources")
                }
                runSnapshotExport(
                    label = "snapshot-selection",
                    scopeKind = SnapshotExporter.Scope.Selection(selectedScenarioIds, selectedSysSns),
                    includeOutputs = includeOutputs,
                    filenameSuffix = suffix,
                    subject = subject
                )
            }
        )
    }
}

/** Fire a system share with a content URI pointing at the binary snapshot. */
private fun android.content.Context.shareSnapshot(uri: Uri, subject: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.sqlite3"
        putExtra(Intent.EXTRA_STREAM, uri)
        if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, null))
}

@Composable
private fun SnapshotPickerDialog(
    scenarios: List<Scenario>,
    sources: List<ExportSourceRow>,
    onDismiss: () -> Unit,
    onConfirm: (scenarioIds: Set<Long>, sysSns: Set<String>, includeOutputs: Boolean) -> Unit
) {
    val selectedScenarios = remember { mutableStateOf(emptySet<Long>()) }
    val selectedSources = remember { mutableStateOf(emptySet<String>()) }
    var includeOutputs by remember { mutableStateOf(true) }
    val canExport = selectedScenarios.value.isNotEmpty() || selectedSources.value.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui2_ie_export_selection_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Lock, contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.ui2_ie_plans_always_included),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(8.dp))

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    if (scenarios.isNotEmpty()) {
                        item("hdr_sc") { PickerHeader(stringResource(R.string.ui2_scenarios_title)) }
                        items(scenarios) { sc ->
                            val checked = sc.scenarioIndex in selectedScenarios.value
                            CheckboxRow(
                                label = sc.scenarioName,
                                sub = "id ${sc.scenarioIndex}",
                                checked = checked,
                                onToggle = {
                                    selectedScenarios.value = if (checked) {
                                        selectedScenarios.value - sc.scenarioIndex
                                    } else {
                                        selectedScenarios.value + sc.scenarioIndex
                                    }
                                }
                            )
                        }
                    }
                    if (sources.isNotEmpty()) {
                        item("hdr_src") {
                            Spacer(Modifier.size(8.dp))
                            PickerHeader(stringResource(R.string.ui2_data_sources))
                        }
                        items(sources) { src ->
                            val checked = src.sysSn in selectedSources.value
                            CheckboxRow(
                                label = src.sysSn,
                                sub = "${src.kind.label}  ·  ${src.startDate} → ${src.finishDate}",
                                checked = checked,
                                onToggle = {
                                    selectedSources.value = if (checked) {
                                        selectedSources.value - src.sysSn
                                    } else {
                                        selectedSources.value + src.sysSn
                                    }
                                }
                            )
                        }
                    }
                    if (scenarios.isEmpty() && sources.isEmpty()) {
                        item("empty") {
                            Text(
                                stringResource(R.string.ui2_ie_nothing_to_export),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.ui2_ie_include_outputs),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.ui2_ie_include_outputs_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = includeOutputs, onCheckedChange = { includeOutputs = it })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canExport,
                onClick = {
                    onConfirm(selectedScenarios.value, selectedSources.value, includeOutputs)
                }
            ) { Text(stringResource(R.string.ui2_ie_export)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun PickerHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun CheckboxRow(
    label: String,
    sub: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Two-state UI model for the DB-snapshot import path. */
private sealed class SnapshotImportState {
    /** Schema mismatch / unreadable file / referential failures — rendered as a dialog. */
    data class Error(val message: String, val details: List<String>) : SnapshotImportState()

    /** Validated staging file waiting on the user's Replace toggle + Import tap. */
    data class Confirm(
        val staged: SnapshotImporter.Staged,
        val summary: SnapshotImporter.Summary
    ) : SnapshotImportState()

    companion object {
        fun from(v: SnapshotImporter.Validation): SnapshotImportState = when (v) {
            is SnapshotImporter.Validation.FileError ->
                Error(v.message, emptyList())
            is SnapshotImporter.Validation.Opened ->
                if (v.ok) Confirm(v.staged, v.summary)
                else Error(
                    "The snapshot is internally inconsistent — nothing was changed.",
                    v.errors
                )
        }
    }
}

private fun SnapshotImporter.CommitResult.summary(context: android.content.Context): String {
    val res = context.resources
    val parts = mutableListOf<String>()
    val plans = plansAdded + plansReplaced
    if (plans > 0) {
        var part = res.getQuantityString(R.plurals.ui2_ie_n_plans, plans, plans)
        if (plansReplaced > 0) part += " " + res.getString(R.string.ui2_ie_n_replaced, plansReplaced)
        parts += part
    }
    val scenarios = scenariosAdded + scenariosReplaced
    if (scenarios > 0) {
        var part = res.getQuantityString(R.plurals.ui2_ie_n_scenarios, scenarios, scenarios)
        if (scenariosReplaced > 0) part += " " + res.getString(R.string.ui2_ie_n_replaced, scenariosReplaced)
        parts += part
    }
    if (sourcesTouched > 0) {
        parts += res.getQuantityString(R.plurals.ui2_ie_n_sources, sourcesTouched, sourcesTouched)
    }
    val skipped = plansSkipped + scenariosSkipped
    val skipFragment = if (skipped > 0)
        " " + res.getQuantityString(R.plurals.ui2_ie_n_kept, skipped, skipped) else ""
    // Imported source rows are written outside Room's change tracking, so the
    // data-source lists won't refresh until the app is relaunched.
    val restartFragment = if (sourcesTouched > 0)
        " " + res.getString(R.string.ui2_ie_restart_suffix) else ""
    return if (parts.isEmpty()) res.getString(R.string.ui2_ie_nothing_imported) + skipFragment
           else res.getString(R.string.ui2_ie_imported_list, parts.joinToString(", ")) +
               skipFragment + restartFragment
}

@Composable
private fun SnapshotPreviewDialog(
    summary: SnapshotImporter.Summary,
    onCancel: () -> Unit,
    onConfirm: (replaceExisting: Boolean) -> Unit
) {
    var replaceExisting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.ui2_ie_import_db)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SummaryLine(stringResource(R.string.ui2_supplier_plans), summary.plans)
                SummaryLine(
                    stringResource(R.string.ui2_ie_noun_scenarios),
                    summary.scenarios,
                    sample = summary.sampleScenarioNames
                )
                // Editions without scenarios refuse that section (plan Q3) —
                // say so up front, before the user commits the import.
                if (!AppProfiles.current.hasScenarios && summary.scenarios > 0) {
                    Text(
                        stringResource(R.string.ui2_ie_scenarios_refused, summary.scenarios),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                SummaryLine(
                    stringResource(R.string.ui2_data_sources),
                    summary.sources,
                    sample = summary.sampleSysSns
                )
                if (summary.transformedRows + summary.rawPowerRows + summary.rawEnergyRows > 0) {
                    Text(
                        stringResource(R.string.ui2_ie_source_rows,
                            summary.transformedRows, summary.rawPowerRows, summary.rawEnergyRows),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    stringResource(R.string.ui2_ie_preview_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (summary.sources > 0) {
                    Text(
                        stringResource(R.string.ui2_ie_restart_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replaceExisting = !replaceExisting }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = replaceExisting,
                        onCheckedChange = { replaceExisting = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.ui2_ie_replace_toggle),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.ui2_ie_replace_toggle_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(replaceExisting) }) {
                Text(stringResource(R.string.ui2_ie_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun SummaryLine(label: String, count: Int, sample: List<String> = emptyList()) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(
            "• $count $label",
            style = MaterialTheme.typography.bodyMedium
        )
        if (sample.isNotEmpty()) {
            val preview = sample.take(3).joinToString(", ")
            val tail = if (sample.size > 3 || count > sample.size) " …" else ""
            Text(
                "$preview$tail",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

private enum class ImportTarget { PLANS, SCENARIOS }

// ── Parsers ────────────────────────────────────────────────────────────────
//
// Both accept either a JSON *array* (the bulk-export shape) or a single
// *object* (the per-item share shape from Phase A) — exactly the same logic
// the legacy MainActivity importers do, but extracted out of the file-pick
// callback so the parser can run on a pasted text buffer.

internal fun parsePricePlansJson(context: android.content.Context, text: String): ParsedPreview<List<PricePlanJsonFile>> = try {
    val gson = Gson()
    val trimmed = text.trimStart()
    // Gson returns null for an empty or "null" payload — guard before .filter.
    val raw: List<PricePlanJsonFile>? = if (trimmed.startsWith("[")) {
        val type = object : TypeToken<List<PricePlanJsonFile>>() {}.type
        gson.fromJson<List<PricePlanJsonFile>?>(text, type)
    } else {
        gson.fromJson(text, PricePlanJsonFile::class.java)?.let { listOf(it) }
    }
    val list = raw ?: emptyList()
    val valid = list.filter { !it.plan.isNullOrBlank() }
    if (valid.isEmpty()) ParsedPreview.Err(context.getString(R.string.ui2_parse_no_plans))
    else ParsedPreview.Ok(
        valid,
        when (valid.size) {
            1 -> context.getString(R.string.ui2_parse_one_plan,
                valid.first().supplier ?: "?", valid.first().plan)
            else -> context.resources.getQuantityString(
                R.plurals.ui2_parse_n_plans, valid.size, valid.size)
        }
    )
} catch (e: JsonSyntaxException) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_malformed))
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

private fun parseScenariosJson(context: android.content.Context, text: String): ParsedPreview<List<ScenarioJsonFile>> = try {
    val gson = Gson()
    val trimmed = text.trimStart()
    val raw: List<ScenarioJsonFile>? = if (trimmed.startsWith("[")) {
        val type = object : TypeToken<List<ScenarioJsonFile>>() {}.type
        gson.fromJson<List<ScenarioJsonFile>?>(text, type)
    } else {
        gson.fromJson(text, ScenarioJsonFile::class.java)?.let { listOf(it) }
    }
    val list = raw ?: emptyList()
    val valid = list.filter { !it.name.isNullOrBlank() }
    if (valid.isEmpty()) ParsedPreview.Err(context.getString(R.string.ui2_parse_no_scenarios))
    else ParsedPreview.Ok(
        valid,
        when (valid.size) {
            1 -> {
                val s = valid.first()
                val invCount = s.inverters?.size ?: 0
                val panelCount = s.panels?.size ?: 0
                val battCount = s.batteries?.size ?: 0
                context.getString(R.string.ui2_parse_one_scenario,
                    s.name, invCount, panelCount, battCount)
            }
            else -> context.resources.getQuantityString(
                R.plurals.ui2_parse_n_scenarios, valid.size, valid.size)
        }
    )
} catch (e: JsonSyntaxException) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_malformed))
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

// ── Card UI ────────────────────────────────────────────────────────────────

@Composable
private fun HintCard() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.ui2_ie_hint_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.ui2_ie_hint_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.ui2_ie_hint_restart),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ComparisonResultsNote() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Share, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui2_ie_compare_note_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.ui2_ie_compare_note_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Unified row used for both export and import — the trailing icon carries
 *  the verb. Export rows pass `Icons.Default.Share` (system chooser will pick
 *  the destination); import rows pass `Icons.Default.FileUpload`. */
@Composable
private fun DataRow(
    title: String,
    format: String,
    helpText: String,
    actionIcon: ImageVector,
    actionTint: Color,
    busy: Boolean,
    anyBusy: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = !anyBusy, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            format,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(helpText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(actionIcon, contentDescription = title,
                    tint = if (anyBusy) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                           else actionTint)
            }
        }
    }
}
