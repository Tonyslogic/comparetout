package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2battery")
public class Scenario2Battery {
    @PrimaryKey
    private long batteryID;
    private long scenarioID;

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getBatteryID() {
        return batteryID;
    }

    public void setBatteryID(long batteryID) {
        this.batteryID = batteryID;
    }
}
