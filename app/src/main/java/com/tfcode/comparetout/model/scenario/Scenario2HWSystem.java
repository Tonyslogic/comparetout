package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2hwsystem")
public class Scenario2HWSystem {
    @PrimaryKey(autoGenerate = true)
    private long s2hwsysID;
    private long hwSystemID;
    private long scenarioID;

    public long getS2hwsysID() {
        return s2hwsysID;
    }

    public void setS2hwsysID(long s2hwsysID) {
        this.s2hwsysID = s2hwsysID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getHwSystemID() {
        return hwSystemID;
    }

    public void setHwSystemID(long hwSystemID) {
        this.hwSystemID = hwSystemID;
    }
}