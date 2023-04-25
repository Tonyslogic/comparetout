package com.tfcode.comparetout.model.scenario;

import androidx.room.ColumnInfo;

public class ScenarioBarChartData {

    @ColumnInfo(name= "Hour") public int hour = 0;
    @ColumnInfo(name= "Load") public double load = 0D;
    @ColumnInfo(name= "Feed") public double feed = 0D;
    @ColumnInfo(name= "Buy") public double buy = 0D;
    @ColumnInfo(name= "PV") public double pv = 0D;
    @ColumnInfo(name= "PV2Battery") public double pv2Battery = 0D;
    @ColumnInfo(name= "PV2Load") public double pv2Load = 0D;
    @ColumnInfo(name= "Battery2Load") public double battery2Load = 0D;
    @ColumnInfo(name= "EVSchedule") public double evSchedule = 0D;
    @ColumnInfo(name= "HWSchedule") public double hwSchedule = 0D;
    @ColumnInfo(name= "EVDivert") public double evDivert = 0D;
    @ColumnInfo(name= "HWDivert") public double hwDivert = 0D;
}
