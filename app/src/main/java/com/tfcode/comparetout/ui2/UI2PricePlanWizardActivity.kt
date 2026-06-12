@file:Suppress("AssignedValueIsNeverRead")

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import dagger.hilt.android.AndroidEntryPoint

// ──────────────────────────────────────────────────────────────────────────
// Supplier-plan wizard. Same shape as the scenario wizard / Directors:
//   - TopAppBar with back arrow.
//   - Action bar with Save / Run calculations / Close.
//   - Accordion sections (Details / Charges / Day rates).
//
// The Day rates section is the heart of the wizard. Each day-rate panel
// pivots on PRICE: you add a cost (c/kWh), then attach one or more time
// ranges to it. A live coverage strip and inline issues mirror the legacy
// validatePlan() rules so the user can see exactly what's missing.
// ──────────────────────────────────────────────────────────────────────────

private enum class PpwFooterAction { SAVE, RUN, CLOSE }

@AndroidEntryPoint
class UI2PricePlanWizardActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                PricePlanWizardScreen(onClose = { finish() })
            }
        }
    }
}

private val DAY_LABELS = listOf(
    1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
    5 to "Fri", 6 to "Sat", 0 to "Sun"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PricePlanWizardScreen(
    viewModel: UI2PricePlanViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val builder by viewModel.builder.collectAsState()
    val isLoading by viewModel.isLoading.observeAsState(false)
    val expanded by viewModel.expandedSections.observeAsState(setOf("details"))
    val expandedDayRates by viewModel.expandedDayRates.observeAsState(emptySet())
    val saveResult by viewModel.saveResult.observeAsState(PricePlanSaveResult.Idle)
    val issues = remember(builder) { validate(builder) }
    val (showHints, toggleShowHints) = rememberShowHints()

    var confirmDiscard by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var importPicker by remember { mutableStateOf<List<PricePlanJsonFile>?>(null) }
    var confirmImportOverwrite by remember { mutableStateOf<PricePlanJsonFile?>(null) }

    LaunchedEffect(saveResult) {
        if (saveResult == PricePlanSaveResult.Saved) onClose()
    }

    val title = when {
        viewModel.isEditMode && builder.planName.isNotBlank() ->
            "Edit · ${builder.supplier} · ${builder.planName}"
        viewModel.isEditMode -> "Edit supplier plan"
        else -> "New supplier plan"
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { confirmDiscard = true }) {
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp)
                ) {
                    PricePlanActionBar(
                        saveResult = saveResult,
                        canRun = issues.isClean,
                        onSave = { viewModel.save(runCosting = false) },
                        onRun = { viewModel.save(runCosting = true) },
                        onClose = { confirmDiscard = true }
                    )
                    Spacer(Modifier.height(6.dp))
                    ImportFromJsonRow(onClick = { showImportSheet = true })
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 104.dp)
                    ) {
                        item("details") {
                            AccordionSection(
                                title = "Plan details",
                                subtitle = if (issues.supplierBlank || issues.planNameBlank)
                                    "Supplier & plan name (required)"
                                else "${builder.supplier} · ${builder.planName}",
                                isComplete = !issues.supplierBlank && !issues.planNameBlank,
                                hasError = issues.supplierBlank || issues.planNameBlank,
                                isExpanded = expanded.contains("details"),
                                onToggle = { viewModel.toggleSection("details") }
                            ) {
                                DetailsSection(builder, issues, viewModel)
                            }
                        }
                        item("charges") {
                            AccordionSection(
                                title = "Charges",
                                subtitle = "Feed €${"%.2f".format(builder.feed)} c · " +
                                    "Standing €${"%.2f".format(builder.standingCharges)}/yr",
                                isComplete = true,
                                hasError = false,
                                isExpanded = expanded.contains("charges"),
                                onToggle = { viewModel.toggleSection("charges") }
                            ) {
                                ChargesSection(builder, viewModel)
                            }
                        }
                        item("rates") {
                            val rateSummary = dayRatesSubtitle(builder, issues)
                            AccordionSection(
                                title = "Day rates",
                                subtitle = rateSummary,
                                isComplete = issues.perDayRate.values.all { it.isClean } &&
                                    !issues.dateRangeOverlap &&
                                    issues.datesMissing.isEmpty() &&
                                    issues.weekdaysMissing.values.all { it.isEmpty() },
                                hasError = issues.dateRangeOverlap ||
                                    issues.datesMissing.isNotEmpty() ||
                                    issues.weekdaysMissing.values.any { it.isNotEmpty() } ||
                                    issues.perDayRate.values.any { !it.isClean },
                                isExpanded = expanded.contains("rates"),
                                onToggle = { viewModel.toggleSection("rates") }
                            ) {
                                DayRatesSection(builder, expandedDayRates, issues, viewModel, showHints)
                            }
                        }
                    }
                }
            }

            // Scrim + right-side drawer (same as every other UI2 screen).
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

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Leave without saving?") },
            text = {
                Text(
                    "Any unsaved changes to this supplier plan will be lost.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            }
        )
    }

    if (showImportSheet) {
        UI2ImportSheet(
            title = "Import supplier-plan JSON",
            hint = "Accepts the JSON shape produced by the Share button on a plan, " +
                    "or a single plan from a bulk export. The wizard will be " +
                    "pre-filled — you can edit anything before saving.",
            applyLabel = "Load into wizard",
            parse = ::parsePricePlanImportJson,
            onApply = { list ->
                showImportSheet = false
                when {
                    list.isEmpty() -> Unit
                    list.size == 1 -> {
                        val one = list.first()
                        // In edit mode, overwriting an existing plan's data is
                        // destructive enough to deserve a confirmation step.
                        // In create mode, just load.
                        if (viewModel.isEditMode) confirmImportOverwrite = one
                        else viewModel.loadFromJson(one)
                    }
                    else -> importPicker = list
                }
            },
            onDismiss = { showImportSheet = false }
        )
    }

    importPicker?.let { list ->
        AlertDialog(
            onDismissRequest = { importPicker = null },
            title = { Text("Multiple supplier plans") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "This file contains ${list.size} plans. Pick the one to load " +
                            "into the wizard. (To import them all into the library " +
                            "instead, use the drawer's Import / Export screen.)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    list.forEachIndexed { idx, file ->
                        OutlinedButton(
                            onClick = {
                                importPicker = null
                                if (viewModel.isEditMode) confirmImportOverwrite = file
                                else viewModel.loadFromJson(file)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val supplier = file.supplier.orEmpty().ifBlank { "?" }
                            val plan = file.plan.orEmpty().ifBlank { "(unnamed)" }
                            Text(
                                "${idx + 1}. $supplier · $plan",
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

    confirmImportOverwrite?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmImportOverwrite = null },
            title = { Text("Replace this plan's data?") },
            text = {
                Text(
                    "The current details, charges and day-rates will be replaced " +
                        "with the contents of the JSON. The plan will still save " +
                        "back to the same row when you tap Save.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.loadFromJson(file)
                    confirmImportOverwrite = null
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImportOverwrite = null }) { Text("Cancel") }
            }
        )
    }
}

/** Compact one-line affordance to open the JSON import sheet from the wizard. */
@Composable
private fun ImportFromJsonRow(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.FileUpload, contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
            )
            Column(Modifier.weight(1f)) {
                Text("Import from JSON",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    "Pre-fill the wizard from a shared plan or bulk-export file.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Parse a clipboard / file payload into a list of [PricePlanJsonFile]. Accepts
 * either a JSON array (bulk-export shape) or a single object (per-plan share
 * shape from the Share button). Mirrors the parser in UI2ImportExportActivity.
 */
private fun parsePricePlanImportJson(text: String): ParsedPreview<List<PricePlanJsonFile>> = try {
    val gson = Gson()
    val trimmed = text.trimStart()
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
            1 -> "Parsed 1 plan: ${valid.first().supplier ?: "?"} · ${valid.first().plan}"
            else -> "Parsed ${valid.size} supplier plans — pick one to load"
        }
    )
} catch (e: JsonSyntaxException) {
    ParsedPreview.Err(e.message ?: "Malformed JSON")
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: "Parse failed")
}

