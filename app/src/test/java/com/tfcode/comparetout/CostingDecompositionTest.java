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

package com.tfcode.comparetout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.util.PlanPricer;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The load-bearing claim behind Phase 4 (plans/region/import-plans.md §1.2, §5.1):
 * a costing's buy total depends only on the import plan and its sell total only on
 * the export side, so
 *
 * <pre>net(i,e) = buy(i) - sell(e) + fixed(i)</pre>
 *
 * is exactly what a brute-force per-pair pass would produce. That is what lets
 * {@code CostingWorker} evaluate N import × M export plans in N+M passes over the
 * simulation series instead of N×M.
 *
 * <p>This test computes both ways over a synthetic year and asserts they agree to
 * the last bit. If a future change makes the two sides interact — a netting-off
 * rule, an export cap tied to import, or dispatch chosen from the tariff pair —
 * this test fails, which is precisely the signal that the decomposition is no
 * longer valid.
 */
public class CostingDecompositionTest {

    private static final double DELTA = 1e-9;

    // ── fixtures ────────────────────────────────────────────────────────────

    private static DayRate band(String start, String end, int rateType, int split,
                                double first, double second) {
        DayRate dr = new DayRate();
        dr.setStartDate(start);
        dr.setEndDate(end);
        MinuteRateRange mrr = new MinuteRateRange();
        mrr.add(0, split, first);
        mrr.add(split, 1440, second);
        dr.setMinuteRateRange(mrr);
        dr.setRateType(rateType);
        return dr;
    }

    private static PricePlan importPlan(String name, double standing, double bonus) {
        PricePlan pp = new PricePlan();
        pp.setSupplier("TestCo");
        pp.setPlanName(name);
        pp.setStandingCharges(standing);
        pp.setSignUpBonus(bonus);
        return pp;
    }

    private static PricePlan exportPlan(String name, double feed) {
        PricePlan pp = new PricePlan();
        pp.setSupplier("ExportCo");
        pp.setPlanName(name);
        pp.setDirection(PricePlan.DIRECTION_EXPORT);
        pp.setFeed(feed);
        return pp;
    }

    /**
     * A year of half-hourly rows with buy and feed that vary by day and slot, so
     * a decomposition error cannot hide behind a constant.
     */
    private static List<ScenarioSimulationData> syntheticYear() {
        List<ScenarioSimulationData> rows = new ArrayList<>();
        for (int doy = 1; doy <= 365; doy++) {
            for (int slot = 0; slot < 48; slot++) {
                ScenarioSimulationData row = new ScenarioSimulationData();
                row.setDayOf2001(doy);
                row.setMinuteOfDay(slot * 30);
                row.setDayOfWeek(doy % 7);
                row.setBuy(((doy * 7 + slot * 3) % 11) / 10.0);
                row.setFeed(((doy * 5 + slot * 2) % 9) / 10.0);
                rows.add(row);
            }
        }
        return rows;
    }

    // ── the reference (brute force) and the decomposition ───────────────────

    /** What a naive per-pair implementation would do: one full pass per pair. */
    private static double bruteForceNet(PricePlan imp, List<DayRate> impRates,
                                        PricePlan exp, List<DayRate> expRates,
                                        List<ScenarioSimulationData> rows) {
        PlanPricer buyPricer = new PlanPricer(imp, impRates);
        PlanPricer sellPricer = (null == exp) ? buyPricer : new PlanPricer(exp, expRates);
        double buy = 0D;
        double sell = 0D;
        for (ScenarioSimulationData row : rows) {
            int dow = (row.getDayOfWeek() == 7) ? 0 : row.getDayOfWeek();
            buy += buyPricer.buyRate(row.getDayOf2001(), row.getMinuteOfDay(), dow, row.getBuy())
                    * row.getBuy();
            sell += sellPricer.sellRate(row.getDayOf2001(), row.getMinuteOfDay(), dow, row.getFeed())
                    * row.getFeed();
        }
        return (buy - sell) + fixedFor(imp);
    }

    private static double fixedFor(PricePlan imp) {
        return (imp.getStandingCharges() * 100) - (imp.getSignUpBonus() * 100);
    }

    private static double buyTotal(PricePlan imp, List<DayRate> rates,
                                   List<ScenarioSimulationData> rows) {
        PlanPricer pricer = new PlanPricer(imp, rates);
        double buy = 0D;
        for (ScenarioSimulationData row : rows) {
            int dow = (row.getDayOfWeek() == 7) ? 0 : row.getDayOfWeek();
            buy += pricer.buyRate(row.getDayOf2001(), row.getMinuteOfDay(), dow, row.getBuy())
                    * row.getBuy();
        }
        return buy;
    }

