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

package com.tfcode.comparetout.importers.alphaess;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ImportAlphaViewPageAdapter  extends FragmentStateAdapter {
    ImportAlphaOverview mAlphaOverview = ImportAlphaOverview.newInstance();
    ImportAlphaDaily mAlphaDaily = ImportAlphaDaily.newInstance();
    ImportAlphaMonthly mAlphaMonthly = ImportAlphaMonthly.newInstance();
    ImportAlphaYearly mAlphaYearly = ImportAlphaYearly.newInstance();

    public ImportAlphaViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int ignoredCount) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return mAlphaOverview;
        if (position == 1) return mAlphaDaily;
        if (position == 2) return mAlphaMonthly;
        else return mAlphaYearly;
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}