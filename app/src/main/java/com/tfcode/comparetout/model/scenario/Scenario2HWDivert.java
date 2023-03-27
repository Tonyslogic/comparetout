package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2hwdivert")
public class Scenario2HWDivert {
    @PrimaryKey(autoGenerate = true)
    private long s2hwdID;
    private long hwDivertID;
    private long scenarioID;

    public long getS2hwdID() {
        return s2hwdID;
    }

    public void setS2hwdID(long s2hwdID) {
        this.s2hwdID = s2hwdID;
    }

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
