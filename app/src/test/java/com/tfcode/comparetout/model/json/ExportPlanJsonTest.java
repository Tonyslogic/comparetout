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
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.priceplan.CompatibilityTags;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Export plans through the JSON vector (plans/region/import-plans.md §6).
 *
 * The same shape serves the file export, the clipboard share and the per-plan
 * Share button, so one round trip covers all three.
 *
 * Two properties matter beyond "the fields survive":
 * <ol>
 *   <li>a pre-v17 file — no Direction, no CompatibleWith — must still import as
 *       an ordinary import plan, and an import plan must still export with those
 *       fields <b>absent</b>, so old files stay byte-identical; and</li>
 *   <li>pairings are carried by NAME, because {@code pricePlanIndex} is
 *       device-local and an id list would be meaningless after transfer.</li>
 * </ol>
 */
public class ExportPlanJsonTest {

    private static final Type LIST_TYPE = new TypeToken<List<PricePlanJsonFile>>(){}.getType();

    private static DayRate flat(double cost, int rateType) {
        DayRate dr = new DayRate();
        dr.setStartDate("01/01");
        dr.setEndDate("12/31");
        MinuteRateRange mrr = new MinuteRateRange();
        mrr.add(0, 1440, cost);
        dr.setMinuteRateRange(mrr);
        dr.setRateType(rateType);
        return dr;
    }

    private static PricePlan exportPlan(long id, String... tags) {
        PricePlan pp = new PricePlan();
        pp.setPricePlanIndex(id);
        pp.setSupplier("Octopus Energy");
        pp.setPlanName("Outgoing Fixed");
        pp.setDirection(PricePlan.DIRECTION_EXPORT);
        if (tags.length > 0) pp.setCompatibleWith(new CompatibilityTags(Arrays.asList(tags)));
        return pp;
    }

    private static PricePlan importPlan() {
        PricePlan pp = new PricePlan();
        pp.setPricePlanIndex(1L);
        pp.setSupplier("Octopus Energy");
        pp.setPlanName("Flexible");
        pp.setFeed(15.0);
        return pp;
    }

    private static List<PricePlanJsonFile> parse(String json) {
        return new Gson().fromJson(json, LIST_TYPE);
    }

    // ── backwards compatibility ─────────────────────────────────────────────

    @Test
    public void importPlansExportWithoutTheNewFields() {
        Map<PricePlan, List<DayRate>> plans = new HashMap<>();
        plans.put(importPlan(), Collections.singletonList(flat(25.0, DayRate.RATE_BUY)));

        String json = PricePlanJsonTools.createPricePlanJson(plans);
        assertFalse("pre-v17 files must stay byte-identical", json.contains("Direction"));
        assertFalse(json.contains("CompatibleWith"));
        assertFalse(json.contains("SelectedWith"));
    }

    @Test
    public void aFileWithNoDirectionImportsAsAnImportPlan() {
        PricePlanJsonFile ppj = new PricePlanJsonFile();
        ppj.supplier = "TestCo";
        ppj.plan = "Flat";
        PricePlan back = PricePlanJsonTools.createPricePlan(ppj);
        assertEquals(PricePlan.DIRECTION_IMPORT, back.getDirection());
        assertNull(back.getCompatibleWith());
    }

    // ── export plans round-trip ─────────────────────────────────────────────

    @Test
    public void anExportPlanRoundTripsWithItsTagsAndRates() {
        Map<PricePlan, List<DayRate>> plans = new LinkedHashMap<>();
        plans.put(exportPlan(2L, "Octopus Energy:*"),
                Collections.singletonList(flat(15.0, DayRate.RATE_SELL)));

        List<PricePlanJsonFile> parsed = parse(
                PricePlanJsonTools.createPricePlanJson(plans));
        assertEquals(1, parsed.size());
        assertEquals("export", parsed.get(0).direction);
        assertEquals(Collections.singletonList("Octopus Energy:*"),
                parsed.get(0).compatibleWith);

        PricePlan back = PricePlanJsonTools.createPricePlan(parsed.get(0));
        assertTrue(back.isExport());
        assertTrue("tags must still match live",
                back.isCompatibleWith(importPlan()));

        // The SELL rate survives as a SELL rate.
        DayRate rate = PricePlanJsonTools.createDayRate(parsed.get(0).rates.get(0));
        assertEquals(DayRate.RATE_SELL, rate.getRateType());
        assertEquals(15.0, rate.getMinuteRateRange().lookup(600), 1e-9);
    }

