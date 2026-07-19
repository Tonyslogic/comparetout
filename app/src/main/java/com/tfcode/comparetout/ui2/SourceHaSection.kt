package com.tfcode.comparetout.ui2

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tfcode.comparetout.ComparisonUIViewModel.Importer
import com.tfcode.comparetout.R
import com.tfcode.comparetout.importers.alphaess.BarcodeLabelReader
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformMeta
import com.tfcode.comparetout.region.RegionProfiles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/*
 * Home Assistant source section — extracted verbatim from UI2DataSourceManagementActivity.kt
 * (mega-refactor B5). Imports inherited; unused are cosmetic.
 */

// ── Home Assistant section ─────────────────────────────────────────────

@Composable
internal fun HASection(
    state: SourceState?,
    sensors: HASensorSnapshot?,
    showHints: Boolean,
    onRediscover: (String, String) -> Unit,
    onRediscoverStored: () -> Unit,
    onFetch: (LocalDateTime) -> Unit,
    onCancel: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit,
    onRemoveSource: () -> Unit,
    onDeviceChange: (String, String, Boolean) -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
    var showDeleteSource by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    var pendingFetch by remember { mutableStateOf<ManagedSystem?>(null) }
    if (showHints) {
        HintLine(stringResource(R.string.ui2_dsm_ha_hint))
    }
    CredentialStrip(
        configured = state?.credentialsConfigured == true,
        good = state?.credentialsKnownGood == true,
        onEdit = { showCreds = true },
        onDeleteSource = if (state?.credentialsConfigured == true || !state?.systems.isNullOrEmpty())
            ({ showDeleteSource = true }) else null
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
        // Re-discovery reuses the stored credentials — retyping a long-lived
        // token to refresh a sensor list was a dead end (users cancelled the
        // dialog and nothing happened). Changing credentials is the strip's
        // Edit action, which still opens the dialog.
        onRediscover = onRediscoverStored,
        onDeviceChange = onDeviceChange
    )

    // Backfill is a multi-step flow (source → timeframe → series → preview →
    // commit) — too much for a nested accordion, so it opens its own wizard.
    run {
        val context = LocalContext.current
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().clickable(
                enabled = state?.credentialsKnownGood == true
            ) {
                context.startActivity(
                    android.content.Intent(context, UI2HaBackfillActivity::class.java))
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui2_habf_title),
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (state?.credentialsKnownGood == true)
                            R.string.ui2_dsm_backfill_sub
                        else R.string.ui2_dsm_connect_ha_first),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showCreds) {
        CredentialDialog(
            title = stringResource(R.string.home_assistant),
            userLabel = stringResource(R.string.ui2_dsm_host_url),
            passLabel = stringResource(R.string.ui2_dsm_lla_token),
            initialUser = "ws://homeassistant.local:8123/api/websocket",
            onDismiss = { showCreds = false },
            onSubmit = { h, t ->
                onRediscover(h, t)
                showCreds = false
            }
        )
    }
    if (showDeleteSource) {
        DeleteSourceDialog(
            sourceName = stringResource(R.string.home_assistant),
            mentionsCredentials = true,
            onDismiss = { showDeleteSource = false },
            onConfirm = { onRemoveSource(); showDeleteSource = false }
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
internal fun HASensorsAccordion(
    sensors: HASensorSnapshot?,
    showHints: Boolean,
    canRediscover: Boolean,
    onRediscover: () -> Unit,
    onDeviceChange: (String, String, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = when {
        sensors == null -> stringResource(R.string.ui2_dsm_not_discovered)
        else -> {
            val d = sensors.devices.size
            stringResource(R.string.ui2_dsm_sensor_summary,
                sensors.grid.size, sensors.gridExports.size,
                sensors.solar.size, sensors.batteries.size) +
                if (d > 0) " " + pluralStringResource(R.plurals.ui2_dsm_n_devices, d, d) else ""
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
                    Text(stringResource(R.string.ui2_dsm_energy_sensors),
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
                            Text(stringResource(R.string.ui2_dsm_rediscover_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onRediscover, enabled = canRediscover) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(if (sensors == null) R.string.ui2_dsm_discover
                                                else R.string.ui2_dsm_rediscover))
                        }
                    }
                    if (sensors != null) {
                        if (sensors.serverRange != null) {
                            Text(stringResource(R.string.ui2_dsm_stats_on_server,
                                    sensors.serverRange),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HASensorTable(sensors)
                        if (sensors.devices.isNotEmpty()) {
                            Text(stringResource(R.string.ui2_dsm_individual_devices),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (showHints) {
                                Text(stringResource(R.string.ui2_dsm_devices_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HADeviceTable(sensors.devices, onDeviceChange)
                        }
                    } else if (!canRediscover) {
                        Text(stringResource(R.string.ui2_dsm_set_creds_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// Fixed column widths so the row layout never reflows when a different role
// is selected (a variable-width role button made everything after it jump).
internal val DEVICE_ROLE_COLUMN = 116.dp
internal val DEVICE_REMOVE_COLUMN = 68.dp

/**
 * "Individual devices" as a fixed-column Device | Role | Remove-from-load
 * table. Each row keeps its editable controls: role dropdown (Ignore / EV /
 * Hot water / Heat pump) and the "Remove from load" opt-in. Persists per
 * change via [UI2DataSourceManagementViewModel.setHaDeviceClassification] —
 * the sensor mapping is handled here; no legacy screen involved.
 */
@Composable
internal fun HADeviceTable(
    devices: List<HADeviceRow>,
    onDeviceChange: (String, String, Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui2_dsm_device),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.ui2_dsm_role),
                modifier = Modifier.width(DEVICE_ROLE_COLUMN),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.ui2_dsm_remove_from_load),
                modifier = Modifier.width(DEVICE_REMOVE_COLUMN),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        devices.forEach { device ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            HADeviceTableRow(device, onDeviceChange)
        }
    }
}

@Composable
internal fun HADeviceTableRow(
    device: HADeviceRow,
    onDeviceChange: (String, String, Boolean) -> Unit
) {
    // roleNames are persisted classification values — never translated.
    val roleNames = listOf("OTHER", "EV", "HOT_WATER", "HEAT_PUMP")
    val roleLabels = listOf(
        stringResource(R.string.ui2_dsm_role_ignore),
        stringResource(R.string.ui2_component_ev),
        stringResource(R.string.ui2_component_hot_water),
        stringResource(R.string.ui2_component_heat_pump)
    )
    val ignoreLabel = roleLabels[0]
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(device.label,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace)
        Box(Modifier.width(DEVICE_ROLE_COLUMN)) {
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(roleLabels.getOrElse(roleNames.indexOf(device.role)) { ignoreLabel },
                    maxLines = 1)
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                roleLabels.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuOpen = false
                            onDeviceChange(device.statId, roleNames[i], device.adjust)
                        }
                    )
                }
            }
        }
        Box(Modifier.width(DEVICE_REMOVE_COLUMN), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = device.adjust,
                onCheckedChange = { onDeviceChange(device.statId, device.role, it) }
            )
        }
    }
}

/**
 * Two-column Role | Sensor table of the discovered energy sensors. Read-only —
 * the "Individual devices" table below keeps the editable controls.
 * Sensor ids wrap rather than ellipsize: the distinguishing suffix of an
 * entity id is exactly what gets cut off by single-line truncation.
 */
@Composable
internal fun HASensorTable(sensors: HASensorSnapshot) {
    val gridImport = stringResource(R.string.ui2_habf_series_buy)
    val gridExport = stringResource(R.string.ui2_habf_series_feed)
    val solarWord = stringResource(R.string.ui2_graphs_solar)
    val batteryWord = stringResource(R.string.ui2_graphs_battery)
    val rows = buildList {
        sensors.grid.forEach { add(gridImport to it) }
        sensors.gridExports.forEach { add(gridExport to it) }
        sensors.solar.forEach { add(solarWord to it) }
        sensors.batteries.forEachIndexed { index, (charging, discharging) ->
            val battery = if (sensors.batteries.size > 1)
                stringResource(R.string.ui2_dsm_battery_n, index + 1) else batteryWord
            charging?.let { add(stringResource(R.string.ui2_dsm_charge_suffix, battery) to it) }
            discharging?.let { add(stringResource(R.string.ui2_dsm_discharge_suffix, battery) to it) }
        }
    }
    if (rows.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row {
            Text(stringResource(R.string.ui2_dsm_role),
                modifier = Modifier.width(104.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.ui2_dsm_sensor),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { (role, statId) ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(role,
                    modifier = Modifier.width(104.dp),
                    style = MaterialTheme.typography.bodySmall)
                Text(statId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

