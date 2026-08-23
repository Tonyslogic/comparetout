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
import com.tfcode.comparetout.profile.AppProfile
import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfile
import com.tfcode.comparetout.region.RegionProfiles
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
    /**
     * Show features built on unofficial or unproven ground.
     *
     * FusionSolar drives undocumented endpoints the vendor can change without
     * notice; Solis is in the design base but not yet proven in the field; the
     * dynamic-tariff prices come from scraped public market reports; and ESBN's
     * *cloud sync* is a scraped browser flow ESB has broken before. They work
     * today, and anything already imported is the user's to keep — but a user
     * who wants only load-bearing, supported surfaces can turn the lot off here.
     *
     * Two sources are split rather than hidden whole, because only half of each
     * is experimental. ESBN's **HDF file import is a supported published
     * format** and is never hidden by this flag, nor is the data it produced —
     * see [esbnCloudEnabled]. Home Assistant's **read path is an official API**
     * and stays; only the backfill that pushes statistics back into the user's
     * HA is gated — see [haBackfillEnabled].
     *
     * Defaults to FALSE. It defaulted to true until the store-release review
     * (plans/store/plan.md §3.6): shipping these surfaces on by default puts
     * unofficial-endpoint and unproven flows in front of every new user and
     * every Play reviewer. Off by default, opt in from Settings.
     *
     * This is the one flag whose default is not `true`, so it is also the one
     * that changes on upgrade: a user who already had ESBN cloud sync,
     * FusionSolar, Solis or wholesale surfaces finds them hidden until they
     * re-enable the toggle. Hiding never deletes — imported rows stay in the
     * database and the source returns intact when re-enabled — but the
     * disappearance is real and should be called out in the release notes.
     *
     * Hiding also stops the background catch-up workers making further calls
     * (see the worker-side guard).
     */
    val showExperimental: Boolean = false,
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
    // FusionSolar sells worldwide — never region-gated (like AlphaESS/HA/Solis).
    val fusionsolar: Boolean = true,
    // Weather/PV caches (PVGIS solar, Copernicus CDS weather)
    val pvgis: Boolean = true,
    val cds: Boolean = true,
    // Wholesale market price cache (behind dynamic tariff plans)
    val wholesale: Boolean = true
)

object UiVisibilityStore {
    private val gson = Gson()

    /**
     * Region hard-gate, applied on top of the user's toggles: a data source the
     * installed edition doesn't support (Octopus outside GB, ESBN outside IE)
     * reads as hidden no matter what the persisted JSON says. Every UI2 surface
     * consumes visibility through [read], so this one mask gates them all; the
     * settings screen additionally hides the unsupported rows (see
     * UI2SettingsActivity) so the toggle can't even be seen.
     *
     * Parameterised (defaults to the installed edition) so the composition is
     * unit-testable without the BuildConfig-derived statics.
     */
    internal fun maskForRegion(
        v: UiVisibility,
        region: RegionProfile = RegionProfiles.current
    ): UiVisibility = v.copy(
        octopus = v.octopus && region.hasOctopus,
        esbn = v.esbn && region.hasEsbn
    )

    /**
     * Profile hard-gate, stacked on the region mask: surfaces the installed
     * build profile doesn't ship (Directors, PVGIS/CDS weather caches, the
     * dynamic-tariff wholesale cache) read as hidden regardless of the
     * persisted JSON. Comparisons is the one flag a profile can force ON —
     * the source edition pins the tab so a stale user toggle can't hide a
     * third of its UI.
     */
    internal fun maskForProfile(
        v: UiVisibility,
        profile: AppProfile = AppProfiles.current
    ): UiVisibility = v.copy(
        comparisons = v.comparisons || profile.pinsComparisons,
        directors = v.directors && profile.hasDirectors,
        pvgis = v.pvgis && profile.hasWeatherCaches,
        cds = v.cds && profile.hasWeatherCaches,
        wholesale = v.wholesale && profile.hasDynamicTariffs
    )

    /**
     * Experimental gate, stacked last: a surface built on unofficial or unproven
     * ground reads as hidden when [UiVisibility.showExperimental] is off,
     * whatever its own toggle says.
     *
     * Stacked rather than substituted, so a user can still hide ESBN alone while
     * keeping FusionSolar — the master flag only ever subtracts. Same shape as
     * [maskForRegion] / [maskForProfile]; the flag itself is never masked.
     *
     * Which surfaces count as experimental is deliberately listed here, in one
     * place, rather than inferred from a per-source property — adding a source
     * to the list is then a one-line, reviewable change.
     */
    internal fun maskForExperimental(v: UiVisibility): UiVisibility {
        if (v.showExperimental) return v
        return v.copy(
            fusionsolar = false,    // unofficial endpoints, API-only source
            solis = false,          // in the design base, not yet proven
            wholesale = false       // dynamic tariffs — scraped market reports
        )
    }

