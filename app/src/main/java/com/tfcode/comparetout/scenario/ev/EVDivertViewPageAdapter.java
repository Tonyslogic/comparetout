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

public class EVDivertViewPageAdapter extends FragmentStateAdapter {

    private int mEVDivertCount;
    private Map<Integer, EVDivertFragment> mEVDivertFragments = new HashMap<>();
    private final Map<Long, Fragment> mFragmentIDMap = new HashMap<>();
    private Map<Integer, Long> mPos2ID = new HashMap<>();
    private long mLastID = 0L;

    public EVDivertViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mEVDivertCount = count;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) {
            EVDivertFragment dayRateFragment = EVDivertFragment.newInstance(position);
            mEVDivertFragments.put(position, dayRateFragment);
            mLastID++;
            mFragmentIDMap.put(mLastID, dayRateFragment);
            mPos2ID.put(position, mLastID);
            return dayRateFragment;
        }
        else return Objects.requireNonNull(mEVDivertFragments.get(position));
    }

    @Override
    public int getItemCount() {
        return mEVDivertCount;
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
        System.out.println("Adding divert charging at " + index);
        EVDivertFragment evScheduleFragment = EVDivertFragment.newInstance(index);
        for (EVDivertFragment frag : mEVDivertFragments.values()) frag.refreshFocus();
        mEVDivertFragments.put(index,evScheduleFragment);
        mLastID++;
        mFragmentIDMap.put(mLastID, evScheduleFragment);
        mPos2ID.put(index, mLastID);
        mEVDivertCount++;
        notifyItemInserted(index);
    }

    public void delete(int pos) {
        Map<Integer, EVDivertFragment> newEVDivertFragments = new HashMap<>();
        Map<Integer, Long> newPos2ID = new HashMap<>();
        newPos2ID.put(0, 0L);
        for (int key: mEVDivertFragments.keySet()){
            EVDivertFragment divertFragment = mEVDivertFragments.get(key);
            if (key < pos) {
                newEVDivertFragments.put(key, mEVDivertFragments.get(key));
                if (divertFragment != null) {
                    divertFragment.refreshFocus();
                }
                newPos2ID.put(key, mPos2ID.get(key));
            }
            if (key > pos) {
                newEVDivertFragments.put(key - 1, divertFragment);
                if (divertFragment != null) {
                    divertFragment.batteryDeleted(key - 1);
                }
                newPos2ID.put(key - 1, mPos2ID.get(key));
            }
        }
        mEVDivertFragments = newEVDivertFragments;
        mFragmentIDMap.remove(mPos2ID.get(pos));
        mPos2ID = newPos2ID;
        mEVDivertCount--;
        notifyItemRemoved(pos);
    }

    // FRAGMENT BROADCAST METHODS
    public void setEdit(boolean ed) {
        for (EVDivertFragment batterySettingsFragment: mEVDivertFragments.values()) batterySettingsFragment.setEditMode(ed);
    }

    public void updateDBIndex() {
        for (EVDivertFragment batterySettingsFragment: mEVDivertFragments.values()) batterySettingsFragment.updateDBIndex();
    }

}
