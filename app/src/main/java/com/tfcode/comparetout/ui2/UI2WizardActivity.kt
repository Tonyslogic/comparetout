@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@file:Suppress("MissingPermission")

package com.tfcode.comparetout.ui2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tfcode.comparetout.R
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tfcode.comparetout.SimulatorLauncher
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.Scenario
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

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

    val context = LocalContext.current
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
        if (result is WizardSaveResult.Done) {
            when {
                result.runSimulation -> {
                    SimulatorLauncher.simulateIfNeeded(context)
                    simulationQueued = true
                }
                result.pvgisStringsQueued > 0 -> {
                    pvgisQueued = result.pvgisStringsQueued
                    // Don't auto-close; user sees the fetch banner and closes manually
                }
                else -> onClose()
            }
        }
    }

    // Existing names for uniqueness check (exclude current scenario in edit mode)
    val usedNames = remember(allScenarios, viewModel.scenarioId) {
        allScenarios
            .filter { it.scenarioIndex != viewModel.scenarioId }
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

    var menuOpen by remember { mutableStateOf(false) }
    val title = if (viewModel.isEditMode) "Edit scenario" else "Build your scenario"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Novice mode", modifier = Modifier.weight(1f))
                                        Switch(checked = noviceMode,
                                            onCheckedChange = { viewModel.toggleNoviceMode() })
                                    }
                                },
                                onClick = { viewModel.toggleNoviceMode() }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WizardProgressStrip(builder = builder)

            if (pvgisQueued > 0) {
                PvgisBackgroundBanner(stringCount = pvgisQueued, onClose = onClose)
            }

            if (noviceMode) {
                WizardHintBanner("Tap any section to expand it. Start and Usage Data are required to run a simulation.")
            }

            // ── Start ───────────────────────────────────────────────────
            WizardAccordionSection(
                id = "start",
                iconContent = { Text("🌱", style = MaterialTheme.typography.titleSmall) },
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
                    onLoadForLink = { viewModel.loadForLink(it) }
                )
            }

            // ── Usage Data ──────────────────────────────────────────────
            WizardAccordionSection(
                id = "load",
                iconContent = {
                    Icon(painterResource(R.drawable.house), contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
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
                    onDeriveFromSource = { sysSn, importer, from, to, fillGaps ->
                        viewModel.deriveLoadProfileFromSource(sysSn, importer, from, to, fillGaps)
                    }
                )
            }

            // ── Inverters ────────────────────────────────────────────────
            val inverterCount = builder.inverterEntries.size
            WizardAccordionSection(
                id = "inverters",
                iconContent = {
                    val res = if (inverterCount > 0) R.drawable.invertertick else R.drawable.inverter
                    Icon(painterResource(res), null, Modifier.size(20.dp), tint = Color.Unspecified)
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
                    onUpdate = { id, fn -> viewModel.updateInverterEntry(id, fn) }
                )
            }

            // ── PV System ────────────────────────────────────────────────
            val panelCount = builder.panelEntries.size
            WizardAccordionSection(
                id = "pv",
                iconContent = {
                    val pvRes = if (panelCount > 0) R.drawable.solarpaneltick else R.drawable.solarpanel
                    Icon(painterResource(pvRes), null, Modifier.size(20.dp), tint = Color.Unspecified)
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
                    }
                )
            }

            // ── EV ──────────────────────────────────────────────────────
            val evCount = builder.evEntries.size
            WizardAccordionSection(
                id = "ev",
                iconContent = {
                    val evRes = if (evCount > 0) R.drawable.ev_on else R.drawable.ev_off
                    Icon(painterResource(evRes), null, Modifier.size(20.dp), tint = Color.Unspecified)
                },
                title = "EV",
                isLinked = builder.isLinked && evCount > 0,
                subtitle = if (!expandedSections.contains("ev") && evCount > 0)
                    "$evCount schedule${if (evCount > 1) "s" else ""}"
                else if (noviceMode) "Electric vehicle charge schedules" else null,
                isComplete = evCount > 0,
                isLocked = !builder.isLoadComplete,
                lockedHint = "Complete Usage Data first",
                isExpanded = expandedSections.contains("ev"),
                onToggle = { viewModel.toggleSection("ev") }
            ) {
                EvSectionContent(
                    entries = builder.evEntries,
                    noviceMode = noviceMode,
                    onAdd = { viewModel.addEvEntry() },
                    onRemove = { viewModel.removeEvEntry(it) },
                    onUpdate = { id, fn -> viewModel.updateEvEntry(id, fn) }
                )
            }

            // ── Footer ──────────────────────────────────────────────────
            WizardFooter(
                canRun = builder.isRunnable && nameError == null,
                isSaving = isSaving,
                simulationQueued = simulationQueued,
                noviceMode = noviceMode,
                onSave = { viewModel.save(runSimulation = false) },
                onRun = { simulationQueued = false; viewModel.save(runSimulation = true) },
                onClose = onClose
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

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
        builder.evEntries.isNotEmpty()
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
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
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
    onLoadForLink: (Long) -> Unit
) {
    var showCopyPicker    by remember { mutableStateOf(false) }
    var showLinkPicker    by remember { mutableStateOf(false) }
    var showScratchConfirm by remember { mutableStateOf(false) }

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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (noviceMode) {
            Text("Every simulation starts from one of three places. Pick what fits.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            listOf(
                Triple(ScenarioMode.NEW,  "From scratch",   "Start with empty sections and build up."),
                Triple(ScenarioMode.COPY, "Copy existing",  "Duplicate a saved simulation, then edit freely."),
                Triple(ScenarioMode.LINK, "Link existing",  "Re-use components from a saved simulation.")
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
    onDeriveFromSource: (String, ComparisonUIViewModel.Importer, LocalDate, LocalDate, Boolean) -> Unit
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
        TabRow(selectedTabIndex = advancedTab) {
            Tab(selected = advancedTab == 0, onClick = { advancedTab = 0 }, text = { Text("Basic") })
            Tab(selected = advancedTab == 1, onClick = { advancedTab = 1 }, text = { Text("Advanced") })
        }

        if (noviceMode) {
            Text("Choose how the load profile is built. Annual usage (kWh) is always required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("SOURCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

            USAGE_SOURCE_OPTIONS.forEach { opt ->
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
    onDerive: (String, ComparisonUIViewModel.Importer, LocalDate, LocalDate, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember { mutableStateOf<SourceDateRange?>(null) }
    var fromDate       by remember { mutableStateOf("") }
    var toDate         by remember { mutableStateOf("") }
    var fillGaps       by remember { mutableStateOf(true) }

    fun onSourcePicked(src: SourceDateRange) {
        selectedSource = src
        val finish = LocalDate.parse(src.finishDate)
        val start  = LocalDate.parse(src.startDate)
        val from   = finish.minusDays(364).let { if (it < start) start else it }
        fromDate   = from.toString()
        toDate     = src.finishDate
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

                    // ESBN warning
                    if (selectedSource?.importerType == ComparisonUIViewModel.Importer.ESBNHDF) {
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

                    if (selectedSource != null) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = fromDate,
                                    onValueChange = { fromDate = it },
                                    label = { Text("From (YYYY-MM-DD)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = toDate,
                                    onValueChange = { toDate = it },
                                    label = { Text("To (YYYY-MM-DD)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
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
            val fromParsed = runCatching { LocalDate.parse(fromDate) }.getOrNull()
            val toParsed   = runCatching { LocalDate.parse(toDate) }.getOrNull()
            val canDerive  = selectedSource != null && fromParsed != null &&
                             toParsed != null && !fromParsed.isAfter(toParsed) && !isDeriving
            Button(
                onClick = {
                    onDerive(selectedSource!!.sysSn, selectedSource!!.importerType,
                             fromParsed!!, toParsed!!, fillGaps)
                    onDismiss()
                },
                enabled = canDerive
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
                    TabRow(selectedTabIndex = tab) {
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
   EV section content
────────────────────────────────────────────────────────────────── */

@Composable
private fun EvSectionContent(
    entries: List<WizardEvEntry>,
    noviceMode: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdate: (String, (WizardEvEntry) -> WizardEvEntry) -> Unit
) {
    var expandedEntryId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    onUpdate: (String, (WizardInverterEntry) -> WizardInverterEntry) -> Unit
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

        FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add inverter")
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
    onCheckPvgisParams: (String, Double, Double, Int, Int) -> Unit = { _, _, _, _, _ -> }
) {
    var expandedEntryId by remember { mutableStateOf<String?>(null) }
    var advancedTab     by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TabRow(selectedTabIndex = advancedTab) {
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

        FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add panel string")
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

@Composable
private fun WizardFooter(
    canRun: Boolean,
    isSaving: Boolean,
    simulationQueued: Boolean,
    noviceMode: Boolean,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (noviceMode) {
                Text("Run the simulation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Required: Start and Usage Data. EV is optional.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
                Button(
                    onClick = onRun,
                    enabled = canRun && !isSaving
                ) {
                    if (isSaving) {
                        Text("Saving…")
                    } else if (simulationQueued) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Queued")
                    } else {
                        Text("Run simulation")
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
