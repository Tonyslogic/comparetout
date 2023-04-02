package com.tfcode.comparetout.model.scenario;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "paneldata", primaryKeys = {"panelID", "date", "minute"})
public class PanelData {

    // LOCAL TIME -- transform before writing!!

    private long panelID;
    @NonNull
    private String date = ""; // YYY-MM-DD
    @NonNull
    private String minute = ""; // HH:mm
    private double pv;
    private int mod; // 0-1435
    private int dow; // the day-of-week, from 1 (Monday) to 7 (Sunday) https://docs.oracle.com/javase/8/docs/api/java/time/LocalDate.html
    private int do2001;

    public long getPanelID() {
        return panelID;
    }

    public void setPanelID(long panelID) {
        this.panelID = panelID;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    @NonNull
    public String getMinute() {
        return minute;
    }

    public void setMinute(@NonNull String minute) {
        this.minute = minute;
    }

    public double getPv() {
        return pv;
    }

    public void setPv(double pv) {
        this.pv = pv;
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
}
