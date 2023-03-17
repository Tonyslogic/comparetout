package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "loadprofile")
public class LoadProfile {

    @PrimaryKey(autoGenerate = true)
    private long id ;

    private double annualUsage = 6200d;
    private double hourlyBaseLoad = 0.3;
    private double gridImportMax = 15.0;
    private String distributionSource = "Custom";
    private double gridExportMax = 6.0;
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

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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
