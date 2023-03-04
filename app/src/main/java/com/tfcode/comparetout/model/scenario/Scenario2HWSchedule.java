package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2hwschedule")
public class Scenario2HWSchedule {
    @PrimaryKey
    private long hwScheduleID;
    private long scenarioID;

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    public long getHwScheduleID() {
        return hwScheduleID;
    }

    public void setHwScheduleID(long hwScheduleID) {
        this.hwScheduleID = hwScheduleID;
    }
}
