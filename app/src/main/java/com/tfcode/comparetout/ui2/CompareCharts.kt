package com.tfcode.comparetout.ui2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

// ──────────────────────────────────────────────────────────────────────────
// Chart renderers for the Compare result panels. Each takes a metric-agnostic
// list of ChartDatum so the same renderer serves both Cost and Usage views.
// Every axis chart carries a labelled value scale on the left and its unit.
// ──────────────────────────────────────────────────────────────────────────

data class SeriesDef(val id: String, val label: String, val color: Color)

data class ChartDatum(
    val title: String,                          // full subject (+ plan) label
    val shortLabel: String,                     // compact axis label
    val values: Map<String, Double>,            // seriesId → total
    val axisLabels: List<String>,               // bucket labels for line/area (N entries)
    val seriesValues: Map<String, List<Double>> // seriesId → N values, parallel to axisLabels
)

private val Y_AXIS_W = 40.dp   // left margin reserved for the value scale

// ── value-axis helpers ──────────────────────────────────────────────────────

/** Round a maximum up to a "nice" 1 / 2 / 5 × 10ⁿ value so ticks read cleanly. */
private fun niceCeil(v: Double): Double {
    if (v <= 0.0) return 1.0
    val base = 10.0.pow(floor(log10(v)))
    val n = v / base
    val nice = when {
        n <= 1.0 -> 1.0
        n <= 2.0 -> 2.0
        n <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * base
}

/** Compact axis number — 1.2k, 340, 0.45 … */
fun axisNumber(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1_000_000 -> "%.1fM".format(v / 1_000_000)
        a >= 1_000 -> "%.1fk".format(v / 1_000)
        a >= 10 -> v.roundToInt().toString()
        a >= 1 -> "%.1f".format(v)
        a > 0.0 -> "%.2f".format(v)
        else -> "0"
    }
}

/** Draws gridlines + value labels for a 0..maxV scale, and the unit top-left. */
private fun DrawScope.drawValueAxis(
    measurer: TextMeasurer, labelColor: Color, gridColor: Color,
    maxV: Double, unit: String, plotTop: Float, plotBottom: Float, axisW: Float
) {
    val style = TextStyle(color = labelColor, fontSize = 9.sp)
    val steps = 4
    for (i in 0..steps) {
        val frac = i / steps.toFloat()
        val v = maxV * (1f - frac)
        val y = plotTop + (plotBottom - plotTop) * frac
        drawLine(gridColor, Offset(axisW, y), Offset(size.width, y), 1f)
        val layout = measurer.measure(axisNumber(v), style)
        drawText(layout, topLeft = Offset(
            axisW - layout.size.width.toFloat() - 3f,
            (y - layout.size.height.toFloat() / 2f)
                .coerceIn(0f, size.height - layout.size.height.toFloat())
        ))
    }
    drawText(measurer.measure(unit, style), topLeft = Offset(1f, 1f))
}

/** Draws a label at an arbitrary value level (used by the signed cost stack). */
private fun DrawScope.drawAxisTick(
    measurer: TextMeasurer, style: TextStyle, gridColor: Color,
    text: String, y: Float, axisW: Float
) {
    drawLine(gridColor, Offset(axisW, y), Offset(size.width, y), 1f)
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(
        axisW - layout.size.width.toFloat() - 3f,
        (y - layout.size.height.toFloat() / 2f)
            .coerceIn(0f, size.height - layout.size.height.toFloat())
    ))
}

// ── grouped bar chart ───────────────────────────────────────────────────────
@Composable
fun CompareBarChart(data: List<ChartDatum>, series: List<SeriesDef>, unit: String, height: Dp = 170.dp) {
    if (data.isEmpty() || series.isEmpty()) return
    // Bucketed path: one panel with N bucket groups, each group has the
    // (subject × series) bars. Entered when there's a non-trivial timeline
    // and either a single subject (split-layout case) or a merged multi-
    // subject view that stays readable: ≤2 series and aligned bucket counts
    // across subjects.
    if (barIsBucketed(data, series)) {
        renderBucketedBars(data, series, unit, height)
        return
    }
    renderTotalsBars(data, series, unit, height)
}

