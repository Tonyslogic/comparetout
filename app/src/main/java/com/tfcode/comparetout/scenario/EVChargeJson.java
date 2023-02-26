package com.tfcode.comparetout.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class EVChargeJson {

    @SerializedName("Name")
    public
    String name;

    @SerializedName("begin")
    public
    Integer begin;

    @SerializedName("end")
    public
    Integer end;

    @SerializedName("draw")
    public
    Double draw;

    @SerializedName("months")
    public
    ArrayList<Integer> months;

    @SerializedName("days")
    public
    ArrayList<Integer> days;
}
