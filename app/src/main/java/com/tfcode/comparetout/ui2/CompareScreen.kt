package com.tfcode.comparetout.ui2

import android.content.res.Configuration
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.StackedBarChart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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

// ──────────────────────────────────────────────────────────────────────────
// Compare tab — recreated from the toutc-compare handoff design.
// Five accordion sections (What · Sources · Filter · Timeframe · Display)
// feed one or two real result panels.
// ──────────────────────────────────────────────────────────────────────────

private val USAGE_IDS = UI2CompareViewModel.USAGE_SERIES.map { it.first }.toSet()

private fun seriesColor(id: String, primary: Color): Color = when (id.removePrefix("c_")) {
    "net"      -> primary
    "buy"      -> Color(0xFF22B8CE)
    "sell"     -> Color(0xFFF5A623)
    "feed"     -> Color(0xFFF5A623)
    "bonus"    -> Color(0xFF4CAF50)
    "fixed"    -> Color(0xFF9E9E9E)
    "load"     -> Color(0xFF3B82F6)
    "pv"       -> Color(0xFFEF5350)
    "pv2load"  -> Color(0xFF66BB6A)
    "bat2load" -> Color(0xFF26A69A)
    "grid2bat" -> Color(0xFF7E57C2)
    else       -> primary
}

private val moneyFmt = DecimalFormat("#,##0.00")
private val kwhFmt = DecimalFormat("#,##0")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    viewModel: UI2CompareViewModel,
    onSwitchLegacy: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val sources by viewModel.sourceItems.collectAsState()
    val sims by viewModel.simItems.collectAsState()
    val plans by viewModel.planItems.collectAsState()
    val results by viewModel.results.observeAsState(null)
    val computing by viewModel.computing.observeAsState(false)
    val noviceMode by viewModel.noviceMode.observeAsState(true)
    val context = LocalContext.current

    var open by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    // When non-null, the format dialog is shown. The metric drives which
    // serialiser the dialog calls on confirmation.
    var shareMetric by remember { mutableStateOf<CompareWhat?>(null) }

    // Subjects currently selected — drives the per-subject timeframe pickers.
    // Slot-aware so duplicates show up as distinct rows ("My SN", "My SN #2").
    val selectedSubjects: List<Pair<String, String>> = remember(state.sources, state.sims, sources, sims) {
        val srcByKey = sources.associateBy { it.sysSn }
        val simByKey = sims.associateBy { it.scenarioId }
        val src = buildSourceSlots(state.sources) { sn -> srcByKey[sn]?.sysSn ?: sn }
            .map { it.subjectId to it.displayName }
        val sim = buildSimSlots(state.sims) { id -> simByKey[id]?.name ?: "Sim #$id" }
            .map { it.subjectId to it.displayName }
        src + sim
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Compare") },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    AccordionCard("What to compare", whatLabel(state.what), true,
                        open == "what", { open = if (open == "what") null else "what" }) {
                        WhatSection(state, viewModel, noviceMode)
                    }
                }
                item {
                    AccordionCard("Sources", scopeSubtitle(state), subjectsReady(state),
                        open == "sources", { open = if (open == "sources") null else "sources" }) {
                        SourcesSection(state, viewModel,
                            state.sources.size, state.sims.size, state.plans.size,
                            noviceMode) { sheet = it }
                    }
                }
                item {
                    AccordionCard("Filter", filterSubtitle(state), state.series.isNotEmpty(),
                        open == "filter", { open = if (open == "filter") null else "filter" }) {
                        FilterSection(state, viewModel)
                    }
                }
                item {
                    AccordionCard("Timeframe", timeSubtitle(state),
                        viewModel.timeframeReady(state),
                        open == "time", { open = if (open == "time") null else "time" }) {
                        TimeframeSection(state, viewModel, noviceMode, selectedSubjects)
                    }
                }
                item {
                    AccordionCard("Display", displaySubtitle(state), true,
                        open == "display", { open = if (open == "display") null else "display" }) {
                        DisplaySection(state, viewModel, noviceMode)
                    }
                }

                val ready = isConfigReady(state, viewModel)
                if (!ready) {
                    item { NotReadyCard(state) }
                } else {
                    val metrics = when (state.what) {
                        CompareWhat.BOTH  -> listOf(CompareWhat.COST, CompareWhat.USAGE)
                        CompareWhat.USAGE -> listOf(CompareWhat.USAGE)
                        CompareWhat.COST  -> listOf(CompareWhat.COST)
                    }
                    if (computing || results == null) {
                        item {
                            Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        items(metrics, key = { it.name }) { metric ->
                            ResultPanel(
                                state = state,
                                metric = metric,
                                results = results!!,
                                novice = noviceMode,
                                onShare = { shareMetric = metric }
                            )
                        }
                    }
                }
            }

            sheet?.let { kind ->
                SelectSheet(kind, state, viewModel, sources, sims, plans) { sheet = null }
            }

            shareMetric?.let { metric ->
                // CSV first — the legacy format people already pipe into Excel —
                // JSON for round-tripping the full row including monthly arrays.
                ShareFormatDialog(
                    title = if (metric == CompareWhat.COST) "Share cost results"
                            else "Share usage results",
                    formats = listOf(ShareFormat.CSV, ShareFormat.JSON),
                    initial = ShareFormat.CSV,
                    onPick = { format ->
                        val payload = when (metric to format) {
                            CompareWhat.COST  to ShareFormat.CSV  -> viewModel.costResultsCsv()
                            CompareWhat.COST  to ShareFormat.JSON -> viewModel.costResultsJson()
                            CompareWhat.USAGE to ShareFormat.CSV  -> viewModel.usageResultsCsv()
                            CompareWhat.USAGE to ShareFormat.JSON -> viewModel.usageResultsJson()
                            else -> null
                        }
                        if (!payload.isNullOrEmpty()) {
                            context.shareText(
                                payload = payload,
                                format = format,
                                subject = if (metric == CompareWhat.COST) "Compare — cost results"
                                          else "Compare — usage results"
                            )
                        }
                    },
                    onDismiss = { shareMetric = null }
                )
            }

            AnimatedVisibility(
                visible = showDrawer,
                enter = fadeIn(tween(180)), exit = fadeOut(tween(180))
            ) {
                Box(Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
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
                        onSwitchLegacy = { showDrawer = false; onSwitchLegacy() },
                        onClose = { showDrawer = false }
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Accordion card
// ──────────────────────────────────────────────────────────────────────────
@Composable
private fun AccordionCard(
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
                    Icon(Icons.Default.Check, contentDescription = "Set",
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand"
                )
            }
            if (open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
            }
        }
    }
}

