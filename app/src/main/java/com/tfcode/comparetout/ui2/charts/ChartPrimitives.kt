/*
 * Copyright (c) 2026. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.ui2.charts

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.SimKPIs
import com.tfcode.comparetout.ui2.SeriesColors

/*
 * Cross-screen chart primitives shared by the Dashboard and Graphs tabs —
 * extracted verbatim from UI2DashboardFragment.kt (mega-refactor B1a).
 */

private fun styleDistBarChart(chart: BarChart, labelColor: Int, gridColor: Int) {
    chart.description.isEnabled = false
    chart.setDrawGridBackground(false)
    chart.setDrawBarShadow(false)
    chart.setFitBars(true)
    chart.setNoDataTextColor(labelColor)
    chart.setTouchEnabled(false)
    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.granularity = 1f
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.textColor = labelColor
    chart.xAxis.textSize = 9f
    chart.axisLeft.isEnabled = true
    chart.axisLeft.setLabelCount(2, true)   // shows actual min + max of the data range
    chart.axisLeft.setDrawGridLines(false)
    chart.axisLeft.textColor = labelColor
    chart.axisLeft.textSize = 8f
    chart.axisLeft.resetAxisMinimum()       // auto-scale to data, not forced to zero
    chart.axisLeft.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float) = "%.1f%%".format(value)
    }
    chart.axisRight.isEnabled = false
    chart.legend.isEnabled = false
    chart.setScaleEnabled(false)
}

internal fun stylePVBarChart(chart: BarChart, labelColor: Int, gridColor: Int) {
    chart.description.isEnabled = false
    chart.setDrawGridBackground(false)
    chart.setDrawBarShadow(false)
    chart.setFitBars(true)
    chart.setNoDataTextColor(labelColor)
    chart.setTouchEnabled(false)
    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.granularity = 1f
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.textColor = labelColor
    chart.xAxis.textSize = 9f
    chart.axisLeft.textColor = labelColor
    chart.axisLeft.gridColor = gridColor
    chart.axisLeft.axisMinimum = 0f
    chart.axisRight.isEnabled = false
    chart.legend.isEnabled = false
    chart.setScaleEnabled(false)
}

// ─── Load distribution charts ──────────────────────────────────────────────

@Composable
fun LoadDistributionCharts(lp: LoadProfile) {
    var zoomedIdx by remember { mutableIntStateOf(-1) }

    val hourlyDist  = remember(lp) { lp.hourlyDist?.dist ?: emptyList<Double>() }
    val dailyDist   = remember(lp) { lp.dowDist?.dowDist ?: emptyList<Double>() }
    val monthlyDist = remember(lp) { lp.monthlyDist?.monthlyDist ?: emptyList<Double>() }

    val hourLabels  = remember { (0..23).map { "%02d".format(it) } }
    val dayLabels   = stringArrayResource(R.array.ui2_days_short_sun_first).toList()
    val monthLabels = stringArrayResource(R.array.ui2_months_short).toList()

    val hourlyTitle  = stringResource(R.string.ui2_dash_hourly_pct)
    val dailyTitle   = stringResource(R.string.ui2_dash_daily_pct)
    val monthlyTitle = stringResource(R.string.ui2_dash_monthly_pct)
    val charts = remember(lp, hourlyTitle) {
        listOf(
            Triple(hourlyTitle,  hourlyDist,  hourLabels),
            Triple(dailyTitle,   dailyDist,   dayLabels),
            Triple(monthlyTitle, monthlyDist, monthLabels)
        )
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        charts.forEachIndexed { idx, (title, dist, labels) ->
            if (dist.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().clickable { zoomedIdx = idx }) {
                    Text("$title  ↗", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 2.dp))
                    SimpleDistBarChart(dist = dist, labels = labels,
                        modifier = Modifier.fillMaxWidth().height(80.dp))
                }
            }
        }
    }

    if (zoomedIdx >= 0) {
        val (title, dist, labels) = charts[zoomedIdx]
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 1.0f).toDp() }
        Dialog(onDismissRequest = { zoomedIdx = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SimpleDistBarChart(dist = dist, labels = labels,
                        modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SimpleDistBarChart(
    dist: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply { styleDistBarChart(this, labelColorArgb, gridColorArgb) }
        },
        update = { chart ->
            styleDistBarChart(chart, labelColorArgb, gridColorArgb)
            val entries = dist.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
            val ds = BarDataSet(entries, "").apply {
                color = barColorArgb
                setDrawValues(false)
            }
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    labels.getOrElse(value.toInt()) { "" }
            }
            chart.data = BarData(ds)
            chart.invalidate()
        },
        modifier = modifier
    )
}

