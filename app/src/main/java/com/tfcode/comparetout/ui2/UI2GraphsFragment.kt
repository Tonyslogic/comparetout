@file:OptIn(ExperimentalMaterial3Api::class)

package com.tfcode.comparetout.ui2

import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import java.util.Locale

// ─── Colors ────────────────────────────────────────────────────────────────

private val SERIES_COLORS: Map<FilterSeries, Color> = mapOf(
    FilterSeries.LOAD          to Color(0xFF2196F3),
    FilterSeries.FEED          to Color(0xFFFFEB3B),
    FilterSeries.BUY           to Color(0xFF00BCD4),
    FilterSeries.PV            to Color(0xFFF44336),
    FilterSeries.PV2BAT        to Color(0xFF4CAF50),
    FilterSeries.PV2LOAD       to Color(0xFF9E9E9E),
    FilterSeries.BAT2LOAD      to Color(0xFF005666),
    FilterSeries.GRID2BAT      to Color(0xFFE91E63),
    FilterSeries.EV_SCHEDULE   to Color(0xFFFFA500),
    FilterSeries.EV_DIVERT     to Color(0xFFE0F2D2),
    FilterSeries.HW_SCHEDULE   to Color(0xFF9C27B0),
    FilterSeries.HW_DIVERT     to Color(0xFF795548),
    FilterSeries.BAT2GRID      to Color(0xFFFFD700),
    FilterSeries.BAT_CHARGE    to Color(0xFF00FF00),
    FilterSeries.BAT_DISCHARGE to Color(0xFFCC6600)
)

private val DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

// ─── Fragment ──────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2GraphsFragment : Fragment() {

    private val viewModel: UI2GraphsViewModel by viewModels()
    private val sharedViewModel: UI2SharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { UI2Theme { GraphsScreen(viewModel, onBack = { findNavController().popBackStack() }) } }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
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
fun GraphsScreen(viewModel: UI2GraphsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var showPanel    by remember { mutableStateOf(false) }
    var showLinePop  by remember { mutableStateOf(false) }
    var showDatePick by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.scenarioName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = { }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // Main column
            Column(modifier = Modifier.fillMaxSize()) {
                DateNavRow(state, viewModel, onDateClick = { showDatePick = true })
                Box(modifier = Modifier.weight(1f)) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        ChartArea(state)
                    }
                }
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
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(290.dp)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize()) {
                    SettingsPanel(state, viewModel, onClose = { showPanel = false })
                }
            }

            // FABs — battery/water (conditional) to the left, settings always on the right
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.showLineFab) {
                    FloatingActionButton(onClick = { showLinePop = true }) {
                        Icon(painterResource(R.drawable.barchart), "SOC/Water Temp", Modifier.size(24.dp))
                    }
                }
                FloatingActionButton(onClick = { showPanel = true }) {
                    Icon(painterResource(R.drawable.ic_baseline_settings_24), "Chart settings", Modifier.size(24.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { viewModel.stepBack() }) { Text("◀", fontSize = 18.sp) }
        TextButton(onClick = onDateClick, modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.from == state.to) fromDisplay else "$fromDisplay – $toDisplay",
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
        IconButton(onClick = { viewModel.stepForward() }) { Text("▶", fontSize = 18.sp) }
    }
}

// ─── Settings panel ────────────────────────────────────────────────────────

