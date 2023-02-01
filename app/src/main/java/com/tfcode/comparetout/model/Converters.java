package com.tfcode.comparetout.model;

import androidx.room.TypeConverter;

import com.google.gson.Gson;

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
}
