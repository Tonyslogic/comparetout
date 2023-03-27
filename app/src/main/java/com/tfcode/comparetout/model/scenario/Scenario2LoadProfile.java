package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2loadprofile")
public class Scenario2LoadProfile {
    @PrimaryKey(autoGenerate = true)
    private long s2lpID;
    private long loadProfileID;
    private long scenarioID;

    public long getS2lpID() {
        return s2lpID;
    }

    public void setS2lpID(long s2lpID) {
        this.s2lpID = s2lpID;
    }

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