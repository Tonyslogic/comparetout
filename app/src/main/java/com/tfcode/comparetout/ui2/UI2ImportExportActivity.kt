package com.tfcode.comparetout.ui2

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
// DB snapshot import + export (raw .db / scoped subset) is gated behind a
// hint-card placeholder for a later iteration.
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

@HiltViewModel
class UI2ImportExportViewModel @Inject constructor(
    private val repository: ToutcRepository
) : ViewModel() {

    // ── export (unchanged from Phase C-lite) ────────────────────────────────

    suspend fun allPlansJson(): String? = withContext(Dispatchers.IO) {
        val map = repository.getAllPricePlansForExport() ?: return@withContext null
        if (map.isEmpty()) null else JsonTools.createPricePlanJson(map)
    }

    suspend fun allScenariosJson(): String? = withContext(Dispatchers.IO) {
        val list = repository.getAllScenariosForExport() ?: return@withContext null
        if (list.isEmpty()) null else JsonTools.createScenarioList(list)
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
            repository.getAllPricePlansNow()?.map { it.planName }?.toSet().orEmpty()
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
            repository.getAllScenariosForExport()
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
    val snackbarHostState = remember { SnackbarHostState() }

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

    Scaffold(
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
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

                item("comparenote") {
                    if (showHints) ComparisonResultsNote()
                }
                item("dbnote") {
                    if (showHints) DatabaseSnapshotNote()
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
}

private enum class ImportTarget { PLANS, SCENARIOS }

// ── Parsers ────────────────────────────────────────────────────────────────
//
// Both accept either a JSON *array* (the bulk-export shape) or a single
// *object* (the per-item share shape from Phase A) — exactly the same logic
// the legacy MainActivity importers do, but extracted out of the file-pick
// callback so the parser can run on a pasted text buffer.

private fun parsePricePlansJson(text: String): ParsedPreview<List<PricePlanJsonFile>> = try {
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

@Composable
private fun DatabaseSnapshotNote() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("Database snapshot — coming soon",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "A scoped SQLite snapshot (everything / single source / single scenario / PV data / " +
                    "+costs) will be added here for advanced users who want to query their data " +
                    "directly. Importing the matching snapshots will land in the same place.",
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
