package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2loadshift")
public class Scenario2LoadShift {
    @PrimaryKey(autoGenerate = true)
    private long s2lsID;
    private long loadShiftID;
    private long scenarioID;

    public long getS2lsID() {
        return s2lsID;
    }

    public void setS2lsID(long s2lsID) {
        this.s2lsID = s2lsID;
    }

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