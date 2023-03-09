package com.tfcode.comparetout.scenario;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.tfcode.comparetout.priceplan.PricePlanEditDayFragment;

public class ScenarioViewPageAdapter extends FragmentStateAdapter {
    ScenarioOverview mScenarioOverview = ScenarioOverview.newInstance();
    SenarioDetails mScenarioDetails = SenarioDetails.newInstance();
    public ScenarioViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int count) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return mScenarioOverview;
        else return mScenarioDetails;
    }

    @Override
    public int getItemCount() {
        return 2;
    }


    public void setEdit(boolean ed) {
        mScenarioOverview.setEditMode(ed);
        mScenarioDetails.setEditMode(ed);
    }
}
