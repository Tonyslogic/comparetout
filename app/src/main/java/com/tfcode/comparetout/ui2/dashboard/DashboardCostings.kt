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

package com.tfcode.comparetout.ui2.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.region.RegionProfiles
import com.tfcode.comparetout.ui2.AdaptiveLayout
import com.tfcode.comparetout.ui2.DataSourceCostingRow
import com.tfcode.comparetout.ui2.PeriodTotals
import com.tfcode.comparetout.ui2.PinnedScrollColumn
import com.tfcode.comparetout.ui2.PinnedScrollTable
import com.tfcode.comparetout.ui2.SeriesColors
import com.tfcode.comparetout.ui2.UsageDistribution
import com.tfcode.comparetout.ui2.charts.PieChart
import com.tfcode.comparetout.ui2.charts.PieLegend
import com.tfcode.comparetout.ui2.charts.PieSlice
import com.tfcode.comparetout.ui2.charts.SimpleDistBarChart
import com.tfcode.comparetout.ui2.charts.dashPieLabels
import com.tfcode.comparetout.ui2.charts.stylePVBarChart
import com.tfcode.comparetout.ui2.itemsPerRow
import java.text.DecimalFormat

/*
 * Dashboard costing tables + data-source explore charts — extracted verbatim
 * from UI2DashboardFragment.kt (mega-refactor B1c).
 */

private val TARIFF_PIE_COLORS = listOf(
    Color(0xFF304567), Color(0xFF2ECC71), Color(0xFFE74C3C), Color(0xFF9B59B6),
    Color(0xFF3498DB), Color(0xFFF39C12), Color(0xFF1ABC9C), Color(0xFF34495E),
    Color(0xFFE67E22), Color(0xFF27AE60)
)

/**
 * Costings table + tariff-band breakdown pie, shared by [AllCostingsTable] and
 * [DataSourceCostingsTable] (which differ only in row type and accessors).
 *
 * COMPACT: table is full width; tapping a row opens the band pie in a dialog
 * (unchanged behaviour). WIDE+: the table sits on the left and a permanent band
 * pie shows on the right, defaulting to the top (best) row and updating when
 * another row is tapped — the dialog stays as the COMPACT fallback.
 */
