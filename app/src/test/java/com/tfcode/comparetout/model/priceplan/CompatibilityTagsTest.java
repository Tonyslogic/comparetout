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

package com.tfcode.comparetout.model.priceplan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Export → import pairing rules (plans/region/import-plans.md §2.2).
 *
 * Matching is live against current supplier/plan names, so renaming an import
 * plan can never silently break a pairing — there is nothing snapshotted to
 * drift.
 */
public class CompatibilityTagsTest {

    private static PricePlan imp(String supplier, String plan) {
        PricePlan pp = new PricePlan();
        pp.setSupplier(supplier);
        pp.setPlanName(plan);
        return pp;
    }

    private static CompatibilityTags tags(String... t) {
        return new CompatibilityTags(Arrays.asList(t));
    }

    @Test
    public void noTagsMeansOpenMarket() {
        CompatibilityTags t = new CompatibilityTags();
        assertTrue(t.isOpenMarket());
        assertTrue(t.matches(imp("Anyone", "Anything")));
    }

    @Test
    public void blankTagsAreStillOpenMarket() {
        assertTrue(tags("", "   ").isOpenMarket());
        assertTrue(tags("", "   ").matches(imp("Anyone", "Anything")));
    }

    @Test
    public void supplierWildcardMatchesEveryPlanFromThatSupplier() {
        CompatibilityTags t = tags("Octopus Energy:*");
        assertTrue(t.matches(imp("Octopus Energy", "Flexible")));
        assertTrue(t.matches(imp("Octopus Energy", "Cosy")));
        assertFalse(t.matches(imp("EDF", "Standard")));
    }

    @Test
    public void aFullyQualifiedTagMatchesOnePlanOnly() {
        CompatibilityTags t = tags("Octopus Energy:Flexible");
        assertTrue(t.matches(imp("Octopus Energy", "Flexible")));
        assertFalse(t.matches(imp("Octopus Energy", "Cosy")));
    }

    @Test
    public void aBareSupplierNameBehavesAsASupplierWildcard() {
        CompatibilityTags t = tags("Octopus Energy");
        assertTrue(t.matches(imp("Octopus Energy", "Flexible")));
        assertFalse(t.matches(imp("EDF", "Standard")));
    }

    @Test
    public void theGlobalWildcardMatchesEverything() {
        assertTrue(tags("*").matches(imp("EDF", "Standard")));
    }

    @Test
    public void matchingIsCaseAndWhitespaceInsensitive() {
        CompatibilityTags t = tags("  octopus energy : flexible  ");
        assertTrue(t.matches(imp("Octopus Energy", "Flexible")));
    }

    @Test
    public void anyTagMatchingIsEnough() {
        CompatibilityTags t = tags("EDF:*", "Octopus Energy:Cosy");
        assertTrue(t.matches(imp("Octopus Energy", "Cosy")));
        assertTrue(t.matches(imp("EDF", "Standard")));
        assertFalse(t.matches(imp("Octopus Energy", "Flexible")));
    }

    /** Plan names containing a colon must still resolve — the split is on the
     *  FIRST colon only. */
    @Test
    public void aPlanNameMayContainAColon() {
        CompatibilityTags t = tags("Octopus Energy:Agile: May 2026");
        assertTrue(t.matches(imp("Octopus Energy", "Agile: May 2026")));
    }

    @Test
    public void nothingMatchesANullPlan() {
        assertFalse(tags("*").matches(null));
        assertFalse(new CompatibilityTags().matches(null));
    }

    @Test
    public void pairingIsExportToImportOnly() {
        PricePlan importPlan = imp("Octopus Energy", "Flexible");
        // An import plan is never the "compatible with" side.
        assertFalse(importPlan.isCompatibleWith(imp("EDF", "Standard")));

        PricePlan exportPlan = new PricePlan();
        exportPlan.setDirection(PricePlan.DIRECTION_EXPORT);
        assertTrue("untagged export plans are open market",
                exportPlan.isCompatibleWith(importPlan));

        exportPlan.setCompatibleWith(tags("EDF:*"));
        assertFalse(exportPlan.isCompatibleWith(importPlan));
    }

    /** The tags survive the Gson TypeConverter round trip used to store them. */
    @Test
    public void tagsRoundTripThroughJson() {
        CompatibilityTags original = tags("Octopus Energy:*", "EDF:Standard");
        CompatibilityTags back = new Gson().fromJson(
                new Gson().toJson(original), CompatibilityTags.class);
        assertEquals(2, back.getTags().size());
        assertTrue(back.matches(imp("Octopus Energy", "Cosy")));
        assertTrue(back.matches(imp("EDF", "Standard")));
        assertFalse(back.matches(imp("EDF", "Fixed")));
    }

    /** Gson can write null straight into the field, bypassing the initialiser. */
    @Test
    public void aNullTagListDegradesToOpenMarket() {
        CompatibilityTags t = new Gson().fromJson("{\"tags\":null}", CompatibilityTags.class);
        assertTrue(t.isOpenMarket());
        assertTrue(t.matches(imp("Anyone", "Anything")));
    }

    @Test
    public void supplierWildcardHelperBuildsTheDiscoveryDefault() {
        assertEquals("Octopus Energy:*",
                CompatibilityTags.supplierWildcard("Octopus Energy"));
        assertTrue(tags(CompatibilityTags.supplierWildcard("Octopus Energy"))
                .matches(imp("Octopus Energy", "Anything")));
    }

    @Test
    public void anEmptyListIsOpenMarket() {
        assertTrue(new CompatibilityTags(new ArrayList<>()).isOpenMarket());
        assertTrue(new CompatibilityTags(Collections.emptyList())
                .matches(imp("Anyone", "Anything")));
    }
}
