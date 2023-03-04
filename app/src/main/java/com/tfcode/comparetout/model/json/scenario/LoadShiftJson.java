package com.tfcode.comparetout.model.json.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class LoadShiftJson {

    @SerializedName("Name")
    public
    String name;

    @SerializedName("begin")
    public
    Integer begin;

    @SerializedName("end")
    public
    Integer end;

    @SerializedName("stop at")
    public
    Double stopAt;

    @SerializedName("months")
    public
    ArrayList<Integer> months;

    @SerializedName("days")
    public
    ArrayList<Integer> days;
}
