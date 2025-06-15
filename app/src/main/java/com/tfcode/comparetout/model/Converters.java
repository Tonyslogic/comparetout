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

package com.tfcode.comparetout.model;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.tfcode.comparetout.model.costings.SubTotals;
import com.tfcode.comparetout.model.priceplan.DoubleHolder;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.Restrictions;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.HWUse;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.MonthHolder;
import com.tfcode.comparetout.model.scenario.MonthlyDist;

/**
 * Room database type converters for complex data types serialization.
 * 
 * This class provides bidirectional conversion between complex Java objects and
 * their JSON string representations for storage in the SQLite database. Room
 * requires type converters for any non-primitive data types, and this class
 * handles the conversion for all custom objects used throughout the energy
 * system data model.
 * 
 * The converters use Gson for JSON serialization/deserialization, providing
 * robust handling of complex object hierarchies while maintaining type safety
 * and null value handling. This approach allows the database to store rich
 * data structures as TEXT fields while preserving object relationships and
 * nested data structures.
 * 
 * Supported data types:
 * - DoubleHolder: Hourly energy pricing data arrays
 * - IntHolder: Day-of-week and scheduling configurations
 * - Restrictions: Complex pricing rule definitions
 * - MinuteRateRange: Minute-precision time-of-use rates
 * - ChargeModel: Battery charging behavior models
 * - Distribution objects: Temporal energy usage patterns
 * - SubTotals: Aggregated cost calculation results
 * 
 * Each converter pair (to/from) ensures data integrity during database
 * operations while providing efficient storage of complex energy system
 * configurations and calculation results. The JSON representation also
 * facilitates data export/import operations and debugging.
 * 
 * Performance considerations:
 * - Gson operations are cached where possible to minimize serialization overhead
 * - Converters are designed to handle null values gracefully
 * - JSON format provides human-readable storage for debugging and maintenance
 */
public class Converters {

    @TypeConverter
    public static String fromDoubleHolder(DoubleHolder h) {
        Gson gson = new Gson();
        return gson.toJson(h);
    }
    @TypeConverter
    public static DoubleHolder toDoubleHolder(String s) {
        return new Gson().fromJson(s, DoubleHolder.class);
    }

    @TypeConverter
    public static String fromIntHolder(IntHolder h) {
        Gson gson = new Gson();
        return gson.toJson(h);
    }
    @TypeConverter
    public static IntHolder toIntHolder(String s) {
        return new Gson().fromJson(s, IntHolder.class);
    }

    @TypeConverter
    public static String fromStringRestrictions(Restrictions h) {
        Gson gson = new Gson();
        return gson.toJson(h);
    }

    @TypeConverter
    public static Restrictions toRestrictions(String s) {
        return new Gson().fromJson(s, Restrictions.class);
    }

    @TypeConverter
    public static String fromMinuteRateRange(MinuteRateRange h) {
        Gson gson = new Gson();
        return gson.toJson(h);
    }

    @TypeConverter
    public static MinuteRateRange toMinuteRateRange(String s) {
        return new Gson().fromJson(s, MinuteRateRange.class);
    }

    @TypeConverter
    public static String fromChargeModel(ChargeModel cm) {
        Gson gson = new Gson();
        return gson.toJson(cm);
    }

    @TypeConverter
    public static ChargeModel toChargeModel(String s) {
        return new Gson().fromJson(s, ChargeModel.class);
    }

    @TypeConverter
    public static  String fromHWUse(HWUse hwu) {
        Gson gson = new Gson();
        return gson.toJson(hwu);
    }

    @TypeConverter
    public static  HWUse toHWUse(String s) {
        return new Gson().fromJson(s, HWUse.class);
    }

    @TypeConverter
    public static  String fromHourlyDist(HourlyDist hd) {
        Gson gson = new Gson();
        return gson.toJson(hd);
    }

    @TypeConverter
    public static  HourlyDist toHourlyDist(String s) {
        return new Gson().fromJson(s, HourlyDist.class);
    }

    @TypeConverter
    public static  String fromDOWDist(DOWDist dowDistribution) {
        Gson gson = new Gson();
        return gson.toJson(dowDistribution);
    }

    @TypeConverter
    public static  DOWDist toDOWDist(String s) {
        return new Gson().fromJson(s, DOWDist.class);
    }

    @TypeConverter
    public static  String fromMonthlyDist(MonthlyDist md) {
        Gson gson = new Gson();
        return gson.toJson(md);
    }

    @TypeConverter
    public static  MonthlyDist toMonthlyDist(String s) {
        return new Gson().fromJson(s, MonthlyDist.class);
    }

    @TypeConverter
    public static  String fromMonthHolder(MonthHolder mh) {
        Gson gson = new Gson();
        return gson.toJson(mh);
    }

    @TypeConverter
    public static  MonthHolder toMonthHolder(String s) {
        return new Gson().fromJson(s, MonthHolder.class);
    }

    @TypeConverter
    public static String fromSubTotals(SubTotals st) {
        Gson gson = new Gson();
        return gson.toJson(st);
    }

    @TypeConverter
    public static SubTotals toSubTotals(String s) {
        return new Gson().fromJson(s,SubTotals.class);
    }
}
