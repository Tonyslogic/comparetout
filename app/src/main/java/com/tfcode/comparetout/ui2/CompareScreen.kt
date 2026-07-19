@file:Suppress("AssignedValueIsNeverRead")

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

// ──────────────────────────────────────────────────────────────────────────
// Compare tab — recreated from the toutc-compare handoff design.
// Five accordion sections (What · Sources · Filter · Timeframe · Display)
// feed one or two real result panels.
// ──────────────────────────────────────────────────────────────────────────

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
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare)) },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.ui2_menu))
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 92.dp)
            ) {
                item {
                    AccordionCard(stringResource(R.string.ui2_cmp_what_title),
                        whatLabel(state.what), true,
                        open == "what", { open = if (open == "what") null else "what" }) {
                        WhatSection(state, viewModel, noviceMode)
                    }
                }
                item {
                    AccordionCard(stringResource(R.string.ui2_cmp_sources_title),
                        scopeSubtitle(state), subjectsReady(state),
                        open == "sources", { open = if (open == "sources") null else "sources" }) {
                        SourcesSection(state, viewModel,
                            state.sources.size, state.sims.size, state.plans.size,
                            noviceMode) { sheet = it }
                    }
                }
                item {
                    AccordionCard(stringResource(R.string.ui2_cmp_filter_title),
                        filterSubtitle(state), state.series.isNotEmpty(),
                        open == "filter", { open = if (open == "filter") null else "filter" }) {
                        FilterSection(state, viewModel, noviceMode)
                    }
                }
                item {
                    AccordionCard(stringResource(R.string.ui2_timeframe), timeSubtitle(state),
                        viewModel.timeframeReady(state),
                        open == "time", { open = if (open == "time") null else "time" }) {
                        TimeframeSection(state, viewModel, noviceMode, selectedSubjects)
                    }
                }
                item {
                    AccordionCard(stringResource(R.string.ui2_cmp_display_title),
                        displaySubtitle(state), true,
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
                val shareSubject = stringResource(
                    if (metric == CompareWhat.COST) R.string.ui2_cmp_share_cost_subject
                    else R.string.ui2_cmp_share_usage_subject)
                ShareFormatDialog(
                    title = stringResource(
                        if (metric == CompareWhat.COST) R.string.ui2_cmp_share_cost_title
                        else R.string.ui2_cmp_share_usage_title),
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
                                subject = shareSubject
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

