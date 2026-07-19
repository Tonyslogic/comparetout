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
 * Compare result panel, charts, tables + pure series/cost mappers — extracted verbatim from CompareScreen.kt (mega-refactor B7).
 * Imports inherited; unused are cosmetic.
 */

private val USAGE_IDS = UI2CompareViewModel.USAGE_SERIES.map { it.first }.toSet()

private fun seriesColor(id: String, primary: Color, isEnergy: Boolean): Color =
    compareSeriesColor(id, primary, isEnergy)

private val moneyFmt = DecimalFormat("#,##0.00")
private val kwhFmt = DecimalFormat("#,##0")

private val MONTH_FMT = DateTimeFormatter.ofPattern("MMM yyyy")
private val DAY_FMT = DateTimeFormatter.ofPattern("dd MMM yy")

// ──────────────────────────────────────────────────────────────────────────
// Result panel
// ──────────────────────────────────────────────────────────────────────────
@Composable
internal fun ResultPanel(
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
            .map { SeriesDef(it.first, stringResource(it.second), seriesColor(it.first, primary, isEnergy = !isCost)) }
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
                Text(stringResource(if (isCost) R.string.ui2_cmp_cost else R.string.ui2_cmp_usage),
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(6.dp))
                Text("· " + stringResource(state.mode.labelRes), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (rowsCount > 0) {
                    TextButton(onClick = onShare, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Default.Share, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_share),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (series.isEmpty()) {
                Text(stringResource(if (isCost) R.string.ui2_cmp_pick_cost_column
                                    else R.string.ui2_cmp_pick_energy_flow),
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
internal fun CostContent(
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
                add(stringResource(R.string.ui2_cmp_subject) to false)
                add(stringResource(R.string.ui2_cmp_plan) to false)
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
                unit = RegionProfiles.current.currencySymbol, onZoom = { zoomedPie = it })
        }
        else -> ChartArea(state, stringResource(R.string.ui2_cmp_cost), chartData, series,
            explanation, RegionProfiles.current.currencySymbol)
    }
    ResultLegends(state, series, chartData)

    zoomedPie?.let { d ->
        val hole = MaterialTheme.colorScheme.surfaceVariant
        ChartPopout(d.title, emptyList(), explanation,
            pieInfo = d to RegionProfiles.current.currencySymbol,
            onDismiss = { zoomedPie = null }) { h ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ComparePieCanvas(d.slices, hole, h)
            }
        }
    }
}

/** Renders the signed cost stack, merged into one chart or split per subject. */
@Composable
internal fun CostStackArea(
    layout: CompareLayout, bars: List<CostBar>, series: List<SeriesDef>, explanation: String?
) {
    if (layout == CompareLayout.SPLIT && bars.size > 1) {
        // Share the signed y-axis (positive + negative extent) across panels.
        val (sharedPos, sharedNeg) = compareCostStackExtents(bars)
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
                                ZoomableChart(bar.title, series.map { it.color to it.label }, explanation) { h ->
                                    CompareCostStackChart(
                                        listOf(bar), RegionProfiles.current.currencySymbol, h,
                                        posExtent = sharedPos, negExtent = sharedNeg
                                    )
                                }
                            }
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        ZoomableChart(stringResource(R.string.ui2_cmp_cost),
            series.map { it.color to it.label }, explanation) { h ->
            CompareCostStackChart(bars, RegionProfiles.current.currencySymbol, h)
        }
    }
}

