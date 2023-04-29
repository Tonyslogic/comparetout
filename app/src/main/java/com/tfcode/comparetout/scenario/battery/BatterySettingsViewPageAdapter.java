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

package com.tfcode.comparetout.scenario.battery;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class BatterySettingsViewPageAdapter extends FragmentStateAdapter {

    private int mBatteryCount;
    private Map<Integer, BatterySettingsFragment> mBatterySettingsFragments = new HashMap<>();
    private final Map<Long, Fragment> mFragmentIDMap = new HashMap<>();
    private Map<Integer, Long> mPos2ID = new HashMap<>();
    private long mLastID = 0L;

    public BatterySettingsViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mBatteryCount = count;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) {
            BatterySettingsFragment dayRateFragment = BatterySettingsFragment.newInstance(position);
            mBatterySettingsFragments.put(position, dayRateFragment);
            mLastID++;
            mFragmentIDMap.put(mLastID, dayRateFragment);
            mPos2ID.put(position, mLastID);
            return dayRateFragment;
        }
        else return Objects.requireNonNull(mBatterySettingsFragments.get(position));
    }

    @Override
    public int getItemCount() {
        return mBatteryCount;
    }

    @Override
    public boolean containsItem(long itemId) {return mFragmentIDMap.containsKey(itemId); }

    @Override
    public long getItemId(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) createFragment(position);
        Long aLong = mPos2ID.get(position);
        if (null == aLong) return 0L;
        return aLong;
    }

    public void add(int index) {
        System.out.println("Adding battery settings at " + index);
        BatterySettingsFragment panelFragment = BatterySettingsFragment.newInstance(index);
        for (BatterySettingsFragment frag : mBatterySettingsFragments.values()) frag.refreshFocus();
        mBatterySettingsFragments.put(index,panelFragment);
        mLastID++;
        mFragmentIDMap.put(mLastID, panelFragment);
        mPos2ID.put(index, mLastID);
        mBatteryCount++;
        notifyItemInserted(index);
    }

    public void delete(int pos) {
        Map<Integer, BatterySettingsFragment> newBatterySettingsFragments = new HashMap<>();
        Map<Integer, Long> newPos2ID = new HashMap<>();
        newPos2ID.put(0, 0L);
        for (int key: mBatterySettingsFragments.keySet()){
            BatterySettingsFragment panelFragment = mBatterySettingsFragments.get(key);
            if (key < pos) {
                newBatterySettingsFragments.put(key, mBatterySettingsFragments.get(key));
                if (panelFragment != null) {
                    panelFragment.refreshFocus();
                }
                newPos2ID.put(key, mPos2ID.get(key));
            }
            if (key > pos) {
                newBatterySettingsFragments.put(key - 1, panelFragment);
                if (panelFragment != null) {
                    panelFragment.batteryDeleted(key - 1);
                }
                newPos2ID.put(key - 1, mPos2ID.get(key));
            }
        }
        mBatterySettingsFragments = newBatterySettingsFragments;
        mFragmentIDMap.remove(mPos2ID.get(pos));
        mPos2ID = newPos2ID;
        mBatteryCount--;
        notifyItemRemoved(pos);
    }

    // FRAGMENT BROADCAST METHODS
    public void setEdit(boolean ed) {
        for (BatterySettingsFragment batterySettingsFragment: mBatterySettingsFragments.values()) batterySettingsFragment.setEditMode(ed);
    }

    public void updateDBIndex() {
        for (BatterySettingsFragment batterySettingsFragment: mBatterySettingsFragments.values()) batterySettingsFragment.updateDBIndex();
    }
}


