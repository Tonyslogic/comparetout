package com.tfcode.comparetout.model.scenario;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "scenarios")
public class Scenario {

    @PrimaryKey(autoGenerate = true)
    private long id ;

    @NonNull
    private String scenarioName = "<SCENARIO>";

    private boolean hasInverters = false;
    private boolean hasBatteries = false;
    private boolean hasPanels = false;
    private boolean hasIRData = false;
    private boolean hasHWSystem = false;

    public boolean isHasInverters() {
        return hasInverters;
    }

    public void setHasInverters(boolean hasInverters) {
        this.hasInverters = hasInverters;
    }

    public boolean isHasBatteries() {
        return hasBatteries;
    }

    public void setHasBatteries(boolean hasBatteries) {
        this.hasBatteries = hasBatteries;
    }

    public boolean isHasPanels() {
        return hasPanels;
    }

    public void setHasPanels(boolean hasPanels) {
        this.hasPanels = hasPanels;
    }

    public boolean isHasIRData() {
        return hasIRData;
    }

    public void setHasIRData(boolean hasIRData) {
        this.hasIRData = hasIRData;
    }

    public boolean isHasHWSystem() {
        return hasHWSystem;
    }

    public void setHasHWSystem(boolean hasHWSystem) {
        this.hasHWSystem = hasHWSystem;
    }

    public boolean isHasLoadProfiles() {
        return hasLoadProfiles;
    }

    public void setHasLoadProfiles(boolean hasLoadProfiles) {
        this.hasLoadProfiles = hasLoadProfiles;
    }

    public boolean isHasLoadShifts() {
        return hasLoadShifts;
    }

    public void setHasLoadShifts(boolean hasLoadShifts) {
        this.hasLoadShifts = hasLoadShifts;
    }

    public boolean isHasEVCharges() {
        return hasEVCharges;
    }

    public void setHasEVCharges(boolean hasEVCharges) {
        this.hasEVCharges = hasEVCharges;
    }

    public boolean isHasHWSchedules() {
        return hasHWSchedules;
    }

    public void setHasHWSchedules(boolean hasHWSchedules) {
        this.hasHWSchedules = hasHWSchedules;
    }

    public boolean isHasHWDivert() {
        return hasHWDivert;
    }

    public void setHasHWDivert(boolean hasHWDivert) {
        this.hasHWDivert = hasHWDivert;
    }

    public boolean isHasEVDivert() {
        return hasEVDivert;
    }

    public void setHasEVDivert(boolean hasEVDivert) {
        this.hasEVDivert = hasEVDivert;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    private boolean hasLoadProfiles = false;
    private boolean hasLoadShifts = false;
    private boolean hasEVCharges = false;
    private boolean hasHWSchedules = false;
    private boolean hasHWDivert = false;
    private boolean hasEVDivert = false;
    private boolean isActive = false;


    @Override
    public boolean equals(@Nullable Object object)
    {
        if(object == null) return false;
        if(object == this) return true;

        if(object instanceof Scenario)
        {
            return scenarioName.equals(((Scenario) object).getScenarioName());
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return scenarioName.hashCode();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @NonNull
    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(@NonNull String scenarioName) {
        this.scenarioName = scenarioName;
    }
}
