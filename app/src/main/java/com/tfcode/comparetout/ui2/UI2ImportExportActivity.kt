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
    fun summary(noun: String): String = when {
        total == 0       -> "Nothing to import"
        replaced == 0    -> "Imported $added $noun"
        added == 0       -> "Replaced $replaced $noun"
        else             -> "Imported $total $noun ($replaced replaced, $added added)"
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
                e.message ?: "Could not read the picked file"
            )
        }
        val result = runCatching { snapshotImporter.validate(staged) }.getOrElse { e ->
            SnapshotImporter.Validation.FileError(
                e.message ?: "Could not validate the picked file"
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
        }
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
        ImportOutcome(replaced, added)
    }
}

/** A parsed import payload waiting on the user's clobber decision. */
private sealed class PendingImport {
    abstract val countLabel: String
    abstract val noun: String       // singular noun for the snackbar
    data class Plans(val list: List<PricePlanJsonFile>) : PendingImport() {
        override val countLabel = if (list.size == 1) "1 supplier plan" else "${list.size} supplier plans"
        override val noun = if (list.size == 1) "plan" else "plans"
    }
    data class Scenarios(val list: List<ScenarioJsonFile>) : PendingImport() {
        override val countLabel = if (list.size == 1) "1 scenario" else "${list.size} scenarios"
        override val noun = if (list.size == 1) "scenario" else "scenarios"
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
            val msg = result?.summary() ?: "Snapshot import failed"
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
                snackbarHostState.showSnackbar("Snapshot export failed")
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
            val msg = outcome?.summary(import.noun) ?: "Import failed"
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
                title = { Text("Import / Export") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                item("export_header") { SectionLabel("Export", Icons.Default.FileDownload) }
                item("export_plans") {
                    DataRow(
                        title = "All supplier plans",
                        format = "JSON",
                        helpText = "Every tariff stored in the app, including day-rate schedules.",
                        actionIcon = Icons.Default.Share,
                        actionTint = MaterialTheme.colorScheme.primary,
                        busy = workingLabel == "plans",
                        anyBusy = workingLabel != null,
                        onClick = {
                            runExport("plans", "All supplier plans", ShareFormat.JSON) {
                                viewModel.allPlansJson()
                            }
                        }
                    )
                }
                item("export_scenarios") {
                    DataRow(
                        title = "All scenarios",
                        format = "JSON",
                        helpText = "Every saved scenario with all of its components — load profile, " +
                                "PV, batteries, hot water, EV.",
                        actionIcon = Icons.Default.Share,
                        actionTint = MaterialTheme.colorScheme.primary,
                        busy = workingLabel == "scenarios",
                        anyBusy = workingLabel != null,
                        onClick = {
                            runExport("scenarios", "All scenarios", ShareFormat.JSON) {
                                viewModel.allScenariosJson()
                            }
                        }
                    )
                }
                item("export_db_all") {
                    DataRow(
                        title = "Everything (database)",
                        format = "SQLite",
                        helpText = "A full SQLite snapshot — all plans, scenarios, data sources, and " +
                                "precomputed simulation results. Re-imports byte-for-byte on another device.",
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
                                subject = "Eco Power Optimiser snapshot"
                            )
                        }
                    )
                }
                item("export_db_selection") {
                    DataRow(
                        title = "Selected scenarios / sources (database)",
                        format = "SQLite",
                        helpText = "Pick which scenarios and data sources to include. All supplier " +
                                "plans are bundled so imported scenarios can find their tariffs.",
                        actionIcon = Icons.Default.Backup,
                        actionTint = MaterialTheme.colorScheme.primary,
                        busy = workingLabel == "snapshot-selection",
                        anyBusy = workingLabel != null,
                        onClick = { showSnapshotPicker = true }
                    )
                }

