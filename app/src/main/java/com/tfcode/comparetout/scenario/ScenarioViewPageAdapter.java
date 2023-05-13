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

package com.tfcode.comparetout.scenario;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ScenarioViewPageAdapter extends FragmentStateAdapter {
    ScenarioOverview mScenarioOverview = ScenarioOverview.newInstance();
    ScenarioDetails mScenarioDetails = ScenarioDetails.newInstance();
    ScenarioMonthly mScenarioMonthly = ScenarioMonthly.newInstance();
    public ScenarioViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int ignoredCount) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return mScenarioOverview;
        if (position == 1) return mScenarioDetails;
        else return mScenarioMonthly;
    }

    @Override
    public int getItemCount() {
        return 3;
    }


    public void setEdit(boolean ed) {
        mScenarioOverview.setEditMode(ed);
    }
}