    @Test
    public void anUntaggedExportPlanStaysOpenMarket() {
        Map<PricePlan, List<DayRate>> plans = new HashMap<>();
        plans.put(exportPlan(2L), Collections.singletonList(flat(15.0, DayRate.RATE_SELL)));

        List<PricePlanJsonFile> parsed = parse(
                PricePlanJsonTools.createPricePlanJson(plans));
        assertNull("no tags → the field is absent", parsed.get(0).compatibleWith);

        PricePlan back = PricePlanJsonTools.createPricePlan(parsed.get(0));
        assertTrue(back.isCompatibleWith(importPlan()));
    }

    // ── pairings ────────────────────────────────────────────────────────────

    @Test
    public void pairingsRideOnTheExportPlanAsNames() {
        Map<PricePlan, List<DayRate>> plans = new LinkedHashMap<>();
        plans.put(importPlan(), Collections.singletonList(flat(25.0, DayRate.RATE_BUY)));
        plans.put(exportPlan(2L), Collections.singletonList(flat(15.0, DayRate.RATE_SELL)));

        Map<Long, List<String>> pairings = new HashMap<>();
        pairings.put(2L, Collections.singletonList("Octopus Energy:Flexible"));

        List<PricePlanJsonFile> parsed = parse(
                PricePlanJsonTools.createPricePlanJson(plans, pairings));

        PricePlanJsonFile exported = parsed.stream()
                .filter(p -> "export".equals(p.direction)).findFirst().orElseThrow();
        assertEquals(Collections.singletonList("Octopus Energy:Flexible"),
                exported.selectedWith);

        // Names, not device-local ids — that is what survives a transfer.
        assertEquals("Octopus Energy:Flexible", PricePlanJsonTools.planKey(importPlan()));

        PricePlanJsonFile imported = parsed.stream()
                .filter(p -> !"export".equals(p.direction)).findFirst().orElseThrow();
        assertNull("import plans never carry pairings", imported.selectedWith);
    }

    @Test
    public void aPlanSetWithNoPairingsOmitsSelectedWith() {
        Map<PricePlan, List<DayRate>> plans = new HashMap<>();
        plans.put(exportPlan(2L), Collections.singletonList(flat(15.0, DayRate.RATE_SELL)));

        String json = PricePlanJsonTools.createPricePlanJson(plans, new HashMap<>());
        assertFalse(json.contains("SelectedWith"));
    }

    // ── the single-plan Share shape ─────────────────────────────────────────

    @Test
    public void theSinglePlanShareShapeCarriesTheSameFields() {
        String json = PricePlanJsonTools.createSinglePricePlanJsonObject(
                exportPlan(2L, "Octopus Energy:*"),
                Collections.singletonList(flat(15.0, DayRate.RATE_SELL)),
                new ArrayList<>(Collections.singletonList("Octopus Energy:Flexible")));

        PricePlanJsonFile back = new Gson().fromJson(json, PricePlanJsonFile.class);
        assertEquals("export", back.direction);
        assertEquals(Collections.singletonList("Octopus Energy:*"), back.compatibleWith);
        assertEquals(Collections.singletonList("Octopus Energy:Flexible"), back.selectedWith);
    }

    @Test
    public void theSinglePlanShareShapeStaysCleanForImportPlans() {
        String json = PricePlanJsonTools.createSinglePricePlanJsonObject(
                importPlan(), Collections.singletonList(flat(25.0, DayRate.RATE_BUY)));
        assertFalse(json.contains("Direction"));
        assertFalse(json.contains("CompatibleWith"));
        assertFalse(json.contains("SelectedWith"));
    }

    /** Region-gated fields still round-trip regardless of direction — a GB user's
     *  export of an Irish plan must not lose its Feed (§3.2). */
    @Test
    public void theBundledFeedAlwaysRoundTrips() {
        Map<PricePlan, List<DayRate>> plans = new HashMap<>();
        plans.put(importPlan(), Collections.singletonList(flat(25.0, DayRate.RATE_BUY)));

        List<PricePlanJsonFile> parsed = parse(
                PricePlanJsonTools.createPricePlanJson(plans));
        assertEquals(15.0, parsed.get(0).feed, 1e-9);
        assertEquals(15.0, PricePlanJsonTools.createPricePlan(parsed.get(0)).getFeed(), 1e-9);
    }
}
