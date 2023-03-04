package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2inverter")
public class Scenario2Inverter {
    @PrimaryKey
    private long inverterID;
    private long scenarioID;

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