// ─── PV string monthly bar charts (one per panel string) ──────────────────

@Composable
fun PVSummaryBarChart(panelSummary: List<PanelPVSummary>, panels: List<Panel>) {
    var zoomedIdx by remember { mutableIntStateOf(-1) }
    val monthLabels = stringArrayResource(R.array.ui2_months_short).toList()

    val grouped  = remember(panelSummary) { panelSummary.groupBy { it.panelID } }
    // Only show strings that belong to this scenario's panel configurations
    val scenarioPanelIds = remember(panels) { panels.map { it.panelIndex }.toSet() }
    val panelIds = remember(grouped, scenarioPanelIds) { grouped.keys.filter { it in scenarioPanelIds }.sorted() }

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    Text(stringResource(R.string.ui2_dash_pv_monthly), style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = 4.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        panelIds.forEachIndexed { idx, panelId ->
            val name     = panels.firstOrNull { it.panelIndex == panelId }?.panelName
                ?: stringResource(R.string.ui2_dash_panel_n, panelId)
            val monthMap = grouped[panelId]?.associate { (it.month.toIntOrNull() ?: 1) to it.tot } ?: emptyMap()
            val dist     = (1..12).map { m -> monthMap[m] ?: 0.0 }
            Column(modifier = Modifier.fillMaxWidth().clickable { zoomedIdx = idx }) {
                Text("$name  ↗", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 2.dp))
                PVStringBarChart(dist = dist, labels = monthLabels,
                    modifier = Modifier.fillMaxWidth().height(100.dp))
            }
        }
    }

    if (zoomedIdx >= 0) {
        val panelId  = panelIds[zoomedIdx]
        val name     = panels.firstOrNull { it.panelIndex == panelId }?.panelName
            ?: stringResource(R.string.ui2_dash_panel_n, panelId)
        val monthMap = grouped[panelId]?.associate { (it.month.toIntOrNull() ?: 1) to it.tot } ?: emptyMap()
        val dist     = (1..12).map { m -> monthMap[m] ?: 0.0 }
        val size     = with(density) { (minOf(containerSize.width, containerSize.height) * 1.0f).toDp() }
        Dialog(onDismissRequest = { zoomedIdx = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    PVStringBarChart(dist = dist, labels = monthLabels,
                        modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PVStringBarChart(
    dist: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = "#F44336".toColorInt()

    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply { stylePVBarChart(this, labelColorArgb, gridColorArgb) }
        },
        update = { chart ->
            stylePVBarChart(chart, labelColorArgb, gridColorArgb)
            val entries = dist.mapIndexed { i, v -> BarEntry((i + 1).toFloat(), v.toFloat()) }
            val ds = BarDataSet(entries, "").apply {
                color = barColorArgb
                setDrawValues(false)
            }
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) =
                    labels.getOrElse(value.toInt() - 1) { "" }
            }
            chart.xAxis.labelCount = 12
            chart.data = BarData(ds)
            chart.invalidate()
        },
        modifier = modifier
    )
}

// ─── Pie charts + expandable card ──────────────────────────────────────────

data class PieSlice(val label: String, val value: Double, val color: Color)

/** Pie titles/slice names resolved from resources in the composable and handed
 *  to the (non-composable) chart builders inside `remember` blocks. Reuses the
 *  ui2_graphs_* vocabulary so the dashboard pies match the Graphs tab. */
internal data class DashPieLabels(
    val selfConsumption: String, val loadSource: String, val solarDistribution: String,
    val batteryFlows: String, val loadDistribution: String, val importExport: String,
    val pvUsed: String, val exported: String, val solar: String, val battery: String,
    val grid: String, val toLoad: String, val toGrid: String, val solarIn: String,
    val gridIn: String, val ev: String, val hotWater: String, val house: String,
    val sold: String, val bought: String, val importW: String, val exportW: String,
    val charge: String, val discharge: String
)

