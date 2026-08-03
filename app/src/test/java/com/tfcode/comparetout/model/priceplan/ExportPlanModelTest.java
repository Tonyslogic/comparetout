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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The v17 export-plan model (plans/region/import-plans.md §2.1, §2.6).
 *
 * An export plan is a PricePlan carrying {@code direction = DIRECTION_EXPORT} and
 * SELL DayRates only. The predicates that used to assume "a plan prices with its
 * BUY rates" have to flip with direction, or a dynamic export plan reads as
 * permanently pending and is never costed.
 */
public class ExportPlanModelTest {

    private static DayRate flatRate(String start, String end, double cost, int rateType) {
        DayRate dr = new DayRate();
        dr.setStartDate(start);
        dr.setEndDate(end);
        MinuteRateRange mrr = new MinuteRateRange();
        mrr.add(0, 1440, cost);
        dr.setMinuteRateRange(mrr);
        dr.setRateType(rateType);
        return dr;
    }

    private static PricePlan exportPlan() {
        PricePlan pp = new PricePlan();
        pp.setSupplier("Octopus Energy");
        pp.setPlanName("Outgoing Fixed");
        pp.setDirection(PricePlan.DIRECTION_EXPORT);
        return pp;
    }

    private static DynamicTerms completeTerms() {
        DynamicTerms dt = new DynamicTerms();
        dt.setMarket("GB-AGILE-EXPORT-C");
        dt.setMultiplier(1.0);
        dt.setAdder(0.0);
        return dt;
    }

    // ── direction basics ────────────────────────────────────────────────────

    @Test
    public void plansAreImportByDefault() {
        PricePlan pp = new PricePlan();
        assertEquals(PricePlan.DIRECTION_IMPORT, pp.getDirection());
        assertFalse(pp.isExport());
        assertEquals(DayRate.RATE_BUY, pp.primaryRateType());
    }

    @Test
    public void anExportPlanPricesWithItsSellRates() {
        assertTrue(exportPlan().isExport());
        assertEquals(DayRate.RATE_SELL, exportPlan().primaryRateType());
    }

    // ── validation ──────────────────────────────────────────────────────────

    @Test
    public void anExportPlanValidatesOnItsSellRatesAlone() {
        List<DayRate> rates = Collections.singletonList(
                flatRate("01/01", "12/31", 15.0, DayRate.RATE_SELL));
        assertEquals(PricePlan.VALID_PLAN, exportPlan().validatePlan(rates));
    }

    @Test
    public void anExportPlanWithNoSellRatesIsInvalid() {
        assertEquals(PricePlan.INVALID_PLAN_NO_DAY_RATES + PricePlan.EXPORT_REASON_OFFSET,
                exportPlan().validatePlan(Collections.emptyList()));
    }

    @Test
    public void anExportPlanSellRatesMustStillTileTheYear() {
        List<DayRate> rates = Collections.singletonList(
                flatRate("01/01", "06/30", 15.0, DayRate.RATE_SELL));
        assertEquals(PricePlan.INVALID_PLAN_MISSING_DATES + PricePlan.EXPORT_REASON_OFFSET,
                exportPlan().validatePlan(rates));
    }

    /** A BUY rate on an export plan is a direction mix-up, and must be caught
     *  rather than silently ignored. */
    @Test
    public void anExportPlanRejectsImportRates() {
        List<DayRate> rates = Arrays.asList(
                flatRate("01/01", "12/31", 15.0, DayRate.RATE_SELL),
                flatRate("01/01", "12/31", 25.0, DayRate.RATE_BUY));
        assertEquals(PricePlan.INVALID_PLAN_EXPORT_HAS_IMPORT_RATES,
                exportPlan().validatePlan(rates));
    }

