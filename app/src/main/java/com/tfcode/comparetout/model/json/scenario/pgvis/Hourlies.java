package com.tfcode.comparetout.model.json.scenario.pgvis;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class Hourlies {
    @SerializedName("hourly")
    public ArrayList<Hourly> hourlies;
}
