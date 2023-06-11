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

public class SimKPIs {

    // Self-consumption (generated - sold) / generated
    @ColumnInfo(name= "gen") public double generated = 0D;
    @ColumnInfo(name= "sold") public double sold = 0D;

    // Self-sufficiency (totalLoad - bought) / totalLoad
    @ColumnInfo(name= "load") public double totalLoad = 0D;
    @ColumnInfo(name= "bought") public double bought = 0D;

    // PV distribution
    @ColumnInfo(name="evDiv") public double evDiv = 0D;
    @ColumnInfo(name="h2oDiv") public double h2oDiv = 0D;
    @ColumnInfo(name="pvToLoad") public double pvToLoad = 0D;
    @ColumnInfo(name="pvToCharge") public double pvToCharge = 0D;

    // Load breakdown
    @ColumnInfo(name="house") public double house = 0D;
    @ColumnInfo(name="h20") public double h20 = 0D;
    @ColumnInfo(name="EV") public double ev = 0D;

}
