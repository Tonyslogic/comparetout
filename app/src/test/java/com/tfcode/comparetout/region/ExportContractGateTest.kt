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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The separate-export-contract region gate (plans/region/import-plans.md §3).
 *
 * `hasSeparateExportContracts` decides whether the export-plan UI exists at all;
 * `showsBundledFeed` decides whether the legacy scalar `Feed` field is displayed.
 * They are not simple inverses — the region-less source edition shows both models.
 *
 * Profiles are compared explicitly rather than through `RegionProfiles.current`,
 * which is fixed by the build flavour and would make this test flavour-dependent.
 */
class ExportContractGateTest {

    @Test
    fun irelandBundlesExportIntoTheImportPlan() {
        assertFalse(RegionProfiles.IE.hasSeparateExportContracts)
        assertTrue("the Feed field is the IE export model",
            RegionProfiles.IE.showsBundledFeed)
    }

    @Test
    fun greatBritainBuysExportSeparatelyAndHidesTheBundledFeed() {
        assertTrue(RegionProfiles.GB.hasSeparateExportContracts)
        assertFalse("an export plan supersedes the bundled rate",
            RegionProfiles.GB.showsBundledFeed)
    }

    /** The source edition surfaces every model and lets the user decide, exactly
     *  as it already does for deemed export and MIC. */
    @Test
    fun theGlobalEditionOffersBothModels() {
        assertTrue(RegionProfiles.GLOBAL.hasSeparateExportContracts)
        assertTrue("global is the documented exception to the inverse rule",
            RegionProfiles.GLOBAL.showsBundledFeed)
        assertTrue(RegionProfiles.GLOBAL.isGlobal)
    }

    /** Guards the shape of the predicate: only the global edition may have both
     *  separate export contracts and a visible bundled feed. */
    @Test
    fun onlyGlobalShowsTheFeedWhenExportIsSeparate() {
        listOf(RegionProfiles.IE, RegionProfiles.GB, RegionProfiles.GLOBAL).forEach { r ->
            if (r.hasSeparateExportContracts && r.showsBundledFeed) {
                assertTrue("${r.editionName} shows both models but is not global", r.isGlobal)
            }
        }
    }
}
