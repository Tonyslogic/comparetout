package com.tfcode.comparetout;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.LoadProfileJson;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles;

import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ComparisonFragment extends Fragment {

    private ComparisonUIViewModel mViewModel;
    private TableLayout mTableLayout;
    private TableLayout mControlTableLayout;
    private List<Scenario> mScenarios;
    private List<PricePlan> mPricePlans;
    private List<Costings> mCostings;
    private String mSortBy = "Nett";

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
        return inflater.inflate(R.layout.fragment_comparison, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTableLayout = requireView().findViewById(R.id.comparisonTable);
        mTableLayout.setShrinkAllColumns(false);
        mTableLayout.setStretchAllColumns(true);
        mTableLayout.setColumnShrinkable(1, true);
        mTableLayout.setColumnStretchable(1, false);

        mControlTableLayout = requireView().findViewById(R.id.comparisonControl);
        updateControl();

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {"Prices", "Scenarios", "Compare"};
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
        a.setText("SortBy: ");
        Spinner spinner = new Spinner(getActivity());
        String[] fields = {"Scenario", "Plan", "Nett", "Buy", "Sell"};
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
                // TODO Auto-generated method stub
            }
        });
        tableRow.addView(a);
        tableRow.addView(spinner);
        mControlTableLayout.addView(tableRow);
    }

    private void updateView() {
        mTableLayout.removeAllViews();

        if (!(null == mCostings) && !(null == mPricePlans) && !(null == mScenarios) ) {
            createRow("Scenario", "Supplier:Plan", "Nett(€)", "Buy(€)","Sell(€)", true);
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
                    rows.add(row);
                }
            }
            // TODO sort rows {"Scenario", "Plan", "Nett", "Buy", "Sell"}
            rows.sort((row1, row2) -> {
                int ret = 0;
                if (mSortBy.equals("Scenario")) ret = row1.scenario.compareTo(row2.scenario);
                if (mSortBy.equals("Plan")) ret = row1.fullName.compareTo(row2.fullName);
                if (mSortBy.equals("Nett")) ret = row1.nett.compareTo(row2.nett);
                if (mSortBy.equals("Buy")) ret = row1.buy.compareTo(row2.buy);
                if (mSortBy.equals("Sell")) ret = row1.sell.compareTo(row2.sell);
                return ret;
            });
            for (Row row: rows){
                createRow(row.scenario, row.fullName, row.nett, row.buy, row.sell, false);
            }
        }
    }

    private void createRow(String a1, String b1, String c1, String d1, String e1, boolean title) {
        // Headers
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

        // ADD TEXTVIEW TO TABLEROW
        tableRow.addView(a);
        tableRow.addView(b);
        tableRow.addView(c);
        tableRow.addView(d);
        tableRow.addView(e);

        // ADD TABLEROW TO TABLELAYOUT
        mTableLayout.addView(tableRow);
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
}