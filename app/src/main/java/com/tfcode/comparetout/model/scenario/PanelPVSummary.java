package com.tfcode.comparetout.model.scenario;

import androidx.room.ColumnInfo;

public class PanelPVSummary {

    // SELECT panelID, substr(Date, 6,2) AS Month, SUM(pv) AS tot FROM paneldata GROUP BY panelID, Month
    @ColumnInfo(name= "panelID") public long panelID = 0L;
    @ColumnInfo(name= "Month") public String month;
    @ColumnInfo(name= "tot") public double tot;
}
