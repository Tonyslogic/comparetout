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

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * One ticked (import plan × export plan) pairing.
 *
 * <p>Costing does not consult this table — every active pair is costed regardless
 * (the buy and sell totals decompose, so N imports × M exports costs N+M passes
 * over the simulation series, not N×M; see plans/region/import-plans.md §1.2).
 * What a row does is:
 *
 * <ol>
 *   <li>make the pair visible in Compare and on the dashboard, and</li>
 *   <li>suppress the import plan's <b>bundled</b> row — a plan with at least one
 *       ticked pair has its scalar {@link PricePlan#getFeed()} treated as zero,
 *       because a separate export contract replaces the bundled shortcut rather
 *       than adding to it (§3.3).</li>
 * </ol>
 *
 * <p>Deliberately not scenario-scoped: a pairing is a statement about which
 * tariff combinations exist in the market, which is true across every scenario.
 * Adding a nullable {@code scenarioID} later is an additive column.
 *
 * <p>The table is also the artifact a future ranking heuristic will populate —
 * which is why {@link #getSource()} exists and why this is a persisted table
 * rather than transient UI state (§11).
 */
@Entity(tableName = "plan_combinations",
        primaryKeys = {"importPlanID", "exportPlanID"},
        indices = {@Index(value = {"exportPlanID"})})
public class PlanCombination {

    /** Written by the user ticking a checkbox. */
    public static final String SOURCE_MANUAL = "manual";
    /** Written by the cheapest-pair default seed, and later by the ranking heuristic. */
    public static final String SOURCE_HEURISTIC = "heuristic";

    private long importPlanID;
    private long exportPlanID;

    /**
     * How the pair came to be selected — {@link #SOURCE_MANUAL} /
     * {@link #SOURCE_HEURISTIC}. Never part of identity: a heuristic suggestion
     * the user then confirms is the same row, re-stamped. Nullable so an
     * unattributed row is legal.
     */
    @Nullable
    @ColumnInfo(name = "source")
    private String source;

    public PlanCombination() {}

    public PlanCombination(long importPlanID, long exportPlanID, @Nullable String source) {
        this.importPlanID = importPlanID;
        this.exportPlanID = exportPlanID;
        this.source = source;
    }

    public long getImportPlanID() {
        return importPlanID;
    }

    public void setImportPlanID(long importPlanID) {
        this.importPlanID = importPlanID;
    }

    public long getExportPlanID() {
        return exportPlanID;
    }

    public void setExportPlanID(long exportPlanID) {
        this.exportPlanID = exportPlanID;
    }

    @Nullable
    public String getSource() {
        return source;
    }

    public void setSource(@Nullable String source) {
        this.source = source;
    }
}
