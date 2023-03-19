package com.tfcode.comparetout.model.costings;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "costings",
        primaryKeys = {"scenarioID", "pricePlanID"},
        indices = {@Index(value = {"scenarioID","pricePlanID"}, unique = true) })
public class Costings {

    private long scenarioID;
    private long pricePlanID;
    private double buy;
    private double sell;
    private SubTotals subTotals;
    private String scenarioName;
    private String fullPlanName;
    private double nett;

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

    public double getNett() {
        return nett;
    }

    public void setNett(double nett) {
        this.nett = nett;
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + scenarioName + ", " + fullPlanName + ", " + nett + ", " + buy + ", " + sell + "]";
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
