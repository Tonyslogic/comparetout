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
 * Wizard start section + scenario picker dialog — extracted verbatim from
 * UI2WizardActivity.kt (mega-refactor B2b). Imports inherited; unused are cosmetic.
 */

/* ──────────────────────────────────────────────────────────────────
   Scenario picker dialog (for Copy / Link selection)
────────────────────────────────────────────────────────────────── */

@Composable
internal fun ScenarioPickerDialog(
    title: String,
    scenarios: List<Scenario>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (scenarios.isEmpty()) {
                Text(stringResource(R.string.ui2_wiz_no_sims_found),
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(scenarios) { scenario ->
                        ListItem(
                            headlineContent = { Text(scenario.scenarioName) },
                            modifier = Modifier.clickable { onSelect(scenario.scenarioIndex); onDismiss() }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

/* ──────────────────────────────────────────────────────────────────
   Start section content
────────────────────────────────────────────────────────────────── */

@Composable
internal fun StartSectionContent(
    builder: WizardBuilder,
    noviceMode: Boolean,
    nameError: String?,
    allScenarios: List<Scenario>,
    onUpdate: ((WizardBuilder) -> WizardBuilder) -> Unit,
    onLoadForCopy: (Long) -> Unit,
    onLoadForLink: (Long) -> Unit,
    onLoadFromJson: (ScenarioJsonFile) -> Unit
) {
    var showCopyPicker    by remember { mutableStateOf(false) }
    var showLinkPicker    by remember { mutableStateOf(false) }
    var showScratchConfirm by remember { mutableStateOf(false) }
    // IMPORT mode opens the shared UI2ImportSheet. If the file contains
    // multiple scenarios the user picks one from a small follow-up dialog;
    // a single-scenario file applies immediately.
    var showImportSheet by remember { mutableStateOf(false) }
    var importPicker by remember { mutableStateOf<List<ScenarioJsonFile>?>(null) }

    if (showCopyPicker) {
        ScenarioPickerDialog(
            title = stringResource(R.string.ui2_wiz_copy_from),
            scenarios = allScenarios,
            onSelect = { id -> onLoadForCopy(id) },
            onDismiss = { showCopyPicker = false }
        )
    }
    if (showLinkPicker) {
        ScenarioPickerDialog(
            title = stringResource(R.string.ui2_wiz_link_to),
            scenarios = allScenarios,
            onSelect = { id -> onLoadForLink(id) },
            onDismiss = { showLinkPicker = false }
        )
    }
    if (showScratchConfirm) {
        AlertDialog(
            onDismissRequest = { showScratchConfirm = false },
            title = { Text(stringResource(R.string.ui2_wiz_scratch_title)) },
            text = { Text(stringResource(R.string.ui2_wiz_scratch_body)) },
            confirmButton = {
                Button(onClick = {
                    onUpdate { b -> WizardBuilder(scenarioMode = ScenarioMode.NEW, scenarioName = b.scenarioName) }
                    showScratchConfirm = false
                }) { Text(stringResource(R.string.ui2_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showScratchConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showImportSheet) {
        val context = LocalContext.current
        UI2ImportSheet(
            title = stringResource(R.string.ui2_wiz_import_scenario_title),
            hint = stringResource(R.string.ui2_wiz_import_scenario_hint),
            applyLabel = stringResource(R.string.ui2_ppw_import_apply),
            parse = { parseScenarioImportJson(context, it) },
            onApply = { list ->
                showImportSheet = false
                if (list.size == 1) onLoadFromJson(list.first())
                else importPicker = list
            },
            onDismiss = { showImportSheet = false }
        )
    }

    importPicker?.let { list ->
        AlertDialog(
            onDismissRequest = { importPicker = null },
            title = { Text(stringResource(R.string.ui2_wiz_multi_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.ui2_wiz_multi_body, list.size),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val unnamed = stringResource(R.string.ui2_ppw_unnamed)
                    list.forEachIndexed { idx, file ->
                        OutlinedButton(
                            onClick = {
                                importPicker = null
                                onLoadFromJson(file)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${idx + 1}. ${file.name ?: unnamed}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importPicker = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_start_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            listOf(
                Triple(ScenarioMode.NEW,
                    stringResource(R.string.ui2_wiz_mode_new),
                    stringResource(R.string.ui2_wiz_mode_new_desc)),
                Triple(ScenarioMode.COPY,
                    stringResource(R.string.ui2_wiz_mode_copy),
                    stringResource(R.string.ui2_wiz_mode_copy_desc)),
                Triple(ScenarioMode.LINK,
                    stringResource(R.string.ui2_wiz_mode_link),
                    stringResource(R.string.ui2_wiz_mode_link_desc)),
                Triple(ScenarioMode.IMPORT,
                    stringResource(R.string.ui2_wiz_mode_import),
                    stringResource(R.string.ui2_wiz_mode_import_desc))
            ).forEach { (mode, modeTitle, desc) ->
                val selected = builder.scenarioMode == mode
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp))
                        .clickable {
                            when (mode) {
                                ScenarioMode.NEW -> { if (builder.scenarioMode != ScenarioMode.NEW) showScratchConfirm = true }
                                ScenarioMode.COPY -> showCopyPicker = true
                                ScenarioMode.LINK -> showLinkPicker = true
                                ScenarioMode.IMPORT -> showImportSheet = true
                            }
                        }
                        .padding(12.dp)
                ) {
                    Text(modeTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(desc, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = builder.scenarioMode == ScenarioMode.NEW,
                    onClick = { if (builder.scenarioMode != ScenarioMode.NEW) showScratchConfirm = true },
                    label = { Text(stringResource(R.string.ui2_wiz_chip_new)) },
                    leadingIcon = { Icon(Icons.Default.Build, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.scenarioMode == ScenarioMode.COPY,
                    onClick = { showCopyPicker = true },
                    label = { Text(stringResource(R.string.ui2_wiz_chip_copy)) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.scenarioMode == ScenarioMode.LINK,
                    onClick = { showLinkPicker = true },
                    label = { Text(stringResource(R.string.ui2_wiz_chip_link)) },
                    leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.scenarioMode == ScenarioMode.IMPORT,
                    onClick = { showImportSheet = true },
                    label = { Text(stringResource(R.string.ui2_ie_import)) },
                    leadingIcon = { Icon(Icons.Default.FileUpload, null, Modifier.size(16.dp)) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = builder.scenarioName,
            onValueChange = { onUpdate { b -> b.copy(scenarioName = it) } },
            label = { Text(stringResource(R.string.ui2_wiz_sim_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = nameError != null,
            supportingText = {
                when {
                    nameError != null -> Text(nameError, color = MaterialTheme.colorScheme.error)
                    noviceMode -> Text(stringResource(R.string.ui2_wiz_sim_name_hint))
                }
            }
        )
    }
}

