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
                title = { Text("Quick mode") },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
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
                    label = { Text("Graphs") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSwitchToFullUi,
                    icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                    label = { Text("Full UI") }
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
                    "See whether solar and a battery would pay off for you, fast. " +
                        "Answer a couple of questions and tap Calculate.",
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
                ) { Text("Calculate") }

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
            Text("How much do you use?", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !hdf,
                    onClick = { vm.setUsageMode(UI2SimpleViewModel.UsageMode.STANDARD) },
                    label = { Text("Typical home") }
                )
                FilterChip(
                    selected = hdf,
                    onClick = { vm.setUsageMode(UI2SimpleViewModel.UsageMode.HDF) },
                    label = { Text("My ESBN data") }
                )
            }
            OutlinedTextField(
                value = state.annualKwh,
                onValueChange = vm::setAnnualKwh,
                readOnly = hdf,
                label = {
                    Text(if (hdf) "Yearly electricity (from your data)" else "Yearly electricity (kWh)")
                },
                suffix = { Text("kWh / year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (hdf) {
                when {
                    importing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Importing your ESBN data…", style = MaterialTheme.typography.bodyMedium)
                    }
                    ready -> Text(
                        "✓ Imported — yearly total read from your data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!importing) {
                    OutlinedButton(onClick = onImportHdf, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ready) "Import a different file" else "Import ESBN HDF file")
                    }
                }
                if (showHints) {
                    Text(
                        "Download an HDF file from the ESBN Networks portal (My Energy " +
                            "Consumption → Download). We read your exact yearly total and " +
                            "half-hourly usage pattern from it — the most accurate option.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (showHints) {
                Text(
                    "It's on your bill as kWh used per year. A typical Irish home is around " +
                        "4,200 kWh. We spread it over the year using a standard smart-meter " +
                        "pattern. For a more accurate result, switch to “My ESBN data”.",
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
                    "Solar panels",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = state.hasSolar, onCheckedChange = vm::setHasSolar)
            }
            if (state.hasSolar) {
                // Capacity stepper: ± 0.5 kWp.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "System size",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = vm::decSolarKwp) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        String.format(Locale.US, "%.1f kWp", state.solarKwp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = vm::incSolarKwp) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
                if (showHints) {
                    Text(
                        "Adjust in 0.5 kWp steps — a typical home install is around 7 kWp. " +
                            "We model it at 40° tilt and fetch a year of expected output for " +
                            "your exact spot from PVGIS (an EU solar dataset). Turn solar off " +
                            "to see the battery-only case.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = state.azimuth,
                    onValueChange = vm::setAzimuth,
                    label = { Text("Roof direction (degrees)") },
                    suffix = { Text("° · 180 = south") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (showHints) {
                    Text(
                        "0 = north, 90 = east, 180 = south, 270 = west. South is best in Ireland.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onRequestLocation, modifier = Modifier.fillMaxWidth()) {
                    Text("Use my location")
                }
                val loc = if (state.locationLat != null && state.locationLon != null) {
                    String.format(
                        Locale.US, "Location set: %.3f, %.3f",
                        state.locationLat, state.locationLon
                    )
                } else {
                    "We need your location to estimate solar yield (PVGIS)."
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
            Text("Battery size", style = MaterialTheme.typography.titleMedium)
            if (showHints) {
                Text(
                    "A home battery stores daytime solar (or cheap night-rate electricity) " +
                        "to use at peak times. Try a few sizes to see which pays off — bigger " +
                        "isn't always better.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 15).forEach { kwh ->
                    FilterChip(
                        selected = state.batteryKwh == kwh,
                        onClick = { vm.setBatteryKwh(kwh) },
                        label = { Text(if (kwh == 0) "None" else "$kwh kWh") }
                    )
                }
            }
            if (state.batteryKwh > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Charge from the grid overnight (02:00–05:00)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = state.nightCharge, onCheckedChange = vm::setNightCharge)
                }
                if (showHints) {
                    Text(
                        "Fills the battery on a cheap night-rate window so you draw less at " +
                            "peak times. Best paired with a night-saver tariff.",
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
                        "Tap Calculate to run the simulation and see your cost.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                UI2SimpleViewModel.Status.BUILDING,
                UI2SimpleViewModel.Status.SIMULATING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (status == UI2SimpleViewModel.Status.BUILDING)
                                "Setting things up…" else "Simulating a year of energy…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    TextButton(onClick = vm::refreshResult) { Text("Refresh") }
                }

                UI2SimpleViewModel.Status.ERROR ->
                    Text(
                        "Something went wrong. Check your inputs and try again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                UI2SimpleViewModel.Status.READY -> {
                    val r = result
                    if (r == null || planCount == 0) {
                        Text(
                            "Add real supplier tariffs to see your cost.",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Costs can only be shown against real tariffs. Download the " +
                                "community-maintained list (it may be out of date — you can " +
                                "edit the plans afterwards).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Estimated yearly cost", style = MaterialTheme.typography.titleMedium)
                        Text(
                            String.format(Locale.US,
                                "${RegionProfiles.current.currencySymbol}%,.0f", r.annualNetEuro),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (r.planName.isNotBlank()) {
                            Text(
                                "Cheapest plan: ${r.planName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            String.format(
                                Locale.US,
                                "Buy ${RegionProfiles.current.currencySymbol}%,.0f · " +
                                    "Sell ${RegionProfiles.current.currencySymbol}%,.0f per year",
                                r.annualBuyEuro, r.annualSellEuro
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()
            // Tariff status + refresh — costs require real plans.
            Text(
                if (planCount == 0) "No supplier tariffs yet."
                else "$planCount supplier tariff${if (planCount == 1) "" else "s"} loaded " +
                    "· community-maintained, may be out of date.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = vm::downloadTariffs, modifier = Modifier.fillMaxWidth()) {
                Text(if (planCount == 0) "Download supplier tariffs" else "Refresh tariffs")
            }
        }
    }
}
