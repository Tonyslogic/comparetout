package com.tfcode.comparetout;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import com.google.android.material.snackbar.Snackbar;
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

    private ComparisonUIViewModel mViewModel;
    private TableLayout mTableLayout;
    private TableLayout mControlTableLayout;
    private List<Scenario> mScenarios;
    private List<PricePlan> mPricePlans;
    private List<Costings> mCostings;
    private String mSortBy = "SortBy: Nett";

    private PopupMenu mPopup;

    private View mPopupView;
    private PopupWindow mPieChartWindow;

    public ComparisonFragment() {
        // Required empty public constructor
    }

    public static ComparisonFragment newInstance() {return new ComparisonFragment();}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
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
        a.setText("Show columns... ");

        if (null == mPopup) {
            //Creating the instance of PopupMenu
            mPopup = new PopupMenu(requireActivity(), a, Gravity.CENTER_HORIZONTAL);
            mPopup.getMenuInflater()
                    .inflate(R.menu.popup_menu_compare, mPopup.getMenu());
        }

        a.setOnClickListener(v -> {
            mPopup.setOnMenuItemClickListener(item -> {
                item.setChecked(!item.isChecked());

                switch (item.getItemId()){
                    case R.id.scenario:
                        mTableLayout.setColumnCollapsed(0, !item.isChecked());
                        break;
                    case R.id.plan:
                        mTableLayout.setColumnCollapsed(1, !item.isChecked());
                        break;
                    case R.id.nett:
                        mTableLayout.setColumnCollapsed(2, !item.isChecked());
                        break;
                    case R.id.buy:
                        mTableLayout.setColumnCollapsed(3, !item.isChecked());
                        break;
                    case R.id.sell:
                        mTableLayout.setColumnCollapsed(4, !item.isChecked());
                        break;
                }
                return true;
            });
            mPopup.show();
        });

        Spinner spinner = new Spinner(getActivity());
        String[] fields = {"SortBy: Scenario", "SortBy: Plan", "SortBy: Nett", "SortBy: Buy", "SortBy: Sell"};
        ArrayList<String> spinnerContent = new ArrayList<>(Arrays.asList(fields));
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, spinnerContent);
        spinner.setAdapter(spinnerAdapter);
        int index = spinnerContent.indexOf(mSortBy);
        spinner.setSelection(index);

        tableRow.setBackgroundColor(Color.LTGRAY);

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

            createRow("Scenario", "Supplier:Plan", "Nett(€)", "Buy(€)","Sell(€)", true, null);
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
                    row.nett = df.format(costing.getNett() /100);
                    row.buy = df.format(costing.getBuy() / 100);
                    row.sell = df.format(costing.getSell() / 100);
                    row.subTotals = costing.getSubTotals();
                    rows.add(row);
                }
            }
            // sort rows {"Scenario", "Plan", "Nett", "Buy", "Sell"}
            rows.sort((row1, row2) -> {
                int ret = 0;
                if (mSortBy.equals("SortBy: Scenario")) ret = row1.scenario.compareTo(row2.scenario);
                if (mSortBy.equals("SortBy: Plan")) ret = row1.fullName.compareTo(row2.fullName);
                if (mSortBy.equals("SortBy: Nett")) ret = row1.nett.compareTo(row2.nett);
                if (mSortBy.equals("SortBy: Buy")) ret = row1.buy.compareTo(row2.buy);
                if (mSortBy.equals("SortBy: Sell")) ret = row1.sell.compareTo(row2.sell);
                return ret;
            });
            for (Row row: rows){
                createRow(row.scenario, row.fullName, row.nett, row.buy, row.sell, false, row.subTotals);
            }
        }
        else {

            mTableLayout.setShrinkAllColumns(true);
            mTableLayout.setStretchAllColumns(false);

            TextView help = new TextView(getActivity());
            help.setSingleLine(false);
            help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

            help.setText(new StringBuilder()
                    .append("Price comparisons will appear here. \n\n")
                    .append("Select usage(s) and costs from the other tabs to control the content of this tab\n\n")
                    .append("You can sort the table, and select the fields to show.\n\n")
                    .append("Tilting the screen (landscape) will better fit more fields.\n\n")
                    .append("Progress of recently added costs and usages can be seen in the notification area.\n\n")
                    .append("Selecting a comparison will pop up a usage graph (pie chart) that shows how much electricity was purchased at each rate").toString());

            mTableLayout.addView(help);
        }
    }

    private void createRow(String a1, String b1, String c1, String d1, String e1, boolean title, SubTotals subTotals) {

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

        if (title) {
            a.setTypeface(a.getTypeface(), Typeface.BOLD);
            b.setTypeface(b.getTypeface(), Typeface.BOLD);
            c.setTypeface(c.getTypeface(), Typeface.BOLD);
            d.setTypeface(d.getTypeface(), Typeface.BOLD);
            e.setTypeface(e.getTypeface(), Typeface.BOLD);
        }
        else {
            tableRow.setOnClickListener(v -> showPieChart(subTotals));
        }

        // SET PARAMS
        a.setLayoutParams(planParams);
        b.setLayoutParams(planParams);
        c.setLayoutParams(planParams);
        d.setLayoutParams(planParams);
        e.setLayoutParams(planParams);

        // SET BACKGROUND COLOR
        a.setBackgroundColor(Color.WHITE);
        b.setBackgroundColor(Color.WHITE);
        c.setBackgroundColor(Color.WHITE);
        d.setBackgroundColor(Color.WHITE);
        e.setBackgroundColor(Color.WHITE);

        // SET PADDING
        a.setPadding(10, 10, 10, 10);
        b.setPadding(10, 10, 10, 10);
        c.setPadding(10, 10, 10, 10);
        d.setPadding(10, 10, 10, 10);
        e.setPadding(10, 10, 10, 10);

        a.setText(a1);
        b.setText(b1);
        c.setText(c1);
        d.setText(d1);
        e.setText(e1);

        tableRow.addView(a);
        tableRow.addView(b);
        tableRow.addView(c);
        tableRow.addView(d);
        tableRow.addView(e);

        // ADD TABLEROW TO TABLELAYOUT
        mTableLayout.addView(tableRow);
    }

    private void showPieChart(SubTotals subTotals) {
        mPieChartWindow.setHeight((int) (requireActivity().getWindow().getDecorView().getHeight()*0.6));
        mPieChartWindow.showAtLocation(mTableLayout, Gravity.CENTER, 0, 0);
        PieChart mPieChart = mPopupView.findViewById(R.id.price_breakdown);

        mPieChart.getDescription().setEnabled(false);
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
        return mScenarios.stream().filter(s -> id.equals(s.getId())).findFirst().orElse(null);
    }

    private PricePlan findPricePlanByID(Long id) {
        if (null == mPricePlans) return null;
        return mPricePlans.stream().filter(s -> id.equals(s.getId())).findFirst().orElse(null);
    }
}

class Row {
    String scenario;
    String fullName;
    String nett;
    String buy;
    String sell;
    SubTotals subTotals;
}