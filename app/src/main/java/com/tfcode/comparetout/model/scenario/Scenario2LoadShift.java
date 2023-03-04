package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2loadshift")
public class Scenario2LoadShift {
    @PrimaryKey
    private long loadShiftID;
    private long scenarioID;

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getLoadShiftID() {
        return loadShiftID;
    }

    public void setLoadShiftID(long loadShiftID) {
        this.loadShiftID = loadShiftID;
    }
}