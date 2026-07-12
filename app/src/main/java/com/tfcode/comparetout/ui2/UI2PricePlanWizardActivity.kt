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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.region.RegionProfiles
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

// Day codes in display order (Mon-first); names come from the shared
// R.array.ui2_days_short_mon_first, resolved where they're rendered.
private val DAY_CODES = listOf(1, 2, 3, 4, 5, 6, 0)

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
            stringResource(R.string.ui2_ppw_title_edit_named, builder.supplier, builder.planName)
        viewModel.isEditMode -> stringResource(R.string.ui2_ppw_title_edit)
        else -> stringResource(R.string.ui2_ppw_title_new)
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
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .widthIn(max = AdaptiveLayout.CONTENT_MAX_WIDTH)
                            .align(Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 104.dp)
                    ) {
                        item("details") {
                            AccordionSection(
                                title = stringResource(R.string.ui2_ppw_details_title),
                                subtitle = if (issues.supplierBlank || issues.planNameBlank)
                                    stringResource(R.string.ui2_ppw_details_required)
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
                                title = stringResource(R.string.ui2_ppw_charges_title),
                                subtitle = stringResource(R.string.ui2_ppw_charges_subtitle,
                                    "%.2f".format(builder.feed),
                                    RegionProfiles.current.minorSymbol,
                                    RegionProfiles.current.currencySymbol,
                                    "%.2f".format(builder.standingCharges)),
                                isComplete = true,
                                hasError = false,
                                isExpanded = expanded.contains("charges"),
                                onToggle = { viewModel.toggleSection("charges") }
                            ) {
                                ChargesSection(builder, viewModel)
                            }
                        }
                        if (builder.isDynamic) {
                            // Dynamic plans: the 365 generated day-rates are not
                            // hand-editable (and would be unusable as cards) — the
                            // terms card is the only rate-mutation path. Usage
                            // restrictions key on rate values, which a dynamic plan
                            // has hundreds of, so that section is gated too.
                            item("dynamic") {
                                val terms = builder.dynamicTerms
                                AccordionSection(
                                    title = stringResource(R.string.ui2_ppw_dynamic_title),
                                    subtitle = if (builder.materialised)
                                        stringResource(R.string.ui2_ppw_dynamic_materialised,
                                            terms?.market ?: "",
                                            windowLabel(terms?.year, terms?.periodStartMonth))
                                    else stringResource(R.string.ui2_ppw_dynamic_pending),
                                    isComplete = builder.materialised,
                                    hasError = false,
                                    isExpanded = expanded.contains("dynamic"),
                                    onToggle = { viewModel.toggleSection("dynamic") }
                                ) {
                                    DynamicTermsSection(builder, viewModel, showHints)
                                }
                            }
                        } else {
                        // Regions with a wholesale market registered can turn a
                        // plan dynamic from here (mirrors the generator pane on
                        // the plan list) — the terms card then replaces the
                        // Rates/Restrictions sections below.
                        if (RegionProfiles.current.dynamicMarkets.isNotEmpty()) {
                            item("make-dynamic") {
                                AccordionSection(
                                    title = stringResource(R.string.ui2_ppw_make_dynamic_title),
                                    subtitle = stringResource(R.string.ui2_ppw_make_dynamic_off),
                                    isComplete = true,
                                    hasError = false,
                                    isExpanded = expanded.contains("make-dynamic"),
                                    onToggle = { viewModel.toggleSection("make-dynamic") }
                                ) {
                                    MakeDynamicSection(viewModel, showHints)
                                }
                            }
                        }
                        item("rates") {
                            val rateSummary = dayRatesSubtitle(builder, issues)
                            AccordionSection(
                                title = stringResource(R.string.ui2_ppw_rates_title),
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
                        item("restrictions") {
                            val count = builder.restrictionEntries.size
                            AccordionSection(
                                title = stringResource(R.string.ui2_ppw_restrictions_title),
                                subtitle = when {
                                    count == 0 -> stringResource(R.string.ui2_ppw_restrictions_none)
                                    builder.restrictionsActive ->
                                        pluralStringResource(R.plurals.ui2_ppw_caps_active, count, count)
                                    else ->
                                        pluralStringResource(R.plurals.ui2_ppw_caps_disabled, count, count)
                                },
                                isComplete = true,
                                hasError = false,
                                isExpanded = expanded.contains("restrictions"),
                                onToggle = { viewModel.toggleSection("restrictions") }
                            ) {
                                RestrictionsSection(builder, viewModel, showHints)
                            }
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
            title = { Text(stringResource(R.string.ui2_ppw_discard_title)) },
            text = {
                Text(
                    stringResource(R.string.ui2_ppw_discard_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onClose() }) {
                    Text(stringResource(R.string.ui2_ppw_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(R.string.ui2_director_keep_editing))
                }
            }
        )
    }

    if (showImportSheet) {
        val context = LocalContext.current
        UI2ImportSheet(
            title = stringResource(R.string.ui2_ppw_import_title),
            hint = stringResource(R.string.ui2_ppw_import_hint),
            applyLabel = stringResource(R.string.ui2_ppw_import_apply),
            parse = { parsePricePlanImportJson(context, it) },
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
            title = { Text(stringResource(R.string.ui2_ppw_multi_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.ui2_ppw_multi_body, list.size),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val unnamed = stringResource(R.string.ui2_ppw_unnamed)
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
                            val plan = file.plan.orEmpty().ifBlank { unnamed }
                            Text(
                                "${idx + 1}. $supplier · $plan",
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

    confirmImportOverwrite?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmImportOverwrite = null },
            title = { Text(stringResource(R.string.ui2_ppw_replace_title)) },
            text = {
                Text(
                    stringResource(R.string.ui2_ppw_replace_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.loadFromJson(file)
                    confirmImportOverwrite = null
                }) { Text(stringResource(R.string.ui2_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImportOverwrite = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
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
                Text(stringResource(R.string.ui2_ppw_import_row_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.ui2_ppw_import_row_sub),
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
private fun parsePricePlanImportJson(context: android.content.Context, text: String): ParsedPreview<List<PricePlanJsonFile>> = try {
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
    if (valid.isEmpty()) ParsedPreview.Err(context.getString(R.string.ui2_parse_no_plans))
    else ParsedPreview.Ok(
        valid,
        when (valid.size) {
            1 -> context.getString(R.string.ui2_ppw_parse_one_plan,
                valid.first().supplier ?: "?", valid.first().plan)
            else -> context.getString(R.string.ui2_ppw_parse_n_plans, valid.size)
        }
    )
} catch (e: JsonSyntaxException) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_malformed))
} catch (e: Throwable) {
    ParsedPreview.Err(e.message ?: context.getString(R.string.ui2_parse_failed))
}

@Composable
private fun dayRatesSubtitle(b: PricePlanBuilder, i: PlanIssues): String {
    val count = b.dayRates.size
    val countStr = pluralStringResource(R.plurals.ui2_ppl_day_rates, count, count)
    val detail = when {
        i.dateRangeOverlap -> stringResource(R.string.ui2_ppw_sub_overlap)
        i.datesMissing.isNotEmpty() ->
            pluralStringResource(R.plurals.ui2_ppw_missing_ranges,
                i.datesMissing.size, i.datesMissing.size)
        i.weekdaysMissing.values.any { it.isNotEmpty() } ->
            stringResource(R.string.ui2_ppw_sub_missing_weekdays)
        i.perDayRate.values.any { !it.isClean } -> stringResource(R.string.ui2_ppw_sub_time_gaps)
        else -> stringResource(R.string.ui2_ppw_sub_all_covered)
    }
    return "$countStr · $detail"
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
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.ui2_ppw_saved))
                                }
                                saving -> Text(stringResource(R.string.ui2_ppw_saving))
                                else -> Text(stringResource(R.string.ui2_save))
                            }
                        }
                        PpwFooterAction.RUN -> Button(
                            onClick = onRun,
                            enabled = canRun && !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.ui2_ppw_run_calc)) }
                        PpwFooterAction.CLOSE -> OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.ui2_close)) }
                    }
                }
            }
            if (saveResult == PricePlanSaveResult.Failed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.ui2_ppw_save_failed),
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
                hasError -> Icon(Icons.Default.Warning,
                    contentDescription = stringResource(R.string.ui2_ppw_has_issues_cd),
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                isComplete -> Icon(Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.ui2_ppw_complete_cd),
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
            label = { Text(stringResource(R.string.ui2_ppw_supplier)) },
            isError = issues.supplierBlank,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = builder.planName,
            onValueChange = { v -> vm.updateBuilder { it.copy(planName = v) } },
            label = { Text(stringResource(R.string.ui2_ppw_plan_name)) },
            isError = issues.planNameBlank,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = if (builder.reference == "<REFERENCE>") "" else builder.reference,
            onValueChange = { v -> vm.updateBuilder { it.copy(reference = v.ifBlank { "<REFERENCE>" }) } },
            label = { Text(stringResource(R.string.ui2_ppw_reference)) },
            placeholder = { Text(stringResource(R.string.ui2_ppw_reference_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = builder.location,
            onValueChange = { v -> vm.updateBuilder { it.copy(location = v.take(2)) } },
            label = { Text(stringResource(R.string.ui2_ppw_location)) },
            placeholder = { Text(stringResource(R.string.ui2_ppw_location_placeholder)) },
            supportingText = {
                Text(stringResource(R.string.ui2_ppw_location_support))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // Deemed export is an IE-only concept — other editions never show the
        // switch (the underlying field stays false, its JSON default).
        if (RegionProfiles.current.hasDeemedExport) {
            SwitchRow(
                title = stringResource(R.string.deemed_export_calculation),
                sub = stringResource(R.string.ui2_ppw_deemed_sub),
                checked = builder.deemedExport
            ) { v -> vm.updateBuilder { it.copy(deemedExport = v) } }
        }
        if (builder.lastUpdate.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.ui2_ppl_last_updated, builder.lastUpdate),
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
            label = stringResource(R.string.ui2_ppw_feed_label, RegionProfiles.current.rateUnit),
            value = builder.feed,
            onValue = { v -> vm.updateBuilder { it.copy(feed = v) } }
        )
        DoubleField(
            label = stringResource(R.string.ui2_ppw_standing_label,
                RegionProfiles.current.currencySymbol),
            value = builder.standingCharges,
            onValue = { v -> vm.updateBuilder { it.copy(standingCharges = v) } }
        )
        DoubleField(
            label = stringResource(R.string.ui2_ppw_bonus_label,
                RegionProfiles.current.currencySymbol),
            value = builder.signUpBonus,
            onValue = { v -> vm.updateBuilder { it.copy(signUpBonus = v) } }
        )
    }
}

// ── Restrictions ────────────────────────────────────────────────────────────
//
// Tiered usage caps, applied by RateLookup during costing: once the capped kWh
// has been bought at a rate within the period, further usage at that rate is
// charged the revised price (e.g. Octopus Zero: 4000 kWh/year fair use, then a
// different unit rate). NB restrictions attach to a rate VALUE, not a band —
// two bands with the same price share the cap, and changing a band's price
// detaches its restriction.
@Composable
private fun RestrictionsSection(
    builder: PricePlanBuilder,
    vm: UI2PricePlanViewModel,
    showHints: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showHints) {
            Text(
                stringResource(R.string.ui2_ppw_restr_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (builder.restrictionEntries.isNotEmpty()) {
            SwitchRow(
                title = stringResource(R.string.ui2_ppw_apply_restrictions),
                sub = stringResource(R.string.ui2_ppw_apply_restrictions_sub),
                checked = builder.restrictionsActive
            ) { v -> vm.setRestrictionsActive(v) }
        }
        builder.restrictionEntries.forEach { entry ->
            RestrictionEntryCard(entry, builder.uniqueRates, vm)
        }
        OutlinedButton(onClick = { vm.addRestriction() }) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.ui2_ppw_add_restriction))
        }
    }
}

