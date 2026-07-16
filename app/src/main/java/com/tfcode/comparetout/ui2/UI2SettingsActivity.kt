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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tfcode.comparetout.R
import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfiles
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
                title = { Text(stringResource(R.string.ui2_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui2_back))
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
                stringResource(R.string.ui2_settings_intro),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Profile hard-gated rows disappear entirely — the mask in
            // UiVisibilityStore would override the toggle anyway, so showing
            // it would only mislead (same treatment as region-gated sources).
            val profile = AppProfiles.current
            if (!profile.pinsComparisons || profile.hasDirectors) {
                GroupHeader(stringResource(R.string.ui2_settings_group_tabs))
                if (!profile.pinsComparisons) {
                    ToggleRow(stringResource(R.string.comparisons),
                        stringResource(R.string.ui2_settings_comparisons_sub), vis.comparisons) {
                        update(vis.copy(comparisons = it))
                    }
                }
                if (profile.hasDirectors) {
                    ToggleRow(stringResource(R.string.ui2_settings_directors),
                        stringResource(R.string.ui2_settings_directors_sub), vis.directors) {
                        update(vis.copy(directors = it))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            if (profile.hasScenarios) {
                GroupHeader(stringResource(R.string.ui2_settings_group_components))
                Text(stringResource(R.string.ui2_settings_components_sub),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow(stringResource(R.string.ui2_component_inverter), null, vis.inverter) { update(vis.copy(inverter = it)) }
                ToggleRow(stringResource(R.string.ui2_component_panels), null, vis.panels) { update(vis.copy(panels = it)) }
                ToggleRow(stringResource(R.string.ui2_component_battery), null, vis.battery) { update(vis.copy(battery = it)) }
                ToggleRow(stringResource(R.string.ui2_component_hot_water), null, vis.hotWater) { update(vis.copy(hotWater = it)) }
                ToggleRow(stringResource(R.string.ui2_component_ev), null, vis.ev) { update(vis.copy(ev = it)) }
                ToggleRow(stringResource(R.string.ui2_component_heat_pump), null, vis.heatPump) { update(vis.copy(heatPump = it)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            GroupHeader(stringResource(R.string.ui2_settings_group_sources))
            ToggleRow(stringResource(R.string.brand_alphaess), null, vis.alphaess) { update(vis.copy(alphaess = it)) }
            ToggleRow(stringResource(R.string.home_assistant), null, vis.homeassistant) { update(vis.copy(homeassistant = it)) }
            // Region-specific sources only offer a toggle in the editions where
            // they exist (UiVisibilityStore hard-gates them everywhere else).
            if (RegionProfiles.current.hasEsbn) {
                ToggleRow(stringResource(R.string.brand_esbn), null, vis.esbn) { update(vis.copy(esbn = it)) }
            }
            if (RegionProfiles.current.hasOctopus) {
                ToggleRow(stringResource(R.string.octopus_energy), null, vis.octopus) { update(vis.copy(octopus = it)) }
            }
            ToggleRow(stringResource(R.string.brand_solis), null, vis.solis) { update(vis.copy(solis = it)) }
            if (profile.hasWeatherCaches) {
                ToggleRow(stringResource(R.string.brand_pvgis),
                    stringResource(R.string.ui2_settings_pvgis_sub), vis.pvgis) { update(vis.copy(pvgis = it)) }
                ToggleRow(stringResource(R.string.brand_cds),
                    stringResource(R.string.ui2_settings_cds_sub), vis.cds) { update(vis.copy(cds = it)) }
            }
            // Dynamic-tariff wholesale price cache — offered wherever the edition
            // supports dynamic tariffs (IE wholesale market / GB Octopus Agile).
            if (profile.hasDynamicTariffs &&
                (RegionProfiles.current.dynamicMarkets.isNotEmpty() ||
                    RegionProfiles.current.hasOctopus)) {
                ToggleRow(stringResource(R.string.ui2_dsm_prices_title),
                    stringResource(R.string.ui2_settings_wholesale_sub), vis.wholesale) {
                    update(vis.copy(wholesale = it))
                }
            }
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
