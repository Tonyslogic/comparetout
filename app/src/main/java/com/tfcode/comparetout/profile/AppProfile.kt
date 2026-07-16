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

package com.tfcode.comparetout.profile

import com.tfcode.comparetout.BuildConfig

// ──────────────────────────────────────────────────────────────────────────
// Build profiles (plans/source/plan.md §2b) — the sibling of RegionProfile.
//
// The flavor also fixes the *profile*: BuildConfig.PROFILE ("FULL"/"SOURCE")
// selects a capability set at class-load time. FULL is today's app; SOURCE is
// the data-source-only edition ("Eco Power Monitor") — importers, the source
// selection list, Compare and price-plan costing of measured data, but no
// scenarios/simulation, no Directors, no PVGIS/CDS, no dynamic tariffs.
//
// Shared screens stay monolithic and *ask* the profile, exactly like the
// region gate. Bulk visibility flows through UiVisibilityStore's profile
// mask; these flags cover what the store doesn't reach.
// ──────────────────────────────────────────────────────────────────────────

data class AppProfile(
    /** "FULL" / "SOURCE" — compared against [BuildConfig.PROFILE]. */
    @JvmField val profileCode: String,
    /** Scenario list/cards, wizard, sim chain, scenario costing, sample scenario data. */
    @JvmField val hasScenarios: Boolean,
    /** Directors tab. */
    @JvmField val hasDirectors: Boolean,
    /** Persistent single-screen ("simple") mode. */
    @JvmField val hasSimpleMode: Boolean,
    /** Legacy MainActivity reachable (routing + switch entries). */
    @JvmField val hasLegacyUi: Boolean,
    /** PVGIS + CDS fetch/cache surfaces. */
    @JvmField val hasWeatherCaches: Boolean,
    /** Dynamic tariff plans: wholesale cache, DynamicTariffWorker, generate-from-market. */
    @JvmField val hasDynamicTariffs: Boolean,
    /** Comparisons tab forced always-on (its visibility toggle hidden). */
    @JvmField val pinsComparisons: Boolean
)

object AppProfiles {

    @JvmField
    val FULL = AppProfile(
        profileCode = "FULL",
        hasScenarios = true,
        hasDirectors = true,
        hasSimpleMode = true,
        hasLegacyUi = true,
        hasWeatherCaches = true,
        hasDynamicTariffs = true,
        pinsComparisons = false
    )

    @JvmField
    val SOURCE = AppProfile(
        profileCode = "SOURCE",
        hasScenarios = false,
        hasDirectors = false,
        hasSimpleMode = false,
        hasLegacyUi = false,
        hasWeatherCaches = false,
        hasDynamicTariffs = false,
        pinsComparisons = true
    )

    /** The profile for the installed edition — fixed at build time by the flavor. */
    @JvmField
    val current: AppProfile = if (BuildConfig.PROFILE == "SOURCE") SOURCE else FULL
}
