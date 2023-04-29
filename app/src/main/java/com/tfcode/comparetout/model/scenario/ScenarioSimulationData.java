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

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "scenariosimulationdata", primaryKeys = {"scenarioID", "date", "minuteOfDay"})
public class ScenarioSimulationData {

    private long scenarioID;
    @NonNull
    private String date = "2001-01-01";
    private int minuteOfDay;
    private int dayOfWeek;
    private int dayOf2001;
    private double load;
    private double Feed;
    private double Buy;
    private double SOC;
    private double directEVcharge;
    private double waterTemp;
    private double kWHDivToWater;
    private double kWHDivToEV;
    private double pvToCharge;
    private double pvToLoad;
    private double batToLoad;
    private double pv;
    private double immersionLoad;

    public double getLoad() {
        return load;
    }

    public void setLoad(double load) {
        this.load = load;
    }

    public int getDayOf2001() {
        return dayOf2001;
    }

    public void setDayOf2001(int dayOf2001) {
        this.dayOf2001 = dayOf2001;
    }

    public long getScenarioID() {
        return scenarioID;
    }

    public void setScenarioID(long scenarioID) {
        this.scenarioID = scenarioID;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    public void setDate(@NonNull String date) {
        this.date = date;
    }

    public int getMinuteOfDay() {
        return minuteOfDay;
    }

    public void setMinuteOfDay(int minuteOfDay) {
        this.minuteOfDay = minuteOfDay;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public double getFeed() {
        return Feed;
    }

    public void setFeed(double feed) {
        Feed = feed;
    }

    public double getBuy() {
        return Buy;
    }

    public void setBuy(double buy) {
        Buy = buy;
    }

    public double getSOC() {
        return SOC;
    }

    public void setSOC(double SOC) {
        this.SOC = SOC;
    }

    public double getDirectEVcharge() {
        return directEVcharge;
    }

    public void setDirectEVcharge(double directEVcharge) {
        this.directEVcharge = directEVcharge;
    }

    public double getWaterTemp() {
        return waterTemp;
    }

    public void setWaterTemp(double waterTemp) {
        this.waterTemp = waterTemp;
    }

    public double getKWHDivToWater() {
        return kWHDivToWater;
    }

    public void setKWHDivToWater(double kWHDivToWater) {
        this.kWHDivToWater = kWHDivToWater;
    }

    public double getKWHDivToEV() {
        return kWHDivToEV;
    }

    public void setKWHDivToEV(double kWHDivToEV) {
        this.kWHDivToEV = kWHDivToEV;
    }

    public double getPvToCharge() {
        return pvToCharge;
    }

    public void setPvToCharge(double pvToCharge) {
        this.pvToCharge = pvToCharge;
    }

    public double getPvToLoad() {
        return pvToLoad;
    }

    public void setPvToLoad(double pvToLoad) {
        this.pvToLoad = pvToLoad;
    }

    public double getBatToLoad() {
        return batToLoad;
    }

    public void setBatToLoad(double batToLoad) {
        this.batToLoad = batToLoad;
    }

    public double getPv() {
        return pv;
    }

    public void setPv(double pv) {
        this.pv = pv;
    }

    public double getImmersionLoad() {
        return immersionLoad;
    }

    public void setImmersionLoad(double immersionLoad) {
        this.immersionLoad = immersionLoad;
    }
}

