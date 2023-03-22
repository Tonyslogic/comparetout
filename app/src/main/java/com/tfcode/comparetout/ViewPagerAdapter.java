package com.tfcode.comparetout;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private static final int CARD_ITEM_SIZE = 3;
    private final PricePlanNavFragment pricePlanNavFragment = PricePlanNavFragment.newInstance();
    private final ScenarioNavFragment scenarioNavFragment = ScenarioNavFragment.newInstance();
    private final ComparisonFragment comparisonFragment = ComparisonFragment.newInstance();

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    @NonNull @Override public Fragment createFragment(int position) {
        if (position == MainActivity.COSTS_FRAGMENT) {
            return pricePlanNavFragment;
        }
        if (position == MainActivity.USAGE_FRAGMENT) {
            return scenarioNavFragment;
        }
        if (position == MainActivity.COMPARE_FRAGMENT) {
            return comparisonFragment;
        }
        return scenarioNavFragment;
    }
    @Override public int getItemCount() {
        return CARD_ITEM_SIZE;
    }
}