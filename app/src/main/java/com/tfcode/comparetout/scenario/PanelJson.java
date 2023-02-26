package com.tfcode.comparetout.scenario;

import com.google.gson.annotations.SerializedName;

public class PanelJson {

    @SerializedName("PanelCount")
    public
    Integer panelCount;

    @SerializedName("PanelkWp")
    public
    Integer panelkWp;

    @SerializedName("Azimuth")
    public
    Integer azimuth;

    @SerializedName("Slope")
    public
    Integer slope;

    @SerializedName("Latitude")
    public
    Double latitude;

    @SerializedName("Longitude")
    public
    Double longitude;

    @SerializedName("MPPT")
    public
    MPPT mppt;
}

class MPPT {

    @SerializedName("Inverter")
    public
    String inverter;

    @SerializedName("MPPT")
    public
    Integer mppt;
}
