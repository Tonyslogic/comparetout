package com.tfcode.comparetout.ui2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.SimulatorLauncher
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.scenario.HeatPumpWeatherCache
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * Wizard battery section content — extracted verbatim from UI2WizardActivity.kt (mega-refactor B2d).
 * Imports inherited; unused are cosmetic.
 */

/* ──────────────────────────────────────────────────────────────────
   Battery section content
────────────────────────────────────────────────────────────────── */

@Composable
internal fun BatterySectionContent(
    entries: List<WizardBatteryEntry>,
    chargeEntries: List<WizardBatteryChargeEntry>,
    dischargeEntries: List<WizardBatteryDischargeEntry>,
    inverterEntries: List<WizardInverterEntry>,
    noviceMode: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardBatteryEntry) -> WizardBatteryEntry) -> Unit,
    onAddCharge: () -> Unit,
    onRemoveCharge: (String) -> Unit,
    onUpdateCharge: (String, (WizardBatteryChargeEntry) -> WizardBatteryChargeEntry) -> Unit,
    onAddDischarge: () -> Unit,
    onRemoveDischarge: (String) -> Unit,
    onUpdateDischarge: (String, (WizardBatteryDischargeEntry) -> WizardBatteryDischargeEntry) -> Unit,
    onImport: () -> Unit
) {
    var expandedBatteryId by remember { mutableStateOf<String?>(null) }
    var expandedChargeId by remember { mutableStateOf<String?>(null) }
    var expandedDischargeId by remember { mutableStateOf<String?>(null) }
    var advancedTab by remember { mutableIntStateOf(0) }

    // Distinct inverter names the schedule cards can target — one schedule applies to every
    // battery sitting on the chosen inverter.
    val batteryInverters = remember(entries) {
        entries.map { it.inverterName.ifBlank { "AlphaESS" } }.distinct()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryTabRow(selectedTabIndex = advancedTab) {
            Tab(selected = advancedTab == 0, onClick = { advancedTab = 0 },
                text = { Text(stringResource(R.string.ui2_cmp_basic)) })
            Tab(selected = advancedTab == 1, onClick = { advancedTab = 1 },
                text = { Text(stringResource(R.string.ui2_cmp_advanced)) })
        }
        Spacer(Modifier.height(4.dp))
        // ── Batteries ──────────────────────────────────────────────
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_batteries_header))
        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_batteries_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.battery1), null, Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.ui2_wiz_no_batteries_yet),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ui2_wiz_no_batteries_hint),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            entries.forEachIndexed { index, entry ->
                WizardBatteryCard(
                    entry = entry,
                    index = index,
                    expanded = expandedBatteryId == entry.id,
                    noviceMode = noviceMode,
                    showAdvanced = advancedTab == 1,
                    inverterEntries = inverterEntries,
                    onToggle = { expandedBatteryId = if (expandedBatteryId == entry.id) null else entry.id },
                    onUpdate = { updated -> onUpdate(entry.id) { updated } },
                    onDelete = { onRemove(entry.id); if (expandedBatteryId == entry.id) expandedBatteryId = null }
                )
            }
        }

        FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ui2_wiz_add_battery))
        }

        Spacer(Modifier.height(4.dp))

        // ── Charge Schedule ────────────────────────────────────────
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_charge_schedule))
        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_charge_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (entries.isEmpty() && chargeEntries.isEmpty()) {
            Text(stringResource(R.string.ui2_wiz_add_battery_first_charge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 6.dp))
        } else if (chargeEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.ui2_wiz_no_charge_windows),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ui2_wiz_charge_windows_hint),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.ui2_wiz_orphan_schedules),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            ScheduleOverflowGate(
                count = chargeEntries.size,
                noun = stringResource(R.string.ui2_wiz_sched_noun_charge)
            ) {
                chargeEntries.forEachIndexed { index, entry ->
                    WizardBatteryChargeCard(
                        entry = entry,
                        index = index,
                        expanded = expandedChargeId == entry.id,
                        noviceMode = noviceMode,
                        batteryInverters = batteryInverters,
                        onToggle = { expandedChargeId = if (expandedChargeId == entry.id) null else entry.id },
                        onUpdate = { updated -> onUpdateCharge(entry.id) { updated } },
                        onDelete = { onRemoveCharge(entry.id); if (expandedChargeId == entry.id) expandedChargeId = null }
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = onAddCharge,
            modifier = Modifier.fillMaxWidth(),
            enabled = entries.isNotEmpty()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ui2_wiz_add_charge_window))
        }

        Spacer(Modifier.height(4.dp))

        // ── Discharge Schedule ─────────────────────────────────────
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_discharge_schedule))
        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_discharge_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (entries.isEmpty() && dischargeEntries.isEmpty()) {
            Text(stringResource(R.string.ui2_wiz_add_battery_first_discharge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 6.dp))
        } else if (dischargeEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.ui2_wiz_no_discharge_windows),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ui2_wiz_discharge_windows_hint),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.ui2_wiz_orphan_schedules),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
            ScheduleOverflowGate(
                count = dischargeEntries.size,
                noun = stringResource(R.string.ui2_wiz_sched_noun_discharge)
            ) {
                dischargeEntries.forEachIndexed { index, entry ->
                    WizardBatteryDischargeCard(
                        entry = entry,
                        index = index,
                        expanded = expandedDischargeId == entry.id,
                        noviceMode = noviceMode,
                        batteryInverters = batteryInverters,
                        onToggle = { expandedDischargeId = if (expandedDischargeId == entry.id) null else entry.id },
                        onUpdate = { updated -> onUpdateDischarge(entry.id) { updated } },
                        onDelete = { onRemoveDischarge(entry.id); if (expandedDischargeId == entry.id) expandedDischargeId = null }
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = onAddDischarge,
            modifier = Modifier.fillMaxWidth(),
            enabled = entries.isNotEmpty()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ui2_wiz_add_discharge_window))
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ui2_wiz_import_battery_json))
        }
    }
}
