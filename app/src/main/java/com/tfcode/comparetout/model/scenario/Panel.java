/*
 * Copyright (c) 2023. Tony Finnerty
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

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
    private String inverter = "AlphaESS";
    private int mppt = 1;
    private String panelName = "<Name>";
    private int connectionMode = PARALLEL;

    public static final int PARALLEL = 0;
    public static final int OPTIMIZED = 1;

    public int getConnectionMode() {
        return connectionMode;
    }

    public void setConnectionMode(int connectionMode) {
        this.connectionMode = connectionMode;
    }

    public String getPanelName() {
        return panelName;
    }

    public void setPanelName(String panelName) {
        this.panelName = panelName;
    }

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
