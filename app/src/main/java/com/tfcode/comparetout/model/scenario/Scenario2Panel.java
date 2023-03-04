package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2panel")
public class Scenario2Panel {
    @PrimaryKey
    private long panelID;
    private long scenarioID;

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