@Composable
private fun RestrictionEntryCard(
    entry: RestrictionEntryBuilder,
    uniqueRates: List<Double>,
    vm: UI2PricePlanViewModel
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rate the cap attaches to — offered from the plan's current bands.
                var rateMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { rateMenu = true }) {
                        Text(if (entry.rate.isBlank()) stringResource(R.string.ui2_ppw_rate_placeholder)
                             else "${entry.rate} ${RegionProfiles.current.rateUnit}")
                    }
                    DropdownMenu(expanded = rateMenu, onDismissRequest = { rateMenu = false }) {
                        uniqueRates.forEach { r ->
                            val label = formatRateValue(r)
                            DropdownMenuItem(
                                text = { Text("$label ${RegionProfiles.current.rateUnit}") },
                                onClick = {
                                    rateMenu = false
                                    vm.updateRestriction(entry.id) { it.copy(rate = label) }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                var periodMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { periodMenu = true }) {
                        Text(entry.period)
                    }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        listOf("Annual", "Monthly", "Bimonthly").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    periodMenu = false
                                    vm.updateRestriction(entry.id) { it.copy(period = p) }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.removeRestriction(entry.id) }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.ui2_ppw_remove_restriction_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var limitText by remember(entry.id) { mutableStateOf(
                    if (entry.kwhLimit > 0) entry.kwhLimit.toString() else "") }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { v ->
                        limitText = v
                        v.toIntOrNull()?.let { n ->
                            vm.updateRestriction(entry.id) { it.copy(kwhLimit = n) }
                        }
                    },
                    label = { Text(stringResource(R.string.ui2_ppw_cap_label)) },
                    isError = limitText.toIntOrNull()?.takeIf { it > 0 } == null,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                var priceText by remember(entry.id) { mutableStateOf(
                    if (entry.revisedPrice != 0.0) formatRateValue(entry.revisedPrice) else "") }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { v ->
                        priceText = v
                        v.toDoubleOrNull()?.let { p ->
                            vm.updateRestriction(entry.id) { it.copy(revisedPrice = p) }
                        }
                    },
                    label = { Text(stringResource(R.string.ui2_ppw_then_label,
                        RegionProfiles.current.rateUnit)) },
                    isError = priceText.toDoubleOrNull() == null,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
                stringResource(R.string.ui2_ppw_rates_hint, RegionProfiles.current.rateUnit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (issues.dateRangeOverlap) {
            InlineIssue(stringResource(R.string.ui2_ppw_overlap_issue))
        }
        if (issues.datesMissing.isNotEmpty()) {
            val rendered = issues.datesMissing.joinToString(", ") { renderDoyRange(it) }
            InlineIssue(stringResource(R.string.ui2_ppw_missing_dates_issue, rendered))
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
            Text(stringResource(R.string.ui2_ppw_add_day_rate))
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
                    Text(stringResource(R.string.ui2_ppw_day_rate_n, index + 1),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.ui2_ppw_day_rate_sub,
                            dr.startDate, dr.endDate, dr.daysOfWeek.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!issues.isClean || missingWeekdays.isNotEmpty()) {
                    Icon(Icons.Default.Warning,
                        contentDescription = stringResource(R.string.ui2_ppw_has_issues_cd),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
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
                            label = { Text(stringResource(R.string.ui2_ppw_start_mmdd)) },
                            isError = issues.dateRangeInvalid != null,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dr.endDate,
                            onValueChange = { v -> edit { copy(endDate = v) } },
                            label = { Text(stringResource(R.string.ui2_ppw_end_mmdd)) },
                            isError = issues.dateRangeInvalid != null,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    issues.dateRangeInvalid?.let { InlineIssue(stringResource(it)) }

                    // Day-of-week chips
                    Text(stringResource(R.string.ui2_ppw_days_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val dayNames = stringArrayResource(R.array.ui2_days_short_mon_first)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DAY_CODES.forEachIndexed { i, code ->
                            FilterChip(
                                selected = code in dr.daysOfWeek,
                                onClick = {
                                    edit {
                                        val set = daysOfWeek.toMutableSet()
                                        if (code in set) set.remove(code) else set.add(code)
                                        copy(daysOfWeek = set)
                                    }
                                },
                                label = { Text(dayNames[i]) }
                            )
                        }
                    }
                    if (missingWeekdays.isNotEmpty()) {
                        val names = DAY_CODES.withIndex()
                            .filter { it.value in missingWeekdays }
                            .joinToString(", ") { dayNames[it.index] }
                        InlineIssue(stringResource(R.string.ui2_ppw_weekdays_issue, names))
                    }

                    // Coverage strip (24h)
                    CoverageStrip(dr.bands)
                    CoverageSummary(issues)

                    // Rates — grouped by price
                    Text(stringResource(R.string.ui2_ppw_rates_header,
                            RegionProfiles.current.rateUnit),
                        style = MaterialTheme.typography.labelSmall,
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
                        Text(stringResource(R.string.ui2_ppw_add_price))
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
                    label = stringResource(R.string.ui2_ppw_cost_label,
                        RegionProfiles.current.rateUnit),
                    value = price,
                    onValue = onPriceChange,
                    modifier = Modifier.weight(1f)
                )
                if (onPriceRemove != null) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onPriceRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_ppw_remove_cost_cd),
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
                        Icon(Icons.Default.Edit,
                            contentDescription = stringResource(R.string.ui2_ppw_edit_range_cd),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { onRangeRemove(band) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_ppw_remove_range_cd),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            TextButton(onClick = { pickRange = RangeEditTarget.Add }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui2_ppw_add_time_range))
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
        title = { Text(stringResource(
            if (stage == 0) R.string.ui2_ppw_select_start else R.string.ui2_ppw_select_end)) },
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
            }) { Text(stringResource(
                if (stage == 0) R.string.ui2_next else R.string.ui2_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
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
            Text(stringResource(R.string.ui2_ppw_all_covered),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val gapText = issues.gaps.joinToString(", ") { renderMinuteRange(it) }
    val overlapText = issues.overlaps.joinToString(", ") { renderMinuteRange(it) }
    Column {
        if (issues.gaps.isNotEmpty()) InlineIssue(stringResource(R.string.ui2_ppw_gap, gapText))
        if (issues.overlaps.isNotEmpty()) InlineIssue(stringResource(R.string.ui2_ppw_overlap, overlapText))
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


// ── Dynamic terms (wholesale-tracking plans) ────────────────────────────────
//
// The terms card is the dynamic plan's only rate-mutation path: edit the
// supplier terms / backtest year and regenerate — DynamicTariffWorker fetches
// the wholesale year and replaces the generated rates. The scalar plan fields
// (name, standing charges, feed) stay editable through the normal sections.

@Composable
private fun DynamicTermsSection(
    builder: PricePlanBuilder,
    viewModel: UI2PricePlanViewModel,
    showHints: Boolean
) {
    val terms = builder.dynamicTerms ?: return
    var auto by remember(builder.pricePlanId) { mutableStateOf(terms.isAutoWindow) }
    var windowStart by remember(builder.pricePlanId) {
        mutableStateOf(windowStartOf(terms.year, terms.periodStartMonth))
    }
    var multiplier by remember(builder.pricePlanId) {
        mutableStateOf((terms.multiplier ?: 1.0).toString())
    }
    var adder by remember(builder.pricePlanId) {
        mutableStateOf((terms.adder ?: 0.0).toString())
    }
    var cap by remember(builder.pricePlanId) {
        mutableStateOf(terms.cap?.toString() ?: "")
    }

    fun parsed(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    val ready = parsed(multiplier) != null && parsed(adder) != null &&
            builder.supplier.isNotBlank() && builder.planName.isNotBlank()

    // Reflect the initially-shown field values into the builder so a plain Save
    // (not just Regenerate) persists the displayed window/multiplier/adder/cap even
    // if the user changes nothing else. For an already-loaded plan this seeds the
    // identical values, so it is not treated as a terms change.
    LaunchedEffect(builder.pricePlanId) {
        viewModel.updateTerms { t ->
            t.autoWindow = auto
            t.year = windowStart.year
            t.periodStartMonth = windowStart.monthValue
            parsed(multiplier)?.let { t.multiplier = it }
            parsed(adder)?.let { t.adder = it }
            t.cap = parsed(cap)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.ui2_ppw_dynamic_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwitchRow(
            title = stringResource(R.string.ui2_dyn_auto_title),
            sub = stringResource(R.string.ui2_dyn_auto_sub),
            checked = auto
        ) { v -> auto = v; viewModel.updateTerms { t -> t.autoWindow = v } }
        if (auto) {
            Text(
                stringResource(R.string.ui2_dyn_auto_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            HistoricalWindowStepper(
                start = windowStart,
                onStartChange = { ym ->
                    windowStart = ym
                    viewModel.updateTerms { t ->
                        t.year = ym.year; t.periodStartMonth = ym.monthValue; t.periodStartDay = null
                    }
                }
            )
            if (showHints) {
                Text(
                    stringResource(R.string.ui2_dyn_window_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedTextField(
            value = multiplier,
            onValueChange = { multiplier = it
                parsed(it)?.let { v -> viewModel.updateTerms { t -> t.multiplier = v } } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_multiplier)) }
        )
        OutlinedTextField(
            value = adder,
            onValueChange = { adder = it
                parsed(it)?.let { v -> viewModel.updateTerms { t -> t.adder = v } } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_adder,
                RegionProfiles.current.rateUnit)) }
        )
        if (showHints) {
            Text(
                stringResource(R.string.ui2_dyn_adder_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = cap,
            onValueChange = { cap = it
                // Blank / unparseable → uncapped (null); persist immediately.
                viewModel.updateTerms { t -> t.cap = parsed(it) } },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.ui2_ppl_dyn_cap,
                RegionProfiles.current.rateUnit)) }
        )
        if (showHints) {
            Text(
                stringResource(R.string.ui2_dyn_cap_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Pending (terms but no generated prices yet, incl. a fresh conversion):
        // the button CREATES; materialised: it REPLACES the generated rates.
        // Driven by the builder's materialised flag (NOT terms.year — the year is
        // now an editable field that lives in the builder).
        val isPending = !builder.materialised
        Button(
            // The fields already write the terms into the builder, so a plain Save
            // and this button do the same thing for a dynamic plan: persist the
            // terms, then (re)generate prices. Route through save() so terms are
            // persisted first (kept even if the fetch fails) instead of relying on
            // the worker's success-only clobber.
            onClick = { viewModel.save(runCosting = true) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(
                if (isPending) R.string.ui2_ppw_dynamic_generate
                else R.string.ui2_ppw_dynamic_regenerate))
        }
        if (showHints) {
            Text(
                stringResource(
                    if (isPending) R.string.ui2_ppw_dynamic_generate_hint
                    else R.string.ui2_ppw_dynamic_regenerate_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isPending) {
            TextButton(
                onClick = { viewModel.clearDynamic() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ui2_ppw_dynamic_revert)) }
        }
    }
}

// Offered on non-dynamic plans (when the region has a wholesale market):
// converting swaps the Rates/Restrictions sections for the Dynamic terms card,
// where the actual fetch is triggered. Reversible until prices materialise.
@Composable
private fun MakeDynamicSection(
    viewModel: UI2PricePlanViewModel,
    showHints: Boolean
) {
    val market = RegionProfiles.current.dynamicMarkets.first()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.ui2_ppw_make_dynamic_desc, market.displayName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showHints) {
            Text(
                stringResource(R.string.ui2_ppw_make_dynamic_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = { viewModel.makeDynamic(market.id) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.ui2_ppw_make_dynamic_button)) }
    }
}
