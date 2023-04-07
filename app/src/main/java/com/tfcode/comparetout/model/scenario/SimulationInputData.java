package com.tfcode.comparetout.model.scenario;

import androidx.room.ColumnInfo;

public class SimulationInputData {
    @ColumnInfo(name= "date") public String date;
    @ColumnInfo(name= "minute") public String minute;
    @ColumnInfo(name= "load") public double load = 0D;
    @ColumnInfo(name= "mod") public int mod = 0;
    @ColumnInfo(name= "dow") public int dow = 0;
    @ColumnInfo(name= "do2001") public int do2001 = 0;
    @ColumnInfo(name= "TPV") public double tpv = 0D;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getMinute() {
        return minute;
    }

    public void setMinute(String minute) {
        this.minute = minute;
    }

    public double getLoad() {
        return load;
    }

    public void setLoad(double load) {
        this.load = load;
    }

    public int getMod() {
        return mod;
    }

    public void setMod(int mod) {
        this.mod = mod;
    }

    public int getDow() {
        return dow;
    }

    public void setDow(int dow) {
        this.dow = dow;
    }

    public int getDo2001() {
        return do2001;
    }

    public void setDo2001(int do2001) {
        this.do2001 = do2001;
    }

    public double getTpv() {
        return tpv;
    }

    public void setTpv(double tpv) {
        this.tpv = tpv;
    }
}
