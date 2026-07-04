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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope

// ──────────────────────────────────────────────────────────────────────────
// App settings — visibility gating.
//
// Three groups of switches backed by [UiVisibilityStore]:
//  - Tabs: Comparisons, Directors (bottom navigation)
//  - Scenario components: hide the matching accordion in the wizard AND the
//    dashboard (Inverter / PV panels / Battery / Hot water / EV / Heat pump)
//  - Data sources: hide the source's card in Data Source Management and its
//    rows in the Scenarios list
// Hiding is cosmetic — data and simulations are untouched.
// ──────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                SettingsScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var vis by remember { mutableStateOf(UiVisibility()) }
    LaunchedEffect(Unit) {
        vis = withContext(Dispatchers.IO) { UiVisibilityStore.read(context) }
        loaded = true
    }
    fun update(newVis: UiVisibility) {
        vis = newVis
        CoroutineScope(Dispatchers.IO).launch { UiVisibilityStore.write(context, newVis) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!loaded) return@Column
            Text(
                "Hide the parts of the app you don't use. Hiding never deletes " +
                        "anything — a hidden component a scenario already has still " +
                        "simulates, and hidden sources keep their data.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            GroupHeader("Tabs")
            ToggleRow("Comparisons", "Cost/usage comparison tab", vis.comparisons) {
                update(vis.copy(comparisons = it))
            }
            ToggleRow("Directors", "Bulk component editing tab", vis.directors) {
                update(vis.copy(directors = it))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            GroupHeader("Scenario components")
            Text("Applies to the wizard and the dashboard accordions.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToggleRow("Inverter", null, vis.inverter) { update(vis.copy(inverter = it)) }
            ToggleRow("PV panels", null, vis.panels) { update(vis.copy(panels = it)) }
            ToggleRow("Battery", null, vis.battery) { update(vis.copy(battery = it)) }
            ToggleRow("Hot water", null, vis.hotWater) { update(vis.copy(hotWater = it)) }
            ToggleRow("EV", null, vis.ev) { update(vis.copy(ev = it)) }
            ToggleRow("Heat pump", null, vis.heatPump) { update(vis.copy(heatPump = it)) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            GroupHeader("Data sources")
            ToggleRow("AlphaESS", null, vis.alphaess) { update(vis.copy(alphaess = it)) }
            ToggleRow("Home Assistant", null, vis.homeassistant) { update(vis.copy(homeassistant = it)) }
            ToggleRow("ESBN Smart Meter", null, vis.esbn) { update(vis.copy(esbn = it)) }
            ToggleRow("Octopus Energy", null, vis.octopus) { update(vis.copy(octopus = it)) }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
