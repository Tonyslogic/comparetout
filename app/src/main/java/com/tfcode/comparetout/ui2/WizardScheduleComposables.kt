@file:Suppress("AssignedValueIsNeverRead")

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

// Breakpoint at which wide (landscape) layouts activate
private val WIDE_BREAKPOINT: Dp = 380.dp

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
private fun MonthChipWide(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
private fun HourDropdown(label: String, hour: Int, onHourChange: (Int) -> Unit, modifier: Modifier = Modifier) {
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
private fun HourRangeBar(startHour: Int, endHour: Int) {
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
                Text(entry.name.ifBlank { stringResource(R.string.ui2_wiz_ev_schedule) },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${fmtHour(entry.startHour)}–${fmtHour(entry.endHour)}  ·  ${entry.drawKw} kW  ·  ${fmtDays(entry.days)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.ui2_collapse else R.string.ui2_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = entry.name,
                    onValueChange = { onUpdate(entry.copy(name = it)) },
                    label = { Text(stringResource(R.string.ui2_wiz_schedule_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                NumericDoubleField(
                    value = entry.drawKw,
                    onValueChange = { onUpdate(entry.copy(drawKw = it)) },
                    label = stringResource(R.string.ui2_wiz_draw_rate),
                    modifier = Modifier.fillMaxWidth()
                )
                if (noviceMode) {
                    Text(stringResource(R.string.ui2_wiz_draw_rate_hint),
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
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_remove))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   EV divert card — expandable with name, active, ev1st, dailyMax,
   minimum, and schedule
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardEvDivertCard(
    entry: WizardEvDivertEntry,
    index: Int,
    expanded: Boolean,
    noviceMode: Boolean,
    onToggle: () -> Unit,
    onUpdate: (WizardEvDivertEntry) -> Unit,
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
                Text(entry.name.ifBlank { stringResource(R.string.ui2_wiz_ev_divert) },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${fmtHour(entry.beginHour)}–${fmtHour(entry.endHour)}  ·  ${entry.dailyMax} kWh/day  ·  ${fmtDays(entry.days)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!entry.active) {
                Text(stringResource(R.string.ui2_dash_off_suffix),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(6.dp))
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.ui2_collapse else R.string.ui2_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                OutlinedTextField(value = entry.name,
                    onValueChange = { onUpdate(entry.copy(name = it)) },
                    label = { Text(stringResource(R.string.ui2_wiz_divert_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui2_wiz_active),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold)
                        if (noviceMode) Text(stringResource(R.string.ui2_wiz_active_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = entry.active, onCheckedChange = { onUpdate(entry.copy(active = it)) })
                }

                if (!noviceMode) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ui2_wiz_ev_priority),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.ui2_wiz_ev_priority_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = entry.ev1st, onCheckedChange = { onUpdate(entry.copy(ev1st = it)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericDoubleField(
                            value = entry.dailyMax,
                            onValueChange = { onUpdate(entry.copy(dailyMax = it)) },
                            label = stringResource(R.string.ui2_wiz_daily_max),
                            modifier = Modifier.weight(1f)
                        )
                        NumericDoubleField(
                            value = entry.minimum,
                            onValueChange = { onUpdate(entry.copy(minimum = it)) },
                            label = stringResource(R.string.ui2_wiz_min_excess),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                WizardScheduleBlock(
                    startHour = entry.beginHour, endHour = entry.endHour,
                    days = entry.days, months = entry.months,
                    onStartHourChange = { onUpdate(entry.copy(beginHour = it)) },
                    onEndHourChange = { onUpdate(entry.copy(endHour = it)) },
                    onDaysChange = { onUpdate(entry.copy(days = it)) },
                    onMonthsChange = { onUpdate(entry.copy(months = it)) },
                    noviceMode = noviceMode
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_remove))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Inverter card — expandable, mirrors WizardEvEntryCard
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardInverterCard(
    entry: WizardInverterEntry,
    index: Int,
    expanded: Boolean,
    noviceMode: Boolean,
    onToggle: () -> Unit,
    onUpdate: (WizardInverterEntry) -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.inverterName.ifBlank { stringResource(R.string.ui2_component_inverter) },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${entry.maxInverterLoad} kW  ·  ${entry.mpptCount} MPPT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.ui2_collapse else R.string.ui2_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = entry.inverterName,
                    onValueChange = { onUpdate(entry.copy(inverterName = it)) },
                    label = { Text(stringResource(R.string.ui2_wiz_inverter_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericDoubleField(
                        value = entry.maxInverterLoad,
                        onValueChange = { onUpdate(entry.copy(maxInverterLoad = it)) },
                        label = stringResource(R.string.ui2_wiz_max_load),
                        modifier = Modifier.weight(1f),
                        supportingText = if (noviceMode)
                            ({ Text(stringResource(R.string.ui2_wiz_max_load_hint)) }) else null
                    )
                    NumericIntField(
                        value = entry.mpptCount,
                        onValueChange = { onUpdate(entry.copy(mpptCount = it)) },
                        label = stringResource(R.string.ui2_wiz_mppt_count),
                        modifier = Modifier.weight(1f),
                        range = 1..8,
                        supportingText = if (noviceMode)
                            ({ Text(stringResource(R.string.ui2_wiz_mppt_count_hint)) }) else null
                    )
                }
                if (!noviceMode) {
                    NumericDoubleField(
                        value = entry.minExcess,
                        onValueChange = { onUpdate(entry.copy(minExcess = it)) },
                        label = stringResource(R.string.ui2_wiz_min_excess),
                        modifier = Modifier.fillMaxWidth()
                    )
                    data class LossField(
                        val value: Int,
                        val label: String,
                        val onChange: (Int) -> Unit,
                    )
                    val lossFields = listOf(
                        LossField(entry.ac2dcLoss, stringResource(R.string.ui2_wiz_ac2dc_loss)) {
                            onUpdate(entry.copy(ac2dcLoss = it))
                        },
                        LossField(entry.dc2acLoss, stringResource(R.string.ui2_wiz_dc2ac_loss)) {
                            onUpdate(entry.copy(dc2acLoss = it))
                        },
                        LossField(entry.dc2dcLoss, stringResource(R.string.ui2_wiz_dc2dc_loss)) {
                            onUpdate(entry.copy(dc2dcLoss = it))
                        },
                    )
                    AdaptiveCellRow(
                        items = lossFields,
                        perRowAtA = 3, perRowAtB = 3, perRowAtC = 1,
                        spacing = 8.dp
                    ) { f ->
                        NumericIntField(
                            value = f.value,
                            onValueChange = f.onChange,
                            label = f.label,
                            modifier = Modifier.fillMaxWidth(),
                            range = 0..50
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_remove))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Panel (PV string) card — expandable, mirrors WizardInverterCard
────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WizardPanelCard(
    entry: WizardPanelEntry,
    index: Int,
    expanded: Boolean,
    noviceMode: Boolean,
    inverterEntries: List<WizardInverterEntry>,
    availableSources: List<SourceDateRange>,
    modifier: Modifier = Modifier,
    panelMonthlySummary: List<PanelPVSummary> = emptyList(),
    pvgisParamsHaveData: Boolean = false,
    showAdvanced: Boolean = false,
    onToggle: () -> Unit,
    onUpdate: (WizardPanelEntry) -> Unit,
    onDelete: () -> Unit,
    onRequestLocation: () -> Unit,
    onCheckPvgisParams: () -> Unit = {},
) {
    // Auto-select inverter when exactly one exists and none is selected
    LaunchedEffect(inverterEntries.size, entry.inverterName) {
        if (inverterEntries.size == 1 && entry.inverterName.isBlank()) {
            onUpdate(entry.copy(inverterName = inverterEntries[0].inverterName))
        }
    }

    // Trigger parameter-based PVGIS check whenever PVGIS is selected or params change
    LaunchedEffect(entry.pvDataSource, entry.latitude, entry.longitude, entry.azimuth, entry.slope) {
        if (entry.pvDataSource == PanelDataSource.PVGIS) onCheckPvgisParams()
    }

    val monthlyKwh = remember(panelMonthlySummary) { panelMonthlySummary.toMonthlyKwh() }
    val hasData = remember(monthlyKwh) { monthlyKwh.any { it > 0 } }

    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                1.dp,
                if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.panelName.ifBlank { stringResource(R.string.ui2_wiz_string_n, index + 1) },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${entry.panelCount} × ${entry.panelkWp} W",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Mini monthly bar chart when data already exists (collapsed preview)
                if (!expanded && hasData) {
                    Spacer(Modifier.height(4.dp))
                    PanelMiniMonthlyBars(monthlyKwh = monthlyKwh)
                }
            }
            if (entry.pvDataSource != PanelDataSource.NONE) {
                Text(
                    stringResource(if (entry.pvDataSource == PanelDataSource.PVGIS)
                        R.string.brand_pvgis else R.string.ui2_wiz_src_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = entry.panelName,
                    onValueChange = { onUpdate(entry.copy(panelName = it)) },
                    label = { Text(stringResource(R.string.ui2_wiz_string_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericIntField(
                        value = entry.panelCount,
                        onValueChange = { onUpdate(entry.copy(panelCount = it)) },
                        label = stringResource(R.string.ui2_wiz_count),
                        modifier = Modifier.weight(1f),
                        range = 1..100,
                        supportingText = if (noviceMode)
                            ({ Text(stringResource(R.string.ui2_wiz_count_hint)) }) else null
                    )
                    NumericIntField(
                        value = entry.panelkWp,
                        onValueChange = { onUpdate(entry.copy(panelkWp = it)) },
                        label = stringResource(R.string.ui2_wiz_wp_panel),
                        modifier = Modifier.weight(1f),
                        range = 50..1000,
                        supportingText = if (noviceMode)
                            ({ Text(stringResource(R.string.ui2_wiz_wp_hint)) }) else null
                    )
                }
                // Location
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumericDoubleField(
                        value = entry.latitude,
                        onValueChange = { onUpdate(entry.copy(latitude = it)) },
                        label = stringResource(R.string.ui2_wiz_lat),
                        modifier = Modifier.weight(1f)
                    )
                    NumericDoubleField(
                        value = entry.longitude,
                        onValueChange = { onUpdate(entry.copy(longitude = it)) },
                        label = stringResource(R.string.ui2_wiz_long),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRequestLocation) {
                        Icon(Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.ui2_wiz_use_gps),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                // Orientation
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericIntField(
                        value = entry.azimuth,
                        onValueChange = { onUpdate(entry.copy(azimuth = it)) },
                        label = stringResource(R.string.ui2_wiz_azimuth),
                        modifier = Modifier.weight(1f),
                        range = 0..360,
                        supportingText = if (noviceMode)
                            ({ Text(stringResource(R.string.ui2_wiz_azimuth_hint)) }) else null
                    )
                    NumericIntField(
                        value = entry.slope,
                        onValueChange = { onUpdate(entry.copy(slope = it)) },
                        label = stringResource(R.string.ui2_wiz_slope),
                        modifier = Modifier.weight(1f),
                        range = 0..90,
                        supportingText = if (noviceMode)
                            ({ Text(stringResource(R.string.ui2_wiz_slope_hint)) }) else null
                    )
                }
                // Inverter + MPPT dropdown
                if (inverterEntries.isNotEmpty()) {
                    val maxMppt = inverterEntries.find { it.inverterName == entry.inverterName }?.mpptCount ?: 1
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (inverterEntries.size == 1) {
                            OutlinedTextField(
                                value = inverterEntries[0].inverterName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.ui2_component_inverter)) },
                                modifier = Modifier.weight(2f), singleLine = true
                            )
                        } else {
                            var invMenu by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(2f)) {
                                OutlinedTextField(
                                    value = entry.inverterName.ifBlank {
                                        stringResource(R.string.ui2_wiz_select_ellipsis) },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(stringResource(R.string.ui2_component_inverter)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        Icon(Icons.Default.KeyboardArrowDown, null,
                                            Modifier.clickable { invMenu = true })
                                    }
                                )
                                DropdownMenu(expanded = invMenu, onDismissRequest = { invMenu = false }) {
                                    inverterEntries.forEach { inv ->
                                        DropdownMenuItem(
                                            text = { Text(inv.inverterName) },
                                            onClick = { onUpdate(entry.copy(inverterName = inv.inverterName, mppt = 1)); invMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                        // MPPT port dropdown
                        var mpptMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = entry.mppt.toString(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.ui2_wiz_mppt)) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    Icon(Icons.Default.KeyboardArrowDown, null,
                                        Modifier.clickable { mpptMenu = true })
                                }
                            )
                            DropdownMenu(expanded = mpptMenu, onDismissRequest = { mpptMenu = false }) {
                                (1..maxMppt).forEach { port ->
                                    DropdownMenuItem(
                                        text = { Text(port.toString()) },
                                        onClick = { onUpdate(entry.copy(mppt = port)); mpptMenu = false }
                                    )
                                }
                            }
                        }
                    }
                } else if (!noviceMode) {
                    OutlinedTextField(
                        value = entry.inverterName,
                        onValueChange = { onUpdate(entry.copy(inverterName = it)) },
                        label = { Text(stringResource(R.string.ui2_wiz_inverter_name)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
                // Data source
                val pvSources = remember(availableSources) {
                    // Meter-only sources (ESBN, Octopus) carry no PV series.
                    availableSources.filter {
                        it.importerType != ComparisonUIViewModel.Importer.ESBNHDF &&
                            it.importerType != ComparisonUIViewModel.Importer.OCTOPUS
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.ui2_wiz_panel_data),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    // Lift the selected chip to primaryContainer + a 1.5dp primary border so the active source
                    // is obvious — matches the Compare screen's chip styling (default secondaryContainer fill
                    // was too close to surface to read).
                    val pvChipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    @Composable
                    fun pvChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = selected,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                        selectedBorderWidth = 1.5.dp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = entry.pvDataSource == PanelDataSource.NONE,
                            onClick = { onUpdate(entry.copy(pvDataSource = PanelDataSource.NONE, pvSourceSysSn = "")) },
                            label = { Text(stringResource(R.string.ui2_wiz_none)) },
                            colors = pvChipColors,
                            border = pvChipBorder(entry.pvDataSource == PanelDataSource.NONE)
                        )
                        FilterChip(
                            selected = entry.pvDataSource == PanelDataSource.PVGIS,
                            onClick = { onUpdate(entry.copy(pvDataSource = PanelDataSource.PVGIS, pvSourceSysSn = "")) },
                            label = { Text(stringResource(R.string.brand_pvgis)) },
                            leadingIcon = {
                                Icon(painterResource(R.drawable.ic_baseline_wb_sunny_36), null,
                                    Modifier.size(16.dp), tint = Color.Unspecified)
                            },
                            colors = pvChipColors,
                            border = pvChipBorder(entry.pvDataSource == PanelDataSource.PVGIS)
                        )
                        // Show the Source chip in Advanced, or when this panel is already on a historical source
                        // (recorded in the DB) — so its selection stays visible without forcing Advanced.
                        if (showAdvanced || entry.pvDataSource == PanelDataSource.SOURCE) {
                            FilterChip(
                                selected = entry.pvDataSource == PanelDataSource.SOURCE,
                                onClick = { onUpdate(entry.copy(pvDataSource = PanelDataSource.SOURCE)) },
                                label = { Text(stringResource(R.string.ui2_habf_source)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                                },
                                colors = pvChipColors,
                                border = pvChipBorder(entry.pvDataSource == PanelDataSource.SOURCE)
                            )
                        }
                    }
                    if (entry.pvDataSource == PanelDataSource.PVGIS) {
                        Text(
                            stringResource(R.string.ui2_wiz_pvgis_params,
                                "%.3f".format(entry.latitude), "%.3f".format(entry.longitude),
                                entry.slope, entry.azimuth),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // PVGIS computes the power server-side and derates per-location temperature itself; this
                        // is the flat non-temperature system loss it can't know. A change re-fetches (it's part of
                        // panelFetchInputsChanged), so it lives with the other PVGIS settings.
                        NumericIntField(
                            value = entry.systemLoss,
                            onValueChange = { onUpdate(entry.copy(systemLoss = it)) },
                            label = stringResource(R.string.ui2_wiz_system_loss),
                            modifier = Modifier.fillMaxWidth(),
                            range = 0..30,
                            supportingText = if (noviceMode)
                                ({ Text(stringResource(R.string.ui2_wiz_system_loss_hint)) }) else null
                        )
                        Spacer(Modifier.height(4.dp))
                        when {
                            hasData -> {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.ui2_wiz_data_fetched,
                                        "%.0f".format(monthlyKwh.sum())),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                PanelMonthlyBarChart(
                                    monthlyKwh = monthlyKwh,
                                    modifier = Modifier.fillMaxWidth().height(100.dp)
                                )
                            }
                            pvgisParamsHaveData -> {
                                Text(
                                    stringResource(R.string.ui2_wiz_data_in_system),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            else -> {
                                Text(
                                    stringResource(R.string.ui2_wiz_will_fetch),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    // SOURCE loaded from the DB but its import data is no longer available to re-pick: show the
                    // recorded source + date range read-only (these are the dates the CDS weather query uses).
                    if (entry.pvDataSource == PanelDataSource.SOURCE && pvSources.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(stringResource(R.string.ui2_dash_source,
                                    entry.pvSourceSysSn.ifBlank { "—" }),
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            if (entry.pvSourceFrom.isNotBlank() && entry.pvSourceTo.isNotBlank()) {
                                Text("${entry.pvSourceFrom} → ${entry.pvSourceTo}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (entry.pvDataSource == PanelDataSource.SOURCE && pvSources.isNotEmpty()) {
                        var showSourceDialog by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp))
                                .clickable { showSourceDialog = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (entry.pvSourceSysSn.isBlank()) {
                                Text(stringResource(R.string.ui2_wiz_select_source),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.pvSourceSysSn,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("${entry.pvSourceFrom} → ${entry.pvSourceTo}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
                        }
                        if (showSourceDialog) {
                            PvSourceDialog(
                                sources      = pvSources,
                                currentSysSn = entry.pvSourceSysSn,
                                onApply      = { src ->
                                    // A newly-chosen source resets to its full date
                                    // range; the advanced date picker narrows it.
                                    onUpdate(
                                        if (src.sysSn != entry.pvSourceSysSn)
                                            entry.copy(
                                                pvSourceSysSn = src.sysSn,
                                                pvSourceFrom  = src.startDate,
                                                pvSourceTo    = src.finishDate
                                            )
                                        else entry.copy(pvSourceSysSn = src.sysSn)
                                    )
                                },
                                onDismiss    = { showSourceDialog = false }
                            )
                        }
                        if (entry.pvSourceSysSn.isNotBlank()) {
                            Text(
                                stringResource(R.string.ui2_wiz_on_save_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (showAdvanced) {
                                // Date range — moved out of the source dialog so it
                                // sits inline with the other advanced source controls.
                                val pvSrc = pvSources.firstOrNull { it.sysSn == entry.pvSourceSysSn }
                                if (pvSrc != null) {
                                    var pvPeriod by remember(entry.pvSourceSysSn) {
                                        mutableStateOf(DataSourcePeriod.ALL)
                                    }
                                    var pvAnchor by remember(entry.pvSourceSysSn) {
                                        mutableStateOf(
                                            runCatching { LocalDate.parse(entry.pvSourceTo) }.getOrNull()
                                                ?: runCatching { LocalDate.parse(pvSrc.finishDate) }.getOrNull()
                                                ?: LocalDate.now()
                                        )
                                    }
                                    fun applyPvRange(p: DataSourcePeriod, a: LocalDate) {
                                        val startD  = LocalDate.parse(pvSrc.startDate)
                                        val finishD = LocalDate.parse(pvSrc.finishDate)
                                        val (rawFrom, rawTo) = periodDateRange(p, a, true, startD, finishD)
                                        onUpdate(entry.copy(
                                            pvSourceFrom = rawFrom.coerceIn(startD, finishD).toString(),
                                            pvSourceTo   = rawTo.coerceIn(startD, finishD).toString()
                                        ))
                                    }
                                    WizardScheduleLabel(stringResource(R.string.ui2_wiz_source_date_range))
                                    PeriodSelector(
                                        selectedPeriod = pvPeriod,
                                        anchorDate     = pvAnchor,
                                        dataStart      = pvSrc.startDate,
                                        dataEnd        = pvSrc.finishDate,
                                        advanced       = true,
                                        onPeriodChange = { p, a, _ ->
                                            pvPeriod = p; pvAnchor = a; applyPvRange(p, a)
                                        },
                                        onNavigate     = { fwd, _ ->
                                            val a = stepAnchor(pvAnchor, pvPeriod, fwd,
                                                LocalDate.parse(pvSrc.startDate),
                                                LocalDate.parse(pvSrc.finishDate))
                                            pvAnchor = a; applyPvRange(pvPeriod, a)
                                        }
                                    )
                                    Text("${entry.pvSourceFrom} → ${entry.pvSourceTo}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val stringKwp = entry.panelCount * entry.panelkWp / 1000.0
                                val displayedKwp = if (entry.pvSourceKwp > 0.0) entry.pvSourceKwp else stringKwp
                                NumericDoubleField(
                                    value = displayedKwp,
                                    onValueChange = { v ->
                                        if (v > 0.0) onUpdate(entry.copy(pvSourceKwp = v))
                                    },
                                    label = stringResource(R.string.ui2_wiz_source_kwp),
                                    modifier = Modifier.fillMaxWidth(),
                                    supportingText = {
                                        Text(stringResource(R.string.ui2_wiz_source_kwp_hint,
                                            "%.2f".format(stringKwp)))
                                    }
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.ui2_wiz_azimuth_factoring),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(R.string.ui2_wiz_azimuth_factoring_hint),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = entry.pvUseAzimuthFactor,
                                        onCheckedChange = { enabled ->
                                            if (enabled && entry.pvSourceAzimuth < 0) {
                                                onUpdate(entry.copy(
                                                    pvUseAzimuthFactor = true,
                                                    pvSourceAzimuth = entry.azimuth
                                                ))
                                            } else {
                                                onUpdate(entry.copy(pvUseAzimuthFactor = enabled))
                                            }
                                        }
                                    )
                                }
                                if (entry.pvUseAzimuthFactor) {
                                    val displayedSrcAz = if (entry.pvSourceAzimuth in 0..360) entry.pvSourceAzimuth else entry.azimuth
                                    NumericIntField(
                                        value = displayedSrcAz,
                                        onValueChange = { onUpdate(entry.copy(pvSourceAzimuth = it)) },
                                        label = stringResource(R.string.ui2_wiz_source_azimuth),
                                        modifier = Modifier.fillMaxWidth(),
                                        range = 0..360,
                                        supportingText = {
                                            Text(stringResource(R.string.ui2_wiz_source_azimuth_hint,
                                                entry.azimuth))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                // Advanced: Optimised — shown when section-level advanced tab is active
                if (showAdvanced) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.ui2_wiz_optimised),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.ui2_wiz_optimised_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = entry.connectionMode == 1,
                            onCheckedChange = { onUpdate(entry.copy(connectionMode = if (it) 1 else 0)) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_remove))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Battery card — expandable, mirrors WizardInverterCard
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardBatteryCard(
    entry: WizardBatteryEntry,
    index: Int,
    expanded: Boolean,
    noviceMode: Boolean,
    showAdvanced: Boolean,
    inverterEntries: List<WizardInverterEntry>,
    onToggle: () -> Unit,
    onUpdate: (WizardBatteryEntry) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-select inverter when exactly one exists and none is selected
    LaunchedEffect(inverterEntries.size, entry.inverterName) {
        if (inverterEntries.size == 1 && entry.inverterName.isBlank()) {
            onUpdate(entry.copy(inverterName = inverterEntries[0].inverterName))
        }
    }

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
                Text(stringResource(R.string.ui2_dsm_battery_n, index + 1),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${entry.batterySize} kWh  ·  ${entry.inverterName.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.ui2_collapse else R.string.ui2_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Size + inverter
                NumericDoubleField(
                    value = entry.batterySize,
                    onValueChange = { newSize ->
                        // If max charge/discharge are still at the 0.5C default for the previous
                        // size, keep them in sync; otherwise leave the user's overrides alone.
                        val prevRate = entry.batterySize / 24.0
                        val keepCharge = kotlin.math.abs(entry.maxCharge - prevRate) < 1e-6
                        val keepDischarge = kotlin.math.abs(entry.maxDischarge - prevRate) < 1e-6
                        onUpdate(entry.copy(
                            batterySize = newSize,
                            maxCharge = if (keepCharge) newSize / 24.0 else entry.maxCharge,
                            maxDischarge = if (keepDischarge) newSize / 24.0 else entry.maxDischarge
                        ))
                    },
                    label = stringResource(R.string.ui2_wiz_battery_size),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (noviceMode)
                        ({ Text(stringResource(R.string.ui2_wiz_battery_size_hint)) }) else null
                )

                // Inverter selector
                if (inverterEntries.isNotEmpty()) {
                    val unknownInverter = entry.inverterName.isNotBlank() &&
                        inverterEntries.none { it.inverterName == entry.inverterName }
                    var invMenu by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = entry.inverterName.ifBlank {
                                stringResource(R.string.ui2_wiz_select_ellipsis) },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.ui2_component_inverter)) },
                            isError = unknownInverter,
                            modifier = Modifier.fillMaxWidth().clickable { invMenu = true },
                            trailingIcon = {
                                Icon(Icons.Default.KeyboardArrowDown, null,
                                    Modifier.clickable { invMenu = true })
                            },
                            supportingText = if (unknownInverter) ({
                                Text(stringResource(R.string.ui2_wiz_unknown_inverter, entry.inverterName))
                            }) else null
                        )
                        DropdownMenu(expanded = invMenu, onDismissRequest = { invMenu = false }) {
                            inverterEntries.forEach { inv ->
                                DropdownMenuItem(
                                    text = { Text(inv.inverterName) },
                                    onClick = {
                                        onUpdate(entry.copy(inverterName = inv.inverterName))
                                        invMenu = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = entry.inverterName,
                        onValueChange = { onUpdate(entry.copy(inverterName = it)) },
                        label = { Text(stringResource(R.string.ui2_wiz_inverter_name)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        supportingText = { Text(stringResource(R.string.ui2_wiz_add_inverter_hint)) }
                    )
                }

                if (showAdvanced) {
                    // BMS: discharge floor + storage loss
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericDoubleField(
                            value = entry.dischargeStop,
                            onValueChange = { onUpdate(entry.copy(dischargeStop = it)) },
                            label = stringResource(R.string.ui2_wiz_min_soc),
                            modifier = Modifier.weight(1f),
                            range = 0.0..100.0
                        )
                        NumericDoubleField(
                            value = entry.storageLoss,
                            onValueChange = { onUpdate(entry.copy(storageLoss = it)) },
                            label = stringResource(R.string.ui2_wiz_storage_loss),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // BMS: max charge / discharge per 5-min interval (~0.5C default)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericDoubleField(
                            value = entry.maxCharge,
                            onValueChange = { onUpdate(entry.copy(maxCharge = it)) },
                            label = stringResource(R.string.ui2_wiz_max_charge),
                            modifier = Modifier.weight(1f)
                        )
                        NumericDoubleField(
                            value = entry.maxDischarge,
                            onValueChange = { onUpdate(entry.copy(maxDischarge = it)) },
                            label = stringResource(R.string.ui2_wiz_max_discharge),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // BMS: charge model curve (taper at top/bottom of SOC range)
                    Text(stringResource(R.string.ui2_wiz_charge_curve),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericIntField(
                            value = entry.cmPercent0,
                            onValueChange = { onUpdate(entry.copy(cmPercent0 = it)) },
                            label = stringResource(R.string.ui2_wiz_soc_0_12),
                            modifier = Modifier.weight(1f),
                            range = 0..100
                        )
                        NumericIntField(
                            value = entry.cmPercent12,
                            onValueChange = { onUpdate(entry.copy(cmPercent12 = it)) },
                            label = stringResource(R.string.ui2_wiz_soc_12_90),
                            modifier = Modifier.weight(1f),
                            range = 0..100
                        )
                        NumericIntField(
                            value = entry.cmPercent90,
                            onValueChange = { onUpdate(entry.copy(cmPercent90 = it)) },
                            label = stringResource(R.string.ui2_wiz_soc_90_100),
                            modifier = Modifier.weight(1f),
                            range = 0..100
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_remove))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Shared dropdown for picking which inverter a battery schedule targets.
   Options are the distinct inverter names from the wizard's batteries.
────────────────────────────────────────────────────────────────── */

@Composable
private fun BatteryInverterDropdown(
    selected: String,
    options: List<String>,
    noviceMode: Boolean,
    onSelect: (String) -> Unit
) {
    val unknown = selected.isNotBlank() && selected !in options
    var menu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected.ifBlank {
                stringResource(if (options.isEmpty()) R.string.ui2_wiz_no_batteries_option
                               else R.string.ui2_wiz_select_ellipsis) },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.ui2_wiz_applies_to_inverter)) },
            isError = unknown,
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = options.isNotEmpty()) { menu = true },
            trailingIcon = {
                Icon(Icons.Default.KeyboardArrowDown, null,
                    Modifier.clickable(enabled = options.isNotEmpty()) { menu = true })
            },
            supportingText = when {
                unknown -> ({ Text(stringResource(R.string.ui2_wiz_unknown_batt_inverter, selected)) })
                noviceMode && options.size > 1 ->
                    ({ Text(stringResource(R.string.ui2_wiz_applies_every)) })
                noviceMode && options.size == 1 ->
                    ({ Text(stringResource(R.string.ui2_wiz_applies_all_on, options[0])) })
                else -> null
            }
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(name); menu = false }
                )
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Battery charge schedule (LoadShift) card
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardBatteryChargeCard(
    entry: WizardBatteryChargeEntry,
    index: Int,
    expanded: Boolean,
    noviceMode: Boolean,
    batteryInverters: List<String>,
    onToggle: () -> Unit,
    onUpdate: (WizardBatteryChargeEntry) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-select the only available inverter if none is selected
    LaunchedEffect(batteryInverters, entry.inverterName) {
        if (entry.inverterName.isBlank() && batteryInverters.size == 1) {
            onUpdate(entry.copy(inverterName = batteryInverters[0]))
        }
    }
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
                Text(entry.name.ifBlank { stringResource(R.string.ui2_wiz_charge_schedule) },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${fmtHour(entry.beginHour)}–${fmtHour(entry.endHour)}  ·  " +
                        stringResource(R.string.ui2_wiz_charge_to, entry.stopAt.toInt()) +
                        "  ·  ${entry.inverterName.ifBlank { "—" }}  ·  ${fmtDays(entry.days)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.ui2_collapse else R.string.ui2_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                OutlinedTextField(value = entry.name,
                    onValueChange = { onUpdate(entry.copy(name = it)) },
                    label = { Text(stringResource(R.string.ui2_wiz_schedule_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                BatteryInverterDropdown(
                    selected = entry.inverterName,
                    options = batteryInverters,
                    noviceMode = noviceMode,
                    onSelect = { onUpdate(entry.copy(inverterName = it)) }
                )

                NumericDoubleField(
                    value = entry.stopAt,
                    onValueChange = { onUpdate(entry.copy(stopAt = it)) },
                    label = stringResource(R.string.ui2_wiz_target_soc),
                    modifier = Modifier.fillMaxWidth(),
                    range = 0.0..100.0,
                    supportingText = if (noviceMode) ({
                        Text(stringResource(R.string.ui2_wiz_target_soc_hint))
                    }) else null
                )

                WizardScheduleBlock(
                    startHour = entry.beginHour, endHour = entry.endHour,
                    days = entry.days, months = entry.months,
                    onStartHourChange = { onUpdate(entry.copy(beginHour = it)) },
                    onEndHourChange = { onUpdate(entry.copy(endHour = it)) },
                    onDaysChange = { onUpdate(entry.copy(days = it)) },
                    onMonthsChange = { onUpdate(entry.copy(months = it)) },
                    noviceMode = noviceMode
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_remove))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Battery discharge schedule (DischargeToGrid) card
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardBatteryDischargeCard(
    entry: WizardBatteryDischargeEntry,
    index: Int,
    expanded: Boolean,
    noviceMode: Boolean,
    batteryInverters: List<String>,
    onToggle: () -> Unit,
    onUpdate: (WizardBatteryDischargeEntry) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(batteryInverters, entry.inverterName) {
        if (entry.inverterName.isBlank() && batteryInverters.size == 1) {
            onUpdate(entry.copy(inverterName = batteryInverters[0]))
        }
    }
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
                Text(entry.name.ifBlank { stringResource(R.string.ui2_wiz_discharge_schedule) },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${fmtHour(entry.beginHour)}–${fmtHour(entry.endHour)}  ·  " +
                        stringResource(R.string.ui2_wiz_kw_down_to,
                            entry.rate.toString(), entry.stopAt.toInt()) +
                        "  ·  ${entry.inverterName.ifBlank { "—" }}  ·  ${fmtDays(entry.days)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.ui2_collapse else R.string.ui2_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                OutlinedTextField(value = entry.name,
                    onValueChange = { onUpdate(entry.copy(name = it)) },
                    label = { Text(stringResource(R.string.ui2_wiz_schedule_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                BatteryInverterDropdown(
                    selected = entry.inverterName,
                    options = batteryInverters,
                    noviceMode = noviceMode,
                    onSelect = { onUpdate(entry.copy(inverterName = it)) }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericDoubleField(
                        value = entry.stopAt,
                        onValueChange = { onUpdate(entry.copy(stopAt = it)) },
                        label = stringResource(R.string.ui2_wiz_min_soc),
                        modifier = Modifier.weight(1f),
                        range = 0.0..100.0,
                        supportingText = if (noviceMode) ({
                            Text(stringResource(R.string.ui2_wiz_discharge_stop_hint))
                        }) else null
                    )
                    NumericDoubleField(
                        value = entry.rate,
                        onValueChange = { onUpdate(entry.copy(rate = it)) },
                        label = stringResource(R.string.ui2_wiz_rate_kw),
                        modifier = Modifier.weight(1f),
                        supportingText = if (noviceMode) ({
                            Text(stringResource(R.string.ui2_wiz_rate_hint))
                        }) else null
                    )
                }

                WizardScheduleBlock(
                    startHour = entry.beginHour, endHour = entry.endHour,
                    days = entry.days, months = entry.months,
                    onStartHourChange = { onUpdate(entry.copy(beginHour = it)) },
                    onEndHourChange = { onUpdate(entry.copy(endHour = it)) },
                    onDaysChange = { onUpdate(entry.copy(days = it)) },
                    onMonthsChange = { onUpdate(entry.copy(months = it)) },
                    noviceMode = noviceMode
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_remove))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Hot Water — system tank card (Basic/Advanced tab gated)
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardHwSystemCard(
    entry: WizardHwSystemEntry,
    expanded: Boolean,
    noviceMode: Boolean,
    showAdvanced: Boolean,
    onToggle: () -> Unit,
    onUpdate: (WizardHwSystemEntry) -> Unit,
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
                Icon(painterResource(R.drawable.waterwarm), null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ui2_wiz_hw_tank),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.ui2_wiz_hw_subtitle,
                        entry.capacity.toString(), entry.usage.toString(), entry.rate.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.ui2_collapse else R.string.ui2_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericIntField(
                        value = entry.capacity,
                        onValueChange = { onUpdate(entry.copy(capacity = it)) },
                        label = stringResource(R.string.ui2_wiz_tank_size),
                        modifier = Modifier.weight(1f),
                        range = 20..1000,
                        supportingText = if (noviceMode)
                            ({ Text(stringResource(R.string.ui2_wiz_tank_size_hint)) }) else null
                    )
                    NumericIntField(
                        value = entry.usage,
                        onValueChange = { onUpdate(entry.copy(usage = it)) },
                        label = stringResource(R.string.ui2_wiz_daily_use),
                        modifier = Modifier.weight(1f),
                        range = 0..Int.MAX_VALUE,
                        supportingText = if (noviceMode)
                            ({ Text(stringResource(R.string.ui2_wiz_daily_use_hint)) }) else null
                    )
                }
                NumericDoubleField(
                    value = entry.rate,
                    onValueChange = { onUpdate(entry.copy(rate = it)) },
                    label = stringResource(R.string.ui2_wiz_heater_power),
                    modifier = Modifier.fillMaxWidth(),
                    range = 0.0..Double.MAX_VALUE,
                    supportingText = if (noviceMode) ({
                        Text(stringResource(R.string.ui2_wiz_heater_power_hint))
                    }) else null
                )

                // Usage pattern — when in the day water is drawn (% of daily usage per hour)
                WizardHwUsageEditor(
                    pattern = entry.usagePattern,
                    noviceMode = noviceMode,
                    onChange = { onUpdate(entry.copy(usagePattern = it)) }
                )

                if (showAdvanced) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericIntField(
                            value = entry.intake,
                            onValueChange = { onUpdate(entry.copy(intake = it)) },
                            label = stringResource(R.string.ui2_wiz_cold_intake),
                            modifier = Modifier.weight(1f),
                            range = 0..50
                        )
                        NumericIntField(
                            value = entry.target,
                            onValueChange = { onUpdate(entry.copy(target = it)) },
                            label = stringResource(R.string.ui2_wiz_target_temp),
                            modifier = Modifier.weight(1f),
                            range = 30..90
                        )
                    }
                    NumericIntField(
                        value = entry.loss,
                        onValueChange = { onUpdate(entry.copy(loss = it)) },
                        label = stringResource(R.string.ui2_wiz_heat_loss),
                        modifier = Modifier.fillMaxWidth(),
                        range = 0..50,
                        supportingText = { Text(stringResource(R.string.ui2_wiz_heat_loss_hint)) }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_wiz_remove_tank))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Hot Water — usage pattern editor (when in the day water is drawn).
   Rows of (hour, percent-of-daily-usage). Visual percent bar shows the
   profile across 24h and a "totals 100%" hint nudges users when off.
────────────────────────────────────────────────────────────────── */

@Composable
private fun WizardHwUsageEditor(
    pattern: List<WizardHwUsePoint>,
    noviceMode: Boolean,
    onChange: (List<WizardHwUsePoint>) -> Unit
) {
    val total = pattern.sumOf { it.percent }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WizardScheduleLabel(stringResource(R.string.ui2_wiz_usage_pattern))
        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_usage_pattern_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HwUsageBar(pattern = pattern)
        pattern.forEachIndexed { idx, point ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericIntField(
                    value = point.hour,
                    onValueChange = { h ->
                        onChange(pattern.toMutableList().also { it[idx] = point.copy(hour = h) })
                    },
                    label = stringResource(R.string.ui2_wiz_hour),
                    modifier = Modifier.weight(1f),
                    range = 0..23
                )
                NumericDoubleField(
                    value = point.percent,
                    onValueChange = { p ->
                        onChange(pattern.toMutableList().also { it[idx] = point.copy(percent = p) })
                    },
                    label = stringResource(R.string.ui2_wiz_pct_daily),
                    modifier = Modifier.weight(1f),
                    range = 0.0..100.0
                )
                IconButton(
                    onClick = {
                        onChange(pattern.toMutableList().also { it.removeAt(idx) })
                    },
                    enabled = pattern.size > 1
                ) {
                    Icon(Icons.Default.Delete, stringResource(R.string.ui2_remove),
                        tint = if (pattern.size > 1) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.outline)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.ui2_wiz_totals_pct, "%.0f".format(total)),
                style = MaterialTheme.typography.labelSmall,
                color = if (kotlin.math.abs(total - 100.0) < 1.0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
            TextButton(
                onClick = {
                    val nextHour = ((pattern.maxOfOrNull { it.hour } ?: 0) + 2).coerceIn(0, 23)
                    onChange(pattern + WizardHwUsePoint(nextHour, 0.0))
                }
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui2_wiz_add_draw))
            }
        }
    }
}

@Composable
private fun HwUsageBar(pattern: List<WizardHwUsePoint>) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val byHour = remember(pattern) {
        val a = DoubleArray(24)
        pattern.forEach { p -> if (p.hour in 0..23) a[p.hour] += p.percent }
        a
    }
    val max = (byHour.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            (0..23).forEach { h ->
                val frac = (byHour[h] / max).coerceIn(0.0, 1.0)
                Box(modifier = Modifier.weight(1f).height(18.dp)) {
                    Column(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.fillMaxHeight((1.0 - frac).toFloat())
                            .clip(RoundedCornerShape(2.dp)).background(Color.Transparent))
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (byHour[h] > 0.0) primary else surface))
                    }
                }
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
   Hot Water — schedule (immersion on-window)
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardHwScheduleCard(
    entry: WizardHwScheduleEntry,
    index: Int,
    expanded: Boolean,
    noviceMode: Boolean,
    onToggle: () -> Unit,
    onUpdate: (WizardHwScheduleEntry) -> Unit,
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
                Text(entry.name.ifBlank { stringResource(R.string.ui2_wiz_hw_schedule) },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${fmtHour(entry.beginHour)}–${fmtHour(entry.endHour)}  ·  ${fmtDays(entry.days)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.ui2_collapse else R.string.ui2_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                OutlinedTextField(value = entry.name,
                    onValueChange = { onUpdate(entry.copy(name = it)) },
                    label = { Text(stringResource(R.string.ui2_wiz_schedule_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)

                WizardScheduleBlock(
                    startHour = entry.beginHour, endHour = entry.endHour,
                    days = entry.days, months = entry.months,
                    onStartHourChange = { onUpdate(entry.copy(beginHour = it)) },
                    onEndHourChange = { onUpdate(entry.copy(endHour = it)) },
                    onDaysChange = { onUpdate(entry.copy(days = it)) },
                    onMonthsChange = { onUpdate(entry.copy(months = it)) },
                    noviceMode = noviceMode
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete,
                            contentDescription = stringResource(R.string.ui2_remove),
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_remove))
                    }
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────
   Panel data chart helpers
────────────────────────────────────────────────────────────────── */

private fun List<PanelPVSummary>.toMonthlyKwh(): List<Double> {
    val result = MutableList(12) { 0.0 }
    forEach { s -> val i = (s.month.toIntOrNull() ?: 1) - 1; if (i in 0..11) result[i] = s.tot }
    return result
}

/** 24dp tall inline bar chart — 12 months, no labels, shown in collapsed header */
@Composable
private fun PanelMiniMonthlyBars(monthlyKwh: List<Double>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val maxVal = monthlyKwh.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Canvas(modifier = modifier.height(24.dp).fillMaxWidth()) {
        val bw = size.width / 12f
        val gap = bw * 0.15f
        monthlyKwh.forEachIndexed { i, v ->
            val h = (v / maxVal * size.height).toFloat().coerceAtLeast(1f)
            drawRect(
                color = barColor,
                topLeft = Offset(i * bw + gap, size.height - h),
                size = CanvasSize(bw - gap * 2f, h)
            )
        }
    }
}

/** Full monthly kWh bar chart for expanded PVGIS confirmation */
@Composable
private fun PanelMonthlyBarChart(monthlyKwh: List<Double>, modifier: Modifier = Modifier) {
    val labels = stringArrayResource(R.array.ui2_months_letters).toList()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(false)
                setDrawGridBackground(false)
                axisRight.isEnabled = false
                axisLeft.apply { setDrawGridLines(false); textSize = 9f }
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float) =
                            labels.getOrElse(value.toInt()) { "" }
                    }
                }
            }
        },
        update = { chart ->
            val entries = monthlyKwh.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
            val ds = BarDataSet(entries, "kWh").apply {
                color = primaryColor; setDrawValues(false)
            }
            chart.data = BarData(ds).apply { barWidth = 0.7f }
            chart.invalidate()
        },
        modifier = modifier
    )
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
    val dayLabels   = stringArrayResource(R.array.ui2_days_short_mon_first).toList()
    val monthLabels = stringArrayResource(R.array.ui2_months_short).toList()
    val hourlyTitle  = stringResource(R.string.ui2_wiz_hourly_dist)
    val dailyTitle   = stringResource(R.string.ui2_wiz_dow_dist)
    val monthlyTitle = stringResource(R.string.ui2_wiz_monthly_dist)

    var zoomedIdx by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (zoomedIdx >= 0) {
            WizardDistBarChart(
                title = when (zoomedIdx) {
                    0 -> hourlyTitle
                    1 -> dailyTitle
                    else -> monthlyTitle
                },
                values = when (zoomedIdx) { 0 -> hourly; 1 -> daily; else -> monthly },
                labels = when (zoomedIdx) { 0 -> hourLabels; 1 -> dayLabels; else -> monthLabels },
                showEveryNthLabel = if (zoomedIdx == 0) 6 else 1,
                barHeight = 160.dp,
                onClick = { zoomedIdx = -1 }
            )
            Text(stringResource(R.string.ui2_wiz_tap_zoom_out),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        } else {
            WizardDistBarChart(title = hourlyTitle, values = hourly,
                labels = hourLabels, showEveryNthLabel = 6, barHeight = 60.dp, onClick = { zoomedIdx = 0 })
            WizardDistBarChart(title = dailyTitle, values = daily,
                labels = dayLabels, showEveryNthLabel = 1, barHeight = 60.dp, onClick = { zoomedIdx = 1 })
            WizardDistBarChart(title = monthlyTitle, values = monthly,
                labels = monthLabels, showEveryNthLabel = 1, barHeight = 60.dp, onClick = { zoomedIdx = 2 })
            Text(stringResource(R.string.ui2_wiz_tap_zoom_in),
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
    modifier: Modifier = Modifier,
    showEveryNthLabel: Int = 1,
    barHeight: Dp = 60.dp,
    onClick: (() -> Unit)? = null,
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
   PV source picker dialog
────────────────────────────────────────────────────────────────── */

@Composable
private fun PvSourceDialog(
    sources: List<SourceDateRange>,
    currentSysSn: String,
    onApply: (SourceDateRange) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember(currentSysSn) {
        mutableStateOf(sources.firstOrNull { it.sysSn == currentSysSn })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui2_wiz_select_pv_source)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sources) { src ->
                    val sel = src.sysSn == selectedSource?.sysSn
                    val typeLabel = when (src.importerType) {
                        ComparisonUIViewModel.Importer.ALPHAESS       -> stringResource(R.string.brand_alphaess)
                        ComparisonUIViewModel.Importer.HOME_ASSISTANT -> stringResource(R.string.home_assistant)
                        ComparisonUIViewModel.Importer.SOLIS          -> stringResource(R.string.ui2_wiz_solis)
                        else -> src.sysSn
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedSource = src }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$typeLabel — ${src.sysSn}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sel) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${src.startDate} → ${src.finishDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (sel) Icon(
                            Icons.Default.CheckCircle, null,
                            Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            val src = selectedSource
            Button(
                onClick = { if (src != null) { onApply(src); onDismiss() } },
                enabled = src != null
            ) { Text(stringResource(R.string.ui2_import_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
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
private fun QuickChip(label: String, onClick: () -> Unit) {
    SuggestionChip(onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

private fun toggleInt(list: List<Int>, value: Int): List<Int> =
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
