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

package com.tfcode.comparetout.scenario.water;

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

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WaterScheduleFragment extends Fragment {
    
    private int mHWScheduleIndex;
    private List<HWSchedule> mHWSchedulesFromActivity;
    private boolean mEdit;
    private List<View> mEditFields;
    private HWSchedule mHWSchedule;
    private TableLayout mDateTableLayout;
    private TableLayout mApplicableGrid;
    private TableLayout mHWScheduleTimes;

    private Handler mMainHandler;

    public WaterScheduleFragment() {
        // Required empty public constructor
    }

    public static WaterScheduleFragment newInstance(int position) {
        WaterScheduleFragment waterScheduleFragment = new WaterScheduleFragment();
        waterScheduleFragment.mHWScheduleIndex = position;
        return waterScheduleFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());

        // The activity may not be created, so these calls wait for the activity creation to complete
        requireActivity().getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if ((event.getTargetState() ==  Lifecycle.State.CREATED ) && !(null == getActivity()) ) {
                mHWSchedulesFromActivity = ((WaterScheduleActivity) requireActivity()).getHWSchedules(mHWScheduleIndex);
                mEdit = ((WaterScheduleActivity) requireActivity()).getEdit();
                mEditFields = new ArrayList<>();
                unpackHWSchedule();
                if (!(null == mDateTableLayout)) updateView();
            }
        });
    }

    private void unpackHWSchedule() {
        if (!(null == mHWSchedulesFromActivity) && !mHWSchedulesFromActivity.isEmpty())
            mHWSchedule = mHWSchedulesFromActivity.get(0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_water_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mDateTableLayout = requireView().findViewById(R.id.waterScheduleEditTable);
        mDateTableLayout.setStretchAllColumns(true);
        mApplicableGrid = requireView().findViewById(R.id.scheduleApplicationTable);
        mApplicableGrid.setShrinkAllColumns(true);
        mApplicableGrid.setStretchAllColumns(true);
        mHWScheduleTimes = requireView().findViewById(R.id.scheduleDetailTable);
        mHWScheduleTimes.setShrinkAllColumns(true);
        mHWScheduleTimes.setStretchAllColumns(true);
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
            mHWSchedulesFromActivity = ((WaterScheduleActivity) requireActivity()).getHWSchedules(mHWScheduleIndex);
            unpackHWSchedule();
            updateView();
        }
    }

    private void updateView() {
        mDateTableLayout.removeAllViews();
        mApplicableGrid.removeAllViews();
        mHWScheduleTimes.removeAllViews();

        if (!(null == getActivity()) && !(null == mHWSchedule)) {

            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            params.topMargin = 2;
            params.rightMargin = 2;

            int integerType = InputType.TYPE_CLASS_NUMBER;

            // Month & Day buttons
            TableLayout buttonTableLayout = new TableLayout(getActivity());
            buttonTableLayout.setStretchAllColumns(true);
            {
                TableRow buttons = new TableRow(getActivity());
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
                        constraintSet.connect(R.id.scheduleDetailScroll, ConstraintSet.TOP, R.id.waterScheduleEditTable, ConstraintSet.BOTTOM,0);
                        constraintSet.applyTo(constraintLayout);
                    }
                });

                buttons.addView(daysMonthsButton);
                buttonTableLayout.addView(buttons);
                mDateTableLayout.addView(buttonTableLayout);

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
                        cb.setPadding(0,25,0,25);
                        cb.setText(title);
                        switch (colNo) {
                            case 0:
                                cb.setChecked(mHWSchedule.getDays().ints.contains(rowNo));
                                Integer finalRowNo = rowNo;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mHWSchedule.getDays().ints.contains(finalRowNo))
                                            mHWSchedule.getDays().ints.add(finalRowNo);
                                    }
                                    else
                                        mHWSchedule.getDays().ints.remove(finalRowNo);
                                    ((WaterScheduleActivity) requireActivity()).updateHWScheduleAtIndex(mHWSchedule, mHWScheduleIndex, 0);
                                    ((WaterScheduleActivity) requireActivity()).setSaveNeeded(true);
                                });
                                break;
                            case 1:
                                cb.setChecked(mHWSchedule.getMonths().months.contains(rowNo + 1));
                                Integer finalMonthCol1No = rowNo;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mHWSchedule.getMonths().months.contains(finalMonthCol1No))
                                            mHWSchedule.getMonths().months.add(finalMonthCol1No);
                                    }
                                    else
                                        mHWSchedule.getMonths().months.remove(finalMonthCol1No);
                                    ((WaterScheduleActivity) requireActivity()).updateHWScheduleAtIndex(mHWSchedule, mHWScheduleIndex, 0);
                                    ((WaterScheduleActivity) requireActivity()).setSaveNeeded(true);
                                });
                                break;
                            case 2:
                                cb.setChecked(mHWSchedule.getMonths().months.contains(rowNo + 7));
                                Integer finalMonthCol2No = rowNo + 7;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mHWSchedule.getMonths().months.contains(finalMonthCol2No))
                                            mHWSchedule.getMonths().months.add(finalMonthCol2No);
                                    }
                                    else
                                        mHWSchedule.getMonths().months.remove(finalMonthCol2No);
                                    ((WaterScheduleActivity) requireActivity()).updateHWScheduleAtIndex(mHWSchedule, mHWScheduleIndex, 0);
                                    ((WaterScheduleActivity) requireActivity()).setSaveNeeded(true);
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
                TextView deleteTitle = new TextView(getActivity());
                deleteTitle.setText(R.string.Delete);
                deleteTitle.setGravity(Gravity.CENTER);
                titleRow.addView(deleteTitle);
                mHWScheduleTimes.addView(titleRow);

                if (!(null == mHWSchedulesFromActivity)) for (HWSchedule hwSchedule: mHWSchedulesFromActivity) {
                    TableRow chargeRow = new TableRow(getActivity());
                    ImageButton linked = new ImageButton(getActivity());
                    linked.setImageResource(R.drawable.ic_baseline_link_24);
                    linked.setContentDescription("Hot water schedule is linked");
                    linked.setBackgroundColor(0);
                    EditText from = new EditText(getActivity());
                    EditText to = new EditText(getActivity());
                    ImageButton delete = new ImageButton(getActivity());
                    delete.setImageResource(R.drawable.ic_baseline_delete_24);
                    delete.setContentDescription("Delete this hot water schedule");
                    delete.setBackgroundColor(0);
                    from.setMinimumHeight(80);
                    from.setHeight(80);
                    to.setMinimumHeight(80);
                    to.setHeight(80);
                    
                    from.setText(String.valueOf(hwSchedule.getBegin()));
                    from.setInputType(integerType);
                    to.setText(String.valueOf(hwSchedule.getEnd()));
                    to.setInputType(integerType);
                    
                    List<String> linkedScenarios = ((WaterScheduleActivity) requireActivity()).getLinkedScenarios(hwSchedule.getHwScheduleIndex());
                    if ((null == linkedScenarios) || linkedScenarios.isEmpty()) {
                        linked.setEnabled(false);
                        linked.setImageResource(R.drawable.ic_baseline_link_off_24);
                        linked.setContentDescription("No linked load shifts");
                    }
                    else {
                        linked.setEnabled(true);
                        linked.setOnClickListener(view -> Snackbar.make(view, "Linked to " + linkedScenarios, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show());
                    }


                    from.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(hwSchedule.getBegin())))) {
                                hwSchedule.setBegin(getIntegerOrZero(s));
                                ((WaterScheduleActivity) requireActivity()).updateHWScheduleAtIndex(hwSchedule, mHWScheduleIndex, hwSchedule.getHwScheduleIndex());
                                ((WaterScheduleActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    to.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(hwSchedule.getEnd())))) {
                                hwSchedule.setEnd(getIntegerOrZero(s));
                                ((WaterScheduleActivity) requireActivity()).updateHWScheduleAtIndex(hwSchedule, mHWScheduleIndex, hwSchedule.getHwScheduleIndex());
                                ((WaterScheduleActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    delete.setOnClickListener(v -> {
                        from.setBackgroundColor(Color.RED);
                        to.setBackgroundColor(Color.RED);
                        chargeRow.setBackgroundColor(Color.RED);
                        ((WaterScheduleActivity) requireActivity()).deleteHWScheduleAtIndex(hwSchedule, mHWScheduleIndex, hwSchedule.getHwScheduleIndex());
                        updateView();
                    });

                    from.setEnabled(mEdit);
                    mEditFields.add(from);
                    to.setEnabled(mEdit);
                    mEditFields.add(to);
                    delete.setEnabled(mEdit);
                    mEditFields.add(delete);

                    chargeRow.addView(linked);
                    chargeRow.addView(from);
                    chargeRow.addView(to);
                    chargeRow.addView(delete);

                    mHWScheduleTimes.addView(chargeRow);
                }
            }

            // Add an add row
            if (mEdit){
                TableRow addRow = new TableRow(getActivity());
                addRow.setBackgroundResource(R.drawable.row_border);
                ImageButton linked = new ImageButton(getActivity());
                linked.setImageResource(R.drawable.ic_baseline_link_off_24);
                linked.setContentDescription("No linked hot water schedules");
                linked.setEnabled(false);
                linked.setBackgroundColor(0);
                EditText from = new EditText(getActivity());
                EditText to = new EditText(getActivity());
                ImageButton add = new ImageButton(getActivity());
                add.setImageResource(android.R.drawable.ic_menu_add);
                add.setContentDescription("Add a new hot water schedule");
                add.setBackgroundColor(0);
                from.setMinimumHeight(80);
                from.setHeight(80);
                to.setMinimumHeight(80);
                to.setHeight(80);

                from.setEnabled(mEdit);
                mEditFields.add(from);
                to.setEnabled(mEdit);
                mEditFields.add(to);
                add.setEnabled(mEdit);
                mEditFields.add(add);

                from.setText("2");
                from.setInputType(integerType);
                to.setText("6");
                to.setInputType(integerType);

                add.setOnClickListener(v -> {
                    HWSchedule nHWSchedule = new HWSchedule();
                    nHWSchedule.getDays().ints = new ArrayList<>(mHWSchedule.getDays().ints);
                    nHWSchedule.getMonths().months = new ArrayList<>(mHWSchedule.getMonths().months);
                    nHWSchedule.setBegin(Integer.parseInt(from.getText().toString()));
                    nHWSchedule.setEnd(Integer.parseInt(to.getText().toString()));
                    ((WaterScheduleActivity) requireActivity()).getNextAddedHWScheduleID(nHWSchedule);
                    mHWSchedulesFromActivity.add(nHWSchedule);
                    ((WaterScheduleActivity) requireActivity()).setSaveNeeded(true);
                    updateView();
                });

                addRow.addView(linked);
                addRow.addView(from);
                addRow.addView(to);
                addRow.addView(add);

                mHWScheduleTimes.addView(addRow);
            }
        }
    }

    public void scheduleDeleted(int newPosition) {
        mHWScheduleIndex = newPosition;
        try {
            mHWSchedulesFromActivity = ((WaterScheduleActivity) requireActivity()).getHWSchedules(mHWScheduleIndex);
            unpackHWSchedule();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            System.out.println("Fragment " + (mHWScheduleIndex + 1) + " was detached from activity during delete");
        }
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        for (View view: mEditFields) view.setEnabled(mEdit);
        updateView();
    }

    public void updateDBIndex() {
        if (!(null == mHWSchedule)) {
            mHWSchedule.setHwScheduleIndex(((WaterScheduleActivity) requireActivity()).getDatabaseID(mHWScheduleIndex));
            mMainHandler.post(this::updateView);
        }
    }

}