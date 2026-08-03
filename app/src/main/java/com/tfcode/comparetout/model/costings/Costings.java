/*
 * Copyright (c) 2023. Tony Finnerty
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

package com.tfcode.comparetout.model.costings;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "costings",
        primaryKeys = {"scenarioID", "pricePlanID", "exportPlanID"},
        indices = {@Index(value = {"scenarioID","pricePlanID","exportPlanID"}, unique = true) })
public class Costings {

    /**
     * {@link #exportPlanID} value meaning "this import plan's own export side" —
     * its SELL DayRates, or failing those the scalar {@link
     * com.tfcode.comparetout.model.priceplan.PricePlan#getFeed()}. Every pre-v17
     * row migrates to this, so bundled-export regions are unaffected.
     */
    public static final long BUNDLED_EXPORT = 0L;

    private long scenarioID;
    private long pricePlanID;
    /**
     * The separate export contract this costing was priced against, or
     * {@link #BUNDLED_EXPORT}. Part of the primary key (v17): one import plan
     * yields one row per export plan it is paired with.
     */
    @ColumnInfo(name = "exportPlanID", defaultValue = "0")
    private long exportPlanID = BUNDLED_EXPORT;
    private double buy;
    private double sell;
    private SubTotals subTotals;
    private String scenarioName;
    private String fullPlanName;
    private double net;

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public String getFullPlanName() {
        return fullPlanName;
    }

    public void setFullPlanName(String fullPlanName) {
        this.fullPlanName = fullPlanName;
    }

    public double getNet() {
        return net;
    }

    public void setNet(double net) {
        this.net = net;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + scenarioName + ", " + fullPlanName + ", " + net + ", " + buy + ", " + sell + "]";
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getPricePlanID() {
        return pricePlanID;
    }

    public void setPricePlanID(long pricePlanID) {
        this.pricePlanID = pricePlanID;
    }

    public long getExportPlanID() {
        return exportPlanID;
    }

    public void setExportPlanID(long exportPlanID) {
        this.exportPlanID = exportPlanID;
    }

    /** True for the legacy/bundled row — export priced from the import plan itself. */
    public boolean isBundledExport() {
        return exportPlanID == BUNDLED_EXPORT;
    }

    public double getBuy() {
        return buy;
    }

    public void setBuy(double buy) {
        this.buy = buy;
    }

    public double getSell() {
        return sell;
    }

    public void setSell(double sell) {
        this.sell = sell;
    }

    public SubTotals getSubTotals() {
        return subTotals;
    }

    public void setSubTotals(SubTotals subTotals) {
        this.subTotals = subTotals;
    }
}
