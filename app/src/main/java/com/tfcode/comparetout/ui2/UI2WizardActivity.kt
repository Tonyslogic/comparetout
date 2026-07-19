@file:OptIn(ExperimentalLayoutApi::class)
@file:Suppress("MissingPermission", "AssignedValueIsNeverRead")

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

// Pixels of scroll delta before the collapsing header toggles — small enough to feel
// responsive, large enough to ignore fingertip wobble.
private const val THRESHOLD = 4

@AndroidEntryPoint
class UI2WizardActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                WizardScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun WizardScreen(
    viewModel: UI2WizardViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val builder                by viewModel.builder.observeAsState(WizardBuilder())
    val isLoading              by viewModel.isLoading.observeAsState(false)
    val noviceMode             by viewModel.noviceMode.observeAsState(true)
    val expandedSections       by viewModel.expandedSections.observeAsState(setOf("start"))
    val allScenarios           by viewModel.allScenarios.observeAsState(emptyList())
    val availableSources       by viewModel.availableSources.observeAsState(emptyList())
    val isDeriving             by viewModel.isDeriving.observeAsState(false)
    val saveResult             by viewModel.saveResult.observeAsState(WizardSaveResult.Idle)
    val pendingLocationRequest by viewModel.pendingLocationRequest.observeAsState(null)
    val panelPvSummary         by viewModel.panelPvSummary.observeAsState(emptyList())
    val pvgisParamCheck        by viewModel.pvgisParamCheck.observeAsState(emptyMap())
    val isSaving               = saveResult == WizardSaveResult.Saving
    var simulationQueued       by remember { mutableStateOf(false) }
    var pvgisQueued            by remember { mutableIntStateOf(0) }
    var lastSavedBuilder       by remember { mutableStateOf<WizardBuilder?>(null) }
    var initialBuilder         by remember { mutableStateOf<WizardBuilder?>(null) }
    var sawLoading             by remember { mutableStateOf(false) }
    var showCloseConfirm       by remember { mutableStateOf(false) }
    var showSavedTick          by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val simulationWorkInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("Simulation")
    }.observeAsState(emptyList())
    val simButtonState: SimButtonState = when {
        !simulationQueued -> SimButtonState.Idle
        simulationWorkInfos.any { it.state == WorkInfo.State.RUNNING } -> SimButtonState.Running
        simulationWorkInfos.isNotEmpty() && simulationWorkInfos.all { it.state.isFinished } &&
            simulationWorkInfos.none {
                it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
            } -> SimButtonState.Done
        simulationWorkInfos.isNotEmpty() && simulationWorkInfos.any {
            it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
        } -> SimButtonState.Failed
        else -> SimButtonState.Queued
    }
    val locationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val entryId = pendingLocationRequest
        if (granted && entryId != null) {
            locationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) viewModel.locationRetrieved(entryId, loc.latitude, loc.longitude)
                    else viewModel.locationRequestDismissed()
                }
                .addOnFailureListener { viewModel.locationRequestDismissed() }
        } else if (entryId != null) {
            viewModel.locationRequestDismissed()
        }
    }

    LaunchedEffect(pendingLocationRequest) {
        val entryId = pendingLocationRequest ?: return@LaunchedEffect
        val hasFine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            locationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) viewModel.locationRetrieved(entryId, loc.latitude, loc.longitude)
                    else viewModel.locationRequestDismissed()
                }
                .addOnFailureListener { viewModel.locationRequestDismissed() }
        } else {
            locationPermLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    LaunchedEffect(saveResult) {
        val result = saveResult
        if (result is WizardSaveResult.Failed) {
            // The save couldn't complete (e.g. duplicate name slipping past the field check). Tell the user
            // instead of dismissing silently; the wizard stays open so they can fix it.
            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
        }
        if (result is WizardSaveResult.Done) {
            when {
                result.needsWeatherFetch -> {
                    // HP on CDS: chain GenerateLoad → WeatherFetch → Simulate → Cost so the sim runs on the
                    // freshly-downloaded weather instead of racing it. Show sim progress only if Run was pressed.
                    SimulatorLauncher.simulateWithWeatherFetch(context, result.scenarioId)
                    simulationQueued = result.runSimulation
                }
                result.runSimulation -> {
                    SimulatorLauncher.simulateIfNeeded(context)
                    simulationQueued = true
                }
                result.pvgisStringsQueued > 0 -> {
                    pvgisQueued = result.pvgisStringsQueued
                }
                else -> {
                    // Plain Save with no PVGIS fetch pending. The save invalidated
                    // this scenario's sim + costing rows, so recompute now rather
                    // than waiting for the next navigation to notice. (When PVGIS
                    // strings are queued the fetch worker kicks off the recompute
                    // once its data lands, so we skip it here.)
                    SimulatorLauncher.simulateIfNeeded(context)
                }
            }
            if (!result.runSimulation) {
                lastSavedBuilder = builder
                showSavedTick = true
            }
        }
    }

    LaunchedEffect(showSavedTick) {
        if (showSavedTick) {
            delay(2000L)
            showSavedTick = false
        }
    }

    // Existing names for uniqueness check (exclude the current scenario). Collected from a flow so that once a
    // brand-new scenario is saved and the VM adopts its id, the just-saved name stops counting against itself
    // (otherwise it would falsely read as "name already in use").
    val currentScenarioId by viewModel.scenarioIdFlow.collectAsState()
    val usedNames = remember(allScenarios, currentScenarioId) {
        allScenarios
            .filter { it.scenarioIndex != currentScenarioId }
            .map { it.scenarioName }
            .toSet()
    }
    val nameInUse = stringResource(R.string.ui2_wiz_name_in_use)
    val nameError = remember(builder.scenarioName, usedNames) {
        when {
            builder.scenarioName.isBlank() -> null
            usedNames.contains(builder.scenarioName) -> nameInUse
            else -> null
        }
    }

    var showDrawer by remember { mutableStateOf(false) }
    // Per-accordion JSON import — null when no sheet is open. Each section's
    // "Import…" button writes its scope here; the sheet is rendered at scaffold
    // level so it floats over whichever accordion is expanded.
    var importScope by remember { mutableStateOf<WizardImportScope?>(null) }
    val title = when {
        viewModel.isEditMode && builder.scenarioName.isNotBlank() ->
            stringResource(R.string.ui2_wiz_title_edit_named, builder.scenarioName)
        viewModel.isEditMode -> stringResource(R.string.ui2_wiz_title_edit)
        else -> stringResource(R.string.ui2_wiz_title_new)
    }

    // Capture the wizard's pristine baseline so close-confirm only fires after real
    // user edits. In edit/copy/link modes the baseline is the loaded scenario; in new
    // mode it's the default builder taken on first render.
    LaunchedEffect(isLoading) {
        if (isLoading) sawLoading = true
    }
    LaunchedEffect(isLoading, sawLoading, builder) {
        if (initialBuilder == null && !isLoading) {
            if (!viewModel.isEditMode || sawLoading) {
                initialBuilder = builder
            }
        }
    }

    val handleClose: () -> Unit = {
        val baseline = lastSavedBuilder ?: initialBuilder
        val hasChanges = baseline != null && baseline != builder
        if (hasChanges) showCloseConfirm = true else onClose()
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text(stringResource(R.string.ui2_ppw_discard_title)) },
            text = { Text(stringResource(R.string.ui2_wiz_unsaved_body)) },
            confirmButton = {
                Button(onClick = { showCloseConfirm = false; onClose() }) {
                    Text(stringResource(R.string.ui2_wiz_leave))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) {
                    Text(stringResource(R.string.ui2_wiz_stay))
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = handleClose) {
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
        }
    ) { padding ->
        // Browser-style collapsing header: hides when the user drags content up (scrolls into
        // the content) and reappears the moment they drag content down. Always shown when
        // the scroll position is at the top.
        val scrollState = rememberScrollState()
        var headerVisible by remember { mutableStateOf(true) }
        var lastScrollValue by remember { mutableIntStateOf(0) }
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.value }.collect { current ->
                val delta = current - lastScrollValue
                when {
                    current == 0       -> headerVisible = true
                    delta >  THRESHOLD -> headerVisible = false
                    delta < -THRESHOLD -> headerVisible = true
                }
                lastScrollValue = current
            }
        }

        // Component-visibility gating (App settings) — a hidden component's
        // accordion disappears here AND on the dashboard; existing scenario
        // content is untouched and still simulates.
        val uiVis = rememberUiVisibility()

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed: progress strip + action buttons (collapses on scroll up)
            AnimatedVisibility(visible = headerVisible) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WizardProgressStrip(builder = builder, uiVis = uiVis)
                    WizardFooter(
                        canRun = builder.isRunnable && nameError == null,
                        canSave = nameError == null,
                        isSaving = isSaving,
                        simButtonState = simButtonState,
                        showSavedTick = showSavedTick,
                        noviceMode = noviceMode,
                        onSave = { viewModel.save(runSimulation = false) },
                        onRun = { simulationQueued = false; viewModel.save(runSimulation = true) },
                        onClose = handleClose
                    )
                }
            }
            // Scrollable accordion sections. Capped + centred at CONTENT_MAX_WIDTH
            // so the form doesn't run to the full width on tablets / landscape.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = AdaptiveLayout.CONTENT_MAX_WIDTH)
                    .align(Alignment.CenterHorizontally)
                    // Stable resourceId so the Robo walkthrough can ELEMENT_SCROLL_INTO_VIEW
                    // any wizard section (incl. Heat Pump, off-screen in landscape) before tapping.
                    .semantics { testTagsAsResourceId = true }
                    .testTag("wizard_section_scroll")
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pvgisQueued > 0) {
                    PvgisBackgroundBanner(stringCount = pvgisQueued, onClose = handleClose)
                }

                if (noviceMode) {
                    WizardHintBanner(stringResource(R.string.ui2_wiz_banner_hint))
                }

                // ── Start ───────────────────────────────────────────────────
            WizardAccordionSection(
                id = "start",
                iconContent = { Text("🌱", style = MaterialTheme.typography.titleMedium) },
                title = stringResource(R.string.ui2_wiz_start),
                isLinked = false,
                subtitle = if (!expandedSections.contains("start") && builder.scenarioName.isNotBlank())
                    builder.scenarioName
                else if (noviceMode) stringResource(R.string.ui2_wiz_start_sub) else null,
                isComplete = builder.isStartComplete && nameError == null,
                isLocked = false,
                isExpanded = expandedSections.contains("start"),
                onToggle = { viewModel.toggleSection("start") }
            ) {
                StartSectionContent(
                    builder = builder,
                    noviceMode = noviceMode,
                    nameError = nameError,
                    allScenarios = allScenarios,
                    onUpdate = { viewModel.updateBuilder(it) },
                    onLoadForCopy = { viewModel.loadForCopy(it) },
                    onLoadForLink = { viewModel.loadForLink(it) },
                    onLoadFromJson = { viewModel.loadFromJson(it) }
                )
            }

            // ── Usage Data ──────────────────────────────────────────────
            WizardAccordionSection(
                id = "load",
                iconContent = {
                    Icon(painterResource(R.drawable.house), contentDescription = null,
                        modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurface)
                },
                title = stringResource(R.string.ui2_dash_usage_data),
                isLinked = builder.isLinked || builder.isLoadLinked,
                subtitle = if (!expandedSections.contains("load") && builder.isLoadComplete)
                    stringResource(R.string.ui2_wiz_kwh_yr, builder.annualUsage)
                else if (noviceMode) stringResource(R.string.ui2_wiz_load_sub) else null,
                isComplete = builder.isLoadComplete,
                isLocked = !builder.isStartComplete,
                lockedHint = stringResource(R.string.ui2_wiz_complete_start_first),
                isExpanded = expandedSections.contains("load"),
                onToggle = { viewModel.toggleSection("load") }
            ) {
                UsageDataSectionContent(
                    builder = builder,
                    noviceMode = noviceMode,
                    allScenarios = allScenarios,
                    availableSources = availableSources,
                    isDeriving = isDeriving,
                    onUpdate = { viewModel.updateBuilder(it) },
                    onLoadSLP = { viewModel.loadSLPProfile(it) },
                    onLoadProfileForCopy = { viewModel.loadProfileForCopy(it) },
                    onLoadProfileForLink = { viewModel.loadProfileForLink(it) },
                    onInitHandCraft = { viewModel.initHandCraft() },
                    onDeriveFromSource = { sysSn, importer, from, to, fillGaps, absoluteYear ->
                        viewModel.deriveLoadProfileFromSource(sysSn, importer, from, to, fillGaps, absoluteYear)
                    },
                    onImport = { importScope = WizardImportScope.USAGE }
                )
            }

            // ── Inverters ────────────────────────────────────────────────
            val inverterCount = builder.inverterEntries.size
            if (uiVis.inverter) WizardAccordionSection(
                id = "inverters",
                iconContent = {
                    val res = if (inverterCount > 0) R.drawable.invertertick else R.drawable.inverter
                    Icon(painterResource(res), null, Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                title = stringResource(R.string.ui2_wiz_inverters),
                isLinked = builder.isLinked && inverterCount > 0,
                subtitle = if (!expandedSections.contains("inverters") && inverterCount > 0)
                    pluralStringResource(R.plurals.ui2_wiz_n_inverters, inverterCount, inverterCount) +
                        "  ·  ${builder.inverterEntries.sumOf { it.maxInverterLoad }} kW"
                else if (noviceMode) stringResource(R.string.ui2_wiz_inverters_sub) else null,
                isComplete = inverterCount > 0,
                isLocked = !builder.isStartComplete,
                lockedHint = stringResource(R.string.ui2_wiz_complete_start_first),
                isExpanded = expandedSections.contains("inverters"),
                onToggle = { viewModel.toggleSection("inverters") }
            ) {
                InverterSectionContent(
                    entries = builder.inverterEntries,
                    noviceMode = noviceMode,
                    onAdd = { viewModel.addInverterEntry() },
                    onRemove = { viewModel.removeInverterEntry(it) },
                    onUpdate = { id, fn -> viewModel.updateInverterEntry(id, fn) },
                    onImport = { importScope = WizardImportScope.INVERTERS }
                )
            }

            // ── PV System ────────────────────────────────────────────────
            val panelCount = builder.panelEntries.size
            if (uiVis.panels) WizardAccordionSection(
                id = "pv",
                iconContent = {
                    val pvRes = if (panelCount > 0) R.drawable.solarpaneltick else R.drawable.solarpanel
                    Icon(painterResource(pvRes), null, Modifier.size(26.dp), tint = Color.Unspecified)
                },
                title = stringResource(R.string.ui2_dash_pv_system),
                isLinked = builder.isLinked && panelCount > 0,
                subtitle = if (!expandedSections.contains("pv") && panelCount > 0)
                    pluralStringResource(R.plurals.ui2_wiz_n_strings, panelCount, panelCount) +
                        "  ·  ${builder.panelEntries.sumOf { it.panelCount * it.panelkWp }} Wp"
                else if (noviceMode) stringResource(R.string.ui2_wiz_pv_sub) else null,
                isComplete = panelCount > 0,
                isLocked = !builder.isStartComplete,
                lockedHint = stringResource(R.string.ui2_wiz_complete_start_first),
                isExpanded = expandedSections.contains("pv"),
                onToggle = { viewModel.toggleSection("pv") }
            ) {
                PVSystemSectionContent(
                    entries = builder.panelEntries,
                    noviceMode = noviceMode,
                    inverterEntries = builder.inverterEntries,
                    availableSources = availableSources,
                    panelPvSummary = panelPvSummary,
                    pvgisParamCheck = pvgisParamCheck,
                    onAdd = { viewModel.addPanelEntry() },
                    onRemove = { viewModel.removePanelEntry(it) },
                    onUpdate = { id, fn -> viewModel.updatePanelEntry(id, fn) },
                    onRequestLocation = { id -> viewModel.requestLocation(id) },
                    onCheckPvgisParams = { id, lat, lon, az, sl ->
                        viewModel.checkPvgisParams(id, lat, lon, az, sl)
                    },
                    onImport = { importScope = WizardImportScope.PV }
                )
            }

            // ── Battery ─────────────────────────────────────────────────
            val batteryCount = builder.batteryEntries.size
            val batteryChargeCount = builder.batteryChargeEntries.size
            val batteryDischargeCount = builder.batteryDischargeEntries.size
            if (uiVis.battery) WizardAccordionSection(
                id = "battery",
                iconContent = {
                    Icon(painterResource(R.drawable.battery1), null, Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                title = stringResource(R.string.ui2_component_battery),
                isLinked = builder.isLinked && batteryCount > 0,
                subtitle = if (!expandedSections.contains("battery") && batteryCount > 0) {
                    buildString {
                        append(pluralStringResource(R.plurals.ui2_wiz_n_batteries,
                            batteryCount, batteryCount))
                        append("  ·  ")
                        append("${builder.batteryEntries.sumOf { it.batterySize }} kWh")
                        if (batteryChargeCount > 0) {
                            append("  ·  ")
                            append(stringResource(R.string.ui2_wiz_n_charge, batteryChargeCount))
                        }
                        if (batteryDischargeCount > 0) {
                            append("  ·  ")
                            append(stringResource(R.string.ui2_wiz_n_discharge, batteryDischargeCount))
                        }
                    }
                } else if (noviceMode) stringResource(R.string.ui2_wiz_battery_sub) else null,
                isComplete = batteryCount > 0,
                isLocked = !builder.isStartComplete,
                lockedHint = stringResource(R.string.ui2_wiz_complete_start_first),
                isExpanded = expandedSections.contains("battery"),
                onToggle = { viewModel.toggleSection("battery") }
            ) {
                BatterySectionContent(
                    entries = builder.batteryEntries,
                    chargeEntries = builder.batteryChargeEntries,
                    dischargeEntries = builder.batteryDischargeEntries,
                    inverterEntries = builder.inverterEntries,
                    noviceMode = noviceMode,
                    onAdd = { viewModel.addBatteryEntry() },
                    onRemove = { viewModel.removeBatteryEntry(it) },
                    onUpdate = { id, fn -> viewModel.updateBatteryEntry(id, fn) },
                    onAddCharge = { viewModel.addBatteryChargeEntry() },
                    onRemoveCharge = { viewModel.removeBatteryChargeEntry(it) },
                    onUpdateCharge = { id, fn -> viewModel.updateBatteryChargeEntry(id, fn) },
                    onAddDischarge = { viewModel.addBatteryDischargeEntry() },
                    onRemoveDischarge = { viewModel.removeBatteryDischargeEntry(it) },
                    onUpdateDischarge = { id, fn -> viewModel.updateBatteryDischargeEntry(id, fn) },
                    onImport = { importScope = WizardImportScope.BATTERY }
                )
            }

            // ── Hot Water ────────────────────────────────────────────────
            val hwSystemCount = if (builder.hwSystem != null) 1 else 0
            val hwScheduleCount = builder.hwSchedules.size
            val hwDivertActive = builder.hwDivert.active
            if (uiVis.hotWater) WizardAccordionSection(
                id = "hotwater",
                iconContent = {
                    val hwRes = if (hwSystemCount > 0) R.drawable.waterwarm else R.drawable.watercold
                    Icon(painterResource(hwRes), null, Modifier.size(26.dp), tint = Color.Unspecified)
                },
                title = stringResource(R.string.ui2_graphs_hot_water),
                isLinked = builder.isLinked && (hwSystemCount > 0 || hwScheduleCount > 0),
                subtitle = if (!expandedSections.contains("hotwater") && (hwSystemCount > 0 || hwScheduleCount > 0 || hwDivertActive)) {
                    buildString {
                        builder.hwSystem?.let { append("${it.capacity} L  ·  ${it.rate} kW") }
                        if (hwScheduleCount > 0) {
                            if (isNotEmpty()) append("  ·  ")
                            append(pluralStringResource(R.plurals.ui2_wiz_n_schedules,
                                hwScheduleCount, hwScheduleCount))
                        }
                        if (hwDivertActive) {
                            if (isNotEmpty()) append("  ·  ")
                            append(stringResource(R.string.ui2_wiz_divert_word))
                        }
                    }
                } else if (noviceMode) stringResource(R.string.ui2_wiz_hw_sub) else null,
                isComplete = hwSystemCount > 0,
                isLocked = !builder.isStartComplete,
                lockedHint = stringResource(R.string.ui2_wiz_complete_start_first),
                isExpanded = expandedSections.contains("hotwater"),
                onToggle = { viewModel.toggleSection("hotwater") }
            ) {
                HwSectionContent(
                    system = builder.hwSystem,
                    schedules = builder.hwSchedules,
                    divert = builder.hwDivert,
                    noviceMode = noviceMode,
                    onEnableSystem = { viewModel.enableHwSystem() },
                    onRemoveSystem = { viewModel.removeHwSystem() },
                    onUpdateSystem = { viewModel.updateHwSystem(it) },
                    onAddSchedule = { viewModel.addHwSchedule() },
                    onRemoveSchedule = { viewModel.removeHwSchedule(it) },
                    onUpdateSchedule = { id, fn -> viewModel.updateHwSchedule(id, fn) },
                    onUpdateDivert = { viewModel.updateHwDivert(it) },
                    onImport = { importScope = WizardImportScope.HW }
                )
            }

            // ── EV ──────────────────────────────────────────────────────
            val evCount = builder.evEntries.size
            val evDivertCount = builder.evDivertEntries.size
            if (uiVis.ev) WizardAccordionSection(
                id = "ev",
                iconContent = {
                    val evRes = if (evCount > 0 || evDivertCount > 0) R.drawable.ev_on else R.drawable.ev_off
                    Icon(painterResource(evRes), null, Modifier.size(26.dp), tint = Color.Unspecified)
                },
                title = stringResource(R.string.ui2_component_ev),
                isLinked = builder.isLinked && (evCount > 0 || evDivertCount > 0),
                subtitle = if (!expandedSections.contains("ev") && (evCount > 0 || evDivertCount > 0)) {
                    buildString {
                        if (evCount > 0) append(pluralStringResource(
                            R.plurals.ui2_wiz_n_schedules, evCount, evCount))
                        if (evCount > 0 && evDivertCount > 0) append(" · ")
                        if (evDivertCount > 0) append(pluralStringResource(
                            R.plurals.ui2_wiz_n_diverts, evDivertCount, evDivertCount))
                    }
                } else if (noviceMode) stringResource(R.string.ui2_wiz_ev_sub) else null,
                isComplete = evCount > 0 || evDivertCount > 0,
                isLocked = !builder.isLoadComplete,
                lockedHint = stringResource(R.string.ui2_wiz_complete_load_first),
                isExpanded = expandedSections.contains("ev"),
                onToggle = { viewModel.toggleSection("ev") }
            ) {
                EvSectionContent(
                    entries = builder.evEntries,
                    divertEntries = builder.evDivertEntries,
                    noviceMode = noviceMode,
                    onAdd = { viewModel.addEvEntry() },
                    onRemove = { viewModel.removeEvEntry(it) },
                    onUpdate = { id, fn -> viewModel.updateEvEntry(id, fn) },
                    onAddDivert = { viewModel.addEvDivertEntry() },
                    onRemoveDivert = { viewModel.removeEvDivertEntry(it) },
                    onUpdateDivert = { id, fn -> viewModel.updateEvDivertEntry(id, fn) },
                    onImport = { importScope = WizardImportScope.EV }
                )
            }

            // Heat Pump — placed after EV to mirror the dashboard order (Phase 5 of plans/hp/plan.md).
            val heatPumpCount = builder.heatPumpEntries.size
            if (uiVis.heatPump) WizardAccordionSection(
                id = "heatpump",
                iconContent = { Text(if (heatPumpCount > 0) "♨️" else "❄️", fontSize = 22.sp) },
                title = stringResource(R.string.ui2_graphs_heat_pump),
                isLinked = builder.isLinked && heatPumpCount > 0,
                subtitle = if (!expandedSections.contains("heatpump") && heatPumpCount > 0) {
                    val hp = builder.heatPumpEntries.first()
                    "${hp.fuelType} · SCOP ${hp.scop}"
                } else if (noviceMode) stringResource(R.string.ui2_wiz_hp_sub) else null,
                isComplete = heatPumpCount > 0,
                isLocked = !builder.isLoadComplete,
                lockedHint = stringResource(R.string.ui2_wiz_complete_load_first),
                isExpanded = expandedSections.contains("heatpump"),
                onToggle = { viewModel.toggleSection("heatpump") }
            ) {
                HeatPumpSectionContent(
                    entries = builder.heatPumpEntries,
                    noviceMode = noviceMode,
                    cdsDateRange = cdsQueryDates(builder.panelEntries),
                    onAdd = { viewModel.addHeatPumpEntry() },
                    onRemove = { viewModel.removeHeatPumpEntry(it) },
                    onUpdate = { id, fn -> viewModel.updateHeatPumpEntry(id, fn) },
                    onImport = { importScope = WizardImportScope.HEATPUMP }
                )
            }

            Spacer(Modifier.height(104.dp))
            }
        }

        // Global app-menu drawer (right side).
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
                    showHints = noviceMode,
                    onShowHintsChange = { if (it != noviceMode) viewModel.toggleNoviceMode() },
                    onSwitchLegacy = {
                        showDrawer = false
                        // The wizard is launched from the UI2 navigation host; closing the
                        // wizard returns to that host where the actual UI2 → Legacy swap
                        // happens via the bottom-nav screens.
                        onClose()
                    },
                    onClose = { showDrawer = false }
                )
            }
        }
        }

        // Per-accordion JSON import sheet — dispatches on the active scope so
        // each scope gets a parser sized to just its slice of the scenario.
        importScope?.let { scope ->
            WizardImportSheet(
                scope = scope,
                onDismiss = { importScope = null },
                onApplied = { importScope = null },
                viewModel = viewModel
            )
        }
    }
}

