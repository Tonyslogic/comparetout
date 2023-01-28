package com.tfcode.comparetout.dbmodel;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class Converters {

    @TypeConverter
    public static String fromDoubleHolder(DoubleHolder h) {
        Gson gson = new Gson();
        String json = gson.toJson(h);
        return json;
    }
    @TypeConverter
    public static DoubleHolder toDoubleHolder(String s) {
        return new Gson().fromJson(s, DoubleHolder.class);
    }

    @TypeConverter
    public static String fromIntHolder(IntHolder h) {
        Gson gson = new Gson();
        String json = gson.toJson(h);
        return json;
    }
    @TypeConverter
    public static IntHolder toIntHolder(String s) {
        return new Gson().fromJson(s, IntHolder.class);
    }
}
