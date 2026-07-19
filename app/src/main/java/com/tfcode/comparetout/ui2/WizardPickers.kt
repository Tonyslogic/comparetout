package com.tfcode.comparetout.ui2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import java.time.LocalDate
import androidx.compose.ui.geometry.Size as CanvasSize

/*
 * Wizard day/month/hour pickers, schedule block + shared field helpers — extracted verbatim from WizardScheduleComposables.kt (mega-refactor B3).
 * Imports inherited; unused are cosmetic.
 */

// Breakpoint at which wide (landscape) layouts activate
internal val WIDE_BREAKPOINT: Dp = 380.dp

/* ──────────────────────────────────────────────────────────────────
   Day picker  (0=Mon … 6=Sun, matching IntHolder backend)
   Portrait : single letter circles
   Landscape: "Mon"…"Sun" labels, fills full width
────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardDayPicker(
    selected: List<Int>,
    onSelectedChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayShort = stringArrayResource(R.array.ui2_days_letters_mon_first)
    val dayLong = stringArrayResource(R.array.ui2_days_short_mon_first)
    val dayFull = stringArrayResource(R.array.ui2_days_full_mon_first)
    val quickChips = listOf(
        stringResource(R.string.ui2_all)          to (0..6).toList(),
        stringResource(R.string.ui2_wiz_weekdays) to (0..4).toList(),
        stringResource(R.string.ui2_wiz_weekend)  to listOf(5, 6),
        stringResource(R.string.ui2_wiz_none)     to emptyList()
    )
    BoxWithConstraints(modifier = modifier) {
        val boxWidth = maxWidth
        val wide = boxWidth >= WIDE_BREAKPOINT
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (wide) {
                // FlowRow (no weight(1f)) so chip text grows with fontScale and
                // wraps onto extra lines instead of clipping. At WIDE+ we use
                // full day names ("Monday", "Tuesday") — the chips still wrap
                // when there's not enough room.
                val labels = if (boxWidth >= AdaptiveLayout.WIDTH_WIDE_AT) dayFull else dayLong
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    labels.forEachIndexed { idx, label ->
                        DayChipWide(label = label, on = selected.contains(idx)) {
                            onSelectedChange(toggleInt(selected, idx))
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayShort.forEachIndexed { idx, label ->
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
internal fun DayChipCircle(label: String, on: Boolean, onClick: () -> Unit) {
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
internal fun DayChipWide(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.widthIn(min = 44.dp).heightIn(min = 36.dp)
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
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/* ──────────────────────────────────────────────────────────────────
   Month picker  (1=Jan … 12=Dec, matching MonthHolder backend)
   Portrait : two rows of 6 with single-letter labels
   Landscape: single row with "Jan"…"Dec" labels, fills full width
────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardMonthPicker(
    selected: List<Int>,
    onSelectedChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthAbbrev = stringArrayResource(R.array.ui2_months_letters)
    val monthLong = stringArrayResource(R.array.ui2_months_short)
    val monthFull = stringArrayResource(R.array.ui2_months_full)
    val quickChips = listOf(
        stringResource(R.string.ui2_wiz_all_year) to (1..12).toList(),
        stringResource(R.string.ui2_wiz_winter)   to listOf(1, 2, 11, 12),
        stringResource(R.string.ui2_wiz_spring)   to listOf(3, 4, 5),
        stringResource(R.string.ui2_wiz_summer)   to listOf(6, 7, 8),
        stringResource(R.string.ui2_wiz_autumn)   to listOf(9, 10)
    )
    BoxWithConstraints(modifier = modifier) {
        val boxWidth = maxWidth
        val wide = boxWidth >= WIDE_BREAKPOINT
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (wide) {
                // Wrap-friendly month chip strip at fs≥1.6 — single Row would
                // clip each chip to ~32 dp; FlowRow sizes to content and wraps
                // onto multiple rows under font scaling. At ULTRA widths the
                // chips show full month names ("January", "February", …).
                val labels = if (boxWidth >= AdaptiveLayout.WIDTH_ULTRA_AT) monthFull else monthLong
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    labels.forEachIndexed { idx, label ->
                        val month = idx + 1
                        MonthChipWide(label = label, on = selected.contains(month)) {
                            onSelectedChange(toggleInt(selected, month))
                        }
                    }
                }
            } else {
                // Two rows of 6
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0..5).forEach { idx ->
                        val month = idx + 1
                        MonthChipWide(label = monthAbbrev[idx], on = selected.contains(month),
                            modifier = Modifier.weight(1f)) {
                            onSelectedChange(toggleInt(selected, month))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (6..11).forEach { idx ->
                        val month = idx + 1
                        MonthChipWide(label = monthAbbrev[idx], on = selected.contains(month),
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
internal fun MonthChipWide(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.widthIn(min = 36.dp).heightIn(min = 28.dp)
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
            maxLines = 1, overflow = TextOverflow.Visible,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
    // From/To dropdowns side-by-side at fs<1.6, stacked at fs>=1.6.
    data class HourSlot(val label: String, val hour: Int, val onChange: (Int) -> Unit)
    val slots = listOf(
        HourSlot(stringResource(R.string.ui2_wiz_from), startHour, onStartChange),
        HourSlot(stringResource(R.string.ui2_wiz_to), endHour, onEndChange),
    )
    // COMPACT: dropdowns above the range bar (stacked). MEDIUM+: dropdowns on
    // the left, range bar on the right so landscape / tablets use the width.
    AdaptiveTwoColumn(
        modifier = modifier,
        breakAt = AdaptiveLayout.WIDTH_MEDIUM_AT,
        primary = {
            AdaptiveCellRow(items = slots, perRowAtA = 2, perRowAtB = 2, perRowAtC = 1) { slot ->
                HourDropdown(label = slot.label, hour = slot.hour, onHourChange = slot.onChange)
            }
        },
        secondary = {
            HourRangeBar(startHour = startHour, endHour = endHour)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HourDropdown(label: String, hour: Int, onHourChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = "%02d:00".format(hour),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
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
internal fun HourRangeBar(startHour: Int, endHour: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            (0..23).forEach { h ->
                val active = if (startHour == endHour) false
                else if (startHour < endHour) h in startHour until endHour
                else h !in endHour..<startHour
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
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_time_window))
        WizardHourRangePicker(startHour = startHour, endHour = endHour,
            onStartChange = onStartHourChange, onEndChange = onEndHourChange)
        if (noviceMode && startHour > endHour) {
            Text(
                text = stringResource(R.string.ui2_wiz_crosses_midnight,
                    fmtHour(startHour), fmtHour(endHour)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_days_of_week))
        WizardDayPicker(selected = days, onSelectedChange = onDaysChange)
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_months))
        WizardMonthPicker(selected = months, onSelectedChange = onMonthsChange)
    }
}

/* ──────────────────────────────────────────────────────────────────
   Shared helpers
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardScheduleLabel(text: String) {
    Text(text = text.uppercase(), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline)
}

/* ──────────────────────────────────────────────────────────────────
   Buffered numeric fields — keep a local string buffer so partial /
   empty input stays in the field while the user is editing. The
   canonical Int/Double model only updates when the buffer parses.
   External value changes (e.g., size→rate auto-sync) push back into
   the buffer iff the buffer's current parse disagrees, so user typing
   isn't clobbered by their own keystroke producing a model update.
────────────────────────────────────────────────────────────────── */

