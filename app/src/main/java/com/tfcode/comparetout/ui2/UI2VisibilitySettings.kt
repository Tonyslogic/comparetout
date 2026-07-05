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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tfcode.comparetout.TOUTCApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────────────────────────────────
// Global UI visibility settings (gating, not deletion).
//
// The user can hide whole tabs (Comparisons / Directors), scenario component
// accordions (in BOTH the wizard and the dashboard — hide HP and neither
// screen shows an HP section) and individual data sources (their management
// cards and list rows). Hiding never touches data: a hidden component that a
// scenario already has still simulates; revealing the section brings the UI
// back.
//
// Stored as one JSON blob in the shared DataStore so the flags survive UI
// switches and exports don't carry them (device preference, not data).
// ──────────────────────────────────────────────────────────────────────────

const val UI_VISIBILITY_KEY = "ui_visibility"

data class UiVisibility(
    // Tabs
    val comparisons: Boolean = true,
    val directors: Boolean = true,
    // Scenario components (wizard + dashboard accordions)
    val inverter: Boolean = true,
    val panels: Boolean = true,
    val battery: Boolean = true,
    val hotWater: Boolean = true,
    val ev: Boolean = true,
    val heatPump: Boolean = true,
    // Data sources
    val alphaess: Boolean = true,
    val homeassistant: Boolean = true,
    val esbn: Boolean = true,
    val octopus: Boolean = true,
    val solis: Boolean = true,
    // Weather/PV caches (PVGIS solar, Copernicus CDS weather)
    val pvgis: Boolean = true,
    val cds: Boolean = true
)

object UiVisibilityStore {
    private val gson = Gson()

    /**
     * Synchronous read (call off the main thread where possible); missing/bad JSON → all visible.
     *
     * Field-by-field with a missing-key default of true: Gson bypasses Kotlin
     * constructor defaults, so a flag added after the JSON was persisted would
     * otherwise deserialize as false and silently hide the new UI.
     */
    fun read(context: Context): UiVisibility {
        val app = context.applicationContext as? TOUTCApplication ?: return UiVisibility()
        val raw = runCatching { app.getStringValueFromDataStore(UI_VISIBILITY_KEY) }.getOrNull()
        if (raw.isNullOrBlank()) return UiVisibility()
        return runCatching {
            val obj = JsonParser.parseString(raw).asJsonObject
            fun flag(name: String): Boolean = obj.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true
            UiVisibility(
                comparisons = flag("comparisons"),
                directors = flag("directors"),
                inverter = flag("inverter"),
                panels = flag("panels"),
                battery = flag("battery"),
                hotWater = flag("hotWater"),
                ev = flag("ev"),
                heatPump = flag("heatPump"),
                alphaess = flag("alphaess"),
                homeassistant = flag("homeassistant"),
                esbn = flag("esbn"),
                octopus = flag("octopus"),
                solis = flag("solis"),
                pvgis = flag("pvgis"),
                cds = flag("cds")
            )
        }.getOrNull() ?: UiVisibility()
    }

    fun write(context: Context, visibility: UiVisibility) {
        val app = context.applicationContext as? TOUTCApplication ?: return
        runCatching { app.putStringValueIntoDataStore(UI_VISIBILITY_KEY, gson.toJson(visibility)) }
    }
}

/**
 * The visibility flags as Compose state, re-read on every ON_RESUME so a trip
 * to the settings screen takes effect the moment the host screen returns.
 * Defaults to all-visible until the first read lands.
 */
@Composable
fun rememberUiVisibility(): UiVisibility {
    val context = LocalContext.current
    var visibility by remember { mutableStateOf(UiVisibility()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                CoroutineScope(Dispatchers.IO).launch {
                    val v = UiVisibilityStore.read(context)
                    visibility = v
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return visibility
}
