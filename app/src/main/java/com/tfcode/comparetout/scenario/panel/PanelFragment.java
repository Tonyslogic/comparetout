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

package com.tfcode.comparetout.scenario.panel;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.PanelJson;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelPVSummary;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PanelFragment extends Fragment {

    private int mPanelIndex;
    private long mScenarioID;
    private String mPanelJsonString;
    private boolean mEdit;
    private List<View> mEditFields;
    private Panel mPanel;
    private TableLayout mTableLayout;
    private BarChart mBarChart;
    private TableLayout mPanelNoData;
    private Handler mMainHandler;

    private ComparisonUIViewModel mViewModel;
    private List<PanelPVSummary> mPanelPVSummaries;
    private List<Inverter> mInverters;

    private final ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                System.out.println("mStartForResult " + result.getResultCode());
                if (result.getResultCode() == Activity.RESULT_OK) {
                    System.out.println("mStartForResult: RESULT_OK");
                    Intent intent = result.getData();
                    // Handle the Intent
                    if (!(null == intent)) {
                        System.out.println("mPanelDataChanged, RESULT = " + intent.getBooleanExtra("RESULT", false));
                        if (intent.getBooleanExtra("RESULT", false)) {
                            new Thread(() -> {
                                System.out.println("Deleting simulation data for " + mPanel.getPanelIndex());
                                mViewModel.deleteSimulationDataForPanelID(mPanel.getPanelIndex());
                                System.out.println("Deleting costing data for " + mPanel.getPanelIndex());
                                mViewModel.deleteCostingDataForPanelID(mPanel.getPanelIndex());
                            }).start();
                        }
                    }
                    else {
                        System.out.println("No idea if the panel data was updated. The intent did not return data");
                    }
                }
            });


    public PanelFragment() {
        // Required empty public constructor
    }

    public static PanelFragment newInstance(int position) {
        PanelFragment panelFragment = new PanelFragment();
        panelFragment.mPanelIndex = position;
        return panelFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());

        mScenarioID = ((PanelActivity) requireActivity()).getScenarioID();
        mPanelJsonString = ((PanelActivity) requireActivity()).getPanelJson();
        mEdit = ((PanelActivity) requireActivity()).getEdit();
        mEditFields = new ArrayList<>();
        unpackPanel();
        mPanel.setPanelIndex(((PanelActivity) requireActivity()).getDatabaseID(mPanelIndex));

        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getPanelDataSummary().observe(this, summaries -> {
            mPanelPVSummaries = summaries;
            updateChartView();
        });
        new Thread(() -> {
            mInverters = mViewModel.getInvertersForScenario(mScenarioID);
            mMainHandler.post(() -> {if (!(null == mTableLayout)) updateEditorView();});
        }).start();
    }

    private void unpackPanel() {
        Type type = new TypeToken<List<PanelJson>>(){}.getType();
        List<PanelJson> panelJson = new Gson().fromJson(mPanelJsonString, type);
        mPanel = JsonTools.createPanelList(panelJson).get(mPanelIndex);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_panel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = requireView().findViewById(R.id.panelEditTable);
        mTableLayout.setShrinkAllColumns(true);
        mTableLayout.setStretchAllColumns(true);

        mBarChart = requireView().findViewById(R.id.scenario_detail_chart);
        mPanelNoData  = requireView().findViewById(R.id.scenario_detail_filter_layout);
        updateView();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity()))
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public void refreshFocus() {
        if (isAdded()) {
            mPanelJsonString = ((PanelActivity) requireActivity()).getPanelJson();
            unpackPanel();
            updateView();
        }
    }

    private void updateView() {
        System.out.println("Updating PanelFragment " + mPanelIndex + ", " + mEdit);
        updateEditorView();
        updateChartView();
        updateDataControlView();
    }

    private void updateDataControlView() {
        mPanelNoData.removeAllViews();
        Button button = new Button(getActivity());
        button.setText(R.string.FetchUpdateData);
        button.setEnabled(mEdit);
        mEditFields.add(button);
        button.setOnClickListener(v -> {
            if (mPanel.getPanelIndex() != 0) {
                Intent intent = new Intent(getActivity(), PVGISActivity.class);
                intent.putExtra("PanelID", mPanel.getPanelIndex());
                intent.putExtra("Edit", mEdit);
//                startActivity(intent);
                mStartForResult.launch(intent);
            }
            else{
                if (!(null == getView())) Snackbar.make(getView(),
                    "New panel! Save and try again.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        mPanelNoData.addView(button);
    }

    private void updateChartView() {
        if (null == mPanelPVSummaries) return;
        System.out.println("updateChartView " + mPanelPVSummaries.size());


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
        List<Double> monthlyDist = new ArrayList<>();
        int monthIndex = 0;
        for (PanelPVSummary summary: mPanelPVSummaries) {
            if (summary.panelID == mPanel.getPanelIndex()){
                monthlyDist.add(summary.tot);
                monthIndex++;
                if (monthIndex == 12) break;
            }
        }
        if (monthlyDist.size() !=12) return;

        mBarChart.getAxisLeft().setTextColor(com.google.android.material.R.attr.colorPrimary); // left y-axis
        mBarChart.getAxisRight().setTextColor(com.google.android.material.R.attr.colorPrimary); // right y-axis
        mBarChart.getXAxis().setTextColor(com.google.android.material.R.attr.colorPrimary);
        mBarChart.getLegend().setTextColor(com.google.android.material.R.attr.colorPrimary);
        mBarChart.getDescription().setTextColor(com.google.android.material.R.attr.colorPrimary);

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
            set1 = new BarDataSet(entries, "Monthly PV generation");
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

    private void updateEditorView() {
        mTableLayout.removeAllViews();

        if (!(null == getActivity())) {

            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            params.topMargin = 2;
            params.rightMargin = 2;
            params.weight = 1;

            int integerType = InputType.TYPE_CLASS_NUMBER;
            int stringType = InputType.TYPE_CLASS_TEXT;

            // CREATE TABLE ROWS
            mTableLayout.addView(createRow("Panel name", mPanel.getPanelName(), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(mPanel.getPanelName()))) {
                        System.out.println("Panel name changed");
                        mPanel.setPanelName(s.toString());
                        ((PanelActivity) requireActivity()).updatePanelAtIndex(mPanel, mPanelIndex);
                        ((PanelActivity) requireActivity()).setSaveNeeded(true);
                    }
                }
            }, params, stringType));
            mTableLayout.addView(createRow("Panel count", String.valueOf(mPanel.getPanelCount()), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mPanel.getPanelCount())))) {
                        System.out.println("Inverter mppt changed");
                        mPanel.setPanelCount(getIntegerOrZero(s));
                        ((PanelActivity) requireActivity()).updatePanelAtIndex(mPanel, mPanelIndex);
                        ((PanelActivity) requireActivity()).setSaveNeeded(true);
                    }
                }
            }, params, integerType));
            mTableLayout.addView(createRow("Panel kWp", String.valueOf(mPanel.getPanelkWp()), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mPanel.getPanelkWp())))) {
                        System.out.println("Inverter mppt changed");
                        mPanel.setPanelkWp(getIntegerOrZero(s));
                        ((PanelActivity) requireActivity()).updatePanelAtIndex(mPanel, mPanelIndex);
                        ((PanelActivity) requireActivity()).setSaveNeeded(true);
                    }
                }
            }, params, integerType));

            TableRow optimizedRow = new TableRow(getActivity());
            TextView optimizedPanels = new TextView(getActivity());
            optimizedPanels.setText(R.string.Optimized);
            optimizedPanels.setMinimumHeight(80);
            optimizedPanels.setHeight(80);
            CheckBox optimizedCheck = new MaterialCheckBox(getActivity());
            optimizedCheck.setEnabled(mEdit);
            mEditFields.add(optimizedCheck);
            optimizedCheck.setChecked(mPanel.getConnectionMode() == Panel.OPTIMIZED);
            optimizedCheck.setOnClickListener(v -> {
                System.out.println("Selected optimized: " + v.getId() + " " + optimizedCheck.isChecked());
                if (optimizedCheck.isChecked()) mPanel.setConnectionMode(Panel.OPTIMIZED);
                else mPanel.setConnectionMode(Panel.PARALLEL);
                ((PanelActivity) requireActivity()).updatePanelAtIndex(mPanel, mPanelIndex);
                ((PanelActivity) requireActivity()).setSaveNeeded(true);
            });
            optimizedRow.addView(optimizedPanels);
            optimizedRow.addView(optimizedCheck);
            mTableLayout.addView(optimizedRow);

            TableRow inverterRow = new TableRow(getActivity());
            TableRow mpptRow = new TableRow(getActivity());
            TextView inverterText = new TextView(getActivity());
            inverterText.setText(R.string.ConnectedInverterName);
            inverterText.setMinimumHeight(80);
            inverterText.setHeight(80);
            TextView mpptText = new TextView(getActivity());
            mpptText.setText(R.string.ConnectedInverterMPPT);
            mpptText.setMinimumHeight(80);
            mpptText.setHeight(80);

            Spinner mpptSpinner = new Spinner(getActivity());
            mpptSpinner.setPadding(20, 20, 20, 20);
            ArrayList<String> mpptSpinnerContent = new ArrayList<>();

            ArrayList<String> inverterSpinnerContent = new ArrayList<>();
            int selectedInverterIndex = 0;
            int itr = 0;
            Inverter initialInverter = null;
            int initialMPPT = mPanel.getMppt();
            if (!(null == mInverters)) for (Inverter inverter : mInverters) {
                String inv = inverter.getInverterName();
                inverterSpinnerContent.add(inv);
                if (mPanel.getInverter().equals(inv)) {
                    selectedInverterIndex = itr;
                    initialInverter = inverter;
                }
                itr++;
            }
            else inverterSpinnerContent.add("Missing inverter");
            if (!(null == initialInverter)) for (int i = 0; i < initialInverter.getMpptCount(); i++)
                mpptSpinnerContent.add(String.valueOf(i + 1));
            else mpptSpinnerContent.add("1");


            mpptSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    int mppt = Integer.parseInt(mpptSpinnerContent.get(position));
                    mPanel.setMppt(mppt);
                    System.out.println("Setting MPPT to:" + mPanel.getMppt());
                    ((PanelActivity) requireActivity()).updatePanelAtIndex(mPanel, mPanelIndex);
                    if (initialMPPT != mPanel.getMppt())
                        ((PanelActivity) requireActivity()).setSaveNeeded(true);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Auto-generated method stub
                }
            });

            Spinner inverterSpinner = new Spinner(getActivity());
            inverterSpinner.setPadding(20, 20, 20, 20);

            ArrayAdapter<String> mpptSpinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, mpptSpinnerContent);
            mpptSpinner.setAdapter(mpptSpinnerAdapter);
            mpptSpinner.setSelection(mPanel.getMppt() - 1);

            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, inverterSpinnerContent);
            inverterSpinner.setAdapter(spinnerAdapter);
            inverterSpinner.setSelection(selectedInverterIndex);
            Inverter finalInitialInverter = initialInverter;
            inverterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    String inverter = inverterSpinnerContent.get(position);
                    mPanel.setInverter(inverter);
                    System.out.println("Setting Inverter to: " + mPanel.getInverter());
                    mpptSpinnerContent.clear();
                    for (int i = 0; i < mInverters.get(position).getMpptCount(); i++)
                        mpptSpinnerContent.add(String.valueOf(i + 1));
                    mpptSpinner.setSelection(mPanel.getMppt() - 1);
                    ((PanelActivity) requireActivity()).updatePanelAtIndex(mPanel, mPanelIndex);
                    if (null == finalInitialInverter)
                        ((PanelActivity) requireActivity()).setSaveNeeded(true);
                    else if (!finalInitialInverter.getInverterName().equals(mPanel.getInverter()))
                        ((PanelActivity) requireActivity()).setSaveNeeded(true);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // Auto-generated method stub
                }
            });
            inverterText.setLayoutParams(params);
            inverterSpinner.setLayoutParams(params);
            inverterSpinner.setEnabled(mEdit);
            mEditFields.add(inverterSpinner);
            inverterRow.addView(inverterText);
            inverterRow.addView(inverterSpinner);
            mTableLayout.addView(inverterRow);


            mpptText.setLayoutParams(params);
            mpptSpinner.setLayoutParams(params);
            mpptSpinner.setEnabled(mEdit);
            mEditFields.add(mpptSpinner);
            mpptRow.addView(mpptText);
            mpptRow.addView(mpptSpinner);
            mTableLayout.addView(mpptRow);
        }
    }

    private TableRow createRow(String title, String initialValue, AbstractTextWatcher action, TableRow.LayoutParams params, int inputType){
        TableRow tableRow = new TableRow(getActivity());
        TextView a = new TextView(getActivity());
        a.setText(title);
        a.setMinimumHeight(80);
        a.setHeight(80);
        EditText b = new EditText(getActivity());
        b.setText(initialValue);
        b.setEnabled(mEdit);
        b.addTextChangedListener(action);
        b.setInputType(inputType);
        mEditFields.add(b);

        a.setLayoutParams(params);
        b.setPadding(20, 20, 20, 20);
        tableRow.addView(a);
        tableRow.addView(b);
        return tableRow;
    }

    public void panelDeleted(int newPosition) {
        System.out.println("Updating fragment index from " + mPanelIndex + " to " + (newPosition));
        mPanelIndex = newPosition;
        try {
            mPanelJsonString = ((PanelActivity) requireActivity()).getPanelJson();
            unpackPanel();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            System.out.println("Fragment " + (mPanelIndex + 1) + " was detached from activity during delete");
        }
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        for (View view: mEditFields) view.setEnabled(mEdit);
    }

    public void updateDBIndex() {
        if (!(null == mPanel))
            mPanel.setPanelIndex(((PanelActivity) requireActivity()).getDatabaseID(mPanelIndex));
    }
}