    /**
     * ESBN is deliberately NOT in [maskForExperimental].
     *
     * Only half of it is experimental. The cloud sync is a scraped browser flow
     * ESB has broken before; the **HDF file import is a published, supported
     * format**, and the data a user already holds is theirs. Hiding the whole
     * source would take away the file import and the export/delete controls for
     * data they own — so the flag gates the cloud half alone, via this, and the
     * ESBN card itself stays under the user's own `esbn` toggle.
     *
     * Same rule for the background worker: [experimentalEnabled] stops
     * ESBNCatchUpWorker (cloud) but never ESBNImportWorker (file).
     */
    fun esbnCloudEnabled(v: UiVisibility): Boolean = v.showExperimental

    /**
     * Home Assistant is split the same way, and for the same reason.
     *
     * Reading from HA is an official, documented websocket API and a proven
     * path. **Pushing** — the backfill wizard writing hourly long-term
     * statistics back into the user's own HA recorder database — is not proven
     * in the field, and it is the one thing this app does that modifies data
     * outside itself: it writes to real entity ids and shifts every later total
     * via `adjust_sum_statistics`. A user who wants only load-bearing surfaces
     * should be able to switch that off without losing HA as a data source.
     *
     * So the flag gates the push alone; the `homeassistant` card, its sensors
     * and its fetch stay under the user's own toggle, exactly as ESBN's HDF
     * import does.
     *
     * Same rule for the worker: [experimentalEnabled] stops HABackfillWorker
     * (push) but never HACatchupWorker (read).
     */
    fun haBackfillEnabled(v: UiVisibility): Boolean = v.showExperimental

    private fun mask(v: UiVisibility): UiVisibility =
        maskForExperimental(maskForProfile(maskForRegion(v)))

    /**
     * Synchronous read (call off the main thread where possible); missing/bad
     * JSON → constructor defaults, i.e. everything visible except the
     * experimental surfaces.
     *
     * Field-by-field with a missing-key default of true: Gson bypasses Kotlin
     * constructor defaults, so a flag added after the JSON was persisted would
     * otherwise deserialize as false and silently hide the new UI.
     *
     * `showExperimental` is the deliberate exception and passes
     * `ifMissing = false`, matching its constructor default. An install whose
     * stored JSON predates the key gets the gate closed rather than open — see
     * [UiVisibility.showExperimental].
     */
    fun read(context: Context): UiVisibility {
        val app = context.applicationContext as? TOUTCApplication
            ?: return mask(UiVisibility())
        val raw = runCatching { app.getStringValueFromDataStore(UI_VISIBILITY_KEY) }.getOrNull()
        if (raw.isNullOrBlank()) return mask(UiVisibility())
        return mask(runCatching {
            val obj = JsonParser.parseString(raw).asJsonObject
            fun flag(name: String, ifMissing: Boolean = true): Boolean =
                obj.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: ifMissing
            UiVisibility(
                showExperimental = flag("showExperimental", ifMissing = false),
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
                fusionsolar = flag("fusionsolar"),
                pvgis = flag("pvgis"),
                cds = flag("cds"),
                wholesale = flag("wholesale")
            )
        }.getOrNull() ?: UiVisibility())
    }

    /**
     * Whether experimental surfaces are enabled — the guard every background
     * catch-up worker on an unofficial API checks before doing any network I/O.
     *
     * Workers guard rather than being cancelled: cancelling the periodic work
     * would leave nothing to re-enqueue it when the user turns the flag back on,
     * so syncing would stay silently dead. Guarding means a disabled source's
     * scheduled run wakes, does nothing, and resumes by itself on re-enable.
     *
     * Synchronous DataStore read — call from a worker thread, never the main one.
     */
    @JvmStatic
    fun experimentalEnabled(context: Context): Boolean = read(context).showExperimental

    fun write(context: Context, visibility: UiVisibility) {
        val app = context.applicationContext as? TOUTCApplication ?: return
        runCatching { app.putStringValueIntoDataStore(UI_VISIBILITY_KEY, gson.toJson(visibility)) }
    }
}

/**
 * The visibility flags as Compose state, re-read on every ON_RESUME so a trip
 * to the settings screen takes effect the moment the host screen returns.
 *
 * Until the first read lands the placeholder is the constructor default:
 * everything visible except the experimental surfaces. That is the right way
 * round — a user with the gate open sees those surfaces appear a frame late,
 * rather than every user seeing them flash before the read hides them.
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
