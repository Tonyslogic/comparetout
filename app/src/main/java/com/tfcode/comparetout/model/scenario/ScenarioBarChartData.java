/*
 * Copyright (c) 2023. Tony Finnerty
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
    @ColumnInfo(name= "Grid2Battery") public double grid2Battery = 0D;
    @ColumnInfo(name= "Battery2Load") public double battery2Load = 0D;
    @ColumnInfo(name= "EVSchedule") public double evSchedule = 0D;
    @ColumnInfo(name= "HWSchedule") public double hwSchedule = 0D;
    @ColumnInfo(name= "EVDivert") public double evDivert = 0D;
    @ColumnInfo(name= "HWDivert") public double hwDivert = 0D;
    @ColumnInfo(name= "Bat2Grid") public double bat2grid = 0D;
}
