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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Composable
fun UI2SimpleScreen(
    viewModel: UI2SimpleViewModel,
    onRequestLocation: () -> Unit,
    onLaunchGraphs: () -> Unit,
    onSwitchToFullUi: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val status by viewModel.status.collectAsState()
    val result by viewModel.result.collectAsState()
    val planCount by viewModel.planCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            Text("Simple mode", style = MaterialTheme.typography.headlineSmall)
            Text(
                "See whether solar and a battery would pay off for you, fast. " +
                    "Answer a couple of questions and tap Calculate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            UsageCard(state, viewModel)
            SolarCard(state, viewModel, onRequestLocation)
            BatteryCard(state, viewModel)

            Button(
                onClick = viewModel::calculate,
                enabled = status != UI2SimpleViewModel.Status.BUILDING &&
                    status != UI2SimpleViewModel.Status.SIMULATING,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Calculate") }

            ResultCard(status, result, planCount, viewModel)

            // Graphs is allowed in simple mode once a scenario exists.
            OutlinedButton(
                onClick = onLaunchGraphs,
                enabled = status == UI2SimpleViewModel.Status.READY,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open graphs") }

            HorizontalDivider()
            TextButton(onClick = onSwitchToFullUi, modifier = Modifier.fillMaxWidth()) {
                Text("Switch to full UI")
            }
          }
        }
    }
}

@Composable
private fun UsageCard(state: UI2SimpleViewModel.UiState, vm: UI2SimpleViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How much do you use?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.annualKwh,
                onValueChange = vm::setAnnualKwh,
                label = { Text("Yearly electricity (kWh)") },
                suffix = { Text("kWh / year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "On your bill as kWh used per year. A typical Irish home is around " +
                    "4,200 kWh. We apply a standard smart-meter usage pattern.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SolarCard(
    state: UI2SimpleViewModel.UiState,
    vm: UI2SimpleViewModel,
    onRequestLocation: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Solar panels (≈7 kWp)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = state.hasSolar, onCheckedChange = vm::setHasSolar)
            }
            if (state.hasSolar) {
                OutlinedTextField(
                    value = state.azimuth,
                    onValueChange = vm::setAzimuth,
                    label = { Text("Roof direction (degrees)") },
                    suffix = { Text("° · 180 = south") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
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
private fun BatteryCard(state: UI2SimpleViewModel.UiState, vm: UI2SimpleViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Battery size", style = MaterialTheme.typography.titleMedium)
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
                            String.format(Locale.US, "€%,.0f", r.annualNetEuro),
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
                                Locale.US, "Buy €%,.0f · Sell €%,.0f per year",
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
