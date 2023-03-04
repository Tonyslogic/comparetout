package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2hwsystem")
public class Scenario2HWSystem {
    @PrimaryKey
    private long hwSystemID;
    private long scenarioID;

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getHwSystemID() {
        return hwSystemID;
    }

    public void setHwSystemID(long hwSystemID) {
        this.hwSystemID = hwSystemID;
    }
}