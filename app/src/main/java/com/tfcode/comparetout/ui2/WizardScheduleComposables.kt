package com.tfcode.comparetout.ui2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter

// Breakpoint at which wide (landscape) layouts activate
private val WIDE_BREAKPOINT: Dp = 380.dp

/* ──────────────────────────────────────────────────────────────────
   Day picker  (0=Mon … 6=Sun, matching IntHolder backend)
   Portrait : single letter circles
   Landscape: "Mon"…"Sun" labels, fills full width
────────────────────────────────────────────────────────────────── */

private val DAY_SHORT  = listOf("M","T","W","T","F","S","S")
private val DAY_LONG   = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardDayPicker(
    selected: List<Int>,
    onSelectedChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickChips = listOf(
        "All"      to (0..6).toList(),
        "Weekdays" to (0..4).toList(),
        "Weekend"  to listOf(5, 6),
        "None"     to emptyList()
    )
    BoxWithConstraints(modifier = modifier) {
        val wide = maxWidth >= WIDE_BREAKPOINT
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (wide) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LONG.forEachIndexed { idx, label ->
                        DayChipWide(label = label, on = selected.contains(idx),
                            modifier = Modifier.weight(1f)) {
                            onSelectedChange(toggleInt(selected, idx))
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_SHORT.forEachIndexed { idx, label ->
                        DayChipCircle(label = label, on = selected.contains(idx)) {
                            onSelectedChange(toggleInt(selected, idx))
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickChips.forEach { (label, days) ->
                    QuickChip(label) { onSelectedChange(days) }
                }
            }
        }
    }
}

@Composable
private fun DayChipCircle(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp)
            .clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp,
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayChipWide(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp,
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1)
    }
}

/* ──────────────────────────────────────────────────────────────────
   Month picker  (1=Jan … 12=Dec, matching MonthHolder backend)
   Portrait : two rows of 6 with single-letter labels
   Landscape: single row with "Jan"…"Dec" labels, fills full width
────────────────────────────────────────────────────────────────── */

private val MONTH_ABBREV = listOf("J","F","M","A","M","J","J","A","S","O","N","D")
private val MONTH_LONG   = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardMonthPicker(
    selected: List<Int>,
    onSelectedChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickChips = listOf(
        "All year" to (1..12).toList(),
        "Winter"   to listOf(1, 2, 11, 12),
        "Spring"   to listOf(3, 4, 5),
        "Summer"   to listOf(6, 7, 8),
        "Autumn"   to listOf(9, 10)
    )
    BoxWithConstraints(modifier = modifier) {
        val wide = maxWidth >= WIDE_BREAKPOINT
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (wide) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    MONTH_LONG.forEachIndexed { idx, label ->
                        val month = idx + 1
                        MonthChipWide(label = label, on = selected.contains(month),
                            modifier = Modifier.weight(1f)) {
                            onSelectedChange(toggleInt(selected, month))
                        }
                    }
                }
            } else {
                // Two rows of 6
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0..5).forEach { idx ->
                        val month = idx + 1
                        MonthChipWide(label = MONTH_ABBREV[idx], on = selected.contains(month),
                            modifier = Modifier.weight(1f)) {
                            onSelectedChange(toggleInt(selected, month))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (6..11).forEach { idx ->
                        val month = idx + 1
                        MonthChipWide(label = MONTH_ABBREV[idx], on = selected.contains(month),
                            modifier = Modifier.weight(1f)) {
                            onSelectedChange(toggleInt(selected, month))
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickChips.forEach { (label, months) ->
                    QuickChip(label) { onSelectedChange(months) }
                }
            }
        }
    }
}

@Composable
private fun MonthChipWide(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp,
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Visible)
    }
}

