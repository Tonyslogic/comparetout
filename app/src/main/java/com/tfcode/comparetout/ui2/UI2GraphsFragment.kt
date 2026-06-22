@file:OptIn(ExperimentalMaterial3Api::class)

package com.tfcode.comparetout.ui2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.tfcode.comparetout.R
import com.tfcode.comparetout.ui2.UI2GraphsViewModel.DisplayScale
import com.tfcode.comparetout.ui2.UI2GraphsViewModel.FilterSeries
import com.tfcode.comparetout.ui2.UI2GraphsViewModel.GraphType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.graphics.Color as AndroidColor

// ─── Colors ────────────────────────────────────────────────────────────────
// The series colour registry (SERIES_COLORS + seriesColor()) lives in SeriesColors.kt — the single source of
// truth shared by the graphs, the Sankey, the Comparison and the Dashboard pies.

private val DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy")

// ─── Fragment ──────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2GraphsFragment : Fragment() {

    private val viewModel: UI2GraphsViewModel by viewModels()
    private val sharedViewModel: UI2SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val onSwitchLegacy: () -> Unit = {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val app = requireActivity().application as com.tfcode.comparetout.TOUTCApplication
                app.putStringValueIntoDataStore("use_ui2", "false")
                val intent = android.content.Intent(requireContext(), com.tfcode.comparetout.MainActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                requireActivity().startActivity(intent)
            }
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                UI2Theme {
                    GraphsScreen(
                        viewModel,
                        onBack = { findNavController().popBackStack() },
                        onSwitchLegacy = onSwitchLegacy
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                sharedViewModel.activeSelection.collect { sel ->
                    when (sel) {
                        is UI2SharedViewModel.ActiveSelection.Simulation -> {
                            Log.d("UI2Graphs", "initializing simulation id=${sel.id}")
                            viewModel.initialize(sel.id)
                        }
                        is UI2SharedViewModel.ActiveSelection.DataSource -> {
                            Log.d("UI2Graphs", "initializing data source sysSn=${sel.sysSn}")
                            viewModel.initializeDataSource(sel.sysSn, sel.importerType, sel.startDate, sel.endDate)
                        }
                        UI2SharedViewModel.ActiveSelection.None -> {}
                    }
                }
            }
        }
    }
}

// ─── Root screen ──────────────────────────────────────────────────────────

@Composable
fun GraphsScreen(
    viewModel: UI2GraphsViewModel,
    onBack: () -> Unit,
    onSwitchLegacy: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var showPanel    by remember { mutableStateOf(false) }
    var showLinePop  by remember { mutableStateOf(false) }
    var showDatePick by remember { mutableStateOf(false) }
    var showDrawer   by remember { mutableStateOf(false) }
    val (showHints, toggleShowHints) = rememberShowHints()

    // Slide-out panels widen modestly on tablets / unfolded foldables so their
    // entries don't ellipsise; phones keep today's compact widths. (Full
    // simultaneous docking at ULTRA is deferred — higher risk, reflows content.)
    val panelScreenWidth = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    val settingsPanelWidth = when {
        panelScreenWidth >= AdaptiveLayout.WIDTH_ULTRA_AT -> 360.dp
        panelScreenWidth >= AdaptiveLayout.WIDTH_WIDE_AT  -> 330.dp
        else -> 290.dp
    }
    val drawerPanelWidth =
        if (panelScreenWidth >= AdaptiveLayout.WIDTH_WIDE_AT) 320.dp else 280.dp

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(state.scenarioName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // Main column
            Column(modifier = Modifier.fillMaxSize()) {
                DateNavRow(state, viewModel, onDateClick = { showDatePick = true })
                if (!state.isLoading && state.graphType != GraphType.SANKEY) {
                    PeriodTotalsRow(state)
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        ChartArea(state)
                    }
                }
                // Persistent bottom bar — chart-type tiles + a menu tile that opens
                // the same Chart Settings slide-out the old FAB drove. Replaces the
                // two FABs that used to hide chart content.
                GraphsBottomBar(
                    currentType = state.graphType,
                    onTypeChange = viewModel::setGraphType,
                    onMenuClick = { showPanel = true }
                )
            }

            // Scrim
            AnimatedVisibility(
                visible = showPanel,
                enter = fadeIn(tween(180)),
                exit  = fadeOut(tween(180))
            ) {
                Box(modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showPanel = false })
            }

            // Right slide-out panel
            AnimatedVisibility(
                visible = showPanel,
                enter = slideInHorizontally(tween(220)) { it },
                exit  = slideOutHorizontally(tween(220)) { it },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(settingsPanelWidth)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize()) {
                    SettingsPanel(
                        state, viewModel,
                        onClose = { showPanel = false },
                        onShowLinePopup = { showLinePop = true }
                    )
                }
            }

            // Global app-menu drawer (right side). Scrim + slide-in panel.
            AnimatedVisibility(visible = showDrawer, enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showDrawer = false })
            }
            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInHorizontally(tween(220)) { it },
                exit = slideOutHorizontally(tween(220)) { it },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(drawerPanelWidth)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxSize()) {
                    UI2DrawerContent(
                        showHints = showHints,
                        onShowHintsChange = { if (it != showHints) toggleShowHints() },
                        onSwitchLegacy = { showDrawer = false; onSwitchLegacy() },
                        onClose = { showDrawer = false }
                    )
                }
            }
        }
    }

    if (showDatePick) {
        DateRangePickerDialog(
            state     = state,
            onConfirm = { from, to -> viewModel.setDateRange(from, to); showDatePick = false },
            onDismiss = { showDatePick = false }
        )
    }

    if (showLinePop) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLinePop = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            LinePopupContent(state)
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Period totals row ─────────────────────────────────────────────────────

