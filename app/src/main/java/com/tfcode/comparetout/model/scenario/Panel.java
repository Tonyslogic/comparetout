package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "panels")
public class Panel {

    @PrimaryKey(autoGenerate = true)
    private long panelIndex;

    private int panelCount = 7;
    private int panelkWp = 325;
    private int azimuth = 136;
    private int slope = 24;
    private double latitude = 53.490;
    private double longitude = -10.015;
    private String inverter = "AplhaESS";
    private int mppt = 1;

    public long getPanelIndex() {
        return panelIndex;
    }

    public void setPanelIndex(long panelIndex) {
        this.panelIndex = panelIndex;
    }

    public int getPanelCount() {
        return panelCount;
    }

    public void setPanelCount(int panelCount) {
        this.panelCount = panelCount;
    }

    public int getPanelkWp() {
        return panelkWp;
    }

    public void setPanelkWp(int panelkWp) {
        this.panelkWp = panelkWp;
    }

    public int getAzimuth() {
        return azimuth;
    }

    public void setAzimuth(int azimuth) {
        this.azimuth = azimuth;
    }

    public int getSlope() {
        return slope;
    }

    public void setSlope(int slope) {
        this.slope = slope;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getInverter() {
        return inverter;
    }

    public void setInverter(String inverter) {
        this.inverter = inverter;
    }

    public int getMppt() {
        return mppt;
    }

    public void setMppt(int mppt) {
        this.mppt = mppt;
    }
}