/* ──────────────────────────────────────────────────────────────────
   Hour range picker + visual bar
────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardHourRangePicker(
    startHour: Int,
    endHour: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HourDropdown(label = "From", hour = startHour, onHourChange = onStartChange,
                modifier = Modifier.weight(1f))
            HourDropdown(label = "To", hour = endHour, onHourChange = onEndChange,
                modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        HourRangeBar(startHour = startHour, endHour = endHour)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HourDropdown(label: String, hour: Int, onHourChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = "%02d:00".format(hour),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (0..23).forEach { h ->
                DropdownMenuItem(
                    text = { Text("%02d:00".format(h), style = MaterialTheme.typography.bodyMedium) },
                    onClick = { onHourChange(h); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun HourRangeBar(startHour: Int, endHour: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            (0..23).forEach { h ->
                val active = if (startHour == endHour) false
                else if (startHour < endHour) h in startHour until endHour
                else h >= startHour || h < endHour
                Box(modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (active) primary else surface))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Reusable schedule block: time + days + months grouped
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardScheduleBlock(
    startHour: Int,
    endHour: Int,
    days: List<Int>,
    months: List<Int>,
    onStartHourChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
    onDaysChange: (List<Int>) -> Unit,
    onMonthsChange: (List<Int>) -> Unit,
    noviceMode: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WizardScheduleLabel("Time window")
        WizardHourRangePicker(startHour = startHour, endHour = endHour,
            onStartChange = onStartHourChange, onEndChange = onEndHourChange)
        if (noviceMode && startHour > endHour) {
            Text(
                text = "Crosses midnight — runs ${fmtHour(startHour)} until ${fmtHour(endHour)} next day.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        WizardScheduleLabel("Days of week")
        WizardDayPicker(selected = days, onSelectedChange = onDaysChange)
        WizardScheduleLabel("Months")
        WizardMonthPicker(selected = months, onSelectedChange = onMonthsChange)
    }
}

/* ──────────────────────────────────────────────────────────────────
   EV entry card — expandable with name, draw, and schedule
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardEvEntryCard(
    entry: WizardEvEntry,
    index: Int,
    expanded: Boolean,
    noviceMode: Boolean,
    onToggle: () -> Unit,
    onUpdate: (WizardEvEntry) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp,
                if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center) {
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name.ifBlank { "EV Schedule" },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${fmtHour(entry.startHour)}–${fmtHour(entry.endHour)}  ·  ${entry.drawKw} kW  ·  ${fmtDays(entry.days)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = entry.name,
                    onValueChange = { onUpdate(entry.copy(name = it)) },
                    label = { Text("Schedule name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(value = entry.drawKw.toString(),
                    onValueChange = { v -> v.toDoubleOrNull()?.let { onUpdate(entry.copy(drawKw = it)) } },
                    label = { Text("Draw rate (kW)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (noviceMode) {
                    Text("How much power the charger draws from the grid during the window.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                WizardScheduleBlock(
                    startHour = entry.startHour, endHour = entry.endHour,
                    days = entry.days, months = entry.months,
                    onStartHourChange = { onUpdate(entry.copy(startHour = it)) },
                    onEndHourChange = { onUpdate(entry.copy(endHour = it)) },
                    onDaysChange = { onUpdate(entry.copy(days = it)) },
                    onMonthsChange = { onUpdate(entry.copy(months = it)) },
                    noviceMode = noviceMode
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove")
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Load profile distribution charts
   Mirrors DataSourceDistributionCharts in the dashboard.
   Uses a pure-Compose bar chart to avoid a second AndroidView reference.
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardDistributionCharts(
    hourly: List<Double>,
    daily: List<Double>,
    monthly: List<Double>,
    modifier: Modifier = Modifier
) {
    val hourLabels  = (0..23).map { "%02d".format(it) }
    val dayLabels   = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
    val monthLabels = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    var zoomedIdx by remember { mutableStateOf(-1) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (zoomedIdx >= 0) {
            WizardDistBarChart(
                title = when (zoomedIdx) {
                    0 -> "Hourly distribution (%)"
                    1 -> "Day-of-week distribution (%)"
                    else -> "Monthly distribution (%)"
                },
                values = when (zoomedIdx) { 0 -> hourly; 1 -> daily; else -> monthly },
                labels = when (zoomedIdx) { 0 -> hourLabels; 1 -> dayLabels; else -> monthLabels },
                showEveryNthLabel = if (zoomedIdx == 0) 6 else 1,
                barHeight = 160.dp,
                onClick = { zoomedIdx = -1 }
            )
            Text("Tap chart to zoom out",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        } else {
            WizardDistBarChart(title = "Hourly distribution (%)", values = hourly,
                labels = hourLabels, showEveryNthLabel = 6, barHeight = 60.dp, onClick = { zoomedIdx = 0 })
            WizardDistBarChart(title = "Day-of-week distribution (%)", values = daily,
                labels = dayLabels, showEveryNthLabel = 1, barHeight = 60.dp, onClick = { zoomedIdx = 1 })
            WizardDistBarChart(title = "Monthly distribution (%)", values = monthly,
                labels = monthLabels, showEveryNthLabel = 1, barHeight = 60.dp, onClick = { zoomedIdx = 2 })
            Text("Tap a chart to zoom in",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun WizardDistBarChart(
    title: String,
    values: List<Double>,
    labels: List<String>,
    showEveryNthLabel: Int = 1,
    barHeight: Dp = 60.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = MaterialTheme.colorScheme.primary.toArgb()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp))
        AndroidView(
            factory = { ctx ->
                BarChart(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    styleWizardDistBarChart(this, labelColorArgb, gridColorArgb)
                }
            },
            update = { chart ->
                styleWizardDistBarChart(chart, labelColorArgb, gridColorArgb)
                val filteredLabels = labels.mapIndexed { i, l -> if (i % showEveryNthLabel == 0) l else "" }
                val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
                val ds = BarDataSet(entries, "").apply {
                    color = barColorArgb
                    setDrawValues(false)
                }
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) =
                        filteredLabels.getOrElse(value.toInt()) { "" }
                }
                chart.data = BarData(ds)
                chart.invalidate()
            },
            modifier = Modifier.fillMaxWidth().height(barHeight)
        )
    }
}

private fun styleWizardDistBarChart(chart: BarChart, labelColor: Int, gridColor: Int) {
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
    chart.axisLeft.setLabelCount(2, true)
    chart.axisLeft.setDrawGridLines(false)
    chart.axisLeft.textColor = labelColor
    chart.axisLeft.textSize = 8f
    chart.axisLeft.resetAxisMinimum()
    chart.axisLeft.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float) = "%.1f%%".format(value)
    }
    chart.axisRight.isEnabled = false
    chart.legend.isEnabled = false
    chart.setScaleEnabled(false)
}

/* ──────────────────────────────────────────────────────────────────
   Shared helpers
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardScheduleLabel(text: String) {
    Text(text = text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    SuggestionChip(onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

private fun toggleInt(list: List<Int>, value: Int): List<Int> =
    if (list.contains(value)) (list - value).sorted() else (list + value).sorted()

fun fmtHour(h: Int): String = "%02d:00".format(h)

fun fmtDays(days: List<Int>): String {
    if (days.isEmpty()) return "(no days)"
    if (days.size == 7) return "Every day"
    if (days.sorted() == (0..4).toList()) return "Weekdays"
    if (days.sorted() == listOf(5, 6)) return "Weekend"
    val names = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
    return days.sorted().joinToString { names.getOrElse(it) { it.toString() } }
}
