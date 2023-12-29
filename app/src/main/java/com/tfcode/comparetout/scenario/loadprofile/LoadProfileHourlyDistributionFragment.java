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

import android.os.Handler;
import android.os.Looper;
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
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoadProfileHourlyDistributionFragment extends Fragment {
    private boolean mEdit = false;

    private BarChart mBarChart;
    private TableLayout mEditTable;
    private Integer mEditTableHeight = null;
    private LoadProfile mLoadProfile;

    public LoadProfileHourlyDistributionFragment() {
        // Required empty public constructor
    }

    public static LoadProfileHourlyDistributionFragment newInstance() {
        LoadProfileHourlyDistributionFragment fragment = new LoadProfileHourlyDistributionFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
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
                updateMasterCopy();
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
        return inflater.inflate(R.layout.fragment_load_profile_hourly_distribution, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBarChart = requireView().findViewById(R.id.hourly_distribution_chart);
        mEditTable = requireView().findViewById(R.id.load_profile_edit_hourly);
//        mFrameLayout = requireView().findViewById(R.id.fl_hourly_distribution);
        if (!(null == mBarChart) && !(null == mLoadProfile)) updateView();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
//        if (!(null == getActivity()))
//            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @SuppressLint("DefaultLocale")
    private void updateView() {
        if (null == mEditTableHeight) mEditTableHeight = mEditTable.getHeight();
        if (!mEdit) {
            mEditTable.setVisibility(View.INVISIBLE);
            mBarChart.setVisibility(View.VISIBLE);
            final String[] xLabels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",
                    "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", };
            final ArrayList<String> xLabel = new ArrayList<>(Arrays.asList(xLabels));
            XAxis xAxis = mBarChart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return xLabel.get((int)value);
                }
            });
            List<Double> hourlyDist = mLoadProfile.getHourlyDist().dist;

            mBarChart.getAxisLeft().setTextColor(Color.DKGRAY); // left y-axis
            mBarChart.getAxisRight().setTextColor(Color.DKGRAY); // right y-axis
            mBarChart.getXAxis().setTextColor(Color.DKGRAY);
            mBarChart.getLegend().setTextColor(Color.DKGRAY);
            mBarChart.getDescription().setTextColor(Color.DKGRAY);

            ArrayList<BarEntry> entries = new ArrayList<>();
            for (int i = 23; i > -1; i--) entries.add(new BarEntry(i, hourlyDist.get(i).floatValue()));

            BarDataSet set1;

            if (mBarChart.getData() != null &&
                    mBarChart.getData().getDataSetCount() > 0) {
                set1 = (BarDataSet) mBarChart.getData().getDataSetByIndex(0);
                set1.setValues(entries);
                mBarChart.getData().notifyDataChanged();
                mBarChart.notifyDataSetChanged();
            } else {
                set1 = new BarDataSet(entries, "Hourly distribution");
                ArrayList<IBarDataSet> dataSets = new ArrayList<>();
                dataSets.add(set1);
                BarData data = new BarData(dataSets);
                data.setValueTextSize(10f);
                data.setBarWidth(0.5f);
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

            // HOURLY Percentages
            DoubleHolder doubleHolder = new DoubleHolder();
            doubleHolder.doubles = mLoadProfile.getHourlyDist().dist;
            int percentageTotal = calculatePercentageTotal(doubleHolder);
            HourlyPercentageRange hourlyPercentageRange = new HourlyPercentageRange(doubleHolder);

            TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            planParams.topMargin = 2;
            planParams.rightMargin = 2;

            TableRow.LayoutParams textParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
            textParams.topMargin = 2;
            textParams.rightMargin = 2;

            if (hourlyPercentageRange.getPercentages().size() > 9)
                planParams.height = mEditTableHeight / Math.min(12, hourlyPercentageRange.getPercentages().size());

            // PERCENTAGES Table
            TableLayout distributionTable = new TableLayout(getActivity());
            distributionTable.setShrinkAllColumns(false);
            distributionTable.setColumnShrinkable(2, true);
            distributionTable.setColumnShrinkable(4, true);
            distributionTable.setColumnShrinkable(5, true);
            distributionTable.setColumnShrinkable(6, true);
            distributionTable.setStretchAllColumns(true);
            distributionTable.setColumnStretchable(2, false);
            distributionTable.setColumnStretchable(4, false);
            distributionTable.setColumnStretchable(5, false);
            distributionTable.setColumnStretchable(6, false);

            // TITLES
            {
                TableRow titleRow = new TableRow(getActivity());
                TextView a = new TextView(getActivity());
                TextView b = new TextView(getActivity());
                TextView c = new TextView(getActivity());
                TextView d = new TextView(getActivity());
                TextView e = new TextView(getActivity());
                TextView f = new TextView(getActivity());
                TextView g = new TextView(getActivity());

                a.setText(R.string.From);
                b.setText(R.string.To);
                c.setText("-");
                d.setText("%");
                e.setText("+");
                f.setText(R.string.Del);
                g.setText(R.string.Split);

                a.setGravity(Gravity.CENTER);
                b.setGravity(Gravity.CENTER);
                c.setGravity(Gravity.CENTER);
                d.setGravity(Gravity.CENTER);
                e.setGravity(Gravity.CENTER);
                f.setGravity(Gravity.CENTER);
                g.setGravity(Gravity.CENTER);

                a.setLayoutParams(textParams);
                b.setLayoutParams(textParams);
                c.setLayoutParams(textParams);
                d.setLayoutParams(textParams);
                e.setLayoutParams(textParams);
                f.setLayoutParams(textParams);
                g.setLayoutParams(textParams);

                titleRow.addView(a);
                titleRow.addView(b);
                titleRow.addView(c);
                titleRow.addView(d);
                titleRow.addView(e);
                titleRow.addView(f);
                titleRow.addView(g);

                distributionTable.addView(titleRow);
            }

            // PERCENTAGES
            EditText totalPercent = new EditText(getActivity());

            for (HourlyPercentage hourlyPercentage: hourlyPercentageRange.getPercentages()){
                TableRow percentRow = new TableRow(getActivity());
                EditText from = new EditText(getActivity());
                EditText to = new EditText(getActivity());
                ImageButton minus = new ImageButton(getActivity());
                EditText percent = new EditText(getActivity());
                ImageButton plus = new ImageButton(getActivity());
                ImageButton del = new ImageButton(getActivity());
                ImageButton add = new ImageButton(getActivity());

                from.setLayoutParams(textParams);
                to.setLayoutParams(textParams);
                minus.setLayoutParams(planParams);
                percent.setLayoutParams(textParams);
                plus.setLayoutParams(planParams);
                del.setLayoutParams(planParams);
                add.setLayoutParams(planParams);

                int end = Math.round(hourlyPercentage.getEnd());
                int begin = Math.round(hourlyPercentage.getBegin());
                int percentDouble = (int) Math.round(hourlyPercentage.getPercentage()) * (end - begin);

                from.setText(String.format("%d", begin));
                from.setInputType(InputType.TYPE_CLASS_NUMBER);
                from.setEnabled(mEdit);
//                from.setMinimumWidth(80);
//                from.setWidth(80);
                from.setPadding(20,25, 20, 25);

                to.setText(String.format("%d", end));
                to.setInputType(InputType.TYPE_CLASS_NUMBER);
                to.setEnabled(mEdit);
//                to.setMinimumWidth(80);
//                to.setWidth(80);
                to.setPadding(20,25, 20, 25);

                percent.setText(String.format("%d", percentDouble));
                percent.setInputType(InputType.TYPE_CLASS_NUMBER);
                percent.setEnabled(mEdit);
//                percent.setMinimumWidth(80);
//                percent.setWidth(80);
                percent.setPadding(20,25, 20, 25);

                minus.setImageResource(android.R.drawable.btn_minus);
                minus.setBackgroundColor(0);
                minus.setScaleType(ImageView.ScaleType.FIT_CENTER);
                minus.setAdjustViewBounds(true);
                minus.setContentDescription(String.format("Reduce percentage for %s to %s", begin, end));
                plus.setImageResource(android.R.drawable.btn_plus);
                plus.setBackgroundColor(0);
                plus.setScaleType(ImageView.ScaleType.FIT_CENTER);
                plus.setAdjustViewBounds(true);
                plus.setContentDescription(String.format("Increase percentage for %s to %s", begin, end));
                del.setImageResource(R.drawable.ic_baseline_delete_24);
                del.setContentDescription(String.format("Delete percentage for %s to %s", begin, end));
                del.setBackgroundColor(0);
                del.setScaleType(ImageView.ScaleType.FIT_CENTER);
                add.setImageResource(android.R.drawable.ic_menu_add);
                add.setContentDescription(String.format("Split row for %s to %s", begin, end));
                add.setBackgroundColor(0);

                from.setEnabled(false);

                to.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        try {
                            int t = Integer.parseInt(s.toString());
                            int fr = Integer.parseInt(from.getText().toString());
                            int p = Integer.parseInt((percent.getText().toString()));
                            doubleHolder.update(fr, t, (double) p/(t-fr));
                            HourlyDist hd = new HourlyDist();
                            hd.dist = doubleHolder.doubles;
                            mLoadProfile.setHourlyDist(hd);
                            if(mEdit) ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                            updateMasterCopy();
                            totalPercent.setText(String.format("%d", calculatePercentageTotal(doubleHolder)));
                        } catch (NumberFormatException nfe) {
                            nfe.printStackTrace();
                        }
                    }
                });
                to.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {new Handler(Looper.getMainLooper()).postDelayed(this::updateView, 200);}});

                percent.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        try {
                            int p = getIntegerOrZero(s);
                            int fr = Integer.parseInt(from.getText().toString());
                            int t = Integer.parseInt((to.getText().toString()));
                            doubleHolder.update(fr, t, (double) p/(t-fr));
                            HourlyDist hd = new HourlyDist();
                            hd.dist = doubleHolder.doubles;
                            mLoadProfile.setHourlyDist(hd);
                            if(mEdit)((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                            updateMasterCopy();
                            totalPercent.setText(String.format("%d", calculatePercentageTotal(doubleHolder)));
                        } catch (NumberFormatException nfe) {
                            nfe.printStackTrace();
                        }
                    }
                });

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

                del.setOnClickListener(v -> {
                    if (hourlyPercentageRange.getPercentages().size() > 1) {
                        int fr = Integer.parseInt(from.getText().toString());
                        int t = Integer.parseInt((to.getText().toString()));
                        double p = Integer.parseInt((percent.getText().toString()));
                        if (fr > 0)
                            p = hourlyPercentageRange.lookup(fr-1);
                        doubleHolder.update(fr, t, p);
                        HourlyDist dist = new HourlyDist();
                        dist.dist = doubleHolder.doubles;
                        mLoadProfile.setHourlyDist(dist);
                        if(mEdit)((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                        updateMasterCopy();
                        totalPercent.setText(String.format("%d", calculatePercentageTotal(doubleHolder)));
                        new Handler(Looper.getMainLooper()).postDelayed(this::updateView, 200);
                    }
                });

                add.setOnClickListener(v -> {
                    int fr = Integer.parseInt(from.getText().toString());
                    int t = Integer.parseInt((to.getText().toString()));
                    if (t - fr > 1) {
                        double p = Integer.parseInt((percent.getText().toString()));
                        int dif = t - fr;
                        int split = fr + dif / 2;
                        doubleHolder.update(fr, split, (p / 2 - 1) / (split - fr));
                        doubleHolder.update(split, t, (p / 2 + 1) / (t - split));
                        HourlyDist dist = new HourlyDist();
                        dist.dist = doubleHolder.doubles;
                        mLoadProfile.setHourlyDist(dist);
                        if (mEdit) ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                        updateMasterCopy();
                        totalPercent.setText(String.format("%d", calculatePercentageTotal(doubleHolder)));
                        new Handler(Looper.getMainLooper()).postDelayed(this::updateView, 200);
                    }
                });

                percentRow.addView(from);
                percentRow.addView(to);
                percentRow.addView(minus);
                percentRow.addView(percent);
                percentRow.addView(plus);
                percentRow.addView(del);
                percentRow.addView(add);

                distributionTable.addView(percentRow);
            }

            // TOTAL
            {
                TableRow titleRow = new TableRow(getActivity());
                TextView from = new TextView(getActivity());
                TextView to = new TextView(getActivity());
                TextView minus = new TextView(getActivity());
                TextView plus = new TextView(getActivity());
                TextView del = new TextView(getActivity());
                TextView add = new TextView(getActivity());

                from.setText("");
                to.setText("");
                minus.setText(R.string.Total);
                totalPercent.setText(String.format("%d", percentageTotal));
                totalPercent.setPadding(0,20, 0, 20);
                plus.setText("");
                del.setText("");
                add.setText("");

                from.setLayoutParams(planParams);
                to.setLayoutParams(planParams);
                minus.setLayoutParams(planParams);
                totalPercent.setLayoutParams(planParams);
                plus.setLayoutParams(planParams);
                del.setLayoutParams(planParams);
                add.setLayoutParams(planParams);

                from.setGravity(Gravity.CENTER);
                to.setGravity(Gravity.CENTER);
                minus.setGravity(Gravity.CENTER);
                totalPercent.setGravity(Gravity.CENTER);
                plus.setGravity(Gravity.CENTER);
                del.setGravity(Gravity.CENTER);
                add.setGravity(Gravity.CENTER);

                totalPercent.setEnabled(false);

                titleRow.addView(from);
                titleRow.addView(to);
                titleRow.addView(minus);
                titleRow.addView(totalPercent);
                titleRow.addView(plus);
                titleRow.addView(del);
                titleRow.addView(add);

                distributionTable.addView(titleRow);
            }
            mEditTable.addView(distributionTable);
        }
    }

    private int calculatePercentageTotal(DoubleHolder dh) {
        double ret = 0;
        for (int i = 0; i < 24; i++){
            ret += dh.doubles.get(i);
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

        loadProfile.setHourlyDist(mLoadProfile.getHourlyDist());

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