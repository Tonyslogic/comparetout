package com.tfcode.comparetout.priceplan;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class PricePlanViewPageAdapter extends FragmentStateAdapter {

    private int mDayRateCount;
    private final PricePlanEditFragment pricePlanEditFragment = PricePlanEditFragment.newInstance();
//    private PricePlanEditDayFragment pricePlanEditDayFragment = PricePlanEditDayFragment.newInstance();

    public PricePlanViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
        mDayRateCount = count;
    }
    @NonNull @Override public Fragment createFragment(int position) {
        if (position == 0) {
            return pricePlanEditFragment;
        }
        return PricePlanEditDayFragment.newInstance(position);
    }
    @Override public int getItemCount() {
        return mDayRateCount;
    }

    public void setDayRateCount(int count) {
        this.mDayRateCount = count;
    }
}