@Composable
private fun SmallCaps(text: String) {
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
private fun HelpText(text: String, novice: Boolean) {
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
private fun WhatSection(state: CompareState, vm: UI2CompareViewModel, novice: Boolean) {
    val opts = listOf(
        Triple(CompareWhat.COST, "Cost", "Compare € across plans & scenarios"),
        Triple(CompareWhat.USAGE, "Usage", "Compare kWh flows across scenarios"),
        Triple(CompareWhat.BOTH, "Combined", "Side-by-side cost & usage")
    )
    SmallCaps("Comparison type")
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
private fun SourcesSection(
    state: CompareState,
    vm: UI2CompareViewModel,
    sourceCount: Int,
    simCount: Int,
    planCount: Int,
    novice: Boolean,
    onOpenSheet: (String) -> Unit
) {
    SelectRow("Data sources", sourceCount) { onOpenSheet("sources") }
    SelectRow("Simulations", simCount) { onOpenSheet("sims") }
    if (state.what != CompareWhat.USAGE) {
        SelectRow("Supplier plans", planCount) { onOpenSheet("plans") }
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
        Text("Selected subjects",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        slots.forEach { (id, name, isDup) ->
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
                        Text("Duplicate", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { vm.removeSubjectSlot(id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        if (novice) {
            HelpText(
                "Duplicate a source or simulation to compare it against itself " +
                    "over different timeframes. Set the Timeframe to “Per subject” " +
                    "to give each copy its own range.", true
            )
        }
        Spacer(Modifier.height(6.dp))
    }

    val subjects = sourceCount + simCount
    val planFactor = if (state.what == CompareWhat.USAGE) 1 else planCount
    val total = subjects * planFactor
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            buildString {
                append("$sourceCount source"); if (sourceCount != 1) append("s")
                append(" + $simCount sim"); if (simCount != 1) append("s")
                if (state.what != CompareWhat.USAGE) {
                    append(" × $planCount plan"); if (planCount != 1) append("s")
                }
                append("  =  $total result"); if (total != 1) append("s")
            },
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
    HelpText("Selection opens in full-screen pickers to keep this view tidy.", novice)
}

@Composable
private fun SelectRow(title: String, count: Int, onTap: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onTap() }
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Surface(
                color = if (count > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else Color.Transparent,
                shape = CircleShape
            ) {
                Text("$count selected",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (count > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Filter
// ──────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(state: CompareState, vm: UI2CompareViewModel) {
    val primary = MaterialTheme.colorScheme.primary
    @Composable
    fun chips(defs: List<Pair<String, String>>, prefix: String) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            defs.forEach { (rawId, label) ->
                val id = prefix + rawId
                val on = state.series.contains(id)
                FilterChip(
                    selected = on,
                    onClick = {
                        vm.update { it.copy(series = if (on) it.series - id else it.series + id) }
                    },
                    label = { Text(label) },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).background(seriesColor(rawId, primary), CircleShape))
                    }
                )
            }
        }
    }
    when (state.what) {
        CompareWhat.COST -> {
            SmallCaps("Cost columns")
            chips(UI2CompareViewModel.COST_SERIES, "")
        }
        CompareWhat.USAGE -> {
            SmallCaps("Energy flows")
            chips(UI2CompareViewModel.USAGE_SERIES, "")
        }
        CompareWhat.BOTH -> {
            SmallCaps("Energy flows")
            chips(UI2CompareViewModel.USAGE_SERIES, "")
            SmallCaps("Cost columns")
            chips(UI2CompareViewModel.COST_SERIES, "c_")
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Timeframe — Basic/Advanced tab, sync toggle, per-subject ranges
// ──────────────────────────────────────────────────────────────────────────
@Composable
private fun TimeframeSection(
    state: CompareState,
    vm: UI2CompareViewModel,
    novice: Boolean,
    selectedSubjects: List<Pair<String, String>>
) {
    TabRow(selectedTabIndex = if (state.advanced) 1 else 0) {
        Tab(selected = !state.advanced, onClick = { vm.update { it.copy(advanced = false) } },
            text = { Text("Basic") })
        Tab(selected = state.advanced, onClick = { vm.update { it.copy(advanced = true) } },
            text = { Text("Advanced") })
    }
    HelpText(
        if (state.advanced) "Advanced: a trailing window ending on the chosen day."
        else "Basic: whole calendar day / month / year.",
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
                Text("Sync ranges", style = MaterialTheme.typography.bodyLarge)
                if (novice) {
                    Text(
                        if (state.sync) "One range applies to every subject"
                        else "Each source / simulation has its own range",
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
        RangePicker("Range", state.globalGran, state.globalAnchor, state.advanced, novice,
            onGran = { g -> vm.update { it.copy(globalGran = g) } },
            onAnchor = { a -> vm.update { it.copy(globalAnchor = a) } })
    } else if (selectedSubjects.isEmpty()) {
        Text("Select sources or simulations first.",
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
                    Text("Duplicate")
                }
                IconButton(onClick = { vm.removeSubjectSlot(id) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove",
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
private fun RangePicker(
    label: String,
    gran: DataSourcePeriod?,
    anchor: LocalDate,
    advanced: Boolean,
    novice: Boolean,
    onGran: (DataSourcePeriod?) -> Unit,
    onAnchor: (LocalDate) -> Unit
) {
    SmallCaps(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        DataSourcePeriod.values().forEach { p ->
            FilterChip(
                selected = gran == p,
                onClick = { onGran(if (gran == p) null else p) },
                label = { Text(p.label) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
    if (gran != null && gran != DataSourcePeriod.ALL) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onAnchor(stepAnchorBy(anchor, gran, -1)) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Earlier")
            }
            Text(rangeLabel(gran, anchor, advanced),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onAnchor(stepAnchorBy(anchor, gran, 1)) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Later")
            }
        }
    }
    val hint = when (gran) {
        null -> "Pick D / M / Y / * to set this range"
        DataSourcePeriod.ALL -> "All time — every reading on file"
        else -> {
            val (f, t) = periodDateRange(gran, anchor, advanced,
                anchor.minusYears(60), anchor.plusYears(60))
            "$f → $t"
        }
    }
    HelpText(hint, novice || gran == null)
}

private fun stepAnchorBy(anchor: LocalDate, gran: DataSourcePeriod, dir: Long): LocalDate = when (gran) {
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
private fun rangeLabel(gran: DataSourcePeriod, anchor: LocalDate, advanced: Boolean): String = when {
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
private fun compareModeIcon(m: CompareMode): ImageVector = when (m) {
    CompareMode.TABLE -> Icons.Default.TableChart
    CompareMode.BAR   -> Icons.Default.BarChart
    CompareMode.STACK -> Icons.Outlined.StackedBarChart
    CompareMode.LINE  -> Icons.Default.ShowChart
    CompareMode.AREA  -> Icons.Default.Timeline
    CompareMode.PIE   -> Icons.Default.PieChart
}

private fun compareLayoutIcon(l: CompareLayout): ImageVector = when (l) {
    CompareLayout.MERGED -> Icons.Default.ShowChart
    CompareLayout.SPLIT  -> Icons.Default.ViewModule
}

@Composable
private fun DisplaySection(state: CompareState, vm: UI2CompareViewModel, novice: Boolean) {
    SmallCaps("Chart style")
    if (novice) {
        // Novice: icons + text, 3 per row so labels stay readable.
        CompareMode.values().toList().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mode ->
                    val active = state.mode == mode
                    Surface(
                        color = if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp)
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
                            Text(mode.label, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    } else {
        // Compact: one row of icon-only chips so the picker doesn't dominate.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompareMode.values().forEach { mode ->
                val active = state.mode == mode
                Surface(
                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                        .then(if (active) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable { vm.update { it.copy(mode = mode) } }
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Icon(compareModeIcon(mode), contentDescription = mode.label,
                            modifier = Modifier.size(20.dp),
                            tint = if (active) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
    val layoutable = state.mode in listOf(CompareMode.BAR, CompareMode.STACK, CompareMode.LINE, CompareMode.AREA)
    if (layoutable) {
        SmallCaps("Layout")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CompareLayout.MERGED to ("Merged" to "All subjects on one chart"),
                CompareLayout.SPLIT to ("Split" to "One chart per subject")
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
        SmallCaps("Axis (x)")
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
                        Text(sc.short,
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
            state.mode == CompareMode.TABLE -> "Tap a column header to sort. Tap again to reverse."
            state.mode == CompareMode.PIE -> "Pie is always small-multiples — one pie per subject."
            (state.mode == CompareMode.LINE || state.mode == CompareMode.AREA) &&
                state.displayScale == CompareAxisScale.AUTO ->
                "Axis granularity picked from each subject's timeframe — short ranges get Day, long ranges get Month or Year."
            state.mode == CompareMode.LINE || state.mode == CompareMode.AREA ->
                "Each subject's chart uses the chosen ${state.displayScale.label.lowercase()} buckets."
            state.mode == CompareMode.BAR && state.displayScale == CompareAxisScale.AUTO ->
                "Bar axis: bucketed when split, or merged with ≤ 2 series; otherwise one bar per subject (totals)."
            state.mode == CompareMode.BAR ->
                "Bar in ${state.displayScale.label.lowercase()} buckets when split, or merged with ≤ 2 series; otherwise totals."
            state.layout == CompareLayout.SPLIT -> "Each subject gets its own chart."
            else -> "All subjects share one chart for direct comparison."
        },
        novice
    )
}

// ──────────────────────────────────────────────────────────────────────────
// Select sheet
// ──────────────────────────────────────────────────────────────────────────
@Composable
private fun SelectSheet(
    kind: String,
    state: CompareState,
    viewModel: UI2CompareViewModel,
    sources: List<CompareSourceItem>,
    sims: List<CompareSimItem>,
    plans: List<ComparePlanItem>,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val spec = remember(kind, state, sources, sims, plans) {
        when (kind) {
            "sources" -> SheetSpec(
                "Data sources", "Real meter installs to include",
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
                "Simulations", "Saved scenarios to include",
                sims.map { listOf(it.scenarioId.toString(), it.name, "Scenario", (it.scenarioId in state.sims).toString()) },
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
                "Supplier plans", "Tariffs to price the energy flows against",
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
                IconButton(onClick = onClose) { Icon(Icons.Default.Clear, "Close") }
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
                placeholder = { Text("Search…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
            LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (filtered.isEmpty()) {
                    item {
                        Text("Nothing matches \"$query\"",
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
                TextButton(onClick = spec.selectAll) { Text("Select all") }
                TextButton(onClick = spec.clear) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done") }
            }
        }
    }
}

private data class SheetSpec(
    val title: String,
    val sub: String,
    val entries: List<List<String>>,
    val toggle: (String) -> Unit,
    val selectAll: () -> Unit,
    val clear: () -> Unit
)

// ──────────────────────────────────────────────────────────────────────────
// Result panel
// ──────────────────────────────────────────────────────────────────────────
@Composable
private fun ResultPanel(
    state: CompareState,
    metric: CompareWhat,
    results: CompareResults,
    novice: Boolean,
    onShare: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val isCost = metric == CompareWhat.COST
    val series: List<SeriesDef> = run {
        val ids = if (isCost) selectedCostSeries(state) else selectedUsageSeries(state)
        val defs = if (isCost) UI2CompareViewModel.COST_SERIES else UI2CompareViewModel.USAGE_SERIES
        ids.mapNotNull { id -> defs.firstOrNull { it.first == id } }
            .map { SeriesDef(it.first, it.second, seriesColor(it.first, primary)) }
    }
    // Share is only useful once there is a table to export — empty result
    // panels suppress the button to avoid offering a no-op action.
    val rowsCount = if (isCost) results.cost.size else results.usage.size
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(
                    if (isCost) primary else Color(0xFF3B82F6), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(if (isCost) "Cost" else "Usage", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(6.dp))
                Text("· ${state.mode.label}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (rowsCount > 0) {
                    TextButton(onClick = onShare, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (series.isEmpty()) {
                Text("Pick at least one ${if (isCost) "cost column" else "energy flow"} in Filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (isCost) {
                CostContent(state, results.cost, series, novice)
            } else {
                UsageContent(state, results.usage, series, novice)
            }
        }
    }
}

@Composable
private fun CostContent(
    state: CompareState, rows: List<CompareCostRow>, series: List<SeriesDef>, novice: Boolean
) {
    if (rows.isEmpty()) { EmptyResult(); return }
    val chartData = costData(rows)
    val explanation = if (novice)
        graphExplanationText(state.mode, CompareWhat.COST, series, chartData) else null
    var zoomedPie by remember { mutableStateOf<ComparePieDatum?>(null) }

    GraphExplanation(explanation)
    when (state.mode) {
        CompareMode.TABLE -> {
            val headers = buildList {
                add("Subject" to false); add("Plan" to false)
                series.forEach { add(it.label to true) }
            }
            val data = rows.map { r ->
                buildList {
                    add(Cell(r.subjectName, 0.0))
                    add(Cell(r.planName, 0.0))
                    series.forEach { s ->
                        val v = costValue(r, s.id)
                        add(Cell(if (r.available) moneyFmt.format(v) else "—", v,
                            if (s.id == "net") netColor(v) else null))
                    }
                }
            }
            ResultTable(headers, data, defaultSort = 2)
        }
        CompareMode.STACK -> CostStackArea(state.layout, costBars(rows, series), series, explanation)
        CompareMode.PIE -> {
            val pies = costPies(rows, series)
            ComparePieGrid(pies, MaterialTheme.colorScheme.surfaceVariant,
                unit = "€", onZoom = { zoomedPie = it })
        }
        else -> ChartArea(state, "Cost", chartData, series, explanation, "€")
    }
    ResultLegends(state.mode, series, chartData)

    zoomedPie?.let { d ->
        val hole = MaterialTheme.colorScheme.surfaceVariant
        ChartPopout(d.title, series, explanation, emptyList(), pieInfo = d to "€",
            onDismiss = { zoomedPie = null }) { h ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ComparePieCanvas(d.slices, hole, h)
            }
        }
    }
}

/** Renders the signed cost stack, merged into one chart or split per subject. */
@Composable
private fun CostStackArea(
    layout: CompareLayout, bars: List<CostBar>, series: List<SeriesDef>, explanation: String?
) {
    if (layout == CompareLayout.SPLIT && bars.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bars.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { bar ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(8.dp)) {
                                ZoomableChart(bar.title, series, explanation, listOf(bar.title)) { h ->
                                    CompareCostStackChart(listOf(bar), "€", h)
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        ZoomableChart("Cost", series, explanation, bars.map { it.title }) { h ->
            CompareCostStackChart(bars, "€", h)
        }
    }
}

@Composable
private fun UsageContent(
    state: CompareState, rows: List<CompareUsageRow>, series: List<SeriesDef>, novice: Boolean
) {
    if (rows.isEmpty()) { EmptyResult(); return }
    val chartData = usageData(rows)
    val explanation = if (novice)
        graphExplanationText(state.mode, CompareWhat.USAGE, series, chartData) else null
    var zoomedPie by remember { mutableStateOf<ComparePieDatum?>(null) }

    GraphExplanation(explanation)
    when (state.mode) {
        CompareMode.TABLE -> {
            val headers = buildList {
                add("Subject" to false)
                series.forEach { add(it.label to true) }
            }
            val data = rows.map { r ->
                buildList {
                    add(Cell(r.subjectName, 0.0))
                    series.forEach { s -> add(Cell(kwhFmt.format(usageValue(r, s.id)), usageValue(r, s.id))) }
                }
            }
            ResultTable(headers, data, defaultSort = 1)
        }
        CompareMode.PIE -> {
            val pies = usagePies(chartData, series)
            ComparePieGrid(pies, MaterialTheme.colorScheme.surfaceVariant,
                unit = "kWh", onZoom = { zoomedPie = it })
        }
        else -> ChartArea(state, "Usage", chartData, series, explanation, "kWh")
    }
    ResultLegends(state.mode, series, chartData)

    zoomedPie?.let { d ->
        val hole = MaterialTheme.colorScheme.surfaceVariant
        ChartPopout(d.title, series, explanation, emptyList(), pieInfo = d to "kWh",
            onDismiss = { zoomedPie = null }) { h ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ComparePieCanvas(d.slices, hole, h)
            }
        }
    }
}

/**
 * The novice-mode caption text for the current graph. Names the actual subjects
 * so the user can tell which source / simulation / plan is which — those names
 * are otherwise truncated on the axes or absent altogether.
 */
private fun graphExplanationText(
    mode: CompareMode,
    metric: CompareWhat,
    series: List<SeriesDef>,
    data: List<ChartDatum>
): String {
    val unit = if (metric == CompareWhat.COST) "euro (€)" else "energy (kWh)"
    val seriesNames = if (series.isEmpty()) "the selected series"
        else series.joinToString(", ") { it.label }
    val firstSeries = series.firstOrNull()?.label ?: "the first series"
    val subjects = when {
        data.isEmpty() -> "the selected subjects"
        data.size <= 5 -> data.joinToString("; ") { it.title }
        else -> data.take(4).joinToString("; ") { it.title } + "; … (${data.size} total)"
    }
    return when (mode) {
        CompareMode.TABLE ->
            "Each row is a source or simulation priced against a plan. Columns show $seriesNames " +
                "in $unit — tap a header to sort. Rows: $subjects."
        CompareMode.BAR ->
            "Grouped bars in $unit. Each group along the bottom is one subject; the coloured bars " +
                "within it are $seriesNames. Subjects: $subjects."
        CompareMode.STACK ->
            if (metric == CompareWhat.COST)
                "Each bar shows one plan's cost in $unit: buy split into its rate bands plus any " +
                    "fixed charge stack up from the zero line; sell hangs below it. The marker is " +
                    "the net — above the line is a cost, below is a credit. Subjects: $subjects."
            else
                "Each bar stacks $seriesNames in $unit for one subject — a taller bar means more " +
                    "in total. Subjects: $subjects."
        CompareMode.LINE ->
            "Each line follows $firstSeries month by month ($unit), one line per subject — see the " +
                "Sources legend for line colours. Subjects: $subjects."
        CompareMode.AREA ->
            "Filled area showing $firstSeries month by month ($unit), one chart per subject. " +
                "Subjects: $subjects."
        CompareMode.PIE ->
            "Each pie splits one subject's $unit into $seriesNames; the subject is named beneath " +
                "its pie. Subjects: $subjects."
    }
}

/** Inline novice caption card, shown above the graph in the result panel. */
@Composable
private fun GraphExplanation(text: String?) {
    if (text == null) return
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Text(text, modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Series legend (every chart) plus a source legend pop-out (multi-subject charts). */
@Composable
private fun ResultLegends(mode: CompareMode, series: List<SeriesDef>, data: List<ChartDatum>) {
    if (mode == CompareMode.TABLE) return
    Spacer(Modifier.height(10.dp))
    ChartLegend(series)
    if (mode != CompareMode.PIE && data.size > 1) {
        Spacer(Modifier.height(4.dp))
        SourceLegend(data)
    }
}

/** Inline subject → colour list. Used in both the source pop-out and chart pop-outs. */
@Composable
private fun SourceLegendList(titles: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sources", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        titles.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(compareSubjectColor(i), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(t, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Source legend as a pop-out link — used inline beneath the result panel. */
@Composable
private fun SourceLegend(data: List<ChartDatum>) {
    var open by remember { mutableStateOf(false) }
    TextButton(
        onClick = { open = true },
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
    ) {
        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Sources (${data.size})  ↗", style = MaterialTheme.typography.labelMedium)
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Box(Modifier.padding(16.dp)) { SourceLegendList(data.map { it.title }) }
            }
        }
    }
}

@Composable
private fun ChartArea(
    state: CompareState, metricLabel: String,
    data: List<ChartDatum>, series: List<SeriesDef>, explanation: String?, unit: String
) {
    val render: @Composable (List<ChartDatum>, Dp) -> Unit = { d, h ->
        when (state.mode) {
            CompareMode.BAR -> CompareBarChart(d, series, unit, h)
            CompareMode.STACK -> CompareStackChart(d, series, unit, h)
            CompareMode.LINE -> CompareLineChart(d, series, area = false, unit = unit, height = h)
            CompareMode.AREA -> CompareLineChart(d, series, area = true, unit = unit, height = h)
            else -> {}
        }
    }
    if (state.layout == CompareLayout.SPLIT && data.size > 1) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            data.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { d ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(Modifier.padding(8.dp)) {
                                ZoomableChart(d.title, series, explanation, listOf(d.title)) { h ->
                                    render(listOf(d), h)
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        ZoomableChart(metricLabel, series, explanation, data.map { it.title }) { h ->
            render(data, h)
        }
    }
}

/** A chart that opens an orientation-aware pop-out when tapped. */
@Composable
private fun ZoomableChart(
    title: String,
    series: List<SeriesDef>,
    explanation: String?,
    sourceTitles: List<String>,
    chart: @Composable (Dp) -> Unit
) {
    var zoomed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { zoomed = true }) {
        Text("$title  ↗", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp))
        chart(170.dp)
    }
    if (zoomed) {
        ChartPopout(title, series, explanation, sourceTitles,
            onDismiss = { zoomed = false }, chart = chart)
    }
}

/**
 * Graph pop-out: full screen width × 80% height, click outside to dismiss.
 * Landscape — graph left, text + legend right. Portrait — text on top, then the
 * graph, then the legend. Scrolls vertically when content overflows.
 */
@Composable
private fun ChartPopout(
    title: String,
    series: List<SeriesDef>,
    explanation: String?,
    sourceTitles: List<String>,
    onDismiss: () -> Unit,
    pieInfo: Pair<ComparePieDatum, String>? = null,
    chart: @Composable (Dp) -> Unit
) {
    val cfg = LocalConfiguration.current
    val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    val popW = cfg.screenWidthDp.dp
    val popH = (cfg.screenHeightDp * 0.8f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.width(popW).height(popH),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            @Composable
            fun InfoColumn() {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (pieInfo != null) {
                    val (datum, unit) = pieInfo
                    Text(
                        "Total · ${axisNumber(datum.total)} $unit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (explanation != null) {
                    Text(explanation, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (pieInfo != null) {
                    val (datum, unit) = pieInfo
                    PieValueLegend(datum.slices, unit)
                } else {
                    ChartLegend(series)
                }
                if (sourceTitles.size > 1) SourceLegendList(sourceTitles)
            }

            if (landscape) {
                Row(Modifier.fillMaxSize().padding(16.dp)) {
                    Box(
                        modifier = Modifier.weight(1.3f).fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        chart((popH.value * 0.74f).dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) { InfoColumn() }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (pieInfo != null) {
                        val (datum, unit) = pieInfo
                        Text(
                            "Total · ${axisNumber(datum.total)} $unit",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (explanation != null) {
                        Text(explanation, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    chart((popH.value * 0.44f).dp)
                    if (pieInfo != null) {
                        val (datum, unit) = pieInfo
                        PieValueLegend(datum.slices, unit)
                    } else {
                        ChartLegend(series)
                    }
                    if (sourceTitles.size > 1) SourceLegendList(sourceTitles)
                }
            }
        }
    }
}

@Composable
private fun EmptyResult() {
    Text("No data for the chosen subjects and timeframe.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ── result table ────────────────────────────────────────────────────────────
private data class Cell(val text: String, val sort: Double, val color: Color? = null)

@Composable
private fun ResultTable(
    headers: List<Pair<String, Boolean>>,
    rows: List<List<Cell>>,
    defaultSort: Int
) {
    var sortCol by remember { mutableStateOf(defaultSort) }
    var ascending by remember { mutableStateOf(true) }
    val sorted = remember(rows, sortCol, ascending) {
        val numeric = headers.getOrNull(sortCol)?.second ?: true
        val cmp = Comparator<List<Cell>> { a, b ->
            if (numeric) a[sortCol].sort.compareTo(b[sortCol].sort)
            else a[sortCol].text.compareTo(b[sortCol].text)
        }
        rows.sortedWith(if (ascending) cmp else cmp.reversed())
    }
    val weights = headers.mapIndexed { i, _ -> if (i == 0) 1.6f else 1f }

    Column {
        Row(Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp)) {
            headers.forEachIndexed { i, (label, _) ->
                val selected = i == sortCol
                Row(
                    Modifier.weight(weights[i]).clickable {
                        if (sortCol == i) ascending = !ascending
                        else { sortCol = i; ascending = i == 0 }
                    },
                    horizontalArrangement = if (i == 0) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected) {
                        Icon(
                            if (ascending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        sorted.forEachIndexed { ri, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { i, cell ->
                    Text(
                        cell.text,
                        modifier = Modifier.weight(weights[i]),
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = cell.color ?: MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (ri < sorted.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun NotReadyCard(state: CompareState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.DateRange, null, modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Nothing to compare yet", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick at least one ${if (state.what == CompareWhat.USAGE) "source or simulation" else "source/simulation and a plan"}, " +
                    "a filter and a timeframe to see results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Helpers
// ──────────────────────────────────────────────────────────────────────────
private fun whatLabel(w: CompareWhat) = when (w) {
    CompareWhat.COST -> "Cost"; CompareWhat.USAGE -> "Usage"; CompareWhat.BOTH -> "Cost + Usage"
}

private fun subjectsReady(s: CompareState): Boolean {
    val hasSubjects = s.sources.isNotEmpty() || s.sims.isNotEmpty()
    return hasSubjects && (s.what == CompareWhat.USAGE || s.plans.isNotEmpty())
}

private fun scopeSubtitle(s: CompareState): String {
    if (s.sources.isEmpty() && s.sims.isEmpty() && s.plans.isEmpty())
        return "Tap to choose sources, sims & plans"
    return buildList {
        add("${s.sources.size} source" + if (s.sources.size == 1) "" else "s")
        add("${s.sims.size} sim" + if (s.sims.size == 1) "" else "s")
        if (s.what != CompareWhat.USAGE)
            add("${s.plans.size} plan" + if (s.plans.size == 1) "" else "s")
    }.joinToString(" · ")
}

private fun filterSubtitle(s: CompareState): String =
    if (s.series.isEmpty()) "Tap to pick series" else "${s.series.size} series selected"

private fun timeSubtitle(s: CompareState): String {
    val mode = if (s.advanced) "advanced" else "basic"
    if (s.sync) {
        val g = s.globalGran ?: return "Tap to pick a range"
        return "${granWord(g, s.globalAnchor)} · synced · $mode"
    }
    val n = s.perSubjectRanges.count { it.value.gran != null }
    return if (n == 0) "Tap to set per-subject ranges"
    else "$n per-subject range" + (if (n == 1) "" else "s") + " · $mode"
}

private fun granWord(gran: DataSourcePeriod, anchor: LocalDate) = when (gran) {
    DataSourcePeriod.ALL -> "all-time"
    DataSourcePeriod.YEAR -> "yearly ${anchor.year}"
    DataSourcePeriod.MONTH -> "monthly ${anchor.format(MONTH_FMT)}"
    DataSourcePeriod.YESTERDAY -> "daily ${anchor.format(DAY_FMT)}"
}

private fun displaySubtitle(s: CompareState): String {
    val layoutable = s.mode in listOf(CompareMode.BAR, CompareMode.STACK, CompareMode.LINE, CompareMode.AREA)
    val axisable = s.mode == CompareMode.LINE || s.mode == CompareMode.AREA || s.mode == CompareMode.BAR
    return s.mode.label +
        (if (layoutable) " · ${if (s.layout == CompareLayout.MERGED) "merged" else "split"}" else "") +
        (if (axisable) " · axis ${s.displayScale.label.lowercase()}" else "")
}

private fun isConfigReady(s: CompareState, vm: UI2CompareViewModel): Boolean {
    val hasSubjects = s.sources.isNotEmpty() || s.sims.isNotEmpty()
    val hasPlans = s.what == CompareWhat.USAGE || s.plans.isNotEmpty()
    return hasSubjects && hasPlans && s.series.isNotEmpty() && vm.timeframeReady(s)
}

private fun selectedCostSeries(s: CompareState): List<String> = when (s.what) {
    CompareWhat.BOTH -> UI2CompareViewModel.COST_SERIES.map { it.first }.filter { "c_$it" in s.series }
    else -> UI2CompareViewModel.COST_SERIES.map { it.first }.filter { it in s.series }
}

private fun selectedUsageSeries(s: CompareState): List<String> =
    UI2CompareViewModel.USAGE_SERIES.map { it.first }.filter { it in s.series && it in USAGE_IDS }

private fun netColor(v: Double): Color =
    if (v < 0) Color(0xFF4CAF50) else if (v > 0) Color(0xFFE53935) else Color.Unspecified

private fun costValue(r: CompareCostRow, id: String): Double = when (id) {
    "net" -> r.net; "buy" -> r.buy; "sell" -> r.sell
    "bonus" -> r.bonus; "fixed" -> r.fixed; else -> 0.0
}

private fun usageValue(r: CompareUsageRow, id: String): Double = when (id) {
    "load" -> r.load; "buy" -> r.buy; "feed" -> r.feed; "pv" -> r.pv
    "pv2load" -> r.pv2load; "bat2load" -> r.bat2load; "grid2bat" -> r.grid2bat
    else -> 0.0
}

// A cost datum is one source/simulation × one plan, so the axis label must
// carry BOTH — otherwise one source across N plans gives N look-alike bars.
private fun costAxisLabel(r: CompareCostRow): String {
    val supplier = r.planName.substringBefore(" · ")
    return "${shorten(r.subjectName)}\n${shorten(supplier)}"
}

private fun costData(rows: List<CompareCostRow>): List<ChartDatum> = rows.map { r ->
    ChartDatum(
        title = "${r.subjectName}  ·  ${r.planName}",   // full — for legends/pop-outs
        shortLabel = costAxisLabel(r),                  // two-line axis label
        values = mapOf("net" to r.net, "buy" to r.buy, "sell" to r.sell,
            "bonus" to r.bonus, "fixed" to r.fixed),
        axisLabels = r.timeline.axisLabels,
        seriesValues = r.timeline.seriesValues          // keyed "net" only
    )
}

// Cost stack: buy rate bands share one cyan and are told apart by hatch pattern;
// fixed grey and sell amber are solid.
private val FIXED_BAND_COLOR = Color(0xFF9E9E9E)
private val SELL_BAND_COLOR = Color(0xFFF5A623)
private val BUY_BAND_COLOR = Color(0xFF22B8CE)
private val BAND_PATTERNS = listOf(
    BandPattern.SOLID, BandPattern.DIAGONAL, BandPattern.CROSS,
    BandPattern.HORIZONTAL, BandPattern.VERTICAL
)
private fun bandPattern(i: Int): BandPattern = BAND_PATTERNS[i % BAND_PATTERNS.size]

/** Build signed cost bars: buy rate bands + fixed (positive), sell (negative). */
private fun costBars(rows: List<CompareCostRow>, series: List<SeriesDef>): List<CostBar> {
    val showBuy = series.any { it.id == "buy" }
    val showFixed = series.any { it.id == "fixed" }
    val showSell = series.any { it.id == "sell" }
    return rows.map { r ->
        val positives = buildList {
            if (showBuy) r.buyBands.forEachIndexed { i, b ->
                if (b > 0.0) add(CostSegment(BUY_BAND_COLOR, b, bandPattern(i)))
            }
            if (showFixed && r.fixed > 0.0) add(CostSegment(FIXED_BAND_COLOR, r.fixed))
        }
        val negatives = buildList {
            if (showSell && r.sell > 0.0) add(CostSegment(SELL_BAND_COLOR, r.sell))
        }
        CostBar(
            label = costAxisLabel(r),
            title = "${r.subjectName}  ·  ${r.planName}",
            positives = positives,
            negatives = negatives,
            net = r.net
        )
    }
}

private fun usageData(rows: List<CompareUsageRow>): List<ChartDatum> = rows.map { r ->
    ChartDatum(
        title = r.subjectName,
        shortLabel = shorten(r.subjectName),
        values = mapOf("load" to r.load, "buy" to r.buy, "feed" to r.feed, "pv" to r.pv,
            "pv2load" to r.pv2load, "bat2load" to r.bat2load, "grid2bat" to r.grid2bat),
        axisLabels = r.timeline.axisLabels,
        seriesValues = r.timeline.seriesValues
    )
}

private fun shorten(s: String): String = if (s.length <= 12) s else s.take(11) + "…"

/**
 * Build pie data for usage — one pie per subject, slices are the selected series
 * each in its own colour. No hatching needed (each series already has a distinct hue).
 */
private fun usagePies(data: List<ChartDatum>, series: List<SeriesDef>): List<ComparePieDatum> =
    data.map { d ->
        val slices = series.mapNotNull { s ->
            val v = (d.values[s.id] ?: 0.0)
            if (v != 0.0) ComparePieSlice(s.label, s.color, v) else null
        }
        ComparePieDatum(d.title, slices)
    }

/**
 * Build pie data for cost — one pie per (subject × plan). Buy is broken into rate
 * bands using the same cyan + hatch-pattern vocabulary as the cost stack; fixed
 * sits alongside in solid grey. Sell is excluded from the pie (it's a credit, and
 * pies don't represent signed values cleanly) — table / stack still show it.
 */
private fun costPies(rows: List<CompareCostRow>, series: List<SeriesDef>): List<ComparePieDatum> {
    val showBuy = series.any { it.id == "buy" }
    val showFixed = series.any { it.id == "fixed" }
    val showBonus = series.any { it.id == "bonus" }
    return rows.map { r ->
        val slices = buildList {
            if (showBuy) r.buyBands.forEachIndexed { i, b ->
                if (b > 0.0) {
                    // Show the actual c/kWh rate instead of "Buy band N" — far more
                    // useful in the pop-out legend where the user is comparing tariffs.
                    val rate = r.buyBandRates.getOrNull(i)
                    val label = if (rate != null) "${rateLabel(rate)} c/kWh" else "Band ${i + 1}"
                    add(ComparePieSlice(label, BUY_BAND_COLOR, b, bandPattern(i)))
                }
            }
            if (showFixed && r.fixed > 0.0) add(ComparePieSlice("Fixed", FIXED_BAND_COLOR, r.fixed))
            if (showBonus && r.bonus > 0.0) add(ComparePieSlice("Bonus", Color(0xFF4CAF50), r.bonus))
        }
        ComparePieDatum("${r.subjectName}  ·  ${r.planName}", slices)
    }
}

/** Format a c/kWh rate compactly: integer cents stay integer, others get 2 dp. */
private fun rateLabel(c: Double): String =
    if (c == c.toInt().toDouble()) c.toInt().toString() else "%.2f".format(c)


