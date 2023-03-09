package com.tfcode.comparetout.scenario;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;

import java.util.List;
import java.util.Objects;

public class ScenarioOverview extends Fragment {

    private ActionBar mActionBar;

    private PricePlanNavViewModel mViewModel;
    private ImageButton mPanelButton;
    private ImageButton mInverterButton;
    private ImageButton mHouseButton;
    private ImageButton mBatteryButton;
    private ImageButton mTankButton;
    private ImageButton mCarButton;
    private ImageButton mClockButton;
    private ImageButton mDivertButton;
    private TableLayout mTableLayout;
    private Long mScenarioID;
    private Scenario mScenario;
    private boolean mEdit = false;
    private boolean mSavingNewScenario = false;

    public ScenarioOverview() {
        // Required empty public constructor
    }

    public static ScenarioOverview newInstance() {
        ScenarioOverview fragment = new ScenarioOverview();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScenarioID = ((ScenarioActivity) requireActivity()).getScenarioID();
        mEdit = ((ScenarioActivity) requireActivity()).getEdit();

        mViewModel = new ViewModelProvider(requireActivity()).get(PricePlanNavViewModel.class);
        mViewModel.getAllScenarios().observe(this, scenarios -> {
            if (mScenarioID == 0) {
                if (!(mSavingNewScenario)) {
                    mScenario = new Scenario();
                    mScenario.setScenarioName("<New scenario>");
                }
                else {
                    mScenarioID = findByName(scenarios, mScenario.getScenarioName());
                }
            }
            else{
                mScenario = findByID(scenarios, mScenarioID);
            }
            mActionBar = Objects.requireNonNull(((ScenarioActivity)requireActivity()).getSupportActionBar());
            mActionBar.setTitle("Scenario: " + mScenario.getScenarioName());
            updateView();
        });
    }

    private static Scenario findByID(List<Scenario> scenarios, Long id) {
        return scenarios.stream().filter(s -> id.equals(s.getId())).findFirst().orElse(null);
    }

    private static long findByName(List<Scenario> scenarios, String name) {
        Scenario scenario = scenarios.stream().filter(s -> name.equals(s.getScenarioName())).findFirst().orElse(null);
        if (!(scenario == null)) return scenario.getId();
        else return 0L;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scenario_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = requireView().findViewById(R.id.editScenarioTable);
        setupButtons();
        setupMenu();
        updateView();
    }

    private void setupMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {}

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                System.out.println("ScenarioOverview.onOptionsItemSelected");
                if (menuItem.getItemId() == R.id.edit_scenario) {//add the function to perform here
                    System.out.println("Edit attempt");
                    setEditMode(true);
                    updateView();
                    return (false);
                }
                if (menuItem.getItemId() == R.id.save_scenario) {//add the function to perform here
                    System.out.println("save attempt");
                    if (mScenarioID == 0) {
                        ScenarioComponents scenarioComponents = new ScenarioComponents(mScenario,
                                null, null, null, null, null,
                                null, null, null, null, null);
                        mViewModel.insertScenario(scenarioComponents);
                        mSavingNewScenario = true;
                    }
                    else {
                        mViewModel.updateScenario(mScenario);
                    }
                    return (false);
                }
                return true;
            }
        });
    }

    private void setupButtons() {
        mPanelButton = requireView().findViewById(R.id.panelButton);
        mPanelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Snackbar.make(requireActivity().getWindow().getDecorView().getRootView(),
                  "Launch the panel editor",
                   Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        });
        mInverterButton = requireView().findViewById(R.id.inverterButton);
        mHouseButton = requireView().findViewById(R.id.houseButton);
        mBatteryButton = requireView().findViewById(R.id.batteryButton);
        mTankButton = requireView().findViewById(R.id.tankButton);
        mCarButton = requireView().findViewById(R.id.carButton);
        mClockButton = requireView().findViewById(R.id.clockButton);
        mDivertButton = requireView().findViewById(R.id.divertButton);
    }

    private void updateView() {
        if (!(null == mTableLayout) && !(null == mScenario)) {
            mTableLayout.removeAllViews();
            updateButtons();
            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams scenarioParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            scenarioParams.topMargin = 10;
            scenarioParams.rightMargin = 10;

            if (mEdit) {
                // CREATE TABLE ROWS
                TableRow tableRow = new TableRow(getActivity());
                TextView a = new TextView(getActivity());
                a.setText("Scenario");
                EditText b = new EditText(getActivity());
                b.setText(mScenario.getScenarioName());
                b.setEnabled(true);
                b.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        mScenario.setScenarioName(s.toString());
                        System.out.println("Scenario name changed to : " + mScenario.getScenarioName());
                        // TODO: update the scenario in the DB
                    }
                });
                a.setLayoutParams(scenarioParams);
                b.setLayoutParams(scenarioParams);
                tableRow.addView(a);
                tableRow.addView(b);
                mTableLayout.addView(tableRow);
            }
            else {
                // TODO show the prices for this scenario
                // CREATE TABLE ROWS
                TableRow tableRow = new TableRow(getActivity());
                TextView a = new TextView(getActivity());
                a.setText("Supplier, Plan");
                TextView b = new TextView(getActivity());
                b.setText("€200.00");
                a.setLayoutParams(scenarioParams);
                b.setLayoutParams(scenarioParams);
                tableRow.addView(a);
                tableRow.addView(b);
                mTableLayout.addView(tableRow);
            }
        }
    }

    private void updateButtons() {
        if (mScenario.isHasPanels()) mPanelButton.setImageResource(R.drawable.solarpaneltick);
        else mPanelButton.setImageResource(R.drawable.solarpanel);
        if (mScenario.isHasInverters()) mInverterButton.setImageResource(R.drawable.invertertick);
        else mInverterButton.setImageResource(R.drawable.inverter);
        if (mScenario.isHasLoadProfiles()) mHouseButton.setImageResource(R.drawable.housetick);
        else mHouseButton.setImageResource(R.drawable.house);
        if (mScenario.isHasBatteries()) mBatteryButton.setImageResource(R.drawable.batterytick);
        else mBatteryButton.setImageResource(R.drawable.battery);
        if (mScenario.isHasHWSystem()) mTankButton.setImageResource(R.drawable.tanktick);
        else mTankButton.setImageResource(R.drawable.tank);
        if (mScenario.isHasEVCharges()) mCarButton.setImageResource(R.drawable.cartick);
        else mCarButton.setImageResource(R.drawable.car);
        if (mScenario.isHasLoadShifts()) mClockButton.setImageResource(R.drawable.ic_baseline_access_time_24_tick);
        else mClockButton.setImageResource(R.drawable.ic_baseline_access_time_24);
        if (mScenario.isHasEVDivert() || mScenario.isHasHWDivert()) mDivertButton.setImageResource(R.drawable.ic_baseline_call_split_24_tick);
        else mDivertButton.setImageResource(R.drawable.ic_baseline_call_split_24);
    }

    public void setEditMode(boolean ed) {
        if (!mEdit) {
            mEdit = ed;
//            if (!(null == mEditFields)) for (View v : mEditFields) v.setEnabled(true);
          ScenarioActivity scenarioActivity = ((ScenarioActivity) getActivity());
            if (!(null == scenarioActivity)) scenarioActivity.setEdit();
        }
    }
}