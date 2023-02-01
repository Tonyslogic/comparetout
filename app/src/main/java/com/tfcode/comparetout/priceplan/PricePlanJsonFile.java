package com.tfcode.comparetout.priceplan;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class PricePlanJsonFile {

    @SerializedName("Supplier")
    public
    String supplier;

    @SerializedName("Plan")
    public
    String plan;

    @SerializedName("Feed")
    public
    Double feed;

    @SerializedName("Standing charges")
    public
    Double standingCharges;

    @SerializedName("Bonus cash")
    public
    Double bonus;

    @SerializedName("Rates")
    public
    ArrayList<DayRateJson> rates;

    @SerializedName("Active")
    public
    Boolean active;

    @SerializedName("LastUpdate")
    public
    String lastUpdate;

    @SerializedName("Reference")
    public
    String reference;
}

