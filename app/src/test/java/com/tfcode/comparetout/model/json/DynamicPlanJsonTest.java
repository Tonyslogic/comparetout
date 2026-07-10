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

package com.tfcode.comparetout.model.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.scenario.DischargeToGridJson;
import com.tfcode.comparetout.model.json.scenario.EVChargeJson;
import com.tfcode.comparetout.model.json.scenario.EVDivertJson;
import com.tfcode.comparetout.model.json.scenario.HWScheduleJson;
import com.tfcode.comparetout.model.json.scenario.LoadShiftJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.DynamicTerms;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.LoadShift;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v16 JSON schema round-trips: the "Dynamic" terms block, optional "Rates",
 * per-rate "RateType", and the optional schedule window fields. Old files must
 * import unchanged and old-shaped data must export byte-identically (new fields
 * absent unless non-default) — the scenario side of that is also guarded by
 * JsonToolsTest.roundTrip against the shipped sample data.
 */
public class DynamicPlanJsonTest {

    private static final Gson GSON = new Gson();

    private static final String TERMS_ONLY_JSON = "{"
            + "\"Supplier\": \"I-SEM Day-Ahead\","
            + "\"Plan\": \"DA + 4.5 capped at 45\","
            + "\"Dynamic\": {"
            + "  \"Market\": \"ISEM-DAM\","
            + "  \"Multiplier\": 1.0,"
            + "  \"Adder\": 4.5,"
            + "  \"Cap\": 45.0"
            + "}}";

    private static DayRate rate(String start, String end, double cost, int rateType) {
        DayRate dr = new DayRate();
        dr.setStartDate(start);
        dr.setEndDate(end);
        MinuteRateRange mrr = new MinuteRateRange();
        mrr.add(0, 1440, cost);
        dr.setMinuteRateRange(mrr);
        dr.setRateType(rateType);
        return dr;
    }

    @Test
    public void termsOnlyJsonImportsAsAPendingDynamicPlan() {
        PricePlanJsonFile ppj = GSON.fromJson(TERMS_ONLY_JSON, PricePlanJsonFile.class);
        assertNull(ppj.rates);
        PricePlan pp = JsonTools.createPricePlan(ppj);
        assertTrue(pp.isDynamic());
        assertTrue(pp.isPendingDynamic(new ArrayList<>()));
        assertEquals(PricePlan.VALID_PLAN, pp.validatePlan(new ArrayList<>()));
        assertEquals("ISEM-DAM", pp.getDynamicTerms().getMarket());
        assertEquals(45.0, pp.getDynamicTerms().getCap(), 1e-9);
        assertNull(pp.getDynamicTerms().getYear());
        // Optional scalars absent in a terms-only file must default, not NPE.
        assertEquals(0.0, pp.getFeed(), 1e-9);
        assertEquals(0.0, pp.getStandingCharges(), 1e-9);
    }

    @Test
    public void dynamicPlanExportsTermsOnly() {
        PricePlan pp = new PricePlan();
        pp.setSupplier("I-SEM Day-Ahead");
        pp.setPlanName("DA + 4.5 (2025)");
        DynamicTerms dt = new DynamicTerms();
        dt.setMarket("ISEM-DAM");
        dt.setYear(2025);
        dt.setMultiplier(1.0);
        dt.setAdder(4.5);
        pp.setDynamicTerms(dt);
        // Even with materialised rates on hand, the export must omit them:
        // they are regenerated locally and must not be redistributed.
        List<DayRate> materialised = Collections.singletonList(
                rate("01/01", "12/31", 25.0, DayRate.RATE_BUY));
        String json = JsonTools.createSinglePricePlanJsonObject(pp, materialised);
        PricePlanJsonFile back = GSON.fromJson(json, PricePlanJsonFile.class);
        assertNull(back.rates);
        assertEquals("ISEM-DAM", back.dynamic.market);
        assertEquals(Integer.valueOf(2025), back.dynamic.year);
        assertEquals(4.5, back.dynamic.adder, 1e-9);
    }

    @Test
    public void rateTypeRoundTripsAndIsAbsentForBuy() {
        PricePlan pp = new PricePlan();
        pp.setSupplier("S");
        pp.setPlanName("P");
        List<DayRate> rates = Arrays.asList(
                rate("01/01", "12/31", 25.0, DayRate.RATE_BUY),
                rate("01/01", "12/31", 15.0, DayRate.RATE_SELL));
        String json = JsonTools.createSinglePricePlanJsonObject(pp, rates);
        PricePlanJsonFile back = GSON.fromJson(json, PricePlanJsonFile.class);
        assertEquals(2, back.rates.size());
        // BUY omits the field entirely — pre-v16 exports stay byte-identical.
        assertNull(back.rates.get(0).rateType);
        assertEquals("sell", back.rates.get(1).rateType);
        DayRate buyBack = JsonTools.createDayRate(back.rates.get(0));
        DayRate sellBack = JsonTools.createDayRate(back.rates.get(1));
        assertEquals(DayRate.RATE_BUY, buyBack.getRateType());
        assertEquals(DayRate.RATE_SELL, sellBack.getRateType());
    }

