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
 * Wizard panel (PV string) card + panel chart helpers + PV source dialog — extracted verbatim from WizardScheduleComposables.kt (mega-refactor B3).
 * Imports inherited; unused are cosmetic.
 */

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
   Panel data chart helpers
────────────────────────────────────────────────────────────────── */

internal fun List<PanelPVSummary>.toMonthlyKwh(): List<Double> {
    val result = MutableList(12) { 0.0 }
    forEach { s -> val i = (s.month.toIntOrNull() ?: 1) - 1; if (i in 0..11) result[i] = s.tot }
    return result
}

/** 24dp tall inline bar chart — 12 months, no labels, shown in collapsed header */
@Composable
internal fun PanelMiniMonthlyBars(monthlyKwh: List<Double>, modifier: Modifier = Modifier) {
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
internal fun PanelMonthlyBarChart(monthlyKwh: List<Double>, modifier: Modifier = Modifier) {
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
   PV source picker dialog
────────────────────────────────────────────────────────────────── */

@Composable
internal fun PvSourceDialog(
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
                        ComparisonUIViewModel.Importer.FUSION_SOLAR   -> stringResource(R.string.ui2_wiz_fusionsolar)
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
