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

package com.tfcode.comparetout.model.ops;

import com.tfcode.comparetout.model.ToutcDB;
import com.tfcode.comparetout.model.dao.CombinationDAO;
import com.tfcode.comparetout.model.json.PricePlanJsonTools;
import com.tfcode.comparetout.model.priceplan.PlanCombination;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestration over the ticked (import × export) pairings — the mirror of the
 * other {@code …Ops} classes, keeping multi-step logic out of the DAO and out of
 * {@code ToutcRepository}.
 *
 * <p>The central product is {@link #pairingsByImportPlan()}: for each import plan
 * that has at least one ticked export plan, the set of export plan ids. Costing
 * consumes it twice — once to know which pairs to write, and once to know which
 * import plans must NOT get a bundled row (an import plan with a selected export
 * contract has its scalar feed superseded, not supplemented; see
 * plans/region/import-plans.md §3.3).
 */
public class CombinationOps {

    private final CombinationDAO combinationDAO;

    public CombinationOps(ToutcDB db) {
        this.combinationDAO = db.combinationDAO();
    }

    /**
     * Ticked export plan ids grouped by import plan id. Import plans with no
     * ticked pairing are absent from the map entirely — callers treat "absent"
     * as "price this plan against its own bundled export side".
     */
    public Map<Long, Set<Long>> pairingsByImportPlan() {
        Map<Long, Set<Long>> byImport = new HashMap<>();
        for (PlanCombination pc : combinationDAO.getCombinationsNow()) {
            byImport.computeIfAbsent(pc.getImportPlanID(), k -> new HashSet<>())
                    .add(pc.getExportPlanID());
        }
        return byImport;
    }

    /** Tick a pairing. Idempotent — re-ticking only re-stamps the source. */
    public void select(long importPlanID, long exportPlanID, String source) {
        combinationDAO.upsert(new PlanCombination(importPlanID, exportPlanID, source));
    }

    /** Untick a pairing. */
    public void deselect(long importPlanID, long exportPlanID) {
        combinationDAO.delete(importPlanID, exportPlanID);
    }

    /**
     * Remove every trace of a deleted plan: its pairings on either side, so the
     * combination UI cannot show a row pointing at a plan that no longer exists.
     * Costings are cleaned separately by {@code CostingDAO.deleteRelatedCostings}.
     */
    public void removePlan(long planID) {
        combinationDAO.deleteForPlan(planID);
    }

    /** Drop pairings whose import or export plan has since been deleted. */
    public void prune() {
        combinationDAO.pruneCombinations();
    }

    /** Every pairing, for callers that want the raw rows (export/round-trip). */
    public List<PlanCombination> all() {
        return new ArrayList<>(combinationDAO.getCombinationsNow());
    }

    /**
     * Export-plan id → the {@code "Supplier:Plan"} keys of the import plans it is
     * paired with, ready for the JSON {@code SelectedWith} field. Named rather
     * than id-keyed because {@code pricePlanIndex} is device-local.
     *
     * @param plans every plan currently in the library, to resolve ids to names
     */
    public Map<Long, List<String>> pairingsAsNames(List<PricePlan> plans) {
        Map<Long, PricePlan> byId = new HashMap<>();
        for (PricePlan pp : plans) byId.put(pp.getPricePlanIndex(), pp);

        Map<Long, List<String>> byExport = new HashMap<>();
        for (PlanCombination pc : combinationDAO.getCombinationsNow()) {
            PricePlan importPlan = byId.get(pc.getImportPlanID());
            if (null == importPlan) continue;   // stale row; prune() will clear it
            byExport.computeIfAbsent(pc.getExportPlanID(), k -> new ArrayList<>())
                    .add(PricePlanJsonTools.planKey(importPlan));
        }
        return byExport;
    }

    /**
     * Re-tick the pairings named by an imported export plan's {@code SelectedWith}.
     * <p>
     * Must run in a SECOND pass, after every plan in the file has been inserted:
     * an export plan may name an import plan that appears later in the same file.
     * Names that do not resolve on this device are <b>silently dropped</b> — a
     * shared file naming a plan the recipient does not have should not fail the
     * import.
     *
     * @return how many named pairings could not be resolved
     */
    public int restorePairings(long exportPlanID, List<String> selectedWith,
                               List<PricePlan> plans) {
        if (null == selectedWith || selectedWith.isEmpty()) return 0;
        Map<String, Long> byKey = new HashMap<>();
        for (PricePlan pp : plans) {
            if (pp.isExport()) continue;        // pairing is export → import
            byKey.put(PricePlanJsonTools.planKey(pp).toLowerCase(java.util.Locale.ROOT),
                    pp.getPricePlanIndex());
        }
        int unresolved = 0;
        for (String key : selectedWith) {
            if (null == key) { unresolved++; continue; }
            Long importId = byKey.get(key.trim().toLowerCase(java.util.Locale.ROOT));
            if (null == importId) unresolved++;
            else select(importId, exportPlanID, PlanCombination.SOURCE_MANUAL);
        }
        return unresolved;
    }
}