@Composable
private fun PeriodTotalsRow(state: UI2GraphsViewModel.GraphState) {
    val activeSeries = remember(state.activeFilters, state.availableFilters) {
        state.activeFilters.intersect(state.availableFilters).toList()
    }
    if (activeSeries.isEmpty()) return

    val points = remember(state.intervalData, state.singleDayBarData, state.displayScale) {
        buildChartPoints(state)
    }
    if (points.isEmpty()) return

    val totals = remember(points, activeSeries) {
        activeSeries.associateWith { series -> points.sumOf { it.values[series] ?: 0.0 } }
    }
    val df = remember { java.text.DecimalFormat("#,##0.0") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        activeSeries.forEach { series ->
            val total = totals[series] ?: 0.0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).background(seriesColor(series)))
                Text(
                    "${series.displayName}: ${df.format(total)} kWh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Date nav + step size rows (always visible) ────────────────────────────

@Composable
private fun DateNavRow(
    state: UI2GraphsViewModel.GraphState,
    viewModel: UI2GraphsViewModel,
    onDateClick: () -> Unit
) {
    val fromDisplay = remember(state.from) {
        runCatching { LocalDate.parse(state.from, UI2GraphsViewModel.FMT).format(DISPLAY_FMT) }
            .getOrElse { state.from }
    }
    val toDisplay = remember(state.to) {
        runCatching { LocalDate.parse(state.to, UI2GraphsViewModel.FMT).format(DISPLAY_FMT) }
            .getOrElse { state.to }
    }
    // Chevrons get a wider hit area (height unchanged so the chart isn't
    // squeezed). The narrow default kept catching the date-picker target.
    val chevronModifier = Modifier.size(width = 72.dp, height = 48.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { viewModel.stepBack() }, modifier = chevronModifier) {
            Text("◀", fontSize = 18.sp)
        }
        TextButton(onClick = onDateClick, modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.from == state.to) fromDisplay else "$fromDisplay – $toDisplay",
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        IconButton(onClick = { viewModel.stepForward() }, modifier = chevronModifier) {
            Text("▶", fontSize = 18.sp)
        }
    }
}

// ─── Settings panel ────────────────────────────────────────────────────────

@Composable
private fun SettingsPanel(
    state: UI2GraphsViewModel.GraphState,
    viewModel: UI2GraphsViewModel,
    onClose: () -> Unit,
    onShowLinePopup: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Chart Settings", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Battery SOC + water-temp popup trigger — was a separate FAB before;
        // now lives at the top of the settings panel and only when the line popup
        // is actually applicable (sim single-day with battery/HW).
        if (state.showLineFab) {
            TextButton(
                onClick = { onClose(); onShowLinePopup() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Battery SOC & Water Temp…")
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // Display scale
        PanelSection("Display Scale") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DisplayScale.entries.forEach { scale ->
                    FilterChip(
                        selected = state.displayScale == scale,
                        onClick  = { viewModel.setDisplayScale(scale) },
                        label    = { Text(scale.fullLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Calculation
        PanelSection("Calculation") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UI2GraphsViewModel.Calculation.entries.forEach { calc ->
                    FilterChip(
                        selected = state.calculation == calc,
                        onClick  = { viewModel.setCalculation(calc) },
                        label    = { Text(calc.label) }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Step size
        PanelSection("Step Size") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                UI2GraphsViewModel.StepSize.entries.forEach { sz ->
                    FilterChip(
                        selected = state.stepSize == sz,
                        onClick  = { viewModel.setStepSize(sz) },
                        label    = { Text(sz.fullLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Filter Series", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp))
        FilterGroupContent(state, viewModel)
    }
}

@Composable
private fun PanelSection(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp))
    content()
}

// ─── Bottom bar (chart-type tiles + settings menu tile) ───────────────────
//
// Visual pattern mirrors CompareScreen.DisplaySection's compact mode — a
// row of equally-weighted Surface tiles with rounded corners, primary
// container background and a 1.5dp primary border when active. Replaces
// the old Chart Settings + line-popup FABs that used to float over chart
// content.

private val GRAPHS_BOTTOM_BAR_TILES: List<Pair<GraphType, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    GraphType.BAR    to Icons.Default.BarChart,
    GraphType.LINE   to Icons.AutoMirrored.Filled.ShowChart,
    GraphType.AREA   to Icons.Default.Timeline,
    GraphType.PIE    to Icons.Default.PieChart,
    GraphType.TABLE  to Icons.Default.TableChart,
    GraphType.SANKEY to Icons.AutoMirrored.Filled.CallSplit
)

@Composable
private fun GraphsBottomBar(
    currentType: GraphType,
    onTypeChange: (GraphType) -> Unit,
    onMenuClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        GRAPHS_BOTTOM_BAR_TILES.forEach { (type, icon) ->
            val active = currentType == type
            BottomBarTile(
                icon = icon,
                contentDescription = type.label,
                active = active,
                shape = shape,
                modifier = Modifier.weight(1f),
                onClick = { onTypeChange(type) }
            )
        }
        BottomBarTile(
            icon = Icons.Default.Settings,
            contentDescription = "Chart settings",
            active = false,
            shape = shape,
            modifier = Modifier.weight(1f),
            onClick = onMenuClick
        )
    }
}

@Composable
private fun BottomBarTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
        shape = shape,
        modifier = modifier
            .then(if (active) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Chart area dispatcher ─────────────────────────────────────────────────

@Composable
private fun ChartArea(state: UI2GraphsViewModel.GraphState) {
    when (state.graphType) {
        GraphType.BAR    -> BarChartView(state)
        GraphType.LINE   -> LineChartView(state)
        GraphType.AREA   -> AreaChartView(state)
        GraphType.PIE    -> PieChartView(state)
        GraphType.TABLE  -> TableView(state)
        GraphType.SANKEY -> SankeyView(state)
    }
}

// ─── Unified data point ─────────────────────────────────────────────────────

data class ChartPoint(val label: String, val values: Map<FilterSeries, Double>)

private fun buildChartPoints(state: UI2GraphsViewModel.GraphState): List<ChartPoint> {
    // Sim single-day at HOUR scale populates singleDayBarData; AlphaESS / data-source
    // mode always flows through intervalData (even on single-day HOUR), so route by
    // which list actually has data, not by mode/scale.
    return if (state.singleDayBarData.isNotEmpty()) {
        state.singleDayBarData.map { row ->
            ChartPoint(
                label = "%02d:00".format(row.hour),
                values = mapOf(
                    FilterSeries.LOAD          to row.load,
                    FilterSeries.FEED          to row.feed,
                    FilterSeries.BUY           to row.buy,
                    FilterSeries.PV            to row.pv,
                    FilterSeries.PV2BAT        to row.pv2Battery,
                    FilterSeries.PV2LOAD       to row.pv2Load,
                    FilterSeries.BAT2LOAD      to row.battery2Load,
                    FilterSeries.GRID2BAT      to row.grid2Battery,
                    FilterSeries.EV_SCHEDULE   to row.evSchedule,
                    FilterSeries.EV_DIVERT     to row.evDivert,
                    // singleDayBarData is sim-only (see UI2GraphsViewModel.fetchData);
                    // AlphaESS data flows through intervalData, so 0 here is correct.
                    FilterSeries.EV_ACTUAL     to 0.0,
                    FilterSeries.HW_SCHEDULE   to row.hwSchedule,
                    FilterSeries.HW_DIVERT     to row.hwDivert,
                    FilterSeries.BAT2GRID      to row.bat2grid,
                    FilterSeries.BAT_CHARGE    to 0.0,
                    FilterSeries.BAT_DISCHARGE to 0.0,
                    FilterSeries.HEAT_PUMP        to row.heatPump,
                    FilterSeries.HEAT_PUMP_BACKUP to row.heatPumpBackup,
                    FilterSeries.HEAT_PUMP_HEAT   to row.heatPumpHeat,
                    FilterSeries.HEAT_PUMP_COP    to row.heatPumpCop,
                    FilterSeries.HEAT_PUMP_TEMP   to row.heatPumpOutdoorTemp,
                    FilterSeries.HEAT_PUMP_WIND   to row.heatPumpWindSpeed
                )
            )
        }
    } else {
        state.intervalData.map { row ->
            ChartPoint(
                label = row.interval ?: "",
                values = mapOf(
                    FilterSeries.LOAD          to row.load,
                    FilterSeries.FEED          to row.feed,
                    FilterSeries.BUY           to row.buy,
                    FilterSeries.PV            to row.pv,
                    FilterSeries.PV2BAT        to row.pv2bat,
                    FilterSeries.PV2LOAD       to row.pv2load,
                    FilterSeries.BAT2LOAD      to row.bat2load,
                    FilterSeries.GRID2BAT      to row.grid2bat,
                    FilterSeries.EV_SCHEDULE   to row.evSchedule,
                    FilterSeries.EV_DIVERT     to row.evDivert,
                    FilterSeries.EV_ACTUAL     to row.evActual,
                    FilterSeries.HW_SCHEDULE   to row.hwSchedule,
                    FilterSeries.HW_DIVERT     to row.hwDivert,
                    FilterSeries.BAT2GRID      to row.bat2grid,
                    FilterSeries.BAT_CHARGE    to row.batCharge,
                    FilterSeries.BAT_DISCHARGE to row.batDischarge,
                    FilterSeries.HEAT_PUMP        to row.heatPump,
                    FilterSeries.HEAT_PUMP_BACKUP to row.heatPumpBackup,
                    FilterSeries.HEAT_PUMP_HEAT   to row.heatPumpHeat,
                    FilterSeries.HEAT_PUMP_COP    to row.heatPumpCop,
                    FilterSeries.HEAT_PUMP_TEMP   to row.heatPumpOutdoorTemp,
                    FilterSeries.HEAT_PUMP_WIND   to row.heatPumpWindSpeed
                )
            )
        }
    }
}

// ─── MPAndroidChart helpers (apply theme colours) ─────────────────────────

private fun applyBarChartStyle(chart: BarChart, labelColor: Int, gridColor: Int) {
    chart.description.isEnabled = false
    chart.setDrawGridBackground(false)
    chart.setDrawBarShadow(false)
    chart.setFitBars(true)
    chart.setNoDataTextColor(labelColor)
    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.granularity = 1f
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.textColor = labelColor
    chart.axisLeft.textColor = labelColor
    chart.axisLeft.gridColor = gridColor
    chart.axisRight.isEnabled = false
    chart.legend.textColor = labelColor
    chart.legend.orientation = Legend.LegendOrientation.HORIZONTAL
    chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
}

private fun applyLineChartStyle(chart: LineChart, labelColor: Int, gridColor: Int) {
    chart.description.isEnabled = false
    chart.setDrawGridBackground(false)
    chart.setNoDataTextColor(labelColor)
    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.granularity = 1f
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.textColor = labelColor
    chart.axisLeft.textColor = labelColor
    chart.axisLeft.gridColor = gridColor
    chart.axisRight.isEnabled = false
    chart.legend.textColor = labelColor
    chart.legend.orientation = Legend.LegendOrientation.HORIZONTAL
    chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
}

// ─── BAR chart ─────────────────────────────────────────────────────────────

@Composable
private fun BarChartView(state: UI2GraphsViewModel.GraphState) {
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f).toArgb()
    val points = remember(state.intervalData, state.singleDayBarData, state.displayScale) {
        buildChartPoints(state)
    }
    val activeSeries = state.activeFilters.intersect(state.availableFilters).toList()

    if (points.isEmpty() || activeSeries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data") }
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            BarChart(ctx).also { applyBarChartStyle(it, labelColor, gridColor) }
        },
        update = { chart ->
            applyBarChartStyle(chart, labelColor, gridColor)
            val labels = points.map { it.label }
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float) = labels.getOrElse(v.toInt()) { "" }
            }
            val dataSets: List<IBarDataSet> = activeSeries.map { series ->
                BarDataSet(
                    points.mapIndexed { i, pt -> BarEntry(i.toFloat(), pt.values[series]?.toFloat() ?: 0f) },
                    series.displayName
                ).apply {
                    color = (seriesColor(series)).toArgb()
                    setDrawValues(false)
                    valueTextColor = labelColor
                }
            }
            val barData = BarData(dataSets)
            if (activeSeries.size > 1) {
                val groupSpace = 0.08f; val barSpace = 0.02f
                barData.barWidth = (1f - groupSpace) / activeSeries.size - barSpace
                chart.data = barData
                chart.groupBars(0f, groupSpace, barSpace)
                chart.xAxis.axisMinimum = 0f
                chart.xAxis.axisMaximum = points.size.toFloat()
                chart.xAxis.setCenterAxisLabels(true)
            } else {
                barData.barWidth = 0.9f
                chart.data = barData
            }
            chart.invalidate()
        }
    )
}

// ─── LINE / AREA charts ────────────────────────────────────────────────────
//
// LINE and AREA share the same MPAndroidChart wiring; AREA just sets
// setDrawFilled(true) on each dataset so the region below the curve is
// filled with the series colour at low alpha. Single helper, two thin
// public composables.

@Composable
private fun LineChartView(state: UI2GraphsViewModel.GraphState) =
    LineOrAreaChartView(state, filled = false)

@Composable
private fun AreaChartView(state: UI2GraphsViewModel.GraphState) =
    LineOrAreaChartView(state, filled = true)

@Composable
private fun LineOrAreaChartView(state: UI2GraphsViewModel.GraphState, filled: Boolean) {
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f).toArgb()
    val points = remember(state.intervalData, state.singleDayBarData, state.displayScale) {
        buildChartPoints(state)
    }
    val activeSeries = state.activeFilters.intersect(state.availableFilters).toList()

    if (points.isEmpty() || activeSeries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data") }
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            LineChart(ctx).also { applyLineChartStyle(it, labelColor, gridColor) }
        },
        update = { chart ->
            applyLineChartStyle(chart, labelColor, gridColor)
            val labels = points.map { it.label }
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float) = labels.getOrElse(v.toInt()) { "" }
            }
            val datasets: List<ILineDataSet> = activeSeries.map { series ->
                val seriesArgb = (seriesColor(series)).toArgb()
                LineDataSet(
                    points.mapIndexed { i, pt -> Entry(i.toFloat(), pt.values[series]?.toFloat() ?: 0f) },
                    series.displayName
                ).apply {
                    color = seriesArgb
                    setDrawCircles(false)
                    lineWidth = 1.5f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    valueTextColor = labelColor
                    if (filled) {
                        setDrawFilled(true)
                        fillColor = seriesArgb
                        fillAlpha = 80
                    }
                }
            }
            chart.data = LineData(datasets)
            chart.invalidate()
        }
    )
}

// ─── PIE chart — energy breakdown pies (Compose Canvas, dashboard pattern) ─

private data class GraphPieSpec(val title: String, val slices: List<PieSlice>)

private fun buildGraphPieSpecs(state: UI2GraphsViewModel.GraphState): List<GraphPieSpec> {
    val points = buildChartPoints(state)
    if (points.isEmpty()) return emptyList()

    val pv       = points.sumOf { it.values[FilterSeries.PV]        ?: 0.0 }
    val feed     = points.sumOf { it.values[FilterSeries.FEED]      ?: 0.0 }
    val buy      = points.sumOf { it.values[FilterSeries.BUY]       ?: 0.0 }
    val pv2load  = points.sumOf { it.values[FilterSeries.PV2LOAD]   ?: 0.0 }
    val pv2bat   = points.sumOf { it.values[FilterSeries.PV2BAT]    ?: 0.0 }
    val bat2load = points.sumOf { it.values[FilterSeries.BAT2LOAD]  ?: 0.0 }
    val grid2bat = points.sumOf { it.values[FilterSeries.GRID2BAT]  ?: 0.0 }
    val bat2grid = points.sumOf { it.values[FilterSeries.BAT2GRID]  ?: 0.0 }
    val evDiv    = points.sumOf { it.values[FilterSeries.EV_DIVERT] ?: 0.0 }
    val hwDiv    = points.sumOf { it.values[FilterSeries.HW_DIVERT] ?: 0.0 }

    val specs = mutableListOf<GraphPieSpec>()

    if (pv > 0) {
        val pvUsed = maxOf(0.0, pv2load + pv2bat + hwDiv + evDiv)
        specs.add(GraphPieSpec("Self Consumption", listOf(
            PieSlice("PV Used", pvUsed, Color(0xFF4CAF50)),
            PieSlice("Exported", feed, Color(0xFFFFEB3B))
        ).filter { it.value > 0 }))
    }

    val gridToLoad = maxOf(0.0, buy - grid2bat)
    if (pv2load + bat2load + gridToLoad > 0) {
        specs.add(GraphPieSpec("Load Source", buildList {
            if (pv2load > 0)    add(PieSlice("Solar",   pv2load,    Color(0xFFF44336)))
            if (bat2load > 0)   add(PieSlice("Battery", bat2load,   Color(0xFF4CAF50)))
            if (gridToLoad > 0) add(PieSlice("Grid",    gridToLoad, Color(0xFF00BCD4)))
        }))
    }

    if (pv > 0) {
        specs.add(GraphPieSpec("Solar Distribution", buildList {
            if (pv2load > 0) add(PieSlice("To Load",   pv2load, Color(0xFF4CAF50)))
            if (pv2bat > 0)  add(PieSlice("Battery",   pv2bat,  Color(0xFF2196F3)))
            if (evDiv > 0)   add(PieSlice("EV",        evDiv,   Color(0xFFFF9800)))
            if (hwDiv > 0)   add(PieSlice("Hot Water", hwDiv,   Color(0xFF9C27B0)))
            if (feed > 0)    add(PieSlice("Exported",  feed,    Color(0xFFFFEB3B)))
        }))
    }

    if (state.hasBattery && pv2bat + grid2bat + bat2load + bat2grid > 0) {
        specs.add(GraphPieSpec("Battery Flows", buildList {
            if (pv2bat > 0)   add(PieSlice("Solar In", pv2bat,   Color(0xFF4CAF50)))
            if (grid2bat > 0) add(PieSlice("Grid In",  grid2bat, Color(0xFF00BCD4)))
            if (bat2load > 0) add(PieSlice("To Load",  bat2load, Color(0xFF2196F3)))
            if (bat2grid > 0) add(PieSlice("To Grid",  bat2grid, Color(0xFFFFD700)))
        }))
    }

    return specs
}

@Composable
private fun PieChartView(state: UI2GraphsViewModel.GraphState) {
    val specs = remember(state.intervalData, state.singleDayBarData, state.displayScale) {
        buildGraphPieSpecs(state)
    }

    if (specs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data") }
        return
    }

    var zoomedIdx by remember(specs) { mutableIntStateOf(-1) }
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    val cols = 2
    val rows = (specs.size + cols - 1) / cols
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until cols) {
                    val idx = row * cols + col
                    if (idx < specs.size) {
                        val spec = specs[idx]
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { zoomedIdx = idx }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(spec.title, style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            PieChart(slices = spec.slices, modifier = Modifier.size(80.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (zoomedIdx in specs.indices) {
        val spec = specs[zoomedIdx]
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 0.9f).toDp() }
        Dialog(onDismissRequest = { zoomedIdx = -1 }) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(spec.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    PieChart(slices = spec.slices, modifier = Modifier.size(size * 0.72f), isDonut = true)
                    PieLegend(slices = spec.slices)
                }
            }
        }
    }
}

// ─── TABLE ─────────────────────────────────────────────────────────────────

@Composable
private fun TableView(state: UI2GraphsViewModel.GraphState) {
    val points = remember(state.intervalData, state.singleDayBarData, state.displayScale) {
        buildChartPoints(state)
    }
    val activeSeries = state.activeFilters.intersect(state.availableFilters).toList()

    if (points.isEmpty() || activeSeries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data") }
        return
    }

    // Shared horizontal scroll so header + rows stay in sync. Pinned first
    // column stays fixed while series columns scroll horizontally — the
    // pinned-column width grows with system fontScale (mirrors
    // PinnedScrollTable; same primitive, hand-rolled here to preserve the
    // LazyColumn body).
    val hScroll = rememberScrollState()
    val colW = AdaptiveLayout.SCROLL_COL_MIN_NUMERIC
    val pinnedWidth = pinnedColumnWidth(adaptiveFontScale())
    val surface = MaterialTheme.colorScheme.surface

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky header
        Row(modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(vertical = 4.dp)
        ) {
            Box(
                Modifier
                    .width(pinnedWidth)
                    .shadow(elevation = 2.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 4.dp)
            ) {
                Text("Interval", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Row(modifier = Modifier
                .weight(1f)
                .horizontalScroll(hScroll)
            ) {
                activeSeries.forEach { s ->
                    Text(s.displayName, modifier = Modifier.width(colW).padding(horizontal = 4.dp),
                        fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End,
                        maxLines = 2)
                }
            }
        }
        HorizontalDivider()
        // Scrollable data rows (vertical scroll; horizontal synced via hScroll)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(points) { pt ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                ) {
                    Box(
                        Modifier
                            .width(pinnedWidth)
                            .shadow(elevation = 2.dp)
                            .background(surface)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(pt.label, fontSize = 10.sp)
                    }
                    Row(modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll)
                    ) {
                        activeSeries.forEach { s ->
                            Text("%.2f".format(pt.values[s] ?: 0.0),
                                modifier = Modifier.width(colW).padding(horizontal = 4.dp),
                                fontSize = 10.sp, textAlign = TextAlign.End)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

// ─── SANKEY ────────────────────────────────────────────────────────────────

private data class SankeyFlow(val from: String, val to: String, val value: Double, val color: Color)

@Composable
private fun SankeyView(state: UI2GraphsViewModel.GraphState) {
    val hasData = state.intervalData.isNotEmpty() || state.singleDayBarData.isNotEmpty()
    if (!hasData) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data") }
        return
    }

    var pv = 0.0; var feed = 0.0; var buy = 0.0
    var pv2bat = 0.0; var pv2load = 0.0; var bat2load = 0.0; var grid2bat = 0.0
    var evSchedule = 0.0; var evDivert = 0.0; var hwSchedule = 0.0; var hwDivert = 0.0; var bat2grid = 0.0
    var heatPump = 0.0   // sim-only (singleDayBarData); shown as a grid destination like scheduled HW/EV

    if (state.singleDayBarData.isNotEmpty()) {
        // Sum hourly bar data for a single simulation day
        state.singleDayBarData.forEach { row ->
            pv += row.pv; feed += row.feed; buy += row.buy
            pv2bat += row.pv2Battery; pv2load += row.pv2Load; bat2load += row.battery2Load
            grid2bat += row.grid2Battery; evSchedule += row.evSchedule; evDivert += row.evDivert
            hwSchedule += row.hwSchedule; hwDivert += row.hwDivert; bat2grid += row.bat2grid
            heatPump += row.heatPump
        }
    } else {
        state.intervalData.forEach { row ->
            pv += row.pv; feed += row.feed; buy += row.buy
            pv2bat += row.pv2bat; pv2load += row.pv2load; bat2load += row.bat2load
            grid2bat += row.grid2bat; evSchedule += row.evSchedule; evDivert += row.evDivert
            hwSchedule += row.hwSchedule; hwDivert += row.hwDivert; bat2grid += row.bat2grid
            heatPump += row.heatPump
        }
    }

    val flows = buildList {
        // ── Load-serving partition (fixes the scheduled-load double count) ──────────────────────────────
        // pv2load / bat2load / gridLoad each serve the FULL inputLoad: base load PLUS the scheduled extras
        // (immersion HW, scheduled EV charge, heat pump). The extras also have their own columns. Drawing the
        // full …→Load flows AND a separate Grid→{HW,EV,HP} on top double-counts the extras on the source side.
        // Fix (plan §"Sankey double-counts", direction B): carve the extras OUT of the load-serving flows so
        // the "Load" sink is base-only, and add matching source→{HW,EV,HP} flows. Proportional rule — the same
        // per-destination fractions for every source — so no assumption the extras are grid-sourced and energy
        // stays balanced. (hwDivert/evDivert are genuine surplus PV, separate from load, so they're untouched.)
        val gridLoad = maxOf(0.0, buy - maxOf(0.0, bat2load - pv2bat))
        val loadServe = pv2load + bat2load + gridLoad
        val extras = hwSchedule + evSchedule + heatPump
        val attributable = minOf(extras, loadServe)   // can't carve more than the load flows actually carry
        val fBase = if (loadServe > 0) (loadServe - attributable) / loadServe else 0.0
        val fHW = if (loadServe > 0 && extras > 0) attributable * hwSchedule / extras / loadServe else 0.0
        val fEV = if (loadServe > 0 && extras > 0) attributable * evSchedule / extras / loadServe else 0.0
        val fHP = if (loadServe > 0 && extras > 0) attributable * heatPump / extras / loadServe else 0.0
        fun addLoadSource(name: String, value: Double, loadColor: Color) {
            if (value <= 0.0) return
            if (value * fBase > 0) add(SankeyFlow(name, "Load",      value * fBase, loadColor))
            if (value * fHW > 0)   add(SankeyFlow(name, "Hot Water", value * fHW,   seriesColor(FilterSeries.HW_SCHEDULE)))
            if (value * fEV > 0)   add(SankeyFlow(name, "EV",        value * fEV,   seriesColor(FilterSeries.EV_SCHEDULE)))
            if (value * fHP > 0)   add(SankeyFlow(name, "Heat Pump", value * fHP,   seriesColor(FilterSeries.HEAT_PUMP)))
        }

        addLoadSource("Solar", pv2load, seriesColor(FilterSeries.PV))
        if (pv2bat > 0)     add(SankeyFlow("Solar",   "Battery",   pv2bat,     seriesColor(FilterSeries.PV2BAT)))
        if (hwDivert > 0)   add(SankeyFlow("Solar",   "Hot Water", hwDivert,   seriesColor(FilterSeries.HW_DIVERT)))
        if (evDivert > 0)   add(SankeyFlow("Solar",   "EV",        evDivert,   seriesColor(FilterSeries.EV_DIVERT)))
        if (feed > 0)       add(SankeyFlow("Solar",   "Export",    feed,        seriesColor(FilterSeries.FEED)))
        addLoadSource("Grid", gridLoad, seriesColor(FilterSeries.BUY))
        if (grid2bat > 0)   add(SankeyFlow("Grid",    "Battery",   grid2bat,    seriesColor(FilterSeries.GRID2BAT)))
        addLoadSource("Battery", bat2load, seriesColor(FilterSeries.BAT2LOAD))
        if (bat2grid > 0)   add(SankeyFlow("Battery", "Export",    bat2grid,    seriesColor(FilterSeries.BAT2GRID)))
    }

    if (flows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No Sankey data") }
        return
    }

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) { drawSankey(flows) }
}

private fun DrawScope.drawSankey(flows: List<SankeyFlow>) {
    val w = size.width; val h = size.height
    val nw = 28f; val gap = 14f
    val lx = 16f; val rx = w - nw - 16f

    val srcNames = flows.map { it.from }.distinct()
    val snkNames = flows.map { it.to }.distinct()
    val grand = flows.sumOf { it.value }.coerceAtLeast(1.0)
    val usableH = h - (maxOf(srcNames.size, snkNames.size) - 1) * gap

    val srcH = srcNames.associateWith { src ->
        (flows.filter { it.from == src }.sumOf { it.value } / grand * usableH).toFloat().coerceAtLeast(4f)
    }
    val snkH = snkNames.associateWith { snk ->
        (flows.filter { it.to == snk }.sumOf { it.value } / grand * usableH).toFloat().coerceAtLeast(4f)
    }
    val srcY = mutableMapOf<String, Float>(); var y = 0f
    srcNames.forEach { s -> srcY[s] = y; y += (srcH[s] ?: 0f) + gap }
    val snkY = mutableMapOf<String, Float>(); y = 0f
    snkNames.forEach { s -> snkY[s] = y; y += (snkH[s] ?: 0f) + gap }

    val srcOff = srcNames.associateWith { 0f }.toMutableMap()
    val snkOff = snkNames.associateWith { 0f }.toMutableMap()

    flows.forEach { flow ->
        val rh = (flow.value / grand * usableH).toFloat().coerceAtLeast(1f)
        val sy = (srcY[flow.from] ?: 0f) + (srcOff[flow.from] ?: 0f)
        val dy = (snkY[flow.to] ?: 0f)   + (snkOff[flow.to] ?: 0f)
        val cp = (lx + nw + rx) / 2f
        val path = Path().apply {
            moveTo(lx + nw, sy); cubicTo(cp, sy, cp, dy, rx, dy)
            lineTo(rx, dy + rh); cubicTo(cp, dy + rh, cp, sy + rh, lx + nw, sy + rh); close()
        }
        drawPath(path, color = flow.color.copy(alpha = 0.65f), style = Fill)
        srcOff[flow.from] = (srcOff[flow.from] ?: 0f) + rh
        snkOff[flow.to]   = (snkOff[flow.to] ?: 0f) + rh
    }

    val tp = android.graphics.Paint().apply { color = AndroidColor.WHITE; textSize = 26f; isAntiAlias = true }
    val vp = android.graphics.Paint().apply { color = AndroidColor.LTGRAY; textSize = 21f; isAntiAlias = true }
    srcNames.forEach { src ->
        val top = srcY[src] ?: 0f; val nh = srcH[src] ?: 0f
        drawRect(Color.DarkGray, Offset(lx, top), Size(nw, nh))
        drawContext.canvas.nativeCanvas.drawText(src, lx + nw + 4f, top + nh / 2f - 6f, tp)
        val tot = flows.filter { it.from == src }.sumOf { it.value }
        drawContext.canvas.nativeCanvas.drawText("%.1f kWh".format(tot), lx + nw + 4f, top + nh / 2f + 16f, vp)
    }
    snkNames.forEach { snk ->
        val top = snkY[snk] ?: 0f; val nh = snkH[snk] ?: 0f
        drawRect(Color.DarkGray, Offset(rx, top), Size(nw, nh))
        drawContext.canvas.nativeCanvas.drawText(snk, rx - 130f, top + nh / 2f - 6f, tp)
        val tot = flows.filter { it.to == snk }.sumOf { it.value }
        drawContext.canvas.nativeCanvas.drawText("%.1f kWh".format(tot), rx - 130f, top + nh / 2f + 16f, vp)
    }
}

// ─── FAB line popup ────────────────────────────────────────────────────────

@Composable
private fun LinePopupContent(state: UI2GraphsViewModel.GraphState) {
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f).toArgb()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Battery SOC & Water Temperature",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp))
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            factory = { ctx ->
                LineChart(ctx).apply {
                    description.isEnabled = false
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.granularity = 60f
                    xAxis.setDrawGridLines(false)
                    xAxis.textColor = labelColor
                    axisLeft.textColor = labelColor
                    axisLeft.gridColor = gridColor
                    axisLeft.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(v: Float) = "%.1f".format(v)
                    }
                    axisRight.isEnabled = true
                    axisRight.textColor = labelColor
                    axisRight.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(v: Float) = "${v.toInt()}°C"
                    }
                    legend.textColor = labelColor
                    legend.orientation = Legend.LegendOrientation.HORIZONTAL
                    legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    setNoDataTextColor(labelColor)
                    xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(v: Float): String {
                            return "%02d:%02d".format((v / 60).toInt(), (v % 60).toInt())
                        }
                    }
                }
            },
            update = { chart ->
                chart.xAxis.textColor = labelColor
                chart.axisLeft.textColor = labelColor
                chart.axisRight.textColor = labelColor
                chart.legend.textColor = labelColor
                if (state.lineData.isEmpty()) { chart.clear(); return@AndroidView }
                val sets = mutableListOf<ILineDataSet>()
                if (state.hasBattery) {
                    sets.add(LineDataSet(
                        state.lineData.map { Entry(it.mod.toFloat(), it.soc.toFloat()) }, "SOC (%)"
                    ).apply {
                        color = seriesColor(FilterSeries.BAT_CHARGE).toArgb()
                        setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
                        axisDependency = YAxis.AxisDependency.LEFT; valueTextColor = labelColor
                    })
                }
                if (state.hasHW) {
                    sets.add(LineDataSet(
                        state.lineData.map { Entry(it.mod.toFloat(), it.waterTemperature.toFloat()) }, "Water Temp (°C)"
                    ).apply {
                        color = seriesColor(FilterSeries.HW_SCHEDULE).toArgb()
                        setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
                        axisDependency = YAxis.AxisDependency.RIGHT
                        enableDashedLine(10f, 5f, 0f); valueTextColor = labelColor
                    })
                }
                if (state.hasHeatPump) {
                    // COP and outdoor temp on the right axis (both small-magnitude, averaged metrics — design §8).
                    sets.add(LineDataSet(
                        state.lineData.map { Entry(it.mod.toFloat(), it.heatPumpCop.toFloat()) }, "HP COP"
                    ).apply {
                        color = seriesColor(FilterSeries.HEAT_PUMP).toArgb()
                        setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
                        axisDependency = YAxis.AxisDependency.RIGHT; valueTextColor = labelColor
                    })
                    sets.add(LineDataSet(
                        state.lineData.map { Entry(it.mod.toFloat(), it.heatPumpOutdoorTemp.toFloat()) }, "Outdoor °C"
                    ).apply {
                        color = seriesColor(FilterSeries.HEAT_PUMP_HEAT).toArgb()
                        setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
                        axisDependency = YAxis.AxisDependency.RIGHT
                        enableDashedLine(10f, 5f, 0f); valueTextColor = labelColor
                    })
                    sets.add(LineDataSet(
                        state.lineData.map { Entry(it.mod.toFloat(), it.heatPumpWindSpeed.toFloat()) }, "Wind m/s"
                    ).apply {
                        color = seriesColor(FilterSeries.HEAT_PUMP_BACKUP).toArgb()
                        setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
                        axisDependency = YAxis.AxisDependency.RIGHT
                        enableDashedLine(4f, 4f, 0f); valueTextColor = labelColor
                    })
                }
                // Left axis: SOC — auto-scales to actual data range (may be kWh, not %)
                chart.axisLeft.axisMinimum = 0f
                chart.axisLeft.resetAxisMaximum()
                // Right axis: water temperature fixed 0–100 °C
                chart.axisRight.axisMinimum = 0f
                chart.axisRight.axisMaximum = 100f
                chart.data = LineData(sets); chart.invalidate()
            }
        )
    }
}