                item("import_header") { SectionLabel("Import", Icons.Default.FileUpload) }
                item("import_plans") {
                    DataRow(
                        title = "Import supplier plans",
                        format = "JSON",
                        helpText = "Pick a JSON file (or paste) to add plans to your library. " +
                                "Plans whose name matches an existing one can be replaced or kept alongside.",
                        actionIcon = Icons.Default.FileUpload,
                        actionTint = MaterialTheme.colorScheme.tertiary,
                        busy = false,
                        anyBusy = false,
                        onClick = { importTarget = ImportTarget.PLANS }
                    )
                }
                item("import_scenarios") {
                    DataRow(
                        title = "Import scenarios",
                        format = "JSON",
                        helpText = "Pick a JSON file (or paste) to add scenarios to your library. " +
                                "Scenarios whose name matches an existing one can be replaced or kept alongside.",
                        actionIcon = Icons.Default.FileUpload,
                        actionTint = MaterialTheme.colorScheme.tertiary,
                        busy = false,
                        anyBusy = false,
                        onClick = { importTarget = ImportTarget.SCENARIOS }
                    )
                }
                item("import_db") {
                    DataRow(
                        title = "Import database snapshot",
                        format = "SQLite",
                        helpText = "Pick a .db file exported from another device. The file is " +
                                "validated against the current schema before any rows are written; " +
                                "you'll see a preview and a Replace toggle before commit.",
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
                title = "Import supplier plans",
                hint = "Accepts the JSON shape produced by the Share button on a plan, " +
                        "or the bulk export above.",
                applyLabel = "Continue",
                communityUrl = PricePlanDownloader.RATES_URL,
                communityNote = "Community-maintained Irish supplier tariffs — may be out of " +
                    "date. You can edit any plan after importing.",
                llmPrompt = PricePlanDownloader.LLM_PROMPT,
                parse = ::parsePricePlansJson,
                onApply = {
                    pendingImport = PendingImport.Plans(it)
                    importTarget = null
                },
                onDismiss = { importTarget = null }
            )
            ImportTarget.SCENARIOS -> UI2ImportSheet(
                title = "Import scenarios",
                hint = "Accepts the JSON shape produced by the Share button on a scenario, " +
                        "or the bulk export above.",
                applyLabel = "Continue",
                parse = ::parseScenariosJson,
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
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import ${p.countLabel}") },
            text = {
                Text(
                    "If a ${p.noun.removeSuffix("s")} with the same name already exists, " +
                        "should the imported version replace it, or be kept alongside the existing one?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { commitImport(clobber = true) }) { Text("Replace existing") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { commitImport(clobber = false) }) { Text("Keep both") }
                    TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
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
                    title = { Text("Snapshot rejected") },
                    text = {
                        Column {
                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                            if (state.details.isNotEmpty()) {
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    "Details:",
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
                                        "…and ${state.details.size - 5} more.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { snapshotImportState = null }) { Text("OK") }
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
            title = { Text("Import complete") },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { snapshotSourcesResult = null }) { Text("OK") }
            }
        )
    }

    // ── Snapshot picker — choose scenarios + sources + outputs toggle ──────
    if (showSnapshotPicker) {
        val scenarios by viewModel.scenarios.collectAsState()
        val sources by viewModel.sources.collectAsState()
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
                    subject = "Eco Power Optimiser snapshot"
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
        title = { Text("Export selection (database)") },
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
                        "Supplier plans are always included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(8.dp))

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    if (scenarios.isNotEmpty()) {
                        item("hdr_sc") { PickerHeader("Scenarios") }
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
                            PickerHeader("Data sources")
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
                                "No scenarios or data sources available to export.",
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
                            "Include simulation results",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Costings, panel data, sim time series. Recomputable from inputs.",
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
            ) { Text("Export") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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

private fun SnapshotImporter.CommitResult.summary(): String {
    val parts = mutableListOf<String>()
    val plans = plansAdded + plansReplaced
    if (plans > 0) {
        parts += if (plansReplaced == 0) "$plans plan${plural(plans)}"
                 else "$plans plan${plural(plans)} ($plansReplaced replaced)"
    }
    val scenarios = scenariosAdded + scenariosReplaced
    if (scenarios > 0) {
        parts += if (scenariosReplaced == 0) "$scenarios scenario${plural(scenarios)}"
                 else "$scenarios scenario${plural(scenarios)} ($scenariosReplaced replaced)"
    }
    if (sourcesTouched > 0) parts += "$sourcesTouched source${plural(sourcesTouched)}"
    val skipped = plansSkipped + scenariosSkipped
    val skipFragment = if (skipped > 0) " — $skipped existing item${plural(skipped)} kept" else ""
    // Imported source rows are written outside Room's change tracking, so the
    // data-source lists won't refresh until the app is relaunched.
    val restartFragment = if (sourcesTouched > 0) " · restart the app to see data sources" else ""
    return if (parts.isEmpty()) "Nothing was imported$skipFragment"
           else "Imported ${parts.joinToString(", ")}$skipFragment$restartFragment"
}

private fun plural(n: Int) = if (n == 1) "" else "s"

@Composable
private fun SnapshotPreviewDialog(
    summary: SnapshotImporter.Summary,
    onCancel: () -> Unit,
    onConfirm: (replaceExisting: Boolean) -> Unit
) {
    var replaceExisting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import database snapshot") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SummaryLine("Supplier plans", summary.plans)
                SummaryLine(
                    "Scenarios",
                    summary.scenarios,
                    sample = summary.sampleScenarioNames
                )
                SummaryLine(
                    "Data sources",
                    summary.sources,
                    sample = summary.sampleSysSns
                )
                if (summary.transformedRows + summary.rawPowerRows + summary.rawEnergyRows > 0) {
                    Text(
                        "Source rows: ${summary.transformedRows} 5-min + " +
                            "${summary.rawPowerRows} power + " +
                            "${summary.rawEnergyRows} energy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    "PV data (paneldata, fetched from PVGIS) and load profile time-series " +
                        "are imported with each scenario. Costings and simulation outputs are " +
                        "regenerated locally from the imported inputs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (summary.sources > 0) {
                    Text(
                        "Restart the app after importing for data sources to appear.",
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
                            "Replace existing data with the same name / serial",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Off: keep what you have, skip imports that collide.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(replaceExisting) }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
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

internal fun parsePricePlansJson(text: String): ParsedPreview<List<PricePlanJsonFile>> = try {
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
    if (valid.isEmpty()) ParsedPreview.Err("No supplier plans found in the JSON.")
    else ParsedPreview.Ok(
        valid,
        when (valid.size) {
            1 -> "Parsed 1 supplier plan: ${valid.first().supplier ?: "?"} · ${valid.first().plan}"
            else -> "Parsed ${valid.size} supplier plans"
        }
    )
} catch (e: JsonSyntaxException) {
    ParsedPreview.Err(e.message ?: "Malformed JSON")
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

private fun parseScenariosJson(text: String): ParsedPreview<List<ScenarioJsonFile>> = try {
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
    if (valid.isEmpty()) ParsedPreview.Err("No scenarios found in the JSON.")
    else ParsedPreview.Ok(
        valid,
        when (valid.size) {
            1 -> {
                val s = valid.first()
                val invCount = s.inverters?.size ?: 0
                val panelCount = s.panels?.size ?: 0
                val battCount = s.batteries?.size ?: 0
                "Parsed scenario \"${s.name}\" · $invCount inverters · $panelCount panels · $battCount batteries"
            }
            else -> "Parsed ${valid.size} scenarios"
        }
    )
} catch (e: JsonSyntaxException) {
    ParsedPreview.Err(e.message ?: "Malformed JSON")
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
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
            Text("About Import / Export",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                "Move plans and scenarios between devices, share them with someone, or back " +
                    "them up to your file storage. For exporting a single plan or scenario use the " +
                    "Share button on its row; for importing one into a wizard step, the Import option " +
                    "on each accordion is more focused than the bulk paths here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Note: when a database snapshot includes data sources, restart the app after " +
                    "importing for those sources to appear.",
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
                Text("Comparison results",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Comparison results are exported from the Compare tab itself — pick your sources, " +
                    "scenarios and plans, then use the Share button on each result panel.",
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
