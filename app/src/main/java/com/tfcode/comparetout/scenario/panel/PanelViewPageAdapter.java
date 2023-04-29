package com.tfcode.comparetout.scenario.panel;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PanelViewPageAdapter extends FragmentStateAdapter {

    private int mPanelCount;
    private Map<Integer, PanelFragment> mPanelFragments = new HashMap<>();
    private final Map<Long, Fragment> mFragmentIDMap = new HashMap<>();
    private Map<Integer, Long> mPos2ID = new HashMap<>();
    private long mLastID = 0L;

    public PanelViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mPanelCount = count;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Long id = mPos2ID.get(position);
        if (id == null) {
            PanelFragment dayRateFragment = PanelFragment.newInstance(position);
            mPanelFragments.put(position, dayRateFragment);
            mLastID++;
            mFragmentIDMap.put(mLastID, dayRateFragment);
            mPos2ID.put(position, mLastID);
            return dayRateFragment;
        }
        else return Objects.requireNonNull(mPanelFragments.get(position));
    }

    @Override
    public int getItemCount() {
        return mPanelCount;
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

    public void add(int index) {
        System.out.println("Adding panel at " + index);
        PanelFragment panelFragment = PanelFragment.newInstance(index);
        for (PanelFragment frag : mPanelFragments.values()) frag.refreshFocus();
        mPanelFragments.put(index,panelFragment);
        mLastID++;
        mFragmentIDMap.put(mLastID, panelFragment);
        mPos2ID.put(index, mLastID);
        mPanelCount++;
        notifyItemInserted(index);
    }

    public void delete(int pos) {
        Map<Integer, PanelFragment> newPanelFragments = new HashMap<>();
        Map<Integer, Long> newPos2ID = new HashMap<>();
        newPos2ID.put(0, 0L);
        for (int key: mPanelFragments.keySet()){
            PanelFragment panelFragment = mPanelFragments.get(key);
            if (key < pos) {
                newPanelFragments.put(key, mPanelFragments.get(key));
                if (panelFragment != null) {
                    panelFragment.refreshFocus();
                }
                newPos2ID.put(key, mPos2ID.get(key));
            }
            if (key > pos) {
                newPanelFragments.put(key - 1, panelFragment);
                if (panelFragment != null) {
                    panelFragment.panelDeleted(key - 1);
                }
                newPos2ID.put(key - 1, mPos2ID.get(key));
            }
        }
        mPanelFragments = newPanelFragments;
        mFragmentIDMap.remove(mPos2ID.get(pos));
        mPos2ID = newPos2ID;
        mPanelCount--;
        notifyItemRemoved(pos);
    }

    // FRAGMENT BROADCAST METHODS
    public void setEdit(boolean ed) {
        for (PanelFragment panelFragment: mPanelFragments.values()) panelFragment.setEditMode(ed);
    }

    public void updateDBIndex() {
        for (PanelFragment panelFragment: mPanelFragments.values()) panelFragment.updateDBIndex();
    }
}
