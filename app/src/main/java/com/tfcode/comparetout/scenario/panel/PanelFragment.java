package com.tfcode.comparetout.scenario.panel;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.PanelJson;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.scenario.inverter.InverterActivity;
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
        mScenarioID = ((PanelActivity) requireActivity()).getScenarioID();
        mPanelJsonString = ((PanelActivity) requireActivity()).getPanelJson();
        mEdit = ((PanelActivity) requireActivity()).getEdit();
        mEditFields = new ArrayList<>();
        unpackPanel();
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

        mBarChart = requireView().findViewById((R.id.pv_data_chart));
        mPanelNoData  = requireView().findViewById((R.id.panelNoData));
        updateView();
    }

    @Override
    public void onResume() {
        super.onResume();
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
//        updateChartView();
        updateDataControlView();
    }

    private void updateDataControlView() {
        mPanelNoData.removeAllViews();
//        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
//        params.topMargin = 2;
//        params.rightMargin = 2;
        Button button = new Button(getActivity());
        button.setText("Fetch / Update data");
        button.setOnClickListener(v -> {

            Toast.makeText(getActivity(), "TODO Fetch", Toast.LENGTH_SHORT).show();
//            Intent intent = new Intent(getActivity(), PanelActivity.class);
//            intent.putExtra("PanelID", mPanelIndex);
//            intent.putExtra("ScenarioName", mScenario.getScenarioName());
//            intent.putExtra("Edit", mEdit);
//            startActivity(intent);
        });
        mPanelNoData.addView(button);
    }

    private void updateEditorView() {
        mTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.topMargin = 2;
        params.rightMargin = 2;

        int integerType = InputType.TYPE_CLASS_NUMBER;
        int doubleType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        int stringType = InputType.TYPE_CLASS_TEXT;

        // CREATE TABLE ROWS
        mTableLayout.addView(createRow("Connected Inverter name", mPanel.getInverter(), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(mPanel.getInverter()))) {
                    System.out.println("Inverter name changed");
                    mPanel.setInverter(s.toString());
                    ((PanelActivity) requireActivity()).updatePanelAtIndex(mPanel, mPanelIndex);
                    ((PanelActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, stringType));
        mTableLayout.addView(createRow("Connected Inverter mppt", String.valueOf(mPanel.getMppt()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mPanel.getMppt())))) {
                    System.out.println("Inverter mppt changed");
                    mPanel.setMppt(getIntegerOrZero(s));
                    ((PanelActivity) requireActivity()).updatePanelAtIndex(mPanel, mPanelIndex);
                    ((PanelActivity) requireActivity()).setSaveNeeded(true);
                }
            }
        }, params, integerType));
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
}