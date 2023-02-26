package com.tfcode.comparetout.scenario;

import com.google.gson.annotations.SerializedName;

public class BatteryJson {

    @SerializedName("Battery Size")
    public
    Double batterySize;

    @SerializedName("Discharge stop")
    public
    Double dischargeStop;

    @SerializedName("ChargeModel")
    public
    ChargeModel chargeModel;

    @SerializedName("Max discharge")
    public
    Double maxDischarge;

    @SerializedName("Max charge")
    public
    Double maxCharge;

    @SerializedName("StorageLoss")
    public
    Double storageLoss;

    @SerializedName("Inverter")
    public
    String inverter;
}

class ChargeModel {

    @SerializedName("0")
    public
    Integer percent0;

    @SerializedName("12")
    public
    Integer percent12;

    @SerializedName("90")
    public
    Integer percent90;

    @SerializedName("100")
    public
    Integer percent100;
}
