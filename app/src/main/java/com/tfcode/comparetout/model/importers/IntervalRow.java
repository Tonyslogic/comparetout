/*
 * Copyright (c) 2023-2024. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tfcode.comparetout.model.importers;

import androidx.room.ColumnInfo;

public class IntervalRow {
    // SELECT PV, LOAD, FEED, BUY, INTERVAL
    @ColumnInfo(name= "PV") public double pv = 0.0;
    @ColumnInfo(name= "LOAD") public double load = 0.0;
    @ColumnInfo(name= "FEED") public double feed = 0.0;
    @ColumnInfo(name= "BUY") public double buy = 0.0;

    @ColumnInfo(name= "PV2BAT") public double pv2bat = 0.0;
    @ColumnInfo(name= "PV2LOAD") public double pv2load = 0.0;
    @ColumnInfo(name= "BAT2LOAD") public double bat2load = 0.0;
    @ColumnInfo(name= "GRID2BAT") public double grid2bat = 0.0;
    @ColumnInfo(name= "EVSCHEDULE") public double evSchedule = 0.0;
    @ColumnInfo(name= "EVDIVERT") public double evDivert = 0.0;
    @ColumnInfo(name= "HWSCHEDULE") public double hwSchedule = 0.0;
    @ColumnInfo(name= "HWDIVERT") public double hwDivert = 0.0;
    @ColumnInfo(name= "BAT2GRID") public double bat2grid = 0.0;
    @ColumnInfo(name= "BAT_CHARGE") public double batCharge = 0.0;
    @ColumnInfo(name= "BAT_DISCHARGE") public double batDischarge = 0.0;

    @ColumnInfo(name= "INTERVAL") public String interval = "";
}
