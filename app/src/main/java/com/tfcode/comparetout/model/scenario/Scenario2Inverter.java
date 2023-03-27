package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2inverter")
public class Scenario2Inverter {
    @PrimaryKey(autoGenerate = true)
    private long s2iID;
    private long inverterID;
    private long scenarioID;

    public long getS2iID() {
        return s2iID;
    }

    public void setS2iID(long s2iID) {
        this.s2iID = s2iID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getInverterID() {
        return inverterID;
    }

    public void setInverterID(long inverterID) {
        this.inverterID = inverterID;
    }
}