    @Test
    public void importPlanValidationIsUnchanged() {
        List<DayRate> rates = Collections.singletonList(
                flatRate("01/01", "12/31", 25.0, DayRate.RATE_BUY));
        assertEquals(PricePlan.VALID_PLAN, new PricePlan().validatePlan(rates));
    }

    // ── pending dynamic, per direction ──────────────────────────────────────

    /** The trap this whole change exists to avoid: a materialised dynamic export
     *  plan has no BUY rates by design, and must NOT read as pending. */
    @Test
    public void aMaterialisedDynamicExportPlanIsNotPending() {
        PricePlan pp = exportPlan();
        pp.setDynamicTerms(completeTerms());
        List<DayRate> rates = Collections.singletonList(
                flatRate("01/01", "12/31", 15.0, DayRate.RATE_SELL));
        assertFalse("SELL rates are this plan's prices", pp.isPendingDynamic(rates));
        assertEquals(PricePlan.VALID_PLAN, pp.validatePlan(rates));
    }

    @Test
    public void aTermsOnlyDynamicExportPlanIsPending() {
        PricePlan pp = exportPlan();
        pp.setDynamicTerms(completeTerms());
        assertTrue(pp.isPendingDynamic(Collections.emptyList()));
        assertEquals("terms-only is a valid pending plan",
                PricePlan.VALID_PLAN, pp.validatePlan(Collections.emptyList()));
    }

    @Test
    public void dynamicImportPendingIsUnchanged() {
        PricePlan pp = new PricePlan();
        pp.setDynamicTerms(completeTerms());
        assertTrue(pp.isPendingDynamic(Collections.emptyList()));
        assertFalse(pp.isPendingDynamic(Collections.singletonList(
                flatRate("01/01", "12/31", 25.0, DayRate.RATE_BUY))));
    }

    // ── identity: direction is part of the key ──────────────────────────────

    @Test
    public void identityIncludesDirection() {
        PricePlan imp = new PricePlan();
        imp.setSupplier("Octopus Energy");
        imp.setPlanName("Flat");
        PricePlan exp = new PricePlan();
        exp.setSupplier("Octopus Energy");
        exp.setPlanName("Flat");
        exp.setDirection(PricePlan.DIRECTION_EXPORT);

        assertNotEquals("same name, different side of the meter", imp, exp);
        // Load-bearing: loadPricePlans() returns Map<PricePlan, List<DayRate>>,
        // so equal keys would merge the two plans' rates into one entry.
        Map<PricePlan, String> byPlan = new HashMap<>();
        byPlan.put(imp, "import");
        byPlan.put(exp, "export");
        assertEquals(2, byPlan.size());
    }

    @Test
    public void nameUsageChecksAreDirectionScoped() {
        PricePlan imp = new PricePlan();
        imp.setSupplier("Octopus Energy");
        imp.setPlanName("Flat");
        imp.setPricePlanIndex(1L);
        PricePlan exp = new PricePlan();
        exp.setSupplier("Octopus Energy");
        exp.setPlanName("Flat");
        exp.setDirection(PricePlan.DIRECTION_EXPORT);
        exp.setPricePlanIndex(2L);

        assertEquals("an export plan may reuse an import plan's name",
                PricePlan.VALID_PLAN,
                exp.checkNameUsageIn(new java.util.HashSet<>(Collections.singletonList(imp))));
    }

    @Test
    public void copyCarriesDirectionAndTags() {
        PricePlan pp = exportPlan();
        pp.setCompatibleWith(new CompatibilityTags(
                Collections.singletonList("Octopus Energy:*")));
        PricePlan copy = pp.copy();
        assertEquals(PricePlan.DIRECTION_EXPORT, copy.getDirection());
        assertTrue(copy.isCompatibleWith(namedImport("Octopus Energy", "Flexible")));
    }

    private static PricePlan namedImport(String supplier, String plan) {
        PricePlan pp = new PricePlan();
        pp.setSupplier(supplier);
        pp.setPlanName(plan);
        return pp;
    }
}
