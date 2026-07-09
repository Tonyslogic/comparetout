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

package com.tfcode.comparetout.ui2

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tfcode.comparetout.R
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

// ──────────────────────────────────────────────────────────────────────────
// Backfill Home Assistant — a standalone wizard (launched from the HA card in
// Data Source Management). The flow is too involved for a nested accordion:
// pick a source, the series and the target; then steer the timeframe (kept
// beside the charts it drives) over the auto-computed HA-vs-source preview
// and commit. Selection, date handling and chart rendering
// follow the Compare screen idioms: FilterChips for subjects/series, the
// shared PeriodSelector ("D / M / Y / *  < >") for the timeframe, and the
// Compare line-chart renderer for the preview.
// ──────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2HaBackfillActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                HaBackfillScreen(onClose = { finish() })
            }
        }
    }
}

private val SERIES_OPTIONS = listOf(
    "buy" to R.string.ui2_habf_series_buy,
    "feed" to R.string.ui2_habf_series_feed,
    "pv" to R.string.ui2_habf_series_pv,
    "charge" to R.string.ui2_habf_series_charge,
    "discharge" to R.string.ui2_habf_series_discharge
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HaBackfillScreen(
    viewModel: UI2HaBackfillViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val sources by viewModel.sources.observeAsState(emptyList())
    val haReady by viewModel.haReady.observeAsState(false)
    val haveSensors by viewModel.haveSensors.observeAsState(false)
    val preview by viewModel.preview.observeAsState(null)
    val previewLoading by viewModel.previewLoading.observeAsState(false)
    val toast by viewModel.toast.observeAsState(null)
    val snackbar = remember { SnackbarHostState() }
    val (showHints, _) = rememberShowHints()

    LaunchedEffect(toast) {
        toast?.let { snackbar.showSnackbar(it.message) }
    }

    // ── selection state (Compare-style: subject → timeframe → series) ──
    var sourceSn by remember { mutableStateOf<String?>(null) }
    var period by remember { mutableStateOf(DataSourcePeriod.MONTH) }
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var series by remember { mutableStateOf(setOf("buy")) }
    var fixReal by remember { mutableStateOf(true) }
    // Discrepancy walk: the day list captured when "Show discrepancies" zooms
    // in, the cursor within it, and the hour picked on the single-day view.
    var discrepancyDays by remember { mutableStateOf<List<LocalDate>>(emptyList()) }
    var discrepancyIndex by remember { mutableStateOf(0) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }

    val source = sources.firstOrNull { it.sysSn == sourceSn }
    val dataStart = source?.startDate?.takeIf { it.isNotEmpty() }
    val dataEnd = source?.finishDate?.takeIf { it.isNotEmpty() }
    val range: Pair<LocalDate, LocalDate>? =
        if (dataStart != null && dataEnd != null) {
            runCatching {
                periodDateRange(period, anchor,
                    advanced = false,
                    dataStart = LocalDate.parse(dataStart),
                    dataEnd = LocalDate.parse(dataEnd))
            }.getOrNull()
        } else null

    // Grey series the selected source's importer cannot provide (e.g. meter-only
    // sources have no solar/battery) — same capability set Compare greys with.
    val provided = source?.importer?.providedEnergySeries
        ?: SERIES_OPTIONS.map { it.first }.toSet()

    val ready = haReady && source != null && range != null &&
            series.isNotEmpty() && (!fixReal || haveSensors)

    // Auto-preview: any selection change recomputes the overlay after a short
    // debounce (the timeframe arrows can be tapped rapidly). At the single-day
    // period the preview drops to hourly buckets for the hour-repair drill-down.
    LaunchedEffect(sourceSn, period, anchor, series) {
        selectedHour = null
        if (source != null && range != null && series.isNotEmpty()) {
            kotlinx.coroutines.delay(350)
            viewModel.previewBackfill(source.sysSn, range.first, range.second,
                series.toList(), hourly = period == DataSourcePeriod.YESTERDAY)
        } else {
            viewModel.clearPreview()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui2_habf_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui2_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showHints) {
                Text(
                    stringResource(R.string.ui2_habf_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!haReady) {
                Text(stringResource(R.string.ui2_habf_connect_first),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            SectionLabel(stringResource(R.string.ui2_habf_source))
            if (sources.isEmpty()) {
                Text(stringResource(R.string.ui2_habf_no_sources),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEach { s ->
                    FilterChip(
                        selected = sourceSn == s.sysSn,
                        onClick = {
                            sourceSn = s.sysSn
                            // Land on the newest data the source holds.
                            anchor = runCatching { LocalDate.parse(s.finishDate) }
                                .getOrDefault(LocalDate.now())
                            period = DataSourcePeriod.MONTH
                            discrepancyDays = emptyList()
                            viewModel.clearPreview()
                        },
                        label = { Text(s.sysSn) }
                    )
                }
            }

            if (source != null && dataStart != null && dataEnd != null) {
                SectionLabel(stringResource(R.string.ui2_habf_series))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SERIES_OPTIONS.forEach { (key, labelRes) ->
                        FilterChip(
                            selected = key in series,
                            enabled = key in provided,
                            onClick = {
                                series = if (key in series) series - key else series + key
                            },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.ui2_habf_target))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // The preview compares stored data, so the target choice
                    // doesn't invalidate it — no clear.
                    FilterChip(
                        selected = fixReal,
                        onClick = { fixReal = true },
                        label = { Text(stringResource(R.string.ui2_habf_fix_real)) }
                    )
                    FilterChip(
                        selected = !fixReal,
                        onClick = { fixReal = false },
                        label = { Text(stringResource(R.string.ui2_habf_separate)) }
                    )
                }
                val targetCaption = stringResource(when {
                    fixReal && haveSensors -> R.string.ui2_habf_caption_fix_ready
                    fixReal -> R.string.ui2_habf_caption_fix_discover
                    else -> R.string.ui2_habf_caption_separate
                })
                Text(targetCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Timeframe sits last, right above the charts it drives — the
                // arrows and the discrepancy walk both re-anchor what's shown.
                SectionLabel(stringResource(R.string.ui2_timeframe))
                PeriodSelector(
                    selectedPeriod = period,
                    anchorDate = anchor,
                    dataStart = dataStart,
                    dataEnd = dataEnd,
                    // No explicit preview clear — the auto-preview effect recomputes
                    // and the stale chart stays visible under the progress bar.
                    onPeriodChange = { p, a, _ ->
                        period = p; anchor = a
                    },
                    onNavigate = { forward, _ ->
                        anchor = stepAnchor(anchor, period, forward,
                            LocalDate.parse(dataStart), LocalDate.parse(dataEnd))
                    }
                )
                range?.let { (f, t) ->
                    Text("$f → $t",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // Committing requires the preview to have landed first (OQ-7) —
                    // it computes automatically, so this gates on the load.
                    Button(
                        enabled = ready && preview != null && !previewLoading,
                        onClick = {
                            range?.let { (f, t) ->
                                viewModel.startBackfill(source.sysSn, f, t, !fixReal, series.toList())
                            }
                        }
                    ) { Text(stringResource(R.string.ui2_habf_backfill_range)) }
                    preview?.takeIf { !it.hourly && it.discrepancies.isNotEmpty() }?.let { p ->
                        OutlinedButton(onClick = {
                            val days = p.discrepancies.mapNotNull { i ->
                                runCatching { LocalDate.parse(p.labels[i]) }.getOrNull()
                            }
                            if (days.isNotEmpty()) {
                                discrepancyDays = days
                                discrepancyIndex = 0
                                anchor = days.first()
                                period = DataSourcePeriod.YESTERDAY
                            }
                        }) { Text(stringResource(R.string.ui2_habf_show_discrepancies,
                            p.discrepancies.size)) }
                    }
                }

                if (previewLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                preview?.let { p ->
                    if (p.hourly && discrepancyDays.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.ui2_habf_discrepancy_day,
                                    discrepancyIndex + 1, discrepancyDays.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(
                                enabled = discrepancyDays.size > 1,
                                onClick = {
                                    discrepancyIndex = (discrepancyIndex - 1 +
                                            discrepancyDays.size) % discrepancyDays.size
                                    anchor = discrepancyDays[discrepancyIndex]
                                }
                            ) { Text(stringResource(R.string.ui2_previous)) }
                            OutlinedButton(
                                enabled = discrepancyDays.size > 1,
                                onClick = {
                                    discrepancyIndex = (discrepancyIndex + 1) % discrepancyDays.size
                                    anchor = discrepancyDays[discrepancyIndex]
                                }
                            ) { Text(stringResource(R.string.ui2_next)) }
                        }
                    }

                    HaBackfillPreviewCharts(p, source.sysSn)

                    if (p.hourly) {
                        SectionLabel(stringResource(R.string.ui2_habf_repair_hour))
                        if (showHints) {
                            Text(stringResource(R.string.ui2_habf_repair_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            p.labels.forEachIndexed { i, label ->
                                FilterChip(
                                    selected = selectedHour == i,
                                    onClick = {
                                        selectedHour = if (selectedHour == i) null else i
                                    },
                                    label = {
                                        Text(if (i in p.discrepancies) "$label ⚠" else label)
                                    }
                                )
                            }
                        }
                        selectedHour?.takeIf { it < p.hourStarts.size }?.let { hi ->
                            HourRepairCard(
                                preview = p,
                                hourIndex = hi,
                                sourceName = source.sysSn,
                                commitEnabled = ready && !previewLoading,
                                onBackfillHour = {
                                    range?.let { (f, t) ->
                                        viewModel.startBackfill(source.sysSn, f, t,
                                            !fixReal, series.toList(), p.hourStarts[hi])
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun seriesLabel(key: String): String =
    SERIES_OPTIONS.firstOrNull { it.first == key }?.second?.let { stringResource(it) } ?: key

/**
 * The look-before-you-overwrite preview: one chart per subject — the source
 * (about to be pushed) above what HA currently holds — like the Compare tab's
 * per-subject cards. Both charts share the y-scale and the series colour
 * registry, so a divergence between them reads directly.
 */
@Composable
private fun HaBackfillPreviewCharts(preview: HaBackfillPreview, sourceName: String) {
    val primary = MaterialTheme.colorScheme.primary
    val defs = preview.seriesKeys.map { key ->
        SeriesDef(key, seriesLabel(key), compareSeriesColor(key, primary, isEnergy = true))
    }
    val caption = stringResource(
        if (preview.hourly) R.string.ui2_habf_hourly_kwh else R.string.ui2_habf_daily_kwh)
    // Shared y-scale — a value present in one graph but not the other must
    // stand out, not be re-normalised away.
    val yMax = (preview.source.values + preview.ha.values)
        .flatten().maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

    BackfillSubjectChart(sourceName, defs, preview.labels, preview.source, yMax, caption)
    BackfillSubjectChart(stringResource(R.string.home_assistant),
        defs, preview.labels, preview.ha, yMax, caption)

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        defs.forEach { def ->
            Box(Modifier.size(10.dp).background(def.color, CircleShape))
            Text(def.label, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.weight(1f))
        Text("${preview.labels.firstOrNull().orEmpty()} → ${preview.labels.lastOrNull().orEmpty()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackfillSubjectChart(
    title: String,
    defs: List<SeriesDef>,
    labels: List<String>,
    values: Map<String, List<Double>>,
    yMax: Double,
    caption: String
) {
    val datum = ChartDatum(
        title = title,
        shortLabel = title,
        values = values.mapValues { it.value.sum() },
        axisLabels = labels,
        seriesValues = values
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$title · $caption", style = MaterialTheme.typography.labelMedium)
            CompareLineChart(data = listOf(datum), series = defs, area = false,
                unit = "kWh", yMax = yMax)
        }
    }
}

/** Hour drill-down: the selected hour's per-series values side by side + commit. */
@Composable
private fun HourRepairCard(
    preview: HaBackfillPreview,
    hourIndex: Int,
    sourceName: String,
    commitEnabled: Boolean,
    onBackfillHour: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ui2_habf_hour, preview.labels[hourIndex]),
                style = MaterialTheme.typography.labelMedium)
            preview.seriesKeys.forEach { key ->
                val s = preview.source[key]?.getOrNull(hourIndex) ?: 0.0
                val h = preview.ha[key]?.getOrNull(hourIndex) ?: 0.0
                Text(stringResource(R.string.ui2_habf_hour_values,
                        seriesLabel(key), sourceName, s, h),
                    style = MaterialTheme.typography.bodySmall)
            }
            Button(enabled = commitEnabled, onClick = onBackfillHour) {
                Text(stringResource(R.string.ui2_habf_backfill_hour))
            }
        }
    }
}
