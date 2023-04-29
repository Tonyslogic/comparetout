package com.tfcode.comparetout.scenario.loadprofile;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class LoadProfileViewPageAdapter extends FragmentStateAdapter {

    LoadProfilePropertiesFragment loadProfilePropertiesFragment = LoadProfilePropertiesFragment.newInstance();
    LoadProfileDailyDistributionFragment loadProfileDailyDistributionFragment = LoadProfileDailyDistributionFragment.newInstance();
    LoadProfileHourlyDistributionFragment loadProfileHourlyDistributionFragment = LoadProfileHourlyDistributionFragment.newInstance();
    LoadProfileMonthlyDistributionFragment loadProfileMonthlyDistributionFragment = LoadProfileMonthlyDistributionFragment.newInstance();

    public LoadProfileViewPageAdapter(LoadProfileActivity loadProfileActivity, int ignoredI) {
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
        return loadProfilePropertiesFragment;
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
