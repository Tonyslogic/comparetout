package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2evcharge")
public class Scenario2EVCharge {
    @PrimaryKey
    private long evChargeID;
    private long scenarioID;

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
