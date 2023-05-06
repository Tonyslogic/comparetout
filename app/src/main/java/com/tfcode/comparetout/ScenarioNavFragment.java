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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.scenario.ScenarioActivity;

import java.util.List;

public class ScenarioNavFragment extends Fragment {

    private ComparisonUIViewModel mViewModel;
    private TableLayout mTableLayout;
    private List<Scenario> mScenarios;

    public static ScenarioNavFragment newInstance() {
        return new ScenarioNavFragment();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        updateView();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mViewModel.getAllScenarios().observe(this, scenarios -> {
            System.out.println("Observed a change in live scenario data " + scenarios.size());
            SimulatorLauncher.simulateIfNeeded(getContext());
            mScenarios = scenarios;
            updateView();
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scenario_nav, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTableLayout = requireView().findViewById(R.id.scenarioTable);

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {getString(R.string.main_activity_usage),
                getString(R.string.main_activity_costs),
                getString(R.string.main_activity_compare)};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();
    }

    private void updateView() {
        mTableLayout.removeAllViews();

        if (mScenarios != null && mScenarios.size() > 0 && getActivity() != null) {
            mTableLayout.setShrinkAllColumns(false);
            mTableLayout.setStretchAllColumns(true);
            mTableLayout.setColumnShrinkable(1, true);
            mTableLayout.setColumnStretchable(1, false);
            for (Scenario scenario : mScenarios) {

                // CREATE TABLE ROW
                TableRow tableRow;
                tableRow = new TableRow(getActivity());

                // CREATE PARAM FOR MARGINING
                TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
                planParams.topMargin = 2;
                planParams.rightMargin = 2;

                TableRow.LayoutParams textParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
                planParams.topMargin = 2;
                planParams.rightMargin = 2;

                // CREATE TEXTVIEW

                MaterialCheckBox a = new MaterialCheckBox(getActivity());
                a.setChecked(scenario.isActive());
                TextView b = new TextView(getActivity());
                b.setGravity(Gravity.CENTER_VERTICAL);
                b.setAutoSizeTextTypeUniformWithConfiguration(
                        1, 17, 1, TypedValue.COMPLEX_UNIT_SP);
                ImageButton c = new ImageButton(getActivity());
                ImageButton d = new ImageButton(getActivity());

                // SET PARAMS

                a.setLayoutParams(planParams);
                b.setLayoutParams(textParams);
                c.setLayoutParams(planParams);
                d.setLayoutParams(planParams);

                // SET BACKGROUND COLOR

                c.setBackgroundColor(0);
                d.setBackgroundColor(0);

                // SET PADDING

                a.setPadding(10, 10, 10, 10);
                b.setPadding(10, 10, 10, 10);
                c.setPadding(10, 10, 10, 10);
                d.setPadding(10, 10, 10, 10);

                // SET TEXTVIEW TEXT

                b.setText(scenario.getScenarioName());
                c.setImageResource(R.drawable.ic_baseline_delete_24);
                d.setImageResource(R.drawable.ic_baseline_content_copy_24);

                a.setId((int) scenario.getScenarioIndex());
                c.setId((int) scenario.getScenarioIndex());
                d.setId((int) scenario.getScenarioIndex());

                a.setOnClickListener(v -> mViewModel.updateScenarioActiveStatus(v.getId(), a.isChecked()));

                c.setOnClickListener(v -> {
                    tableRow.setBackgroundColor(Color.RED);
                    a.setBackgroundColor(Color.RED);
                    b.setBackgroundColor(Color.RED);
                    c.setBackgroundColor(Color.RED);
                    d.setBackgroundColor(Color.RED);
                    mViewModel.deleteScenario(v.getId());
                });

                d.setOnClickListener(v -> mViewModel.copyScenario(v.getId()));

                b.setOnClickListener(v -> {
                    tableRow.setBackgroundColor(Color.LTGRAY);
                    a.setBackgroundColor(Color.LTGRAY);
                    b.setBackgroundColor(Color.LTGRAY);
                    c.setBackgroundColor(Color.LTGRAY);
                    d.setBackgroundColor(Color.LTGRAY);
                    Intent intent = new Intent(getActivity(), ScenarioActivity.class);
                    intent.putExtra("ScenarioID", scenario.getScenarioIndex());
                    startActivity(intent);
                });

                // ADD TEXTVIEW TO TABLEROW

                tableRow.addView(a);
                tableRow.addView(b);
                tableRow.addView(c);
                tableRow.addView(d);

                // ADD TABLEROW TO TABLELAYOUT
                mTableLayout.addView(tableRow);
            }
        }
        else {
            //TODO Popup help
            mTableLayout.setShrinkAllColumns(true);
            mTableLayout.setStretchAllColumns(false);

            TextView help = new TextView(getActivity());
            help.setSingleLine(false);
            help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

            help.setText(R.string.NoScenariosText);

            mTableLayout.addView(help);
        }
    }

}