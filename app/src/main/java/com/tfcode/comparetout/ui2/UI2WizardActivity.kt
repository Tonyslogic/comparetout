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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val nameError = remember(builder.scenarioName, usedNames) {
        when {
            builder.scenarioName.isBlank() -> null
            usedNames.contains(builder.scenarioName) -> "Name already in use"
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
            "Edit scenario · ${builder.scenarioName}"
        viewModel.isEditMode -> "Edit scenario"
        else -> "Build your scenario"
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
            title = { Text("Leave without saving?") },
            text = { Text("You have unsaved changes. Leave anyway?") },
            confirmButton = {
                Button(onClick = { showCloseConfirm = false; onClose() }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) { Text("Stay") }
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

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed: progress strip + action buttons (collapses on scroll up)
            AnimatedVisibility(visible = headerVisible) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WizardProgressStrip(builder = builder)
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pvgisQueued > 0) {
                    PvgisBackgroundBanner(stringCount = pvgisQueued, onClose = handleClose)
                }

                if (noviceMode) {
                    WizardHintBanner("Tap any section to expand it. Start and Usage Data are required to run a simulation.")
                }

                // ── Start ───────────────────────────────────────────────────
            WizardAccordionSection(
                id = "start",
                iconContent = { Text("🌱", style = MaterialTheme.typography.titleMedium) },
                title = "Start",
                isLinked = false,
                subtitle = if (!expandedSections.contains("start") && builder.scenarioName.isNotBlank())
                    builder.scenarioName
                else if (noviceMode) "How do you want to begin?" else null,
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
                title = "Usage Data",
                isLinked = builder.isLinked || builder.isLoadLinked,
                subtitle = if (!expandedSections.contains("load") && builder.isLoadComplete)
                    "${builder.annualUsage} kWh / yr"
                else if (noviceMode) "How much electricity does the home use?" else null,
                isComplete = builder.isLoadComplete,
                isLocked = !builder.isStartComplete,
                lockedHint = "Complete Start first",
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
            WizardAccordionSection(
                id = "inverters",
                iconContent = {
                    val res = if (inverterCount > 0) R.drawable.invertertick else R.drawable.inverter
                    Icon(painterResource(res), null, Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                title = "Inverters",
                isLinked = builder.isLinked && inverterCount > 0,
                subtitle = if (!expandedSections.contains("inverters") && inverterCount > 0)
                    "$inverterCount inverter${if (inverterCount > 1) "s" else ""}  ·  ${builder.inverterEntries.sumOf { it.maxInverterLoad }} kW"
                else if (noviceMode) "Solar inverter configuration" else null,
                isComplete = inverterCount > 0,
                isLocked = !builder.isStartComplete,
                lockedHint = "Complete Start first",
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
            WizardAccordionSection(
                id = "pv",
                iconContent = {
                    val pvRes = if (panelCount > 0) R.drawable.solarpaneltick else R.drawable.solarpanel
                    Icon(painterResource(pvRes), null, Modifier.size(26.dp), tint = Color.Unspecified)
                },
                title = "PV System",
                isLinked = builder.isLinked && panelCount > 0,
                subtitle = if (!expandedSections.contains("pv") && panelCount > 0)
                    "$panelCount string${if (panelCount > 1) "s" else ""}  ·  ${builder.panelEntries.sumOf { it.panelCount * it.panelkWp }} Wp"
                else if (noviceMode) "Solar panel strings" else null,
                isComplete = panelCount > 0,
                isLocked = !builder.isStartComplete,
                lockedHint = "Complete Start first",
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
            WizardAccordionSection(
                id = "battery",
                iconContent = {
                    Icon(painterResource(R.drawable.battery1), null, Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                title = "Battery",
                isLinked = builder.isLinked && batteryCount > 0,
                subtitle = if (!expandedSections.contains("battery") && batteryCount > 0) {
                    buildString {
                        append("$batteryCount batter${if (batteryCount > 1) "ies" else "y"}")
                        append("  ·  ")
                        append("${builder.batteryEntries.sumOf { it.batterySize }} kWh")
                        if (batteryChargeCount > 0) append("  ·  $batteryChargeCount charge")
                        if (batteryDischargeCount > 0) append("  ·  $batteryDischargeCount discharge")
                    }
                } else if (noviceMode) "Battery storage and schedules" else null,
                isComplete = batteryCount > 0,
                isLocked = !builder.isStartComplete,
                lockedHint = "Complete Start first",
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
            WizardAccordionSection(
                id = "hotwater",
                iconContent = {
                    val hwRes = if (hwSystemCount > 0) R.drawable.waterwarm else R.drawable.watercold
                    Icon(painterResource(hwRes), null, Modifier.size(26.dp), tint = Color.Unspecified)
                },
                title = "Hot Water",
                isLinked = builder.isLinked && (hwSystemCount > 0 || hwScheduleCount > 0),
                subtitle = if (!expandedSections.contains("hotwater") && (hwSystemCount > 0 || hwScheduleCount > 0 || hwDivertActive)) {
                    buildString {
                        builder.hwSystem?.let { append("${it.capacity} L  ·  ${it.rate} kW") }
                        if (hwScheduleCount > 0) {
                            if (isNotEmpty()) append("  ·  ")
                            append("$hwScheduleCount schedule${if (hwScheduleCount > 1) "s" else ""}")
                        }
                        if (hwDivertActive) {
                            if (isNotEmpty()) append("  ·  ")
                            append("divert")
                        }
                    }
                } else if (noviceMode) "Immersion heater and solar divert" else null,
                isComplete = hwSystemCount > 0,
                isLocked = !builder.isStartComplete,
                lockedHint = "Complete Start first",
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
            WizardAccordionSection(
                id = "ev",
                iconContent = {
                    val evRes = if (evCount > 0 || evDivertCount > 0) R.drawable.ev_on else R.drawable.ev_off
                    Icon(painterResource(evRes), null, Modifier.size(26.dp), tint = Color.Unspecified)
                },
                title = "EV",
                isLinked = builder.isLinked && (evCount > 0 || evDivertCount > 0),
                subtitle = if (!expandedSections.contains("ev") && (evCount > 0 || evDivertCount > 0)) {
                    buildString {
                        if (evCount > 0) append("$evCount schedule${if (evCount > 1) "s" else ""}")
                        if (evCount > 0 && evDivertCount > 0) append(" · ")
                        if (evDivertCount > 0) append("$evDivertCount divert${if (evDivertCount > 1) "s" else ""}")
                    }
                } else if (noviceMode) "EV charge schedules and solar divert" else null,
                isComplete = evCount > 0 || evDivertCount > 0,
                isLocked = !builder.isLoadComplete,
                lockedHint = "Complete Usage Data first",
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
            WizardAccordionSection(
                id = "heatpump",
                iconContent = { Text(if (heatPumpCount > 0) "♨️" else "❄️", fontSize = 22.sp) },
                title = "Heat Pump",
                isLinked = builder.isLinked && heatPumpCount > 0,
                subtitle = if (!expandedSections.contains("heatpump") && heatPumpCount > 0) {
                    val hp = builder.heatPumpEntries.first()
                    "${hp.fuelType} · SCOP ${hp.scop}"
                } else if (noviceMode) "Model a heat pump from your current heating" else null,
                isComplete = heatPumpCount > 0,
                isLocked = !builder.isLoadComplete,
                lockedHint = "Complete Usage Data first",
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
private enum class WizardImportScope { USAGE, INVERTERS, PV, BATTERY, HW, EV, HEATPUMP }

/* ──────────────────────────────────────────────────────────────────
   Progress strip
────────────────────────────────────────────────────────────────── */

@Composable
private fun WizardProgressStrip(builder: WizardBuilder) {
    val sections = listOf(
        builder.isStartComplete,
        builder.isLoadComplete,
        builder.inverterEntries.isNotEmpty(),
        builder.panelEntries.isNotEmpty(),
        builder.batteryEntries.isNotEmpty(),
        builder.hwSystem != null || builder.hwSchedules.isNotEmpty() || builder.hwDivert.active,
        builder.evEntries.isNotEmpty() || builder.evDivertEntries.isNotEmpty()
    )
    val done = sections.count { it }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            sections.forEach { complete ->
                Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("$done of ${sections.size} configured",
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
                .semantics { contentDescription = "Wizard section: $title" }
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
                        Icon(Icons.Default.Link, contentDescription = "Linked",
                            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                isComplete -> Icon(Icons.Default.CheckCircle, contentDescription = "Complete",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                isLocked   -> Icon(Icons.Default.Lock, contentDescription = "Locked",
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
   Scenario picker dialog (for Copy / Link selection)
────────────────────────────────────────────────────────────────── */

@Composable
private fun ScenarioPickerDialog(
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
                Text("No existing simulations found.", style = MaterialTheme.typography.bodyMedium)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ──────────────────────────────────────────────────────────────────
   Start section content
────────────────────────────────────────────────────────────────── */

@Composable
private fun StartSectionContent(
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
            title = "Copy from simulation",
            scenarios = allScenarios,
            onSelect = { id -> onLoadForCopy(id) },
            onDismiss = { showCopyPicker = false }
        )
    }
    if (showLinkPicker) {
        ScenarioPickerDialog(
            title = "Link to simulation",
            scenarios = allScenarios,
            onSelect = { id -> onLoadForLink(id) },
            onDismiss = { showLinkPicker = false }
        )
    }
    if (showScratchConfirm) {
        AlertDialog(
            onDismissRequest = { showScratchConfirm = false },
            title = { Text("Start from scratch?") },
            text = { Text("This will clear the Usage Data and EV sections. Your simulation name will be kept. Continue?") },
            confirmButton = {
                Button(onClick = {
                    onUpdate { b -> WizardBuilder(scenarioMode = ScenarioMode.NEW, scenarioName = b.scenarioName) }
                    showScratchConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showScratchConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showImportSheet) {
        UI2ImportSheet(
            title = "Import scenario from JSON",
            hint = "Accepts the JSON shape produced by the Share button on a scenario, " +
                    "or a single scenario from a bulk export. The accordions will be pre-filled — " +
                    "you can edit anything before saving.",
            applyLabel = "Load into wizard",
            parse = ::parseScenarioImportJson,
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
            title = { Text("Multiple scenarios") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "This file contains ${list.size} scenarios. Pick the one to load into " +
                            "the wizard. (To import them all into the library instead, use the " +
                            "drawer's Import / Export screen.)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    list.forEachIndexed { idx, file ->
                        OutlinedButton(
                            onClick = {
                                importPicker = null
                                onLoadFromJson(file)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${idx + 1}. ${file.name ?: "(unnamed)"}",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importPicker = null }) { Text("Cancel") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (noviceMode) {
            Text("Every simulation starts from one of four places. Pick what fits.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            listOf(
                Triple(ScenarioMode.NEW,    "From scratch",   "Start with empty sections and build up."),
                Triple(ScenarioMode.COPY,   "Copy existing",  "Duplicate a saved simulation, then edit freely."),
                Triple(ScenarioMode.LINK,   "Link existing",  "Re-use components from a saved simulation."),
                Triple(ScenarioMode.IMPORT, "Import JSON",    "Paste or pick a scenario JSON file to pre-fill every section.")
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
                    label = { Text("New") },
                    leadingIcon = { Icon(Icons.Default.Build, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.scenarioMode == ScenarioMode.COPY,
                    onClick = { showCopyPicker = true },
                    label = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.scenarioMode == ScenarioMode.LINK,
                    onClick = { showLinkPicker = true },
                    label = { Text("Link") },
                    leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.scenarioMode == ScenarioMode.IMPORT,
                    onClick = { showImportSheet = true },
                    label = { Text("Import") },
                    leadingIcon = { Icon(Icons.Default.FileUpload, null, Modifier.size(16.dp)) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = builder.scenarioName,
            onValueChange = { onUpdate { b -> b.copy(scenarioName = it) } },
            label = { Text("Simulation name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = nameError != null,
            supportingText = {
                when {
                    nameError != null -> Text(nameError, color = MaterialTheme.colorScheme.error)
                    noviceMode -> Text("A unique name — shown everywhere in the app.")
                }
            }
        )
    }
}

/* ──────────────────────────────────────────────────────────────────
   Usage Data section content
────────────────────────────────────────────────────────────────── */

private data class SourceOption(
    val src: LoadSource,
    val title: String,
    val desc: String,
    val disabled: Boolean = false
)

private val USAGE_SOURCE_OPTIONS = listOf(
    SourceOption(LoadSource.SOURCE, "Derive from a source",
        "Smart-meter export or utility import."),
    SourceOption(LoadSource.SLP, "Standard load profile",
        "One of 6 pre-built Irish residential profiles."),
    SourceOption(LoadSource.COPY_PROFILE, "Copy from existing",
        "Import distribution from another simulation, then edit freely."),
    SourceOption(LoadSource.HAND, "Hand-craft",
        "Set hourly, daily and monthly weightings manually."),
    SourceOption(LoadSource.LINKED, "Link to existing profile",
        "Re-use a profile from another simulation. Changes in source propagate here.")
)

@Composable
private fun UsageDataSectionContent(
    builder: WizardBuilder,
    noviceMode: Boolean,
    allScenarios: List<Scenario>,
    availableSources: List<SourceDateRange>,
    isDeriving: Boolean,
    onUpdate: ((WizardBuilder) -> WizardBuilder) -> Unit,
    onLoadSLP: (String) -> Unit,
    onLoadProfileForCopy: (Long) -> Unit,
    onLoadProfileForLink: (Long) -> Unit,
    onInitHandCraft: () -> Unit,
    onDeriveFromSource: (String, ComparisonUIViewModel.Importer, LocalDate, LocalDate, Boolean, Boolean) -> Unit,
    onImport: () -> Unit
) {
    var advancedTab          by remember { mutableIntStateOf(0) }
    var showCopyProfilePicker by remember { mutableStateOf(false) }
    var showLinkProfilePicker by remember { mutableStateOf(false) }
    var showHandCraftDialog   by remember { mutableStateOf(false) }
    var showDeriveDialog      by remember { mutableStateOf(false) }
    var showSLPDialog         by remember { mutableStateOf(false) }

    if (showCopyProfilePicker) {
        ScenarioPickerDialog(
            title = "Copy load profile from",
            scenarios = allScenarios,
            onSelect = { id -> onLoadProfileForCopy(id) },
            onDismiss = { showCopyProfilePicker = false }
        )
    }
    if (showLinkProfilePicker) {
        ScenarioPickerDialog(
            title = "Link load profile from",
            scenarios = allScenarios,
            onSelect = { id -> onLoadProfileForLink(id) },
            onDismiss = { showLinkProfilePicker = false }
        )
    }
    if (showHandCraftDialog) {
        HandCraftDialog(
            hourly  = builder.loadProfileHourly  ?: List(24) { 100.0 / 24.0 },
            daily   = builder.loadProfileDaily   ?: List(7)  { 100.0 / 7.0 },
            monthly = builder.loadProfileMonthly ?: List(12) { 100.0 / 12.0 },
            onSave  = { h, d, m ->
                onUpdate { b -> b.copy(loadProfileHourly = h, loadProfileDaily = d, loadProfileMonthly = m) }
            },
            onDismiss = { showHandCraftDialog = false }
        )
    }
    if (showDeriveDialog) {
        DeriveFromSourceDialog(
            availableSources = availableSources,
            isDeriving = isDeriving,
            onDerive = onDeriveFromSource,
            onDismiss = { showDeriveDialog = false }
        )
    }
    if (showSLPDialog) {
        SLPPickerDialog(
            currentProfile = builder.slpProfile,
            onSelect = { onLoadSLP(it) },
            onDismiss = { showSLPDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryTabRow(selectedTabIndex = advancedTab) {
            Tab(selected = advancedTab == 0, onClick = { advancedTab = 0 }, text = { Text("Basic") })
            Tab(selected = advancedTab == 1, onClick = { advancedTab = 1 }, text = { Text("Advanced") })
        }

        if (noviceMode) {
            Text("Choose how the load profile is built. Annual usage (kWh) is always required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("SOURCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

            val noviceVisible = USAGE_SOURCE_OPTIONS.filter {
                // "Derive from a source" is now novice-visible (it's how you turn your own imported smart-meter /
                // inverter data into a load profile). Only Hand-craft stays advanced-only.
                it.src != LoadSource.HAND
            }
            noviceVisible.forEach { opt ->
                val selected = builder.loadSource == opt.src
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
                            when (opt.src) {
                                LoadSource.SOURCE       -> { onUpdate { it.copy(loadSource = LoadSource.SOURCE, isLoadLinked = false) }; showDeriveDialog = true }
                                LoadSource.SLP          -> { onUpdate { it.copy(loadSource = LoadSource.SLP,    isLoadLinked = false) }; showSLPDialog    = true }
                                LoadSource.COPY_PROFILE -> showCopyProfilePicker = true
                                LoadSource.HAND         -> { onInitHandCraft(); showHandCraftDialog = true }
                                LoadSource.LINKED       -> showLinkProfilePicker = true
                            }
                        }
                        .padding(12.dp)
                ) {
                    Text(opt.title,
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(opt.desc, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = builder.loadSource == LoadSource.SOURCE,
                    onClick  = { onUpdate { it.copy(loadSource = LoadSource.SOURCE, isLoadLinked = false) }; showDeriveDialog = true },
                    label    = { Text("Source") },
                    leadingIcon = { Icon(painterResource(R.drawable.house), null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.loadSource == LoadSource.SLP,
                    onClick  = { onUpdate { it.copy(loadSource = LoadSource.SLP, isLoadLinked = false) }; showSLPDialog = true },
                    label    = { Text("SLP") },
                    leadingIcon = { Icon(Icons.Default.Build, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.loadSource == LoadSource.COPY_PROFILE,
                    onClick  = { showCopyProfilePicker = true },
                    label    = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.loadSource == LoadSource.HAND,
                    onClick  = { onInitHandCraft(); showHandCraftDialog = true },
                    label    = { Text("Craft") },
                    leadingIcon = { Icon(Icons.Default.Build, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.loadSource == LoadSource.LINKED,
                    onClick  = { showLinkProfilePicker = true },
                    label    = { Text("Link") },
                    leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) }
                )
            }
        }

        // Distribution source — read-only, shows what created the current profile
        val sourceLabel = when (builder.loadSource) {
            LoadSource.SOURCE       -> builder.distributionSource.ifBlank { "" }
            LoadSource.SLP          -> builder.slpProfile.ifBlank { "" }
            LoadSource.COPY_PROFILE, LoadSource.HAND, LoadSource.LINKED ->
                builder.distributionSource.ifBlank { "" }
        }
        if (sourceLabel.isNotBlank()) {
            OutlinedTextField(
                value = sourceLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Distribution source") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = if (noviceMode) ({
                    Text("Derived automatically from how the profile was created.")
                }) else null
            )
        }

        // COPY_PROFILE / HAND: edit distribution button
        if (builder.loadSource == LoadSource.COPY_PROFILE || builder.loadSource == LoadSource.HAND) {
            FilledTonalButton(
                onClick = {
                    if (builder.loadProfileHourly == null) onInitHandCraft()
                    showHandCraftDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (builder.hasDistributionData) "Edit distribution" else "Set distribution")
            }
            if (!builder.hasDistributionData && noviceMode) {
                Text("Tap to set hourly, daily and monthly usage patterns.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Annual usage — always show except when SOURCE selected but no data derived yet
        if (builder.loadSource != LoadSource.SOURCE || builder.hasDistributionData) {
                OutlinedTextField(
                    value = builder.annualUsage,
                    onValueChange = { onUpdate { b -> b.copy(annualUsage = it) } },
                    label = { Text("Annual usage (kWh)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    readOnly = builder.loadSource == LoadSource.LINKED,
                    supportingText = if (noviceMode) ({ Text("Total electricity used in a year.") }) else null
                )
            }

            // Distribution charts
            if (builder.hasDistributionData && builder.loadProfileDaily != null && builder.loadProfileMonthly != null) {
                WizardDistributionCharts(
                    hourly  = builder.loadProfileHourly!!,
                    daily   = builder.loadProfileDaily!!,
                    monthly = builder.loadProfileMonthly!!
                )
            }

        // ── Advanced fields ─────────────────────────────────────────
        if (advancedTab == 1) {
            Text("ADVANCED", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
            OutlinedTextField(
                value = builder.hourlyBaseLoad,
                onValueChange = { onUpdate { b -> b.copy(hourlyBaseLoad = it) } },
                label = { Text("Hourly base load (kWh)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                supportingText = if (noviceMode) ({ Text("Always-on load — fridge, standby, etc.") }) else null
            )
            OutlinedTextField(
                value = builder.gridImportMax,
                onValueChange = { onUpdate { b -> b.copy(gridImportMax = it) } },
                label = { Text("Grid import max (kW)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                supportingText = if (noviceMode) ({ Text("Highest single-hour draw from the grid.") }) else null
            )
            OutlinedTextField(
                value = builder.gridExportMax,
                onValueChange = { onUpdate { b -> b.copy(gridExportMax = it) } },
                label = { Text("Grid export max (kW)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                supportingText = if (noviceMode) ({ Text("Highest single-hour export — set by your contract.") }) else null
            )
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import load profile (JSON)")
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Derive from a source dialog
────────────────────────────────────────────────────────────────── */

@Composable
private fun DeriveFromSourceDialog(
    availableSources: List<SourceDateRange>,
    isDeriving: Boolean,
    // (sysSn, importer, from, to, fillGaps, absoluteYear)
    onDerive: (String, ComparisonUIViewModel.Importer, LocalDate, LocalDate, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember { mutableStateOf<SourceDateRange?>(null) }
    // Advanced PeriodSelector state: a D/M/Y trailing window ending on the anchor.
    var period         by remember { mutableStateOf(DataSourcePeriod.YEAR) }
    var anchor         by remember { mutableStateOf(LocalDate.now()) }
    var fillGaps       by remember { mutableStateOf(true) }
    // Averages = an average hourly/daily/monthly shape over the window; Absolute year = the source's real
    // measured series for the chosen year mapped onto the 2001 grid (plus that year's distribution).
    var absoluteYear   by remember { mutableStateOf(false) }

    fun onSourcePicked(src: SourceDateRange) {
        selectedSource = src
        period = DataSourcePeriod.YEAR
        anchor = LocalDate.parse(src.finishDate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Derive from a source") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (availableSources.isEmpty()) {
                    item {
                        Text("No imported data found. Import smart-meter or inverter data first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Extraction", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !absoluteYear,
                                    onClick  = { absoluteYear = false },
                                    label    = { Text("Averages") }
                                )
                                FilterChip(
                                    selected = absoluteYear,
                                    onClick  = { absoluteYear = true; period = DataSourcePeriod.YEAR },
                                    label    = { Text("Absolute year") }
                                )
                            }
                            Text(
                                if (absoluteYear)
                                    "Uses the actual measured load for the chosen year, mapped onto the 2001 simulation grid."
                                else
                                    "Builds an average hourly/daily/monthly shape over the chosen window.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Text("Select a data source:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    items(availableSources) { src ->
                        val sel = selectedSource == src
                        val typeLabel = when (src.importerType) {
                            ComparisonUIViewModel.Importer.ALPHAESS        -> "AlphaESS"
                            ComparisonUIViewModel.Importer.ESBNHDF         -> "ESBN"
                            ComparisonUIViewModel.Importer.HOME_ASSISTANT  -> "Home Assistant"
                            ComparisonUIViewModel.Importer.OCTOPUS         -> "Octopus"
                            else -> "Unknown"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp))
                                .clickable { onSourcePicked(src) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("$typeLabel — ${src.sysSn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (sel) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface)
                                Text("${src.startDate} → ${src.finishDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (sel) Icon(Icons.Default.CheckCircle, null,
                                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Meter-only (ESBN / Octopus) warning
                    if (selectedSource?.importerType == ComparisonUIViewModel.Importer.ESBNHDF ||
                        selectedSource?.importerType == ComparisonUIViewModel.Importer.OCTOPUS) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Warning, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Text("ESBN provides import/export only (no solar or load readings). " +
                                     "Load is estimated from grid import — this will be inaccurate if " +
                                     "solar panels or a battery are present.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    val src = selectedSource
                    if (src != null) {
                        item {
                            PeriodSelector(
                                selectedPeriod = period,
                                anchorDate     = anchor,
                                dataStart      = src.startDate,
                                dataEnd        = src.finishDate,
                                advanced       = true,
                                onPeriodChange = { p, a, _ -> period = p; anchor = a },
                                onNavigate     = { fwd, _ ->
                                    anchor = stepAnchor(anchor, period, fwd,
                                        LocalDate.parse(src.startDate),
                                        LocalDate.parse(src.finishDate))
                                }
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fill gaps with SLP", style = MaterialTheme.typography.bodySmall)
                                    Text("Patch missing days using a standard load profile.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = fillGaps, onCheckedChange = { fillGaps = it })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val src = selectedSource
            Button(
                onClick = {
                    if (src != null) {
                        val startD  = LocalDate.parse(src.startDate)
                        val finishD = LocalDate.parse(src.finishDate)
                        val (rawFrom, rawTo) = periodDateRange(period, anchor, true, startD, finishD)
                        onDerive(src.sysSn, src.importerType,
                                 rawFrom.coerceIn(startD, finishD),
                                 rawTo.coerceIn(startD, finishD), fillGaps, absoluteYear)
                        onDismiss()
                    }
                },
                enabled = src != null && !isDeriving
            ) { Text(if (isDeriving) "Deriving…" else "Derive") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ──────────────────────────────────────────────────────────────────
   SLP picker dialog
────────────────────────────────────────────────────────────────── */

@Composable
private fun SLPPickerDialog(
    currentProfile: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Standard load profile") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(StandardLoadProfiles.standardLoadProfiles.toList()) { profileName ->
                    val sel = profileName == currentProfile
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp,
                                if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp))
                            .clickable { onSelect(profileName); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(profileName, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            color = if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                        if (sel) Icon(Icons.Default.CheckCircle, null,
                            Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ──────────────────────────────────────────────────────────────────
   Hand-craft distribution dialog
────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandCraftDialog(
    hourly: List<Double>,
    daily: List<Double>,
    monthly: List<Double>,
    onSave: (List<Double>, List<Double>, List<Double>) -> Unit,
    onDismiss: () -> Unit
) {
    var localHourly  by remember { mutableStateOf(hourly) }
    var localDaily   by remember { mutableStateOf(daily) }
    var localMonthly by remember { mutableStateOf(monthly) }
    var tab          by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Edit distribution", style = MaterialTheme.typography.titleMedium) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                onSave(localHourly, localDaily, localMonthly)
                                onDismiss()
                            }) { Text("Save") }
                        }
                    )
                }
            ) { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues)) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Hourly") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Daily") })
                        Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Monthly") })
                    }
                    when (tab) {
                        0 -> DistributionSliderList(
                            values = localHourly,
                            labels = (0..23).map { "%02d:00".format(it) },
                            uniformValue = 100.0 / 24.0,
                            onChange = { idx, v -> localHourly  = localHourly.toMutableList().also  { it[idx] = v } },
                            onReset  = { localHourly = List(24) { 100.0 / 24.0 } }
                        )
                        1 -> DistributionSliderList(
                            values = localDaily,
                            labels = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun"),
                            uniformValue = 100.0 / 7.0,
                            onChange = { idx, v -> localDaily   = localDaily.toMutableList().also   { it[idx] = v } },
                            onReset  = { localDaily = List(7) { 100.0 / 7.0 } }
                        )
                        2 -> DistributionSliderList(
                            values = localMonthly,
                            labels = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"),
                            uniformValue = 100.0 / 12.0,
                            onChange = { idx, v -> localMonthly = localMonthly.toMutableList().also { it[idx] = v } },
                            onReset  = { localMonthly = List(12) { 100.0 / 12.0 } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionSliderList(
    values: List<Double>,
    labels: List<String>,
    uniformValue: Double,
    onChange: (Int, Double) -> Unit,
    onReset: () -> Unit
) {
    val maxSlider = (uniformValue * 4).toFloat().coerceAtLeast(20f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sum: ${"%.1f".format(values.sum())}%", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onReset) { Text("Reset to flat") }
        }
        LazyColumn {
            itemsIndexed(values) { idx, value ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(labels.getOrElse(idx) { idx.toString() },
                        modifier = Modifier.width(56.dp),
                        style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { onChange(idx, it.toDouble()) },
                        valueRange = 0f..maxSlider,
                        modifier = Modifier.weight(1f)
                    )
                    Text("%.1f".format(value),
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Battery section content
────────────────────────────────────────────────────────────────── */

@Composable
private fun BatterySectionContent(
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
            Tab(selected = advancedTab == 0, onClick = { advancedTab = 0 }, text = { Text("Basic") })
            Tab(selected = advancedTab == 1, onClick = { advancedTab = 1 }, text = { Text("Advanced") })
        }
        Spacer(Modifier.height(4.dp))
        // ── Batteries ──────────────────────────────────────────────
        WizardScheduleLabel("Batteries")
        if (noviceMode) {
            Text("Each battery stores excess solar (or off-peak grid) energy for use later. " +
                "Add one battery per physical unit; multiple batteries on the same inverter are fine.",
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
                    Text("No batteries yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("Optional — skip if you have no battery storage.",
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
            Text("Add battery")
        }

        Spacer(Modifier.height(4.dp))

        // ── Charge Schedule ────────────────────────────────────────
        WizardScheduleLabel("Charge schedule")
        if (noviceMode) {
            Text("Charge from the grid during cheap windows (e.g. night-saver) so the stored energy " +
                "covers expensive peak hours. Add a window for each cheap-rate period; leave empty if " +
                "you only want solar charging.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (entries.isEmpty() && chargeEntries.isEmpty()) {
            Text("Add a battery above to enable charge scheduling.",
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
                    Text("No charge windows", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("Add one if your tariff has cheap off-peak hours worth pre-charging from.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            if (entries.isEmpty()) {
                Text("⚠  Schedules below target an inverter with no batteries — add a battery to make them effective.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
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

        FilledTonalButton(
            onClick = onAddCharge,
            modifier = Modifier.fillMaxWidth(),
            enabled = entries.isNotEmpty()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add charge window")
        }

        Spacer(Modifier.height(4.dp))

        // ── Discharge Schedule ─────────────────────────────────────
        WizardScheduleLabel("Discharge schedule")
        if (noviceMode) {
            Text("Force the battery to export to the grid during a chosen window — useful when feed-in " +
                "rates are higher than the avoided import cost (peak-export tariffs). Leave empty for " +
                "standard self-consumption only.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (entries.isEmpty() && dischargeEntries.isEmpty()) {
            Text("Add a battery above to enable discharge scheduling.",
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
                    Text("No discharge windows", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("Add one if your tariff has high feed-in rates worth exporting to.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            if (entries.isEmpty()) {
                Text("⚠  Schedules below target an inverter with no batteries — add a battery to make them effective.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
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

        FilledTonalButton(
            onClick = onAddDischarge,
            modifier = Modifier.fillMaxWidth(),
            enabled = entries.isNotEmpty()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add discharge window")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Import battery setup (JSON)")
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Hot Water section content
────────────────────────────────────────────────────────────────── */

@Composable
private fun HwSectionContent(
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
            Tab(selected = advancedTab == 0, onClick = { advancedTab = 0 }, text = { Text("Basic") })
            Tab(selected = advancedTab == 1, onClick = { advancedTab = 1 }, text = { Text("Advanced") })
        }
        Spacer(Modifier.height(4.dp))

        // ── Tank ────────────────────────────────────────────────────
        WizardScheduleLabel("Tank")
        if (noviceMode) {
            Text("Configure the hot-water cylinder so the simulation can model when the immersion " +
                "is needed and how much excess solar can be diverted to it.",
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
                    Text("No hot-water tank", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("Optional — skip if hot water isn't part of this scenario.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FilledTonalButton(onClick = onEnableSystem, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add hot-water tank")
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
        WizardScheduleLabel("Heater schedule")
        if (noviceMode) {
            Text("Windows when the immersion is allowed to run from the grid (e.g. cheap night-rate). " +
                "Leave empty if you only want hot water from solar divert.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (system == null && schedules.isEmpty()) {
            Text("Add a tank above to enable scheduling.",
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
                    Text("No heater windows", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("Add one if your tariff has a cheap window worth heating the tank in.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
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

        FilledTonalButton(
            onClick = onAddSchedule,
            modifier = Modifier.fillMaxWidth(),
            enabled = system != null
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add heater window")
        }

        Spacer(Modifier.height(4.dp))

        // ── Solar Divert ───────────────────────────────────────────
        WizardScheduleLabel("Solar divert")
        if (noviceMode) {
            Text("Send excess solar to the immersion instead of exporting it. Free hot water when " +
                "the panels are producing more than the house is using.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Divert excess solar to hot water",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (divert.active) "Active" else "Off",
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
            Text("Add a tank above to enable solar divert.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp))
        }

        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Import hot-water setup (JSON)")
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   EV section content
────────────────────────────────────────────────────────────────── */

@Composable
private fun EvSectionContent(
    entries: List<WizardEvEntry>,
    divertEntries: List<WizardEvDivertEntry>,
    noviceMode: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardEvEntry) -> WizardEvEntry) -> Unit,
    onAddDivert: () -> Unit,
    onRemoveDivert: (String) -> Unit,
    onUpdateDivert: (String, (WizardEvDivertEntry) -> WizardEvDivertEntry) -> Unit,
    onImport: () -> Unit
) {
    var expandedEntryId by remember { mutableStateOf<String?>(null) }
    var expandedDivertId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // ── Charging Schedules ───────────────────────────────────
        WizardScheduleLabel("Charging Schedules")
        if (noviceMode) {
            Text("Add scheduled EV charging windows. Each window defines when the car charges and at what rate.",
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
                    Text("🚗", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("No EV schedules yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("This section is optional — skip it if you don't have an EV.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            entries.forEachIndexed { index, entry ->
                WizardEvEntryCard(
                    entry = entry,
                    index = index,
                    expanded = expandedEntryId == entry.id,
                    noviceMode = noviceMode,
                    onToggle = { expandedEntryId = if (expandedEntryId == entry.id) null else entry.id },
                    onUpdate = { updated -> onUpdate(entry.id) { updated } },
                    onDelete = { onRemove(entry.id); if (expandedEntryId == entry.id) expandedEntryId = null }
                )
            }
        }

        FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add EV schedule")
        }

        Spacer(Modifier.height(4.dp))

        // ── Solar Divert ─────────────────────────────────────────
        WizardScheduleLabel("Solar Divert")
        if (noviceMode) {
            Text("Divert surplus solar energy to charge the EV within a defined time window.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (divertEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_baseline_call_split_24), null,
                        Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(6.dp))
                    Text("No divert windows", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("Optional — add if you want to charge the EV from excess solar.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            divertEntries.forEachIndexed { index, entry ->
                WizardEvDivertCard(
                    entry = entry,
                    index = index,
                    expanded = expandedDivertId == entry.id,
                    noviceMode = noviceMode,
                    onToggle = { expandedDivertId = if (expandedDivertId == entry.id) null else entry.id },
                    onUpdate = { updated -> onUpdateDivert(entry.id) { updated } },
                    onDelete = { onRemoveDivert(entry.id); if (expandedDivertId == entry.id) expandedDivertId = null }
                )
            }
        }

        FilledTonalButton(onClick = onAddDivert, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add divert window")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Import EV setup (JSON)")
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Heat Pump section content (Phase 5 of plans/hp/plan.md)
   ────────────────────────────────────────────────────────────────── */

private fun defaultCalorific(fuel: String): Double = when (fuel) {
    "Natural gas" -> 1.0      // entered in kWh already
    "LPG" -> 7.08
    else -> 10.35             // kerosene / home-heating oil
}

// A sensible default annual use per fuel — switching fuel changes the unit (litres ↔ kWh), so the figure
// is reset to avoid a nonsensical implied result (e.g. 2300 litres carried into a kWh gas field → ~0).
private fun defaultAnnualUse(fuel: String): String = when (fuel) {
    "Natural gas" -> "11000"  // kWh / yr
    "LPG" -> "2000"           // litres / yr
    else -> "2300"            // litres / yr
}

@Composable
private fun HeatPumpSectionContent(
    entries: List<WizardHeatPumpEntry>,
    noviceMode: Boolean,
    cdsDateRange: Pair<String, String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardHeatPumpEntry) -> WizardHeatPumpEntry) -> Unit,
    onImport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (noviceMode && entries.isEmpty()) {
            Text("Estimate the electricity a heat pump would use in place of your oil or gas boiler.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        entries.forEach { hp -> HeatPumpCard(hp, noviceMode, cdsDateRange, onRemove, onUpdate) }
        if (entries.isEmpty()) {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text("Add heat pump")
            }
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Import HP setup (JSON)")
        }
    }
}

@Composable
private fun HpNumberField(
    label: String, value: String, hint: String?, novice: Boolean,
    modifier: Modifier = Modifier, onValue: (String) -> Unit
) {
    // Raw text in state, parsed on save — matches every other wizard input box.
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        supportingText = if (novice && hint != null) ({ Text(hint) }) else null
    )
}

@Composable
private fun HpDoubleField(
    label: String, value: Double, hint: String?, novice: Boolean,
    modifier: Modifier = Modifier, onValue: (Double) -> Unit
) {
    // lat/long are Doubles in the entry (not String like the other fields). Keep the raw text locally so a
    // partial edit ("-", "53.") doesn't fight the committed Double; commit only on a valid parse.
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; it.toDoubleOrNull()?.let(onValue) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        supportingText = if (novice && hint != null) ({ Text(hint) }) else null
    )
}

/**
 * True if a CDS Personal Access Token is stored (and decryptable). Mirrors the
 * gate in [UI2DataSourceManagementViewModel] (DataStore key "cds_key"); the
 * literal key is duplicated here because the VM's constant is file-private.
 * Blocking DataStore read — call from a background dispatcher.
 */
private fun cdsCredentialsPresent(context: android.content.Context): Boolean {
    val app = context.applicationContext as TOUTCApplication
    val raw = app.getStringValueFromDataStore("cds_key")
    if (raw.isNullOrEmpty()) return false
    return runCatching { TOUTCApplication.decryptString(raw) }.getOrNull()?.isNotEmpty() == true
}

/**
 * Plain-language wind-sensitivity levels for the "Heat energy required" sub-section. Each maps to the model's
 * `alphaWind` (wind-infiltration coefficient): higher ⇒ a draughtier home, which concentrates heat
 * demand onto cold + windy peaks and pushes more onto the expensive backup heater. The default
 * `0.03` lands in [WIND_LEVELS]`[1]` so existing scenarios open unchanged.
 */
private data class WindLevel(val label: String, val short: String, val alphaWind: Double)
private val WIND_LEVELS = listOf(
    WindLevel("No difference", "Same", 0.0),
    WindLevel("Gets cold quickly", "Cools rapidly", 0.04),
    WindLevel("Drafts / curtains move", "Drafts", 0.10)
)
/** Snap a stored `alphaWind` to the nearest [WindLevel] (bucket midpoints 0.02 / 0.07). */
private fun windLevelFor(alphaWind: Double): WindLevel = when {
    alphaWind < 0.02 -> WIND_LEVELS[0]
    alphaWind < 0.07 -> WIND_LEVELS[1]
    else             -> WIND_LEVELS[2]
}

/**
 * Representative annual heating degree-days for Ireland at a 15.5 °C base — used **only** for the card's
 * live "≈ kWh" headline on a new build (`area × HLI × 24 × HDD ÷ 1000`). The simulation itself derives the
 * real HDD from the chosen weather series, so this constant just makes the on-screen estimate sensible.
 */
private const val IRELAND_HDD_15_5 = 2200.0

/**
 * The (start, end) ISO dates the heat-pump CDS weather query will use — the wizard-side mirror of
 * `HeatPumpWeatherCache.pvSourcePeriod`: a historical PV "Source" range drives the dates, otherwise the 2001
 * reference year. min-start / max-end across any sourced strings (PVGIS/None panels stay on 2001).
 */
private fun cdsQueryDates(panels: List<WizardPanelEntry>): Pair<String, String> {
    val sourced = panels.filter {
        it.pvDataSource == PanelDataSource.SOURCE &&
            it.pvSourceFrom.isNotBlank() && it.pvSourceTo.isNotBlank() &&
            !(it.pvSourceFrom == "2001-01-01" && it.pvSourceTo == "2001-12-31")
    }
    if (sourced.isEmpty()) return "2001-01-01" to "2001-12-31"
    return sourced.minOf { it.pvSourceFrom } to sourced.maxOf { it.pvSourceTo }
}

/**
 * A lightweight expandable sub-section used inside [HeatPumpCard] (Heat energy required / HP characteristics
 * / Location & weather). Deliberately not [WizardAccordionSection], which is the heavy top-level card.
 */
@Composable
private fun HpSubSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                // Summary of the section's current values, shown only while collapsed.
                if (!expanded && subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun HeatPumpCard(
    hp: WizardHeatPumpEntry,
    novice: Boolean,
    cdsDateRange: Pair<String, String>,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardHeatPumpEntry) -> WizardHeatPumpEntry) -> Unit
) {
    fun update(fn: (WizardHeatPumpEntry) -> WizardHeatPumpEntry) = onUpdate(hp.id, fn)

    // CDS weather is gated on credentials configured in Data Source Management.
    // We re-check on each tap (not a cached flag) so credentials set during a
    // round-trip to that screen are picked up without re-entering the wizard.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCdsAlert by remember { mutableStateOf(false) }

    // Basic/Advanced tab (mirrors the Hot-Water card): keeps advanced inputs + their hints out of the
    // novice's way. Independent of noviceMode, which only governs the basic-field hints.
    var showAdvanced by remember { mutableStateOf(false) }
    // Three single-open sub-sections, none open by default.
    var openSection by remember { mutableStateOf("") }
    fun toggle(id: String) { openSection = if (openSection == id) "" else id }
    val newBuild = hp.fuelType == "None"

    // Shared derived values — also feed the collapsed sub-section summaries.
    val scopN = hp.scop.toDoubleOrNull() ?: 3.6
    val estHeat = if (newBuild) {
        // Fabric estimate for the headline only: area·HLI·24·HDD/1000 with a representative Irish HDD.
        // The simulation derives the real HDD from the chosen weather, so this is a ballpark.
        val area = hp.floorAreaM2.toDoubleOrNull() ?: 0.0
        val hli = hp.heatLossIndex.toDoubleOrNull() ?: 0.0
        area * hli * 24.0 * IRELAND_HDD_15_5 / 1000.0
    } else {
        val fuelN = hp.fuelAnnual.toDoubleOrNull() ?: 0.0
        val effN = (hp.boilerEfficiencyPct.toDoubleOrNull() ?: 80.0) / 100.0
        val gross = fuelN * hp.calorificValue * effN
        val frac = hp.spaceHeatingPct.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { it / 100.0 }
        frac?.let { gross * it } ?: (gross - hp.dhwAnnualKWh).coerceAtLeast(0.0)
    }
    val fuelShort = when (hp.fuelType) {
        "Kerosene/Oil" -> "Kerosene"; "Natural gas" -> "Gas"; "LPG" -> "LPG"; "None" -> "New build"; else -> hp.fuelType
    }
    val weatherCached = remember(hp.latitude, hp.longitude, hp.weatherSource) {
        hp.weatherSource == "cds" &&
            HeatPumpWeatherCache.hasAnyCacheForLocation(context, hp.latitude, hp.longitude)
    }
    val heatSummary = "$fuelShort · ≈ ${estHeat.roundToInt()} kWh/yr"
    val hpSummary = "${hp.capacityKw} kW · SCOP ${hp.scop}"
    val weatherSummary = if (hp.weatherSource == "cds") "CDS${if (weatherCached) " · cached" else ""}"
                         else "2001, Ireland"

    // Per-section novice hints — shown above each sub-accordion (matches the other wizard sections).
    val heatHint = "How much heat the home needs — from your current fuel use, or estimated from the " +
        "building fabric for a new build."
    val hpHint = "The heat pump unit's rated efficiency and heat output."
    val weatherHint = "Where the outdoor weather for the hourly simulation comes from, plus how the home " +
        "responds to wind."

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        PrimaryTabRow(selectedTabIndex = if (showAdvanced) 1 else 0) {
            Tab(selected = !showAdvanced, onClick = { showAdvanced = false }, text = { Text("Basic") })
            Tab(selected = showAdvanced, onClick = { showAdvanced = true }, text = { Text("Advanced") })
        }

        // ── 1) Heat energy required — what the home needs (fuel history or new-build fabric) ──
        if (novice) {
            Text(heatHint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HpSubSection("Heat energy required", openSection == "heat", { toggle("heat") },
            subtitle = heatSummary) {
            // Fuel type (or None for a new build with no boiler history). AdaptiveChipRow gives short labels in
            // portrait, full labels on wide screens, and a dropdown at large font — all managed centrally.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Current heating fuel", style = MaterialTheme.typography.labelMedium)
                AdaptiveChipRow(
                    items = listOf("Kerosene/Oil", "Natural gas", "LPG", "None"),
                    isSelected = { it == hp.fuelType },
                    onSelect = { fuel ->
                        if (fuel == "None") update { it.copy(fuelType = "None", alphaWind = 0.0) }
                        else update { it.copy(
                            fuelType = fuel,
                            calorificValue = defaultCalorific(fuel),
                            fuelAnnual = defaultAnnualUse(fuel)
                        ) }
                    },
                    label = { when (it) { "Kerosene/Oil" -> "Kero"; "Natural gas" -> "Gas"; "LPG" -> "LPG"; else -> "None" } },
                    labelLong = { if (it == "None") "None (new build)" else it },
                    shortItemWidth = 60.dp, longItemWidth = 112.dp
                )
                if (newBuild) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HpNumberField("Floor area (m²)", hp.floorAreaM2,
                            "Total heated floor area.", novice, Modifier.weight(1f)) { v -> update { it.copy(floorAreaM2 = v) } }
                        HpNumberField("HLI (W/K/m²)", hp.heatLossIndex,
                            "Whole-house heat loss, fabric + ventilation. A compliant new build is ~0.7–1.2.",
                            novice, Modifier.weight(1f)) { v -> update { it.copy(heatLossIndex = v) } }
                    }
                } else {
                    val unit = if (hp.fuelType == "Natural gas") "kWh / yr" else "litres / yr"
                    HpNumberField("Annual fuel use ($unit)", hp.fuelAnnual,
                        "Your current annual oil/gas use.", novice, Modifier.fillMaxWidth()) { v -> update { it.copy(fuelAnnual = v) } }
                }
            }

            // ── Implied result (always on — a live sanity-check, not a hint) ──
            val elec = if (scopN > 0) estHeat / scopN else 0.0
            Box(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("≈ ${estHeat.roundToInt()} kWh heat/yr   ·   ≈ ${elec.roundToInt()} kWh elec @ SCOP ${fmtNum(scopN)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    if (novice) {
                        Text("Ballpark — the final demand comes from the hourly simulation.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HpNumberField("Desired indoor temperature (°C)", hp.desiredIndoorTemp,
                "Leave equal to today's setpoint to keep current comfort.", novice, Modifier.fillMaxWidth()) {
                    v -> update { it.copy(desiredIndoorTemp = v) }
            }

            // ── Advanced (demand) — hidden until the Advanced tab is selected ──
            if (showAdvanced) {
                if (!newBuild) {
                    HpNumberField("Space heating %", hp.spaceHeatingPct,
                        "% of the fuel that was space heating, not hot water (blank = use the default).",
                        novice, Modifier.fillMaxWidth()) { v -> update { it.copy(spaceHeatingPct = v) } }
                    HpNumberField("Old boiler efficiency %", hp.boilerEfficiencyPct,
                        "Seasonal efficiency of the boiler you're replacing.", novice, Modifier.fillMaxWidth()) { v -> update { it.copy(boilerEfficiencyPct = v) } }
                }
                HpNumberField("Current indoor temperature (°C)", hp.currentIndoorTemp,
                    "Today's setpoint — used to rescale demand if the desired temperature differs.", novice, Modifier.fillMaxWidth()) { v -> update { it.copy(currentIndoorTemp = v) } }
                HpNumberField("Balance-point temperature (°C)", hp.balancePoint,
                    "Outdoor temperature below which the home needs heating.", novice, Modifier.fillMaxWidth()) { v -> update { it.copy(balancePoint = v) } }
            }
        }

        // ── 2) HP characteristics — the unit itself ──
        if (novice) {
            Text(hpHint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HpSubSection("HP characteristics", openSection == "hp", { toggle("hp") },
            subtitle = hpSummary) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HpNumberField("Rated COP", hp.copRated,
                    "Datasheet efficiency at 7 °C outdoor (A7/W35).", novice, Modifier.weight(1f)) { v -> update { it.copy(copRated = v) } }
                HpNumberField("SCOP", hp.scop,
                    "Seasonal efficiency averaged across the year.", novice, Modifier.weight(1f)) { v -> update { it.copy(scop = v) } }
            }
            HpNumberField("Capacity (kW)", hp.capacityKw,
                "Max heat output; the backup heater covers any shortfall.", novice, Modifier.fillMaxWidth()) { v -> update { it.copy(capacityKw = v) } }

            // ── Advanced (HP unit) — hidden until the Advanced tab is selected ──
            if (showAdvanced) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HpNumberField("COP ref temp (°C)", hp.copRefTemp,
                        "Outdoor temperature the rated COP is quoted at.", novice, Modifier.weight(1f)) { v -> update { it.copy(copRefTemp = v) } }
                    HpNumberField("COP slope (/°C)", hp.copSlope,
                        "How much COP changes per °C outdoors.", novice, Modifier.weight(1f)) { v -> update { it.copy(copSlope = v) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = hp.backupHeater, onCheckedChange = { c -> update { it.copy(backupHeater = c) } })
                    Spacer(Modifier.width(8.dp))
                    Text("Backup electric heater")
                }
            }
        }

        // ── 3) Location & weather ──
        if (novice) {
            Text(weatherHint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HpSubSection("Location & weather", openSection == "weather", { toggle("weather") },
            subtitle = weatherSummary) {
            // ── Wind sensitivity (maps to alphaWind) — how fast the home loses heat on cold, windy days ──
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("When the wind blows…", style = MaterialTheme.typography.labelMedium)
                AdaptiveChipRow(
                    items = WIND_LEVELS,
                    isSelected = { windLevelFor(hp.alphaWind) == it },
                    onSelect = { level -> update { it.copy(alphaWind = level.alphaWind) } },
                    label = { it.short },
                    labelLong = { it.label },
                    shortItemWidth = 76.dp, longItemWidth = 150.dp
                )
                if (novice) {
                    Text("A draughty home loses heat fastest on cold, windy days — that can push a heat " +
                        "pump past its capacity onto the expensive backup heater.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Weather data", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = hp.weatherSource == "sample",
                        onClick = { update { it.copy(weatherSource = "sample") } },
                        label = { Text("2001, Ireland") })
                    FilterChip(selected = hp.weatherSource == "cds",
                        onClick = {
                            // Gate: only switch to CDS if credentials are present.
                            // Otherwise alert + point the user at Data Source Management.
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { cdsCredentialsPresent(context) }
                                if (ok) update { it.copy(weatherSource = "cds") }
                                else showCdsAlert = true
                            }
                        },
                        label = { Text("CDS") })
                }
                if (novice) {
                    Text("The 2001, Ireland sample lets you try the heat pump now; CDS fetches real weather for your location.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hp.weatherSource == "cds") {
                    // Surface what CDS will fetch — the criteria live in the HP section (design §4.1a-5) even
                    // though location defaults from the PV array and the period follows the load data.
                    Text("CDS fetch location", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HpDoubleField("Latitude", hp.latitude, null, novice, Modifier.weight(1f)) { v ->
                            update { it.copy(latitude = v) } }
                        HpDoubleField("Longitude", hp.longitude, null, novice, Modifier.weight(1f)) { v ->
                            update { it.copy(longitude = v) } }
                    }
                    val onRefYear = cdsDateRange.first == "2001-01-01" && cdsDateRange.second == "2001-12-31"
                    Text("CDS query: ${cdsDateRange.first} → ${cdsDateRange.second}" +
                        (if (onRefYear) " (reference year)" else " (from your PV source data)"),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 2.dp))
                    Text("Fetched at ERA5 grid node %.2f, %.2f.".format(
                            HeatPumpWeatherCache.snapToEra5Grid(hp.latitude),
                            HeatPumpWeatherCache.snapToEra5Grid(hp.longitude)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (novice) {
                        Text(if (onRefYear)
                                "Uses the 2001 reference year. Add real PV data from a historical source " +
                                    "(in the PV System section) to align weather to your measured year."
                             else
                                "The period matches your imported PV data so weather and solar share the same year.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Location defaults to your PV array — edit for a different site.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (showCdsAlert) {
            AlertDialog(
                onDismissRequest = { showCdsAlert = false },
                title = { Text("Set up CDS first") },
                text = {
                    Text("Fetching real weather needs a Copernicus CDS Personal Access Token. " +
                        "Add it under Data Source Management, then choose CDS here.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCdsAlert = false
                        context.startActivity(
                            android.content.Intent(context, UI2DataSourceManagementActivity::class.java))
                    }) { Text("Open Data Source Management") }
                },
                dismissButton = {
                    TextButton(onClick = { showCdsAlert = false }) { Text("Cancel") }
                }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onRemove(hp.id) }) {
                Text("Remove heat pump", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Inverter section content
────────────────────────────────────────────────────────────────── */

@Composable
private fun InverterSectionContent(
    entries: List<WizardInverterEntry>,
    noviceMode: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardInverterEntry) -> WizardInverterEntry) -> Unit,
    onImport: () -> Unit
) {
    var expandedEntryId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (noviceMode) {
            Text("Add solar inverters. Each inverter can serve multiple panel arrays and batteries.",
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
                    Icon(painterResource(R.drawable.inverter), contentDescription = null,
                        modifier = Modifier.size(40.dp), tint = Color.Unspecified)
                    Spacer(Modifier.height(6.dp))
                    Text("No inverters yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("This section is optional — skip it if you have no solar or battery.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            entries.forEachIndexed { index, entry ->
                WizardInverterCard(
                    entry = entry,
                    index = index,
                    expanded = expandedEntryId == entry.id,
                    noviceMode = noviceMode,
                    onToggle = { expandedEntryId = if (expandedEntryId == entry.id) null else entry.id },
                    onUpdate = { updated -> onUpdate(entry.id) { updated } },
                    onDelete = { onRemove(entry.id); if (expandedEntryId == entry.id) expandedEntryId = null }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add inverter")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import JSON")
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   PV System section content
────────────────────────────────────────────────────────────────── */

@Composable
private fun PVSystemSectionContent(
    entries: List<WizardPanelEntry>,
    noviceMode: Boolean,
    inverterEntries: List<WizardInverterEntry>,
    availableSources: List<SourceDateRange>,
    panelPvSummary: List<PanelPVSummary> = emptyList(),
    pvgisParamCheck: Map<String, Boolean> = emptyMap(),
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardPanelEntry) -> WizardPanelEntry) -> Unit,
    onRequestLocation: (String) -> Unit,
    onCheckPvgisParams: (String, Double, Double, Int, Int) -> Unit = { _, _, _, _, _ -> },
    onImport: () -> Unit
) {
    var expandedEntryId by remember { mutableStateOf<String?>(null) }
    var advancedTab     by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryTabRow(selectedTabIndex = advancedTab) {
            Tab(selected = advancedTab == 0, onClick = { advancedTab = 0 }, text = { Text("Basic") })
            Tab(selected = advancedTab == 1, onClick = { advancedTab = 1 }, text = { Text("Advanced") })
        }
        Spacer(Modifier.height(4.dp))

        if (noviceMode && advancedTab == 0) {
            Text("Add panel strings. Each string is an independent array with its own orientation and data source.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.solarpanel), contentDescription = null,
                        modifier = Modifier.size(40.dp), tint = Color.Unspecified)
                    Spacer(Modifier.height(6.dp))
                    Text("No panel strings yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (noviceMode) {
                        Spacer(Modifier.height(4.dp))
                        Text("This section is optional — skip it if you have no solar panels.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            entries.forEachIndexed { index, entry ->
                WizardPanelCard(
                    entry = entry,
                    index = index,
                    expanded = expandedEntryId == entry.id,
                    noviceMode = noviceMode,
                    inverterEntries = inverterEntries,
                    availableSources = availableSources,
                    panelMonthlySummary = panelPvSummary.filter { it.panelID == entry.panelIndex },
                    pvgisParamsHaveData = pvgisParamCheck[entry.id] ?: false,
                    showAdvanced = advancedTab == 1,
                    onToggle = { expandedEntryId = if (expandedEntryId == entry.id) null else entry.id },
                    onUpdate = { updated -> onUpdate(entry.id) { updated } },
                    onDelete = { onRemove(entry.id); if (expandedEntryId == entry.id) expandedEntryId = null },
                    onRequestLocation = { onRequestLocation(entry.id) },
                    onCheckPvgisParams = { onCheckPvgisParams(entry.id, entry.latitude, entry.longitude, entry.azimuth, entry.slope) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add string")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Import JSON")
            }
        }
    }
}

@Composable
private fun PvgisBackgroundBanner(stringCount: Int, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Fetching solar data",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text(
                "PVGIS data for $stringCount panel string${if (stringCount > 1) "s" else ""} is being downloaded in the background. " +
                    "The dashboard PV System card will show solar data once complete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            FilledTonalButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
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
                Text("Run the simulation", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("Required: Start and Usage Data. EV is optional.",
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
                    Text("Saved")
                } else {
                    Text("Save")
                }
            }
            Button(
                onClick = onRun,
                enabled = canRun && !isSaving &&
                    simButtonState != SimButtonState.Queued &&
                    simButtonState != SimButtonState.Running
            ) {
                when {
                    isSaving -> Text("Saving…")
                    simButtonState == SimButtonState.Running -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("Running…")
                    }
                    simButtonState == SimButtonState.Done -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Done")
                    }
                    simButtonState == SimButtonState.Failed -> Text("Failed — retry?")
                    simButtonState == SimButtonState.Queued -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Queued")
                    }
                    else -> Text("Run simulation")
                }
            }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f)
            ) {
                Text("Close")
            }
        }
        }
    }
}

/**
 * Per-accordion import sheet. Each [scope] selects the parser and apply
 * lambda. Every parser also accepts a *whole* [ScenarioJsonFile] paste and
 * slices the relevant keys — so a single scenario JSON can feed any accordion
 * one at a time.
 */
@Composable
private fun WizardImportSheet(
    scope: WizardImportScope,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
    viewModel: UI2WizardViewModel
) {
    when (scope) {
        WizardImportScope.USAGE -> UI2ImportSheet(
            title = "Import load profile",
            hint = "Accepts a load-profile JSON or a whole scenario JSON. " +
                    "Annual usage and all distributions will be replaced.",
            parse = ::parseLoadProfileImport,
            onApply = { viewModel.replaceLoadProfileFromJson(it); onApplied() },
            onDismiss = onDismiss
        )
        WizardImportScope.INVERTERS -> UI2ImportSheet(
            title = "Import inverters",
            hint = "Replaces the current inverter list. Accepts an inverter JSON " +
                    "array or a whole scenario JSON.",
            parse = ::parseInvertersImport,
            onApply = { viewModel.replaceInvertersFromJson(it); onApplied() },
            onDismiss = onDismiss
        )
        WizardImportScope.PV -> UI2ImportSheet(
            title = "Import PV panel strings",
            hint = "Replaces the current panel-string list. Accepts a panel JSON " +
                    "array or a whole scenario JSON.",
            parse = ::parsePanelsImport,
            onApply = { viewModel.replacePanelsFromJson(it); onApplied() },
            onDismiss = onDismiss
        )
        WizardImportScope.BATTERY -> UI2ImportSheet(
            title = "Import battery setup",
            hint = "Replaces batteries, charge and discharge schedules together — " +
                    "schedules reference inverter and battery names. Accepts a " +
                    "battery slice or a whole scenario JSON.",
            parse = ::parseBatteryImport,
            onApply = {
                viewModel.replaceBatteryGroupFromJson(it.batteries, it.loadShifts, it.discharges)
                onApplied()
            },
            onDismiss = onDismiss
        )
        WizardImportScope.HW -> UI2ImportSheet(
            title = "Import hot-water setup",
            hint = "Replaces hot-water system, schedules and divert. Accepts a hot-water " +
                    "slice or a whole scenario JSON.",
            parse = ::parseHwImport,
            onApply = {
                viewModel.replaceHwGroupFromJson(it.system, it.schedules, it.divert)
                onApplied()
            },
            onDismiss = onDismiss
        )
        WizardImportScope.EV -> UI2ImportSheet(
            title = "Import EV setup",
            hint = "Replaces EV charge schedules and diverts. Accepts an EV " +
                    "slice or a whole scenario JSON.",
            parse = ::parseEvImport,
            onApply = {
                viewModel.replaceEvGroupFromJson(it.charges, it.diverts, it.legacyDivert)
                onApplied()
            },
            onDismiss = onDismiss
        )
        WizardImportScope.HEATPUMP -> UI2ImportSheet(
            title = "Import heat pump",
            hint = "Replaces the heat pump. Accepts a heat-pump JSON array or a whole scenario JSON.",
            parse = ::parseHeatPumpImport,
            onApply = { viewModel.replaceHeatPumpsFromJson(it); onApplied() },
            onDismiss = onDismiss
        )
    }
}

/**
 * Try to deserialise [text] as either [target] directly, or as a whole
 * [ScenarioJsonFile] from which [extract] pulls the relevant slice. Returns
 * null if both attempts fail. Both routes are wrapped in their own
 * `runCatching` so a JSON shape that decodes into one but not the other still
 * succeeds.
 */
private inline fun <reified Target, R> tryParseSliceOrScenario(
    text: String,
    extract: (ScenarioJsonFile) -> R?,
    decodeTarget: (String) -> Target?,
    convertTarget: (Target) -> R?
): R? {
    runCatching {
        decodeTarget(text)?.let { convertTarget(it) }
    }.getOrNull()?.let { return it }
    runCatching {
        Gson().fromJson(text, ScenarioJsonFile::class.java)?.let(extract)
    }.getOrNull()?.let { return it }
    return null
}

private fun parseLoadProfileImport(text: String): ParsedPreview<com.tfcode.comparetout.model.json.scenario.LoadProfileJson> = try {
    val gson = Gson()
    val lp = tryParseSliceOrScenario(
        text,
        extract = { it.loadProfile },
        decodeTarget = { gson.fromJson(it, com.tfcode.comparetout.model.json.scenario.LoadProfileJson::class.java) },
        convertTarget = { it.takeIf { lp -> lp.annualUsage != null || lp.hourlyDistribution != null } }
    )
    if (lp == null) ParsedPreview.Err("Couldn't find a load profile in the JSON.")
    else ParsedPreview.Ok(lp,
        "Parsed load profile · ${lp.annualUsage ?: 0.0} kWh/yr · base ${lp.hourlyBaseLoad ?: 0.0}")
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

private fun parseInvertersImport(text: String): ParsedPreview<List<com.tfcode.comparetout.model.json.scenario.InverterJson>> = try {
    val gson = Gson()
    val listType = object : TypeToken<List<com.tfcode.comparetout.model.json.scenario.InverterJson>>() {}.type
    val raw = tryParseSliceOrScenario(
        text,
        extract = { it.inverters?.toList() },
        decodeTarget = { gson.fromJson<List<com.tfcode.comparetout.model.json.scenario.InverterJson>?>(it, listType) },
        convertTarget = { it.takeIf { l -> l.isNotEmpty() } }
    )
    if (raw.isNullOrEmpty()) ParsedPreview.Err("No inverters found in the JSON.")
    else ParsedPreview.Ok(raw, "Parsed ${raw.size} inverter${if (raw.size == 1) "" else "s"}")
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

private fun parsePanelsImport(text: String): ParsedPreview<List<com.tfcode.comparetout.model.json.scenario.PanelJson>> = try {
    val gson = Gson()
    val listType = object : TypeToken<List<com.tfcode.comparetout.model.json.scenario.PanelJson>>() {}.type
    val raw = tryParseSliceOrScenario(
        text,
        extract = { it.panels?.toList() },
        decodeTarget = { gson.fromJson<List<com.tfcode.comparetout.model.json.scenario.PanelJson>?>(it, listType) },
        convertTarget = { it.takeIf { l -> l.isNotEmpty() } }
    )
    if (raw.isNullOrEmpty()) ParsedPreview.Err("No PV panel strings found in the JSON.")
    else ParsedPreview.Ok(raw, "Parsed ${raw.size} panel string${if (raw.size == 1) "" else "s"}")
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

private fun parseHeatPumpImport(text: String): ParsedPreview<List<com.tfcode.comparetout.model.json.scenario.HeatPumpJson>> = try {
    val gson = Gson()
    val listType = object : TypeToken<List<com.tfcode.comparetout.model.json.scenario.HeatPumpJson>>() {}.type
    val raw = tryParseSliceOrScenario(
        text,
        extract = { it.heatPumps?.toList() },
        decodeTarget = { gson.fromJson<List<com.tfcode.comparetout.model.json.scenario.HeatPumpJson>?>(it, listType) },
        convertTarget = { it.takeIf { l -> l.isNotEmpty() && l.any { hp -> hp.fuelAnnual != null } } }
    )
    if (raw.isNullOrEmpty()) ParsedPreview.Err("No heat pump found in the JSON.")
    else ParsedPreview.Ok(raw, "Parsed heat pump · ${raw.first().fuelType ?: "?"}")
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

/** Carries the three battery-related lists together since they cross-reference. */
private data class BatteryImportSlice(
    val batteries: List<com.tfcode.comparetout.model.json.scenario.BatteryJson>?,
    val loadShifts: List<com.tfcode.comparetout.model.json.scenario.LoadShiftJson>?,
    val discharges: List<com.tfcode.comparetout.model.json.scenario.DischargeToGridJson>?
)

private fun parseBatteryImport(text: String): ParsedPreview<BatteryImportSlice> = try {
    val scenario = runCatching { Gson().fromJson(text, ScenarioJsonFile::class.java) }.getOrNull()
    val slice = scenario?.let {
        BatteryImportSlice(it.batteries?.toList(), it.loadShifts?.toList(), it.dischargeToGrids?.toList())
    }
    if (slice == null || (slice.batteries.isNullOrEmpty()
            && slice.loadShifts.isNullOrEmpty()
            && slice.discharges.isNullOrEmpty())) {
        ParsedPreview.Err("No battery setup found in the JSON. " +
                "Expected a scenario JSON or an object with \"Batteries\"/\"LoadShift\"/\"DischargeToGrid\".")
    } else {
        val battCount = slice.batteries?.size ?: 0
        val shiftCount = slice.loadShifts?.size ?: 0
        val dischCount = slice.discharges?.size ?: 0
        ParsedPreview.Ok(slice,
            "Parsed $battCount batter${if (battCount == 1) "y" else "ies"} · " +
                "$shiftCount charge · $dischCount discharge schedules")
    }
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

private data class HwImportSlice(
    val system: com.tfcode.comparetout.model.json.scenario.HWSystemJson?,
    val schedules: List<com.tfcode.comparetout.model.json.scenario.HWScheduleJson>?,
    val divert: com.tfcode.comparetout.model.json.scenario.HWDivertJson?
)

private fun parseHwImport(text: String): ParsedPreview<HwImportSlice> = try {
    val scenario = runCatching { Gson().fromJson(text, ScenarioJsonFile::class.java) }.getOrNull()
    val slice = scenario?.let {
        HwImportSlice(it.hwSystem, it.hwSchedules?.toList(), it.hwDivert)
    }
    if (slice == null || (slice.system == null && slice.schedules.isNullOrEmpty() && slice.divert == null)) {
        ParsedPreview.Err("No hot-water setup found in the JSON. " +
                "Expected a scenario JSON or an object with \"HWSystem\"/\"HWSchedule\"/\"HWDivert\".")
    } else {
        val schedCount = slice.schedules?.size ?: 0
        ParsedPreview.Ok(slice,
            "Parsed " + (if (slice.system != null) "hot-water system · " else "") +
                "$schedCount schedule${if (schedCount == 1) "" else "s"}" +
                (if (slice.divert?.active == true) " · divert active" else ""))
    }
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

private data class EvImportSlice(
    val charges: List<com.tfcode.comparetout.model.json.scenario.EVChargeJson>?,
    val diverts: List<com.tfcode.comparetout.model.json.scenario.EVDivertJson>?,
    val legacyDivert: com.tfcode.comparetout.model.json.scenario.EVDivertJson?
)

private fun parseEvImport(text: String): ParsedPreview<EvImportSlice> = try {
    val scenario = runCatching { Gson().fromJson(text, ScenarioJsonFile::class.java) }.getOrNull()
    val slice = scenario?.let {
        EvImportSlice(it.evCharges?.toList(), it.evDiverts?.toList(), it.evDivert)
    }
    if (slice == null || (slice.charges.isNullOrEmpty() && slice.diverts.isNullOrEmpty() && slice.legacyDivert == null)) {
        ParsedPreview.Err("No EV setup found in the JSON. " +
                "Expected a scenario JSON or an object with \"EVCharge\"/\"EVDivert\"/\"EVDiverts\".")
    } else {
        val chargeCount = slice.charges?.size ?: 0
        val divertCount = (slice.diverts?.size ?: 0) + (if (slice.legacyDivert != null) 1 else 0)
        ParsedPreview.Ok(slice,
            "Parsed $chargeCount EV schedule${if (chargeCount == 1) "" else "s"} · " +
                "$divertCount divert${if (divertCount == 1) "" else "s"}")
    }
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

/**
 * Parse text that the user pasted (or file content read) into a list of
 * scenarios. Accepts both the bulk-export shape (`[…]`) and the single-object
 * shape produced by Phase A's per-scenario Share button (`{…}`). Returns a
 * [ParsedPreview.Err] for malformed input — the sheet renders it in red.
 */
private fun parseScenarioImportJson(text: String): ParsedPreview<List<ScenarioJsonFile>> = try {
    val gson = Gson()
    val trimmed = text.trimStart()
    val raw: List<ScenarioJsonFile>? = if (trimmed.startsWith("[")) {
        val type = object : TypeToken<List<ScenarioJsonFile>>() {}.type
        gson.fromJson<List<ScenarioJsonFile>?>(text, type)
    } else {
        gson.fromJson(text, ScenarioJsonFile::class.java)?.let { listOf(it) }
    }
    val valid = (raw ?: emptyList()).filter { !it.name.isNullOrBlank() }
    if (valid.isEmpty()) ParsedPreview.Err("No scenarios found in the JSON.")
    else ParsedPreview.Ok(
        valid,
        when (valid.size) {
            1 -> {
                val s = valid.first()
                val inv = s.inverters?.size ?: 0
                val panel = s.panels?.size ?: 0
                val batt = s.batteries?.size ?: 0
                "Parsed \"${s.name}\" · $inv inverters · $panel panels · $batt batteries"
            }
            else -> "Parsed ${valid.size} scenarios — you'll pick one to load"
        }
    )
} catch (e: JsonSyntaxException) {
    ParsedPreview.Err(e.message ?: "Malformed JSON")
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}
