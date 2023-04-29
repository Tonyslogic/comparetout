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

@Entity(tableName = "loadprofile")
public class LoadProfile {

    @PrimaryKey(autoGenerate = true)
    private long loadProfileIndex;

    private double annualUsage = 6200d;
    private double hourlyBaseLoad = 0.3;
    private double gridImportMax = 15.0;
    private String distributionSource = "Custom";
    private double gridExportMax = 5.0;
    private HourlyDist hourlyDist = new HourlyDist();
    private DOWDist dowDist = new DOWDist();
    private MonthlyDist monthlyDist = new MonthlyDist();

    public String getDistributionSource() {return distributionSource;}

    public void setDistributionSource(String distributionSource)
        {this.distributionSource = distributionSource;}

    public double getGridImportMax() {
        return gridImportMax;
    }

    public void setGridImportMax(double gridImportMax) {
        this.gridImportMax = gridImportMax;
    }

    public double getGridExportMax() {
        return gridExportMax;
    }

    public void setGridExportMax(double gridExportMax) {
        this.gridExportMax = gridExportMax;
    }

    public long getLoadProfileIndex() {
        return loadProfileIndex;
    }

    public void setLoadProfileIndex(long loadProfileIndex) {
        this.loadProfileIndex = loadProfileIndex;
    }

    public double getAnnualUsage() {
        return annualUsage;
    }

    public void setAnnualUsage(double annualUsage) {
        this.annualUsage = annualUsage;
    }

    public double getHourlyBaseLoad() {
        return hourlyBaseLoad;
    }

    public void setHourlyBaseLoad(double hourlyBaseLoad) {
        this.hourlyBaseLoad = hourlyBaseLoad;
    }

    public HourlyDist getHourlyDist() {
        return hourlyDist;
    }

    public void setHourlyDist(HourlyDist hourlyDist) {
        this.hourlyDist = hourlyDist;
    }

    public DOWDist getDowDist() {
        return dowDist;
    }

    public void setDowDist(DOWDist dowDist) {
        this.dowDist = dowDist;
    }

    public MonthlyDist getMonthlyDist() {
        return monthlyDist;
    }

    public void setMonthlyDist(MonthlyDist monthlyDist) {
        this.monthlyDist = monthlyDist;
    }
}
