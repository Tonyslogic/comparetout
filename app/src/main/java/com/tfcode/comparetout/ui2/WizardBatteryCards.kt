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
 * Wizard battery + charge/discharge schedule cards — extracted verbatim from WizardScheduleComposables.kt (mega-refactor B3).
 * Imports inherited; unused are cosmetic.
 */

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
internal fun BatteryInverterDropdown(
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
