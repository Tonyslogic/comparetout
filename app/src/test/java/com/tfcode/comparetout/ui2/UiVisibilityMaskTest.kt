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

    private val allVisible = UiVisibility()

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
}
