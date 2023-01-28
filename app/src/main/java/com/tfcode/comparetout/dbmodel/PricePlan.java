package com.tfcode.comparetout.dbmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "PricePlans")
public class PricePlan {
    @PrimaryKey(autoGenerate = true)
    private long id;
    @NonNull private String supplier;
    @NonNull private String planName;
    @NonNull private double feed;
    @NonNull private double standingCharges;
    @NonNull private double signUpBonus;
    @NonNull
    @ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
    private String lastUpdate;
    @NonNull private String reference;
    @NonNull private boolean active;

    @Override
    public boolean equals(@Nullable Object object)
    {
        if(object == null) return false;
        if(object == this) return true;

        if(object instanceof PricePlan)
        {
            if (planName.equals(((PricePlan) object).getPlanName())
                    && supplier.equals(((PricePlan) object).getSupplier()))
                return true;
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
}
