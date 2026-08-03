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

package com.tfcode.comparetout.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.ops.CombinationOps;
import com.tfcode.comparetout.model.priceplan.PlanCombination;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Pairing storage and the rules costing reads off it
 * (plans/region/import-plans.md §2.4, §3.3, §5.4).
 *
 * Runs against a real in-memory Room database so the DAO queries — including the
 * prune and delete clauses that guard against stranded rows — are exercised as
 * written rather than mocked.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PairingRulesTest {

    private ToutcDB db;
    private CombinationOps ops;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, ToutcDB.class)
                .allowMainThreadQueries().build();
        ops = new CombinationOps(db);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private long addPlan(String name, int direction) {
        PricePlan pp = new PricePlan();
        pp.setSupplier("TestCo");
        pp.setPlanName(name);
        pp.setDirection(direction);
        return db.pricePlanDAO().addNewPricePlanWithDayRates(
                pp, Collections.emptyList(), false);
    }

    // ── grouping ────────────────────────────────────────────────────────────

    @Test
    public void importPlansWithNoPairingAreAbsentFromTheMap() {
        long imp = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        assertTrue(ops.pairingsByImportPlan().isEmpty());
        // Absent means "price against the plan's own bundled export side".
        assertFalse(ops.pairingsByImportPlan().containsKey(imp));
    }

    @Test
    public void pairingsGroupByImportPlan() {
        long impA = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        long impB = addPlan("DayNight", PricePlan.DIRECTION_IMPORT);
        long expA = addPlan("Outgoing Fixed", PricePlan.DIRECTION_EXPORT);
        long expB = addPlan("Outgoing TOU", PricePlan.DIRECTION_EXPORT);

        ops.select(impA, expA, PlanCombination.SOURCE_MANUAL);
        ops.select(impA, expB, PlanCombination.SOURCE_MANUAL);
        ops.select(impB, expA, PlanCombination.SOURCE_HEURISTIC);

        Map<Long, Set<Long>> byImport = ops.pairingsByImportPlan();
        assertEquals(2, byImport.size());
        assertEquals(2, byImport.get(impA).size());
        assertTrue(byImport.get(impA).contains(expA));
        assertTrue(byImport.get(impA).contains(expB));
        assertEquals(Collections.singleton(expA), byImport.get(impB));
    }

    /** Re-ticking is idempotent: same row, re-stamped source, not a duplicate. */
    @Test
    public void selectingTwiceOnlyRestampsTheSource() {
        long imp = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        long exp = addPlan("Outgoing Fixed", PricePlan.DIRECTION_EXPORT);

        ops.select(imp, exp, PlanCombination.SOURCE_HEURISTIC);
        ops.select(imp, exp, PlanCombination.SOURCE_MANUAL);

        assertEquals(1, ops.all().size());
        assertEquals(PlanCombination.SOURCE_MANUAL, ops.all().get(0).getSource());
    }

    @Test
    public void deselectingRestoresTheBundledCase() {
        long imp = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        long exp = addPlan("Outgoing Fixed", PricePlan.DIRECTION_EXPORT);
        ops.select(imp, exp, PlanCombination.SOURCE_MANUAL);
        assertTrue(ops.pairingsByImportPlan().containsKey(imp));

        ops.deselect(imp, exp);
        assertFalse("unticking must bring the bundled row back",
                ops.pairingsByImportPlan().containsKey(imp));
    }

    // ── cleanup: a stranded pairing would hide an import plan for good ──────

    @Test
    public void deletingAPlanDropsItsPairingsFromBothSides() {
        long impA = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        long impB = addPlan("DayNight", PricePlan.DIRECTION_IMPORT);
        long exp = addPlan("Outgoing Fixed", PricePlan.DIRECTION_EXPORT);
        ops.select(impA, exp, PlanCombination.SOURCE_MANUAL);
        ops.select(impB, exp, PlanCombination.SOURCE_MANUAL);

        ops.removePlan(exp);

        assertTrue("a pairing naming a deleted export plan would keep both import "
                + "plans' bundled rows suppressed forever", ops.all().isEmpty());
    }

    @Test
    public void pruneDropsPairingsWhosePlansAreGone() {
        long imp = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        long exp = addPlan("Outgoing Fixed", PricePlan.DIRECTION_EXPORT);
        ops.select(imp, exp, PlanCombination.SOURCE_MANUAL);

        // Delete the plan row directly, bypassing the ops cleanup, to simulate a
        // path that forgot to tidy up.
        db.pricePlanDAO().deletePricePlan(exp);
        assertEquals("still stranded before the prune", 1, ops.all().size());

        ops.prune();
        assertTrue(ops.all().isEmpty());
    }

    // ── costing cleanup, the other half of §5.4 ─────────────────────────────

    @Test
    public void deletingAnExportPlanClearsCostingsThatNameIt() {
        long imp = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        long exp = addPlan("Outgoing Fixed", PricePlan.DIRECTION_EXPORT);

        db.costingDAO().saveCosting(costing(1L, imp, Costings.BUNDLED_EXPORT));
        db.costingDAO().saveCosting(costing(1L, imp, exp));

        // Deleting the EXPORT plan must clear the pair row even though the export
        // plan appears in the second key column, not the first.
        db.costingDAO().deleteRelatedCostings((int) exp);

        assertEquals("only the bundled row survives",
                1, db.costingDAO().getAllCostingsForScenario(1L).size());
        assertTrue(db.costingDAO().costingExists(1L, imp, Costings.BUNDLED_EXPORT));
        assertFalse(db.costingDAO().costingExists(1L, imp, exp));
    }

    @Test
    public void pruneCostingsKeepsBundledRowsAndDropsOrphanedPairs() {
        long imp = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        long exp = addPlan("Outgoing Fixed", PricePlan.DIRECTION_EXPORT);
        db.costingDAO().saveCosting(costing(1L, imp, Costings.BUNDLED_EXPORT));
        db.costingDAO().saveCosting(costing(1L, imp, exp));

        db.pricePlanDAO().deletePricePlan(exp);
        db.costingDAO().pruneCostings();

        // The bundled sentinel is 0, not a plan id — it must survive the prune's
        // existence test, or every legacy row would vanish on sight.
        assertTrue("bundled rows must not be pruned",
                db.costingDAO().costingExists(1L, imp, Costings.BUNDLED_EXPORT));
        assertFalse(db.costingDAO().costingExists(1L, imp, exp));
    }

    /** Both rows coexist for one (scenario, import plan): the three-column key is
     *  what makes a bundled row and a pair row distinct. */
    @Test
    public void bundledAndPairedRowsCoexistUnderTheThreeColumnKey() {
        long imp = addPlan("Flat", PricePlan.DIRECTION_IMPORT);
        long expA = addPlan("Outgoing Fixed", PricePlan.DIRECTION_EXPORT);
        long expB = addPlan("Outgoing TOU", PricePlan.DIRECTION_EXPORT);

        db.costingDAO().saveCosting(costing(1L, imp, Costings.BUNDLED_EXPORT));
        db.costingDAO().saveCosting(costing(1L, imp, expA));
        db.costingDAO().saveCosting(costing(1L, imp, expB));

        assertEquals(3, db.costingDAO().getAllCostingsForScenario(1L).size());
    }

    private static Costings costing(long scenarioId, long importId, long exportId) {
        Costings c = new Costings();
        c.setScenarioID(scenarioId);
        c.setPricePlanID(importId);
        c.setExportPlanID(exportId);
        c.setScenarioName("Sim");
        c.setFullPlanName("TestCo:Flat");
        c.setBuy(1000.0);
        c.setSell(100.0);
        c.setNet(900.0);
        return c;
    }
}
