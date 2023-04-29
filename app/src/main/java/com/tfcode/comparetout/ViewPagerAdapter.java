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