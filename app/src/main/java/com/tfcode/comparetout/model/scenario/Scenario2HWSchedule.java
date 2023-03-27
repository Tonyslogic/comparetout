package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenario2hwschedule")
public class Scenario2HWSchedule {
    @PrimaryKey(autoGenerate = true)
    private long s2hwsID;
    private long hwScheduleID;
    private long scenarioID;

    public long getS2hwsID() {
        return s2hwsID;
    }

    public void setS2hwsID(long s2hwsID) {
        this.s2hwsID = s2hwsID;
    }

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