private fun dayRatesSubtitle(b: PricePlanBuilder, i: PlanIssues): String {
    val count = b.dayRates.size
    val countStr = "$count day-rate" + if (count == 1) "" else "s"
    return when {
        i.dateRangeOverlap -> "$countStr · date ranges overlap"
        i.datesMissing.isNotEmpty() ->
            "$countStr · missing ${i.datesMissing.size} date range${if (i.datesMissing.size == 1) "" else "s"}"
        i.weekdaysMissing.values.any { it.isNotEmpty() } -> "$countStr · missing weekdays"
        i.perDayRate.values.any { !it.isClean } -> "$countStr · time gaps remain"
        else -> "$countStr · all dates & minutes covered"
    }
}

// ── action bar ──────────────────────────────────────────────────────────────
@Composable
private fun PricePlanActionBar(
    saveResult: PricePlanSaveResult,
    canRun: Boolean,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onClose: () -> Unit
) {
    val saving = saveResult == PricePlanSaveResult.Saving
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        border = BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            ActionRowCenter {
                AdaptiveCellRow(
                    items = listOf(PpwFooterAction.SAVE, PpwFooterAction.RUN, PpwFooterAction.CLOSE),
                    perRowAtA = 3, perRowAtB = 3, perRowAtC = 1,
                    spacing = 8.dp
                ) { action ->
                    when (action) {
                        PpwFooterAction.SAVE -> OutlinedButton(
                            onClick = onSave, enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when {
                                saveResult == PricePlanSaveResult.Saved -> {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Saved")
                                }
                                saving -> Text("Saving…")
                                else -> Text("Save")
                            }
                        }
                        PpwFooterAction.RUN -> Button(
                            onClick = onRun,
                            enabled = canRun && !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Run calculations") }
                        PpwFooterAction.CLOSE -> OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Close") }
                    }
                }
            }
            if (saveResult == PricePlanSaveResult.Failed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Couldn't save. Fix the highlighted issues and try again.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ── accordion shell (same shape as the scenario wizard) ─────────────────────
@Composable
private fun AccordionSection(
    title: String,
    subtitle: String?,
    isComplete: Boolean,
    hasError: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val borderColor = when {
        hasError -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        isExpanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = if (borderColor != null) BorderStroke(1.dp, borderColor) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = if (hasError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                hasError -> Icon(Icons.Default.Warning, contentDescription = "Has issues",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                isComplete -> Icon(Icons.Default.CheckCircle, contentDescription = "Complete",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                              else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                content()
            }
        }
    }
}

// ── Plan details ────────────────────────────────────────────────────────────
@Composable
private fun DetailsSection(
    builder: PricePlanBuilder,
    issues: PlanIssues,
    vm: UI2PricePlanViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = builder.supplier,
            onValueChange = { v -> vm.updateBuilder { it.copy(supplier = v) } },
            label = { Text("Supplier") },
            isError = issues.supplierBlank,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = builder.planName,
            onValueChange = { v -> vm.updateBuilder { it.copy(planName = v) } },
            label = { Text("Plan name") },
            isError = issues.planNameBlank,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = if (builder.reference == "<REFERENCE>") "" else builder.reference,
            onValueChange = { v -> vm.updateBuilder { it.copy(reference = v.ifBlank { "<REFERENCE>" }) } },
            label = { Text("Reference (optional)") },
            placeholder = { Text("URL or note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SwitchRow(
            title = "Deemed export",
            sub = "Supplier estimates your export instead of measuring it.",
            checked = builder.deemedExport
        ) { v -> vm.updateBuilder { it.copy(deemedExport = v) } }
        if (builder.lastUpdate.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Last updated ${builder.lastUpdate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Charges ─────────────────────────────────────────────────────────────────
@Composable
private fun ChargesSection(builder: PricePlanBuilder, vm: UI2PricePlanViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DoubleField(
            label = "Feed-in tariff (c/kWh)",
            value = builder.feed,
            onValue = { v -> vm.updateBuilder { it.copy(feed = v) } }
        )
        DoubleField(
            label = "Standing charges (€/year)",
            value = builder.standingCharges,
            onValue = { v -> vm.updateBuilder { it.copy(standingCharges = v) } }
        )
        DoubleField(
            label = "Sign-up bonus (€)",
            value = builder.signUpBonus,
            onValue = { v -> vm.updateBuilder { it.copy(signUpBonus = v) } }
        )
    }
}

// ── Day rates ───────────────────────────────────────────────────────────────
@Composable
private fun DayRatesSection(
    builder: PricePlanBuilder,
    expanded: Set<Long>,
    issues: PlanIssues,
    vm: UI2PricePlanViewModel,
    showHints: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showHints) {
            Text(
                "Each day rate covers a date window and a set of weekdays. Add costs " +
                    "(c/kWh) and assign time ranges to each — together they must cover " +
                    "the full 24-hour day. Across all day rates, the dates must tile " +
                    "the calendar year and every weekday must be covered.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (issues.dateRangeOverlap) {
            InlineIssue("Date ranges overlap. Trim one of the start/end dates so each calendar day belongs to exactly one day-rate.")
        }
        if (issues.datesMissing.isNotEmpty()) {
            val rendered = issues.datesMissing.joinToString(", ") { renderDoyRange(it) }
            InlineIssue("Missing date coverage: $rendered. Add or extend a day rate to cover these days.")
        }
        builder.dayRates.forEachIndexed { idx, dr ->
            DayRatePanel(
                index = idx,
                dr = dr,
                issues = issues.perDayRate[dr.id] ?: DayRateIssues(),
                missingWeekdays = issues.weekdaysMissing[dr.id].orEmpty(),
                isOpen = dr.id in expanded,
                onToggle = { vm.toggleDayRate(dr.id) },
                onChange = { fn -> vm.updateDayRate(dr.id, fn) },
                onRemove = if (builder.dayRates.size > 1) ({ vm.removeDayRate(dr.id) }) else null
            )
        }
        OutlinedButton(onClick = { vm.addDayRate() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add day rate")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayRatePanel(
    index: Int,
    dr: DayRateBuilder,
    issues: DayRateIssues,
    missingWeekdays: Set<Int>,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onChange: ((DayRateBuilder) -> DayRateBuilder) -> Unit,
    onRemove: (() -> Unit)?
) {
    fun edit(transform: DayRateBuilder.() -> DayRateBuilder) { onChange { it.transform() } }

    val borderColor = if (issues.isClean && missingWeekdays.isEmpty())
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Day rate ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        "${dr.startDate} → ${dr.endDate} · ${dr.daysOfWeek.size}/7 days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!issues.isClean || missingWeekdays.isNotEmpty()) {
                    Icon(Icons.Default.Warning, contentDescription = "Has issues",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                    }
                }
                Icon(
                    imageVector = if (isOpen) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = isOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Date range — MM/DD start + end
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dr.startDate,
                            onValueChange = { v -> edit { copy(startDate = v) } },
                            label = { Text("Start (MM/DD)") },
                            isError = issues.dateRangeInvalid != null,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dr.endDate,
                            onValueChange = { v -> edit { copy(endDate = v) } },
                            label = { Text("End (MM/DD)") },
                            isError = issues.dateRangeInvalid != null,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    issues.dateRangeInvalid?.let { InlineIssue(it) }

                    // Day-of-week chips
                    Text("DAYS", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DAY_LABELS.forEach { (code, name) ->
                            FilterChip(
                                selected = code in dr.daysOfWeek,
                                onClick = {
                                    edit {
                                        val set = daysOfWeek.toMutableSet()
                                        if (code in set) set.remove(code) else set.add(code)
                                        copy(daysOfWeek = set)
                                    }
                                },
                                label = { Text(name) }
                            )
                        }
                    }
                    if (missingWeekdays.isNotEmpty()) {
                        val names = DAY_LABELS.filter { it.first in missingWeekdays }
                            .joinToString(", ") { it.second }
                        InlineIssue("These weekdays aren't covered by any day rate sharing this date window: $names")
                    }

                    // Coverage strip (24h)
                    CoverageStrip(dr.bands)
                    CoverageSummary(issues)

                    // Rates — grouped by price
                    Text("RATES (c/kWh)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val groups = remember(dr.bands) {
                        // Group by price; preserve first-seen ordering so reordering doesn't
                        // make the cards jump when the user is editing.
                        val seen = mutableMapOf<Double, MutableList<RateBand>>()
                        for (band in dr.bands) {
                            seen.getOrPut(band.price) { mutableListOf() }.add(band)
                        }
                        seen.map { (price, list) -> price to list.sortedBy { it.beginMinute } }
                    }
                    groups.forEach { (price, bands) ->
                        PriceCard(
                            price = price, bands = bands,
                            onPriceChange = { newPrice ->
                                edit {
                                    copy(bands = this.bands.map { b ->
                                        if (b.price == price) b.copy(price = newPrice) else b
                                    })
                                }
                            },
                            onRangeAdd = { begin, end ->
                                edit { copy(bands = this.bands + RateBand(begin, end, price)) }
                            },
                            onRangeUpdate = { old, begin, end ->
                                edit {
                                    copy(bands = this.bands.map { b ->
                                        if (b === old || (b.beginMinute == old.beginMinute &&
                                                b.endMinute == old.endMinute &&
                                                b.price == old.price))
                                            b.copy(beginMinute = begin, endMinute = end)
                                        else b
                                    })
                                }
                            },
                            onRangeRemove = { band ->
                                edit { copy(bands = this.bands.filterNot { it === band ||
                                    (it.beginMinute == band.beginMinute &&
                                        it.endMinute == band.endMinute &&
                                        it.price == band.price) }) }
                            },
                            onPriceRemove = if (groups.size > 1) ({
                                edit { copy(bands = this.bands.filterNot { it.price == price }) }
                            }) else null
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            // Suggest the next "round" price one cent above the highest
                            // currently used — easier than typing into an empty field.
                            val nextPrice = ((dr.bands.maxOfOrNull { it.price } ?: 9.0) + 1.0)
                            // New price needs a range — use the first 30-minute gap, or the
                            // last hour if everything is covered.
                            val (begin, end) = firstFreeSlot(dr.bands) ?: (0 to 60)
                            edit { copy(bands = bands + RateBand(begin, end, nextPrice)) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add price")
                    }
                }
            }
        }
    }
}

/** One price card — heading is the editable c/kWh value, body lists time ranges. */
@Composable
private fun PriceCard(
    price: Double,
    bands: List<RateBand>,
    onPriceChange: (Double) -> Unit,
    onRangeAdd: (Int, Int) -> Unit,
    onRangeUpdate: (RateBand, Int, Int) -> Unit,
    onRangeRemove: (RateBand) -> Unit,
    onPriceRemove: (() -> Unit)?
) {
    var pickRange by remember { mutableStateOf<RangeEditTarget?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DoubleField(
                    label = "Cost (c/kWh)",
                    value = price,
                    onValue = onPriceChange,
                    modifier = Modifier.weight(1f)
                )
                if (onPriceRemove != null) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onPriceRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove cost",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
            bands.forEach { band ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${minuteLabel(band.beginMinute)} → ${minuteLabel(band.endMinute)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatDuration(band.durationMinutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { pickRange = RangeEditTarget.Edit(band) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit range",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { onRangeRemove(band) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove range",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            TextButton(onClick = { pickRange = RangeEditTarget.Add }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add time range")
            }
        }
    }

    pickRange?.let { target ->
        TimeRangeDialog(
            initialBegin = (target as? RangeEditTarget.Edit)?.band?.beginMinute ?: 0,
            initialEnd = (target as? RangeEditTarget.Edit)?.band?.endMinute ?: 60,
            onDismiss = { pickRange = null },
            onConfirm = { begin, end ->
                when (target) {
                    is RangeEditTarget.Add -> onRangeAdd(begin, end)
                    is RangeEditTarget.Edit -> onRangeUpdate(target.band, begin, end)
                }
                pickRange = null
            }
        )
    }
}

private sealed interface RangeEditTarget {
    object Add : RangeEditTarget
    data class Edit(val band: RateBand) : RangeEditTarget
}

/**
 * A two-step Material 3 time picker dialog: pick start, then end. Both stages
 * are minute-precise (15-minute snap by default on the M3 picker; users can
 * type a value to override).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRangeDialog(
    initialBegin: Int,
    initialEnd: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var stage by remember { mutableIntStateOf(0) }  // 0 = pick begin, 1 = pick end
    var beginMin by remember { mutableIntStateOf(initialBegin) }
    var endMin by remember { mutableIntStateOf(initialEnd) }

    val pickerState = rememberTimePickerState(
        initialHour = if (stage == 0) beginMin / 60 else endMin / 60,
        initialMinute = if (stage == 0) beginMin % 60 else endMin % 60,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (stage == 0) "Select start time" else "Select end time") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val picked = pickerState.hour * 60 + pickerState.minute
                if (stage == 0) {
                    beginMin = picked
                    stage = 1
                } else {
                    endMin = if (picked == 0) 1440 else picked
                    val begin = beginMin.coerceIn(0, 1440)
                    val end = endMin.coerceIn(0, 1440).coerceAtLeast(begin + 1)
                    onConfirm(begin, end)
                }
            }) { Text(if (stage == 0) "Next" else "Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── coverage strip + summary ────────────────────────────────────────────────
@Composable
private fun CoverageStrip(bands: List<RateBand>) {
    val covered = MaterialTheme.colorScheme.primary
    val gap = MaterialTheme.colorScheme.error
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(18.dp)) {
        drawRect(empty)
        if (bands.isEmpty()) return@Canvas
        val w = size.width
        // 1) Paint covered areas — overlaps will sum but visually it's fine.
        bands.forEach { b ->
            val x0 = w * b.beginMinute / 1440f
            val x1 = w * b.endMinute / 1440f
            drawRect(
                color = covered,
                topLeft = Offset(x0, 0f),
                size = Size((x1 - x0).coerceAtLeast(1f), size.height)
            )
        }
        // 2) Overlay overlapping segments in red so the user sees the clash.
        val sorted = bands.sortedBy { it.beginMinute }
        var cursor = 0
        sorted.forEach { b ->
            if (b.beginMinute < cursor) {
                val x0 = w * b.beginMinute / 1440f
                val x1 = w * minOf(cursor, b.endMinute) / 1440f
                drawRect(color = gap, topLeft = Offset(x0, 0f),
                    size = Size((x1 - x0).coerceAtLeast(1f), size.height))
            }
            cursor = maxOf(cursor, b.endMinute)
        }
        // 3) Border so the strip reads as a single element.
        drawRect(color = covered.copy(alpha = 0.4f),
            topLeft = Offset(0f, 0f), size = size,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
    }
}

@Composable
private fun CoverageSummary(issues: DayRateIssues) {
    if (issues.gaps.isEmpty() && issues.overlaps.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("All 24 hours covered", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val gapText = issues.gaps.joinToString(", ") { renderMinuteRange(it) }
    val overlapText = issues.overlaps.joinToString(", ") { renderMinuteRange(it) }
    Column {
        if (issues.gaps.isNotEmpty()) InlineIssue("Gap: $gapText")
        if (issues.overlaps.isNotEmpty()) InlineIssue("Overlap: $overlapText")
    }
}

// ── inline issue + small helpers ────────────────────────────────────────────
@Composable
private fun InlineIssue(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Warning, null, modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error)
            Text(text, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun DoubleField(
    label: String,
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(formatRate(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            v.toDoubleOrNull()?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(sub, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun formatRate(v: Double): String =
    if (v == v.toInt().toDouble()) v.toInt().toString() else "%.2f".format(v)

private fun minuteLabel(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60; val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private fun renderMinuteRange(r: IntRange): String =
    "${minuteLabel(r.first)}–${minuteLabel(r.last)}"

/** "M/D" rendering of an inclusive day-of-year range, for the missing-dates banner. */
private fun renderDoyRange(r: IntRange): String {
    val from = java.time.LocalDate.ofYearDay(2001, r.first)
    val to = java.time.LocalDate.ofYearDay(2001, r.last)
    return if (r.first == r.last) "${from.monthValue}/${from.dayOfMonth}"
    else "${from.monthValue}/${from.dayOfMonth}–${to.monthValue}/${to.dayOfMonth}"
}

/**
 * Find the first uncovered 1-hour gap in the existing bands so newly-added
 * prices land somewhere sensible. Returns `null` when the day is already full.
 */
private fun firstFreeSlot(bands: List<RateBand>): Pair<Int, Int>? {
    val sorted = bands.sortedBy { it.beginMinute }
    var cursor = 0
    for (b in sorted) {
        if (b.beginMinute > cursor) return cursor to minOf(cursor + 60, b.beginMinute)
        cursor = maxOf(cursor, b.endMinute)
    }
    if (cursor < 1440) return cursor to minOf(cursor + 60, 1440)
    return null
}

