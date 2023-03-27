package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2panel")
public class Scenario2Panel {
    @PrimaryKey(autoGenerate = true)
    private long s2pID;
    private long panelID;
    private long scenarioID;

    public long getS2pID() {
        return s2pID;
    }

    public void setS2pID(long s2pID) {
        this.s2pID = s2pID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getPanelID() {
        return panelID;
    }

    public void setPanelID(long panelID) {
        this.panelID = panelID;
    }
}