@Composable
private fun renderTotalsBars(
    data: List<ChartDatum>, series: List<SeriesDef>, unit: String, height: Dp
) {
    val maxV = niceCeil(data.maxOf { d -> series.maxOf { abs(d.values[it.id] ?: 0.0) } })
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val axisW = Y_AXIS_W.toPx()
            val top = 14f
            val baseline = size.height - 4f
            drawValueAxis(measurer, labelColor, gridColor, maxV, unit, top, baseline, axisW)
            val groupW = (size.width - axisW) / data.size
            val barGap = groupW * 0.12f
            val barW = (groupW - barGap * 2) / series.size
            data.forEachIndexed { gi, d ->
                series.forEachIndexed { si, s ->
                    val v = abs(d.values[s.id] ?: 0.0)
                    val h = (v / maxV).toFloat() * (baseline - top)
                    val x = axisW + gi * groupW + barGap + si * barW
                    drawRect(s.color, Offset(x, baseline - h), Size(barW * 0.86f, h))
                }
            }
        }
        AxisLabels(data.map { it.shortLabel })
    }
}

/**
 * Bucketed bar renderer: one panel, N bucket groups across the x-axis, each
 * group holds (data.size × series.size) bars. Colour story mirrors the line
 * chart's [buildCompareLines]:
 *   1 subject × N series  → series colours
 *   N subjects × 1 series → subject palette via [compareSubjectColor]
 *   N subjects × M series → subject hue × series-indexed alpha
 * Tick labels are sampled from the longest subject's axisLabels.
 */
@Composable
private fun renderBucketedBars(
    data: List<ChartDatum>, series: List<SeriesDef>, unit: String, height: Dp
) {
    val colorMode = compareColorMode(data.size, series.size)
    val tickSource = data.maxByOrNull { it.axisLabels.size } ?: data.first()
    val n = tickSource.axisLabels.size
    if (n <= 0) return
    val maxV = niceCeil(
        data.flatMap { d ->
            series.flatMap { s -> d.seriesValues[s.id] ?: emptyList() }
        }.maxOfOrNull { abs(it) } ?: 1.0
    )
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val ticks = sampleTickLabels(tickSource.axisLabels)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val axisW = Y_AXIS_W.toPx()
            val top = 14f
            val baseline = size.height - 4f
            drawValueAxis(measurer, labelColor, gridColor, maxV, unit, top, baseline, axisW)
            val plotW = size.width - axisW
            val groupW = plotW / n
            val gap = groupW * 0.12f
            val barsPerGroup = data.size * series.size
            val barW = (groupW - gap * 2f) / barsPerGroup.coerceAtLeast(1)
            for (bi in 0 until n) {
                var slot = 0
                data.forEachIndexed { di, d ->
                    series.forEachIndexed { si, s ->
                        val pts = d.seriesValues[s.id] ?: return@forEachIndexed
                        val v = abs(pts.getOrNull(bi) ?: 0.0)
                        val h = (v / maxV).toFloat() * (baseline - top)
                        val color = compareEncodedColor(colorMode, di, s, si, series.size)
                        val x = axisW + bi * groupW + gap + slot * barW
                        drawRect(color, Offset(x, baseline - h),
                            Size(barW * 0.86f, h))
                        slot++
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Spacer(Modifier.width(Y_AXIS_W))
            ticks.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }
    }
}

// ── stacked bar chart ───────────────────────────────────────────────────────
@Composable
fun CompareStackChart(data: List<ChartDatum>, series: List<SeriesDef>, unit: String, height: Dp = 170.dp) {
    if (data.isEmpty() || series.isEmpty()) return
    val maxV = niceCeil(data.maxOf { d -> series.sumOf { abs(d.values[it.id] ?: 0.0) } })
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val axisW = Y_AXIS_W.toPx()
            val top = 14f
            val baseline = size.height - 4f
            drawValueAxis(measurer, labelColor, gridColor, maxV, unit, top, baseline, axisW)
            val groupW = (size.width - axisW) / data.size
            val barW = groupW * 0.5f
            data.forEachIndexed { gi, d ->
                var y = baseline
                series.forEach { s ->
                    val v = abs(d.values[s.id] ?: 0.0)
                    val h = (v / maxV).toFloat() * (baseline - top)
                    drawRect(s.color, Offset(axisW + gi * groupW + (groupW - barW) / 2f, y - h), Size(barW, h))
                    y -= h
                }
            }
        }
        AxisLabels(data.map { it.shortLabel })
    }
}

