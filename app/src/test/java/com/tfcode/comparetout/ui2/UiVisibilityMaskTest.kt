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

import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The region + profile hard-gates stacked by UiVisibilityStore. A regression
 * here silently hides (or resurrects) whole tabs and cache surfaces, so the
 * composition is pinned down explicitly against the shipped profile objects.
 */
class UiVisibilityMaskTest {

    /** Everything on, including the experimental gate. Stated explicitly rather
     *  than taken from `UiVisibility()`, because `showExperimental` now defaults
     *  to false — the bare constructor is no longer "all visible", and letting
     *  this fixture drift with it would quietly weaken every assertion below. */
    private val allVisible = UiVisibility(showExperimental = true)

    // ── FULL profile is the identity ────────────────────────────────────

    @Test
    fun fullProfileChangesNothing() {
        assertEquals(allVisible, UiVisibilityStore.maskForProfile(allVisible, AppProfiles.FULL))
    }

    @Test
    fun fullProfileRespectsUserToggles() {
        val userHidden = allVisible.copy(comparisons = false, directors = false, wholesale = false)
        assertEquals(userHidden, UiVisibilityStore.maskForProfile(userHidden, AppProfiles.FULL))
    }

    // ── SOURCE profile hard-gates ───────────────────────────────────────

    @Test
    fun sourceProfileHidesDirectorsAndCaches() {
        val masked = UiVisibilityStore.maskForProfile(allVisible, AppProfiles.SOURCE)
        assertFalse(masked.directors)
        assertFalse(masked.pvgis)
        assertFalse(masked.cds)
        assertFalse(masked.wholesale)
    }

    @Test
    fun sourceProfilePinsComparisonsOverStaleToggle() {
        val userHidden = allVisible.copy(comparisons = false)
        val masked = UiVisibilityStore.maskForProfile(userHidden, AppProfiles.SOURCE)
        assertTrue(masked.comparisons)
    }

    @Test
    fun sourceProfileLeavesSourceTogglesAlone() {
        val userHidden = allVisible.copy(alphaess = false, solis = false)
        val masked = UiVisibilityStore.maskForProfile(userHidden, AppProfiles.SOURCE)
        assertFalse(masked.alphaess)
        assertFalse(masked.solis)
        assertTrue(masked.homeassistant)
    }

    // ── Region mask still composes underneath ───────────────────────────

    @Test
    fun regionMaskGatesForeignSources() {
        val ie = UiVisibilityStore.maskForRegion(allVisible, RegionProfiles.IE)
        assertTrue(ie.esbn)
        assertFalse(ie.octopus)

        val gb = UiVisibilityStore.maskForRegion(allVisible, RegionProfiles.GB)
        assertFalse(gb.esbn)
        assertTrue(gb.octopus)
    }

    // The global (source) edition hides no source: the region mask must pass
    // every importer through untouched.
    @Test
    fun globalRegionHidesNoSource() {
        val global = UiVisibilityStore.maskForRegion(allVisible, RegionProfiles.GLOBAL)
        assertEquals(allVisible, global)
        assertTrue(global.esbn)
        assertTrue(global.octopus)
    }

    @Test
    fun stackedMasksApplyBothGates() {
        val stacked = UiVisibilityStore.maskForProfile(
            UiVisibilityStore.maskForRegion(allVisible, RegionProfiles.IE),
            AppProfiles.SOURCE
        )
        assertFalse(stacked.octopus)   // region gate
        assertFalse(stacked.directors) // profile gate
        assertTrue(stacked.comparisons)
        assertTrue(stacked.esbn)
    }

    // The shipping source-edition combination: GLOBAL region + SOURCE profile.
    @Test
    fun globalSourceStackKeepsAllSourcesAndPinsComparisons() {
        val stacked = UiVisibilityStore.maskForProfile(
            UiVisibilityStore.maskForRegion(allVisible.copy(comparisons = false), RegionProfiles.GLOBAL),
            AppProfiles.SOURCE
        )
        assertTrue(stacked.esbn)
        assertTrue(stacked.octopus)
        assertTrue(stacked.alphaess)
        assertTrue(stacked.homeassistant)
        assertTrue(stacked.solis)
        assertTrue(stacked.comparisons)  // pinned
        assertFalse(stacked.directors)
        assertFalse(stacked.wholesale)
    }

    // ── experimental gate ───────────────────────────────────────────────