    @Test
    public void legacyPlanJsonImportsAndExportsWithoutNewFields() {
        String legacy = "{\"Supplier\": \"EI\", \"Plan\": \"Std\", \"Feed\": 21.0,"
                + "\"Standing charges\": 280.0, \"Bonus cash\": 0.0, \"Active\": true,"
                + "\"LastUpdate\": \"2024-01-01\", \"Reference\": \"r\","
                + "\"Rates\": [{\"Days\": [0,1,2,3,4,5,6], \"Hours\": [],"
                + "\"MinuteRange\": [{\"startMinute\": 0, \"endMinute\": 1440, \"cost\": 25.0}],"
                + "\"startDate\": \"01/01\", \"endDate\": \"12/31\"}]}";
        PricePlanJsonFile ppj = GSON.fromJson(legacy, PricePlanJsonFile.class);
        PricePlan pp = JsonTools.createPricePlan(ppj);
        assertFalse(pp.isDynamic());
        List<DayRate> drs = new ArrayList<>();
        for (DayRateJson drj : ppj.rates) drs.add(JsonTools.createDayRate(drj));
        assertEquals(DayRate.RATE_BUY, drs.get(0).getRateType());
        assertEquals(PricePlan.VALID_PLAN, pp.validatePlan(drs));
        String out = JsonTools.createSinglePricePlanJsonObject(pp, drs);
        assertFalse(out.contains("RateType"));
        assertFalse(out.contains("Dynamic"));
    }

    @Test
    public void scheduleWindowFieldsDefaultWhenAbsent() {
        String legacyShift = "{\"Name\": \"Smart night\", \"begin\": 2, \"end\": 4,"
                + "\"stop at\": 80.0, \"months\": [1,2,3], \"days\": [0,1], \"Inverter\": \"A\"}";
        LoadShift ls = JsonTools.createLoadShift(GSON.fromJson(legacyShift, LoadShiftJson.class));
        assertEquals("01/01", ls.getStartDate());
        assertEquals("12/31", ls.getEndDate());
        assertEquals(-1, ls.getBeginMinute());
        assertEquals(-1, ls.getEndMinute());
        // Effective window derives from the legacy whole-hour fields; the
        // engine INCLUDES the end hour for battery schedules, so end=4 ⇒ 300.
        assertEquals(120, ls.getEffectiveBeginMinute());
        assertEquals(300, ls.getEffectiveEndMinute());
    }

    @Test
    public void scheduleDefaultsAreOmittedOnExport() {
        LoadShift ls = new LoadShift();
        String json = GSON.toJson(JsonTools.createLoadShiftJson(
                Collections.singletonList(ls)));
        assertFalse(json.contains("StartDate"));
        assertFalse(json.contains("BeginMinute"));
    }

    @Test
    public void loadShiftWindowFieldsRoundTrip() {
        LoadShift ls = new LoadShift();
        ls.setStartDate("06/01");
        ls.setEndDate("08/31");
        ls.setBeginMinute(150);
        ls.setEndMinute(270);
        String json = GSON.toJson(JsonTools.createLoadShiftJson(Collections.singletonList(ls)));
        LoadShiftJson[] back = GSON.fromJson(json, LoadShiftJson[].class);
        LoadShift restored = JsonTools.createLoadShift(back[0]);
        assertEquals("06/01", restored.getStartDate());
        assertEquals("08/31", restored.getEndDate());
        assertEquals(150, restored.getEffectiveBeginMinute());
        assertEquals(270, restored.getEffectiveEndMinute());
    }

    @Test
    public void allFiveScheduleTypesRoundTripTheWindowFields() {
        DischargeToGrid d2g = new DischargeToGrid();
        d2g.setStartDate("11/01");
        d2g.setBeginMinute(90);
        DischargeToGridJson[] d2gBack = GSON.fromJson(
                GSON.toJson(JsonTools.createDischargeJson(Collections.singletonList(d2g))),
                DischargeToGridJson[].class);
        assertEquals("11/01", JsonTools.createDischarge(d2gBack[0]).getStartDate());
        assertEquals(90, JsonTools.createDischarge(d2gBack[0]).getBeginMinute());

        EVCharge evc = new EVCharge();
        evc.setEndDate("03/31");
        evc.setEndMinute(390);
        EVChargeJson[] evcBack = GSON.fromJson(
                GSON.toJson(JsonTools.createEVChargeJson(Collections.singletonList(evc))),
                EVChargeJson[].class);
        assertEquals("03/31", JsonTools.createEVCharge(evcBack[0]).getEndDate());
        assertEquals(390, JsonTools.createEVCharge(evcBack[0]).getEndMinute());

        HWSchedule hws = new HWSchedule();
        hws.setStartDate("05/15");
        hws.setEndMinute(345);
        HWScheduleJson[] hwsBack = GSON.fromJson(
                GSON.toJson(JsonTools.createHWScheduleJson(Collections.singletonList(hws))),
                HWScheduleJson[].class);
        assertEquals("05/15", JsonTools.createHWSchedule(hwsBack[0]).getStartDate());
        assertEquals(345, JsonTools.createHWSchedule(hwsBack[0]).getEndMinute());

        EVDivert evd = new EVDivert();
        evd.setStartDate("07/01");
        evd.setBeginMinute(660);
        EVDivertJson[] evdBack = GSON.fromJson(
                GSON.toJson(JsonTools.createEVDivertJson(Collections.singletonList(evd))),
                EVDivertJson[].class);
        EVDivert evdRestored = JsonTools.createEVDivert(evdBack[0]);
        assertEquals("07/01", evdRestored.getStartDate());
        assertEquals(660, evdRestored.getBeginMinute());
    }
}
