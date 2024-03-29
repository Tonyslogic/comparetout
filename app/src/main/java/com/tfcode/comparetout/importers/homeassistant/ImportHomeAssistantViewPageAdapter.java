/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.homeassistant;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ImportHomeAssistantViewPageAdapter extends FragmentStateAdapter {
    ImportHAOverview mHAOverview = ImportHAOverview.newInstance();
    ImportHAGraphs mHAGraphs = ImportHAGraphs.newInstance();
    ImportHAKeyStats mHAKeyStats = ImportHAKeyStats.newInstance();
    ImportHAGenerateScenario mHAGenerate = ImportHAGenerateScenario.newInstance();

    public ImportHomeAssistantViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int ignoredCount) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return mHAOverview;
        if (position == 1) return mHAGraphs;
        if (position == 2) return mHAKeyStats;
        else return mHAGenerate;
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
