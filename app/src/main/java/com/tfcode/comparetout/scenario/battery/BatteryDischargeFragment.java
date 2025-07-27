package com.tfcode.comparetout.scenario.battery;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
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
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link BatteryDischargeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BatteryDischargeFragment extends Fragment {

    private int mDischargeIndex;
    private long mScenarioID;
    private boolean mEdit;
    private List<View> mEditFields;
    private DischargeToGrid mDischarge;
    // TODO rename these 5
    private List<DischargeToGrid> mDischargesFromActivity;
    private TableLayout mDateTableLayout;
    private TableLayout mApplicableGrid;
    private TableLayout DischargeTimes;

    private Handler mMainHandler;
    private ComparisonUIViewModel mViewModel;
    private List<Inverter> mInverters;

    public BatteryDischargeFragment() {
        // Required empty public constructor
    }

    public static BatteryDischargeFragment newInstance(int position) {
        BatteryDischargeFragment fragment = new BatteryDischargeFragment();
        fragment.mDischargeIndex = position;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());

        // The activity may not be created, so these calls wait for the activity creation to complete
        requireActivity().getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if ((event.getTargetState() ==  Lifecycle.State.CREATED ) && !(null == getActivity()) ) {
                mScenarioID = ((BatteryDischargeActivity) requireActivity()).getScenarioID();
                mDischargesFromActivity = ((BatteryDischargeActivity) requireActivity()).getDischarges(mDischargeIndex);
                mEdit = ((BatteryDischargeActivity) requireActivity()).getEdit();
                mEditFields = new ArrayList<>();
                unpackDischarge();
                mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
                new Thread(() -> {
                    mInverters = mViewModel.getInvertersForScenario(mScenarioID);
                    mMainHandler.post(() -> {if (!(null == mDateTableLayout)) updateView();});
                }).start();
                if (!(null == mDateTableLayout)) updateView();
            }
        });
    }

    private void unpackDischarge() {
        if (!(null == mDischargesFromActivity) && !mDischargesFromActivity.isEmpty())
            mDischarge = mDischargesFromActivity.get(0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_battery_discharge, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mDateTableLayout = requireView().findViewById(R.id.dischargeEditTable);
        mDateTableLayout.setStretchAllColumns(true);
        mApplicableGrid = requireView().findViewById(R.id.dischargeApplicationTable);
        mApplicableGrid.setShrinkAllColumns(true);
        mApplicableGrid.setStretchAllColumns(true);
        DischargeTimes = requireView().findViewById(R.id.dischargeDetailTable);
        DischargeTimes.setShrinkAllColumns(true);
        DischargeTimes.setStretchAllColumns(true);
        updateView();
    }

    public void refreshFocus() {
        if (isAdded()) {
            mDischargesFromActivity = ((BatteryDischargeActivity) requireActivity()).getDischarges(mDischargeIndex);
            unpackDischarge();
            updateView();
        }
    }

    private void updateView() {
        mDateTableLayout.removeAllViews();
        mApplicableGrid.removeAllViews();
        DischargeTimes.removeAllViews();

        if (!(null == getActivity()) && !(null == mDischarge)) {

            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            params.topMargin = 2;
            params.rightMargin = 2;

            int integerType = InputType.TYPE_CLASS_NUMBER;
            int doubleType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

            // Inverter selection
            TableLayout nameTableLayout = new TableLayout(getActivity());
            nameTableLayout.setStretchAllColumns(true);
            {
                TableRow nameRow = new TableRow(getActivity());
                TextView dischargePrompt = new TextView(getActivity());
                dischargePrompt.setText(R.string.discharge_schedule_name);

                EditText dischargeName = new EditText(getActivity());
                dischargeName.setText(mDischarge.getName());
                dischargeName.setEnabled(mEdit);

                dischargeName.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (!(s.toString().equals(mDischarge.getName()))) {
                            mDischarge.setName(s.toString());
                            ((BatteryDischargeActivity)requireActivity()).updateDischargeAtIndex(mDischarge, mDischargeIndex, 0);
                            ((BatteryDischargeActivity)requireActivity()).setSaveNeeded(true);
                        }
                    }
                });

                nameRow.addView(dischargePrompt);
                nameRow.addView(dischargeName);
                nameTableLayout.addView(nameRow);
            }
            mDateTableLayout.addView(nameTableLayout);
            TableLayout inverterTableLayout = new TableLayout(getActivity());
            inverterTableLayout.setStretchAllColumns(true);
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
                    if (mDischarge.getInverter().equals(inv)) {
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
                        mDischarge.setInverter(inverter);
                        ((BatteryDischargeActivity) requireActivity()).updateDischargeAtIndex(mDischarge, mDischargeIndex, 0);
                        if (null == finalInitialInverter)
                            ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
                        else if (!finalInitialInverter.getInverterName().equals(mDischarge.getInverter()))
                            ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
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
                inverterTableLayout.addView(inverterRow);
            }
            mDateTableLayout.addView(inverterTableLayout);

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
                        constraintSet.connect(R.id.dischargeDetailScroll, ConstraintSet.TOP, R.id.dischargeApplicationTable, ConstraintSet.BOTTOM,0);
                        constraintSet.applyTo(constraintLayout);
                    }
                    else {
                        daysMonthsButton.setText(R.string.show_days_months);
                        mApplicableGrid.setVisibility(View.INVISIBLE);
                        ConstraintLayout constraintLayout = (ConstraintLayout) mApplicableGrid.getParent();
                        ConstraintSet constraintSet = new ConstraintSet();
                        constraintSet.clone(constraintLayout);
                        constraintSet.connect(R.id.dischargeDetailScroll, ConstraintSet.TOP, R.id.dischargeEditTable, ConstraintSet.BOTTOM,0);
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
                                cb.setChecked(mDischarge.getDays().ints.contains(rowNo));
                                Integer finalRowNo = rowNo;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mDischarge.getDays().ints.contains(finalRowNo))
                                            mDischarge.getDays().ints.add(finalRowNo);
                                    }
                                    else
                                        mDischarge.getDays().ints.remove(finalRowNo);
                                    ((BatteryDischargeActivity) requireActivity()).updateDischargeAtIndex(mDischarge, mDischargeIndex, 0);
                                    ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
                                });
                                break;
                            case 1:
                                cb.setChecked(mDischarge.getMonths().months.contains(rowNo));
                                Integer finalMonthCol1No = rowNo;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mDischarge.getMonths().months.contains(finalMonthCol1No))
                                            mDischarge.getMonths().months.add(finalMonthCol1No);
                                    }
                                    else
                                        mDischarge.getMonths().months.remove(finalMonthCol1No);
                                    ((BatteryDischargeActivity) requireActivity()).updateDischargeAtIndex(mDischarge, mDischargeIndex, 0);
                                    ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
                                });
                                break;
                            case 2:
                                cb.setChecked(mDischarge.getMonths().months.contains(rowNo + 7));
                                Integer finalMonthCol2No = rowNo + 7;
                                cb.setOnClickListener(v -> {
                                    if (cb.isChecked()) {
                                        if (!mDischarge.getMonths().months.contains(finalMonthCol2No))
                                            mDischarge.getMonths().months.add(finalMonthCol2No);
                                    }
                                    else
                                        mDischarge.getMonths().months.remove(finalMonthCol2No);
                                    ((BatteryDischargeActivity) requireActivity()).updateDischargeAtIndex(mDischarge, mDischargeIndex, 0);
                                    ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
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
                TextView drawTitle = new TextView(getActivity());
                drawTitle.setText(R.string.rate_kw);
                titleRow.addView(drawTitle);
                TextView stopTitle = new TextView(getActivity());
                stopTitle.setText(R.string.StopAt);
                titleRow.addView(stopTitle);
                TextView deleteTitle = new TextView(getActivity());
                deleteTitle.setText(R.string.Delete);
                deleteTitle.setGravity(Gravity.CENTER);
                titleRow.addView(deleteTitle);
                DischargeTimes.addView(titleRow);

                if (!(null == mDischargesFromActivity)) for (DischargeToGrid discharge: mDischargesFromActivity) {
                    TableRow chargeRow = new TableRow(getActivity());
                    ImageButton linked = new ImageButton(getActivity());
                    linked.setImageResource(R.drawable.ic_baseline_link_24);
                    linked.setContentDescription("Discharge is linked");
                    linked.setBackgroundColor(0);
                    EditText from = new EditText(getActivity());
                    EditText to = new EditText(getActivity());
                    EditText draw = new EditText(getActivity());
                    EditText stop = new EditText(getActivity());
                    ImageButton delete = new ImageButton(getActivity());
                    delete.setImageResource(R.drawable.ic_baseline_delete_24);
                    delete.setContentDescription("Delete this load shift");
                    delete.setBackgroundColor(0);
                    from.setMinimumHeight(80);
                    from.setHeight(80);
                    to.setMinimumHeight(80);
                    to.setHeight(80);
                    draw.setMinimumHeight(80);
                    draw.setHeight(80);
                    stop.setMinimumHeight(80);
                    stop.setHeight(80);

                    from.setText(String.valueOf(discharge.getBegin()));
                    from.setInputType(integerType);
                    to.setText(String.valueOf(discharge.getEnd()));
                    to.setInputType(integerType);
                    draw.setText(String.valueOf(discharge.getRate()));
                    draw.setInputType(doubleType);
                    stop.setText(String.valueOf(discharge.getStopAt()));
                    stop.setInputType(doubleType);

                    List<String> linkedScenarios = ((BatteryDischargeActivity) requireActivity()).getLinkedScenarios(discharge.getD2gIndex());
                    if ((null == linkedScenarios) || linkedScenarios.isEmpty()) {
                        linked.setEnabled(false);
                        linked.setImageResource(R.drawable.ic_baseline_link_off_24);
                        linked.setContentDescription("No linked load shifts");
                    }
                    else {
                        linked.setEnabled(true);
                        linked.setImageResource(R.drawable.ic_baseline_link_24);
                        linked.setOnClickListener(view -> Snackbar.make(view.getRootView(), "Linked to " + linkedScenarios, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show());
                    }


                    from.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(discharge.getBegin())))) {
                                discharge.setBegin(getIntegerOrZero(s));
                                ((BatteryDischargeActivity) requireActivity()).updateDischargeAtIndex(discharge, mDischargeIndex, discharge.getD2gIndex());
                                ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    to.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(discharge.getEnd())))) {
                                discharge.setEnd(getIntegerOrZero(s));
                                ((BatteryDischargeActivity) requireActivity()).updateDischargeAtIndex(discharge, mDischargeIndex, discharge.getD2gIndex());
                                ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    draw.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(discharge.getRate())))) {
                                discharge.setRate(getDoubleOrZero(s));
                                ((BatteryDischargeActivity) requireActivity()).updateDischargeAtIndex(discharge, mDischargeIndex, discharge.getD2gIndex());
                                ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    stop.addTextChangedListener( new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            if (!(s.toString().equals(String.valueOf(discharge.getStopAt())))) {
                                discharge.setStopAt(getDoubleOrZero(s));
                                ((BatteryDischargeActivity) requireActivity()).updateDischargeAtIndex(discharge, mDischargeIndex, discharge.getD2gIndex());
                                ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
                            } } } );

                    delete.setOnClickListener(v -> {
                        from.setBackgroundColor(Color.RED);
                        to.setBackgroundColor(Color.RED);
                        draw.setBackgroundColor(Color.RED);
                        chargeRow.setBackgroundColor(Color.RED);
                        ((BatteryDischargeActivity) requireActivity()).deleteDischargeAtIndex(discharge, mDischargeIndex, discharge.getD2gIndex());
                        updateView();
                    });

                    from.setEnabled(mEdit);
                    mEditFields.add(from);
                    to.setEnabled(mEdit);
                    mEditFields.add(to);
                    draw.setEnabled(mEdit);
                    mEditFields.add(draw);
                    stop.setEnabled(mEdit);
                    mEditFields.add(stop);
                    delete.setEnabled(mEdit);
                    mEditFields.add(delete);

                    chargeRow.addView(linked);
                    chargeRow.addView(from);
                    chargeRow.addView(to);
                    chargeRow.addView(draw);
                    chargeRow.addView(stop);
                    chargeRow.addView(delete);

                    DischargeTimes.addView(chargeRow);
                }
            }

            // Add an add row
            if (mEdit){
                TableRow addRow = new TableRow(getActivity());
                addRow.setBackgroundResource(R.drawable.row_border);
                ImageButton linked = new ImageButton(getActivity());
                linked.setImageResource(R.drawable.ic_baseline_link_off_24);
                linked.setContentDescription("No linked discharge schedules");
                linked.setEnabled(false);
                linked.setBackgroundColor(0);
                EditText from = new EditText(getActivity());
                EditText to = new EditText(getActivity());
                EditText draw = new EditText(getActivity());
                EditText stop = new EditText(getActivity());
                ImageButton add = new ImageButton(getActivity());
                add.setImageResource(android.R.drawable.ic_menu_add);
                add.setContentDescription("Add a new charge time");
                add.setBackgroundColor(0);
                from.setMinimumHeight(80);
                from.setHeight(80);
                to.setMinimumHeight(80);
                to.setHeight(80);
                draw.setMinimumHeight(80);
                draw.setHeight(80);
                stop.setMinimumHeight(80);
                stop.setHeight(80);

                from.setEnabled(mEdit);
                mEditFields.add(from);
                to.setEnabled(mEdit);
                mEditFields.add(to);
                draw.setEnabled(mEdit);
                mEditFields.add(draw);
                stop.setEnabled(mEdit);
                mEditFields.add(stop);
                add.setEnabled(mEdit);
                mEditFields.add(add);

                from.setText(R.string._17);
                from.setInputType(integerType);
                to.setText(R.string._19);
                to.setInputType(integerType);
                draw.setText(R.string._0_225);
                draw.setInputType(doubleType);
                stop.setText(R.string.TwentyPoint0);
                stop.setInputType(doubleType);

                add.setOnClickListener(v -> {
                    DischargeToGrid discharge = new DischargeToGrid();
                    discharge.setInverter(discharge.getInverter());
                    discharge.getDays().ints = new ArrayList<>(mDischarge.getDays().ints);
                    discharge.getMonths().months = new ArrayList<>(mDischarge.getMonths().months);
                    discharge.setBegin(Integer.parseInt(from.getText().toString()));
                    discharge.setEnd(Integer.parseInt(to.getText().toString()));
                    discharge.setRate(Double.parseDouble(draw.getText().toString()));
                    discharge.setStopAt(Double.parseDouble(stop.getText().toString()));
                    ((BatteryDischargeActivity) requireActivity()).getNextAddedDischargeID(discharge);
                    mDischargesFromActivity.add(discharge);
                    ((BatteryDischargeActivity) requireActivity()).setSaveNeeded(true);
                    updateView();
                });

                addRow.addView(linked);
                addRow.addView(from);
                addRow.addView(to);
                addRow.addView(draw);
                addRow.addView(stop);
                addRow.addView(add);

                DischargeTimes.addView(addRow);
            }
        }
    }

    public void batteryDeleted(int newPosition) {
        mDischargeIndex = newPosition;
        try {
            mDischargesFromActivity = ((BatteryDischargeActivity) requireActivity()).getDischarges(mDischargeIndex);
            unpackDischarge();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            System.out.println("Fragment " + (mDischargeIndex + 1) + " was detached from activity during delete");
        }
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        for (View view: mEditFields) view.setEnabled(mEdit);
        updateView();
    }

    public void updateDBIndex() {
        if (!(null == mDischarge)) {
            mDischarge.setD2gIndex(((BatteryDischargeActivity) requireActivity()).getDatabaseID(mDischargeIndex));
            mMainHandler.post(this::updateView);
        }
    }
}