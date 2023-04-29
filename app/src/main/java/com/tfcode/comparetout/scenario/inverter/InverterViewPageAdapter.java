package com.tfcode.comparetout.scenario.inverter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InverterViewPageAdapter extends FragmentStateAdapter {

    private int mInverterCount;
    private Map<Integer, InverterFragment> mInverterFragments = new HashMap<>();
    private final Map<Long, Fragment> mFragmentIDMap = new HashMap<>();
    private Map<Integer, Long> mPos2ID = new HashMap<>();
    private long mLastID = 0L;

    public InverterViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mInverterCount = count;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) {
            InverterFragment dayRateFragment = InverterFragment.newInstance(position);
            mInverterFragments.put(position, dayRateFragment);
            mLastID++;
            mFragmentIDMap.put(mLastID, dayRateFragment);
            mPos2ID.put(position, mLastID);
            return dayRateFragment;
        }
        else return Objects.requireNonNull(mInverterFragments.get(position));
    }

    @Override
    public int getItemCount() {
        return mInverterCount;
    }

    @Override
    public boolean containsItem(long itemId) {return mFragmentIDMap.containsKey(itemId); }

    @Override
    public long getItemId(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) createFragment(position);
        Long itemID = mPos2ID.get(position);
        if (!(null == itemID))
            return itemID;
        else
            return 0L;
    }

    public void add(int index) {
        System.out.println("Adding inverter at " + index);
        InverterFragment inverterFragment = InverterFragment.newInstance(index);
        for (InverterFragment frag : mInverterFragments.values()) frag.refreshFocus();
        mInverterFragments.put(index,inverterFragment);
        mLastID++;
        mFragmentIDMap.put(mLastID, inverterFragment);
        mPos2ID.put(index, mLastID);
        mInverterCount++;
        notifyItemInserted(index);
    }

    public void delete(int pos) {
        Map<Integer, InverterFragment> newInverterFragments = new HashMap<>();
        Map<Integer, Long> newPos2ID = new HashMap<>();
        newPos2ID.put(0, 0L);
        for (int key: mInverterFragments.keySet()){
            InverterFragment inverterFragment = mInverterFragments.get(key);
            if (key < pos) {
                newInverterFragments.put(key, mInverterFragments.get(key));
                if (inverterFragment != null) {
                    inverterFragment.refreshFocus();
                }
                newPos2ID.put(key, mPos2ID.get(key));
            }
            if (key > pos) {
                newInverterFragments.put(key - 1, inverterFragment);
                if (inverterFragment != null) {
                    inverterFragment.inverterDeleted(key - 1);
                }
                newPos2ID.put(key - 1, mPos2ID.get(key));
            }
        }
        mInverterFragments = newInverterFragments;
        mFragmentIDMap.remove(mPos2ID.get(pos));
        mPos2ID = newPos2ID;
        mInverterCount--;
        notifyItemRemoved(pos);
    }

    // FRAGMENT BROADCAST METHODS
    public void setEdit(boolean ed) {
        for (InverterFragment inverterFragment: mInverterFragments.values()) inverterFragment.setEditMode(ed);
    }
}
