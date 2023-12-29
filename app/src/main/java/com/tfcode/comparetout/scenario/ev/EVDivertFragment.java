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

package com.tfcode.comparetout.scenario.ev;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EVDivertFragment extends Fragment {

    private int mEVDivertIndex;
    private List<EVDivert> mEVDivertsFromActivity;
    private boolean mEdit;
    private List<View> mEditFields;
    private EVDivert mEVDivert;
    private TableLayout mPropertiesTableLayout;
    private TableLayout mApplicableGrid;
    private TableLayout mEVDivertTimes;

    private Handler mMainHandler;

    public EVDivertFragment() {
        // Required empty public constructor
    }

    public static EVDivertFragment newInstance(int position) {
        EVDivertFragment evDivertFragment = new EVDivertFragment();
        evDivertFragment.mEVDivertIndex = position;
        return evDivertFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());

        // The activity may not be created, so these calls wait for the activity creation to complete
        ((EVDivertActivity) requireActivity()).getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if ((event.getTargetState() ==  Lifecycle.State.CREATED ) && !(null == getActivity()) ) {
                mEVDivertsFromActivity = ((EVDivertActivity) requireActivity()).getEVDiverts(mEVDivertIndex);
                mEdit = ((EVDivertActivity) requireActivity()).getEdit();
                mEditFields = new ArrayList<>();
                unpackEVDivert();
                if (!(null == mPropertiesTableLayout)) updateView();
            }
        });
    }

    private void unpackEVDivert() {
        if (!(null == mEVDivertsFromActivity) && !mEVDivertsFromActivity.isEmpty())
            mEVDivert = mEVDivertsFromActivity.get(0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_e_v_divert, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPropertiesTableLayout = requireView().findViewById(R.id.evDivertEditTable);
        mPropertiesTableLayout.setStretchAllColumns(true);
        mApplicableGrid = requireView().findViewById(R.id.scheduleApplicationTable);
        mApplicableGrid.setShrinkAllColumns(true);
        mApplicableGrid.setStretchAllColumns(true);
        mEVDivertTimes = requireView().findViewById(R.id.scheduleDetailTable);
        mEVDivertTimes.setShrinkAllColumns(true);
        mEVDivertTimes.setStretchAllColumns(true);
        updateView();
    }

    public void refreshFocus() {
        if (isAdded()) {
            mEVDivertsFromActivity = ((EVDivertActivity) requireActivity()).getEVDiverts(mEVDivertIndex);
            unpackEVDivert();
            updateView();
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateView() {
        mPropertiesTableLayout.removeAllViews();
        mApplicableGrid.removeAllViews();
        mEVDivertTimes.removeAllViews();

        if (!(null == getActivity()) && !(null == mEVDivert)) {

            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            params.topMargin = 2;
            params.rightMargin = 2;

            int integerType = InputType.TYPE_CLASS_NUMBER;
            int doubleType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

            // property selection
            TableLayout diversionDetailsTableLayout = new TableLayout(getActivity());
            diversionDetailsTableLayout.setStretchAllColumns(true);
            {
                TableRow nameRow = new TableRow(getActivity());
                MaterialTextView evChargePrompt = new MaterialTextView(getActivity());
                evChargePrompt.setText(R.string.ev_diversion_name);

                EditText evChargeName = new EditText(getActivity());
                evChargeName.setText(mEVDivert.getName());
                evChargeName.setEnabled(mEdit);

                evChargeName.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (!(s.toString().equals(mEVDivert.getName()))) {
                            mEVDivert.setName(s.toString());
                            ((EVDivertActivity)requireActivity()).updateEVDivertAtIndex(mEVDivert, mEVDivertIndex, 0);
                            ((EVDivertActivity)requireActivity()).setSaveNeeded(true);
                        }
                    }
                });

                nameRow.addView(evChargePrompt);
                nameRow.addView(evChargeName);
                diversionDetailsTableLayout.addView(nameRow);

                TableRow activeRow = new TableRow(getActivity());
                MaterialTextView activePrompt = new MaterialTextView(getActivity());
                activePrompt.setText(R.string.active);

                MaterialCheckBox activeCheck = new MaterialCheckBox(getActivity());
                activeCheck.setChecked(mEVDivert.isActive());
                activeCheck.setEnabled(mEdit);
                activeCheck.setOnClickListener(v -> {
                    mEVDivert.setActive(activeCheck.isChecked());
                    ((EVDivertActivity)requireActivity()).updateEVDivertAtIndex(mEVDivert, mEVDivertIndex, 0);
                    ((EVDivertActivity)requireActivity()).setSaveNeeded(true);
                });

                activeRow.addView(activePrompt);
                activeRow.addView(activeCheck);
                diversionDetailsTableLayout.addView(activeRow);

                TableRow priorityRow = new TableRow(getActivity());
                MaterialTextView priorityPrompt = new MaterialTextView(getActivity());
                priorityPrompt.setText(R.string.ev_diversion_priority);

                MaterialCheckBox priorityCheck = new MaterialCheckBox(getActivity());
                priorityCheck.setChecked(mEVDivert.isEv1st());
                priorityCheck.setEnabled(mEdit);
                priorityCheck.setOnClickListener(v -> {
                    mEVDivert.setEv1st(priorityCheck.isChecked());
                    ((EVDivertActivity)requireActivity()).updateEVDivertAtIndex(mEVDivert, mEVDivertIndex, 0);
                    ((EVDivertActivity)requireActivity()).setSaveNeeded(true);
                });

                priorityRow.addView(priorityPrompt);
                priorityRow.addView(priorityCheck);
                diversionDetailsTableLayout.addView(priorityRow);

                TableRow dailyMaxRow = new TableRow(getActivity());
                MaterialTextView dailyMaxPrompt = new MaterialTextView(getActivity());
                dailyMaxPrompt.setText(R.string.daily_max_diversion);

                EditText dailyMaxValue = new EditText(getActivity());
                dailyMaxValue.setText(String.valueOf(mEVDivert.getDailyMax()));
                dailyMaxValue.setEnabled(mEdit);
                dailyMaxValue.setInputType(doubleType);
                dailyMaxValue.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (!(s.toString().equals(String.valueOf(mEVDivert.getDailyMax())))) {
                            mEVDivert.setDailyMax(getDoubleOrZero(s));
                            ((EVDivertActivity)requireActivity()).updateEVDivertAtIndex(mEVDivert, mEVDivertIndex, 0);
                            ((EVDivertActivity)requireActivity()).setSaveNeeded(true);
                        }
                    }
                });

                dailyMaxRow.addView(dailyMaxPrompt);
                dailyMaxRow.addView(dailyMaxValue);
                diversionDetailsTableLayout.addView(dailyMaxRow);

                TableRow minThresholdRow = new TableRow(getActivity());
                MaterialTextView minThresholdPrompt = new MaterialTextView(getActivity());
                minThresholdPrompt.setText(R.string.diversion_threshold);

                EditText minThresholdValue = new EditText(getActivity());
                minThresholdValue.setText(String.valueOf(mEVDivert.getMinimum()));
                minThresholdValue.setEnabled(mEdit);
                minThresholdValue.setInputType(doubleType);
                minThresholdValue.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (!(s.toString().equals(String.valueOf(mEVDivert.getMinimum())))) {
                            mEVDivert.setMinimum(getDoubleOrZero(s));
                            ((EVDivertActivity)requireActivity()).updateEVDivertAtIndex(mEVDivert, mEVDivertIndex, 0);
                            ((EVDivertActivity)requireActivity()).setSaveNeeded(true);
                        }
                    }
                });

                minThresholdRow.addView(minThresholdPrompt);
                minThresholdRow.addView(minThresholdValue);
                diversionDetailsTableLayout.addView(minThresholdRow);
            }
            mPropertiesTableLayout.addView(diversionDetailsTableLayout);

            // Month & Day buttons
            TableLayout buttonTableLayout = new TableLayout(getActivity());
            buttonTableLayout.setStretchAllColumns(true);
            {
                TableRow propertyButtons = new TableRow(getActivity());
                Button propertyButton = new Button(getActivity());
                propertyButton.setText("Hide EV divert properties");
                propertyButton.setOnClickListener(v -> {
                    propertyButton.setText("Show EV divert properties");
                    if (diversionDetailsTableLayout.getVisibility() == View.VISIBLE) {
                        diversionDetailsTableLayout.setVisibility(View.GONE);
                    }
                    else {
                        propertyButton.setText("Hide EV divert properties");
                        diversionDetailsTableLayout.setVisibility(View.VISIBLE);
                    }
                });

                propertyButtons.addView(propertyButton);
                buttonTableLayout.addView(propertyButtons);

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
                        constraintSet.connect(R.id.scheduleDetailScroll, ConstraintSet.TOP, R.id.evDivertEditTable, ConstraintSet.BOTTOM,0);
                        constraintSet.applyTo(constraintLayout);
                    }
                });

                buttons.addView(daysMonthsButton);
                buttonTableLayout.addView(buttons);
                mPropertiesTableLayout.addView(buttonTableLayout);

            }

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
                            cb.setChecked(mEVDivert.getDays().ints.contains(rowNo));
                            Integer finalRowNo = rowNo;
                            cb.setOnClickListener(v -> {
                                if (cb.isChecked()) {
                                    if (!mEVDivert.getDays().ints.contains(finalRowNo))
                                        mEVDivert.getDays().ints.add(finalRowNo);
                                }
                                else
                                    mEVDivert.getDays().ints.remove(finalRowNo);
                                ((EVDivertActivity) requireActivity()).updateEVDivertAtIndex(mEVDivert, mEVDivertIndex, 0);
                                ((EVDivertActivity) requireActivity()).setSaveNeeded(true);
                            });
                            break;
                        case 1:
                            cb.setChecked(mEVDivert.getMonths().months.contains(rowNo));
                            Integer finalMonthCol1No = rowNo;
                            cb.setOnClickListener(v -> {
                                if (cb.isChecked()) {
                                    if (!mEVDivert.getMonths().months.contains(finalMonthCol1No))
                                        mEVDivert.getMonths().months.add(finalMonthCol1No);
                                }
                                else
                                    mEVDivert.getMonths().months.remove(finalMonthCol1No);
                                ((EVDivertActivity) requireActivity()).updateEVDivertAtIndex(mEVDivert, mEVDivertIndex, 0);
                                ((EVDivertActivity) requireActivity()).setSaveNeeded(true);
                            });
                            break;
                        case 2:
                            cb.setChecked(mEVDivert.getMonths().months.contains(rowNo + 7));
                            Integer finalMonthCol2No = rowNo + 7;
                            cb.setOnClickListener(v -> {
                                if (cb.isChecked()) {
                                    if (!mEVDivert.getMonths().months.contains(finalMonthCol2No))
                                        mEVDivert.getMonths().months.add(finalMonthCol2No);
                                }
                                else
                                    mEVDivert.getMonths().months.remove(finalMonthCol2No);
                                ((EVDivertActivity) requireActivity()).updateEVDivertAtIndex(mEVDivert, mEVDivertIndex, 0);
                                ((EVDivertActivity) requireActivity()).setSaveNeeded(true);
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
                mEVDivertTimes.addView(titleRow);

                if (!(null == mEVDivertsFromActivity)) for (EVDivert evDivert: mEVDivertsFromActivity) {
                    TableRow chargeRow = new TableRow(getActivity());
                    ImageButton linked = new ImageButton(getActivity());
                    linked.setImageResource(R.drawable.ic_baseline_link_24);
                    linked.setContentDescription("Load shift is linked");
                    linked.setBackgroundColor(0);
                    EditText from = new EditText(getActivity());
                    EditText to = new EditText(getActivity());
                    ImageButton delete = new ImageButton(getActivity());
                    delete.setImageResource(R.drawable.ic_baseline_delete_24);
                    delete.setContentDescription("Delete this load shift");
                    delete.setBackgroundColor(0);
                    from.setMinimumHeight(80);
                    from.setHeight(80);
                    to.setMinimumHeight(80);
                    to.setHeight(80);

                    from.setText(String.valueOf(evDivert.getBegin()));
                    from.setInputType(integerType);
                    to.setText(String.valueOf(evDivert.getEnd()));
                    to.setInputType(integerType);

                    List<String> linkedScenarios = ((EVDivertActivity) requireActivity()).getLinkedScenarios(evDivert.getEvDivertIndex());
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
                            if (!(s.toString().equals(String.valueOf(evDivert.getBegin())))) {
                                evDivert.setBegin(getIntegerOrZero(s));
                                ((EVDivertActivity) requireActivity()).updateEVDivertAtIndex(evDivert, mEVDivertIndex, evDivert.getEvDivertIndex());
                                ((EVDivertActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    to.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(evDivert.getEnd())))) {
                                evDivert.setEnd(getIntegerOrZero(s));
                                ((EVDivertActivity) requireActivity()).updateEVDivertAtIndex(evDivert, mEVDivertIndex, evDivert.getEvDivertIndex());
                                ((EVDivertActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    delete.setOnClickListener(v -> {
                        from.setBackgroundColor(Color.RED);
                        to.setBackgroundColor(Color.RED);
                        chargeRow.setBackgroundColor(Color.RED);
                        ((EVDivertActivity) requireActivity()).deleteEVDivertAtIndex(evDivert, mEVDivertIndex, evDivert.getEvDivertIndex());
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

                    mEVDivertTimes.addView(chargeRow);
                }
            }

            // Add an add row
            if (mEdit){
                TableRow addRow = new TableRow(getActivity());
                addRow.setBackgroundResource(R.drawable.row_border);
                ImageButton linked = new ImageButton(getActivity());
                linked.setImageResource(R.drawable.ic_baseline_link_off_24);
                linked.setContentDescription("No linked EV divert schedules");
                linked.setEnabled(false);
                linked.setBackgroundColor(0);
                EditText from = new EditText(getActivity());
                EditText to = new EditText(getActivity());
                ImageButton add = new ImageButton(getActivity());
                add.setImageResource(android.R.drawable.ic_menu_add);
                add.setContentDescription("Add a new divert time");
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

                from.setText("12");
                from.setInputType(integerType);
                to.setText("16");
                to.setInputType(integerType);

                add.setOnClickListener(v -> {
                    EVDivert nEVDivert = new EVDivert();
                    nEVDivert.getDays().ints = new ArrayList<>(mEVDivert.getDays().ints);
                    nEVDivert.getMonths().months = new ArrayList<>(mEVDivert.getMonths().months);
                    nEVDivert.setBegin(Integer.parseInt(from.getText().toString()));
                    nEVDivert.setEnd(Integer.parseInt(to.getText().toString()));
                    ((EVDivertActivity) requireActivity()).getNextAddedEVDivertID(nEVDivert);
                    mEVDivertsFromActivity.add(nEVDivert);
                    ((EVDivertActivity) requireActivity()).setSaveNeeded(true);
                    updateView();
                });

                addRow.addView(linked);
                addRow.addView(from);
                addRow.addView(to);
                addRow.addView(add);

                mEVDivertTimes.addView(addRow);
            }
        }
    }

    public void batteryDeleted(int newPosition) {
        mEVDivertIndex = newPosition;
        try {
            mEVDivertsFromActivity = ((EVDivertActivity) requireActivity()).getEVDiverts(mEVDivertIndex);
            unpackEVDivert();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            System.out.println("Fragment " + (mEVDivertIndex + 1) + " was detached from activity during delete");
        }
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        for (View view: mEditFields) view.setEnabled(mEdit);
        updateView();
    }

    public void updateDBIndex() {
        if (!(null == mEVDivert)) {
            mEVDivert.setEvDivertIndex(((EVDivertActivity) requireActivity()).getDatabaseID(mEVDivertIndex));
            mMainHandler.post(this::updateView);
        }
    }
}