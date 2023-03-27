package com.tfcode.comparetout.model.scenario;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "hwdivert")
public class HWDivert {

    @PrimaryKey(autoGenerate = true)
    private long hwDivertIndex;

    private boolean active = true;

    public long getHwDivertIndex() {
        return hwDivertIndex;
    }

    public void setHwDivertIndex(long hwDivertIndex) {
        this.hwDivertIndex = hwDivertIndex;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
