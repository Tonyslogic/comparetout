package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2battery")
public class Scenario2Battery {
    @PrimaryKey(autoGenerate = true)
    private long s2bID;
    private long batteryID;
    private long scenarioID;

    public long getS2bID() {
        return s2bID;
    }

    public void setS2bID(long s2bID) {
        this.s2bID = s2bID;
    }

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
