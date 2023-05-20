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

package com.tfcode.comparetout.scenario.battery;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BatteryChargingFragment extends Fragment {

    private int mBatteryScheduleIndex;
    private long mScenarioID;
    private List<LoadShift> mLoadShiftsFromActivity;
    private boolean mEdit;
    private List<View> mEditFields;
    private LoadShift mLoadShift;
    private TableLayout mInverterDate;
    private TableLayout mApplicableGrid;
    private TableLayout mLoadShiftTimes;

    private Handler mMainHandler;
    private ComparisonUIViewModel mViewModel;
    private List<Inverter> mInverters;

    public BatteryChargingFragment() {
        // Required empty public constructor
    }

    public static BatteryChargingFragment newInstance(int position) {
        BatteryChargingFragment inverterFragment = new BatteryChargingFragment();
        inverterFragment.mBatteryScheduleIndex = position;
        return inverterFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());

        // The activity may not be created, so these calls wait for the activity creation to complete
        ((BatteryChargingActivity) requireActivity()).getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            System.out.println(event.getTargetState());
            if ((event.getTargetState() ==  Lifecycle.State.CREATED ) && !(null == getActivity()) ) {
                mScenarioID = ((BatteryChargingActivity) requireActivity()).getScenarioID();
                mLoadShiftsFromActivity = ((BatteryChargingActivity) requireActivity()).getLoadShifts(mBatteryScheduleIndex);
                mEdit = ((BatteryChargingActivity) requireActivity()).getEdit();
                mEditFields = new ArrayList<>();
                unpackLoadShift();
                mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
                new Thread(() -> {
                    mInverters = mViewModel.getInvertersForScenario(mScenarioID);
                    mMainHandler.post(() -> {if (!(null == mInverterDate)) updateView();});
                }).start();
            }
        });
    }

    private void unpackLoadShift() {
        if (!(null == mLoadShiftsFromActivity) && !mLoadShiftsFromActivity.isEmpty())
            mLoadShift = mLoadShiftsFromActivity.get(0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_battery_charging, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mInverterDate = requireView().findViewById(R.id.batteryChargingEditTable);
        mInverterDate.setStretchAllColumns(true);
        mApplicableGrid = requireView().findViewById(R.id.scheduleApplicationTable);
        mApplicableGrid.setShrinkAllColumns(true);
        mApplicableGrid.setStretchAllColumns(true);
        mLoadShiftTimes = requireView().findViewById(R.id.scheduleDetailTable);
        mLoadShiftTimes.setShrinkAllColumns(true);
        mLoadShiftTimes.setStretchAllColumns(true);
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
            mLoadShiftsFromActivity = ((BatteryChargingActivity) requireActivity()).getLoadShifts(mBatteryScheduleIndex);
            unpackLoadShift();
            updateView();
        }
    }

    private void updateView() {
        System.out.println("Updating ChargingFragment " + mBatteryScheduleIndex + ", " + mEdit);
        mInverterDate.removeAllViews();
        mApplicableGrid.removeAllViews();
        mLoadShiftTimes.removeAllViews();

        if (!(null == getActivity()) && !(null == mLoadShift)) {

            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            params.topMargin = 2;
            params.rightMargin = 2;

            int integerType = InputType.TYPE_CLASS_NUMBER;
            int doubleType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

            // Inverter selection
            {
                TableRow inverterRow = new TableRow(getActivity());
                TextView inverterText = new TextView(getActivity());
                inverterText.setText(R.string.ConnectedInverterName);
                ArrayList<String> inverterSpinnerContent = new ArrayList<>();
                int selectedInverterIndex = 0;
                int itr = 0;
                Inverter initialInverter = null;
                if (!(null == mInverters)) for (Inverter inverter : mInverters) {
                    String inv = inverter.getInverterName();
                    inverterSpinnerContent.add(inv);
                    if (mLoadShift.getInverter().equals(inv)) {
                        selectedInverterIndex = itr;
                        initialInverter = inverter;
                    }
                    itr++;
                }
                else inverterSpinnerContent.add("Missing inverter");
                Spinner inverterSpinner = new Spinner(getActivity());
                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, inverterSpinnerContent);
                inverterSpinner.setAdapter(spinnerAdapter);
                inverterSpinner.setSelection(selectedInverterIndex);
                Inverter finalInitialInverter = initialInverter;
                inverterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String inverter = inverterSpinnerContent.get(position);
                        mLoadShift.setInverter(inverter);
                        ((BatteryChargingActivity) requireActivity()).updateLoadShiftAtIndex(mLoadShift, mBatteryScheduleIndex, 0);
                        if (null == finalInitialInverter)
                            ((BatteryChargingActivity) requireActivity()).setSaveNeeded(true);
                        else if (!finalInitialInverter.getInverterName().equals(mLoadShift.getInverter()))
                            ((BatteryChargingActivity) requireActivity()).setSaveNeeded(true);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Auto-generated method stub
                    }
                });
                inverterText.setLayoutParams(params);
                inverterSpinner.setPadding(20, 20, 20, 20);
                inverterSpinner.setEnabled(mEdit);
                mEditFields.add(inverterSpinner);
                inverterRow.addView(inverterText);
                inverterRow.addView(inverterSpinner);
                mInverterDate.addView(inverterRow);
            }

            // Month & Day buttons
            {
                TableRow buttons = new TableRow(getActivity());
                Button addButton = new Button(getActivity());
                addButton.setText(R.string.add_new_time);
                addButton.setOnClickListener(v -> {
                    LoadShift nLoadShift = new LoadShift();
                    nLoadShift.setInverter(mLoadShift.getInverter());
                    nLoadShift.getDays().ints = new ArrayList<>(mLoadShift.getDays().ints);
                    nLoadShift.getMonths().months = new ArrayList<>(mLoadShift.getMonths().months);
                    ((BatteryChargingActivity) requireActivity()).getNextAddedLoadShiftID(nLoadShift);
                    mLoadShiftsFromActivity.add(nLoadShift);
                    updateView();
                });
                addButton.setEnabled(mEdit);
                mEditFields.add(addButton);
                Button daysMonthsButton = new Button(getActivity());
                daysMonthsButton.setText(R.string.show_days_months);
                daysMonthsButton.setOnClickListener(v -> {
                    daysMonthsButton.setText(R.string.hide_days_months);
                    if (mApplicableGrid.getVisibility() == View.INVISIBLE) {
                        mApplicableGrid.setVisibility(View.VISIBLE);
                        ConstraintLayout constraintLayout = (ConstraintLayout) mApplicableGrid.getParent();
                        ConstraintSet constraintSet = new ConstraintSet();
                        constraintSet.clone(constraintLayout);
                        constraintSet.connect(R.id.scheduleDetailScroll, ConstraintSet.TOP, R.id.scheduleApplicationTable, ConstraintSet.BOTTOM,0);
                        constraintSet.applyTo(constraintLayout);
                    }
                    else {
                        daysMonthsButton.setText(R.string.show_days_months);
                        mApplicableGrid.setVisibility(View.INVISIBLE);
                        ConstraintLayout constraintLayout = (ConstraintLayout) mApplicableGrid.getParent();
                        ConstraintSet constraintSet = new ConstraintSet();
                        constraintSet.clone(constraintLayout);
                        constraintSet.connect(R.id.scheduleDetailScroll, ConstraintSet.TOP, R.id.batteryChargingEditTable, ConstraintSet.BOTTOM,0);
                        constraintSet.applyTo(constraintLayout);
                    }
                });

                buttons.addView(addButton);
                buttons.addView(daysMonthsButton);
                mInverterDate.addView(buttons);

                // Add a grid for the days/months that the Load shift applies to
                {
                    TableRow gridRow = new TableRow(getActivity());
                    List<String> gridItems = Arrays.asList(
                            "Mon", "Jan", "Jul",
                            "Tue", "Feb", "Aug",
                            "Wed", "Mar", "Sep",
                            "Thu", "Apr", "Oct",
                            "Fri", "May", "Nov",
                            "Sat", "Jun", "Dec",
                            "Sun");
                    int colNo = 0;
                    int rowNo = 0;
                    for (String title : gridItems) {
                        CheckBox cb = new CheckBox(getActivity());
                        cb.setPadding(10,10, 10, 10);
                        cb.setText(title);
                        switch (colNo) {
                            case 0:
                                cb.setChecked(mLoadShift.getDays().ints.contains(rowNo));
                                Integer finalRowNo = rowNo;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mLoadShift.getDays().ints.contains(finalRowNo))
                                            mLoadShift.getDays().ints.add(finalRowNo);
                                    }
                                    else
                                        mLoadShift.getDays().ints.remove(finalRowNo);
                                    ((BatteryChargingActivity) requireActivity()).updateLoadShiftAtIndex(mLoadShift, mBatteryScheduleIndex, 0);
                                });
                                break;
                            case 1:
                                cb.setChecked(mLoadShift.getMonths().months.contains(rowNo + 1));
                                Integer finalMonthCol1No = rowNo;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mLoadShift.getMonths().months.contains(finalMonthCol1No))
                                            mLoadShift.getMonths().months.add(finalMonthCol1No);
                                    }
                                    else
                                        mLoadShift.getMonths().months.remove(finalMonthCol1No);
                                    ((BatteryChargingActivity) requireActivity()).updateLoadShiftAtIndex(mLoadShift, mBatteryScheduleIndex, 0);
                                });
                                break;
                            case 2:
                                cb.setChecked(mLoadShift.getMonths().months.contains(rowNo + 7));
                                Integer finalMonthCol2No = rowNo + 7;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mLoadShift.getMonths().months.contains(finalMonthCol2No))
                                            mLoadShift.getMonths().months.add(finalMonthCol2No);
                                    }
                                    else
                                        mLoadShift.getMonths().months.remove(finalMonthCol2No);
                                    ((BatteryChargingActivity) requireActivity()).updateLoadShiftAtIndex(mLoadShift, mBatteryScheduleIndex, 0);
                                });
                                break;
                        }
                        cb.setEnabled(mEdit);
                        mEditFields.add(cb);
                        colNo++;
                        gridRow.addView(cb);
                        if (colNo == 3 || rowNo == 6) {
                            colNo = 0;
                            rowNo++;
                            mApplicableGrid.addView(gridRow);
                            gridRow = new TableRow(getActivity());
                        }
                    }
                }
            }

            // Add a row for each load shift in the tabGroup
            {
                TableRow titleRow = new TableRow(getActivity());
                titleRow.setPadding(0,40,0,0);
                TextView linkTitle = new TextView(getActivity());
                linkTitle.setText(R.string.linked);
                linkTitle.setGravity(Gravity.CENTER);
                titleRow.addView(linkTitle);
                TextView fromTitle = new TextView(getActivity());
                fromTitle.setText(R.string.FromHr);
                titleRow.addView(fromTitle);
                TextView toTitle = new TextView(getActivity());
                toTitle.setText(R.string.ToHr);
                titleRow.addView(toTitle);
                TextView stopTitle = new TextView(getActivity());
                stopTitle.setText(R.string.StopAt);
                titleRow.addView(stopTitle);
                TextView deleteTitle = new TextView(getActivity());
                deleteTitle.setText(R.string.Delete);
                deleteTitle.setGravity(Gravity.CENTER);
                titleRow.addView(deleteTitle);
                mLoadShiftTimes.addView(titleRow);

                if (!(null == mLoadShiftsFromActivity)) for (LoadShift loadShift: mLoadShiftsFromActivity) {
                    TableRow chargeRow = new TableRow(getActivity());
                    ImageButton linked = new ImageButton(getActivity());
                    linked.setImageResource(R.drawable.ic_baseline_link_24);
                    linked.setBackgroundColor(0);
                    EditText from = new EditText(getActivity());
                    EditText to = new EditText(getActivity());
                    EditText stop = new EditText(getActivity());
                    ImageButton delete = new ImageButton(getActivity());
                    delete.setImageResource(R.drawable.ic_baseline_delete_24);
                    delete.setBackgroundColor(0);
                    from.setMinimumHeight(80);
                    from.setHeight(80);

                    from.setText(String.valueOf(loadShift.getBegin()));
                    from.setInputType(integerType);
                    to.setText(String.valueOf(loadShift.getEnd()));
                    to.setInputType(integerType);
                    stop.setText(String.valueOf(loadShift.getStopAt()));
                    stop.setInputType(doubleType);

                    List<String> linkedScenarios = ((BatteryChargingActivity) requireActivity()).getLinkedScenarios(loadShift.getLoadShiftIndex());
                    if ((null == linkedScenarios) || linkedScenarios.isEmpty()) {
                        linked.setEnabled(false);
                        linked.setImageResource(R.drawable.ic_baseline_link_off_24);
                    }
                    else {
                        linked.setEnabled(true);
                        linked.setOnClickListener(view -> Snackbar.make(view, "Linked to " + linkedScenarios, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show());
                    }


                    from.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(loadShift.getBegin())))) {
                                System.out.println("Update the from");
                                loadShift.setBegin(getIntegerOrZero(s));
                                ((BatteryChargingActivity) requireActivity()).updateLoadShiftAtIndex(loadShift, mBatteryScheduleIndex, loadShift.getLoadShiftIndex());
                                ((BatteryChargingActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    to.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(loadShift.getEnd())))) {
                                System.out.println("Update the to");
                                loadShift.setEnd(getIntegerOrZero(s));
                                ((BatteryChargingActivity) requireActivity()).updateLoadShiftAtIndex(loadShift, mBatteryScheduleIndex, loadShift.getLoadShiftIndex());
                                ((BatteryChargingActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    stop.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(loadShift.getStopAt())))) {
                                System.out.println("Update the stop");
                                loadShift.setStopAt(getDoubleOrZero(s));
                                ((BatteryChargingActivity) requireActivity()).updateLoadShiftAtIndex(loadShift, mBatteryScheduleIndex, loadShift.getLoadShiftIndex());
                                ((BatteryChargingActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    delete.setOnClickListener(v -> {
                        from.setBackgroundColor(Color.RED);
                        to.setBackgroundColor(Color.RED);
                        stop.setBackgroundColor(Color.RED);
                        chargeRow.setBackgroundColor(Color.RED);
                        ((BatteryChargingActivity) requireActivity()).deleteLoadShiftAtIndex(loadShift, mBatteryScheduleIndex, loadShift.getLoadShiftIndex());
                        updateView();
                    });

                    from.setEnabled(mEdit);
                    mEditFields.add(from);
                    to.setEnabled(mEdit);
                    mEditFields.add(to);
                    stop.setEnabled(mEdit);
                    mEditFields.add(stop);
                    delete.setEnabled(mEdit);
                    mEditFields.add(delete);

                    chargeRow.addView(linked);
                    chargeRow.addView(from);
                    chargeRow.addView(to);
                    chargeRow.addView(stop);
                    chargeRow.addView(delete);

                    mLoadShiftTimes.addView(chargeRow);
                }
            }
        }
    }

    public void batteryDeleted(int newPosition) {
        System.out.println("Updating fragment index from " + mBatteryScheduleIndex + " to " + (newPosition));
        mBatteryScheduleIndex = newPosition;
        try {
            mLoadShiftsFromActivity = ((BatteryChargingActivity) requireActivity()).getLoadShifts(mBatteryScheduleIndex);
            unpackLoadShift();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            System.out.println("Fragment " + (mBatteryScheduleIndex + 1) + " was detached from activity during delete");
        }
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        for (View view: mEditFields) view.setEnabled(mEdit);
    }

    public void updateDBIndex() {
        if (!(null == mLoadShift)) {
            mLoadShift.setLoadShiftIndex(((BatteryChargingActivity) requireActivity()).getDatabaseID(mBatteryScheduleIndex));
            mMainHandler.post(this::updateView);
        }
    }

   
}