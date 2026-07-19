package com.tfcode.comparetout.ui2

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import com.tfcode.comparetout.R
import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfiles
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.StackedBarChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * Compare configuration accordions (what/sources/filter/timeframe/display + select sheet) — extracted verbatim from CompareScreen.kt (mega-refactor B7).
 * Imports inherited; unused are cosmetic.
 */

private fun seriesColor(id: String, primary: Color, isEnergy: Boolean): Color =
    compareSeriesColor(id, primary, isEnergy)

// ──────────────────────────────────────────────────────────────────────────
// Accordion card
// ──────────────────────────────────────────────────────────────────────────
@Composable
internal fun AccordionCard(
    title: String,
    subtitle: String,
    done: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().then(
            if (open) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
            else Modifier
        )
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (done && !open) {
                    Icon(Icons.Default.Check,
                        contentDescription = stringResource(R.string.ui2_cmp_set_cd),
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (open) R.string.ui2_collapse else R.string.ui2_expand)
                )
            }
            if (open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
            }
        }
    }
}

@Composable
internal fun SmallCaps(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

/** Italic explanatory caption — shown only in novice mode. */
@Composable
internal fun HelpText(text: String, novice: Boolean) {
    if (novice) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
    }
}

// ──────────────────────────────────────────────────────────────────────────
// What
// ──────────────────────────────────────────────────────────────────────────
@Composable
internal fun WhatSection(state: CompareState, vm: UI2CompareViewModel, novice: Boolean) {
    val opts = listOf(
        Triple(CompareWhat.COST, stringResource(R.string.ui2_cmp_cost),
            stringResource(R.string.ui2_cmp_what_cost_sub, RegionProfiles.current.currencySymbol)),
        Triple(CompareWhat.USAGE, stringResource(R.string.ui2_cmp_usage),
            stringResource(R.string.ui2_cmp_what_usage_sub)),
        Triple(CompareWhat.BOTH, stringResource(R.string.ui2_cmp_combined),
            stringResource(R.string.ui2_cmp_what_both_sub))
    )
    SmallCaps(stringResource(R.string.ui2_cmp_comparison_type))
    opts.forEach { (id, label, sub) ->
        val active = state.what == id
        Surface(
            color = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .clickable { vm.update { it.copy(what = id) } }
        ) {
            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = active, onClick = { vm.update { it.copy(what = id) } })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (novice) {
                        Text(sub, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Sources
// ──────────────────────────────────────────────────────────────────────────
@Composable
internal fun SourcesSection(
    state: CompareState,
    vm: UI2CompareViewModel,
    sourceCount: Int,
    simCount: Int,
    planCount: Int,
    novice: Boolean,
    onOpenSheet: (String) -> Unit
) {
    SelectRow(stringResource(R.string.ui2_data_sources), sourceCount) { onOpenSheet("sources") }
    // Profiles without scenarios compare real sources only — no sims picker.
    if (AppProfiles.current.hasScenarios) {
        SelectRow(stringResource(R.string.ui2_cmp_simulations), simCount) { onOpenSheet("sims") }
    }
    if (state.what != CompareWhat.USAGE) {
        SelectRow(stringResource(R.string.ui2_supplier_plans), planCount) { onOpenSheet("plans") }
    }
    Spacer(Modifier.height(8.dp))

    // Selected-subjects strip — primary path for duplicating a source/sim so
    // the user can compare it against itself at different timeframes. Lives
    // here (not just in the Timeframe accordion) because in sync mode the
    // per-subject pickers are hidden, but the user still needs to manage
    // duplicates.
    val sources = vm.sourceItems.collectAsState().value
    val sims    = vm.simItems.collectAsState().value
    val slots = remember(state.sources, state.sims, sources, sims) {
        val srcByKey = sources.associateBy { it.sysSn }
        val simByKey = sims.associateBy { it.scenarioId }
        buildSourceSlots(state.sources) { sn -> srcByKey[sn]?.sysSn ?: sn }
            .map { Triple(it.subjectId, it.displayName, it.occurrence > 0) } +
        buildSimSlots(state.sims) { id -> simByKey[id]?.name ?: "Sim #$id" }
            .map { Triple(it.subjectId, it.displayName, it.occurrence > 0) }
    }
    if (slots.isNotEmpty()) {
        Text(stringResource(R.string.ui2_cmp_selected_subjects),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // One subject card per row on a phone; two side-by-side at WIDE+ so
        // tablets / unfolded foldables stop showing a skinny single column.
        val subjectCell: @Composable (Triple<String, String, Boolean>) -> Unit = { (id, name, isDup) ->
            Surface(
                color = if (isDup) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                        else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { vm.duplicateSubject(id) }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_cmp_duplicate),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { vm.removeSubjectSlot(id) }) {
                        Icon(Icons.Default.Close,
                            contentDescription = stringResource(R.string.ui2_remove),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth >= AdaptiveLayout.WIDTH_WIDE_AT) 2 else 1
            Column(modifier = Modifier.fillMaxWidth()) {
                if (perRow == 1) {
                    slots.forEach { subjectCell(it) }
                } else {
                    slots.chunked(perRow).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { Box(Modifier.weight(1f)) { subjectCell(it) } }
                            repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (novice) {
            HelpText(stringResource(R.string.ui2_cmp_duplicate_help), true)
        }
        Spacer(Modifier.height(6.dp))
    }

    val subjects = sourceCount + simCount
    val planFactor = if (state.what == CompareWhat.USAGE) 1 else planCount
    val total = subjects * planFactor
    val sourcesTxt = pluralStringResource(R.plurals.ui2_cmp_n_sources, sourceCount, sourceCount)
    val simsTxt    = pluralStringResource(R.plurals.ui2_cmp_n_sims, simCount, simCount)
    val plansTxt   = if (state.what != CompareWhat.USAGE)
        pluralStringResource(R.plurals.ui2_cmp_n_plans, planCount, planCount) else null
    val resultsTxt = pluralStringResource(R.plurals.ui2_cmp_n_results, total, total)
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            buildString {
                append(sourcesTxt); append(" + "); append(simsTxt)
                plansTxt?.let { append(" × "); append(it) }
                append("  =  "); append(resultsTxt)
            },
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
    HelpText(stringResource(R.string.ui2_cmp_pickers_help), novice)
}

@Composable
internal fun SelectRow(title: String, count: Int, onTap: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onTap() }
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Surface(
                color = if (count > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else Color.Transparent,
                shape = CircleShape
            ) {
                Text(stringResource(R.string.ui2_cmp_count_selected, count),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (count > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Filter
// ──────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSection(state: CompareState, vm: UI2CompareViewModel, novice: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    // Energy series at least one selected subject can provide; others are greyed.
    val available by vm.availableEnergySeries.collectAsState()
    // Hoisted in the VM so the tab choice survives the accordion scrolling off.
    val advanced by vm.filterAdvanced.collectAsState()

    @Composable
    fun chips(defs: List<Pair<String, Int>>, prefix: String, isEnergy: Boolean) {
        // Default FilterChip uses secondaryContainer for the selected fill, which
        // is too close to surface in many themes — selected vs unselected was hard
        // to tell apart. Lift selected to primaryContainer + a 1.5dp primary
        // border, matching the active-state visual on BottomBarTile / DisplaySection.
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            defs.forEach { (rawId, labelRes) ->
                val id = prefix + rawId
                val on = state.series.contains(id)
                // Cost columns aren't subject-gated. An energy series is available
                // only if a selected subject records it. A greyed series stays
                // tappable while it's still selected, so it's never trapped on.
                val avail = !isEnergy || rawId in available
                val chipEnabled = avail || on
                FilterChip(
                    selected = on,
                    enabled = chipEnabled,
                    onClick = {
                        vm.update { it.copy(series = if (on) it.series - id else it.series + id) }
                    },
                    label = { Text(stringResource(labelRes)) },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).background(seriesColor(rawId, primary, isEnergy), CircleShape))
                    },
                    colors = chipColors,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = chipEnabled,
                        selected = on,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                        selectedBorderWidth = 1.5.dp
                    )
                )
            }
        }
    }

    PrimaryTabRow(selectedTabIndex = if (advanced) 1 else 0) {
        Tab(selected = !advanced, onClick = { vm.setFilterAdvanced(false) },
            text = { Text(stringResource(R.string.ui2_cmp_basic)) })
        Tab(selected = advanced, onClick = { vm.setFilterAdvanced(true) },
            text = { Text(stringResource(R.string.ui2_cmp_advanced)) })
    }
    HelpText(
        stringResource(R.string.ui2_cmp_filter_help_base) + " " +
            stringResource(if (advanced) R.string.ui2_cmp_filter_help_advanced
                           else R.string.ui2_cmp_filter_help_basic),
        novice
    )
    Spacer(Modifier.height(8.dp))

    // Advanced is additive: it keeps the basic series visible and reveals the
    // advanced ones alongside them (it doesn't replace the basic set).
    val energyDefs = if (advanced) UI2CompareViewModel.USAGE_SERIES
        else UI2CompareViewModel.USAGE_SERIES.filter { it.first in UI2CompareViewModel.BASIC_ENERGY_IDS }
    val costDefs = if (advanced) UI2CompareViewModel.COST_SERIES
        else UI2CompareViewModel.COST_SERIES.filter { it.first in UI2CompareViewModel.BASIC_COST_IDS }
    when (state.what) {
        CompareWhat.COST -> {
            SmallCaps(stringResource(R.string.ui2_cmp_cost_columns))
            chips(costDefs, "", isEnergy = false)
        }
        CompareWhat.USAGE -> {
            SmallCaps(stringResource(R.string.ui2_cmp_energy_flows))
            chips(energyDefs, "", isEnergy = true)
        }
        CompareWhat.BOTH -> {
            SmallCaps(stringResource(R.string.ui2_cmp_energy_flows))
            chips(energyDefs, "", isEnergy = true)
            SmallCaps(stringResource(R.string.ui2_cmp_cost_columns))
            chips(costDefs, "c_", isEnergy = false)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Timeframe — Basic/Advanced tab, sync toggle, per-subject ranges
// ──────────────────────────────────────────────────────────────────────────
@Composable
internal fun TimeframeSection(
    state: CompareState,
    vm: UI2CompareViewModel,
    novice: Boolean,
    selectedSubjects: List<Pair<String, String>>
) {
    PrimaryTabRow(selectedTabIndex = if (state.advanced) 1 else 0) {
        Tab(selected = !state.advanced, onClick = { vm.update { it.copy(advanced = false) } },
            text = { Text(stringResource(R.string.ui2_cmp_basic)) })
        Tab(selected = state.advanced, onClick = { vm.update { it.copy(advanced = true) } },
            text = { Text(stringResource(R.string.ui2_cmp_advanced)) })
    }
    HelpText(
        stringResource(if (state.advanced) R.string.ui2_cmp_time_help_advanced
                       else R.string.ui2_cmp_time_help_basic),
        novice
    )
    Spacer(Modifier.height(10.dp))

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui2_cmp_sync_ranges),
                    style = MaterialTheme.typography.bodyLarge)
                if (novice) {
                    Text(
                        stringResource(if (state.sync) R.string.ui2_cmp_sync_on_sub
                                       else R.string.ui2_cmp_sync_off_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = state.sync, onCheckedChange = { v -> vm.update { it.copy(sync = v) } })
        }
    }
    Spacer(Modifier.height(12.dp))

    if (state.sync) {
        RangePicker(stringResource(R.string.ui2_cmp_range),
            state.globalGran, state.globalAnchor, state.advanced, novice,
            onGran = { g -> vm.setGlobalGran(g) },
            onAnchor = { a -> vm.update { it.copy(globalAnchor = a) } })
    } else if (selectedSubjects.isEmpty()) {
        Text(stringResource(R.string.ui2_cmp_select_first),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        selectedSubjects.forEachIndexed { idx, (id, name) ->
            if (idx > 0) Spacer(Modifier.height(12.dp))
            val sr = state.perSubjectRanges[id] ?: SubjectRange(state.globalGran, state.globalAnchor)
            // Header row above each picker so the user can clone / remove
            // *this specific* slot. "Duplicate" seeds the new slot with the
            // current range so the user only has to change the timeframe to
            // make the comparison useful.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { vm.duplicateSubject(id) }) {
                    Icon(Icons.Default.Add, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui2_cmp_duplicate))
                }
                IconButton(onClick = { vm.removeSubjectSlot(id) }) {
                    Icon(Icons.Default.Close,
                        contentDescription = stringResource(R.string.ui2_remove),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp))
                }
            }
            RangePicker("", sr.gran, sr.anchor, state.advanced, novice,
                onGran = { g ->
                    vm.update {
                        it.copy(perSubjectRanges = it.perSubjectRanges + (id to SubjectRange(g, sr.anchor)))
                    }
                },
                onAnchor = { a ->
                    vm.update {
                        it.copy(perSubjectRanges = it.perSubjectRanges + (id to SubjectRange(sr.gran, a)))
                    }
                })
        }
    }
}

@Composable
internal fun RangePicker(
    label: String,
    gran: DataSourcePeriod?,
    anchor: LocalDate,
    advanced: Boolean,
    novice: Boolean,
    onGran: (DataSourcePeriod?) -> Unit,
    onAnchor: (LocalDate) -> Unit
) {
    SmallCaps(label)
    val dayWord = stringResource(R.string.ui2_cmp_day)
    val monthWord = stringResource(R.string.ui2_cmp_month)
    val yearWord = stringResource(R.string.ui2_cmp_year)
    val allTimeWord = stringResource(R.string.ui2_cmp_all_time)
    val longLabel: (DataSourcePeriod) -> String = {
        when (it) {
            DataSourcePeriod.YESTERDAY -> dayWord
            DataSourcePeriod.MONTH     -> monthWord
            DataSourcePeriod.YEAR      -> yearWord
            DataSourcePeriod.ALL       -> allTimeWord
        }
    }
    // labelFor runs outside composition — resolve the short labels here.
    val shortLabels = DataSourcePeriod.entries.associateWith { stringResource(it.labelRes) }
    AdaptivePeriodControl(
        segments = DataSourcePeriod.entries,
        selected = gran,
        labelFor = { shortLabels.getValue(it) },
        longLabelFor = longLabel,
        onSelect = { p -> onGran(if (gran == p) null else p) },
        dateSlot = {
            if (gran != null && gran != DataSourcePeriod.ALL) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onAnchor(stepAnchorBy(anchor, gran, -1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.ui2_cmp_earlier))
                    }
                    Text(rangeLabel(gran, anchor, advanced),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onAnchor(stepAnchorBy(anchor, gran, 1)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.ui2_cmp_later))
                    }
                }
            }
        }
    )
    val hint = when (gran) {
        null -> stringResource(R.string.ui2_cmp_pick_range_hint)
        DataSourcePeriod.ALL -> stringResource(R.string.ui2_cmp_all_time_hint)
        else -> {
            val (f, t) = periodDateRange(gran, anchor, advanced,
                anchor.minusYears(60), anchor.plusYears(60))
            "$f → $t"
        }
    }
    HelpText(hint, novice || gran == null)
}

