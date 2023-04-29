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

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.costings.SubTotals;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.Scenario;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ComparisonFragment extends Fragment {

    private TableLayout mTableLayout;
    private TableLayout mControlTableLayout;
    private List<Scenario> mScenarios;
    private List<PricePlan> mPricePlans;
    private List<Costings> mCostings;
    private String mSortBy = "SortBy: Net";
    private static final String SORT_KEY = "SORT_KEY";

    private static final String SHOW_SCENARIO = "SHOW_SCENARIO";
    private static final String SHOW_PLAN = "SHOW_PLAN";
    private static final String SHOW_NET = "SHOW_NET";
    private static final String SHOW_BUY = "SHOW_BUY";
    private static final String SHOW_SELL = "SHOW_SELL";
    private static final String SHOW_BONUS = "SHOW_BONUS";
    private static final String SHOW_FIXED = "SHOW_FIXED";
    private boolean mShowScenario = true;
    private boolean mShowPlan = true;
    private boolean mShowNet = true;
    private boolean mShowBuy = true;
    private boolean mShowSell = true;
    private boolean mShowBonus = false;
    private boolean mShowFixed = false;
    private int mVisibleColCount = 5;

    private PopupMenu mPopup;

    private View mPopupView;
    private PopupWindow mPieChartWindow;

    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    public ComparisonFragment() {
        // Required empty public constructor
    }

    public static ComparisonFragment newInstance() {return new ComparisonFragment();}

    @Override
    public void onResume() {
        super.onResume();
        if (mVisibleColCount <= 5 && !(null == getActivity()))
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        else if (!(null == getActivity()))
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mOrientation = getActivity().getResources().getConfiguration().orientation;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SORT_KEY, mSortBy);
        outState.putBoolean(SHOW_SCENARIO, mShowScenario);
        outState.putBoolean(SHOW_PLAN, mShowPlan);
        outState.putBoolean(SHOW_NET, mShowNet);
        outState.putBoolean(SHOW_BUY, mShowBuy);
        outState.putBoolean(SHOW_SELL, mShowSell);
        outState.putBoolean(SHOW_BONUS, mShowBonus);
        outState.putBoolean(SHOW_FIXED, mShowFixed);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mSortBy = savedInstanceState.getString(SORT_KEY);
            mShowScenario = savedInstanceState.getBoolean(SHOW_SCENARIO);
            mShowPlan = savedInstanceState.getBoolean(SHOW_PLAN);
            mShowNet = savedInstanceState.getBoolean(SHOW_NET);
            mShowBuy = savedInstanceState.getBoolean(SHOW_BUY);
            mShowSell = savedInstanceState.getBoolean(SHOW_SELL);
            mShowBonus = savedInstanceState.getBoolean(SHOW_BONUS);
            mShowFixed = savedInstanceState.getBoolean(SHOW_FIXED);
            mVisibleColCount = 0;
            if (mShowScenario) mVisibleColCount++;
            if (mShowPlan) mVisibleColCount++;
            if (mShowNet) mVisibleColCount++;
            if (mShowSell) mVisibleColCount++;
            if (mShowBonus) mVisibleColCount++;
            if (mShowBuy) mVisibleColCount++;
            if (mShowFixed) mVisibleColCount++;
        }
        ComparisonUIViewModel mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mViewModel.getAllComparisons().observe(this, costings -> {
            System.out.println("Observed a change in live comparison data " + costings.size());
            mCostings = costings;
            updateView();
        });
        mViewModel.getAllScenarios().observe(this, scenarios -> {
            mScenarios = scenarios;
            updateView();
        });
        mViewModel.getAllPricePlans().observe(this, plans -> {
            mPricePlans = new ArrayList<>();
            mPricePlans.addAll(plans.keySet());
            updateView();
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        mPopupView = inflater.inflate(R.layout.popup_compare, container);
        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mPieChartWindow = new PopupWindow(mPopupView, width, height, focusable);

        return inflater.inflate(R.layout.fragment_comparison, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTableLayout = requireView().findViewById(R.id.comparisonTable);

        mControlTableLayout = requireView().findViewById(R.id.comparisonControl);
        updateControl();

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {getString(R.string.main_activity_usage), getString(R.string.main_activity_costs), getString(R.string.main_activity_compare)};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();
    }

    private void updateControl() {
        mControlTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;

        // CREATE TABLE ROWS
        TableRow tableRow = new TableRow(getActivity());
        TextView a = new TextView(getActivity());
        a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        a.setText(R.string.ComparisonFilterText);

        if (null == mPopup) {
            //Creating the instance of PopupMenu
            mPopup = new PopupMenu(requireActivity(), a, Gravity.CENTER_HORIZONTAL);
            mPopup.getMenuInflater()
                    .inflate(R.menu.popup_menu_compare, mPopup.getMenu());
            mPopup.getMenu().findItem(R.id.scenario).setChecked(mShowScenario);
            mPopup.getMenu().findItem(R.id.plan).setChecked(mShowPlan);
            mPopup.getMenu().findItem(R.id.net).setChecked(mShowNet);
            mPopup.getMenu().findItem(R.id.buy).setChecked(mShowBuy);
            mPopup.getMenu().findItem(R.id.sell).setChecked(mShowSell);
            mPopup.getMenu().findItem(R.id.bonus).setChecked(mShowBonus);
            mPopup.getMenu().findItem(R.id.fixed).setChecked(mShowFixed);
        }

        a.setOnClickListener(v -> {
            mPopup.setOnMenuItemClickListener(item -> {
                item.setChecked(!item.isChecked());
                if (item.isChecked())  mVisibleColCount++; else mVisibleColCount--;
                if (mVisibleColCount > 5 && (!(null == getActivity())))
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                else if (!(null == getActivity()))
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

                int itemID = item.getItemId();
                if (itemID == R.id.scenario) {
                    mTableLayout.setColumnCollapsed(0, !item.isChecked());
                    mShowScenario = item.isChecked();
                }
                if (itemID == R.id.plan) {
                    mTableLayout.setColumnCollapsed(1, !item.isChecked());
                    mShowPlan = item.isChecked();
                }
                if (itemID == R.id.net) {
                    mTableLayout.setColumnCollapsed(2, !item.isChecked());
                    mShowNet = item.isChecked();
                }
                if (itemID == R.id.buy) {
                    mTableLayout.setColumnCollapsed(3, !item.isChecked());
                    mShowBuy = item.isChecked();
                }
                if (itemID == R.id.sell) {
                    mTableLayout.setColumnCollapsed(4, !item.isChecked());
                    mShowSell = item.isChecked();
                }
                if (itemID == R.id.bonus) {
                    mTableLayout.setColumnCollapsed(5, !item.isChecked());
                    mShowBonus = item.isChecked();
                }
                if (itemID == R.id.fixed) {
                    mTableLayout.setColumnCollapsed(6, !item.isChecked());
                    mShowFixed = item.isChecked();
                }
                return true;
            });
            mPopup.show();
        });

        Spinner spinner = new Spinner(getActivity());
        String[] fields = {"SortBy: Scenario", "SortBy: Plan", "SortBy: Net", "SortBy: Buy",
                "SortBy: Sell", "SortBy: Bonus", "SortBy: Fixed"};
        ArrayList<String> spinnerContent = new ArrayList<>(Arrays.asList(fields));
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, spinnerContent);
        spinner.setAdapter(spinnerAdapter);
        int index = spinnerContent.indexOf(mSortBy);
        spinner.setSelection(index);

        tableRow.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSortBy = fields[position];
                updateView();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Auto-generated method stub
            }
        });
        tableRow.addView(a);
        tableRow.addView(spinner);
        mControlTableLayout.addView(tableRow);
    }

    private void updateView() {
        mTableLayout.removeAllViews();

        if (!(null == mCostings)  && (mCostings.size() > 0)
                && !(null == mPricePlans) && (mPricePlans.size() > 0)
                && !(null == mScenarios) && mScenarios.size() > 0) {
            mTableLayout.setShrinkAllColumns(false);
            mTableLayout.setStretchAllColumns(true);
            mTableLayout.setColumnShrinkable(1, true);
            mTableLayout.setColumnStretchable(1, false);

            mTableLayout.setColumnCollapsed(0, !mShowScenario);
            mTableLayout.setColumnCollapsed(1, !mShowPlan);
            mTableLayout.setColumnCollapsed(2, !mShowNet);
            mTableLayout.setColumnCollapsed(3, !mShowBuy);
            mTableLayout.setColumnCollapsed(4, !mShowSell);
            mTableLayout.setColumnCollapsed(5, !mShowBonus);
            mTableLayout.setColumnCollapsed(6, !mShowFixed);

            createRow("Scenario", "Supplier:Plan", "Net(€)",
                    "Buy(€)","Sell(€)", "Bonus(€)", "Fixed(€)", true, null);
            ArrayList<Row> rows = new ArrayList<>();
            for (Costings costing : mCostings) {
                Scenario scenario = findScenarioByID(costing.getScenarioID());
                PricePlan pricePlan = findPricePlanByID(costing.getPricePlanID());
                if ((null == pricePlan) || (null == scenario)) continue;
                if (pricePlan.isActive() && scenario.isActive()) {
                    Row row = new Row();
                    row.scenario = costing.getScenarioName();
                    row.fullName = costing.getFullPlanName();
                    DecimalFormat df = new DecimalFormat("#.00");
                    row.net = df.format(costing.getNet() /100);
                    row.buy = df.format(costing.getBuy() / 100);
                    row.sell = df.format(costing.getSell() / 100);
                    row.bonus = df.format(pricePlan.getSignUpBonus());
                    row.fixed = df.format(pricePlan.getStandingCharges());
                    row.subTotals = costing.getSubTotals();
                    rows.add(row);
                }
            }
            // sort rows {"Scenario", "Plan", "Net", "Buy", "Sell"}
            rows.sort((row1, row2) -> {
                int ret = 0;
                if (mSortBy.equals("SortBy: Scenario")) ret = row1.scenario.compareTo(row2.scenario);
                if (mSortBy.equals("SortBy: Plan")) ret = row1.fullName.compareTo(row2.fullName);
                try {
                    if (mSortBy.equals("SortBy: Net")) ret = Double.valueOf(row1.net).compareTo(Double.valueOf(row2.net));
                    if (mSortBy.equals("SortBy: Buy")) ret = Double.valueOf(row1.buy).compareTo(Double.valueOf(row2.buy));
                    if (mSortBy.equals("SortBy: Sell")) ret = Double.valueOf(row1.sell).compareTo(Double.valueOf(row2.sell));
                    if (mSortBy.equals("SortBy: Bonus")) ret = Double.valueOf(row1.bonus).compareTo(Double.valueOf(row2.bonus));
                    if (mSortBy.equals("SortBy: Fixed")) ret = Double.valueOf(row1.fixed).compareTo(Double.valueOf(row2.fixed));
                }catch (NumberFormatException nfe) {
                    if (mSortBy.equals("SortBy: Net")) ret = row1.net.compareTo(row2.net);
                    if (mSortBy.equals("SortBy: Buy")) ret = row1.buy.compareTo(row2.buy);
                    if (mSortBy.equals("SortBy: Sell")) ret = row1.sell.compareTo(row2.sell);
                    if (mSortBy.equals("SortBy: Bonus")) ret = row1.bonus.compareTo(row2.bonus);
                    if (mSortBy.equals("SortBy: Fixed")) ret = row1.fixed.compareTo(row2.fixed);
                }
                return ret;
            });
            for (Row row: rows){
                createRow(row.scenario, row.fullName, row.net, row.buy, row.sell, row.bonus, row.fixed, false, row.subTotals);
            }
        }
        else {

            mTableLayout.setShrinkAllColumns(true);
            mTableLayout.setStretchAllColumns(false);

            TextView help = new TextView(getActivity());
            help.setSingleLine(false);
            help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

            help.setText(R.string.EmptyComparisonsText);

            mTableLayout.addView(help);
        }
    }

    private void createRow(String scenarioName, String planName, String net, String buy, String sell,
                           String bonus, String fixed, boolean title, SubTotals subTotals) {

        TableRow tableRow;
        tableRow = new TableRow(getActivity());

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;

        TextView a = new TextView(getActivity());
        TextView b = new TextView(getActivity());
        TextView c = new TextView(getActivity());
        TextView d = new TextView(getActivity());
        TextView e = new TextView(getActivity());
        TextView f = new TextView(getActivity());
        TextView g = new TextView(getActivity());

        if (title) {
            a.setTypeface(a.getTypeface(), Typeface.BOLD);
            b.setTypeface(b.getTypeface(), Typeface.BOLD);
            c.setTypeface(c.getTypeface(), Typeface.BOLD);
            d.setTypeface(d.getTypeface(), Typeface.BOLD);
            e.setTypeface(e.getTypeface(), Typeface.BOLD);
            f.setTypeface(d.getTypeface(), Typeface.BOLD);
            g.setTypeface(e.getTypeface(), Typeface.BOLD);
        }
        else {
            tableRow.setOnClickListener(v -> showPieChart(subTotals, scenarioName, planName));
        }

        // SET PARAMS
        a.setLayoutParams(planParams);
        b.setLayoutParams(planParams);
        c.setLayoutParams(planParams);
        d.setLayoutParams(planParams);
        e.setLayoutParams(planParams);
        f.setLayoutParams(planParams);
        g.setLayoutParams(planParams);

        // SET BACKGROUND COLOR
//        a.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);
//        b.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);
//        c.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);
//        d.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);
//        e.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);
//        f.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);
//        g.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);

        // SET PADDING
        a.setPadding(10, 10, 10, 10);
        b.setPadding(10, 10, 10, 10);
        c.setPadding(10, 10, 10, 10);
        d.setPadding(10, 10, 10, 10);
        e.setPadding(10, 10, 10, 10);
        f.setPadding(10, 10, 10, 10);
        g.setPadding(10, 10, 10, 10);

        a.setText(scenarioName);
        b.setText(planName);
        c.setText(net);
        d.setText(buy);
        e.setText(sell);
        f.setText(bonus);
        g.setText(fixed);

        tableRow.addView(a);
        tableRow.addView(b);
        tableRow.addView(c);
        tableRow.addView(d);
        tableRow.addView(e);
        tableRow.addView(f);
        tableRow.addView(g);

        // ADD TABLEROW TO TABLELAYOUT
        mTableLayout.addView(tableRow);
    }

    private void showPieChart(SubTotals subTotals, String scenarioName, String planName) {
        if (mOrientation == Configuration.ORIENTATION_PORTRAIT)
            mPieChartWindow.setHeight((int) (requireActivity().getWindow().getDecorView().getHeight()*0.6));
        else
            mPieChartWindow.setWidth((int) (requireActivity().getWindow().getDecorView().getWidth()*0.6));
        mPieChartWindow.showAtLocation(mTableLayout, Gravity.CENTER, 0, 0);
        PieChart mPieChart = mPopupView.findViewById(R.id.price_breakdown);

        mPieChart.getDescription().setEnabled(true);
        mPieChart.getDescription().setText(scenarioName + ":" + planName);
        mPieChart.setRotationEnabled(true);
        mPieChart.setDragDecelerationFrictionCoef(0.9f);
        mPieChart.setRotationAngle(0);
        mPieChart.setHighlightPerTapEnabled(true);
        mPieChart.setHoleColor(Color.parseColor("#000000"));

        ArrayList<PieEntry> pieEntries = new ArrayList<>();
        String label = "Cost/kWh";

        Map<String, Double> priceUnitsMap = new HashMap<>();
        for (Double price : subTotals.getPrices()) {
            priceUnitsMap.put(price.toString() + "¢", subTotals.getSubTotalForPrice(price));
        }
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#304567"));
        colors.add(Color.parseColor("#309967"));
        colors.add(Color.parseColor("#476567"));
        colors.add(Color.parseColor("#890567"));
        colors.add(Color.parseColor("#a35567"));
        colors.add(Color.parseColor("#ff5f67"));
        colors.add(Color.parseColor("#3ca567"));

        for(String type: priceUnitsMap.keySet()){
            pieEntries.add(new PieEntry(Objects.requireNonNull(priceUnitsMap.get(type)).floatValue(), type));
        }
        PieDataSet pieDataSet = new PieDataSet(pieEntries,label);
        pieDataSet.setValueTextSize(12f);
        pieDataSet.setColors(colors);
        PieData pieData = new PieData(pieDataSet);
        pieData.setDrawValues(true);
        pieData.setValueFormatter(new PercentFormatter(mPieChart));
        mPieChart.setData(pieData);
        mPieChart.setUsePercentValues(true);
        mPieChart.invalidate();
    }

    private Scenario findScenarioByID(Long id) {
        if (null == mScenarios) return null;
        return mScenarios.stream().filter(s -> id.equals(s.getScenarioIndex())).findFirst().orElse(null);
    }

    private PricePlan findPricePlanByID(Long id) {
        if (null == mPricePlans) return null;
        return mPricePlans.stream().filter(s -> id.equals(s.getPricePlanIndex())).findFirst().orElse(null);
    }
}

class Row {
    String scenario;
    String fullName;
    String net;
    String buy;
    String sell;
    SubTotals subTotals;
    String bonus;
    String fixed;
}