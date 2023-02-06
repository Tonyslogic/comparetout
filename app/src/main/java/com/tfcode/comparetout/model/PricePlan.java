package com.tfcode.comparetout.model;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.text.SimpleDateFormat;
import java.util.Date;

@Entity(tableName = "PricePlans")
public class PricePlan {
    @PrimaryKey(autoGenerate = true)
    private long id;
    @NonNull private String supplier = "<SUPPLIER>";
    @NonNull private String planName = "<PLAN>";
    private double feed = 0.0;
    private double standingCharges = 0.0;
    private double signUpBonus = 0.0;
    @SuppressLint("SimpleDateFormat")
    @NonNull
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
    private String lastUpdate = (new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
    @NonNull private String reference = "<REFERENCE>";
    private boolean active = true;


    @Override
    public boolean equals(@Nullable Object object)
    {
        if(object == null) return false;
        if(object == this) return true;

        if(object instanceof PricePlan)
        {
            return planName.equals(((PricePlan) object).getPlanName())
                    && supplier.equals(((PricePlan) object).getSupplier());
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return (supplier + planName).hashCode();
    }


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }


    @NonNull
    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(@NonNull String supplier) {
        this.supplier = supplier;
    }

    @NonNull
    public String getPlanName() {
        return planName;
    }

    public void setPlanName(@NonNull String planName) {
        this.planName = planName;
    }

    public double getFeed() {
        return feed;
    }

    public void setFeed(double feed) {
        this.feed = feed;
    }

    public double getStandingCharges() {
        return standingCharges;
    }

    public void setStandingCharges(double standingCharges) {
        this.standingCharges = standingCharges;
    }

    public double getSignUpBonus() {
        return signUpBonus;
    }

    public void setSignUpBonus(double signUpBonus) {
        this.signUpBonus = signUpBonus;
    }

    @NonNull
    public String getReference() {
        return reference;
    }

    public void setReference(@NonNull String reference) {
        this.reference = reference;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @NonNull
    public String getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(@NonNull String lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public PricePlan copy() {
        PricePlan copy = new PricePlan();
        copy.supplier = supplier;
        copy.planName = planName + " (Copy)";
        copy.feed = feed;
        copy.standingCharges = standingCharges;
        copy.signUpBonus = signUpBonus;
        copy.lastUpdate = lastUpdate;
        copy.reference = reference;
        copy.active = active;
        copy.id = 0;
        return copy;
    }
}
