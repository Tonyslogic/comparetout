package com.tfcode.comparetout.model.json.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class LoadProfileJson {

    @SerializedName("AnnualUsage")
    public
    Double annualUsage;

    @SerializedName("HourlyBaseLoad")
    public
    Double hourlyBaseLoad;

    @SerializedName("GridImportMax")
    public
    Double gridImportMax;

    @SerializedName("GridExportMax")
    public
    Double gridExportMax;

    @SerializedName("HourlyDistribution")
    public
    ArrayList<Double> hourlyDistribution;

    @SerializedName("DayOfWeekDistribution")
    public
    DOWDistribution dayOfWeekDistribution;

    @SerializedName("MonthlyDistribution")
    public
    MonthlyDistribution monthlyDistribution;
}