@Composable
fun NumericIntField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    range: IntRange? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            new.toIntOrNull()?.let { parsed ->
                val out = range?.let { parsed.coerceIn(it.first, it.last) } ?: parsed
                if (out != value) onValueChange(out)
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = supportingText
    )
}

@Composable
fun NumericDoubleField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Double>? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toDoubleOrNull() != value) text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            new.toDoubleOrNull()?.let { parsed ->
                val out = range?.let { parsed.coerceIn(it.start, it.endInclusive) } ?: parsed
                if (out != value) onValueChange(out)
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = supportingText
    )
}

@Composable
internal fun QuickChip(label: String, onClick: () -> Unit) {
    SuggestionChip(onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

internal fun toggleInt(list: List<Int>, value: Int): List<Int> =
    if (list.contains(value)) (list - value).sorted() else (list + value).sorted()

fun fmtHour(h: Int): String = "%02d:00".format(h)

@Composable
fun fmtDays(days: List<Int>): String {
    if (days.isEmpty()) return stringResource(R.string.ui2_wiz_no_days)
    if (days.size == 7) return stringResource(R.string.ui2_wiz_every_day)
    if (days.sorted() == (0..4).toList()) return stringResource(R.string.ui2_wiz_weekdays)
    if (days.sorted() == listOf(5, 6)) return stringResource(R.string.ui2_wiz_weekend)
    val names = stringArrayResource(R.array.ui2_days_short_mon_first)
    return days.sorted().joinToString { names.getOrElse(it) { idx -> idx.toString() } }
}