// ── line / area chart ──────────────────────────────────────────────────────

/**
 * One rendered line: N bucket points (parallel to [axisLabels]), a colour, and
 * a human label for the legend. Lines in the same chart may have different
 * bucket counts when sync=off lets each subject resolve AUTO independently.
 */
data class CompareLine(
    val points: List<Double>,
    val axisLabels: List<String>,
    val color: Color,
    val label: String
)

/**
 * Build every (subject × series) combination that actually has bucketed data.
 * - 1 subject × N series      → colour by series
 * - N subjects × 1 series     → colour by subject
 * - N subjects × M series     → blend (subject colour × series alpha intensity)
 * Series that no subject has data for get omitted entirely.
 */
fun buildCompareLines(data: List<ChartDatum>, series: List<SeriesDef>): List<CompareLine> {
    if (data.isEmpty() || series.isEmpty()) return emptyList()
    val mode = compareColorMode(data.size, series.size)
    val lines = mutableListOf<CompareLine>()
    data.forEachIndexed { di, d ->
        series.forEachIndexed { si, s ->
            val pts = d.seriesValues[s.id] ?: return@forEachIndexed
            if (pts.all { it == 0.0 } && !d.seriesValues.containsKey(s.id)) return@forEachIndexed
            val color = compareEncodedColor(mode, di, s, si, series.size)
            val label = when (mode) {
                CompareColorMode.SERIES  -> s.label
                CompareColorMode.SUBJECT -> d.title
                CompareColorMode.BLEND   -> "${shortenLabel(d.title)} · ${s.label}"
            }
            lines += CompareLine(pts, d.axisLabels, color, label)
        }
    }
    return lines
}

private fun shortenLabel(s: String): String = if (s.length <= 18) s else s.take(17) + "…"

/**
 * Sample [labels] down to at most [target] entries, evenly spaced. Keeps the
 * first and last entries so the user always sees both ends of the range.
 * Used by the line chart's tick row — 30 day-labels can't fit on screen.
 */
private fun sampleTickLabels(labels: List<String>, target: Int = 6): List<String> {
    if (labels.isEmpty()) return emptyList()
    if (labels.size <= target) return labels
    val n = target.coerceAtLeast(2)
    val step = (labels.size - 1).toDouble() / (n - 1)
    return (0 until n).map { labels[(it * step).toInt().coerceAtMost(labels.lastIndex)] }
}

