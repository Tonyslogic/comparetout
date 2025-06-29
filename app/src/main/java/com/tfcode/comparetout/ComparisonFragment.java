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
import androidx.webkit.WebViewAssetLoader;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fragment providing comprehensive cost comparison visualization and analysis.
 * 
 * This fragment serves as the primary interface for viewing and analyzing energy cost
 * calculations across different scenarios and price plans. It presents complex financial
 * data through multiple visualization methods including sortable tables, pie charts,
 * and detailed breakdowns of cost components.
 * 
 * Key features:
 * - Dynamic table with sortable columns for different cost metrics
 * - Configurable column visibility to focus on specific cost components
 * - Interactive pie charts showing cost distribution and comparisons
 * - Responsive layout adaptation based on device orientation
 * - Export capabilities for analysis results
 * - Integrated help system with context-sensitive assistance
 * 
 * The fragment manages complex UI state including:
 * - Sort preferences across different cost metrics (net, buy, sell, etc.)
 * - Column visibility settings for customized data presentation
 * - Orientation-aware layout management for optimal data display
 * - Popup window management for charts and help content
 * 
 * Data presentation modes:
 * - Tabular comparison of all scenario/plan combinations
 * - Pie chart visualizations for cost breakdown analysis
 * - Filtering and sorting capabilities for focused analysis
 * - Cost component visibility toggles (buy/sell/net/bonuses/fixed)
 * 
 * The fragment implements reactive data binding with the ViewModel to ensure
 * real-time updates when underlying cost calculations change, providing users
 * with immediate feedback as they modify scenarios or price plans.
 */
public class ComparisonFragment extends Fragment {

    private TableLayout mTableLayout;
    private ImageButton mSortButton;
    private ImageButton mFilterButton;
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

    private PopupMenu mFilterPopup;
    private PopupMenu mShowPopup;

    private View mPopupView;
    private PopupWindow mPieChartWindow;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupHelpView;
    private PopupWindow mHelpWindow;

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

