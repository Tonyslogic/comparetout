package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2evdivert")
public class Scenario2EVDivert {
    @PrimaryKey
    private long evDivertID;
    private long scenarioID;

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getEvDivertID() {
        return evDivertID;
    }

    public void setEvDivertID(long evDivertID) {
        this.evDivertID = evDivertID;
    }
}