@Composable
internal fun UsageContent(
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
                add(stringResource(R.string.ui2_cmp_subject) to false)
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
        else -> ChartArea(state, stringResource(R.string.ui2_cmp_usage),
            chartData, series, explanation, "kWh")
    }
    ResultLegends(state, series, chartData)

    zoomedPie?.let { d ->
        val hole = MaterialTheme.colorScheme.surfaceVariant
        ChartPopout(d.title, emptyList(), explanation, pieInfo = d to "kWh",
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
@Composable
internal fun graphExplanationText(
    mode: CompareMode,
    metric: CompareWhat,
    series: List<SeriesDef>,
    data: List<ChartDatum>
): String {
    val unit = if (metric == CompareWhat.COST) {
        val p = RegionProfiles.current
        stringResource(if (p.currencySymbol == "£") R.string.ui2_cmp_unit_sterling
                       else R.string.ui2_cmp_unit_euro, p.currencySymbol)
    } else stringResource(R.string.ui2_cmp_unit_energy)
    val seriesNames = if (series.isEmpty()) stringResource(R.string.ui2_cmp_the_selected_series)
        else series.joinToString(", ") { it.label }
    val firstSeries = series.firstOrNull()?.label
        ?: stringResource(R.string.ui2_cmp_the_first_series)
    val subjects = when {
        data.isEmpty() -> stringResource(R.string.ui2_cmp_the_selected_subjects)
        data.size <= 5 -> data.joinToString("; ") { it.title }
        else -> data.take(4).joinToString("; ") { it.title } +
            stringResource(R.string.ui2_cmp_subjects_more, data.size)
    }
    return when (mode) {
        CompareMode.TABLE ->
            stringResource(R.string.ui2_cmp_expl_table, seriesNames, unit, subjects)
        CompareMode.BAR ->
            stringResource(R.string.ui2_cmp_expl_bar, unit, seriesNames, subjects)
        CompareMode.STACK ->
            if (metric == CompareWhat.COST)
                stringResource(R.string.ui2_cmp_expl_stack_cost, unit, subjects)
            else
                stringResource(R.string.ui2_cmp_expl_stack_usage, seriesNames, unit, subjects)
        CompareMode.LINE ->
            stringResource(R.string.ui2_cmp_expl_line, firstSeries, unit, subjects)
        CompareMode.AREA ->
            stringResource(R.string.ui2_cmp_expl_area, firstSeries, unit, subjects)
        CompareMode.PIE ->
            stringResource(R.string.ui2_cmp_expl_pie, unit, seriesNames, subjects)
    }
}

/** Inline novice caption card, shown above the graph in the result panel. */
@Composable
internal fun GraphExplanation(text: String?) {
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

/**
 * Legend beneath the result panel — driven by the same colour encoding the
 * chart uses, so a swatch can never disagree with the line/bar it labels.
 */
@Composable
internal fun ResultLegends(state: CompareState, series: List<SeriesDef>, data: List<ChartDatum>) {
    if (state.mode == CompareMode.TABLE) return
    val entries = resultLegendEntries(state, series, data)
    if (entries.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    CompareEntryLegend(entries)
}

/**
 * The (colour, label) legend entries that match what the panel actually draws:
 *  - TABLE          → none
 *  - PIE / STACK    → series (slices / stack bands are series-coloured)
 *  - split layout   → series (each chart is one subject, series-coloured)
 *  - single subject → series
 *  - merged BAR totals (not bucketed) → series
 *  - merged LINE / AREA / bucketed BAR → subject or subject×series (blend)
 */
internal fun resultLegendEntries(
    state: CompareState, series: List<SeriesDef>, data: List<ChartDatum>
): List<Pair<Color, String>> {
    val seriesEntries = series.map { it.color to it.label }
    val perSubjectCharts = state.layout == CompareLayout.SPLIT && data.size > 1
    return when (state.mode) {
        CompareMode.TABLE -> emptyList()
        CompareMode.PIE, CompareMode.STACK -> seriesEntries
        CompareMode.BAR -> when {
            perSubjectCharts || data.size <= 1 -> seriesEntries
            barIsBucketed(data, series) -> compareLegendEntries(data, series)
            else -> seriesEntries   // one totals bar per subject — series-coloured
        }
        CompareMode.LINE, CompareMode.AREA -> when {
            perSubjectCharts || data.size <= 1 -> seriesEntries
            else -> compareLegendEntries(data, series)
        }
    }
}

@Composable
internal fun ChartArea(
    state: CompareState, metricLabel: String,
    data: List<ChartDatum>, series: List<SeriesDef>, explanation: String?, unit: String
) {
    // In SPLIT layout, every panel must share the same y-axis (both ceiling
    // and floor) so a taller subject — or a credit-bearing one — can be
    // compared at a glance. Compute once across the full data set; charts
    // apply niceCeil themselves.
    val splitting = state.layout == CompareLayout.SPLIT && data.size > 1
    val sharedBarRange = if (splitting && state.mode == CompareMode.BAR)
        compareBarYRange(data, series) else null
    val sharedLineRange = if (splitting && (state.mode == CompareMode.LINE || state.mode == CompareMode.AREA))
        compareLineYRange(data, series) else null
    val sharedStackMax = if (splitting && state.mode == CompareMode.STACK)
        compareStackYMax(data, series) else null
    val render: @Composable (List<ChartDatum>, Dp) -> Unit = { d, h ->
        when (state.mode) {
            CompareMode.BAR -> CompareBarChart(
                d, series, unit, h,
                yMax = sharedBarRange?.first, yMin = sharedBarRange?.second
            )
            CompareMode.STACK -> CompareStackChart(d, series, unit, h, yMax = sharedStackMax)
            CompareMode.LINE -> CompareLineChart(
                d, series, area = false, unit = unit, height = h,
                yMax = sharedLineRange?.first, yMin = sharedLineRange?.second
            )
            CompareMode.AREA -> CompareLineChart(
                d, series, area = true, unit = unit, height = h,
                yMax = sharedLineRange?.first, yMin = sharedLineRange?.second
            )
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
                                ZoomableChart(d.title, series.map { it.color to it.label }, explanation) { h ->
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
        ZoomableChart(metricLabel, resultLegendEntries(state, series, data), explanation) { h ->
            render(data, h)
        }
    }
}

/** A chart that opens an orientation-aware pop-out when tapped. */
@Composable
internal fun ZoomableChart(
    title: String,
    legend: List<Pair<Color, String>>,
    explanation: String?,
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
        ChartPopout(title, legend, explanation,
            onDismiss = { zoomed = false }, chart = chart)
    }
}

/**
 * Graph pop-out: full screen width × 80% height, click outside to dismiss.
 * Landscape — graph left, text + legend right. Portrait — text on top, then the
 * graph, then the legend. Scrolls vertically when content overflows.
 */
@Composable
internal fun ChartPopout(
    title: String,
    legend: List<Pair<Color, String>>,
    explanation: String?,
    onDismiss: () -> Unit,
    pieInfo: Pair<ComparePieDatum, String>? = null,
    chart: @Composable (Dp) -> Unit
) {
    val cfg = LocalConfiguration.current
    val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val popW = with(density) { containerSize.width.toDp() }
    val popH = with(density) { (containerSize.height * 0.8f).toDp() }
    // At ULTRA (large tablet) give the legend more breathing room without
    // shrinking the chart on small landscape phones.
    val ultra = popW >= AdaptiveLayout.WIDTH_ULTRA_AT
    val chartWeight = if (ultra) 2f else 1.3f
    val legendWeight = if (ultra) 1.2f else 1f
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
                        stringResource(R.string.ui2_cmp_total_fmt, axisNumber(datum.total), unit),
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
                    CompareEntryLegend(legend)
                }
            }

            if (landscape) {
                Row(Modifier.fillMaxSize().padding(16.dp)) {
                    Box(
                        modifier = Modifier.weight(chartWeight).fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        chart((popH.value * 0.74f).dp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(legendWeight).fillMaxHeight()
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
                        CompareEntryLegend(legend)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyResult() {
    Text(stringResource(R.string.ui2_cmp_no_data_result),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ── result table ────────────────────────────────────────────────────────────
internal data class Cell(val text: String, val sort: Double, val color: Color? = null)

@Composable
internal fun ResultTable(
    headers: List<Pair<String, Boolean>>,
    rows: List<List<Cell>>,
    defaultSort: Int
) {
    if (headers.isEmpty()) return
    var sortCol by remember { mutableIntStateOf(defaultSort) }
    var ascending by remember { mutableStateOf(true) }
    val sorted = remember(rows, sortCol, ascending) {
        val numeric = headers.getOrNull(sortCol)?.second ?: true
        val cmp = Comparator<List<Cell>> { a, b ->
            if (numeric) a[sortCol].sort.compareTo(b[sortCol].sort)
            else a[sortCol].text.compareTo(b[sortCol].text)
        }
        rows.sortedWith(if (ascending) cmp else cmp.reversed())
    }

    fun headerSlot(index: Int): @Composable () -> Unit = {
        val selected = index == sortCol
        val label = headers[index].first
        Row(
            Modifier.clickable {
                if (sortCol == index) ascending = !ascending
                else { sortCol = index; ascending = index == 0 }
            },
            horizontalArrangement = if (index == 0) Arrangement.Start else Arrangement.End,
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

    val scrollColumns = (1 until headers.size).map { i ->
        PinnedScrollColumn<List<Cell>>(
            header = headers[i].first,
            align = TextAlign.End,
            minWidth = AdaptiveLayout.SCROLL_COL_MIN_NUMERIC,
            headerSlot = headerSlot(i),
            cell = { row ->
                val cell = row[i]
                Text(
                    cell.text,
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = cell.color ?: MaterialTheme.colorScheme.onSurface
                )
            }
        )
    }

    PinnedScrollTable(
        rows = sorted,
        pinnedHeader = headers[0].first,
        pinnedWeight = 1.6f,
        pinnedHeaderSlot = headerSlot(0),
        pinnedCell = { row ->
            val cell = row[0]
            Text(
                cell.text,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = cell.color ?: MaterialTheme.colorScheme.onSurface
            )
        },
        columns = scrollColumns
    )
}

@Composable
internal fun NotReadyCard(state: CompareState) {
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
            Text(stringResource(R.string.ui2_cmp_not_ready_title),
                style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(if (state.what == CompareWhat.USAGE)
                    R.string.ui2_cmp_not_ready_usage else R.string.ui2_cmp_not_ready_cost),
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
@Composable
internal fun whatLabel(w: CompareWhat) = stringResource(when (w) {
    CompareWhat.COST -> R.string.ui2_cmp_cost
    CompareWhat.USAGE -> R.string.ui2_cmp_usage
    CompareWhat.BOTH -> R.string.ui2_cmp_cost_usage
})

internal fun subjectsReady(s: CompareState): Boolean {
    val hasSubjects = s.sources.isNotEmpty() || s.sims.isNotEmpty()
    return hasSubjects && (s.what == CompareWhat.USAGE || s.plans.isNotEmpty())
}

@Composable
internal fun scopeSubtitle(s: CompareState): String {
    if (s.sources.isEmpty() && s.sims.isEmpty() && s.plans.isEmpty())
        return stringResource(R.string.ui2_cmp_scope_tap)
    return buildList {
        add(pluralStringResource(R.plurals.ui2_cmp_n_sources, s.sources.size, s.sources.size))
        add(pluralStringResource(R.plurals.ui2_cmp_n_sims, s.sims.size, s.sims.size))
        if (s.what != CompareWhat.USAGE)
            add(pluralStringResource(R.plurals.ui2_cmp_n_plans, s.plans.size, s.plans.size))
    }.joinToString(" · ")
}

@Composable
internal fun filterSubtitle(s: CompareState): String =
    if (s.series.isEmpty()) stringResource(R.string.ui2_cmp_filter_tap)
    else stringResource(R.string.ui2_cmp_series_selected, s.series.size)

@Composable
internal fun timeSubtitle(s: CompareState): String {
    val mode = stringResource(if (s.advanced) R.string.ui2_cmp_advanced_word
                              else R.string.ui2_cmp_basic_word)
    if (s.sync) {
        val g = s.globalGran ?: return stringResource(R.string.ui2_cmp_time_tap)
        return stringResource(R.string.ui2_cmp_time_synced, granWord(g, s.globalAnchor), mode)
    }
    val n = s.perSubjectRanges.count { it.value.gran != null }
    return if (n == 0) stringResource(R.string.ui2_cmp_time_tap_subjects)
    else pluralStringResource(R.plurals.ui2_cmp_per_subject, n, n, mode)
}

@Composable
internal fun granWord(gran: DataSourcePeriod, anchor: LocalDate) = when (gran) {
    DataSourcePeriod.ALL -> stringResource(R.string.ui2_cmp_all_time_word)
    DataSourcePeriod.YEAR -> stringResource(R.string.ui2_cmp_yearly, anchor.year.toString())
    DataSourcePeriod.MONTH -> stringResource(R.string.ui2_cmp_monthly, anchor.format(MONTH_FMT))
    DataSourcePeriod.YESTERDAY -> stringResource(R.string.ui2_cmp_daily, anchor.format(DAY_FMT))
}

@Composable
internal fun displaySubtitle(s: CompareState): String {
    val layoutable = s.mode in listOf(CompareMode.BAR, CompareMode.STACK, CompareMode.LINE, CompareMode.AREA)
    val axisable = s.mode == CompareMode.LINE || s.mode == CompareMode.AREA || s.mode == CompareMode.BAR
    return buildList {
        add(stringResource(s.mode.labelRes))
        if (layoutable) add(stringResource(
            if (s.layout == CompareLayout.MERGED) R.string.ui2_cmp_merged_word
            else R.string.ui2_cmp_split_word))
        if (axisable) add(stringResource(R.string.ui2_cmp_axis_word,
            stringResource(s.displayScale.labelRes).lowercase()))
    }.joinToString(" · ")
}

internal fun isConfigReady(s: CompareState, vm: UI2CompareViewModel): Boolean {
    val hasSubjects = s.sources.isNotEmpty() || s.sims.isNotEmpty()
    val hasPlans = s.what == CompareWhat.USAGE || s.plans.isNotEmpty()
    return hasSubjects && hasPlans && s.series.isNotEmpty() && vm.timeframeReady(s)
}

internal fun selectedCostSeries(s: CompareState): List<String> = when (s.what) {
    CompareWhat.BOTH -> UI2CompareViewModel.COST_SERIES.map { it.first }.filter { "c_$it" in s.series }
    else -> UI2CompareViewModel.COST_SERIES.map { it.first }.filter { it in s.series }
}

internal fun selectedUsageSeries(s: CompareState): List<String> =
    UI2CompareViewModel.USAGE_SERIES.map { it.first }.filter { it in s.series && it in USAGE_IDS }

internal fun netColor(v: Double): Color =
    if (v < 0) Color(0xFF4CAF50) else if (v > 0) Color(0xFFE53935) else Color.Unspecified

internal fun costValue(r: CompareCostRow, id: String): Double = when (id) {
    "net" -> r.net; "buy" -> r.buy; "sell" -> r.sell
    "bonus" -> r.bonus; "fixed" -> r.fixed; else -> 0.0
}

internal fun usageValue(r: CompareUsageRow, id: String): Double = when (id) {
    "load" -> r.load; "buy" -> r.buy; "feed" -> r.feed; "pv" -> r.pv
    "pv2load" -> r.pv2load; "bat2load" -> r.bat2load; "grid2bat" -> r.grid2bat
    "charge" -> r.charge; "discharge" -> r.discharge
    "evSchedule" -> r.evSchedule; "evDivert" -> r.evDivert
    "hwSchedule" -> r.hwSchedule; "hwDivert" -> r.hwDivert
    "heatPump" -> r.heatPump; "heatPumpBackup" -> r.heatPumpBackup
    "heatPumpHeat" -> r.heatPumpHeat
    else -> 0.0
}

// A cost datum is one source/simulation × one plan, so the axis label must
// carry BOTH — otherwise one source across N plans gives N look-alike bars.
internal fun costAxisLabel(r: CompareCostRow): String {
    val supplier = r.planName.substringBefore(" · ")
    return "${shorten(r.subjectName)}\n${shorten(supplier)}"
}

internal fun costData(rows: List<CompareCostRow>): List<ChartDatum> = rows.map { r ->
    ChartDatum(
        title = "${r.subjectName}  ·  ${r.planName}",   // full — for legends/pop-outs
        shortLabel = costAxisLabel(r),                  // two-line axis label
        values = mapOf("net" to r.net, "buy" to r.buy, "sell" to r.sell,
            "bonus" to r.bonus, "fixed" to r.fixed),
        axisLabels = r.timeline.axisLabels,
        seriesValues = r.timeline.seriesValues          // keys: net/buy/sell/fixed/bonus
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
internal fun bandPattern(i: Int): BandPattern = BAND_PATTERNS[i % BAND_PATTERNS.size]

/** Build signed cost bars: buy rate bands + fixed (positive), sell (negative). */
internal fun costBars(rows: List<CompareCostRow>, series: List<SeriesDef>): List<CostBar> {
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

internal fun usageData(rows: List<CompareUsageRow>): List<ChartDatum> = rows.map { r ->
    ChartDatum(
        title = r.subjectName,
        shortLabel = shorten(r.subjectName),
        values = mapOf("load" to r.load, "buy" to r.buy, "feed" to r.feed, "pv" to r.pv,
            "pv2load" to r.pv2load, "bat2load" to r.bat2load, "grid2bat" to r.grid2bat,
            "charge" to r.charge, "discharge" to r.discharge,
            "evSchedule" to r.evSchedule, "evDivert" to r.evDivert,
            "hwSchedule" to r.hwSchedule, "hwDivert" to r.hwDivert,
            "heatPump" to r.heatPump, "heatPumpBackup" to r.heatPumpBackup,
            "heatPumpHeat" to r.heatPumpHeat,
            "evActual" to r.evActual, "hwActual" to r.hwActual, "hpActual" to r.hpActual),
        axisLabels = r.timeline.axisLabels,
        seriesValues = r.timeline.seriesValues
    )
}

internal fun shorten(s: String): String = if (s.length <= 12) s else s.take(11) + "…"

/**
 * Build pie data for usage — one pie per subject, slices are the selected series
 * each in its own colour. No hatching needed (each series already has a distinct hue).
 */
internal fun usagePies(data: List<ChartDatum>, series: List<SeriesDef>): List<ComparePieDatum> =
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
@Composable
internal fun costPies(rows: List<CompareCostRow>, series: List<SeriesDef>): List<ComparePieDatum> {
    val showBuy = series.any { it.id == "buy" }
    val showFixed = series.any { it.id == "fixed" }
    val showBonus = series.any { it.id == "bonus" }
    val fixedLabel = stringResource(R.string.ui2_cmp_fixed)
    val bonusLabel = stringResource(R.string.ui2_cmp_bonus)
    val bandLabel: @Composable (Int) -> String = { i ->
        stringResource(R.string.ui2_cmp_band_n, i + 1)
    }
    return rows.map { r ->
        val slices = buildList {
            if (showBuy) r.buyBands.forEachIndexed { i, b ->
                if (b > 0.0) {
                    // Show the actual c/kWh rate instead of "Buy band N" — far more
                    // useful in the pop-out legend where the user is comparing tariffs.
                    val rate = r.buyBandRates.getOrNull(i)
                    val label = if (rate != null)
                        "${rateLabel(rate)} ${RegionProfiles.current.rateUnit}" else bandLabel(i)
                    add(ComparePieSlice(label, BUY_BAND_COLOR, b, bandPattern(i)))
                }
            }
            if (showFixed && r.fixed > 0.0) add(ComparePieSlice(fixedLabel, FIXED_BAND_COLOR, r.fixed))
            if (showBonus && r.bonus > 0.0) add(ComparePieSlice(bonusLabel, Color(0xFF4CAF50), r.bonus))
        }
        ComparePieDatum("${r.subjectName}  ·  ${r.planName}", slices)
    }
}

/** Format a c/kWh rate compactly: integer cents stay integer, others get 2 dp. */
internal fun rateLabel(c: Double): String =
    if (c == c.toInt().toDouble()) c.toInt().toString() else "%.2f".format(c)


