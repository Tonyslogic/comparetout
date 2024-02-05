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
    @ColumnInfo(name= "PV") public double pv;
    @ColumnInfo(name= "LOAD") public double load;
    @ColumnInfo(name= "FEED") public double feed;
    @ColumnInfo(name= "BUY") public double buy;
    @ColumnInfo(name= "INTERVAL") public String interval;
}
