package com.tfcode.comparetout.model.json.scenario;

import com.google.gson.annotations.SerializedName;

public class InverterJson {

    @SerializedName("Name")
    public
    String name;

    @SerializedName("MinExcess")
    public
    Double minExcess;

    @SerializedName("MaxInverterLoad")
    public
    Double maxInverterLoad;

    @SerializedName("MPPTCount")
    public
    Integer mPPTCount;

    @SerializedName("AC2DCLoss")
    public
    Integer ac2dcLoss;

    @SerializedName("DC2ACLoss")
    public
    Integer dc2acLoss;

    @SerializedName("DC2DCLoss")
    public
    Integer dc2dcLoss;
}