@Composable
private fun <R> CostingsTableWithBandPie(
    rows: List<R>,
    rowId: (R) -> Any,
    pinnedCell: @Composable (R) -> Unit,
    columns: List<PinnedScrollColumn<R>>,
    rowBackground: @Composable (R, Int) -> Color,
    planName: (R) -> String,
    bandSlices: (R) -> List<PieSlice>,
) {
    // Track selection by stable id, not row instance — the costings list is
    // re-created on each recomposition and Costings has no structural equality,
    // so keying on the instance would reset the selection every frame.
    var zoomedId by remember { mutableStateOf<Any?>(null) }
    var selectedId by remember { mutableStateOf<Any?>(null) }
    val selected = rows.firstOrNull { rowId(it) == selectedId } ?: rows.firstOrNull()
    val zoomed = rows.firstOrNull { rowId(it) == zoomedId }
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= AdaptiveLayout.WIDTH_WIDE_AT
        // Highlight the row whose tariff-band pie is currently on screen. WIDE:
        // the side pie is always visible and defaults to the top row, so
        // highlight `selected`. COMPACT: the pie is a dialog opened on tap, so
        // only highlight once a row has been tapped — we set selectedId on that
        // tap too, keeping the row highlighted while the dialog is up and after
        // it's dismissed.
        val highlightId: Any? = if (wide) selected?.let { rowId(it) } else selectedId
        val highlightColor = MaterialTheme.colorScheme.secondaryContainer
        val effectiveRowBackground: @Composable (R, Int) -> Color = { r, idx ->
            if (highlightId != null && rowId(r) == highlightId) highlightColor
            else rowBackground(r, idx)
        }
        val table: @Composable () -> Unit = {
            PinnedScrollTable(
                rows = rows,
                pinnedHeader = stringResource(R.string.ui2_cmp_plan),
                pinnedWeight = 2f,
                pinnedCell = pinnedCell,
                columns = columns,
                rowBackground = effectiveRowBackground,
                onRowClick = {
                    selectedId = rowId(it)
                    if (!wide) zoomedId = rowId(it)
                },
                footer = {
                    Text(
                        stringResource(if (wide) R.string.ui2_dash_tap_row_wide
                                       else R.string.ui2_dash_tap_row_compact),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.weight(1.6f)) { table() }
                Box(Modifier.weight(1f)) {
                    val r = selected
                    if (r != null) {
                        val slices = bandSlices(r)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(planName(r), style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            if (slices.isNotEmpty()) {
                                PieChart(slices = slices,
                                    modifier = Modifier.size(160.dp), isDonut = true)
                                Spacer(Modifier.height(8.dp))
                                PieLegend(slices = slices)
                            } else {
                                Text(stringResource(R.string.ui2_dash_no_bands),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            table()
        }
    }

    val z = zoomed
    if (z != null) {
        val slices = bandSlices(z)
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 0.9f).toDp() }
        Dialog(onDismissRequest = { zoomedId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(planName(z), style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    PieChart(slices = slices, modifier = Modifier.size(size * 0.55f), isDonut = true)
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        PieLegend(slices = slices)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AllCostingsTable(
    costings: List<Costings>,
    planStandingCharges: Map<Long, Double>,
    simDays: Long,
    df: DecimalFormat,
    favouritePlanId: Long? = null,
    planActive: Map<Long, Boolean> = emptyMap()
) {
    // Only active plans appear in the dashboard's Tariff Plan table — match
    // DataSourceCostingsTable. Plans missing from the map (pre-existing
    // costings rows for since-deleted plans) default to active so they don't
    // silently vanish.
    val visible = costings.filter { planActive[it.pricePlanID] ?: true }

    if (visible.isEmpty() && costings.isNotEmpty()) {
        Text(
            stringResource(R.string.ui2_dash_no_active_plans),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val firstId = visible.firstOrNull()?.pricePlanID
    val columns = listOf(
        PinnedScrollColumn<Costings>(
            header = stringResource(R.string.ui2_dash_net),
            accent = { it.pricePlanID == firstId },
            cell = { c ->
                Text(df.format(c.net / 100.0),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    color = if (c.pricePlanID == firstId)
                        MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
        ),
        PinnedScrollColumn<Costings>(header = stringResource(R.string.ui2_dash_buy), cell = { c ->
            Text(df.format(c.buy / 100.0),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<Costings>(header = stringResource(R.string.ui2_dash_sell), cell = { c ->
            Text(df.format(c.sell / 100.0),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<Costings>(header = stringResource(R.string.ui2_cmp_fixed), cell = { c ->
            val fixed = (planStandingCharges[c.pricePlanID] ?: 0.0) * (simDays / 365.0)
            Text(df.format(fixed),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        })
    )

    CostingsTableWithBandPie(
        rows = visible,
        rowId = { it.pricePlanID },
        pinnedCell = { c ->
            val isFav = favouritePlanId != null && favouritePlanId == c.pricePlanID
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFav) {
                    Icon(Icons.Default.Star,
                        contentDescription = stringResource(R.string.ui2_dash_current_plan_cd),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(c.fullPlanName ?: "",
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        },
        columns = columns,
        rowBackground = { c, idx ->
            val isFav = favouritePlanId != null && favouritePlanId == c.pricePlanID
            when {
                isFav -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                idx == 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> Color.Transparent
            }
        },
        planName = { it.fullPlanName ?: "" },
        bandSlices = { c ->
            val st = c.subTotals
            if (st == null) emptyList()
            else st.prices.sortedBy { it }.mapIndexed { i, price ->
                val kwh = st.getSubTotalForPrice(price) ?: 0.0
                // Region-aware minor unit ("c" / "p"), not a hardcoded cent.
                PieSlice("%.1f".format(price) + RegionProfiles.current.minorSymbol,
                    kwh, TARIFF_PIE_COLORS[i % TARIFF_PIE_COLORS.size])
            }.filter { it.value > 0 }
        }
    )
}

// ─── Data source PV bar chart (period-aware) ──────────────────────────────

@Composable
internal fun DataSourcePVBarChart(pvData: List<Pair<String, Double>>) {
    var zoomed by remember { mutableStateOf(false) }
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = "#F44336".toColorInt()
    val labels = remember(pvData) { pvData.map { it.first } }
    val values = remember(pvData) { pvData.map { it.second } }
    val total  = remember(pvData) { pvData.sumOf { it.second } }
    val kwhDf  = remember { DecimalFormat("#,##0.0") }

    Column(modifier = Modifier.fillMaxWidth().clickable { zoomed = true }) {
        Text(stringResource(R.string.ui2_dash_solar_total, kwhDf.format(total)) + "  ↗",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 2.dp))
        AndroidView(
            factory = { ctx -> BarChart(ctx).apply { stylePVBarChart(this, labelColorArgb, gridColorArgb) } },
            update = { chart ->
                stylePVBarChart(chart, labelColorArgb, gridColorArgb)
                val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
                val ds = BarDataSet(entries, "").apply { color = barColorArgb; setDrawValues(false) }
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float) = labels.getOrElse(v.toInt()) { "" }
                }
                chart.xAxis.labelCount = minOf(labels.size, 8)
                chart.data = BarData(ds); chart.invalidate()
            },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )
    }
    if (zoomed) {
        val size = with(density) { (minOf(containerSize.width, containerSize.height) * 1.0f).toDp() }
        Dialog(onDismissRequest = { zoomed = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.size(size), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(modifier = Modifier.padding(12.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.ui2_dash_solar_gen),
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    AndroidView(
                        factory = { ctx -> BarChart(ctx).apply { stylePVBarChart(this, labelColorArgb, gridColorArgb) } },
                        update = { chart ->
                            stylePVBarChart(chart, labelColorArgb, gridColorArgb)
                            val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
                            val ds = BarDataSet(entries, "").apply { color = barColorArgb; setDrawValues(false) }
                            chart.xAxis.valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(v: Float) = labels.getOrElse(v.toInt()) { "" }
                            }
                            chart.xAxis.labelCount = minOf(labels.size, 12)
                            chart.data = BarData(ds); chart.invalidate()
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}

// ─── Data source content ──────────────────────────────────────────────────
// The "D / M / Y / *  < >" selector lives in PeriodSelector.kt.

@Composable
internal fun DataSourceExplorePies(
    importerType: ComparisonUIViewModel.Importer,
    periodTotals: PeriodTotals?
) {
    if (periodTotals == null) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp), strokeWidth = 2.dp)
        return
    }

    val isEsbn = importerType == ComparisonUIViewModel.Importer.ESBNHDF ||
        importerType == ComparisonUIViewModel.Importer.OCTOPUS
    val l = dashPieLabels()
    val charts = remember(periodTotals, isEsbn, l) {
        if (isEsbn) {
            listOf(
                l.importExport to listOf(
                    PieSlice(l.importW, periodTotals.buy,  SeriesColors.gridImport),
                    PieSlice(l.exportW, periodTotals.feed, SeriesColors.export)
                )
            )
        } else {
            // Mirror UI2GraphsFragment.buildGraphPieSpecs — same four-pie set so the
            // dashboard's at-a-glance summary matches what the user sees when they
            // drill into Explore Data. For AlphaESS:
            //   • v1 (pre-migration) rows leave pv2*/bat2*/grid2* fields at 0, so the
            //     extra slices appear degenerate or empty. Once the Migrate worker
            //     stamps v2 they fill out fully.
            //   • Battery Flows is gated on actual battery activity in the data, not
            //     on a scenario flag — AlphaESS has no scenario, so we read it off
            //     PeriodTotals.charging/discharging.
            val gridToLoad = maxOf(0.0, periodTotals.buy - periodTotals.grid2bat)
            val pvUsed = maxOf(0.0, periodTotals.pv2load + periodTotals.pv2bat)
                .takeIf { it > 0 } ?: maxOf(0.0, periodTotals.pv - periodTotals.feed)
            val out = mutableListOf<Pair<String, List<PieSlice>>>()
            out += l.selfConsumption to listOf(
                PieSlice(l.pvUsed, pvUsed, SeriesColors.solar),
                PieSlice(l.exported, periodTotals.feed, SeriesColors.export)
            )
            out += l.loadSource to buildList {
                if (periodTotals.pv2load > 0) add(PieSlice(l.solar,   periodTotals.pv2load, SeriesColors.solar))
                if (periodTotals.bat2load > 0) add(PieSlice(l.battery, periodTotals.bat2load, SeriesColors.battery))
                if (gridToLoad > 0)            add(PieSlice(l.grid,    gridToLoad,           SeriesColors.gridImport))
                // Pre-v2 fallback: if no flow-decomposed slices, fall back to the
                // legacy approximation so the pie is never empty.
                if (isEmpty()) {
                    val approxSolar = maxOf(0.0, periodTotals.load - periodTotals.buy)
                    if (approxSolar > 0) add(PieSlice(l.solar,  approxSolar,        SeriesColors.solar))
                    if (periodTotals.buy > 0) add(PieSlice(l.grid,   periodTotals.buy,   SeriesColors.gridImport))
                }
            }
            if (periodTotals.pv > 0) {
                out += l.solarDistribution to buildList {
                    if (periodTotals.pv2load > 0)  add(PieSlice(l.toLoad,   periodTotals.pv2load, SeriesColors.house))
                    if (periodTotals.pv2bat > 0)   add(PieSlice(l.battery,  periodTotals.pv2bat,  SeriesColors.pvToBattery))
                    if (periodTotals.evActual > 0) add(PieSlice(l.ev,       periodTotals.evActual, SeriesColors.ev))
                    if (periodTotals.feed > 0)     add(PieSlice(l.exported, periodTotals.feed,    SeriesColors.export))
                }
            }
            val batteryActive = periodTotals.charging > 0 || periodTotals.discharging > 0 ||
                    periodTotals.pv2bat > 0 || periodTotals.grid2bat > 0 ||
                    periodTotals.bat2load > 0 || periodTotals.bat2grid > 0
            if (batteryActive) {
                val flows = buildList {
                    if (periodTotals.pv2bat > 0)   add(PieSlice(l.solarIn, periodTotals.pv2bat,   SeriesColors.pvToBattery))
                    if (periodTotals.grid2bat > 0) add(PieSlice(l.gridIn,  periodTotals.grid2bat, SeriesColors.gridToBattery))
                    if (periodTotals.bat2load > 0) add(PieSlice(l.toLoad,  periodTotals.bat2load, SeriesColors.battery))
                    if (periodTotals.bat2grid > 0) add(PieSlice(l.toGrid,  periodTotals.bat2grid, SeriesColors.batteryToGrid))
                    // Pre-v2 fallback: no flow decomposition yet, so show net battery
                    // throughput from the legacy charge/discharge columns. Migration
                    // upgrades this to the four-source breakdown above.
                    if (isEmpty()) {
                        if (periodTotals.charging > 0)    add(PieSlice(l.charge,    periodTotals.charging,    SeriesColors.batteryCharge))
                        if (periodTotals.discharging > 0) add(PieSlice(l.discharge, periodTotals.discharging, SeriesColors.batteryDischarge))
                    }
                }
                if (flows.isNotEmpty()) out += l.batteryFlows to flows
            }
            out
        }
    }

    var zoomedChart by remember { mutableIntStateOf(-1) }
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    // Wrap charts into a grid so 3 and 4 pies stay readable. Column count is
    // width-driven (central itemsPerRow): 2 on a phone, 3-4 on a tablet, so
    // landscape / large screens stop wasting half the row. Empty grid cells get
    // a Spacer to keep columns aligned.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        val cols = itemsPerRow(maxWidth, AdaptiveLayout.PIE_CELL_MIN_WIDTH)
            .coerceIn(1, charts.size)
        val rows = (charts.size + cols - 1) / cols
        Column(modifier = Modifier.fillMaxWidth()) {
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until cols) {
                        val idx = row * cols + col
                        if (idx < charts.size) {
                            val (title, slices) = charts[idx]
                            val visible = slices.filter { it.value > 0 }
                            Column(
                                modifier = Modifier.weight(1f).clickable { zoomedChart = idx }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(title, style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center)
                                Spacer(Modifier.height(4.dp))
                                PieChart(slices = visible, modifier = Modifier.size(80.dp))
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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
internal fun DataSourceCostingsTable(
    costings: List<DataSourceCostingRow>?,
    df: DecimalFormat,
    favouritePlanId: Long? = null
) {
    if (costings == null) {
        CircularProgressIndicator(modifier = Modifier.padding(4.dp).size(20.dp), strokeWidth = 2.dp)
        return
    }
    // Only active plans appear in the dashboard's Tariff Plan table — the
    // Compare tab still evaluates every plan regardless. Toggle via the
    // Supplier Plans screen (edit icon on this accordion).
    val visible = costings.filter { it.active }
    if (visible.isEmpty()) {
        Text(
            stringResource(if (costings.isEmpty()) R.string.ui2_dash_no_plans
                           else R.string.ui2_dash_no_active_plans),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val firstId = visible.firstOrNull()?.pricePlanId
    val columns = listOf(
        PinnedScrollColumn<DataSourceCostingRow>(
            header = stringResource(R.string.ui2_dash_net),
            accent = { it.pricePlanId == firstId },
            cell = { row ->
                Text(df.format(row.net / 100.0),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                    color = if (row.pricePlanId == firstId)
                        MaterialTheme.colorScheme.primary else Color.Unspecified)
            }
        ),
        PinnedScrollColumn<DataSourceCostingRow>(header = stringResource(R.string.ui2_dash_buy), cell = { row ->
            Text(df.format(row.buy / 100.0),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<DataSourceCostingRow>(header = stringResource(R.string.ui2_dash_sell), cell = { row ->
            Text(df.format(row.sell / 100.0),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<DataSourceCostingRow>(header = stringResource(R.string.ui2_cmp_fixed), cell = { row ->
            Text(df.format(row.fixed),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        })
    )

    CostingsTableWithBandPie(
        rows = visible,
        rowId = { it.pricePlanId },
        pinnedCell = { row ->
            val isFav = favouritePlanId != null && favouritePlanId == row.pricePlanId
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFav) {
                    Icon(Icons.Default.Star,
                        contentDescription = stringResource(R.string.ui2_dash_current_plan_cd),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                Text(row.planName,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        },
        columns = columns,
        rowBackground = { row, idx ->
            val isFav = favouritePlanId != null && favouritePlanId == row.pricePlanId
            when {
                isFav -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                idx == 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> Color.Transparent
            }
        },
        planName = { it.planName },
        bandSlices = { row ->
            val st = row.subTotals
            if (st == null) emptyList()
            else st.prices.sortedBy { it }.mapIndexed { i, price ->
                val kwh = st.getSubTotalForPrice(price) ?: 0.0
                // Region-aware minor unit ("c" / "p"), not a hardcoded cent.
                PieSlice("%.1f".format(price) + RegionProfiles.current.minorSymbol,
                    kwh, TARIFF_PIE_COLORS[i % TARIFF_PIE_COLORS.size])
            }.filter { it.value > 0 }
        }
    )
}

@Composable
internal fun PeriodTotalRow(label: String, value: Double, df: DecimalFormat) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("${df.format(value)} kWh", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DataSourceDistributionCharts(distribution: UsageDistribution) {
    var zoomedIdx by remember { mutableIntStateOf(-1) }

    val hourLabels  = remember { (0..23).map { "%02d".format(it) } }
    val dayLabels   = stringArrayResource(R.array.ui2_days_short_sun_first).toList()
    val monthLabels = stringArrayResource(R.array.ui2_months_short).toList()

    // ESBN has no load series, so its distributions fall back to grid import —
    // label them accordingly. Every other importer buckets actual load.
    val metric = stringResource(
        if (distribution.basedOnLoad) R.string.ui2_graphs_load else R.string.ui2_dash_import)
    val hourlyTitle  = stringResource(R.string.ui2_dash_hourly_pct)
    val dailyTitle   = stringResource(R.string.ui2_dash_daily_pct)
    val monthlyTitle = stringResource(R.string.ui2_dash_monthly_pct)
    val charts = remember(distribution, metric) {
        listOf(
            Triple("$metric $hourlyTitle",  distribution.hourly,   hourLabels),
            Triple("$metric $dailyTitle",   distribution.daily,    dayLabels),
            Triple("$metric $monthlyTitle", distribution.monthly,  monthLabels)
        )
    }

    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    // At MEDIUM+ widths render the three distributions side-by-side (Hourly /
    // Daily / Monthly) so landscape phones and tablets stop showing skinny
    // 80-dp bars stacked vertically.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val visible = charts.mapIndexedNotNull { idx, c ->
            if (c.second.any { it > 0 }) idx to c else null
        }
        if (visible.isEmpty()) return@BoxWithConstraints
        if (maxWidth >= AdaptiveLayout.WIDTH_MEDIUM_AT) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visible.forEach { (idx, c) ->
                    val (title, dist, labels) = c
                    Column(modifier = Modifier.weight(1f).clickable { zoomedIdx = idx }) {
                        Text("$title  ↗", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 2.dp))
                        SimpleDistBarChart(dist = dist, labels = labels,
                            modifier = Modifier.fillMaxWidth().height(80.dp))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { (idx, c) ->
                    val (title, dist, labels) = c
                    Column(modifier = Modifier.fillMaxWidth().clickable { zoomedIdx = idx }) {
                        Text("$title  ↗", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 2.dp))
                        SimpleDistBarChart(dist = dist, labels = labels,
                            modifier = Modifier.fillMaxWidth().height(80.dp))
                    }
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
