package com.tfcode.comparetout.model.scenario;

import androidx.room.ColumnInfo;

public class SimKPIs {

    // Self-consumption (generated - sold) / generated
    @ColumnInfo(name= "gen") public double generated = 0D;
    @ColumnInfo(name= "sold") public double sold = 0D;

    // Self-sufficiency (totalLoad - bought) / totalLoad
    @ColumnInfo(name= "load") public double totalLoad = 0D;
    @ColumnInfo(name= "bought") public double bought = 0D;

}
