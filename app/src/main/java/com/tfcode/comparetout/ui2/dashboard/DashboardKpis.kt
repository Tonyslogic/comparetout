package com.tfcode.comparetout.ui2.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfiles
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.SimKPIs
import com.tfcode.comparetout.ui2.charts.ExpandableCard
import com.tfcode.comparetout.ui2.charts.LoadDistributionCharts
import com.tfcode.comparetout.ui2.charts.PVSummaryBarChart
import com.tfcode.comparetout.ui2.charts.PieChart
import com.tfcode.comparetout.ui2.charts.PieLegend
import com.tfcode.comparetout.ui2.charts.PieSlice
import com.tfcode.comparetout.ui2.charts.SimulationPieCharts
import com.tfcode.comparetout.ui2.charts.SimpleDistBarChart
import com.tfcode.comparetout.ui2.charts.dashPieLabels
import com.tfcode.comparetout.ui2.charts.stylePVBarChart
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.time.LocalDate
import com.tfcode.comparetout.ui2.KpiSummary
import com.tfcode.comparetout.ui2.KpiMonthRow
import com.tfcode.comparetout.ui2.DataSourcePeriod
import com.tfcode.comparetout.ui2.PeriodSelector
import com.tfcode.comparetout.ui2.AdaptiveChipRow
import com.tfcode.comparetout.ui2.AdaptiveLayout
import com.tfcode.comparetout.ui2.PinnedScrollColumn
import com.tfcode.comparetout.ui2.PinnedScrollTable

/*
 * Dashboard KPI accordion + tables — extracted verbatim from
 * UI2DashboardFragment.kt (mega-refactor B1e). Imports inherited; unused
 * entries are cosmetic only.
 */

// ──────────────────────────────────────────────────────────────────────────
// KPI accordion — same data set the legacy ImportKeyStatsFragment shows
// (self-consumption / sufficiency / max-self-sufficiency, PV total, feed total
// + per-month best/worst/average). The range picker drives the summary;
// the 13-button * J F M A M J J A S O N D row filters only the monthly
// best/worst/avg table — so it sits between the two tables, not next to
// the date picker.
// ──────────────────────────────────────────────────────────────────────────
@Composable
internal fun KpiAccordion(
    period: DataSourcePeriod,
    anchor: LocalDate,
    monthFilter: Int,
    summary: KpiSummary?,
    months: List<KpiMonthRow>?,
    bounds: Pair<LocalDate, LocalDate>?,
    onPeriodChange: (DataSourcePeriod, LocalDate) -> Unit,
    onNavigate: (forward: Boolean) -> Unit,
    onMonthFilterChange: (Int) -> Unit,
    df: DecimalFormat,
    kwhDf: DecimalFormat,
    showHints: Boolean
) {
    ExpandableCard(
        title = stringResource(R.string.ui2_dash_kpis),
        leadingIcon = {
            Icon(painterResource(R.drawable.barchart), null, Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface)
        },
        trailingContent = { _ ->
            if (summary != null)
                Icon(painterResource(R.drawable.tick), null, Modifier.size(18.dp), tint = Color.Unspecified)
        },
        showEdit = false
    ) {
        // Use the simulation / source's actual data bounds so the chevron
        // navigation stops at the real data edges and the picker doesn't show
        // anchors decades before any data exists (the old 1976 issue).
        val cosmeticStart = (bounds?.first ?: anchor).toString()
        val cosmeticEnd   = (bounds?.second ?: anchor).toString()
        PeriodSelector(
            selectedPeriod = period,
            anchorDate     = anchor,
            dataStart      = cosmeticStart,
            dataEnd        = cosmeticEnd,
            advanced       = false,
            onPeriodChange = { p, a, _ -> onPeriodChange(p, a) },
            onNavigate     = { fwd, _ -> onNavigate(fwd) }
        )
        Spacer(Modifier.height(8.dp))

        // KPI summary table — driven by the date picker above.
        when (summary) {
            null -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else -> KpiSummaryTable(summary, df, kwhDf, showHints)
        }

        // Month filter sits directly above the per-month table it controls.
        Spacer(Modifier.height(10.dp))
        MonthFilterRow(monthFilter, onMonthFilterChange)
        Spacer(Modifier.height(8.dp))

        // Monthly key stats — filtered by the chip row above.
        when (months) {
            null -> {}
            else -> {
                val filtered = if (monthFilter == 0) months
                               else months.filter { it.monthNumber == monthFilter }
                KpiMonthsTable(filtered, kwhDf)
            }
        }
    }
}

