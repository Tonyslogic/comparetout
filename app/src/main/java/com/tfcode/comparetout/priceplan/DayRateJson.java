package com.tfcode.comparetout.priceplan;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class DayRateJson {
    @SerializedName("Days")
    public
    ArrayList<Integer> days;

    @SerializedName("Hours")
    public
    ArrayList<Double> hours;

    @SerializedName("startDate")
    public
    String startDate;

    @SerializedName("endDate")
    public
    String endDate;

}
