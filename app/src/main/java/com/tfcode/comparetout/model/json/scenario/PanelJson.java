package com.tfcode.comparetout.model.json.scenario;

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

    @SerializedName("Inverter")
    public
    String inverter;

    @SerializedName("MPPT")
    public
    Integer mppt;

    @SerializedName("PanelName")
    public
    String panelName;

    @SerializedName("Optimized")
    public
    Boolean optimized;
}
