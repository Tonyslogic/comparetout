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

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.icu.text.DecimalFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.scenario.ScenarioBarChartData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ScenarioYear extends Fragment {

    private Long mScenarioID = 0L;
    private ComparisonUIViewModel mViewModel;
    private Handler mMainHandler;

    private PopupMenu mPopup;
    private ImageButton mFilterButton;

    private BarChart mBarChart;
    private PieChart mPieChart;
    private TextView mTextView;

    private List<ScenarioBarChartData> mBarData;

    private static final String SHOW_LOAD = "SHOW_LOAD";
    private static final String SHOW_FEED = "SHOW_FEED";
    private static final String SHOW_BUY = "SHOW_BUY";
    private static final String SHOW_PV = "SHOW_PV";
    private static final String SHOW_PV2BAT = "SHOW_PV2BAT";
    private static final String SHOW_PV2LOAD = "SHOW_PV2LOAD";
    private static final String SHOW_GRID2BAT = "SHOW_GRID2BAT";
    private static final String SHOW_BAT2LOAD = "SHOW_BAT2LOAD";
    private static final String SHOW_EVSCHEDULE = "SHOW_EVSCHEDULE";
    private static final String SHOW_HWSCHEDULE = "SHOW_HWSCHEDULE";
    private static final String SHOW_EVDIVERT = "SHOW_EVDIVERT";
    private static final String SHOW_HWDIVERT = "SHOW_HWDIVERT";
    private static final String SHOW_SOC = "SHOW_SOC";
    private static final String SHOW_HWTEMPERATURE = "SHOW_HWTEMPERATURE";
    private static final String SCENARIO_ID = "SCENARIO_ID";

    private boolean mShowLoad = true;
    private boolean mShowFeed = true;
    private boolean mShowBuy;
    private boolean mShowPV = true;
    private boolean mShowPV2Bat;
    private boolean mShowPV2Load;
    private boolean mShowGrid2Battery;
    private boolean mShowBat2Load;
    private boolean mShowEVSchedule;
    private boolean mShowHWSchedule;
    private boolean mShowEVDivert;
    private boolean mShowHWDivert;
    private boolean mShowSOC = true;
    private boolean mShowHWTemperature;

    private int mBarFilterCount = 3;
    private int mLineFilterCount = 2;

    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    public ScenarioYear() {
        // Required empty public constructor
    }

    public static ScenarioYear newInstance() {
        return new ScenarioYear();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity())){
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            mOrientation = getActivity().getResources().getConfiguration().orientation;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_LOAD, mShowLoad);
        outState.putBoolean(SHOW_FEED, mShowFeed);
        outState.putBoolean(SHOW_BUY, mShowBuy);
        outState.putBoolean(SHOW_PV, mShowPV);
        outState.putBoolean(SHOW_PV2BAT, mShowPV2Bat);
        outState.putBoolean(SHOW_PV2LOAD, mShowPV2Load);
        outState.putBoolean(SHOW_GRID2BAT, mShowGrid2Battery);
        outState.putBoolean(SHOW_BAT2LOAD, mShowBat2Load);
        outState.putBoolean(SHOW_EVSCHEDULE, mShowEVSchedule);
        outState.putBoolean(SHOW_HWSCHEDULE, mShowHWSchedule);
        outState.putBoolean(SHOW_EVDIVERT, mShowEVDivert);
        outState.putBoolean(SHOW_HWDIVERT, mShowHWDivert);
        outState.putBoolean(SHOW_SOC, mShowSOC);
        outState.putBoolean(SHOW_HWTEMPERATURE, mShowHWTemperature);
        outState.putLong(SCENARIO_ID, mScenarioID);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mShowLoad = savedInstanceState.getBoolean(SHOW_LOAD);
            mShowFeed = savedInstanceState.getBoolean(SHOW_FEED);
            mShowBuy = savedInstanceState.getBoolean(SHOW_BUY);
            mShowPV = savedInstanceState.getBoolean(SHOW_PV);
            mShowPV2Bat = savedInstanceState.getBoolean(SHOW_PV2BAT);
            mShowPV2Load = savedInstanceState.getBoolean(SHOW_PV2LOAD);
            mShowGrid2Battery = savedInstanceState.getBoolean(SHOW_GRID2BAT);
            mShowBat2Load = savedInstanceState.getBoolean(SHOW_BAT2LOAD);
            mShowEVSchedule = savedInstanceState.getBoolean(SHOW_EVSCHEDULE);
            mShowHWSchedule = savedInstanceState.getBoolean(SHOW_HWSCHEDULE);
            mShowEVDivert = savedInstanceState.getBoolean(SHOW_EVDIVERT);
            mShowHWDivert = savedInstanceState.getBoolean(SHOW_HWDIVERT);
            mShowSOC = savedInstanceState.getBoolean(SHOW_SOC);
            mShowHWTemperature = savedInstanceState.getBoolean(SHOW_HWTEMPERATURE);

            mBarFilterCount = 0;
            if (mShowLoad) mBarFilterCount++;
            if (mShowFeed) mBarFilterCount++;
            if (mShowBuy) mBarFilterCount++;
            if (mShowPV) mBarFilterCount++;
            if (mShowPV2Bat) mBarFilterCount++;
            if (mShowGrid2Battery) mBarFilterCount++;
            if (mShowPV2Load) mBarFilterCount++;
            if (mShowBat2Load) mBarFilterCount++;
            if (mShowEVSchedule) mBarFilterCount++;
            if (mShowHWSchedule) mBarFilterCount++;
            if (mShowEVDivert) mBarFilterCount++;
            if (mShowHWDivert) mBarFilterCount++;

            mLineFilterCount = 0;
            if (mShowSOC) mLineFilterCount++;
            if (mShowHWTemperature) mLineFilterCount++;

            mScenarioID = savedInstanceState.getLong(SCENARIO_ID);
        }
        if (mScenarioID == 0) mScenarioID = ((ScenarioActivity) requireActivity()).getScenarioID();
        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getAllComparisons().observe(this, costings -> {
            System.out.println("Observed a change in the costings");
            updateKPIs();
        });
    }

    private void updateKPIs() {
        new Thread(() -> {
            mBarData = mViewModel.getYearBarData(mScenarioID);
            System.out.println("mBarData has " + mBarData.size() + " entries." + mViewModel.toString() + " : " + mScenarioID );
            mMainHandler.post(this::updateView);
        }).start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_senario_year, container, false);
    }

    @SuppressLint({"DefaultLocale", "DiscouragedApi"})
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMainHandler = new Handler(Looper.getMainLooper());
        mBarChart = view.findViewById((R.id.scenario_detail_chart));
        mPieChart = view.findViewById((R.id.pvDestinationsPie));
        mTextView = view.findViewById((R.id.no_simulation_data));
        mFilterButton = view.findViewById((R.id.filter));

        setupPopupFilterMenu();
    }

    private void setupPopupFilterMenu() {
        if (null == mPopup) {
            //Creating the instance of PopupMenu
            mPopup = new PopupMenu(requireActivity(), mFilterButton, Gravity.CENTER_HORIZONTAL);
            mPopup.getMenuInflater()
                    .inflate(R.menu.popup_menu_filter, mPopup.getMenu());
            mPopup.getMenu().findItem(R.id.load).setChecked(mShowLoad);
            mPopup.getMenu().findItem(R.id.feed).setChecked(mShowFeed);
            mPopup.getMenu().findItem(R.id.buy).setChecked(mShowBuy);
            mPopup.getMenu().findItem(R.id.pv).setChecked(mShowPV);
            mPopup.getMenu().findItem(R.id.pv2bat).setChecked(mShowPV2Bat);
            mPopup.getMenu().findItem(R.id.pv2load).setChecked(mShowPV2Load);
            mPopup.getMenu().findItem(R.id.bat2load).setChecked(mShowBat2Load);
            mPopup.getMenu().findItem(R.id.gridToBattery).setChecked(mShowGrid2Battery);
            mPopup.getMenu().findItem(R.id.evSchedule).setChecked(mShowEVSchedule);
            mPopup.getMenu().findItem(R.id.hwSchedule).setChecked(mShowHWSchedule);
            mPopup.getMenu().findItem(R.id.evDivert).setChecked(mShowEVDivert);
            mPopup.getMenu().findItem(R.id.hwSchedule).setChecked(mShowHWSchedule);
            mPopup.getMenu().findItem(R.id.soc).setVisible(false);
            mPopup.getMenu().findItem(R.id.hwTemp).setVisible(false);
        }

        mPopup.setOnMenuItemClickListener(item -> {
            item.setChecked(!item.isChecked());
            int itemID = item.getItemId();
            if (itemID == R.id.load) {
                mShowLoad = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.feed) {
                mShowFeed = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.buy) {
                mShowBuy = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.pv) {
                mShowPV = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.pv2bat) {
                mShowPV2Bat = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.gridToBattery) {
                mShowGrid2Battery = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.pv2load) {
                mShowPV2Load = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.bat2load) {
                mShowBat2Load = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.evSchedule) {
                mShowEVSchedule = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.hwSchedule) {
                mShowHWSchedule = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.evDivert) {
                mShowEVDivert = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.hwDivert) {
                mShowHWDivert = item.isChecked();
                mBarFilterCount = item.isChecked() ? mBarFilterCount + 1 : mBarFilterCount - 1;
            }
            if (itemID == R.id.soc) {
                mShowSOC = item.isChecked();
                mLineFilterCount = item.isChecked() ? mLineFilterCount + 1 : mLineFilterCount - 1;
            }
            if (itemID == R.id.hwTemp) {
                mShowHWTemperature = item.isChecked();
                mLineFilterCount = item.isChecked() ? mLineFilterCount + 1 : mLineFilterCount - 1;
            }

            updateKPIs();
            return false;
        });

        mFilterButton.setOnClickListener(v -> mPopup.show());
    }

    private void updateView() {
        System.out.println("updateView");
        boolean showText = false;
        if (!(null == mBarChart) && (!(null == mBarData)) && !mBarData.isEmpty()) {
            mBarChart.setVisibility(View.VISIBLE);
            mBarChart.clear();
            mBarChart.notifyDataSetChanged();
            mBarChart.invalidate();
            mTextView.setVisibility(View.INVISIBLE);

            final ArrayList<String> xLabel = new ArrayList<>();
            for (ScenarioBarChartData row : mBarData) {
                xLabel.add(String.valueOf(row.hour));
            }
            XAxis xAxis = mBarChart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    String ret = "-";
                    try {
                        ret = xLabel.get((int) value);
                    } catch (IndexOutOfBoundsException goober) {
                        // The notify/invalidate do not always execute before this
                        System.out.println("IndexOutOfBoundsException from clear/notify/invalidate being late");
                    }
                    return ret;
                }
            });

            mBarChart.getAxisLeft().setTextColor(Color.DKGRAY); // left y-axis
            mBarChart.getAxisRight().setTextColor(Color.DKGRAY); // right y-axis
            mBarChart.getXAxis().setTextColor(Color.DKGRAY);
            mBarChart.getLegend().setTextColor(Color.DKGRAY);
            mBarChart.getDescription().setTextColor(Color.DKGRAY);

            ArrayList<BarEntry> loadEntries = new ArrayList<>();
            ArrayList<BarEntry> feedEntries = new ArrayList<>();
            ArrayList<BarEntry> buyEntries = new ArrayList<>();
            ArrayList<BarEntry> pvEntries = new ArrayList<>();
            ArrayList<BarEntry> pv2BatteryEntries = new ArrayList<>();
            ArrayList<BarEntry> pv2LoadEntries = new ArrayList<>();
            ArrayList<BarEntry> grid2BatteryEntries = new ArrayList<>();
            ArrayList<BarEntry> battery2LoadEntries = new ArrayList<>();
            ArrayList<BarEntry> evScheduleEntries = new ArrayList<>();
            ArrayList<BarEntry> hwScheduleEntries = new ArrayList<>();
            ArrayList<BarEntry> evDivertEntries = new ArrayList<>();
            ArrayList<BarEntry> hwDivertEntries = new ArrayList<>();
            for (int i = 0; i < xLabel.size(); i++) {
                loadEntries.add(new BarEntry(i, (float) mBarData.get(i).load));
                feedEntries.add(new BarEntry(i, (float) mBarData.get(i).feed));
                buyEntries.add(new BarEntry(i, (float) mBarData.get(i).buy));
                pvEntries.add(new BarEntry(i, (float) mBarData.get(i).pv));
                pv2BatteryEntries.add(new BarEntry(i, (float) mBarData.get(i).pv2Battery));
                pv2LoadEntries.add(new BarEntry(i, (float) mBarData.get(i).pv2Load));
                grid2BatteryEntries.add(new BarEntry(i, (float) mBarData.get(i).grid2Battery));
                battery2LoadEntries.add(new BarEntry(i, (float) mBarData.get(i).battery2Load));
                evScheduleEntries.add(new BarEntry(i, (float) mBarData.get(i).evSchedule));
                hwScheduleEntries.add(new BarEntry(i, (float) mBarData.get(i).hwSchedule));
                evDivertEntries.add(new BarEntry(i, (float) mBarData.get(i).evDivert));
                hwDivertEntries.add(new BarEntry(i, (float) mBarData.get(i).hwDivert));
            }

            BarDataSet loadSet;
            BarDataSet feedSet;
            BarDataSet buySet;
            BarDataSet pvSet;
            BarDataSet pv2BatterySet;
            BarDataSet pv2LoadSet;
            BarDataSet grid2BatterySet;
            BarDataSet battery2LoadSet;
            BarDataSet evScheduleSet;
            BarDataSet hwScheduleSet;
            BarDataSet evDivertSet;
            BarDataSet hwDivertSet;

            loadSet = new BarDataSet(loadEntries, "Monthly load");
            loadSet.setColor(Color.BLUE);
            feedSet = new BarDataSet(feedEntries, "Monthly feed");
            feedSet.setColor(Color.YELLOW);
            buySet = new BarDataSet(buyEntries, "Monthly buy");
            buySet.setColor(Color.GREEN);
            pvSet = new BarDataSet(pvEntries, "Monthly PV");
            pvSet.setColor(Color.RED);
            pv2BatterySet = new BarDataSet(pv2BatteryEntries, "Monthly PV to battery");
            pv2BatterySet.setColor(Color.DKGRAY);
            pv2LoadSet = new BarDataSet(pv2LoadEntries, "Monthly PV to load");
            pv2LoadSet.setColor(Color.parseColor("#3ca567"));
            grid2BatterySet = new BarDataSet(grid2BatteryEntries, "Monthly grid to battery");
            grid2BatterySet.setColor(Color.parseColor("#aaaaaa"));
            battery2LoadSet = new BarDataSet(battery2LoadEntries, "Monthly battery to load");
            battery2LoadSet.setColor(Color.parseColor("#309967"));
            evScheduleSet = new BarDataSet(evScheduleEntries, "Monthly EV charging");
            evScheduleSet.setColor(Color.parseColor("#476567"));
            hwScheduleSet = new BarDataSet(hwScheduleEntries, "Monthly water heating");
            hwScheduleSet.setColor(Color.parseColor("#890567"));
            evDivertSet = new BarDataSet(evDivertEntries, "Monthly EV diversion");
            evDivertSet.setColor(Color.parseColor("#a35567"));
            hwDivertSet = new BarDataSet(hwDivertEntries, "Monthly hot water diversion");
            hwDivertSet.setColor(Color.parseColor("#ff5f67"));

            ArrayList<IBarDataSet> dataSets = new ArrayList<>();
            if (mShowLoad) dataSets.add(loadSet);
            if (mShowFeed) dataSets.add(feedSet);
            if (mShowBuy) dataSets.add(buySet);
            if (mShowPV) dataSets.add(pvSet);
            if (mShowPV2Bat) dataSets.add(pv2BatterySet);
            if (mShowPV2Load) dataSets.add(pv2LoadSet);
            if (mShowGrid2Battery) dataSets.add(grid2BatterySet);
            if (mShowBat2Load) dataSets.add(battery2LoadSet);
            if (mShowEVSchedule) dataSets.add(evScheduleSet);
            if (mShowHWSchedule) dataSets.add(hwScheduleSet);
            if (mShowEVDivert) dataSets.add(evDivertSet);
            if (mShowHWDivert) dataSets.add(hwDivertSet);

            BarData data = new BarData(dataSets);
            data.setValueTextSize(10f);
            data.setDrawValues(false);
            mBarChart.getDescription().setEnabled(false);
            mBarChart.setData(data);

            if (mBarFilterCount > 1) {
                //data
                float groupSpace = 0.04f;
                float barSpace; // x2 dataset
                float barWidth; // x2 dataset
                // (0.46 + 0.02) * 2 + 0.04 = 1.00 -> interval per "group"
                // (0.22 + 0.02) * 4 + 0.05
                // (barWidth + barSpace) * elementsInGroup + groupSpace = 1

                float section = 0.96f / (float) mBarFilterCount;
                barSpace = section - section / (float) mBarFilterCount;
                barWidth = section - barSpace;

                data.setBarWidth(barWidth);
                mBarChart.groupBars(0, groupSpace, barSpace);
            }
            mBarChart.invalidate();
            mBarChart.refreshDrawableState();
        }

        if (!(null == mBarChart) && mOrientation == Configuration.ORIENTATION_LANDSCAPE) {

            ConstraintLayout constraintLayout = (ConstraintLayout) mBarChart.getParent();
            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(constraintLayout);
            constraintSet.connect(R.id.scenario_detail_chart, ConstraintSet.BOTTOM, R.id.scenario_detail_filter_layout, ConstraintSet.TOP, 0);
            constraintSet.connect(R.id.scenario_detail_filter_layout, ConstraintSet.TOP, R.id.scenario_detail_chart, ConstraintSet.BOTTOM, 0);
            constraintSet.applyTo(constraintLayout);
        }
        else if (!(null == mPieChart) && (!(null == mBarData)) && !mBarData.isEmpty())  {
            mPieChart.getDescription().setEnabled(true);
            mPieChart.getDescription().setTextColor(Color.DKGRAY);
            mPieChart.setRotationEnabled(true);
            mPieChart.setDragDecelerationFrictionCoef(0.9f);
            mPieChart.setRotationAngle(0);
            mPieChart.setHighlightPerTapEnabled(true);
            mPieChart.setHoleColor(Color.parseColor("#000000"));

            ArrayList<PieEntry> pieEntries = new ArrayList<>();
            String label = "kWh";

            Map<String, Double> generationDestinationMap = new HashMap<>();

            double feed = 0D;
            double load = 0D;
            double evDivert = 0D;
            double hwDivert = 0D;
            double battery = 0D;
            double pv = 0D;
            for (ScenarioBarChartData row : mBarData) {
                feed += row.feed;
                load += row.load;
                evDivert += row.evDivert;
                hwDivert += row.hwDivert;
                battery += row.pv2Battery;
                pv += row.pv;
            }
            if (feed > 0) generationDestinationMap.put("Feed", feed);
            if (load > 0) generationDestinationMap.put("Load", load);
            if (evDivert > 0) generationDestinationMap.put("EV", evDivert);
            if (hwDivert > 0)generationDestinationMap.put("Water", hwDivert);
            if (battery > 0) generationDestinationMap.put("Battery", battery);

            mPieChart.getDescription().setText("PV (" + new DecimalFormat("0.00").format(pv)+ " kWh)");
            mPieChart.getDescription().setTextSize(16f);

            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(Color.parseColor("#304567"));
            colors.add(Color.parseColor("#309967"));
            colors.add(Color.parseColor("#476567"));
            colors.add(Color.parseColor("#890567"));
            colors.add(Color.parseColor("#a35567"));
            colors.add(Color.parseColor("#ff5f67"));
            colors.add(Color.parseColor("#3ca567"));
            colors.add(Color.parseColor("#aaaaaa"));

            for(String type: generationDestinationMap.keySet()){
                pieEntries.add(new PieEntry(Objects.requireNonNull(generationDestinationMap.get(type)).floatValue(), type));
            }
            PieDataSet pieDataSet = new PieDataSet(pieEntries,label);
            pieDataSet.setValueTextSize(12f);
            pieDataSet.setColors(colors);
            PieData pieData = new PieData(pieDataSet);
            pieData.setDrawValues(true);
            pieData.setValueFormatter(new PercentFormatter(mPieChart));
            mPieChart.getLegend().setTextColor(Color.DKGRAY);
            mPieChart.getLegend().setTextSize(12f);
            mPieChart.setData(pieData);
            mPieChart.setUsePercentValues(true);
            mPieChart.invalidate();
            mPieChart.setVisibility(View.VISIBLE);
        }
        else showText = true;

        if (showText) {
            if (mBarChart != null) {
                mBarChart.setVisibility(View.INVISIBLE);
            }
            if (mPieChart != null) {
                mPieChart.setVisibility(View.INVISIBLE);
            }
            mTextView.setVisibility(View.VISIBLE);
            mTextView.setText(R.string.NoChartData);
        }
    }
}
