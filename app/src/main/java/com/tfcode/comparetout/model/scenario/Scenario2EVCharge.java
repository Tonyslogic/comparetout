package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2evcharge")
public class Scenario2EVCharge {
    @PrimaryKey(autoGenerate = true)
    private long s2evcID;
    private long evChargeID;
    private long scenarioID;

    public long getS2evcID() {
        return s2evcID;
    }

    public void setS2evcID(long s2evcID) {
        this.s2evcID = s2evcID;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getEvChargeID() {
        return evChargeID;
    }

    public void setEvChargeID(long evChargeID) {
        this.evChargeID = evChargeID;
    }
}
