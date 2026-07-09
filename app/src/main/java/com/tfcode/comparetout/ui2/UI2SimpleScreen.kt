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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tfcode.comparetout.R
import com.tfcode.comparetout.region.RegionProfiles
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Simple mode's single screen: a few inputs, a Calculate button, and the
 * resulting cost. Designed to answer "would solar / a battery pay off?" quickly
 * without exposing the full wizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UI2SimpleScreen(
    viewModel: UI2SimpleViewModel,
    onRequestLocation: () -> Unit,
    onLaunchGraphs: () -> Unit,
    onSwitchToFullUi: () -> Unit,
    onImportHdf: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val status by viewModel.status.collectAsState()
    val result by viewModel.result.collectAsState()
    val planCount by viewModel.planCount.collectAsState()
    val hdfState by viewModel.hdfState.collectAsState()

    val (showHints, toggleHints) = rememberShowHints()
    var showDrawer by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui2_simple_title)) },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu,
                            contentDescription = stringResource(R.string.ui2_menu))
                    }
                }
            )
        },
        bottomBar = {
            // Tab-like bottom nav for quick mode: Graphs (once a scenario exists)
            // and the switch back to the full UI.
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    enabled = status == UI2SimpleViewModel.Status.READY,
                    onClick = onLaunchGraphs,
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                    label = { Text(stringResource(R.string.ui2_simple_graphs)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSwitchToFullUi,
                    icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                    label = { Text(stringResource(R.string.ui2_simple_full_ui)) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Column(
                modifier = Modifier
                    .widthIn(max = AdaptiveLayout.CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.ui2_simple_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                UsageCard(state, viewModel, showHints, hdfState, onImportHdf)
                SolarCard(state, viewModel, showHints, onRequestLocation)
                BatteryCard(state, viewModel, showHints)

                Button(
                    onClick = viewModel::calculate,
                    enabled = status != UI2SimpleViewModel.Status.BUILDING &&
                        status != UI2SimpleViewModel.Status.SIMULATING,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.ui2_simple_calculate)) }

                ResultCard(status, result, planCount, viewModel)
              }
            }

            // Right-side slide-in drawer (trimmed for simple mode), matching the
            // pattern used across the full-UI screens.
            AnimatedVisibility(
                visible = showDrawer, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { showDrawer = false }
                )
            }
            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInHorizontally(tween(220)) { it },
                exit = slideOutHorizontally(tween(220)) { it },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(300.dp)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxSize()) {
                    UI2DrawerContent(
                        showHints = showHints,
                        onShowHintsChange = { toggleHints() },
                        onSwitchLegacy = { },
                        onClose = { showDrawer = false },
                        simpleMode = true
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageCard(
    state: UI2SimpleViewModel.UiState,
    vm: UI2SimpleViewModel,
    showHints: Boolean,
    hdfState: UI2SimpleViewModel.HdfState,
    onImportHdf: () -> Unit
) {
    val hdf = state.usageMode == UI2SimpleViewModel.UsageMode.HDF
    val importing = hdfState is UI2SimpleViewModel.HdfState.Importing
    val ready = hdfState is UI2SimpleViewModel.HdfState.Ready
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui2_simple_usage_title),
                style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !hdf,
                    onClick = { vm.setUsageMode(UI2SimpleViewModel.UsageMode.STANDARD) },
                    label = { Text(stringResource(R.string.ui2_simple_usage_typical)) }
                )
                // The ESBN HDF usage source is an IE meter-operator concept —
                // other editions only get the typical-home mode.
                if (RegionProfiles.current.hasEsbn) {
                    FilterChip(
                        selected = hdf,
                        onClick = { vm.setUsageMode(UI2SimpleViewModel.UsageMode.HDF) },
                        label = { Text(stringResource(R.string.ui2_simple_usage_hdf)) }
                    )
                }
            }
            OutlinedTextField(
                value = state.annualKwh,
                onValueChange = vm::setAnnualKwh,
                readOnly = hdf,
                label = {
                    Text(stringResource(
                        if (hdf) R.string.ui2_simple_usage_label_hdf
                        else R.string.ui2_simple_usage_label))
                },
                suffix = { Text(stringResource(R.string.ui2_simple_usage_suffix)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (hdf) {
                when {
                    importing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.ui2_simple_hdf_importing),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    ready -> Text(
                        stringResource(R.string.ui2_simple_hdf_imported),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!importing) {
                    OutlinedButton(onClick = onImportHdf, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(
                            if (ready) R.string.ui2_simple_hdf_import_other
                            else R.string.ui2_simple_hdf_import))
                    }
                }
                if (showHints) {
                    Text(
                        stringResource(R.string.ui2_simple_hdf_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (showHints) {
                Text(
                    stringResource(R.string.ui2_simple_usage_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SolarCard(
    state: UI2SimpleViewModel.UiState,
    vm: UI2SimpleViewModel,
    showHints: Boolean,
    onRequestLocation: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ui2_simple_solar_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = state.hasSolar, onCheckedChange = vm::setHasSolar)
            }
            if (state.hasSolar) {
                // Capacity stepper: ± 0.5 kWp.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.ui2_simple_solar_size),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = vm::decSolarKwp) {
                        Icon(Icons.Default.Remove,
                            contentDescription = stringResource(R.string.ui2_decrease))
                    }
                    Text(
                        String.format(Locale.US, "%.1f kWp", state.solarKwp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = vm::incSolarKwp) {
                        Icon(Icons.Default.Add,
                            contentDescription = stringResource(R.string.ui2_increase))
                    }
                }
                if (showHints) {
                    Text(
                        stringResource(R.string.ui2_simple_solar_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = state.azimuth,
                    onValueChange = vm::setAzimuth,
                    label = { Text(stringResource(R.string.ui2_simple_azimuth_label)) },
                    suffix = { Text(stringResource(R.string.ui2_simple_azimuth_suffix)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (showHints) {
                    Text(
                        stringResource(R.string.ui2_simple_azimuth_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onRequestLocation, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui2_simple_use_location))
                }
                val loc = if (state.locationLat != null && state.locationLon != null) {
                    stringResource(R.string.ui2_simple_location_set,
                        String.format(Locale.US, "%.3f, %.3f",
                            state.locationLat, state.locationLon))
                } else {
                    stringResource(R.string.ui2_simple_location_needed)
                }
                Text(
                    loc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BatteryCard(
    state: UI2SimpleViewModel.UiState,
    vm: UI2SimpleViewModel,
    showHints: Boolean
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ui2_simple_battery_title),
                style = MaterialTheme.typography.titleMedium)
            if (showHints) {
                Text(
                    stringResource(R.string.ui2_simple_battery_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 15).forEach { kwh ->
                    FilterChip(
                        selected = state.batteryKwh == kwh,
                        onClick = { vm.setBatteryKwh(kwh) },
                        label = { Text(if (kwh == 0)
                            stringResource(R.string.ui2_simple_battery_none) else "$kwh kWh") }
                    )
                }
            }
            if (state.batteryKwh > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.ui2_simple_night_charge),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = state.nightCharge, onCheckedChange = vm::setNightCharge)
                }
                if (showHints) {
                    Text(
                        stringResource(R.string.ui2_simple_night_charge_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    status: UI2SimpleViewModel.Status,
    result: UI2SimpleViewModel.Result?,
    planCount: Int,
    vm: UI2SimpleViewModel
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (status) {
                UI2SimpleViewModel.Status.IDLE ->
                    Text(
                        stringResource(R.string.ui2_simple_idle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                UI2SimpleViewModel.Status.BUILDING,
                UI2SimpleViewModel.Status.SIMULATING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(
                                if (status == UI2SimpleViewModel.Status.BUILDING)
                                    R.string.ui2_simple_building
                                else R.string.ui2_simple_simulating),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(onClick = vm::refreshResult) {
                        Text(stringResource(R.string.ui2_simple_refresh))
                    }
                }

                UI2SimpleViewModel.Status.ERROR ->
                    Text(
                        stringResource(R.string.ui2_simple_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                UI2SimpleViewModel.Status.READY -> {
                    val r = result
                    if (r == null || planCount == 0) {
                        Text(
                            stringResource(R.string.ui2_simple_no_plans_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.ui2_simple_no_plans_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(stringResource(R.string.ui2_simple_est_cost),
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            String.format(Locale.US,
                                "${RegionProfiles.current.currencySymbol}%,.0f", r.annualNetEuro),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (r.planName.isNotBlank()) {
                            Text(
                                stringResource(R.string.ui2_simple_cheapest_plan, r.planName),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        val cur = RegionProfiles.current.currencySymbol
                        Text(
                            stringResource(R.string.ui2_simple_buy_sell,
                                String.format(Locale.US, "$cur%,.0f", r.annualBuyEuro),
                                String.format(Locale.US, "$cur%,.0f", r.annualSellEuro)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()
            // Tariff status + refresh — costs require real plans.
            Text(
                if (planCount == 0) stringResource(R.string.ui2_simple_no_tariffs)
                else pluralStringResource(R.plurals.ui2_simple_tariffs_loaded,
                    planCount, planCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = vm::downloadTariffs, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(
                    if (planCount == 0) R.string.ui2_simple_download_tariffs
                    else R.string.ui2_simple_refresh_tariffs))
            }
        }
    }
}
