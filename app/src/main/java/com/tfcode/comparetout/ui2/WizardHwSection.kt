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
 * Wizard hot-water section content — extracted verbatim from UI2WizardActivity.kt (mega-refactor B2d).
 * Imports inherited; unused are cosmetic.
 */

/* ──────────────────────────────────────────────────────────────────
   Hot Water section content
────────────────────────────────────────────────────────────────── */

@Composable
internal fun HwSectionContent(
    system: WizardHwSystemEntry?,
    schedules: List<WizardHwScheduleEntry>,
    divert: WizardHwDivertEntry,
    noviceMode: Boolean,
    onEnableSystem: () -> Unit,
    onRemoveSystem: () -> Unit,
    onUpdateSystem: ((WizardHwSystemEntry) -> WizardHwSystemEntry) -> Unit,
    onAddSchedule: () -> Unit,
    onRemoveSchedule: (String) -> Unit,
    onUpdateSchedule: (String, (WizardHwScheduleEntry) -> WizardHwScheduleEntry) -> Unit,
    onUpdateDivert: ((WizardHwDivertEntry) -> WizardHwDivertEntry) -> Unit,
    onImport: () -> Unit
) {
    var systemExpanded by remember { mutableStateOf(false) }
    var expandedScheduleIndex by remember { mutableIntStateOf(-1) }
    var advancedTab by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryTabRow(selectedTabIndex = advancedTab) {
            Tab(selected = advancedTab == 0, onClick = { advancedTab = 0 },
                text = { Text(stringResource(R.string.ui2_cmp_basic)) })
            Tab(selected = advancedTab == 1, onClick = { advancedTab = 1 },
                text = { Text(stringResource(R.string.ui2_cmp_advanced)) })
        }
        Spacer(Modifier.height(4.dp))

        // ── Tank ────────────────────────────────────────────────────
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_tank_header))
        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_tank_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (system == null) {
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.watercold), null, Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.ui2_wiz_no_tank),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ui2_wiz_no_tank_hint),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FilledTonalButton(onClick = onEnableSystem, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui2_wiz_add_tank))
            }
        } else {
            WizardHwSystemCard(
                entry = system,
                expanded = systemExpanded,
                noviceMode = noviceMode,
                showAdvanced = advancedTab == 1,
                onToggle = { systemExpanded = !systemExpanded },
                onUpdate = { updated -> onUpdateSystem { updated } },
                onDelete = { onRemoveSystem(); systemExpanded = false }
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Schedule ────────────────────────────────────────────────
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_heater_schedule))
        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_heater_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (system == null && schedules.isEmpty()) {
            Text(stringResource(R.string.ui2_wiz_add_tank_first),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 6.dp))
        } else if (schedules.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.ui2_wiz_no_heater_windows),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.ui2_wiz_heater_windows_hint),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            ScheduleOverflowGate(
                count = schedules.size,
                noun = stringResource(R.string.ui2_wiz_sched_noun_hw)
            ) {
                schedules.forEachIndexed { index, entry ->
                    WizardHwScheduleCard(
                        entry = entry,
                        index = index,
                        expanded = expandedScheduleIndex == index,
                        noviceMode = noviceMode,
                        onToggle = { expandedScheduleIndex = if (expandedScheduleIndex == index) -1 else index },
                        onUpdate = { updated -> onUpdateSchedule(entry.id) { updated } },
                        onDelete = {
                            onRemoveSchedule(entry.id)
                            if (expandedScheduleIndex == index) expandedScheduleIndex = -1
                        }
                    )
                }
            }
        }

        FilledTonalButton(
            onClick = onAddSchedule,
            modifier = Modifier.fillMaxWidth(),
            enabled = system != null
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ui2_wiz_add_heater_window))
        }

        Spacer(Modifier.height(4.dp))

        // ── Solar Divert ───────────────────────────────────────────
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_solar_divert))
        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_hw_divert_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ui2_wiz_divert_to_hw),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(if (divert.active) R.string.ui2_wiz_active
                                   else R.string.ui2_wiz_off),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (divert.active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = divert.active,
                enabled = system != null,
                onCheckedChange = { v -> onUpdateDivert { it.copy(active = v) } }
            )
        }
        if (system == null && noviceMode) {
            Text(stringResource(R.string.ui2_wiz_add_tank_divert),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp))
        }

        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ui2_wiz_import_hw_json))
        }
    }
}
