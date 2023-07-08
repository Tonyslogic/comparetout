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

package com.tfcode.comparetout.scenario.ev;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EVScheduleViewPageAdapter  extends FragmentStateAdapter {

    private int mEVScheduleCount;
    private Map<Integer, EVScheduleFragment> mEVScheduleFragments = new HashMap<>();
    private final Map<Long, Fragment> mFragmentIDMap = new HashMap<>();
    private Map<Integer, Long> mPos2ID = new HashMap<>();
    private long mLastID = 0L;

    public EVScheduleViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mEVScheduleCount = count;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) {
            EVScheduleFragment dayRateFragment = EVScheduleFragment.newInstance(position);
            mEVScheduleFragments.put(position, dayRateFragment);
            mLastID++;
            mFragmentIDMap.put(mLastID, dayRateFragment);
            mPos2ID.put(position, mLastID);
            return dayRateFragment;
        }
        else return Objects.requireNonNull(mEVScheduleFragments.get(position));
    }

    @Override
    public int getItemCount() {
        return mEVScheduleCount;
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
        EVScheduleFragment evScheduleFragment = EVScheduleFragment.newInstance(index);
        for (EVScheduleFragment frag : mEVScheduleFragments.values()) frag.refreshFocus();
        mEVScheduleFragments.put(index,evScheduleFragment);
        mLastID++;
        mFragmentIDMap.put(mLastID, evScheduleFragment);
        mPos2ID.put(index, mLastID);
        mEVScheduleCount++;
        notifyItemInserted(index);
    }

    public void delete(int pos) {
        Map<Integer, EVScheduleFragment> newEVScheduleFragments = new HashMap<>();
        Map<Integer, Long> newPos2ID = new HashMap<>();
        newPos2ID.put(0, 0L);
        for (int key: mEVScheduleFragments.keySet()){
            EVScheduleFragment panelFragment = mEVScheduleFragments.get(key);
            if (key < pos) {
                newEVScheduleFragments.put(key, mEVScheduleFragments.get(key));
                if (panelFragment != null) {
                    panelFragment.refreshFocus();
                }
                newPos2ID.put(key, mPos2ID.get(key));
            }
            if (key > pos) {
                newEVScheduleFragments.put(key - 1, panelFragment);
                if (panelFragment != null) {
                    panelFragment.batteryDeleted(key - 1);
                }
                newPos2ID.put(key - 1, mPos2ID.get(key));
            }
        }
        mEVScheduleFragments = newEVScheduleFragments;
        mFragmentIDMap.remove(mPos2ID.get(pos));
        mPos2ID = newPos2ID;
        mEVScheduleCount--;
        notifyItemRemoved(pos);
    }

    // FRAGMENT BROADCAST METHODS
    public void setEdit(boolean ed) {
        for (EVScheduleFragment batterySettingsFragment: mEVScheduleFragments.values()) batterySettingsFragment.setEditMode(ed);
    }

    public void updateDBIndex() {
        for (EVScheduleFragment batterySettingsFragment: mEVScheduleFragments.values()) batterySettingsFragment.updateDBIndex();
    }

}


