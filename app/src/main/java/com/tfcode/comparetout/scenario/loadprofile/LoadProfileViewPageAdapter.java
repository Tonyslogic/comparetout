package com.tfcode.comparetout.scenario.loadprofile;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class LoadProfileViewPageAdapter extends FragmentStateAdapter {

    LoadProfilePropertiesFragment loadProfilePropertiesFragment = LoadProfilePropertiesFragment.newInstance();
    LoadProfileDailyDistributionFragment loadProfileDailyDistributionFragment = LoadProfileDailyDistributionFragment.newInstance();
    LoadProfileHourlyDistributionFragment loadProfileHourlyDistributionFragment = LoadProfileHourlyDistributionFragment.newInstance();
    LoadProfileMonthlyDistributionFragment loadProfileMonthlyDistributionFragment = LoadProfileMonthlyDistributionFragment.newInstance();

//    ScenarioOverview mScenarioOverview = ScenarioOverview.newInstance();
//    SenarioDetails mScenarioDetails = SenarioDetails.newInstance();

    public LoadProfileViewPageAdapter(LoadProfileActivity loadProfileActivity, int i) {
        super(loadProfileActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return loadProfilePropertiesFragment;
            case 1: return loadProfileDailyDistributionFragment;
            case 2: return loadProfileMonthlyDistributionFragment;
            case 3: return loadProfileHourlyDistributionFragment;
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return 4;
    }

    // FRAGMENT BROADCAST METHODS
    public void setEdit(boolean ed) {
        loadProfilePropertiesFragment.setEditMode(ed);
        loadProfileDailyDistributionFragment.setEditMode(ed);
        loadProfileMonthlyDistributionFragment.setEditMode(ed);
        loadProfileHourlyDistributionFragment.setEditMode(ed);
    }

    public void updateDistributionFromLeader() {
        loadProfileDailyDistributionFragment.updateDistributionFromLeader();
        loadProfileMonthlyDistributionFragment.updateDistributionFromLeader();
        loadProfileHourlyDistributionFragment.updateDistributionFromLeader();
    }
}