@Composable
private fun SettingsPanel(
    state: UI2GraphsViewModel.GraphState,
    viewModel: UI2GraphsViewModel,
    onClose: () -> Unit
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

        // Graph type
        PanelSection("Graph Type") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GraphType.entries.forEach { type ->
                    FilterChip(
                        selected = state.graphType == type,
                        onClick  = { viewModel.setGraphType(type) },
                        label    = { Text(type.label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

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

// ─── Chart area dispatcher ─────────────────────────────────────────────────

@Composable
private fun ChartArea(state: UI2GraphsViewModel.GraphState) {
    when (state.graphType) {
        GraphType.BAR    -> BarChartView(state)
        GraphType.LINE   -> LineChartView(state)
        GraphType.PIE    -> PieChartView(state)
        GraphType.TABLE  -> TableView(state)
        GraphType.SANKEY -> SankeyView(state)
    }
}

// ─── Unified data point ─────────────────────────────────────────────────────

data class ChartPoint(val label: String, val values: Map<FilterSeries, Double>)

private fun buildChartPoints(state: UI2GraphsViewModel.GraphState): List<ChartPoint> {
    return if (state.isSingleDay && state.displayScale == DisplayScale.HOUR) {
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
                    FilterSeries.HW_SCHEDULE   to row.hwSchedule,
                    FilterSeries.HW_DIVERT     to row.hwDivert,
                    FilterSeries.BAT2GRID      to row.bat2grid,
                    FilterSeries.BAT_CHARGE    to 0.0,
                    FilterSeries.BAT_DISCHARGE to 0.0
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
                    FilterSeries.HW_SCHEDULE   to row.hwSchedule,
                    FilterSeries.HW_DIVERT     to row.hwDivert,
                    FilterSeries.BAT2GRID      to row.bat2grid,
                    FilterSeries.BAT_CHARGE    to row.batCharge,
                    FilterSeries.BAT_DISCHARGE to row.batDischarge
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
                    color = (SERIES_COLORS[series] ?: Color.Gray).toArgb()
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

// ─── LINE chart ────────────────────────────────────────────────────────────

@Composable
private fun LineChartView(state: UI2GraphsViewModel.GraphState) {
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
                LineDataSet(
                    points.mapIndexed { i, pt -> Entry(i.toFloat(), pt.values[series]?.toFloat() ?: 0f) },
                    series.displayName
                ).apply {
                    color = (SERIES_COLORS[series] ?: Color.Gray).toArgb()
                    setDrawCircles(false)
                    lineWidth = 1.5f
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    valueTextColor = labelColor
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

    var zoomedIdx by remember(specs) { mutableStateOf(-1) }

    if (zoomedIdx in specs.indices) {
        val spec = specs[zoomedIdx]
        val cfg = LocalConfiguration.current
        val pieSize = minOf(cfg.screenWidthDp, cfg.screenHeightDp).dp * 0.9f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .clickable { zoomedIdx = -1 }
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(spec.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            PieChart(slices = spec.slices, modifier = Modifier.size(pieSize))
            PieLegend(slices = spec.slices)
            Spacer(Modifier.height(8.dp))
            Text("Tap to return to grid", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    } else {
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

    // Shared horizontal scroll so header + rows stay in sync
    val hScroll = rememberScrollState()
    val colW = 72.dp

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky header
        Row(modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(hScroll)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(vertical = 4.dp)
        ) {
            Text("Interval", modifier = Modifier.width(88.dp).padding(horizontal = 4.dp),
                fontWeight = FontWeight.Bold, fontSize = 11.sp)
            activeSeries.forEach { s ->
                Text(s.displayName, modifier = Modifier.width(colW).padding(horizontal = 4.dp),
                    fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End,
                    maxLines = 2)
            }
        }
        HorizontalDivider()
        // Scrollable data rows (vertical scroll; horizontal synced via hScroll)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(points) { pt ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll)
                    .padding(vertical = 2.dp)
                ) {
                    Text(pt.label, modifier = Modifier.width(88.dp).padding(horizontal = 4.dp),
                        fontSize = 10.sp)
                    activeSeries.forEach { s ->
                        Text("%.2f".format(pt.values[s] ?: 0.0),
                            modifier = Modifier.width(colW).padding(horizontal = 4.dp),
                            fontSize = 10.sp, textAlign = TextAlign.End)
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
    val hasData = state.intervalData.isNotEmpty() ||
            (state.isSingleDay && state.displayScale == DisplayScale.HOUR && state.singleDayBarData.isNotEmpty())
    if (!hasData) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data") }
        return
    }

    var pv = 0.0; var feed = 0.0; var buy = 0.0
    var pv2bat = 0.0; var pv2load = 0.0; var bat2load = 0.0; var grid2bat = 0.0
    var evSchedule = 0.0; var evDivert = 0.0; var hwSchedule = 0.0; var hwDivert = 0.0; var bat2grid = 0.0

    if (state.isSingleDay && state.displayScale == DisplayScale.HOUR) {
        // Sum hourly bar data for a single day
        state.singleDayBarData.forEach { row ->
            pv += row.pv; feed += row.feed; buy += row.buy
            pv2bat += row.pv2Battery; pv2load += row.pv2Load; bat2load += row.battery2Load
            grid2bat += row.grid2Battery; evSchedule += row.evSchedule; evDivert += row.evDivert
            hwSchedule += row.hwSchedule; hwDivert += row.hwDivert; bat2grid += row.bat2grid
        }
    } else {
        state.intervalData.forEach { row ->
            pv += row.pv; feed += row.feed; buy += row.buy
            pv2bat += row.pv2bat; pv2load += row.pv2load; bat2load += row.bat2load
            grid2bat += row.grid2bat; evSchedule += row.evSchedule; evDivert += row.evDivert
            hwSchedule += row.hwSchedule; hwDivert += row.hwDivert; bat2grid += row.bat2grid
        }
    }

    val flows = buildList {
        if (pv2load > 0)    add(SankeyFlow("Solar",   "Load",      pv2load,    SERIES_COLORS[FilterSeries.PV]!!))
        if (pv2bat > 0)     add(SankeyFlow("Solar",   "Battery",   pv2bat,     SERIES_COLORS[FilterSeries.PV2BAT]!!))
        if (hwDivert > 0)   add(SankeyFlow("Solar",   "Hot Water", hwDivert,   SERIES_COLORS[FilterSeries.HW_DIVERT]!!))
        if (evDivert > 0)   add(SankeyFlow("Solar",   "EV",        evDivert,   SERIES_COLORS[FilterSeries.EV_DIVERT]!!))
        if (feed > 0)       add(SankeyFlow("Solar",   "Export",    feed,        SERIES_COLORS[FilterSeries.FEED]!!))
        val gridLoad = maxOf(0.0, buy - maxOf(0.0, bat2load - pv2bat))
        if (gridLoad > 0)   add(SankeyFlow("Grid",    "Load",      gridLoad,    SERIES_COLORS[FilterSeries.BUY]!!))
        if (grid2bat > 0)   add(SankeyFlow("Grid",    "Battery",   grid2bat,    SERIES_COLORS[FilterSeries.GRID2BAT]!!))
        if (hwSchedule > 0) add(SankeyFlow("Grid",    "Hot Water", hwSchedule,  SERIES_COLORS[FilterSeries.HW_SCHEDULE]!!))
        if (evSchedule > 0) add(SankeyFlow("Grid",    "EV",        evSchedule,  SERIES_COLORS[FilterSeries.EV_SCHEDULE]!!))
        if (bat2load > 0)   add(SankeyFlow("Battery", "Load",      bat2load,    SERIES_COLORS[FilterSeries.BAT2LOAD]!!))
        if (bat2grid > 0)   add(SankeyFlow("Battery", "Export",    bat2grid,    SERIES_COLORS[FilterSeries.BAT2GRID]!!))
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
                        color = (SERIES_COLORS[FilterSeries.BAT_CHARGE] ?: Color.Green).toArgb()
                        setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
                        axisDependency = YAxis.AxisDependency.LEFT; valueTextColor = labelColor
                    })
                }
                if (state.hasHW) {
                    sets.add(LineDataSet(
                        state.lineData.map { Entry(it.mod.toFloat(), it.waterTemperature.toFloat()) }, "Water Temp (°C)"
                    ).apply {
                        color = (SERIES_COLORS[FilterSeries.HW_SCHEDULE] ?: Color.Magenta).toArgb()
                        setDrawCircles(false); lineWidth = 2f; setDrawValues(false)
                        axisDependency = YAxis.AxisDependency.RIGHT
                        enableDashedLine(10f, 5f, 0f); valueTextColor = labelColor
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
    if (state.hasBattery) {
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
                .background(SERIES_COLORS[s] ?: Color.Gray)
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

    val selectableDates = remember(state.dataStartDate, state.dataEndDate) {
        val startMs = runCatching { LocalDate.parse(state.dataStartDate, fmt).toEpochDay() * 86_400_000L }
            .getOrElse { Long.MIN_VALUE }
        val endMs = runCatching { LocalDate.parse(state.dataEndDate, fmt).toEpochDay() * 86_400_000L }
            .getOrElse { Long.MAX_VALUE }
        val startYr = runCatching { LocalDate.parse(state.dataStartDate, fmt).year }.getOrElse { 0 }
        val endYr   = runCatching { LocalDate.parse(state.dataEndDate, fmt).year }.getOrElse { 9999 }
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in startMs..endMs
            override fun isSelectableYear(year: Int) = year in startYr..endYr
        }
    }

    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initFrom.toEpochDay() * 86_400_000L,
        initialSelectedEndDateMillis   = initTo.toEpochDay()   * 86_400_000L,
        selectableDates = selectableDates
    )

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
                TextButton(onClick = {
                    val f = pickerState.selectedStartDateMillis
                    val t = pickerState.selectedEndDateMillis
                    if (f != null && t != null) onConfirm(
                        LocalDate.ofEpochDay(f / 86_400_000L).format(fmt),
                        LocalDate.ofEpochDay(t / 86_400_000L).format(fmt)
                    ) else onDismiss()
                }) { Text("OK") }
            }
        }
    }
}
