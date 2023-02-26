package com.tfcode.comparetout.scenario;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

public class ScenarioJsonFile {

    @SerializedName("Name")
    public
    String name;

    @SerializedName("Inverters")
    public
    ArrayList<InverterJson> inverters;

    @SerializedName("Batteries")
    public
    ArrayList<BatteryJson> batteries;

    @SerializedName("Panels")
    public
    ArrayList<PanelJson> panels;

    @SerializedName("HWSystem")
    public
    HWSystemJson hwSystem;

    @SerializedName("LoadProfile")
    public
    LoadProfileJson loadProfile;

    @SerializedName("LoadShift")
    public
    ArrayList<LoadShiftJson> loadShifts;

    @SerializedName("EVCharge")
    public
    ArrayList<EVChargeJson> evCharges;

    @SerializedName("HWSchedule")
    public
    ArrayList<HWScheduleJson> hwSchedules;

    @SerializedName("HWDivert")
    public
    HWDivert hwDivert;

    @SerializedName("EVDivert")
    public
    EVDivertJson evDivert;
}

class HWDivert {

    @SerializedName("active")
    public
    Boolean active;
}
