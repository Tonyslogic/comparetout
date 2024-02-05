/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout.importers;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NavigableMap;

public class CostViewModel extends AndroidViewModel {

    private boolean mLoaded = false;
    private boolean mGenerated = false;
    private String mSelectedDates = "No range selected";
    private LocalDateTime mDBStart;
    private LocalDateTime mDBEnd;
    private LocalDateTime mSelectedStart;
    private LocalDateTime mSelectedEnd;
    private NavigableMap<LocalDateTime, Double> mImports;
    private NavigableMap<LocalDateTime, Double> mExports;
    private List<Costings> mCostings;
    private long mTotalDaysSelected = 0;
    private List<PricePlan> mPlans;

    public CostViewModel(@NonNull Application application) {
        super(application);
    }

    public List<PricePlan> getPlans() {
        return mPlans;
    }

    public void setPlans(List<PricePlan> mPlans) {
        this.mPlans = mPlans;
    }

    public boolean isReadyToCost() {
        return mLoaded;
    }

    public void setLoaded(boolean mLoaded) {
        this.mLoaded = mLoaded;
    }

    public boolean isGenerated() {
        return mGenerated;
    }

    public void setGenerated(boolean mGenerated) {
        this.mGenerated = mGenerated;
    }

    public String getSelectedDates() {
        return mSelectedDates;
    }

    public void setSelectedDates(String mSelectedDates) {
        this.mSelectedDates = mSelectedDates;
    }

    public LocalDateTime getDBStart() {
        return mDBStart;
    }

    public void setDBStart(LocalDateTime mFileStart) {
        this.mDBStart = mFileStart;
    }

    public LocalDateTime getDBEnd() {
        return mDBEnd;
    }

    public void setDBEnd(LocalDateTime mFileEnd) {
        this.mDBEnd = mFileEnd;
    }

    public LocalDateTime getSelectedStart() {
        return mSelectedStart;
    }

    public void setSelectedStart(LocalDateTime mSelectedStart) {
        this.mSelectedStart = mSelectedStart;
    }

    public LocalDateTime getSelectedEnd() {
        return mSelectedEnd;
    }

    public void setSelectedEnd(LocalDateTime mSelectedEnd) {
        this.mSelectedEnd = mSelectedEnd;
    }

    public NavigableMap<LocalDateTime, Double> getImports() {
        return mImports;
    }

    public void setImports(NavigableMap<LocalDateTime, Double> mImports) {
        this.mImports = mImports;
    }

    public NavigableMap<LocalDateTime, Double> getExports() {
        return mExports;
    }

    public void setExports(NavigableMap<LocalDateTime, Double> mExports) {
        this.mExports = mExports;
    }

    public List<Costings> getCostings() {
        return mCostings;
    }

    public void setCostings(List<Costings> mCostings) {
        this.mCostings = mCostings;
    }

    public long getTotalDaysSelected() {
        return mTotalDaysSelected;
    }

    public void setTotalDaysSelected(long mTotalDaysSelected) {
        this.mTotalDaysSelected = mTotalDaysSelected;
    }
}
