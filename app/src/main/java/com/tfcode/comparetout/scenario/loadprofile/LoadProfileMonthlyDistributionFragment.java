package com.tfcode.comparetout.scenario.loadprofile;

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
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.LoadProfileJson;
import com.tfcode.comparetout.model.scenario.LoadProfile;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LoadProfileMonthlyDistributionFragment extends Fragment {
    private boolean mEdit = false;

    private PricePlanNavViewModel mViewModel;
    private HorizontalBarChart mBarChart;
    private TableLayout mEditTable;
    private Long mScenarioID;
    private LoadProfile mLoadProfile;

    public LoadProfileMonthlyDistributionFragment() {
        // Required empty public constructor
    }

    public static LoadProfileMonthlyDistributionFragment newInstance() {
        LoadProfileMonthlyDistributionFragment fragment = new LoadProfileMonthlyDistributionFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScenarioID = ((LoadProfileActivity) requireActivity()).getScenarioID();
        mEdit = ((LoadProfileActivity) requireActivity()).getEdit();
//        mEditFields = new ArrayList<>();
        mViewModel = new ViewModelProvider(requireActivity()).get(PricePlanNavViewModel.class);
        mViewModel.getLoadProfile(mScenarioID).observe(this, profile -> {
            if (!(null == profile)) {
                System.out.println("LPMDF Observed a change in live profile data " + profile.getId());
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
        return inflater.inflate(R.layout.fragment_load_profile_monthly_distribution, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBarChart = requireView().findViewById(R.id.monthly_distribution_chart);
        mEditTable = requireView().findViewById(R.id.load_profile_edit_monthly);
        if (!(null == mBarChart) && !(null == mLoadProfile)) updateView();
    }

    private void updateView() {
        System.out.println("Updating LoadProfileMonthlyDistributionFragment " + mEdit);
        if (!mEdit) {
            mEditTable.setVisibility(View.INVISIBLE);
            mBarChart.setVisibility(View.VISIBLE);
            final ArrayList<String> xLabel = new ArrayList<>();
            xLabel.add("Jan");
            xLabel.add("Feb");
            xLabel.add("Mar");
            xLabel.add("Apr");
            xLabel.add("May");
            xLabel.add("Jun");
            xLabel.add("Jul");
            xLabel.add("Aug");
            xLabel.add("Sep");
            xLabel.add("Oct");
            xLabel.add("Nov");
            xLabel.add("Dec");
            XAxis xAxis = mBarChart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return xLabel.get((int)value);
                }
            });
            xAxis.setLabelCount(12, false);
            List<Double> monthlyDist = mLoadProfile.getMonthlyDist().monthlyDist;

            ArrayList<BarEntry> entries = new ArrayList<>();
            for (int i = 0; i < 12; i++) entries.add(new BarEntry(i, monthlyDist.get(i).floatValue()));

            BarDataSet set1;

            if (mBarChart.getData() != null &&
                    mBarChart.getData().getDataSetCount() > 0) {
                set1 = (BarDataSet) mBarChart.getData().getDataSetByIndex(0);
                set1.setValues(entries);
                mBarChart.getData().notifyDataChanged();
                mBarChart.notifyDataSetChanged();
            } else {
                set1 = new BarDataSet(entries, "Monthly distribution");
                ArrayList<IBarDataSet> dataSets = new ArrayList<>();
                dataSets.add(set1);
                BarData data = new BarData(dataSets);
                data.setValueTextSize(10f);
                data.setBarWidth(0.9f);
                mBarChart.getDescription().setEnabled(false);
                mBarChart.setData(data);
            }
        }
        else {
            mEditTable.setVisibility(View.VISIBLE);
            mBarChart.setVisibility(View.INVISIBLE);
            mEditTable.removeAllViews();
            TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            planParams.topMargin = 2;
            planParams.rightMargin = 2;
            planParams.height = mEditTable.getHeight() / 13;
            planParams.weight = 1;
            // Distribution Table

            TableLayout distributionTable = new TableLayout(getActivity());
            distributionTable.setShrinkAllColumns(false);
            distributionTable.setColumnShrinkable(1, true);
            distributionTable.setColumnShrinkable(3, true);
            distributionTable.setStretchAllColumns(true);
            distributionTable.setColumnStretchable(1, false);
            distributionTable.setColumnStretchable(3, false);
            EditText totalPercent = new EditText(getActivity());
            int totalPercentValue = calculateTotalPercentValue(mLoadProfile.getMonthlyDist().monthlyDist);


            String[] months = {"January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"};
            int monthIndex = 0;
            for (String month : months) {
                TableRow monthRow = new TableRow(getActivity());
                TextView dow = new TextView(getActivity());
                ImageButton minus = new ImageButton(getActivity());
                EditText percent = new EditText(getActivity());
                ImageButton plus = new ImageButton(getActivity());
                dow.setText(month);
                dow.setGravity(Gravity.CENTER);

                monthRow.setLayoutParams(planParams);
                dow.setLayoutParams(planParams);
                minus.setLayoutParams(planParams);
                percent.setLayoutParams(planParams);
                plus.setLayoutParams(planParams);

                double d = mLoadProfile.getMonthlyDist().monthlyDist.get(monthIndex);
                int i = (int) Math.round(d);
                percent.setText("" + i );
                percent.setInputType(InputType.TYPE_CLASS_NUMBER);
                percent.setEnabled(mEdit);
                int finalDayIndex = monthIndex;
                percent.addTextChangedListener(new LocalTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        mLoadProfile.getMonthlyDist().monthlyDist.set(finalDayIndex, Double.parseDouble(s.toString()));
                        System.out.println(month + "changed to : " + Double.parseDouble(s.toString()));
                        ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                        updateMasterCopy();
                        totalPercent.setText("" + calculateTotalPercentValue(mLoadProfile.getMonthlyDist().monthlyDist));
                    }
                });

                minus.setImageResource(android.R.drawable.btn_minus);
                minus.setBackgroundColor(Color.WHITE);
                minus.setScaleType(ImageView.ScaleType.FIT_CENTER);
                minus.setAdjustViewBounds(true);
                plus.setImageResource(android.R.drawable.btn_plus);
                plus.setBackgroundColor(Color.WHITE);
                plus.setScaleType(ImageView.ScaleType.FIT_CENTER);
                plus.setAdjustViewBounds(true);

                minus.setOnClickListener(v -> {
                    int currentVal = (int) Math.round(Double.parseDouble(percent.getText().toString()));
                    currentVal--;
                    percent.setText("" + currentVal);
                });

                plus.setOnClickListener(v -> {
                    int currentVal = (int) Math.round(Double.parseDouble(percent.getText().toString()));
                    currentVal++;
                    percent.setText("" + currentVal);
                });

                monthRow.addView(dow);
                monthRow.addView(minus);
                monthRow.addView(percent);
                monthRow.addView(plus);
                distributionTable.addView(monthRow);
                monthIndex++;
            }
            {
                TextView tot = new TextView(getActivity());
                TableRow totalRow = new TableRow(getActivity());
                TextView minus = new TextView(getActivity());
                TextView plus = new TextView(getActivity());
                tot.setText("Total");
                tot.setGravity(Gravity.CENTER);
                minus.setText("");
                minus.setGravity(Gravity.CENTER);
                totalPercent.setText("" + totalPercentValue);
                totalPercent.setGravity(Gravity.CENTER);
                totalPercent.setEnabled(false);
                plus.setText("");
                plus.setGravity(Gravity.CENTER);

                tot.setLayoutParams(planParams);
                minus.setLayoutParams(planParams);
                totalPercent.setLayoutParams(planParams);
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

    private int calculateTotalPercentValue(List<Double> monthlyDist) {
        double ret = 0;
        for (int i = 0; i < 12; i++){
            ret += monthlyDist.get(i);
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

        loadProfile.setMonthlyDist(mLoadProfile.getMonthlyDist());

        lpj = JsonTools.createLoadProfileJson(loadProfile);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String newLoadProfileJsonString =  gson.toJson(lpj, type);
        ((LoadProfileActivity) requireActivity()).setLoadProfileJson(newLoadProfileJsonString);
    }
}