// ─── Filter content ────────────────────────────────────────────────────────

@Composable
private fun FilterGroupContent(
    state: UI2GraphsViewModel.GraphState,
    viewModel: UI2GraphsViewModel
) {
    FilterGroup("Core", state.availableFilters.intersect(UI2GraphsViewModel.CORE_FILTERS), state, viewModel)
    if (state.hasBattery || state.hasBatteryData) {
        Text("Battery", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        FilterGroup(null, UI2GraphsViewModel.BATTERY_FILTERS, state, viewModel)
    }
    val sc = state.components
    val hasEV = sc?.scenario?.isHasEVCharges == true || sc?.scenario?.isHasEVDivert == true ||
            sc?.evCharges?.isNotEmpty() == true || sc?.evDiverts?.isNotEmpty() == true
    if (hasEV) {
        Text("EV", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        FilterGroup(null, UI2GraphsViewModel.EV_FILTERS, state, viewModel)
    }
    if (state.hasHW) {
        Text("Hot Water", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        FilterGroup(null, UI2GraphsViewModel.HW_FILTERS, state, viewModel)
    }
    if (state.hasHeatPump) {
        Text("Heat Pump", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        FilterGroup(null, UI2GraphsViewModel.HEAT_PUMP_FILTERS, state, viewModel)
    }
}

@Composable
private fun FilterGroup(
    title: String?,
    series: Set<FilterSeries>,
    state: UI2GraphsViewModel.GraphState,
    viewModel: UI2GraphsViewModel
) {
    if (title != null) {
        Text(title, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 4.dp))
    }
    series.forEach { s ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = s in state.activeFilters, onCheckedChange = { viewModel.toggleFilter(s) })
            Box(modifier = Modifier.size(10.dp)
                .background(seriesColor(s))
                .border(0.5.dp, MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(8.dp))
            Text(s.displayName, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ─── Date range picker dialog ──────────────────────────────────────────────

@Composable
private fun DateRangePickerDialog(
    state: UI2GraphsViewModel.GraphState,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val fmt = UI2GraphsViewModel.FMT
    val initFrom = runCatching { LocalDate.parse(state.from, fmt) }.getOrElse { LocalDate.now().minusMonths(1) }
    val initTo   = runCatching { LocalDate.parse(state.to,   fmt) }.getOrElse { LocalDate.now() }
    val dataStart = runCatching { LocalDate.parse(state.dataStartDate, fmt) }.getOrNull()
    val dataEnd   = runCatching { LocalDate.parse(state.dataEndDate,   fmt) }.getOrNull()

    val selectableDates = remember(state.dataStartDate, state.dataEndDate) {
        val startMs = dataStart?.let { it.toEpochDay() * 86_400_000L } ?: Long.MIN_VALUE
        val endMs   = dataEnd?.let   { it.toEpochDay() * 86_400_000L } ?: Long.MAX_VALUE
        val startYr = dataStart?.year ?: 0
        val endYr   = dataEnd?.year   ?: 9999
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in startMs..endMs
            override fun isSelectableYear(year: Int) = year in startYr..endYr
        }
    }

    // Land on today's month if it overlaps the data range; otherwise the last
    // month with data. Without this, M3 scrolls to the start of selection (or
    // the start of the year range) — five years of data deep is annoying.
    val displayMonth = remember(dataStart, dataEnd) {
        val today = LocalDate.now()
        when {
            dataStart != null && dataEnd != null &&
                !today.isBefore(dataStart.withDayOfMonth(1)) && !today.isAfter(dataEnd) -> today
            dataEnd != null -> dataEnd
            else -> today
        }.withDayOfMonth(1)
    }

    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initFrom.toEpochDay() * 86_400_000L,
        initialSelectedEndDateMillis   = initTo.toEpochDay()   * 86_400_000L,
        initialDisplayedMonthMillis    = displayMonth.toEpochDay() * 86_400_000L,
        selectableDates = selectableDates
    )

    val pickedStart = pickerState.selectedStartDateMillis
        ?.let { LocalDate.ofEpochDay(it / 86_400_000L) }
    val pickedEnd = pickerState.selectedEndDateMillis
        ?.let { LocalDate.ofEpochDay(it / 86_400_000L) }
    val okLabel = when {
        pickedStart != null && pickedEnd != null -> {
            val days = (pickedEnd.toEpochDay() - pickedStart.toEpochDay()).toInt() + 1
            "OK · $days day" + if (days == 1) "" else "s"
        }
        pickedStart != null -> "OK · 1 day"
        else -> "OK"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
                .padding(8.dp)
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(480.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = {
                        // Tap-one-date + OK is a single-day selection: M3's
                        // DateRangePicker won't let the user tap the same day
                        // twice to set start == end, so we synthesise it here.
                        when {
                            pickedStart != null && pickedEnd != null ->
                                onConfirm(pickedStart.format(fmt), pickedEnd.format(fmt))
                            pickedStart != null ->
                                onConfirm(pickedStart.format(fmt), pickedStart.format(fmt))
                            else -> onDismiss()
                        }
                    },
                    enabled = pickedStart != null
                ) { Text(okLabel) }
            }
        }
    }
}