    /** Load-bearing, and inverted at the store-release review
     *  (plans/store/plan.md §3.6): the experimental gate is the one flag that
     *  defaults CLOSED, so a fresh install — and an existing install whose
     *  persisted JSON predates the key — shows no unofficial-endpoint surface
     *  until the user opts in. Every other flag still defaults to visible. */
    @Test
    fun experimentalDefaultsToHidden() {
        assertFalse(UiVisibility().showExperimental)
        val fresh = UiVisibilityStore.maskForExperimental(UiVisibility())
        assertFalse("unofficial FusionSolar endpoints", fresh.fusionsolar)
        assertFalse("unproven", fresh.solis)
        assertFalse("scraped market reports", fresh.wholesale)
        // The gate closes only the experimental surfaces; nothing else moves.
        assertTrue(fresh.alphaess)
        assertTrue(fresh.comparisons)
        assertEquals(allVisible, UiVisibilityStore.maskForExperimental(allVisible))
    }

    @Test
    fun turningExperimentalOffHidesTheUnofficialSources() {
        val masked = UiVisibilityStore.maskForExperimental(
            allVisible.copy(showExperimental = false))
        assertFalse("unofficial FusionSolar endpoints", masked.fusionsolar)
        assertFalse("unproven", masked.solis)
        assertFalse("scraped market reports", masked.wholesale)
    }

    /**
     * ESBN must survive the gate. Only its cloud sync is a scraped flow — the
     * HDF file import is a published, supported format, and the data a user has
     * already imported is theirs. Hiding the whole source would take away the
     * file import and the export/delete controls for data they own.
     */
    @Test
    fun esbnSurvivesTheGateBecauseHdfImportIsSupported() {
        val masked = UiVisibilityStore.maskForExperimental(
            allVisible.copy(showExperimental = false))
        assertTrue("HDF import is a supported format", masked.esbn)
        // The cloud half is what the flag governs, reported separately.
        assertFalse(UiVisibilityStore.esbnCloudEnabled(
            allVisible.copy(showExperimental = false)))
        assertTrue(UiVisibilityStore.esbnCloudEnabled(allVisible))
    }

    /**
     * Home Assistant is split the same way. Reading from HA is an official
     * websocket API and survives the gate; only the backfill that pushes
     * statistics back into the user's recorder database — the one thing the app
     * does that writes outside itself — is governed by the flag.
     */
    @Test
    fun haSurvivesTheGateButItsPushDoesNot() {
        val off = allVisible.copy(showExperimental = false)
        assertTrue("reading HA is an official API",
            UiVisibilityStore.maskForExperimental(off).homeassistant)
        assertFalse(UiVisibilityStore.haBackfillEnabled(off))
        assertTrue(UiVisibilityStore.haBackfillEnabled(allVisible))
    }

    /** The gate only ever subtracts: supported sources are untouched, so a user
     *  turning it off does not lose AlphaESS, Home Assistant or Octopus. */
    @Test
    fun theGateNeverTouchesSupportedSources() {
        val masked = UiVisibilityStore.maskForExperimental(
            allVisible.copy(showExperimental = false))
        assertTrue(masked.alphaess)
        assertTrue(masked.homeassistant)
        assertTrue(masked.octopus)
        assertTrue(masked.pvgis)
        assertTrue(masked.cds)
        // Nor tabs, nor scenario components.
        assertTrue(masked.comparisons)
        assertTrue(masked.directors)
        assertTrue(masked.heatPump)
    }

    /** The flag itself must never be masked, or it could not be turned back on. */
    @Test
    fun theFlagItselfSurvivesTheMask() {
        assertFalse(UiVisibilityStore.maskForExperimental(
            allVisible.copy(showExperimental = false)).showExperimental)
    }

    /** Stacked, not substituted: a user can still hide one experimental source
     *  while the master is on. */
    @Test
    fun perSourceTogglesStillApplyWhileExperimentalIsOn() {
        val masked = UiVisibilityStore.maskForExperimental(allVisible.copy(solis = false))
        assertFalse(masked.solis)
        assertTrue(masked.fusionsolar)
        assertTrue(masked.esbn)
    }

    /** Hiding is a display decision — it must not imply anything about stored
     *  data, and re-enabling restores exactly the previous per-source state. */
    @Test
    fun reEnablingRestoresThePreviousPerSourceChoices() {
        // User had FusionSolar off by choice, Solis on.
        val stored = allVisible.copy(fusionsolar = false)
        val off = stored.copy(showExperimental = false)
        assertFalse(UiVisibilityStore.maskForExperimental(off).solis)
        // Switching back on returns the stored choices, not an all-on reset.
        val backOn = UiVisibilityStore.maskForExperimental(off.copy(showExperimental = true))
        assertTrue(backOn.solis)
        assertFalse("the user's own FusionSolar choice is remembered", backOn.fusionsolar)
    }
}
