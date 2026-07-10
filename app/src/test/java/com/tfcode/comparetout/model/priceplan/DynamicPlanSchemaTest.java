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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v16 plan validation: BUY/SELL partition and the terms-only (pending dynamic)
 * branch. BUY-only plans must validate exactly as before; SELL rates are
 * optional but, when present, must independently tile the year and the day;
 * a dynamic plan with no BUY rates is valid iff its terms are materialisable.
 */
public class DynamicPlanSchemaTest {

    private static final double DELTA = 1e-9;

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

    private static DynamicTerms completeTerms() {
        DynamicTerms dt = new DynamicTerms();
        dt.setMarket("ISEM-DAM");
        dt.setMultiplier(1.0);
        dt.setAdder(4.5);
        return dt;
    }

    @Test
    public void legacyBuyOnlyPlanStillValidates() {
        List<DayRate> rates = Collections.singletonList(
                flatRate("01/01", "12/31", 25.0, DayRate.RATE_BUY));
        assertEquals(PricePlan.VALID_PLAN, new PricePlan().validatePlan(rates));
    }

    @Test
    public void emptyNonDynamicPlanIsInvalid() {
        assertEquals(PricePlan.INVALID_PLAN_NO_DAY_RATES,
                new PricePlan().validatePlan(new ArrayList<>()));
    }

    @Test
    public void sellRatesAloneDoNotSatisfyBuyCoverage() {
        List<DayRate> rates = Collections.singletonList(
                flatRate("01/01", "12/31", 15.0, DayRate.RATE_SELL));
        assertEquals(PricePlan.INVALID_PLAN_NO_DAY_RATES,
                new PricePlan().validatePlan(rates));
    }

    @Test
    public void buyAndSellFullYearIsValid() {
        List<DayRate> rates = Arrays.asList(
                flatRate("01/01", "12/31", 25.0, DayRate.RATE_BUY),
                flatRate("01/01", "12/31", 15.0, DayRate.RATE_SELL));
        assertEquals(PricePlan.VALID_PLAN, new PricePlan().validatePlan(rates));
    }

    @Test
    public void sellRatesMustIndependentlyCoverTheYear() {
        List<DayRate> rates = Arrays.asList(
                flatRate("01/01", "12/31", 25.0, DayRate.RATE_BUY),
                flatRate("01/01", "06/30", 15.0, DayRate.RATE_SELL));
        int result = new PricePlan().validatePlan(rates);
        assertEquals(PricePlan.INVALID_PLAN_MISSING_DATES + PricePlan.EXPORT_REASON_OFFSET, result);
        assertTrue(PricePlan.getInvalidReason(result).endsWith("(export)"));
    }

    @Test
    public void termsOnlyPendingPlanIsValid() {
        PricePlan pp = new PricePlan();
        pp.setDynamicTerms(completeTerms());
        assertEquals(PricePlan.VALID_PLAN, pp.validatePlan(new ArrayList<>()));
        assertTrue(pp.isDynamic());
        assertTrue(pp.isPendingDynamic(new ArrayList<>()));
    }

    @Test
    public void incompleteTermsAreInvalid() {
        PricePlan pp = new PricePlan();
        DynamicTerms dt = completeTerms();
        dt.setMarket(null);
        pp.setDynamicTerms(dt);
        assertEquals(PricePlan.INVALID_PLAN_INCOMPLETE_DYNAMIC_TERMS,
                pp.validatePlan(new ArrayList<>()));
    }

    @Test
    public void materialisedDynamicPlanGetsTheFullChecks() {
        PricePlan pp = new PricePlan();
        pp.setDynamicTerms(completeTerms());
        // BUY rates present but not covering the year: no longer pending, so the
        // normal coverage checks apply.
        List<DayRate> rates = Collections.singletonList(
                flatRate("01/01", "06/30", 25.0, DayRate.RATE_BUY));
        assertEquals(PricePlan.INVALID_PLAN_MISSING_DATES, pp.validatePlan(rates));
        assertFalse(pp.isPendingDynamic(rates));
    }

    @Test
    public void unitPriceAppliesMultiplierAdderAndClamps() {
        DynamicTerms dt = completeTerms(); // multiplier 1.0, adder 4.5
        assertEquals(14.5, dt.unitPrice(10.0), DELTA);
        dt.setCap(45.0);
        assertEquals(45.0, dt.unitPrice(60.0), DELTA);
        dt.setFloor(0.0);
        assertEquals(0.0, dt.unitPrice(-20.0), DELTA);
    }

    @Test
    public void copyOfADynamicPlanIsATermsOnlyCopy() {
        PricePlan pp = new PricePlan();
        pp.setDynamicTerms(completeTerms());
        PricePlan copy = pp.copy();
        assertTrue(copy.isDynamic());
        // Callers copy DayRates separately; a dynamic copy stays pending.
        assertTrue(copy.isPendingDynamic(new ArrayList<>()));
    }
}