@Composable
internal fun dashPieLabels() = DashPieLabels(
    selfConsumption   = stringResource(R.string.ui2_graphs_self_consumption),
    loadSource        = stringResource(R.string.ui2_graphs_load_source),
    solarDistribution = stringResource(R.string.ui2_graphs_solar_distribution),
    batteryFlows      = stringResource(R.string.ui2_graphs_battery_flows),
    loadDistribution  = stringResource(R.string.ui2_dash_load_distribution),
    importExport      = stringResource(R.string.ui2_dash_import_export),
    pvUsed            = stringResource(R.string.ui2_graphs_pv_used),
    exported          = stringResource(R.string.ui2_graphs_exported),
    solar             = stringResource(R.string.ui2_graphs_solar),
    battery           = stringResource(R.string.ui2_graphs_battery),
    grid              = stringResource(R.string.ui2_graphs_grid),
    toLoad            = stringResource(R.string.ui2_graphs_to_load),
    toGrid            = stringResource(R.string.ui2_graphs_to_grid),
    solarIn           = stringResource(R.string.ui2_graphs_solar_in),
    gridIn            = stringResource(R.string.ui2_graphs_grid_in),
    ev                = stringResource(R.string.ui2_graphs_ev),
    hotWater          = stringResource(R.string.ui2_graphs_hot_water),
    house             = stringResource(R.string.ui2_dash_house),
    sold              = stringResource(R.string.ui2_dash_sold),
    bought            = stringResource(R.string.ui2_dash_bought),
    importW           = stringResource(R.string.ui2_dash_import),
    exportW           = stringResource(R.string.ui2_graphs_export),
    charge            = stringResource(R.string.ui2_dash_charge),
    discharge         = stringResource(R.string.ui2_dash_discharge)
)

@Composable
fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier, isDonut: Boolean = false) {
    val holeColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val total = slices.sumOf { it.value }.toFloat()
        if (total <= 0f) return@Canvas
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.value.toFloat() / total) * 360f
            drawArc(color = slice.color, startAngle = startAngle, sweepAngle = sweep, useCenter = true)
            startAngle += sweep
        }
        if (isDonut) {
            drawCircle(color = holeColor, radius = size.minDimension * 0.245f)
        }
    }
}

@Composable
fun PieLegend(slices: List<PieSlice>) {
    Column(modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(modifier = Modifier.size(12.dp).background(slice.color))
                Spacer(Modifier.width(8.dp))
                Text("${slice.label}: ${"%.1f".format(slice.value)} kWh", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SimulationPieCharts(kpis: SimKPIs) {
    var zoomedChart by remember { mutableIntStateOf(-1) }

    val l = dashPieLabels()
    val charts = remember(kpis, l) {
        listOf(
            l.selfConsumption to listOf(
                PieSlice(l.pvUsed, maxOf(0.0, kpis.generated - kpis.sold), SeriesColors.solar),
                PieSlice(l.sold, kpis.sold, SeriesColors.export)
            ),
            l.loadSource to listOf(
                PieSlice(l.solar, maxOf(0.0, kpis.totalLoad - kpis.bought), SeriesColors.solar),
                PieSlice(l.bought, kpis.bought, SeriesColors.gridImport)
            ),
            l.solarDistribution to listOf(
                PieSlice(l.toLoad, kpis.pvToLoad, SeriesColors.house),
                PieSlice(l.battery, kpis.pvToCharge, SeriesColors.pvToBattery),
                PieSlice(l.ev, kpis.evDiv, SeriesColors.ev),
                PieSlice(l.hotWater, kpis.h2oDiv, SeriesColors.hotWater),
                PieSlice(l.sold, kpis.sold, SeriesColors.export)
            ),
            l.loadDistribution to listOf(
                PieSlice(l.house, kpis.house, SeriesColors.house),
                PieSlice(l.hotWater, kpis.h20, SeriesColors.hotWater),
                PieSlice(l.ev, kpis.ev, SeriesColors.ev)
            )
        )
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    Column(modifier = Modifier.padding(top = 4.dp)) {
        for (row in 0 until 2) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 2) {
                    val idx = row * 2 + col
                    val visible = charts[idx].second.filter { it.value > 0 }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { zoomedChart = idx }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            charts[idx].first,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        PieChart(slices = visible, modifier = Modifier.size(80.dp))
                    }
                }
            }
        }
    }

    if (zoomedChart >= 0) {
        val (title, slices) = charts[zoomedChart]
        val visible = slices.filter { it.value > 0 }
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 0.9f).toDp() }
        Dialog(onDismissRequest = { zoomedChart = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    PieChart(slices = visible, modifier = Modifier.size(size * 0.55f), isDonut = true)
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        PieLegend(slices = visible)
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableCard(
    title: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable (expanded: Boolean) -> Unit)? = null,
    showEdit: Boolean = true,
    initiallyExpanded: Boolean = false,
    onEdit: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            )
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (trailingContent != null) {
                    trailingContent(expanded)
                    Spacer(Modifier.width(4.dp))
                }
                if (expanded && showEdit && onEdit != null) {
                    Box(
                        modifier = Modifier
                            .clickable { onEdit() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.ui2_edit),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.ui2_collapse else R.string.ui2_expand)
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}
