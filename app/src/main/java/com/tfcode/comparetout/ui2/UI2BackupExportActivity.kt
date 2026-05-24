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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────
// Backup & Export — drawer-launched management screen for app-wide exports.
//
// Phase C-lite scope: bulk JSON for plans and scenarios. The legacy
// MainActivity also exported "all comparisons" as CSV across every saved
// (scenario, plan) combination — that table is not part of the UI2 mental
// model (UI2 always computes against the current selection), so it is
// intentionally left out here. Comparison results are exported per panel
// from the Compare tab's Share button instead.
//
// The DB-snapshot scope chooser is deferred to a later iteration — when it
// lands it will appear as a row above these two.
// ──────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2BackupExportActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                BackupExportScreen(onClose = { finish() })
            }
        }
    }
}

@HiltViewModel
class UI2BackupExportViewModel @Inject constructor(
    private val repository: ToutcRepository
) : ViewModel() {

    /** Map<PricePlan, List<DayRate>> → JSON list (legacy bulk shape). */
    suspend fun allPlansJson(): String? = withContext(Dispatchers.IO) {
        val map = repository.getAllPricePlansForExport() ?: return@withContext null
        if (map.isEmpty()) null else JsonTools.createPricePlanJson(map)
    }

    /** List<ScenarioComponents> → JSON list (legacy bulk shape). */
    suspend fun allScenariosJson(): String? = withContext(Dispatchers.IO) {
        val list = repository.getAllScenariosForExport() ?: return@withContext null
        if (list.isEmpty()) null else JsonTools.createScenarioList(list)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupExportScreen(
    viewModel: UI2BackupExportViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (showHints, toggleShowHints) = rememberShowHints()
    var showDrawer by remember { mutableStateOf(false) }
    var workingLabel by remember { mutableStateOf<String?>(null) }

    // Build payload on IO, share on Main. `workingLabel` drives a single
    // ambient spinner so the user can tell something is happening for the
    // larger payloads (all-comparisons CSV can be tens of thousands of rows).
    fun run(label: String, subject: String, format: ShareFormat, build: suspend () -> String?) {
        if (workingLabel != null) return  // ignore re-taps while a job is in flight
        workingLabel = label
        scope.launch {
            val payload = runCatching { build() }.getOrNull()
            workingLabel = null
            if (!payload.isNullOrEmpty()) {
                context.shareText(payload, format, subject)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Export") },
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
        }
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
                item("plans") {
                    ExportRow(
                        title = "All supplier plans",
                        format = "JSON",
                        helpText = "Every tariff stored in the app, including day-rate schedules. " +
                                "Re-importable via the legacy import path.",
                        busy = workingLabel == "plans",
                        anyBusy = workingLabel != null,
                        onClick = {
                            run("plans", "All supplier plans", ShareFormat.JSON) {
                                viewModel.allPlansJson()
                            }
                        }
                    )
                }
                item("scenarios") {
                    ExportRow(
                        title = "All scenarios",
                        format = "JSON",
                        helpText = "Every saved scenario with all of its components — load profile, " +
                                "PV, batteries, hot water, EV.",
                        busy = workingLabel == "scenarios",
                        anyBusy = workingLabel != null,
                        onClick = {
                            run("scenarios", "All scenarios", ShareFormat.JSON) {
                                viewModel.allScenariosJson()
                            }
                        }
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
}

@Composable
private fun HintCard() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("About Backup & Export",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                "Bulk exports for sharing the app's data with another device, a " +
                    "spreadsheet, or a backup. For exporting a single plan or scenario, " +
                    "use the Share button on its row instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                    "directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExportRow(
    title: String,
    format: String,
    helpText: String,
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
                Icon(Icons.Default.Share, contentDescription = "Share $title",
                    tint = if (anyBusy) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                           else MaterialTheme.colorScheme.primary)
            }
        }
    }
}
