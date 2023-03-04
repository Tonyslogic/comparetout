package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2hwdivert")
public class Scenario2HWDivert {
    @PrimaryKey
    private long hwDivertID;
    private long scenarioID;

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getHwDivertID() {
        return hwDivertID;
    }

    public void setHwDivertID(long hwDivertID) {
        this.hwDivertID = hwDivertID;
    }
}
