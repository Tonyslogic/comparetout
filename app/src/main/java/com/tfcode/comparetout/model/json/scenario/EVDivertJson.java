package com.tfcode.comparetout.model.json.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class EVDivertJson {

    @SerializedName("Name")
    public
    String name;

    @SerializedName("active")
    public
    Boolean active;

    @SerializedName("ev1st")
    public
    Boolean ev1st;

    @SerializedName("begin")
    public
    Integer begin;

    @SerializedName("end")
    public
    Integer end;

    @SerializedName("dailyMax")
    public
    Double dailyMax;

    @SerializedName("months")
    public
    ArrayList<Integer> months;

    @SerializedName("days")
    public
    ArrayList<Integer> days;
}
