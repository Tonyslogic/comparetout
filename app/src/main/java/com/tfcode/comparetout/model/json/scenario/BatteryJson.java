package com.tfcode.comparetout.model.json.scenario;

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
    ChargeModelJson chargeModel;

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

