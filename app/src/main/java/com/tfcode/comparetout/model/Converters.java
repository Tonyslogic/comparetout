package com.tfcode.comparetout.model;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.tfcode.comparetout.model.costings.SubTotals;
import com.tfcode.comparetout.model.priceplan.DoubleHolder;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.HWUse;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.MonthHolder;
import com.tfcode.comparetout.model.scenario.MonthlyDist;

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