@Composable
fun CompareLineChart(
    data: List<ChartDatum>, series: List<SeriesDef>, area: Boolean,
    unit: String, height: Dp = 170.dp
) {
    val lines = buildCompareLines(data, series)
    if (lines.isEmpty()) return
    val maxV = niceCeil(lines.flatMap { it.points }.maxOfOrNull { abs(it) } ?: 1.0)
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    // Tick labels come from the longest line (most buckets) so the densest
    // axis wins — shorter lines stay readable, denser lines aren't truncated.
    val tickSource = lines.maxByOrNull { it.axisLabels.size } ?: lines.first()
    val ticks = sampleTickLabels(tickSource.axisLabels)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val axisW = Y_AXIS_W.toPx()
            val top = 14f
            val bottom = size.height - 4f
            drawValueAxis(measurer, labelColor, gridColor, maxV, unit, top, bottom, axisW)
            val plotW = size.width - axisW
            lines.forEach { line ->
                val n = line.points.size
                if (n == 0) return@forEach
                val denom = (n - 1).coerceAtLeast(1).toFloat()
                val path = Path()
                line.points.forEachIndexed { i, v ->
                    val x = if (n == 1) axisW + plotW / 2f
                            else axisW + plotW * i / denom
                    val y = top + (bottom - top) * (1f - (abs(v) / maxV).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (area) {
                    val fill = Path()
                    fill.addPath(path)
                    fill.lineTo(size.width, bottom)
                    fill.lineTo(axisW, bottom)
                    fill.close()
                    drawPath(fill, line.color.copy(alpha = 0.22f))
                }
                drawPath(path, line.color, style = Stroke(width = 3f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Spacer(Modifier.width(Y_AXIS_W))
            ticks.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }
    }
}

// ── cost stacked chart — banded buy + fixed up, sell down, zero line ────────

/** Fill style for a cost segment — buy rate bands share one colour, differ by pattern. */
enum class BandPattern { SOLID, DIAGONAL, CROSS, HORIZONTAL, VERTICAL }

data class CostSegment(
    val color: Color,
    val value: Double,
    val pattern: BandPattern = BandPattern.SOLID
)

data class CostBar(
    val label: String,                  // two-line axis label
    val title: String,                  // full label
    val positives: List<CostSegment>,   // buy rate bands, then fixed
    val negatives: List<CostSegment>,   // sell
    val net: Double
)

/**
 * Signed cost stack: buy split into rate bands plus fixed charges rise above a
 * zero line; sell hangs below it. A marker per bar shows where the net lands.
 * Buy bands share one colour and are told apart by hatch pattern.
 */
@Composable
fun CompareCostStackChart(bars: List<CostBar>, unit: String, height: Dp = 170.dp) {
    if (bars.isEmpty()) return
    val maxPos = bars.maxOf { b -> b.positives.sumOf { it.value } }
    val maxNeg = bars.maxOf { b -> b.negatives.sumOf { it.value } }
    // include the net markers so they stay on-screen even with no bands selected
    val posExtent = niceCeil(max(maxPos, bars.maxOf { it.net }.coerceAtLeast(0.0)))
    val negExtent = max(maxNeg, (-bars.minOf { it.net }).coerceAtLeast(0.0))
        .let { if (it <= 0.0) 0.0 else niceCeil(it) }
    val span = max(posExtent + negExtent, 1e-6)
    val netColor = MaterialTheme.colorScheme.primary
    val zeroColor = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val axisW = Y_AXIS_W.toPx()
            val pad = 14f
            val innerH = size.height - pad * 2
            val zeroY = pad + innerH * (posExtent / span).toFloat()
            val axisStyle = TextStyle(color = labelColor, fontSize = 9.sp)
            // value scale: top, mid-positive, zero, mid-negative, bottom
            listOf(posExtent, posExtent / 2, 0.0, -negExtent / 2, -negExtent).forEach { v ->
                val y = zeroY - (v / span).toFloat() * innerH
                drawAxisTick(measurer, axisStyle, gridColor, axisNumber(v), y, axisW)
            }
            drawText(measurer.measure(unit, axisStyle), topLeft = Offset(1f, 1f))
            val groupW = (size.width - axisW) / bars.size
            val barW = groupW * 0.5f
            bars.forEachIndexed { gi, bar ->
                val x = axisW + gi * groupW + (groupW - barW) / 2f
                var y = zeroY
                bar.positives.forEach { seg ->
                    val h = (seg.value / span).toFloat() * innerH
                    drawHatchSegment(x, y - h, barW, h, seg.color, seg.pattern)
                    y -= h
                }
                y = zeroY
                bar.negatives.forEach { seg ->
                    val h = (seg.value / span).toFloat() * innerH
                    drawHatchSegment(x, y, barW, h, seg.color, seg.pattern)
                    y += h
                }
                val netY = zeroY - (bar.net / span).toFloat() * innerH
                drawLine(netColor, Offset(x - 4f, netY), Offset(x + barW + 4f, netY), 3.5f)
            }
            drawLine(zeroColor.copy(alpha = 0.7f),
                Offset(axisW, zeroY), Offset(size.width, zeroY), 2.5f)
        }
        AxisLabels(bars.map { it.label })
    }
}

/** Draws one stack segment — solid, or a hatch pattern over a translucent fill. */
private fun DrawScope.drawHatchSegment(
    x: Float, y: Float, w: Float, h: Float, color: Color, pattern: BandPattern
) {
    if (h <= 0f) return
    if (pattern == BandPattern.SOLID) {
        drawRect(color, Offset(x, y), Size(w, h))
        return
    }
    drawRect(color.copy(alpha = 0.20f), Offset(x, y), Size(w, h))
    val gap = 8f
    val sw = 2f
    clipRect(x, y, x + w, y + h) {
        when (pattern) {
            BandPattern.HORIZONTAL -> {
                var ly = y
                while (ly <= y + h) { drawLine(color, Offset(x, ly), Offset(x + w, ly), sw); ly += gap }
            }
            BandPattern.VERTICAL -> {
                var lx = x
                while (lx <= x + w) { drawLine(color, Offset(lx, y), Offset(lx, y + h), sw); lx += gap }
            }
            BandPattern.DIAGONAL -> {
                var o = -h
                while (o <= w) {
                    drawLine(color, Offset(x + o, y), Offset(x + o + h, y + h), sw); o += gap
                }
            }
            BandPattern.CROSS -> {
                var o = -h
                while (o <= w) {
                    drawLine(color, Offset(x + o, y), Offset(x + o + h, y + h), sw)
                    drawLine(color, Offset(x + o, y + h), Offset(x + o + h, y), sw)
                    o += gap
                }
            }
            BandPattern.SOLID -> {}
        }
    }
    drawRect(color, Offset(x, y), Size(w, h), style = Stroke(width = 1.5f))
}

// ── pie (small multiples grid; the host screen owns the pop-out) ────────────

/** One pie slice — colour + value + label, optionally hatched for cost rate bands. */
data class ComparePieSlice(
    val label: String,
    val color: Color,
    val value: Double,
    val pattern: BandPattern = BandPattern.SOLID
)

/** A single pie's worth of data, paired with its title and per-slice breakdown. */
data class ComparePieDatum(
    val title: String,
    val slices: List<ComparePieSlice>
) {
    val total: Double get() = slices.sumOf { abs(it.value) }
}

@Composable
fun ComparePieGrid(
    data: List<ComparePieDatum>,
    holeColor: Color,
    unit: String,
    onZoom: (ComparePieDatum) -> Unit
) {
    if (data.isEmpty()) return

    // One or two pies — put the label beside each pie to use the spare width.
    if (data.size <= 2) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            data.forEach { d ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onZoom(d) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ComparePieCanvas(d.slices, holeColor, 108.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${d.title}  ↗", style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text("${axisNumber(d.total)} $unit total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        return
    }

    // Three or more — small-multiples grid with the label beneath each pie.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        data.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { d ->
                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier.clickable { onZoom(d) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ComparePieCanvas(d.slices, holeColor, 108.dp)
                            Spacer(Modifier.height(4.dp))
                            Text("${d.title}  ↗", style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center)
                            Text("${axisNumber(d.total)} $unit total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Pie canvas. Hatched slices use the same band-pattern vocabulary as the cost stack. */
@Composable
fun ComparePieCanvas(slices: List<ComparePieSlice>, holeColor: Color, diameter: Dp) {
    val sum = slices.sumOf { abs(it.value) }
    Canvas(Modifier.size(diameter)) {
        if (sum <= 0.0) {
            drawCircle(Color.Gray.copy(alpha = 0.25f))
            return@Canvas
        }
        var start = -90f
        slices.forEach { sl ->
            val sweep = (abs(sl.value) / sum * 360.0).toFloat()
            drawHatchArc(start, sweep, sl.color, sl.pattern)
            start += sweep
        }
        drawCircle(color = holeColor, radius = size.minDimension * 0.27f)
    }
}

/** Draws a wedge — solid, or a tinted fill overlaid with hatch lines clipped to it. */
private fun DrawScope.drawHatchArc(
    startAngleDeg: Float, sweepAngleDeg: Float, color: Color, pattern: BandPattern
) {
    if (sweepAngleDeg <= 0f) return
    if (pattern == BandPattern.SOLID) {
        drawArc(color, startAngleDeg, sweepAngleDeg, useCenter = true,
            topLeft = Offset.Zero, size = size)
        return
    }
    // Translucent fill so the eye reads the colour through the hatch.
    drawArc(color.copy(alpha = 0.22f), startAngleDeg, sweepAngleDeg, useCenter = true,
        topLeft = Offset.Zero, size = size)
    // Build a proper wedge: centre → first arc point → arc → close (back to centre).
    // (moveTo + addArc starts a new subpath, which would clip a chord-closed arc
    // segment rather than a wedge — use arcTo so the line from the centre joins.)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f
    val wedge = Path().apply {
        moveTo(cx, cy)
        arcTo(Rect(Offset.Zero, size), startAngleDeg, sweepAngleDeg, forceMoveTo = false)
        close()
    }
    val gap = 10f
    val sw = 2.2f
    clipPath(wedge) {
        when (pattern) {
            BandPattern.HORIZONTAL -> {
                var y = cy - r
                while (y <= cy + r) { drawLine(color, Offset(cx - r, y), Offset(cx + r, y), sw); y += gap }
            }
            BandPattern.VERTICAL -> {
                var x = cx - r
                while (x <= cx + r) { drawLine(color, Offset(x, cy - r), Offset(x, cy + r), sw); x += gap }
            }
            BandPattern.DIAGONAL -> {
                var o = -2 * r
                while (o <= 2 * r) {
                    drawLine(color, Offset(cx - r + o, cy - r), Offset(cx + r + o, cy + r), sw); o += gap
                }
            }
            BandPattern.CROSS -> {
                var o = -2 * r
                while (o <= 2 * r) {
                    drawLine(color, Offset(cx - r + o, cy - r), Offset(cx + r + o, cy + r), sw)
                    drawLine(color, Offset(cx - r + o, cy + r), Offset(cx + r + o, cy - r), sw)
                    o += gap
                }
            }
            BandPattern.SOLID -> {}
        }
    }
    // Outline so adjacent same-colour wedges remain visible.
    drawArc(color, startAngleDeg, sweepAngleDeg, useCenter = true,
        topLeft = Offset.Zero, size = size, style = Stroke(width = 1.5f))
}

/** Per-slice legend with values — used in the pie pop-out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PieValueLegend(slices: List<ComparePieSlice>, unit: String) {
    val total = slices.sumOf { abs(it.value) }.coerceAtLeast(1e-9)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.forEach { sl ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PieSwatch(sl.color, sl.pattern)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${sl.label} · ${axisNumber(sl.value)} $unit (${"%.0f%%".format(100 * abs(sl.value) / total)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PieSwatch(color: Color, pattern: BandPattern) {
    Canvas(Modifier.size(14.dp)) {
        if (pattern == BandPattern.SOLID) {
            drawRect(color)
            return@Canvas
        }
        drawRect(color.copy(alpha = 0.22f))
        val gap = 4f; val sw = 1.2f
        clipRect {
            when (pattern) {
                BandPattern.HORIZONTAL -> {
                    var y = 0f
                    while (y <= size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), sw); y += gap }
                }
                BandPattern.VERTICAL -> {
                    var x = 0f
                    while (x <= size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), sw); x += gap }
                }
                BandPattern.DIAGONAL -> {
                    var o = -size.height
                    while (o <= size.width) {
                        drawLine(color, Offset(o, 0f), Offset(o + size.height, size.height), sw); o += gap
                    }
                }
                BandPattern.CROSS -> {
                    var o = -size.height
                    while (o <= size.width) {
                        drawLine(color, Offset(o, 0f), Offset(o + size.height, size.height), sw)
                        drawLine(color, Offset(o, size.height), Offset(o + size.height, 0f), sw)
                        o += gap
                    }
                }
                BandPattern.SOLID -> {}
            }
        }
        drawRect(color, style = Stroke(width = 1f))
    }
}

/** Coloured-dot legend over arbitrary (colour, label) entries. Wraps to fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompareEntryLegend(entries: List<Pair<Color, String>>) {
    if (entries.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        entries.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(9.dp)) { drawCircle(color) }
                Spacer(Modifier.size(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** x-axis subject labels — inset by the value-axis width so they sit under the plot. */
@Composable
private fun AxisLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Spacer(Modifier.width(Y_AXIS_W))
        labels.forEach { l ->
            Text(
                l,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
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

// ── colour encoding — single source of truth for chart + legend ─────────────
// A merged chart can encode data by SERIES (one subject, many series),
// by SUBJECT (many subjects, one series), or BLEND (many × many: subject hue
// with series-indexed alpha). The renderers and the legend builder both consult
// these so a legend swatch can never disagree with what's drawn.

enum class CompareColorMode { SERIES, SUBJECT, BLEND }

fun compareColorMode(subjectCount: Int, seriesCount: Int): CompareColorMode = when {
    subjectCount <= 1 -> CompareColorMode.SERIES
    seriesCount <= 1  -> CompareColorMode.SUBJECT
    else              -> CompareColorMode.BLEND
}

fun compareEncodedColor(
    mode: CompareColorMode, subjectIndex: Int, series: SeriesDef,
    seriesIndex: Int, seriesCount: Int
): Color = when (mode) {
    CompareColorMode.SERIES  -> series.color
    CompareColorMode.SUBJECT -> compareSubjectColor(subjectIndex)
    CompareColorMode.BLEND   -> compareSubjectColor(subjectIndex).copy(
        alpha = 0.55f + 0.45f * (1f - seriesIndex / max(1f, (seriesCount - 1).toFloat())))
}

/**
 * Legend entries (colour + label) matching exactly what [buildCompareLines] and
 * the bucketed-bar renderer draw, so a merged chart's legend always agrees with
 * the lines/bars on screen.
 */
fun compareLegendEntries(
    data: List<ChartDatum>, series: List<SeriesDef>
): List<Pair<Color, String>> {
    if (data.isEmpty() || series.isEmpty()) return emptyList()
    return when (compareColorMode(data.size, series.size)) {
        CompareColorMode.SERIES  -> series.map { it.color to it.label }
        CompareColorMode.SUBJECT -> data.mapIndexed { i, d -> compareSubjectColor(i) to d.title }
        CompareColorMode.BLEND   -> data.flatMapIndexed { di, d ->
            series.mapIndexed { si, s ->
                compareEncodedColor(CompareColorMode.BLEND, di, s, si, series.size) to
                    "${shortenLabel(d.title)} · ${s.label}"
            }
        }
    }
}

/**
 * Whether [CompareBarChart] uses the per-bucket renderer (coloured like the line
 * chart) rather than one totals bar per subject (always series-coloured).
 */
fun barIsBucketed(data: List<ChartDatum>, series: List<SeriesDef>): Boolean {
    val firstN = data.firstOrNull()?.axisLabels?.size ?: 0
    val sameN = firstN > 1 && data.all { it.axisLabels.size == firstN }
    return sameN && (data.size == 1 || series.size <= 2)
}
