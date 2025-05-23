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

package com.tfcode.comparetout.importers.esbn;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.tfcode.comparetout.util.GraphableActivity;

public class ImportESBNViewPageAdapter extends FragmentStateAdapter  implements GraphableActivity {
    ImportESBNOverview mESBNOverview = ImportESBNOverview.newInstance();
    ImportESBNGraphs mESBNGraphs = ImportESBNGraphs.newInstance();
    ImportESBNGenerateScenario mESBNGenerate = ImportESBNGenerateScenario.newInstance();

    public ImportESBNViewPageAdapter(@NonNull FragmentActivity fragmentActivity, int ignoredCount) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return mESBNOverview;
        if (position == 1) return mESBNGraphs;
        else return mESBNGenerate;
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    @Override
    public void hideFAB() {
        // Do nothing
    }

    @Override
    public String getSelectedSystemSN() {
        return null;
    }

    @Override
    public void setSelectedSystemSN(String serialNumber) {
        mESBNGraphs.setSelectedSystemSN(serialNumber);
        mESBNGenerate.setSelectedSystemSN(serialNumber);
    }
}