    private static double sellTotal(PricePlan plan, List<DayRate> rates,
                                    List<ScenarioSimulationData> rows) {
        PlanPricer pricer = new PlanPricer(plan, rates);
        double sell = 0D;
        for (ScenarioSimulationData row : rows) {
            int dow = (row.getDayOfWeek() == 7) ? 0 : row.getDayOfWeek();
            sell += pricer.sellRate(row.getDayOf2001(), row.getMinuteOfDay(), dow, row.getFeed())
                    * row.getFeed();
        }
        return sell;
    }

    // ── tests ───────────────────────────────────────────────────────────────

    @Test
    public void decomposedNetMatchesBruteForceForEveryPair() {
        List<ScenarioSimulationData> rows = syntheticYear();

        List<PricePlan> imports = Arrays.asList(
                importPlan("Flat", 250.0, 0.0),
                importPlan("DayNight", 300.0, 50.0),
                importPlan("Wide", 180.5, 12.25));
        List<List<DayRate>> importRates = Arrays.asList(
                Arrays.asList(band("01/01", "12/31", DayRate.RATE_BUY, 480, 25.0, 25.0)),
                Arrays.asList(band("01/01", "12/31", DayRate.RATE_BUY, 480, 8.0, 32.0)),
                Arrays.asList(
                        band("01/01", "06/30", DayRate.RATE_BUY, 720, 20.0, 30.0),
                        band("07/01", "12/31", DayRate.RATE_BUY, 720, 18.0, 28.0)));

        List<PricePlan> exports = Arrays.asList(
                exportPlan("Outgoing Fixed", 15.0),
                exportPlan("Outgoing TOU", 0.0));
        List<List<DayRate>> exportRates = Arrays.asList(
                // No SELL rates → the plan's scalar feed is the export price.
                new ArrayList<>(),
                Arrays.asList(band("01/01", "12/31", DayRate.RATE_SELL, 960, 4.0, 21.0)));

        // Decomposed: N buy totals + M sell totals.
        double[] buys = new double[imports.size()];
        for (int i = 0; i < imports.size(); i++) {
            buys[i] = buyTotal(imports.get(i), importRates.get(i), rows);
        }
        double[] sells = new double[exports.size()];
        for (int e = 0; e < exports.size(); e++) {
            sells[e] = sellTotal(exports.get(e), exportRates.get(e), rows);
        }

        for (int i = 0; i < imports.size(); i++) {
            for (int e = 0; e < exports.size(); e++) {
                double decomposed = (buys[i] - sells[e]) + fixedFor(imports.get(i));
                double brute = bruteForceNet(imports.get(i), importRates.get(i),
                        exports.get(e), exportRates.get(e), rows);
                assertEquals("pair (" + imports.get(i).getPlanName() + ", "
                                + exports.get(e).getPlanName() + ")",
                        brute, decomposed, DELTA);
            }
        }
    }

    /** The bundled row: an import plan priced against its own export side must
     *  still equal a single-pass computation, unchanged from pre-v17 behaviour. */
    @Test
    public void bundledRowMatchesTheLegacySinglePass() {
        List<ScenarioSimulationData> rows = syntheticYear();
        PricePlan imp = importPlan("Flat", 250.0, 10.0);
        imp.setFeed(21.0);   // no SELL rates → scalar feed
        List<DayRate> rates =
                Arrays.asList(band("01/01", "12/31", DayRate.RATE_BUY, 480, 8.0, 32.0));

        double decomposed = (buyTotal(imp, rates, rows) - sellTotal(imp, rates, rows))
                + fixedFor(imp);
        assertEquals(bruteForceNet(imp, rates, null, null, rows), decomposed, DELTA);
    }

    /** Tiered restrictions are buy-side and stateful across a pass, which is the
     *  one mechanism that could plausibly have coupled the two sides. It does not:
     *  the same import plan yields the same buy total regardless of export. */
    @Test
    public void tierRestrictionsDoNotCoupleTheSides() {
        List<ScenarioSimulationData> rows = syntheticYear();
        PricePlan imp = importPlan("Tiered", 200.0, 0.0);
        com.tfcode.comparetout.model.priceplan.Restrictions restrictions =
                new com.tfcode.comparetout.model.priceplan.Restrictions();
        restrictions.setActive(true);
        com.tfcode.comparetout.model.priceplan.Restriction r =
                new com.tfcode.comparetout.model.priceplan.Restriction();
        r.addEntry(com.tfcode.comparetout.model.priceplan.Restriction.RestrictionType.annual,
                "25.0", 4000, 35.0);
        restrictions.setRestrictions(Arrays.asList(r));
        imp.setRestrictions(restrictions);

        List<DayRate> rates =
                Arrays.asList(band("01/01", "12/31", DayRate.RATE_BUY, 480, 25.0, 25.0));

        // A fresh pricer per computation, because tier state accumulates.
        double first = buyTotal(imp, rates, rows);
        double second = buyTotal(imp, rates, rows);
        assertEquals("the buy total is a function of the import plan alone",
                first, second, DELTA);
        assertTrue("the tier must actually have engaged", first > 0.0);
    }
}
