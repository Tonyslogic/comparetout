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

package com.tfcode.comparetout.model.importers.alphaess;

import androidx.room.ColumnInfo;

public class ScheduleRIInput {
    @ColumnInfo(name= "month") public int month;
    @ColumnInfo(name= "day_of_week") public int dow;
    @ColumnInfo(name= "hour") public int hour;
    @ColumnInfo(name= "cbat") public double cbat;
    @ColumnInfo(name= "CFG") public double cfg;
}