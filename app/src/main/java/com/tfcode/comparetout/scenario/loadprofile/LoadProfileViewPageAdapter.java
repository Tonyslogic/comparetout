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
