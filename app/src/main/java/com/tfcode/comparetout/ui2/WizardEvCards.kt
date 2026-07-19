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
 * Wizard EV entry + EV divert cards — extracted verbatim from WizardScheduleComposables.kt (mega-refactor B3).
 * Imports inherited; unused are cosmetic.
 */

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
