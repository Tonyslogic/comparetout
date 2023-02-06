package com.tfcode.comparetout.priceplan;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;

public class PricePlanViewPageAdapter extends FragmentStateAdapter {

    private int mDayRateCount;
    private final PricePlanEditFragment pricePlanEditFragment = PricePlanEditFragment.newInstance();
    private Map<Integer, PricePlanEditDayFragment> dayRateFragments = new HashMap<>();

    public PricePlanViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mDayRateCount = count;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return pricePlanEditFragment;
        }
        PricePlanEditDayFragment dayRateFragment = PricePlanEditDayFragment.newInstance(position);
        dayRateFragments.put(position, dayRateFragment);
        return dayRateFragment;
    }

    public void add(int index) {
        Map<Integer, PricePlanEditDayFragment> newDayRateFragments = new HashMap<>();
        for (int key: dayRateFragments.keySet()){
            if (key < index) newDayRateFragments.put(key, dayRateFragments.get(key));
            else newDayRateFragments.put(key + 1, dayRateFragments.get(key));
        }
        newDayRateFragments.put(index, PricePlanEditDayFragment.newInstance(index));
        dayRateFragments = newDayRateFragments;
        mDayRateCount = mDayRateCount + 1;
        notifyDataSetChanged();
    }

    public void setEdit(boolean ed) {
        pricePlanEditFragment.setEditMode(ed);
        for (PricePlanEditDayFragment pricePlanEditDayFragment: dayRateFragments.values()){
            pricePlanEditDayFragment.setEditMode(ed);
        }
    }

    @Override
    public int getItemCount() {
        return mDayRateCount;
    }


}