internal fun stepAnchorBy(anchor: LocalDate, gran: DataSourcePeriod, dir: Long): LocalDate = when (gran) {
    DataSourcePeriod.YESTERDAY -> anchor.plusDays(dir)
    DataSourcePeriod.MONTH -> anchor.plusMonths(dir)
    DataSourcePeriod.YEAR -> anchor.plusYears(dir)
    DataSourcePeriod.ALL -> anchor
}

private val MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy")
private val DAY_FMT = DateTimeFormatter.ofPattern("dd MMM yy")
private val SPAN_FMT = DateTimeFormatter.ofPattern("d MMM yy")

// Basic shows the calendar unit; Advanced shows the trailing-window span, so the
// D/M/Y/* picker visibly reflects the Basic/Advanced tab.
internal fun rangeLabel(gran: DataSourcePeriod, anchor: LocalDate, advanced: Boolean): String = when {
    gran == DataSourcePeriod.ALL -> "*"
    gran == DataSourcePeriod.YESTERDAY -> anchor.format(DAY_FMT)
    !advanced && gran == DataSourcePeriod.YEAR -> anchor.year.toString()
    !advanced && gran == DataSourcePeriod.MONTH -> anchor.format(MONTH_FMT)
    else -> {
        val (f, t) = periodDateRange(gran, anchor, advanced,
            anchor.minusYears(60), anchor.plusYears(60))
        "${f.format(SPAN_FMT)} – ${t.format(SPAN_FMT)}"
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Display
// ──────────────────────────────────────────────────────────────────────────
internal fun compareModeIcon(m: CompareMode): ImageVector = when (m) {
    CompareMode.TABLE -> Icons.Default.TableChart
    CompareMode.BAR   -> Icons.Default.BarChart
    CompareMode.STACK -> Icons.Outlined.StackedBarChart
    CompareMode.LINE  -> Icons.AutoMirrored.Filled.ShowChart
    CompareMode.AREA  -> Icons.Default.Timeline
    CompareMode.PIE   -> Icons.Default.PieChart
}

internal fun compareLayoutIcon(l: CompareLayout): ImageVector = when (l) {
    CompareLayout.MERGED -> Icons.AutoMirrored.Filled.ShowChart
    CompareLayout.SPLIT  -> Icons.Default.ViewModule
}

@Composable
internal fun DisplaySection(state: CompareState, vm: UI2CompareViewModel, novice: Boolean) {
    SmallCaps(stringResource(R.string.ui2_cmp_chart_style))
    if (novice) {
        // Novice: icons + text. AdaptiveCellRow keeps the cards 3-up at fs<1.6
        // and stacks them at fs>=1.6 so the labels stop ellipsising.
        AdaptiveCellRow(
            items = CompareMode.entries.toList(),
            perRowAtA = 3, perRowAtB = 3, perRowAtC = 1,
            spacing = 8.dp
        ) { mode ->
            val active = state.mode == mode
            Surface(
                color = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .then(if (active) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { vm.update { it.copy(mode = mode) } }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(compareModeIcon(mode), contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(mode.labelRes), style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    } else {
        // Compact: icon-only chips. AdaptiveCellRow keeps 4-up at fs<1.6 and
        // shrinks to two rows of two at fs>=1.6 so each chip keeps a
        // comfortable tap target.
        AdaptiveCellRow(
            items = CompareMode.entries.toList(),
            perRowAtA = 4, perRowAtB = 2, perRowAtC = 2,
            spacing = 6.dp
        ) { mode ->
            val active = state.mode == mode
            Surface(
                color = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
                    .then(if (active) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { vm.update { it.copy(mode = mode) } }
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center) {
                    Icon(compareModeIcon(mode), contentDescription = stringResource(mode.labelRes),
                        modifier = Modifier.size(20.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    val layoutable = state.mode in listOf(CompareMode.BAR, CompareMode.STACK, CompareMode.LINE, CompareMode.AREA)
    if (layoutable) {
        SmallCaps(stringResource(R.string.ui2_cmp_layout))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CompareLayout.MERGED to (stringResource(R.string.ui2_cmp_merged)
                    to stringResource(R.string.ui2_cmp_merged_sub)),
                CompareLayout.SPLIT to (stringResource(R.string.ui2_cmp_split)
                    to stringResource(R.string.ui2_cmp_split_sub))
            ).forEach { (lo, txt) ->
                val active = state.layout == lo
                Surface(
                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                        .then(if (active) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable { vm.update { it.copy(layout = lo) } }
                ) {
                    if (novice) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(compareLayoutIcon(lo), null, modifier = Modifier.size(16.dp),
                                    tint = if (active) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(6.dp))
                                Text(txt.first, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface)
                            }
                            Text(txt.second, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center) {
                            Icon(compareLayoutIcon(lo), contentDescription = txt.first,
                                modifier = Modifier.size(20.dp),
                                tint = if (active) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
    // X-axis granularity matters for line/area always, and for bar when the
    // bucketed renderer kicks in (single subject, or merged with ≤2 series).
    if (state.mode == CompareMode.LINE || state.mode == CompareMode.AREA || state.mode == CompareMode.BAR) {
        SmallCaps(stringResource(R.string.ui2_cmp_axis_x))
        val scales = listOf(CompareAxisScale.AUTO) + CompareAxisScale.CONCRETE
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            scales.forEach { sc ->
                val active = state.displayScale == sc
                Surface(
                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                        .then(if (active) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable { vm.update { it.copy(displayScale = sc) } }
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(sc.shortRes),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
    HelpText(
        when {
            state.mode == CompareMode.TABLE -> stringResource(R.string.ui2_cmp_help_table)
            state.mode == CompareMode.PIE -> stringResource(R.string.ui2_cmp_help_pie)
            (state.mode == CompareMode.LINE || state.mode == CompareMode.AREA) &&
                state.displayScale == CompareAxisScale.AUTO ->
                stringResource(R.string.ui2_cmp_help_line_auto)
            state.mode == CompareMode.LINE || state.mode == CompareMode.AREA ->
                stringResource(R.string.ui2_cmp_help_line, stringResource(state.displayScale.labelRes).lowercase())
            state.mode == CompareMode.BAR && state.displayScale == CompareAxisScale.AUTO ->
                stringResource(R.string.ui2_cmp_help_bar_auto)
            state.mode == CompareMode.BAR ->
                stringResource(R.string.ui2_cmp_help_bar, stringResource(state.displayScale.labelRes).lowercase())
            state.layout == CompareLayout.SPLIT -> stringResource(R.string.ui2_cmp_help_split)
            else -> stringResource(R.string.ui2_cmp_help_merged)
        },
        novice
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Select sheet
// ──────────────────────────────────────────────────────────────────────────
@Composable
internal fun SelectSheet(
    kind: String,
    state: CompareState,
    viewModel: UI2CompareViewModel,
    sources: List<CompareSourceItem>,
    sims: List<CompareSimItem>,
    plans: List<ComparePlanItem>,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    // Resolved up front — the SheetSpec is built inside remember, which is not
    // a composable context.
    val sourcesTitle = stringResource(R.string.ui2_data_sources)
    val sourcesSub = stringResource(R.string.ui2_cmp_sheet_sources_sub)
    val simsTitle = stringResource(R.string.ui2_cmp_simulations)
    val simsSub = stringResource(R.string.ui2_cmp_sheet_sims_sub)
    val plansTitle = stringResource(R.string.ui2_supplier_plans)
    val plansSub = stringResource(R.string.ui2_cmp_sheet_plans_sub)
    val scenarioWord = stringResource(R.string.ui2_cmp_scenario_word)

    val spec = remember(kind, state, sources, sims, plans) {
        when (kind) {
            "sources" -> SheetSpec(
                sourcesTitle, sourcesSub,
                sources.map { listOf(it.sysSn, it.sysSn, it.typeName, (it.sysSn in state.sources).toString()) },
                // Toggle: first tap adds an instance, second tap removes
                // *every* instance (including duplicates) so the sheet's
                // check-box semantics stay intuitive. Duplicates are managed
                // from the Sources accordion / per-subject timeframe view.
                { id -> viewModel.update {
                    val s = it.sources
                    it.copy(sources = if (id in s) s.filter { sn -> sn != id } else s + id)
                } },
                { viewModel.update { st ->
                    // "Select all" → ensure every catalogue entry has at least one slot,
                    // preserving any duplicates the user already created.
                    val have = st.sources.toSet()
                    val toAdd = sources.map { it.sysSn }.filter { it !in have }
                    st.copy(sources = st.sources + toAdd)
                } },
                { viewModel.update { it.copy(sources = emptyList()) } }
            )
            "sims" -> SheetSpec(
                simsTitle, simsSub,
                sims.map { listOf(it.scenarioId.toString(), it.name, scenarioWord, (it.scenarioId in state.sims).toString()) },
                { id -> viewModel.update {
                    val s = it.sims; val v = id.toLong()
                    it.copy(sims = if (v in s) s.filter { x -> x != v } else s + v)
                } },
                { viewModel.update { st ->
                    val have = st.sims.toSet()
                    val toAdd = sims.map { it.scenarioId }.filter { it !in have }
                    st.copy(sims = st.sims + toAdd)
                } },
                { viewModel.update { it.copy(sims = emptyList()) } }
            )
            else -> SheetSpec(
                plansTitle, plansSub,
                plans.map { listOf(it.planId.toString(), it.planName, it.supplier, (it.planId in state.plans).toString()) },
                { id -> viewModel.update { val s = it.plans; val v = id.toLong(); it.copy(plans = if (v in s) s - v else s + v) } },
                { viewModel.update { it.copy(plans = plans.map { p -> p.planId }.toSet()) } },
                { viewModel.update { it.copy(plans = emptySet()) } }
            )
        }
    }
    val filtered = spec.entries.filter {
        query.isBlank() || (it[1] + " " + it[2]).contains(query, ignoreCase = true)
    }
    val selectedCount = spec.entries.count { it[3] == "true" }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Clear, stringResource(R.string.ui2_close))
                }
                Column(Modifier.weight(1f)) {
                    Text(spec.title, style = MaterialTheme.typography.titleMedium)
                    Text(spec.sub, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), shape = CircleShape) {
                    Text("$selectedCount", Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.ui2_cmp_search)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (filtered.isEmpty()) {
                    item {
                        Text(stringResource(R.string.ui2_cmp_nothing_matches, query),
                            Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(filtered, key = { it[0] }) { e ->
                    val on = e[3] == "true"
                    Surface(
                        color = if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable { spec.toggle(e[0]) }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(22.dp)
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(6.dp))
                                    .border(1.5.dp,
                                        if (on) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (on) Icon(Icons.Default.Check, null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e[1], style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e[2], style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = spec.selectAll) { Text(stringResource(R.string.ui2_cmp_select_all)) }
                TextButton(onClick = spec.clear) { Text(stringResource(R.string.ui2_clear)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text(stringResource(R.string.ui2_done)) }
            }
        }
    }
}

internal data class SheetSpec(
    val title: String,
    val sub: String,
    val entries: List<List<String>>,
    val toggle: (String) -> Unit,
    val selectAll: () -> Unit,
    val clear: () -> Unit
)
