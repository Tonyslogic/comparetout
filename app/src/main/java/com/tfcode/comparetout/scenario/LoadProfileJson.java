package com.tfcode.comparetout.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class LoadProfileJson {

    @SerializedName("AnnulaUsage")
    public
    Double annualUsage;

    @SerializedName("HourlyBaseLoad")
    public
    Double hourlyBaseLoad;

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

class DOWDistribution {

    @SerializedName("Sun")
    public
    Double sun;

    @SerializedName("Mon")
    public
    Double mon;

    @SerializedName("Tue")
    public
    Double tue;

    @SerializedName("Wed")
    public
    Double wed;

    @SerializedName("Thu")
    public
    Double thu;

    @SerializedName("Fri")
    public
    Double fri;

    @SerializedName("Sat")
    public
    Double sat;

}

class MonthlyDistribution {

    @SerializedName("Oct")
    public
    Double oct;

    @SerializedName("Nov")
    public
    Double nov;

    @SerializedName("Dec")
    public
    Double dec;

    @SerializedName("Jan")
    public
    Double jan;

    @SerializedName("Feb")
    public
    Double feb;

    @SerializedName("Apr")
    public
    Double apr;

    @SerializedName("May")
    public
    Double may;

    @SerializedName("Jun")
    public
    Double jun;

    @SerializedName("Jul")
    public
    Double jul;

    @SerializedName("Aug")
    public
    Double aug;

    @SerializedName("Sep")
    public
    Double sep;

}