        if (!(null == getContext())) {
            mAssetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(getContext()))
                    .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(getContext()))
                    .build();
        }
        ComparisonUIViewModel mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mViewModel.getAllComparisons().observe(this, costings -> {
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
        mPopupHelpView = inflater.inflate(R.layout.popup_help, container);
        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mPieChartWindow = new PopupWindow(mPopupView, width, height, focusable);
        mHelpWindow = new PopupWindow(mPopupHelpView, width, height, focusable);

        return inflater.inflate(R.layout.fragment_comparison, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTableLayout = requireView().findViewById(R.id.comparisonTable);

        mSortButton = view.findViewById((R.id.compare_sort));
        mFilterButton = view.findViewById((R.id.compare_filter));

        updateControl();

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {getString(R.string.DataTabName), getString(R.string.main_activity_usage), getString(R.string.main_activity_costs), getString(R.string.main_activity_compare)};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();

        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(MainActivity.USAGE_FRAGMENT)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/scenarionav/tab.html");
            return true;});
        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(MainActivity.COSTS_FRAGMENT)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/plannav/tab.html");
            return true;});
        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(MainActivity.COMPARE_FRAGMENT)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/compare/tab.html");
            return true;});
        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(MainActivity.DATA_MANAGEMENT_FRAGMENT)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/datatab/tab.html");
            return true;});
    }

    private void updateControl() {

        if (null == mFilterPopup) {
            //Creating the instance of PopupMenu
            mFilterPopup = new PopupMenu(requireActivity(), mFilterButton, Gravity.CENTER_HORIZONTAL);
            mFilterPopup.getMenuInflater()
                    .inflate(R.menu.popup_menu_compare, mFilterPopup.getMenu());
            mFilterPopup.getMenu().findItem(R.id.scenario).setChecked(mShowScenario);
            mFilterPopup.getMenu().findItem(R.id.plan).setChecked(mShowPlan);
            mFilterPopup.getMenu().findItem(R.id.net).setChecked(mShowNet);
            mFilterPopup.getMenu().findItem(R.id.buy).setChecked(mShowBuy);
            mFilterPopup.getMenu().findItem(R.id.sell).setChecked(mShowSell);
            mFilterPopup.getMenu().findItem(R.id.bonus).setChecked(mShowBonus);
            mFilterPopup.getMenu().findItem(R.id.fixed).setChecked(mShowFixed);
        }

        mFilterButton.setOnClickListener(v -> {
            mFilterPopup.setOnMenuItemClickListener(item -> {
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
                // Keep the popup menu open
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
                item.setActionView(new View(getActivity()));
                item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return false;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        return false;
                    }
                });
                return false;
            });
            mFilterPopup.show();
        });

        if (null == mShowPopup) {
            mShowPopup = new PopupMenu(requireActivity(), mSortButton, Gravity.CENTER_HORIZONTAL);
            mShowPopup.getMenuInflater()
                    .inflate(R.menu.popup_sort_compare, mShowPopup.getMenu());
            mShowPopup.getMenu().findItem(R.id.scenario).setChecked((mSortBy.equals("SortBy: Scenario")));
            mShowPopup.getMenu().findItem(R.id.plan).setChecked((mSortBy.equals("SortBy: Plan")));
            mShowPopup.getMenu().findItem(R.id.net).setChecked((mSortBy.equals("SortBy: Net")));
            mShowPopup.getMenu().findItem(R.id.buy).setChecked((mSortBy.equals("SortBy: Buy")));
            mShowPopup.getMenu().findItem(R.id.sell).setChecked((mSortBy.equals("SortBy: Sell")));
            mShowPopup.getMenu().findItem(R.id.bonus).setChecked((mSortBy.equals("SortBy: Bonus")));
            mShowPopup.getMenu().findItem(R.id.fixed).setChecked((mSortBy.equals("SortBy: Fixed")));
        }

        mSortButton.setOnClickListener(v -> {
            mShowPopup.setOnMenuItemClickListener(item -> {
                int itemID = item.getItemId();
                if (itemID == R.id.scenario) mSortBy = "SortBy: Scenario";
                if (itemID == R.id.plan) mSortBy = "SortBy: Plan";
                if (itemID == R.id.net) mSortBy = "SortBy: Net";
                if (itemID == R.id.buy) mSortBy = "SortBy: Buy";
                if (itemID == R.id.sell) mSortBy = "SortBy: Sell";
                if (itemID == R.id.bonus) mSortBy = "SortBy: Bonus";
                if (itemID == R.id.fixed) mSortBy = "SortBy: Fixed";

                mShowPopup.getMenu().findItem(R.id.scenario).setChecked((mSortBy.equals("SortBy: Scenario")));
                mShowPopup.getMenu().findItem(R.id.plan).setChecked((mSortBy.equals("SortBy: Plan")));
                mShowPopup.getMenu().findItem(R.id.net).setChecked((mSortBy.equals("SortBy: Net")));
                mShowPopup.getMenu().findItem(R.id.buy).setChecked((mSortBy.equals("SortBy: Buy")));
                mShowPopup.getMenu().findItem(R.id.sell).setChecked((mSortBy.equals("SortBy: Sell")));
                mShowPopup.getMenu().findItem(R.id.bonus).setChecked((mSortBy.equals("SortBy: Bonus")));
                mShowPopup.getMenu().findItem(R.id.fixed).setChecked((mSortBy.equals("SortBy: Fixed")));

                updateView();

                return true;
            });
            mShowPopup.show();
        });

        mSortButton.setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/compare/sort.html");
            return true;});
        mFilterButton.setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/compare/filter.html");
            return true;});
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

            createRow("Usage", "Supplier:Plan", "Net(€)",
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
        tableRow.setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/compare/row.html");
            return true;});

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

        a.setMinimumHeight(80);
        a.setHeight(80);

        // SET PARAMS
        a.setLayoutParams(planParams);
        b.setLayoutParams(planParams);
        c.setLayoutParams(planParams);
        d.setLayoutParams(planParams);
        e.setLayoutParams(planParams);
        f.setLayoutParams(planParams);
        g.setLayoutParams(planParams);

        // SET PADDING
        a.setPadding(10, 20, 10, 10);
        b.setPadding(10, 20, 10, 10);
        c.setPadding(10, 20, 10, 10);
        d.setPadding(10, 20, 10, 10);
        e.setPadding(10, 20, 10, 10);
        f.setPadding(10, 20, 10, 10);
        g.setPadding(10, 20, 10, 10);

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

    private void showHelp(String url) {
        if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mHelpWindow.setHeight((int) (requireActivity().getWindow().getDecorView().getHeight()*0.6));
            mHelpWindow.setWidth((int) (requireActivity().getWindow().getDecorView().getWidth()));
        }
        else {
            mHelpWindow.setWidth((int) (requireActivity().getWindow().getDecorView().getWidth() * 0.6));
            mHelpWindow.setHeight((int) (requireActivity().getWindow().getDecorView().getHeight()));
        }
        mHelpWindow.showAtLocation(mTableLayout, Gravity.CENTER, 0, 0);
        WebView webView = mPopupHelpView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
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