@Composable
private fun MonthFilterRow(selected: Int, onChange: (Int) -> Unit) {
    val labelsShort = listOf("*") + stringArrayResource(R.array.ui2_months_letters)
    val labelsLong  = listOf(stringResource(R.string.ui2_all)) +
        stringArrayResource(R.array.ui2_months_short)
    // AdaptiveChipRow keeps all 13 on one weighted line at normal font, wraps
    // to multiple rows as the font enlarges, and collapses to a dropdown at
    // the largest tier. 3-letter labels appear in landscape (MEDIUM+).
    AdaptiveChipRow(
        items = labelsShort.indices.toList(),
        isSelected = { it == selected },
        onSelect = onChange,
        label = { labelsShort[it] },
        labelLong = { labelsLong[it] }
    )
}

@Composable
private fun KpiSummaryTable(summary: KpiSummary, df: DecimalFormat, kwhDf: DecimalFormat, showHints: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Formula subs stay literal — PV/Feed/Load notation, not prose.
        KpiRow(stringResource(R.string.ui2_dash_kpi_self_consumption),
            "(PV − Feed) / PV", df.format(summary.selfConsumption) + "%", showHints)
        KpiRow(stringResource(R.string.ui2_dash_kpi_self_sufficiency),
            "(PV − Feed) / Load", df.format(summary.selfSufficiency) + "%", showHints)
        KpiRow(stringResource(R.string.ui2_dash_kpi_max_self_sufficiency),
            "PV / Load", df.format(summary.maxSelfSufficiency) + "%", showHints)
        KpiRow(stringResource(R.string.ui2_dash_kpi_generation),
            "PV", kwhDf.format(summary.pv), showHints)
        KpiRow(stringResource(R.string.ui2_dash_kpi_feed),
            "Feed", kwhDf.format(summary.feed), showHints)
    }
}

@Composable
private fun KpiRow(label: String, sub: String, value: String, showHints: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (showHints) {
                Text(sub, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun KpiMonthsTable(rows: List<KpiMonthRow>, kwhDf: DecimalFormat) {
    if (rows.isEmpty()) {
        Text(stringResource(R.string.ui2_dash_no_filter_data),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp))
        return
    }
    val columns = listOf(
        PinnedScrollColumn<KpiMonthRow>(header = stringResource(R.string.ui2_dash_pv_tot), cell = { row ->
            Text(kwhDf.format(row.pvTotal),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }),
        PinnedScrollColumn<KpiMonthRow>(
            header = stringResource(R.string.ui2_dash_best),
            minWidth = AdaptiveLayout.SCROLL_COL_MIN_MIXED,
            weight = 1.4f,
            cell = { row ->
                // best/worst arrive as "<value> on <dd>" — re-format the leading
                // kWh value to 1 dp; the DB query (AlphaEssDAO) and the sim path
                // (buildMonthRowsFromDoy) both emit 2 dp, but the display layer
                // is the single source of truth so they stay consistent.
                Text(reformatKwhOn(row.best, kwhDf),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        ),
        PinnedScrollColumn<KpiMonthRow>(
            header = stringResource(R.string.ui2_dash_worst),
            minWidth = AdaptiveLayout.SCROLL_COL_MIN_MIXED,
            weight = 1.4f,
            cell = { row ->
                Text(reformatKwhOn(row.worst, kwhDf),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        ),
        PinnedScrollColumn<KpiMonthRow>(header = stringResource(R.string.ui2_dash_avg), cell = { row ->
            Text(kwhDf.format(row.average),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        })
    )
    // Single table — at normal size the weighted layout distributes the
    // columns across the full width (including landscape); under font scaling
    // it pins the YY-MM column and scrolls the values.
    PinnedScrollTable(
        rows = rows,
        pinnedHeader = stringResource(R.string.ui2_dash_yymm),
        pinnedWeight = 1f,
        pinnedCell = { row ->
            Text(row.monthLabel, style = MaterialTheme.typography.bodySmall)
        },
        columns = columns
    )
}

/**
 * Re-format a pre-built "<value> on <dd>" string by parsing the leading
 * number and re-emitting it via [kwhDf] (1 dp). Falls back to the original
 * string if the leading token isn't a number — covers "—" and any future
 * format drift gracefully.
 */
private fun reformatKwhOn(s: String, kwhDf: DecimalFormat): String {
    val space = s.indexOf(' ')
    val head = if (space < 0) s else s.substring(0, space)
    val tail = if (space < 0) "" else s.substring(space)
    val v = head.toDoubleOrNull() ?: return s
    return kwhDf.format(v) + tail
}
