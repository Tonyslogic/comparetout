package com.tfcode.comparetout.model.scenario;

import androidx.room.ColumnInfo;

public class ScenarioLineGraphData {

    @ColumnInfo(name= "minuteOfDay") public int mod = 0;
    @ColumnInfo(name= "SOC") public double soc = 0D;
    @ColumnInfo(name= "waterTemp") public double waterTemperature = 0D;

}
