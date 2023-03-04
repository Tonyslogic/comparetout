package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "hwsystem")
public class HWSystem {

    @PrimaryKey(autoGenerate = true)
    private long id ;

    private int hwCapacity = 165;
    private int hwUsage = 200;
    private int hwIntake = 15;
    private int hwTarget = 75;
    private int hwLoss = 8;
    private double hwRate = 2.5;
    private HWUse hwUse = new HWUse();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getHwCapacity() {
        return hwCapacity;
    }

    public void setHwCapacity(int hwCapacity) {
        this.hwCapacity = hwCapacity;
    }

    public int getHwUsage() {
        return hwUsage;
    }

    public void setHwUsage(int hwUsage) {
        this.hwUsage = hwUsage;
    }

    public int getHwIntake() {
        return hwIntake;
    }

    public void setHwIntake(int hwIntake) {
        this.hwIntake = hwIntake;
    }

    public int getHwTarget() {
        return hwTarget;
    }

    public void setHwTarget(int hwTarget) {
        this.hwTarget = hwTarget;
    }

    public int getHwLoss() {
        return hwLoss;
    }

    public void setHwLoss(int hwLoss) {
        this.hwLoss = hwLoss;
    }

    public double getHwRate() {
        return hwRate;
    }

    public void setHwRate(double hwRate) {
        this.hwRate = hwRate;
    }

    public HWUse getHwUse() {
        return hwUse;
    }

    public void setHwUse(HWUse hwUse) {
        this.hwUse = hwUse;
    }
}
