package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2evdivert")
public class Scenario2EVDivert {
    @PrimaryKey(autoGenerate = true)
    private long s2evdID;
    private long evDivertID;
    private long scenarioID;

    public long getS2evdID() {
        return s2evdID;
    }

    public void setS2evdID(long s2evdID) {
        this.s2evdID = s2evdID;
    }

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
