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
 * Wizard hot-water system/usage/schedule cards — extracted verbatim from WizardScheduleComposables.kt (mega-refactor B3).
 * Imports inherited; unused are cosmetic.
 */

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
internal fun WizardHwUsageEditor(
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
internal fun HwUsageBar(pattern: List<WizardHwUsePoint>) {
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
