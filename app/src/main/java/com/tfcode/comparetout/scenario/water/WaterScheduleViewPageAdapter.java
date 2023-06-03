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

package com.tfcode.comparetout.scenario.water;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WaterScheduleViewPageAdapter extends FragmentStateAdapter {

    private int mWaterScheduleCount;
    private Map<Integer, WaterScheduleFragment> mWaterScheduleFragments = new HashMap<>();
    private final Map<Long, Fragment> mFragmentIDMap = new HashMap<>();
    private Map<Integer, Long> mPos2ID = new HashMap<>();
    private long mLastID = 0L;

    public WaterScheduleViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mWaterScheduleCount = count;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) {
            WaterScheduleFragment waterScheduleFragment = WaterScheduleFragment.newInstance(position);
            mWaterScheduleFragments.put(position, waterScheduleFragment);
            mLastID++;
            mFragmentIDMap.put(mLastID, waterScheduleFragment);
            mPos2ID.put(position, mLastID);
            return waterScheduleFragment;
        }
        else return Objects.requireNonNull(mWaterScheduleFragments.get(position));
    }

    @Override
    public int getItemCount() {
        return mWaterScheduleCount;
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
        System.out.println("Adding water schedule charging at " + index);
        WaterScheduleFragment waterScheduleFragment = WaterScheduleFragment.newInstance(index);
        for (WaterScheduleFragment frag : mWaterScheduleFragments.values()) frag.refreshFocus();
        mWaterScheduleFragments.put(index,waterScheduleFragment);
        mLastID++;
        mFragmentIDMap.put(mLastID, waterScheduleFragment);
        mPos2ID.put(index, mLastID);
        mWaterScheduleCount++;
        notifyItemInserted(index);
    }

    public void delete(int pos) {
        Map<Integer, WaterScheduleFragment> newWaterScheduleFragments = new HashMap<>();
        Map<Integer, Long> newPos2ID = new HashMap<>();
        newPos2ID.put(0, 0L);
        for (int key: mWaterScheduleFragments.keySet()){
            WaterScheduleFragment waterScheduleFragment = mWaterScheduleFragments.get(key);
            if (key < pos) {
                newWaterScheduleFragments.put(key, mWaterScheduleFragments.get(key));
                if (waterScheduleFragment != null) {
                    waterScheduleFragment.refreshFocus();
                }
                newPos2ID.put(key, mPos2ID.get(key));
            }
            if (key > pos) {
                newWaterScheduleFragments.put(key - 1, waterScheduleFragment);
                if (waterScheduleFragment != null) {
                    waterScheduleFragment.scheduleDeleted(key - 1);
                }
                newPos2ID.put(key - 1, mPos2ID.get(key));
            }
        }
        mWaterScheduleFragments = newWaterScheduleFragments;
        mFragmentIDMap.remove(mPos2ID.get(pos));
        mPos2ID = newPos2ID;
        mWaterScheduleCount--;
        notifyItemRemoved(pos);
    }

    // FRAGMENT BROADCAST METHODS
    public void setEdit(boolean ed) {
        for (WaterScheduleFragment waterScheduleFragment: mWaterScheduleFragments.values()) waterScheduleFragment.setEditMode(ed);
    }

    public void updateDBIndex() {
        for (WaterScheduleFragment waterScheduleFragment: mWaterScheduleFragments.values()) waterScheduleFragment.updateDBIndex();
    }

}
