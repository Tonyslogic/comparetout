package com.tfcode.comparetout.ui2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ──────────────────────────────────────────────────────────────────────────
// Chart renderers for the Compare result panels. Each takes a metric-agnostic
// list of ChartDatum so the same renderer serves both Cost and Usage views.
// ──────────────────────────────────────────────────────────────────────────

data class SeriesDef(val id: String, val label: String, val color: Color)

data class ChartDatum(
    val title: String,                       // full subject (+ plan) label
    val shortLabel: String,                  // compact axis label
    val values: Map<String, Double>,         // seriesId → total
    val monthly: Map<String, List<Double>>   // seriesId → 12 monthly values
)

// ── grouped bar chart ───────────────────────────────────────────────────────
@Composable
fun CompareBarChart(data: List<ChartDatum>, series: List<SeriesDef>, height: Dp = 170.dp) {
    if (data.isEmpty() || series.isEmpty()) return
    val maxV = max(data.maxOf { d -> series.maxOf { abs(d.values[it.id] ?: 0.0) } }, 1e-6)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val baseline = size.height - 4f
            drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, baseline), Offset(size.width, baseline), 1.5f)
            val groupW = size.width / data.size
            val barGap = groupW * 0.12f
            val barW = (groupW - barGap * 2) / series.size
            data.forEachIndexed { gi, d ->
                series.forEachIndexed { si, s ->
                    val v = abs(d.values[s.id] ?: 0.0)
                    val h = (v / maxV * (baseline - 6f)).toFloat()
                    val x = gi * groupW + barGap + si * barW
                    drawRect(s.color, Offset(x, baseline - h), Size(barW * 0.86f, h))
                }
            }
        }
        AxisLabels(data.map { it.shortLabel })
    }
}

// ── stacked bar chart ───────────────────────────────────────────────────────
@Composable
fun CompareStackChart(data: List<ChartDatum>, series: List<SeriesDef>, height: Dp = 170.dp) {
    if (data.isEmpty() || series.isEmpty()) return
    val totals = data.map { d -> series.sumOf { abs(d.values[it.id] ?: 0.0) } }
    val maxV = max(totals.maxOrNull() ?: 1.0, 1e-6)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val baseline = size.height - 4f
            drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, baseline), Offset(size.width, baseline), 1.5f)
            val groupW = size.width / data.size
            val barW = groupW * 0.5f
            data.forEachIndexed { gi, d ->
                var y = baseline
                series.forEach { s ->
                    val v = abs(d.values[s.id] ?: 0.0)
                    val h = (v / maxV * (baseline - 6f)).toFloat()
                    drawRect(s.color, Offset(gi * groupW + (groupW - barW) / 2f, y - h), Size(barW, h))
                    y -= h
                }
            }
        }
        AxisLabels(data.map { it.shortLabel })
    }
}

// ── line / area chart (monthly) ─────────────────────────────────────────────
@Composable
fun CompareLineChart(data: List<ChartDatum>, series: List<SeriesDef>, area: Boolean, height: Dp = 170.dp) {
    if (data.isEmpty() || series.isEmpty()) return
    val metric = series.first()
    val lines = data.mapIndexed { idx, d ->
        val pts = d.monthly[metric.id] ?: List(12) { 0.0 }
        val color = if (data.size == 1) metric.color else compareSubjectColor(idx)
        pts to color
    }
    val maxV = max(lines.flatMap { it.first }.maxOfOrNull { abs(it) } ?: 1.0, 1e-6)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val h = size.height - 18f
            for (g in 0..3) {
                val y = h * g / 3f
                drawLine(Color.Gray.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            lines.forEach { (pts, color) ->
                val path = Path()
                pts.forEachIndexed { i, v ->
                    val x = size.width * i / 11f
                    val y = h * (1f - (abs(v) / maxV).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (area) {
                    val fill = Path()
                    fill.addPath(path)
                    fill.lineTo(size.width, h)
                    fill.lineTo(0f, h)
                    fill.close()
                    drawPath(fill, color.copy(alpha = 0.22f))
                }
                drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Jan", "Apr", "Jul", "Oct", "Dec").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── pie (small multiples grid, each pie pops out) ───────────────────────────
@Composable
fun ComparePieGrid(data: List<ChartDatum>, series: List<SeriesDef>, holeColor: Color) {
    if (data.isEmpty() || series.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        data.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { d ->
                    Box(Modifier.weight(1f)) { ComparePie(d, series, holeColor) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ComparePie(datum: ChartDatum, series: List<SeriesDef>, holeColor: Color) {
    var zoomed by remember { mutableStateOf(false) }
    Column(
        Modifier.clickable { zoomed = true },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PieCanvas(datum, series, holeColor, 108.dp)
        Spacer(Modifier.height(4.dp))
        Text("${datum.title}  ↗", style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
    if (zoomed) {
        val cfg = LocalConfiguration.current
        val dim = min(cfg.screenWidthDp, cfg.screenHeightDp).dp
        Dialog(onDismissRequest = { zoomed = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.size(dim), shape = MaterialTheme.shapes.medium, tonalElevation = 8.dp) {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(datum.title, style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    PieCanvas(datum, series, holeColor, dim * 0.62f)
                    Spacer(Modifier.height(12.dp))
                    ChartLegend(series)
                }
            }
        }
    }
}

@Composable
private fun PieCanvas(datum: ChartDatum, series: List<SeriesDef>, holeColor: Color, diameter: Dp) {
    val slices = series.mapNotNull { s ->
        val v = abs(datum.values[s.id] ?: 0.0)
        if (v > 0.0) s.color to v else null
    }
    val sum = slices.sumOf { it.second }
    Canvas(Modifier.size(diameter)) {
        if (sum <= 0.0) {
            drawCircle(Color.Gray.copy(alpha = 0.25f))
            return@Canvas
        }
        var start = -90f
        slices.forEach { (color, v) ->
            val sweep = (v / sum * 360.0).toFloat()
            drawArc(color, start, sweep, useCenter = true, topLeft = Offset.Zero, size = size)
            start += sweep
        }
        drawCircle(color = holeColor, radius = size.minDimension * 0.27f)
    }
}

/** Coloured-dot series legend, reused by chart panels and pie pop-outs. */
@Composable
fun ChartLegend(series: List<SeriesDef>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(9.dp)) { drawCircle(s.color) }
                Spacer(Modifier.size(4.dp))
                Text(s.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AxisLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        labels.forEach { l ->
            Text(
                l,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                // cost labels are two lines (source on top, supplier below)
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// per-subject palette — shared by merged line charts and the source legend
private val SUBJECT_PALETTE = listOf(
    Color(0xFF3b82f6), Color(0xFF22d3ee), Color(0xFFf87171),
    Color(0xFF4ade80), Color(0xFFfb923c), Color(0xFFa78bfa)
)

/** Stable colour for the n-th subject in a merged chart / the source legend. */
fun compareSubjectColor(i: Int): Color = SUBJECT_PALETTE[(if (i < 0) 0 else i) % SUBJECT_PALETTE.size]
