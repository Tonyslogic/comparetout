package com.tfcode.comparetout.scenario.inverter;

import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.json.scenario.InverterJson;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.priceplan.PricePlanActivity;
import com.tfcode.comparetout.scenario.ScenarioActivity;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class InverterFragment extends Fragment {

    private int mInverterIndex;
    private long mScenarioID;
    private String mInverterJsonString;
    private boolean mEdit;
    private List<View> mEditFields;
    private Inverter mInverter;
    private TableLayout mTableLayout;

    public InverterFragment() {
        // Required empty public constructor
    }

    public static InverterFragment newInstance(int position) {
        InverterFragment inverterFragment = new InverterFragment();
        inverterFragment.mInverterIndex = position;
        return inverterFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScenarioID = ((InverterActivity) requireActivity()).getScenarioID();
        mInverterJsonString = ((InverterActivity) requireActivity()).getInverterJson();
        mEdit = ((InverterActivity) requireActivity()).getEdit();
        mEditFields = new ArrayList<>();
        unpackInverter();
    }

    private void unpackInverter() {
        Type type = new TypeToken<List<InverterJson>>(){}.getType();
        List<InverterJson> inverterJson = new Gson().fromJson(mInverterJsonString, type);
        mInverter = JsonTools.createInverterList(inverterJson).get(mInverterIndex);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inverter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = requireView().findViewById(R.id.inverterEditTable);
        mTableLayout.setShrinkAllColumns(true);
        mTableLayout.setStretchAllColumns(true);
        updateView();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    public void refreshFocus() {
        if (isAdded()) {
            mInverterJsonString = ((InverterActivity) requireActivity()).getInverterJson();
            unpackInverter();
            updateView();
        }
    }

    private void updateView() {
        System.out.println("Updating InverterFragment " + mInverterIndex + ", " + mEdit);
        mTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.topMargin = 2;
        params.rightMargin = 2;

        int integerType = InputType.TYPE_CLASS_NUMBER;
        int doubleType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        int stringType = InputType.TYPE_CLASS_TEXT;

        // CREATE TABLE ROWS
        mTableLayout.addView(createRow("Inverter name", mInverter.getInverterName(), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(mInverter.getInverterName()))) {
                    System.out.println("Inverter name changed");
                    mInverter.setInverterName(s.toString());
                    ((InverterActivity) requireActivity()).updateInverterAtIndex(mInverter, mInverterIndex);
                    ((InverterActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, stringType));
        mTableLayout.addView(createRow("Minimum excess (kW)", String.valueOf(mInverter.getMinExcess()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mInverter.getMinExcess())))) {
                    System.out.println("TODO: Update the min excess");
                    mInverter.setMinExcess(getDoubleOrZero(s));
                    ((InverterActivity) requireActivity()).updateInverterAtIndex(mInverter, mInverterIndex);
                    ((InverterActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Maximum load (kW)", String.valueOf(mInverter.getMaxInverterLoad()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mInverter.getMaxInverterLoad())))) {
                    System.out.println("TODO: Update the max inverter load");
                    mInverter.setMaxInverterLoad(getDoubleOrZero(s));
                    ((InverterActivity) requireActivity()).updateInverterAtIndex(mInverter, mInverterIndex);
                    ((InverterActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Number of MPPT", String.valueOf(mInverter.getMpptCount()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mInverter.getMpptCount())))) {
                    System.out.println("TODO: Update the mppt count");
                    mInverter.setMpptCount(getIntegerOrZero(s));
                    ((InverterActivity) requireActivity()).updateInverterAtIndex(mInverter, mInverterIndex);
                    ((InverterActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, integerType));
        mTableLayout.addView(createRow("AC to DC Loss (%)", String.valueOf(mInverter.getAc2dcLoss()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mInverter.getAc2dcLoss())))) {
                    System.out.println("TODO: Update the ac2dcloss");
                    mInverter.setAc2dcLoss(getIntegerOrZero(s));
                    ((InverterActivity) requireActivity()).updateInverterAtIndex(mInverter, mInverterIndex);
                    ((InverterActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, integerType));
        mTableLayout.addView(createRow("DC to AC Loss (%)", String.valueOf(mInverter.getDc2acLoss()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mInverter.getDc2acLoss())))) {
                    System.out.println("TODO: Update the dc2acloss");
                    mInverter.setDc2acLoss(getIntegerOrZero(s));
                    ((InverterActivity) requireActivity()).updateInverterAtIndex(mInverter, mInverterIndex);
                    ((InverterActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, integerType));
        mTableLayout.addView(createRow("DC to DC Loss (%)", String.valueOf(mInverter.getDc2dcLoss()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mInverter.getDc2dcLoss())))) {
                    System.out.println("TODO: Update the dc2dcloss");
                    mInverter.setDc2dcLoss(getIntegerOrZero(s));
                    ((InverterActivity) requireActivity()).updateInverterAtIndex(mInverter, mInverterIndex);
                    ((InverterActivity) requireActivity()).setSaveNeeded(true);
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

    public void inverterDeleted(int newPosition) {
        System.out.println("Updating fragment index from " + mInverterIndex + " to " + (newPosition));
        mInverterIndex = newPosition;
        try {
            mInverterJsonString = ((InverterActivity) requireActivity()).getInverterJson();
            unpackInverter();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            System.out.println("Fragment " + (mInverterIndex + 1) + " was detached from activity during delete");
        }
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        for (View view: mEditFields) view.setEnabled(mEdit);
    }

    public void update() {
    }
}