/** Each per-accordion Import button writes one of these into the wizard's
 *  scaffold state to open the matching sheet. */
internal enum class WizardImportScope { USAGE, INVERTERS, PV, BATTERY, HW, EV, HEATPUMP }

/* ──────────────────────────────────────────────────────────────────
   Progress strip
────────────────────────────────────────────────────────────────── */

@Composable
private fun WizardProgressStrip(builder: WizardBuilder, uiVis: UiVisibility) {
    // One entry per wizard section. `visible` mirrors the accordion gating in the
    // form (Start + Usage are always shown; the rest follow App-settings component
    // visibility). The denominator counts only VISIBLE sections, while the
    // numerator counts every DEFINED section — so a component that is defined but
    // hidden still contributes, giving e.g. "7 of 5".
    data class SectionState(val complete: Boolean, val visible: Boolean)
    val sections = listOf(
        SectionState(builder.isStartComplete, true),
        SectionState(builder.isLoadComplete, true),
        SectionState(builder.inverterEntries.isNotEmpty(), uiVis.inverter),
        SectionState(builder.panelEntries.isNotEmpty(), uiVis.panels),
        SectionState(builder.batteryEntries.isNotEmpty(), uiVis.battery),
        SectionState(builder.hwSystem != null || builder.hwSchedules.isNotEmpty() || builder.hwDivert.active, uiVis.hotWater),
        SectionState(builder.evEntries.isNotEmpty() || builder.evDivertEntries.isNotEmpty(), uiVis.ev),
        SectionState(builder.heatPumpEntries.isNotEmpty(), uiVis.heatPump)
    )
    val done = sections.count { it.complete }
    val total = sections.count { it.visible }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            sections.filter { it.visible }.forEach { section ->
                Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (section.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.ui2_wiz_n_of_m, done, total),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ──────────────────────────────────────────────────────────────────
   Hint banner
────────────────────────────────────────────────────────────────── */

@Composable
private fun WizardHintBanner(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp).padding(top = 1.dp),
            tint = MaterialTheme.colorScheme.secondary)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/* ──────────────────────────────────────────────────────────────────
   Generic accordion section
────────────────────────────────────────────────────────────────── */

@Composable
private fun WizardAccordionSection(
    id: String,
    iconContent: @Composable () -> Unit,
    title: String,
    isLinked: Boolean,
    subtitle: String?,
    isComplete: Boolean,
    isLocked: Boolean,
    lockedHint: String? = null,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = if (isExpanded)
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            // Stable selector for Play Console Robo / FTL automation: each accordion
            // toggle is matched by `contentDescription="Wizard section: $title"`. Robo
            // can't see Compose testTags, so this is the canonical way to expose the
            // toggle. See plans/roboscript/robo-plan.md (Phase 4A).
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .semantics {
                    contentDescription = "Wizard section: $title"
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.width(42.dp).height(32.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                iconContent()
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (isLinked) {
                        Icon(Icons.Default.Link,
                            contentDescription = stringResource(R.string.ui2_wiz_linked_cd),
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                isComplete -> Icon(Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.ui2_ppw_complete_cd),
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                isLocked   -> Icon(Icons.Default.Lock,
                    contentDescription = stringResource(R.string.ui2_wiz_locked_cd),
                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            }
            Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                if (isLocked && lockedHint != null) {
                    Row(modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Text(lockedHint, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                content()
            }
        }
    }
}


/* ──────────────────────────────────────────────────────────────────
   Schedule overflow gate
────────────────────────────────────────────────────────────────── */

/** Lists longer than this collapse behind a count + "Show all" prompt. */
private const val SCHEDULE_OVERFLOW_THRESHOLD = 5

/**
 * A dynamic-tariff optimised scenario can carry dozens of schedule rows. When a
 * list has more than [SCHEDULE_OVERFLOW_THRESHOLD] entries, collapse it behind a
 * "N <noun> — Show all" prompt so the accordion stays scannable; [content] (the
 * cards) is only composed once the user opts in.
 *
 * Adding an entry (the count grows) auto-expands the list so the new row is
 * visible and editable rather than hidden — only a fresh load of an
 * already-large list starts collapsed. The wrapper is always present (even at or
 * below the threshold) so this state survives crossing the threshold via an add.
 */
@Composable
internal fun ScheduleOverflowGate(
    count: Int,
    noun: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var previousCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > previousCount) expanded = true
        previousCount = count
    }
    val collapsible = count > SCHEDULE_OVERFLOW_THRESHOLD
    if (collapsible) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.ui2_wiz_sched_overflow_count, count, noun),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(if (expanded) R.string.ui2_wiz_sched_hide
                                   else R.string.ui2_wiz_sched_show_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    if (!collapsible || expanded) content()
}




/* ──────────────────────────────────────────────────────────────────
   Footer
────────────────────────────────────────────────────────────────── */

private sealed class SimButtonState {
    object Idle    : SimButtonState()
    object Queued  : SimButtonState()
    object Running : SimButtonState()
    object Done    : SimButtonState()
    object Failed  : SimButtonState()
}

@Composable
private fun WizardFooter(
    canRun: Boolean,
    canSave: Boolean,
    isSaving: Boolean,
    simButtonState: SimButtonState,
    showSavedTick: Boolean,
    noviceMode: Boolean,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (noviceMode) {
                Text(stringResource(R.string.ui2_wiz_run_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.ui2_wiz_run_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                // Gate Save on the name check too (not just Run): saving a duplicate name violates the
                // scenarios.scenarioName UNIQUE index, which the DAO swallows into a bogus id and then NPEs
                // in savePanel — the whole save fails silently. Blocking it here is the user-visible fix.
                onClick = onSave,
                enabled = canSave && !isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (showSavedTick) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui2_ppw_saved))
                } else {
                    Text(stringResource(R.string.ui2_save))
                }
            }
            Button(
                onClick = onRun,
                enabled = canRun && !isSaving &&
                    simButtonState != SimButtonState.Queued &&
                    simButtonState != SimButtonState.Running
            ) {
                when {
                    isSaving -> Text(stringResource(R.string.ui2_ppw_saving))
                    simButtonState == SimButtonState.Running -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui2_wiz_running))
                    }
                    simButtonState == SimButtonState.Done -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui2_done))
                    }
                    simButtonState == SimButtonState.Failed ->
                        Text(stringResource(R.string.ui2_wiz_failed_retry))
                    simButtonState == SimButtonState.Queued -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui2_wiz_queued))
                    }
                    else -> Text(stringResource(R.string.ui2_director_run_sim))
                }
            }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.ui2_close))
            }
        }
        }
    }
}

