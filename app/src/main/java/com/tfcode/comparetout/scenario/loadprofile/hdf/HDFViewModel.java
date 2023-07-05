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

package com.tfcode.comparetout.scenario.loadprofile.hdf;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NavigableMap;

public class HDFViewModel  extends AndroidViewModel {

    private boolean mLoaded = false;
    private boolean mGenerated = false;
    private boolean mCosted = false;
    private boolean mHasSelectedDates = false;
    private String mSelectedDates = "No range selected";
    private String mAvailableDates = "";
    private LocalDateTime mFileStart;
    private LocalDateTime mFileEnd;
    private LocalDateTime mSelectedStart;
    private LocalDateTime mSelectedEnd;
    private NavigableMap<LocalDateTime, Double> mImports;
    private NavigableMap<LocalDateTime, Double> mExports;
    private List<Costings> mCostings;
    private Double mTotalSelectedImport = 0d;
    private long mTotalDaysSelected = 0;
    private List<PricePlan> mPlans;

    public HDFViewModel(@NonNull Application application) {
        super(application);
    }

    public List<PricePlan> getPlans() {
        return mPlans;
    }

    public void setPlans(List<PricePlan> mPlans) {
        this.mPlans = mPlans;
    }

    public boolean isLoaded() {
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

    public boolean isCosted() {
        return mCosted;
    }

    public void setCosted(boolean mCosted) {
        this.mCosted = mCosted;
    }

    public boolean hasSelectedDates() {
        return mHasSelectedDates;
    }

    public void setHasSelectedDates(boolean mHasSelectedDates) {
        this.mHasSelectedDates = mHasSelectedDates;
    }

    public String getSelectedDates() {
        return mSelectedDates;
    }

    public void setSelectedDates(String mSelectedDates) {
        this.mSelectedDates = mSelectedDates;
    }

    public String getAvailableDates() {
        return mAvailableDates;
    }

    public void setAvailableDates(String mAvailableDates) {
        this.mAvailableDates = mAvailableDates;
    }

    public LocalDateTime getFileStart() {
        return mFileStart;
    }

    public void setFileStart(LocalDateTime mFileStart) {
        this.mFileStart = mFileStart;
    }

    public LocalDateTime getFileEnd() {
        return mFileEnd;
    }

    public void setFileEnd(LocalDateTime mFileEnd) {
        this.mFileEnd = mFileEnd;
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

    public Double getTotalSelectedImport() {
        return mTotalSelectedImport;
    }

    public void setTotalSelectedImport(Double mTotalSelectedImport) {
        this.mTotalSelectedImport = mTotalSelectedImport;
    }

    public long getTotalDaysSelected() {
        return mTotalDaysSelected;
    }

    public void setTotalDaysSelected(long mTotalDaysSelected) {
        this.mTotalDaysSelected = mTotalDaysSelected;
    }
}
