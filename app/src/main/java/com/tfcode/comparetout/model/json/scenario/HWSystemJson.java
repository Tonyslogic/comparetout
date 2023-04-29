package com.tfcode.comparetout.model.json.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class HWSystemJson {

    @SerializedName("HWCapacity")
    public
    Integer hwCapacity;

    @SerializedName("HWUsage")
    public
    Integer hwUsage;

    @SerializedName("HWIntake")
    public
    Integer hwIntake;

    @SerializedName("HWTarget")
    public
    Integer hwTarget;

    @SerializedName("HWLoss")
    public
    Integer hwLoss;

    @SerializedName("HWRate")
    public
    Double hwRate;

    @SerializedName("HWUse")
    public
    ArrayList<ArrayList<Double>> hwUse;
}
