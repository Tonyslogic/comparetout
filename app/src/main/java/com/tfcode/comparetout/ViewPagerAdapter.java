package com.tfcode.comparetout;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private static final int CARD_ITEM_SIZE = 3;
    private PricePlanNavFragment pricePlanNavFragment = PricePlanNavFragment.newInstance();
    private ScenarioNavFragment scenarioNavFragment = ScenarioNavFragment.newInstance();
    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    @NonNull @Override public Fragment createFragment(int position) {
        if (position == 0) {
            return pricePlanNavFragment;
        }
        if (position == 1) {
            return scenarioNavFragment;
        }
        return CardFragment.newInstance(position);
    }
    @Override public int getItemCount() {
        return CARD_ITEM_SIZE;
    }
}