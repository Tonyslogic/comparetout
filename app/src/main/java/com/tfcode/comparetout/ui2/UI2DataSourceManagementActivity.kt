@file:Suppress("AssignedValueIsNeverRead")

package com.tfcode.comparetout.ui2

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tfcode.comparetout.ComparisonUIViewModel.Importer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ──────────────────────────────────────────────────────────────────────────
// UI2 Data Source Management
//
// Three accordions, one per importer type — each collapses to a one-line
// summary (name + status chip) and expands to surface the bits the user
// needs to operate the source: credentials, system list, fetch + schedule,
// data deletion. Plus, for Home Assistant only, an "Energy sensors" panel
// that reflects what HA's Energy dashboard reports.
//
// Everything else (key stats, graphs, costing, generation) is already
// surfaced on the dashboard or in the scenario wizard — this screen is
// deliberately limited to "things you do to a source", not "things you
// see from a source".
// ──────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2DataSourceManagementActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                DataSourceManagementScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataSourceManagementScreen(
    viewModel: UI2DataSourceManagementViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val alphaRaw by viewModel.alpha.observeAsState()
    val haRaw by viewModel.ha.observeAsState()
    val esbnRaw by viewModel.esbn.observeAsState()
    val fetchMap by viewModel.fetchStatus.observeAsState(emptyMap())
    val haSensors by viewModel.haSensors.observeAsState()
    val busy by viewModel.busy.observeAsState(false)
    val toast by viewModel.toast.observeAsState()

    // Merge the persistent SourceState (DataStore + DB) with the live
    // WorkManager status (per-SN fetching/scheduled). Done in the activity so
    // the VM doesn't have to re-emit a full SourceState every time a worker
    // ticks.
    val alpha = alphaRaw?.withLiveFetch(fetchMap)
    val ha = haRaw?.withLiveFetch(fetchMap)
    val esbn = esbnRaw?.withLiveFetch(fetchMap)
    val (showHints, toggleShowHints) = rememberShowHints()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDrawer by remember { mutableStateOf(false) }

    LaunchedEffect(toast?.tag) {
        val t = toast ?: return@LaunchedEffect
        scope.launch {
            snackbarHostState.showSnackbar(t.message)
            viewModel.acknowledgeToast()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Data Source Management") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showHints) {
                    item("intro") { IntroHint() }
                }
                item("alpha") {
                    SourceAccordion(
                        title = "AlphaESS Cloud",
                        subtitle = "Inverter sync via OpenAPI",
                        state = alpha,
                        showHints = showHints,
                        body = {
                            AlphaSection(
                                state = alpha,
                                showHints = showHints,
                                onSetCredentials = viewModel::setAlphaCredentials,
                                onSelect = viewModel::selectAlphaSystem,
                                onFetch = viewModel::fetchAlpha,
                                onCancel = viewModel::cancelFetch,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange
                            )
                        }
                    )
                }
                item("ha") {
                    SourceAccordion(
                        title = "Home Assistant",
                        subtitle = "WebSocket + Energy dashboard",
                        state = ha,
                        showHints = showHints,
                        body = {
                            HASection(
                                state = ha,
                                sensors = haSensors,
                                showHints = showHints,
                                onRediscover = viewModel::discoverHA,
                                onFetch = viewModel::fetchHA,
                                onCancel = viewModel::cancelFetch,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange
                            )
                        }
                    )
                }
                item("esbn") {
                    SourceAccordion(
                        title = "ESBN Smart Meter",
                        subtitle = "HDF file import (cloud sync deprecated)",
                        state = esbn,
                        showHints = showHints,
                        body = {
                            EsbnSection(
                                state = esbn,
                                showHints = showHints,
                                onImportFile = viewModel::importEsbnFile,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange
                            )
                        }
                    )
                }
            }

            if (busy) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            // Right-side menu, identical pattern to every other UI2 screen.
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
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize()) {
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
private fun IntroHint() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("About data sources",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                "Real-world energy data flows from your inverter or smart meter into the " +
                    "dashboard and Compare tab. Set credentials, pick which system to track, " +
                    "and decide whether the app should refresh automatically each day. " +
                    "Stats and graphs themselves live in the dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceAccordion(
    title: String,
    subtitle: String,
    state: SourceState?,
    showHints: Boolean,
    body: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(state)
                Column(Modifier.weight(1f)) {
                    Text(title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val systemCount = state?.systems?.size ?: 0
                if (systemCount > 0 && !expanded) {
                    Text("$systemCount system" + if (systemCount == 1) "" else "s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    body()
                }
            }
        }
    }
}

/** Compact "connected / not configured / invalid" indicator. */
@Composable
private fun StatusChip(state: SourceState?) {
    val (icon, tint, label) = when {
        state == null -> Triple(Icons.Outlined.Warning,
            MaterialTheme.colorScheme.outline, "Loading")
        state.importer == Importer.ESBNHDF -> Triple(Icons.Default.UploadFile,
            MaterialTheme.colorScheme.primary, "File")
        !state.credentialsConfigured -> Triple(Icons.Default.CloudOff,
            MaterialTheme.colorScheme.outline, "Not set")
        !state.credentialsKnownGood -> Triple(Icons.Outlined.Warning,
            MaterialTheme.colorScheme.error, "Invalid")
        else -> Triple(Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary, "Ready")
    }
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = CircleShape,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

// ── AlphaESS section ──────────────────────────────────────────────────

@Composable
private fun AlphaSection(
    state: SourceState?,
    showHints: Boolean,
    onSetCredentials: (String, String) -> Unit,
    onSelect: (String) -> Unit,
    onFetch: (String, LocalDateTime) -> Unit,
    onCancel: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    var pendingFetch by remember { mutableStateOf<ManagedSystem?>(null) }
    if (showHints) {
        HintLine("AlphaESS uses your AlphaCloud OpenAPI keys — generate them in the developer portal.")
    }
    CredentialStrip(
        configured = state?.credentialsConfigured == true,
        good = state?.credentialsKnownGood == true,
        onEdit = { showCreds = true }
    )
    SystemList(
        systems = state?.systems.orEmpty(),
        selected = state?.selectedSn,
        canFetch = state?.credentialsKnownGood == true,
        onSelect = onSelect,
        onFetch = { sn ->
            val sys = state?.systems?.firstOrNull { it.sysSn == sn } ?: return@SystemList
            pendingFetch = sys
        },
        onCancel = onCancel,
        onDelete = { sys -> pendingDelete = sys }
    )
    if (showCreds) {
        CredentialDialog(
            title = "AlphaESS credentials",
            userLabel = "App ID",
            passLabel = "App Secret",
            initialUser = "",
            onDismiss = { showCreds = false },
            onSubmit = { u, p ->
                onSetCredentials(u, p)
                showCreds = false
            }
        )
    }
    pendingDelete?.let { sys ->
        DeleteDialog(
            sysSn = sys.sysSn,
            availableStart = sys.startDate,
            availableEnd = sys.endDate,
            onDismiss = { pendingDelete = null },
            onDeleteAll = { onDeleteAll(sys.sysSn); pendingDelete = null },
            onDeleteRange = { f, t -> onDeleteRange(sys.sysSn, f, t); pendingDelete = null }
        )
    }
    pendingFetch?.let { sys ->
        FetchStartDialog(
            sysSn = sys.sysSn,
            lastDataDate = sys.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { pendingFetch = null },
            onConfirm = { start ->
                onFetch(sys.sysSn, start)
                pendingFetch = null
            }
        )
    }
}

// ── Home Assistant section ─────────────────────────────────────────────

@Composable
private fun HASection(
    state: SourceState?,
    sensors: HASensorSnapshot?,
    showHints: Boolean,
    onRediscover: (String, String) -> Unit,
    onFetch: (LocalDateTime) -> Unit,
    onCancel: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    var pendingFetch by remember { mutableStateOf<ManagedSystem?>(null) }
    if (showHints) {
        HintLine("Set up the Energy dashboard in Home Assistant first — that's where these sensors come from.")
    }
    CredentialStrip(
        configured = state?.credentialsConfigured == true,
        good = state?.credentialsKnownGood == true,
        onEdit = { showCreds = true }
    )

    SystemList(
        systems = state?.systems.orEmpty(),
        selected = state?.selectedSn ?: "HomeAssistant",
        // Match AlphaESS: visible whenever credentials are usable. The VM
        // toasts "Discover sensors first" if the underlying worker needs
        // them — keeping the UI's affordances symmetrical across sources
        // is more valuable than gating the button here.
        canFetch = state?.credentialsKnownGood == true,
        onSelect = { /* HA has a single canonical system */ },
        onFetch = { sn ->
            val sys = state?.systems?.firstOrNull { it.sysSn == sn } ?: return@SystemList
            pendingFetch = sys
        },
        onCancel = onCancel,
        onDelete = { sys -> pendingDelete = sys }
    )

    // Sensors are a configuration detail most users only revisit when
    // something's wrong. Hide behind a nested accordion so the section's
    // primary actions (fetch / delete) stay above the fold.
    HASensorsAccordion(
        sensors = sensors,
        showHints = showHints,
        canRediscover = state?.credentialsConfigured == true,
        onRediscover = {
            // Re-discovery needs current credentials — we ask again rather
            // than decrypt-and-reuse, so users see what's about to be sent.
            showCreds = true
        }
    )

    PushToHaToggle()

    if (showCreds) {
        CredentialDialog(
            title = "Home Assistant",
            userLabel = "Host URL",
            passLabel = "Long-lived access token",
            initialUser = "ws://homeassistant.local:8123/api/websocket",
            onDismiss = { showCreds = false },
            onSubmit = { h, t ->
                onRediscover(h, t)
                showCreds = false
            }
        )
    }
    pendingDelete?.let { sys ->
        DeleteDialog(
            sysSn = sys.sysSn,
            availableStart = sys.startDate,
            availableEnd = sys.endDate,
            onDismiss = { pendingDelete = null },
            onDeleteAll = { onDeleteAll(sys.sysSn); pendingDelete = null },
            onDeleteRange = { f, t -> onDeleteRange(sys.sysSn, f, t); pendingDelete = null }
        )
    }
    pendingFetch?.let { sys ->
        FetchStartDialog(
            sysSn = sys.sysSn,
            lastDataDate = sys.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { pendingFetch = null },
            onConfirm = { start ->
                onFetch(start)
                pendingFetch = null
            }
        )
    }
}

/**
 * Collapsible "Energy sensors" panel — collapsed by default so it doesn't
 * push the primary actions (fetch / delete) off-screen. Header shows a
 * one-line summary of what's been discovered (or "Not yet discovered").
 */
@Composable
private fun HASensorsAccordion(
    sensors: HASensorSnapshot?,
    showHints: Boolean,
    canRediscover: Boolean,
    onRediscover: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = when {
        sensors == null -> "Not yet discovered"
        else -> {
            val g = sensors.grid.size
            val e = sensors.gridExports.size
            val s = sensors.solar.size
            val b = sensors.batteries.size
            "$g grid in · $e grid out · $s solar · $b battery"
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Energy sensors",
                        style = MaterialTheme.typography.titleSmall)
                    Text(summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showHints && sensors == null) {
                            Text("Tap Re-discover after enabling the Energy dashboard in HA.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onRediscover, enabled = canRediscover) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (sensors == null) "Discover" else "Re-discover")
                        }
                    }
                    if (sensors != null) {
                        SensorList("Grid import", sensors.grid)
                        SensorList("Grid export", sensors.gridExports)
                        SensorList("Solar", sensors.solar)
                        if (sensors.batteries.isNotEmpty()) {
                            Text("Batteries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            sensors.batteries.forEach { (charge, discharge) ->
                                Text("  ↓ ${discharge ?: "—"}  ↑ ${charge ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace)
                            }
                        }
                    } else if (!canRediscover) {
                        Text("Set credentials first to discover sensors.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorList(label: String, sensors: List<String>) {
    if (sensors.isEmpty()) return
    Text(label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    sensors.forEach { s ->
        Text("  · $s",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PushToHaToggle() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Push to Home Assistant",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Coming soon · publish another source's data back to HA as a sensor",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = false, onCheckedChange = null, enabled = false)
        }
    }
}

// ── ESBN section ──────────────────────────────────────────────────────

@Composable
private fun EsbnSection(
    state: SourceState?,
    showHints: Boolean,
    onImportFile: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onImportFile(uri.toString())
    }
    // Always visible — even if hints off — because it explains why credentials
    // are absent. Calling it a "hint" understates the user-blocking nature.
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Warning, null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp))
            Text(
                "ESBN cloud sync is deprecated. Download your HDF file from the ESBN " +
                    "customer portal and import it below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
    Button(
        onClick = { pickFile.launch("*/*") },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Download, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Import HDF file")
    }
    SystemList(
        systems = state?.systems.orEmpty(),
        selected = state?.selectedSn,
        canFetch = false,
        onSelect = { /* read-only */ },
        onFetch = { /* no cloud fetch */ },
        onCancel = { /* no cloud fetch */ },
        onDelete = { sys -> pendingDelete = sys }
    )
    if (state?.systems.isNullOrEmpty() && showHints) {
        Text("Imported MPRNs will appear here once a file has been ingested.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    pendingDelete?.let { sys ->
        DeleteDialog(
            sysSn = sys.sysSn,
            availableStart = sys.startDate,
            availableEnd = sys.endDate,
            onDismiss = { pendingDelete = null },
            onDeleteAll = { onDeleteAll(sys.sysSn); pendingDelete = null },
            onDeleteRange = { f, t -> onDeleteRange(sys.sysSn, f, t); pendingDelete = null }
        )
    }
}

// ── Shared widgets ─────────────────────────────────────────────────────

@Composable
private fun CredentialStrip(
    configured: Boolean,
    good: Boolean,
    onEdit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Credentials", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        !configured -> "Not configured"
                        !good -> "Configured · last check failed"
                        else -> "Configured · last check OK"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (configured && !good)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (configured) "Update" else "Set")
            }
        }
    }
}

@Composable
private fun SystemList(
    systems: List<ManagedSystem>,
    selected: String?,
    canFetch: Boolean,
    onSelect: (String) -> Unit,
    onFetch: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (ManagedSystem) -> Unit
) {
    if (systems.isEmpty()) {
        Text("No systems configured yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        systems.forEach { sys -> SystemRow(sys, selected, canFetch, onSelect, onFetch, onCancel, onDelete) }
    }
}

@Composable
private fun SystemRow(
    sys: ManagedSystem,
    selected: String?,
    canFetch: Boolean,
    onSelect: (String) -> Unit,
    onFetch: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (ManagedSystem) -> Unit
) {
    val isSelected = sys.sysSn == selected
    val active = sys.fetching || sys.scheduled
    Surface(
        color = when {
            sys.fetching -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                sys.fetching -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                isSelected   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else         -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        ),
        modifier = Modifier.fillMaxWidth().clickable { onSelect(sys.sysSn) }
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sys.fetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(sys.sysSn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sys.scheduled) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape
                    ) {
                        Text("Auto-sync",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            // Status line — tells the user exactly what's happening (data range
            // when idle, "Fetching X" when a catch-up is mid-flight).
            val statusText = when {
                sys.fetching && sys.progress != null ->
                    "Fetching · ${sys.progress.take(40)}"
                sys.fetching -> "Fetching…"
                sys.dateRange != null -> sys.dateRange
                else -> "No data yet"
            }
            Text(statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (sys.fetching) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canFetch) {
                    OutlinedButton(
                        onClick = { if (active) onCancel(sys.sysSn) else onFetch(sys.sysSn) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (active) Icons.Default.Stop else Icons.Default.PlayArrow,
                            null, Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (active) "Stop" else "Fetch")
                    }
                }
                OutlinedButton(
                    onClick = { onDelete(sys) },
                    enabled = !sys.fetching,   // don't let the user delete mid-fetch
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp),
                        tint = if (sys.fetching) MaterialTheme.colorScheme.outline
                               else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete",
                        color = if (sys.fetching) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun HintLine(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Outlined.Warning, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Text(text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CredentialDialog(
    title: String,
    userLabel: String,
    passLabel: String,
    initialUser: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(userLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text(passLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(user.trim(), pass.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Two-step delete flow.
 *
 *  Step 1: ask "All readings" vs "A date range…" — both options need explicit
 *          confirmation, so this dialog never deletes on first tap.
 *  Step 2 (range only): show an M3 DateRangePicker bounded to the system's
 *          known data range, then "Delete N readings between FROM and TO?".
 *          A second confirmation prevents fat-finger range deletion.
 *
 * If the system has no data range yet (start/end null), only "All readings"
 * is offered — there's nothing to range-pick against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteDialog(
    sysSn: String,
    availableStart: String?,
    availableEnd: String?,
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteRange: (LocalDateTime, LocalDateTime) -> Unit
) {
    val isoFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val start = remember(availableStart) {
        runCatching { availableStart?.let { LocalDate.parse(it, isoFmt) } }.getOrNull()
    }
    val end = remember(availableEnd) {
        runCatching { availableEnd?.let { LocalDate.parse(it, isoFmt) } }.getOrNull()
    }
    val hasRange = start != null && end != null

    var mode by remember { mutableStateOf<DeleteMode>(DeleteMode.Choose) }

    when (val m = mode) {
        DeleteMode.Choose -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete data for \"$sysSn\"?") },
            // Both choices live in the body as full-width OutlinedButtons so
            // neither looks secondary. The AlertDialog "confirmButton" is
            // demoted to Cancel — confirmation always happens on the next
            // step, never on this initial chooser.
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose how much data to remove. Credentials and " +
                        "system configuration are preserved either way.",
                        style = MaterialTheme.typography.bodyMedium)
                    if (hasRange) {
                        Text("Available data: $start ↔ $end",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("No data has been recorded for this system yet.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = { mode = DeleteMode.ConfirmAll },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("All readings", color = MaterialTheme.colorScheme.error)
                    }
                    if (hasRange) {
                        OutlinedButton(
                            onClick = { mode = DeleteMode.PickRange },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pick a date range…")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.Cancel, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
            }
        )

        DeleteMode.ConfirmAll -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete ALL data?") },
            text = {
                Text("This permanently removes every reading for \"$sysSn\". " +
                    "There is no undo.",
                    style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(onClick = onDeleteAll,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { mode = DeleteMode.Choose }) {
                    Text("Back")
                }
            }
        )

        is DeleteMode.PickRange -> {
            val pickerState = rememberDateRangePickerState(
                initialSelectedStartDateMillis = start?.toUtcMillis(),
                initialSelectedEndDateMillis = end?.toUtcMillis(),
                yearRange = (start!!.year)..(end!!.year)
            )
            // Re-derive the selection as the user picks. M3's own
            // DatePickerDialog has a long-running layout bug where a
            // DateRangePicker's lazy grid demands more height than the
            // dialog allocates and pushes the confirm row off-screen, so
            // we drop down to a plain Dialog and pin the buttons ourselves.
            val pickedStart = pickerState.selectedStartDateMillis?.let {
                LocalDate.ofEpochDay(it / 86_400_000L)
            }
            val pickedEnd = pickerState.selectedEndDateMillis?.let {
                LocalDate.ofEpochDay(it / 86_400_000L)
            }
            val days = if (pickedStart != null && pickedEnd != null)
                (pickedEnd.toEpochDay() - pickedStart.toEpochDay()).toInt() + 1 else 0
            StickyButtonPickerDialog(
                onDismiss = onDismiss,
                confirmEnabled = pickedStart != null && pickedEnd != null,
                confirmLabel = if (pickedStart != null && pickedEnd != null)
                    "OK · delete $days day" + (if (days == 1) "" else "s")
                else "Pick start & end",
                confirmIcon = Icons.Default.Delete,
                onBack = { mode = DeleteMode.Choose },
                onConfirm = {
                    mode = DeleteMode.ConfirmRange(
                        pickerState.selectedStartDateMillis!!,
                        pickerState.selectedEndDateMillis!!
                    )
                }
            ) {
                DateRangePicker(
                    state = pickerState,
                    title = {
                        Text("Range to delete from \"$sysSn\"",
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp))
                    },
                    headline = {
                        Text(
                            if (pickedStart != null && pickedEnd != null)
                                "$pickedStart  →  $pickedEnd   ·   $days day" +
                                        (if (days == 1) "" else "s")
                            else "Tap a start date, then an end date",
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                        )
                    },
                    showModeToggle = false
                )
            }
        }

        is DeleteMode.ConfirmRange -> {
            val from = remember(m.startMs) { LocalDate.ofEpochDay(m.startMs / 86_400_000L) }
            val to   = remember(m.endMs)   { LocalDate.ofEpochDay(m.endMs   / 86_400_000L) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Delete range?") },
                text = {
                    Text(
                        "Every reading between $from and $to for \"$sysSn\" will be " +
                            "permanently removed. There is no undo.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteRange(from.atStartOfDay(), to.atStartOfDay())
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete range")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mode = DeleteMode.Choose }) {
                        Text("Back")
                    }
                }
            )
        }
    }
}

private sealed class DeleteMode {
    object Choose : DeleteMode()
    object ConfirmAll : DeleteMode()
    object PickRange : DeleteMode()
    data class ConfirmRange(val startMs: Long, val endMs: Long) : DeleteMode()
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * Asks the user where the catch-up should begin. The default is one day after
 * the last date we already have (so a re-fetch picks up where it left off),
 * falling back to one year ago for a fresh source. The actual fetch then runs
 * from the chosen date through "yesterday" (the last complete day); the daily
 * worker scheduled by the VM tops up automatically each morning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FetchStartDialog(
    sysSn: String,
    lastDataDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val defaultStart = remember(lastDataDate) {
        lastDataDate?.plusDays(1) ?: LocalDate.now().minusYears(1)
    }
    val yesterday = remember { LocalDate.now().minusDays(1) }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = defaultStart.toUtcMillis(),
        yearRange = (defaultStart.year.coerceAtMost(yesterday.year - 5))..yesterday.year
    )
    val picked = pickerState.selectedDateMillis?.let {
        LocalDate.ofEpochDay(it / 86_400_000L)
    }
    val days = if (picked != null)
        (yesterday.toEpochDay() - picked.toEpochDay()).toInt() + 1 else 0
    val confirmLabel = when {
        picked == null -> "Pick a start date"
        picked.isAfter(yesterday) -> "Pick an earlier date"
        else -> "OK · fetch $days day" + (if (days == 1) "" else "s")
    }
    StickyButtonPickerDialog(
        onDismiss = onDismiss,
        confirmEnabled = picked != null && !picked.isAfter(yesterday),
        confirmLabel = confirmLabel,
        confirmIcon = Icons.Default.PlayArrow,
        onBack = onDismiss,
        backLabel = "Cancel",
        onConfirm = { onConfirm(picked!!.atStartOfDay()) }
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text("Fetch start date for \"$sysSn\"",
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp))
            },
            headline = {
                Text(
                    when {
                        picked == null -> "Pick a date · catch-up runs up to $yesterday"
                        picked.isAfter(yesterday) -> "Must be no later than $yesterday"
                        else -> "$picked  →  $yesterday   ·   $days day" +
                                (if (days == 1) "" else "s")
                    },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                )
            },
            showModeToggle = false
        )
    }
}

/**
 * A small dialog wrapper that places a picker (DatePicker / DateRangePicker)
 * in a height-weighted slot, then pins a Back/Cancel + Confirm button row
 * at the bottom of the dialog. Solves the M3 layout problem where the
 * picker's lazy grid demands more height than DatePickerDialog allocates
 * and the action row scrolls off the bottom of the screen.
 */
@Composable
private fun StickyButtonPickerDialog(
    onDismiss: () -> Unit,
    confirmEnabled: Boolean,
    confirmLabel: String,
    confirmIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onBack: () -> Unit,
    backLabel: String = "Back",
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Picker fills the available vertical space first; whatever
                // height remains is given to the bottom action row, which
                // is therefore guaranteed to stay on-screen.
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    content()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text(backLabel) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = confirmEnabled,
                        onClick = onConfirm
                    ) {
                        Icon(confirmIcon, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

/**
 * Layer the live WorkManager state ([FetchStatus] per SN) on top of a
 * persisted [SourceState]. The merged value is what the row composables
 * consume so they can show "Fetching… 2024-08-01" without the VM having
 * to keep two parallel copies of the system list.
 */
private fun SourceState.withLiveFetch(
    fetchMap: Map<String, FetchStatus>
): SourceState = copy(
    systems = systems.map { sys ->
        val live = fetchMap[sys.sysSn] ?: return@map sys
        sys.copy(
            scheduled = live.scheduled || sys.scheduled,
            fetching = live.running,
            progress = live.progress
        )
    }
)
