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

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.LoadProfileJson;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LoadProfileDailyDistributionFragment extends Fragment {
    private boolean mEdit = false;

    private BarChart mBarChart;
    private TableLayout mEditTable;
    private LoadProfile mLoadProfile;

    public LoadProfileDailyDistributionFragment() {
        // Required empty public constructor
    }

    public static LoadProfileDailyDistributionFragment newInstance() {
        return new LoadProfileDailyDistributionFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Long mScenarioID = ((LoadProfileActivity) requireActivity()).getScenarioID();
        mEdit = ((LoadProfileActivity) requireActivity()).getEdit();
//        mEditFields = new ArrayList<>();
        ComparisonUIViewModel mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getLoadProfile(mScenarioID).observe(this, profile -> {
            if (!(null == profile)) {
                mLoadProfile = profile;
                updateView();
            }
            else {
                String loadProfileJsonString = ((LoadProfileActivity) requireActivity()).getLoadProfileJson();
                Type type = new TypeToken<LoadProfileJson>(){}.getType();
                LoadProfileJson lpj = new Gson().fromJson(loadProfileJsonString, type);
                mLoadProfile = JsonTools.createLoadProfile(lpj);
                updateView();
            }});
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_load_profile_daily_distribution, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBarChart = requireView().findViewById(R.id.daily_distribution_chart);
        mEditTable = requireView().findViewById(R.id.load_profile_edit_daily);
        if (!(null == mBarChart) && !(null == mLoadProfile)) updateView();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity()))
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @SuppressLint("DefaultLocale")
    private void updateView() {
        System.out.println("Updating LoadProfileDailyDistributionFragment " + mEdit);
        if (!mEdit) {
            mEditTable.setVisibility(View.INVISIBLE);
            mBarChart.setVisibility(View.VISIBLE);
            final ArrayList<String> xLabel = new ArrayList<>();
            xLabel.add("Sun");
            xLabel.add("Mon");
            xLabel.add("Tue");
            xLabel.add("Wed");
            xLabel.add("Thu");
            xLabel.add("Fri");
            xLabel.add("Sat");
            XAxis xAxis = mBarChart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return xLabel.get((int)value);
                }
            });
            List<Double> dowDist = mLoadProfile.getDowDist().dowDist;

            mBarChart.getAxisLeft().setTextColor(Color.DKGRAY); // left y-axis
            mBarChart.getAxisRight().setTextColor(Color.DKGRAY); // right y-axis
            mBarChart.getXAxis().setTextColor(Color.DKGRAY);
            mBarChart.getLegend().setTextColor(Color.DKGRAY);
            mBarChart.getDescription().setTextColor(Color.DKGRAY);

            ArrayList<BarEntry> entries = new ArrayList<>();
            for (int i = 0; i < 7; i++) entries.add(new BarEntry(i, dowDist.get(i).floatValue()));

            BarDataSet set1;

            if (mBarChart.getData() != null &&
                    mBarChart.getData().getDataSetCount() > 0) {
                set1 = (BarDataSet) mBarChart.getData().getDataSetByIndex(0);
                set1.setValues(entries);
                mBarChart.getData().notifyDataChanged();
                mBarChart.notifyDataSetChanged();
            } else {
                set1 = new BarDataSet(entries, "Daily distribution");
                ArrayList<IBarDataSet> dataSets = new ArrayList<>();
                dataSets.add(set1);
                BarData data = new BarData(dataSets);
                data.setValueTextSize(10f);
                data.setBarWidth(0.9f);
                mBarChart.getDescription().setEnabled(false);
                mBarChart.setData(data);
            }
            mBarChart.invalidate();
            mBarChart.refreshDrawableState();
        }
        else {
            mEditTable.setVisibility(View.VISIBLE);
            mBarChart.setVisibility(View.INVISIBLE);
            mEditTable.removeAllViews();
            TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            planParams.topMargin = 2;
            planParams.rightMargin = 2;
            planParams.height = mEditTable.getHeight() / 8;
            planParams.weight = 1;

            TableRow.LayoutParams textParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
            textParams.topMargin = 2;
            textParams.rightMargin = 2;
            // Distribution Table

            TableLayout distributionTable = new TableLayout(getActivity());
            distributionTable.setShrinkAllColumns(false);
            distributionTable.setColumnShrinkable(1, true);
            distributionTable.setColumnShrinkable(3, true);
            distributionTable.setStretchAllColumns(true);
            distributionTable.setColumnStretchable(1, false);
            distributionTable.setColumnStretchable(3, false);
            EditText totalPercent = new EditText(getActivity());
            int totalPercentValue = calculateTotalPercentValue(mLoadProfile.getDowDist().dowDist);


            String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            int dayIndex = 0;
            for (String day : days) {
                TableRow dayRow = new TableRow(getActivity());
                TextView dow = new TextView(getActivity());
                ImageButton minus = new ImageButton(getActivity());
                EditText percent = new EditText(getActivity());
                ImageButton plus = new ImageButton(getActivity());
                dow.setText(day);
                dow.setGravity(Gravity.CENTER);

                dayRow.setLayoutParams(planParams);
                dow.setLayoutParams(textParams);
                minus.setLayoutParams(planParams);
                percent.setLayoutParams(textParams);
                plus.setLayoutParams(planParams);

                double d = mLoadProfile.getDowDist().dowDist.get(dayIndex);
                int i = (int) Math.round(d);
                percent.setText(String.format("%d", i));
                percent.setInputType(InputType.TYPE_CLASS_NUMBER);
                percent.setEnabled(mEdit);
                int finalDayIndex = dayIndex;
                percent.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        mLoadProfile.getDowDist().dowDist.set(finalDayIndex, getDoubleOrZero(s));
                        System.out.println(day + "changed to : " + getDoubleOrZero(s));
                        ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                        updateMasterCopy();
                        totalPercent.setText(String.format("%d", calculateTotalPercentValue(mLoadProfile.getDowDist().dowDist)));
                    }
                });

                minus.setImageResource(android.R.drawable.btn_minus);
                minus.setBackgroundColor(0);
                minus.setScaleType(ImageView.ScaleType.FIT_CENTER);
                minus.setAdjustViewBounds(true);
                minus.setContentDescription(String.format("Reduce percentage for %s", day));
                plus.setImageResource(android.R.drawable.btn_plus);
                plus.setBackgroundColor(0);
                plus.setScaleType(ImageView.ScaleType.FIT_CENTER);
                plus.setContentDescription(String.format("Increase percentage for %s", day));
                plus.setAdjustViewBounds(true);

                minus.setOnClickListener(v -> {
                    int currentVal = (int) Math.round(Double.parseDouble(percent.getText().toString()));
                    currentVal--;
                    percent.setText(String.format("%d", currentVal));
                });

                plus.setOnClickListener(v -> {
                    int currentVal = (int) Math.round(Double.parseDouble(percent.getText().toString()));
                    currentVal++;
                    percent.setText(String.format("%d", currentVal));
                });

                dayRow.addView(dow);
                dayRow.addView(minus);
                dayRow.addView(percent);
                dayRow.addView(plus);
                distributionTable.addView(dayRow);
                dayIndex++;
            }
            {
                TextView tot = new TextView(getActivity());
                TableRow totalRow = new TableRow(getActivity());
                TextView minus = new TextView(getActivity());
                TextView plus = new TextView(getActivity());
                tot.setText(R.string.Total);
                tot.setGravity(Gravity.CENTER);
                minus.setText("");
                minus.setGravity(Gravity.CENTER);
                totalPercent.setText(String.format("%d", totalPercentValue));
                totalPercent.setGravity(Gravity.CENTER);
                totalPercent.setEnabled(false);
                plus.setText("");
                plus.setGravity(Gravity.CENTER);

                tot.setLayoutParams(textParams);
                minus.setLayoutParams(planParams);
                totalPercent.setLayoutParams(textParams);
                plus.setLayoutParams(planParams);

                totalRow.addView(tot);
                totalRow.addView(minus);
                totalRow.addView(totalPercent);
                totalRow.addView(plus);
                distributionTable.addView(totalRow);
            }
            mEditTable.addView(distributionTable);
        }
    }

    private int calculateTotalPercentValue(List<Double> dowDist) {
        double ret = 0;
        for (int i = 0; i < 7; i++){
            ret += dowDist.get(i);
        }
        return (int) Math.round(ret);
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        updateView();
    }

    private void updateMasterCopy() {
        String loadProfileJsonString = ((LoadProfileActivity) requireActivity()).getLoadProfileJson();
        Type type = new TypeToken<LoadProfileJson>(){}.getType();
        LoadProfileJson lpj = new Gson().fromJson(loadProfileJsonString, type);
        LoadProfile loadProfile = JsonTools.createLoadProfile(lpj);

        loadProfile.setDowDist(mLoadProfile.getDowDist());

        lpj = JsonTools.createLoadProfileJson(loadProfile);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String newLoadProfileJsonString =  gson.toJson(lpj, type);
        ((LoadProfileActivity) requireActivity()).setLoadProfileJson(newLoadProfileJsonString);
    }

    public void updateDistributionFromLeader() {
        String loadProfileJsonString = ((LoadProfileActivity) requireActivity()).getLoadProfileJson();
        Type type = new TypeToken<LoadProfileJson>(){}.getType();
        LoadProfileJson lpj = new Gson().fromJson(loadProfileJsonString, type);
        mLoadProfile = JsonTools.createLoadProfile(lpj);
    }
}