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

package com.tfcode.comparetout.priceplan;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PricePlanViewPageAdapter extends FragmentStateAdapter {

    private int mDayRateCount;
    private final PricePlanEditFragment pricePlanEditFragment = PricePlanEditFragment.newInstance();
    private Map<Integer, PricePlanEditDayFragment> mDayRateFragments = new HashMap<>();
    private final Map<Long, Fragment> mFragmentIDMap = new HashMap<>();
    private Map<Integer, Long> mPos2ID = new HashMap<>();
    private long mLastID = 1L;

    public PricePlanViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mDayRateCount = count;
        mFragmentIDMap.put(0L, pricePlanEditFragment);
        mPos2ID.put(0, 0L);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return pricePlanEditFragment;
        }
        Long id = mPos2ID.get(position);
        if (id == null) {
            PricePlanEditDayFragment dayRateFragment = PricePlanEditDayFragment.newInstance(position);
            mDayRateFragments.put(position, dayRateFragment);
            mLastID++;
            mFragmentIDMap.put(mLastID, dayRateFragment);
            mPos2ID.put(position, mLastID);
            return dayRateFragment;
        }
        else return Objects.requireNonNull(mDayRateFragments.get(position));
    }

    public void add(int index) {
        System.out.println("Adding day rate at " + index);
        PricePlanEditDayFragment pricePlanEditDayFragment = PricePlanEditDayFragment.newInstance(index);
        for (PricePlanEditDayFragment frag : mDayRateFragments.values()) frag.refreshFocus();
        mDayRateFragments.put(index,pricePlanEditDayFragment);
        mLastID++;
        mFragmentIDMap.put(mLastID, pricePlanEditDayFragment);
        mPos2ID.put(index, mLastID);
        mDayRateCount++;
        notifyItemInserted(index);
    }

    public void delete(int pos) {
        Map<Integer, PricePlanEditDayFragment> newDayRateFragments = new HashMap<>();
        Map<Integer, Long> newPos2ID = new HashMap<>();
        newPos2ID.put(0, 0L);
        for (int key: mDayRateFragments.keySet()){
            PricePlanEditDayFragment pricePlanEditDayFragment = mDayRateFragments.get(key);
            if (key < pos) {
                newDayRateFragments.put(key, mDayRateFragments.get(key));
                if (pricePlanEditDayFragment != null) {
                    pricePlanEditDayFragment.refreshFocus();
                }
                newPos2ID.put(key, mPos2ID.get(key));
            }
            if (key > pos) {
                newDayRateFragments.put(key - 1, pricePlanEditDayFragment);
                if (pricePlanEditDayFragment != null) {
                    pricePlanEditDayFragment.dayRateDeleted(key - 1);
                }
                newPos2ID.put(key - 1, mPos2ID.get(key));
            }
        }
        mDayRateFragments = newDayRateFragments;
        mFragmentIDMap.remove(mPos2ID.get(pos));
        mPos2ID = newPos2ID;
        mDayRateCount--;
        notifyItemRemoved(pos);
    }

    public void setEdit(boolean ed) {
        pricePlanEditFragment.setEditMode(ed);
        for (PricePlanEditDayFragment pricePlanEditDayFragment: mDayRateFragments.values()){
            pricePlanEditDayFragment.setEditMode(ed);
        }
    }

    @Override
    public int getItemCount() {
        return mDayRateCount;
    }

    @Override
    public boolean containsItem(long itemId) {return mFragmentIDMap.containsKey(itemId); }

    @Override
    public long getItemId(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) createFragment(position);
        Long itemID =mPos2ID.get(position);
        if (!(null == itemID)) return itemID;
        else return 0L;
    }
}