package com.tfcode.comparetout.scenario.battery;

import android.annotation.SuppressLint;
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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.BatteryJson;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BatterySettingsFragment extends Fragment {

    private int mBatteryIndex;
    private long mScenarioID;
    private String mBatteryJsonString;
    private boolean mEdit;
    private List<View> mEditFields;
    private Battery mBattery;
    private TableLayout mTableLayout;

    private Handler mMainHandler;
    private ComparisonUIViewModel mViewModel;
    private List<Inverter> mInverters;

    public BatterySettingsFragment() {
        // Required empty public constructor
    }

    public static BatterySettingsFragment newInstance(int position) {
        BatterySettingsFragment inverterFragment = new BatterySettingsFragment();
        inverterFragment.mBatteryIndex = position;
        return inverterFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());

        mScenarioID = ((BatterySettingsActivity) requireActivity()).getScenarioID();
        mBatteryJsonString = ((BatterySettingsActivity) requireActivity()).getBatteryJson();
        mEdit = ((BatterySettingsActivity) requireActivity()).getEdit();
        mEditFields = new ArrayList<>();
        unpackBattery();
        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        new Thread(() -> {
            mInverters = mViewModel.getInvertersForScenario(mScenarioID);
            mMainHandler.post(() -> {if (!(null == mTableLayout)) updateView();});
        }).start();
    }

    private void unpackBattery() {
        Type type = new TypeToken<List<BatteryJson>>(){}.getType();
        List<BatteryJson> batteryJson = new Gson().fromJson(mBatteryJsonString, type);
        mBattery = JsonTools.createBatteryList(batteryJson).get(mBatteryIndex);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_battery_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = requireView().findViewById(R.id.batterySettingsEditTable);
        mTableLayout.setShrinkAllColumns(true);
        mTableLayout.setStretchAllColumns(true);
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
            mBatteryJsonString = ((BatterySettingsActivity) requireActivity()).getBatteryJson();
            unpackBattery();
            updateView();
        }
    }

    private void updateView() {
        System.out.println("Updating BatteryFragment " + mBatteryIndex + ", " + mEdit);
        mTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.topMargin = 2;
        params.rightMargin = 2;

        int integerType = InputType.TYPE_CLASS_NUMBER;
        int doubleType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

        // CREATE TABLE ROWS
        TableRow inverterRow = new TableRow(getActivity());
        TextView inverterText = new TextView(getActivity());
        inverterText.setText(R.string.ConnectedInverterName);
        ArrayList<String> inverterSpinnerContent = new ArrayList<>();
        int selectedInverterIndex = 0;
        int itr = 0;
        Inverter initialInverter = null;
        if (!(null == mInverters)) for (Inverter inverter: mInverters) {
            String inv = inverter.getInverterName();
            inverterSpinnerContent.add(inv);
            if (mBattery.getInverter().equals(inv)) {
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
        inverterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String inverter = inverterSpinnerContent.get(position);
                mBattery.setInverter(inverter);
                System.out.println("Setting Inverter to: " + inverter);
                ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                if (null == finalInitialInverter) ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                else if (!finalInitialInverter.getInverterName().equals(mBattery.getInverter()))
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
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

        mTableLayout.addView(createRow("Battery size (kWh)", String.valueOf(mBattery.getBatterySize()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mBattery.getBatterySize())))) {
                    System.out.println("TODO: Update the batterySize");
                    mBattery.setBatterySize(getDoubleOrZero(s));
                    ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Discharge stop (%)", String.valueOf(mBattery.getDischargeStop()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mBattery.getDischargeStop())))) {
                    System.out.println("TODO: Update the discharge stop percentage");
                    mBattery.setDischargeStop(getDoubleOrZero(s));
                    ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Max discharge in 5 minutes (kWh)", String.valueOf(mBattery.getMaxDischarge()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mBattery.getMaxDischarge())))) {
                    System.out.println("TODO: Update the max discharge");
                    mBattery.setMaxDischarge(getDoubleOrZero(s));
                    ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Max charge in 5 minutes (kWh)", String.valueOf(mBattery.getMaxCharge()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mBattery.getMaxCharge())))) {
                    System.out.println("TODO: Update the max charge");
                    mBattery.setMaxCharge(getDoubleOrZero(s));
                    ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Storage Loss (%)", String.valueOf(mBattery.getStorageLoss()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mBattery.getStorageLoss())))) {
                    System.out.println("TODO: Update the storage loss");
                    mBattery.setStorageLoss(getDoubleOrZero(s));
                    ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Charge rate @ 0-12% SOC (%)", String.valueOf(mBattery.getChargeModel().percent0), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mBattery.getChargeModel().percent0)))) {
                    System.out.println("TODO: Update the charge model");
                    mBattery.getChargeModel().percent0 = getIntegerOrZero(s);
                    ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, integerType));
        mTableLayout.addView(createRow("Charge rate @ 12-90% SOC (%)", String.valueOf(mBattery.getChargeModel().percent12), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mBattery.getChargeModel().percent12)))) {
                    System.out.println("TODO: Update the charge model");
                    mBattery.getChargeModel().percent12 = getIntegerOrZero(s);
                    ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, integerType));
        mTableLayout.addView(createRow("Charge rate @ 90-100% SOC (%)", String.valueOf(mBattery.getChargeModel().percent90), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mBattery.getChargeModel().percent90)))) {
                    System.out.println("TODO: Update the charge model");
                    mBattery.getChargeModel().percent90 = getIntegerOrZero(s);
                    ((BatterySettingsActivity) requireActivity()).updateBatteryAtIndex(mBattery, mBatteryIndex);
                    ((BatterySettingsActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, integerType));
    }

    private TableRow createRow(String title, String initialValue, AbstractTextWatcher action, TableRow.LayoutParams params, int inputType){
        TableRow tableRow = new TableRow(getActivity());
        TextView a = new TextView(getActivity());
        a.setText(title);
        EditText b = new EditText(getActivity());
        b.setText(initialValue);
        b.setEnabled(mEdit);
        b.addTextChangedListener(action);
        b.setInputType(inputType);
        mEditFields.add(b);

        a.setLayoutParams(params);
        b.setLayoutParams(params);
        tableRow.addView(a);
        tableRow.addView(b);
        return tableRow;
    }

    public void batteryDeleted(int newPosition) {
        System.out.println("Updating fragment index from " + mBatteryIndex + " to " + (newPosition));
        mBatteryIndex = newPosition;
        try {
            mBatteryJsonString = ((BatterySettingsActivity) requireActivity()).getBatteryJson();
            unpackBattery();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            System.out.println("Fragment " + (mBatteryIndex + 1) + " was detached from activity during delete");
        }
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        for (View view: mEditFields) view.setEnabled(mEdit);
    }

    public void updateDBIndex() {
        if (!(null == mBattery))
            mBattery.setBatteryIndex(((BatterySettingsActivity) requireActivity()).getDBID(mBatteryIndex));
    }
}