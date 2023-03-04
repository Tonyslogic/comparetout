package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2loadprofile")
public class Scenario2LoadProfile {
    @PrimaryKey
    private long loadProfileID;
    private long scenarioID;

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getLoadProfileID() {
        return loadProfileID;
    }

    public void setLoadProfileID(long loadProfileID) {
        this.loadProfileID = loadProfileID;
    }
}