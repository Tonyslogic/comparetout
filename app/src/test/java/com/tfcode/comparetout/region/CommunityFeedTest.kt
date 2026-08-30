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

package com.tfcode.comparetout.region

import com.tfcode.comparetout.ui2.planFilterCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The community price-plan feeds (`PricePlanDownloader`, `MainActivity` download
 * menu, and the region picker in the source edition).
 *
 * GB gained a feed on 2026-08-29 (`comparetout-doc/price-plans/rates_gb.json`),
 * so the "GB has nothing to download" branch is gone. Two things are worth
 * pinning: that each region-bound edition points at *its own* file, and that
 * [RegionProfiles.communityFeedChoices] picks the new feed up for the global
 * edition automatically — it is a `mapNotNull` over the known profiles, so a
 * region gaining a URL is meant to need no other change.
 *
 * Profiles are compared explicitly rather than through `RegionProfiles.current`,
 * which is fixed by the build flavour and would make this test flavour-dependent.
 */
class CommunityFeedTest {

    @Test
    fun eachRegionPointsAtItsOwnFeedFile() {
        assertTrue("IE feed is rates.json",
            RegionProfiles.IE.pricePlanFeedUrl!!.endsWith("/price-plans/rates.json"))
        assertTrue("GB feed is rates_gb.json",
            RegionProfiles.GB.pricePlanFeedUrl!!.endsWith("/price-plans/rates_gb.json"))
    }

    /** The note is what carries the may-be-out-of-date caveat to the user, and
     *  the download UI shows it beside the entry point — a feed without one
     *  would ship the tariffs with no caveat attached. */
    @Test
    fun everyFeedCarriesItsCaveat() {
        listOf(RegionProfiles.IE, RegionProfiles.GB).forEach { r ->
            assertNotNull("${r.editionName} has a feed", r.pricePlanFeedUrl)
            assertTrue("${r.editionName} feed note mentions staleness",
                r.pricePlanFeedNote!!.contains("out of date"))
        }
    }

    /** The source edition bakes no feed of its own; it asks the user to pick. */
    @Test
    fun theGlobalEditionBakesNoFeed() {
        assertNull(RegionProfiles.GLOBAL.pricePlanFeedUrl)
        assertNull(RegionProfiles.GLOBAL.pricePlanFeedNote)
    }

    @Test
    fun aRegionBoundEditionIsOfferedOnlyItsOwnFeed() {
        val gb = RegionProfiles.communityFeedChoices(RegionProfiles.GB)
        assertEquals(1, gb.size)
        assertEquals("Great Britain", gb[0].regionName)
        assertEquals(RegionProfiles.GB.pricePlanFeedUrl, gb[0].url)
    }

    /** The edition decides which plans are "local", not the handset. A GB build on
     *  an Irish SIM must still show GB tariffs; before this, the Supplier Plans
     *  screen hid all 25 of them and the view model deactivated them. */
    @Test
    fun aRegionBoundEditionJudgesPlansByItsOwnRegion() {
        listOf("IE", "GB", "", "US").forEach { sim ->
            assertEquals("GB build, SIM=$sim", "GB",
                planFilterCountry(RegionProfiles.GB, sim))
            assertEquals("IE build, SIM=$sim", "IE",
                planFilterCountry(RegionProfiles.IE, sim))
        }
    }

    /** The source edition has no region of its own, so the phone is the best
     *  signal available and stays in charge. */
    @Test
    fun theGlobalEditionStillFollowsTheDevice() {
        assertEquals("GB", planFilterCountry(RegionProfiles.GLOBAL, "GB"))
        assertEquals("IE", planFilterCountry(RegionProfiles.GLOBAL, "IE"))
        assertEquals("", planFilterCountry(RegionProfiles.GLOBAL, ""))
    }

    /** Both regions now have feeds, so the global picker offers two — this is the
     *  assertion that would have failed before GB got a URL. */
    @Test
    fun theGlobalPickerOffersEveryKnownFeed() {
        val choices = RegionProfiles.communityFeedChoices(RegionProfiles.GLOBAL)
        assertEquals(2, choices.size)
        assertEquals(listOf("Ireland", "Great Britain"), choices.map { it.regionName })
        assertTrue("every choice carries a note", choices.all { it.note.isNotBlank() })
    }
}
