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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

// ──────────────────────────────────────────────────────────────────────────
// Backfill Home Assistant — a standalone wizard (launched from the HA card in
// Data Source Management). The flow is too involved for a nested accordion:
// pick a source, a timeframe, the series and the target; preview the HA-vs-
// source overlay; then commit. Selection, date handling and chart rendering
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
    "buy" to "Grid import",
    "feed" to "Grid export",
    "pv" to "Solar",
    "charge" to "Battery charge",
    "discharge" to "Battery discharge"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backfill Home Assistant") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                    "Push another source's stored history into Home Assistant's hourly " +
                            "statistics — fix gaps or bad meter data. Preview compares what " +
                            "HA holds against what will be written before anything changes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!haReady) {
                Text("Connect Home Assistant first (Data Source Management → Home Assistant).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            SectionLabel("Source")
            if (sources.isEmpty()) {
                Text("No other source has stored data to push.",
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
                            viewModel.clearPreview()
                        },
                        label = { Text(s.sysSn) }
                    )
                }
            }

            if (source != null && dataStart != null && dataEnd != null) {
                SectionLabel("Timeframe")
                PeriodSelector(
                    selectedPeriod = period,
                    anchorDate = anchor,
                    dataStart = dataStart,
                    dataEnd = dataEnd,
                    onPeriodChange = { p, a, _ ->
                        period = p; anchor = a; viewModel.clearPreview()
                    },
                    onNavigate = { forward, _ ->
                        anchor = stepAnchor(anchor, period, forward,
                            LocalDate.parse(dataStart), LocalDate.parse(dataEnd))
                        viewModel.clearPreview()
                    }
                )
                range?.let { (f, t) ->
                    Text("$f → $t",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SectionLabel("Series")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SERIES_OPTIONS.forEach { (key, label) ->
                        FilterChip(
                            selected = key in series,
                            enabled = key in provided,
                            onClick = {
                                series = if (key in series) series - key else series + key
                                viewModel.clearPreview()
                            },
                            label = { Text(label) }
                        )
                    }
                }

                SectionLabel("Target")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = fixReal,
                        onClick = { fixReal = true; viewModel.clearPreview() },
                        label = { Text("Fix my sensor statistics") }
                    )
                    FilterChip(
                        selected = !fixReal,
                        onClick = { fixReal = false; viewModel.clearPreview() },
                        label = { Text("Separate app-owned series") }
                    )
                }
                val targetCaption = when {
                    fixReal && haveSensors ->
                        "Overwrites the selected range of your HA sensor statistics " +
                                "(hourly); later totals are shifted so history stays consistent."
                    fixReal ->
                        "Overwrites the selected range of your HA sensor statistics " +
                                "(hourly). Discover sensors first."
                    else ->
                        "Writes comparetout:* statistics alongside your sensors — " +
                                "non-destructive, easy to remove in HA."
                }
                Text(targetCaption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = ready,
                        onClick = {
                            range?.let { (f, t) ->
                                viewModel.previewBackfill(source.sysSn, f, t, series.first())
                            }
                        }
                    ) { Text("Preview") }
                    // Committing requires looking at the preview first (OQ-7).
                    Button(
                        enabled = ready && preview != null,
                        onClick = {
                            range?.let { (f, t) ->
                                viewModel.startBackfill(source.sysSn, f, t, !fixReal, series.toList())
                            }
                            viewModel.clearPreview()
                        }
                    ) { Text("Backfill") }
                }

                preview?.let { p -> HaBackfillPreviewChart(p, source.sysSn) }
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

/**
 * Daily-kWh overlay of the source series (about to be pushed) against what HA
 * currently holds for the same days — rendered with the Compare line chart so
 * the preview looks like every other comparison in the app.
 */
@Composable
private fun HaBackfillPreviewChart(preview: HaBackfillPreview, sourceName: String) {
    val sourceColor = MaterialTheme.colorScheme.primary
    val haColor = MaterialTheme.colorScheme.tertiary
    val seriesLabel = SERIES_OPTIONS.firstOrNull { it.first == preview.series }?.second
        ?: preview.series
    val defs = listOf(
        SeriesDef("source", sourceName, sourceColor),
        SeriesDef("ha", "Home Assistant", haColor)
    )
    val datum = ChartDatum(
        title = seriesLabel,
        shortLabel = seriesLabel,
        values = mapOf(
            "source" to preview.source.sum(),
            "ha" to preview.ha.sum()
        ),
        axisLabels = preview.labels,
        seriesValues = mapOf(
            "source" to preview.source,
            "ha" to preview.ha
        )
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$seriesLabel · daily kWh", style = MaterialTheme.typography.labelMedium)
            CompareLineChart(data = listOf(datum), series = defs, area = false, unit = "kWh")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(sourceColor, CircleShape))
                Text(sourceName, style = MaterialTheme.typography.labelSmall)
                Box(Modifier.size(10.dp).background(haColor, CircleShape))
                Text("Home Assistant", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text("${preview.labels.firstOrNull().orEmpty()} → ${preview.labels.lastOrNull().orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
