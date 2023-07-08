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

package com.tfcode.comparetout.scenario;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.scenario.HWDivert;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.model.scenario.SimKPIs;
import com.tfcode.comparetout.scenario.battery.BatteryChargingActivity;
import com.tfcode.comparetout.scenario.battery.BatterySettingsActivity;
import com.tfcode.comparetout.scenario.ev.EVDivertActivity;
import com.tfcode.comparetout.scenario.ev.EVScheduleActivity;
import com.tfcode.comparetout.scenario.inverter.InverterActivity;
import com.tfcode.comparetout.scenario.loadprofile.LoadProfileActivity;
import com.tfcode.comparetout.scenario.panel.PanelActivity;
import com.tfcode.comparetout.scenario.water.WaterScheduleActivity;
import com.tfcode.comparetout.scenario.water.WaterSettingsActivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ScenarioOverview extends Fragment {

    private static final String SCENARIO_ID = "SCENARIO_ID";
    private static final String EDIT = "EDIT";

    private ActionBar mActionBar;
    private Handler mMainHandler;

    private ComparisonUIViewModel mViewModel;
    private ImageButton mPanelButton;
    private ImageButton mInverterButton;
    private ImageButton mHouseButton;
    private ImageButton mBatteryButton;
    private ImageButton mTankButton;
    private ImageButton mCarButton;
    private TableLayout mTableLayout;
    private TableLayout mHelpTable;
    private Long mScenarioID;
    private Scenario mScenario;
    private ScenarioComponents mScenarioComponents = null;
    private List<String> mScenarioNames;

    private SimKPIs mSimKPIs;
    private Costings mBestCosting;
    private boolean mEdit = false;
    private boolean mSavingNewScenario = false;

    private boolean mHasPanelData = false;

    public ScenarioOverview() {
        // Required empty public constructor
    }

    public static ScenarioOverview newInstance() {
        return new ScenarioOverview();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EDIT, mEdit);
        outState.putLong(SCENARIO_ID, mScenarioID);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mEdit = savedInstanceState.getBoolean(EDIT);
            mScenarioID = savedInstanceState.getLong(SCENARIO_ID);
        }
        else if (!(null == getActivity())) {
            if ((null == mScenarioID) || mScenarioID == 0)
                mScenarioID = ((ScenarioActivity) getActivity()).getScenarioID();
            mEdit = ((ScenarioActivity) getActivity()).getEdit();
            if (mEdit) ((ScenarioActivity) getActivity()).setSaveNeeded(true);
        }

        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getAllScenarios().observe(this, scenarios -> {
            mScenarioNames = new ArrayList<>();
            for (Scenario s : scenarios) mScenarioNames.add(s.getScenarioName());
            if (mScenarioID == 0) {
                if (!(mSavingNewScenario)) {
                    mScenario = new Scenario();
                    mScenario.setScenarioName("<New scenario>");
                }
                else {
                    mScenarioID = findByName(scenarios, mScenario.getScenarioName());
                    if (mScenarioID == null) mScenarioID = 0L;
                }
            }
            else{
                mScenario = findByID(scenarios, mScenarioID);
            }
            mScenarioNames.remove(mScenario.getScenarioName());
            mActionBar = Objects.requireNonNull(((ScenarioActivity)requireActivity()).getSupportActionBar());
            mActionBar.setTitle("Usage: " + mScenario.getScenarioName());
            updateScenarioComponents();
            updateButtons();
            updateView();
        });
        mViewModel.getAllComparisons().observe(this, costings -> updateKPIs());
    }

    private void updateScenarioComponents() {
        new Thread(() -> {
            mScenarioComponents = mViewModel.getScenarioComponentsForID(mScenarioID);
            mMainHandler.post(this::updateButtons);
        }).start();
    }

    private void updateKPIs() {
        new Thread(() -> {
            mSimKPIs = mViewModel.getSimKPIsForScenario(mScenarioID);
            mBestCosting = mViewModel.getBestCostingForScenario(mScenarioID);
            mHasPanelData = mViewModel.checkForMissingPanelData(mScenarioID);
            mMainHandler.post(this::updateView);
        }).start();
    }

    private static Scenario findByID(List<Scenario> scenarios, Long id) {
        return scenarios.stream().filter(s -> id.equals(s.getScenarioIndex())).findFirst().orElse(null);
    }

    private static Long findByName(List<Scenario> scenarios, String name) {
        Scenario scenario = scenarios.stream().filter(s -> name.equals(s.getScenarioName())).findFirst().orElse(null);
        if (!(scenario == null)) {
            return scenario.getScenarioIndex();
        }
        else return null;
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
        mHelpTable = requireView().findViewById(R.id.scenarioHelpTable);
        mMainHandler = new Handler(Looper.getMainLooper());
        setupButtons();
        setupMenu();
        SimulatorLauncher.simulateIfNeeded(getContext());
        updateView();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity())) getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        updateKPIs();
    }

    private void setupMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {}

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.edit_scenario) {
                    setEditMode(true);
                    updateView();
                    return (false);
                }
                if (menuItem.getItemId() == R.id.save_scenario) {
                    if (!(null == getActivity()) && !((ScenarioActivity)getActivity()).isSimulationInProgress()) {
                        if (mScenarioID == 0) {
                            ScenarioComponents scenarioComponents = new ScenarioComponents(mScenario,
                                    null, null, null, null, null,
                                    null, null, null, null, null);
                            mViewModel.insertScenario(scenarioComponents);
                        } else {
                            mViewModel.updateScenario(mScenario);
                        }
                        mSavingNewScenario = true;
                        mEdit = false;
                        ((ScenarioActivity) requireActivity()).setSaveNeeded(false);
                    }
                    else {
                        if (!(null == getView())) Snackbar.make(getView(),
                                        "Cannot save during simulation. Try again in a moment.", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }
                    return (false);
                }
                return true;
            }
        });
    }

    private void setupButtons() {
        mPanelButton = requireView().findViewById(R.id.panelButton);
        mPanelButton.setOnClickListener(v -> {
            if (!mScenario.isHasInverters()) {
                Snackbar.make(requireActivity().getWindow().getDecorView().getRootView(),
                    "Add at least one inverter before adding panels",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
            else {
                Intent intent = new Intent(getActivity(), PanelActivity.class);
                intent.putExtra("ScenarioID", mScenarioID);
                intent.putExtra("ScenarioName", mScenario.getScenarioName());
                intent.putExtra("Edit", (mEdit | !mScenario.isHasPanels()));
                startActivity(intent);
            }
        });

        mInverterButton = requireView().findViewById(R.id.inverterButton);
        mInverterButton.setOnClickListener(v -> {
            if (mScenarioID == 0L || !(mScenario.isHasLoadProfiles())) {
                Snackbar.make(requireActivity().getWindow().getDecorView().getRootView(),
                        "Create a load profile before configuring inverters",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
            else {
                Intent intent = new Intent(getActivity(), InverterActivity.class);
                intent.putExtra("ScenarioID", mScenarioID);
                intent.putExtra("ScenarioName", mScenario.getScenarioName());
                intent.putExtra("Edit", (mEdit | !mScenario.isHasInverters()));
                startActivity(intent);
            }
        });

        mHouseButton = requireView().findViewById(R.id.houseButton);
        mHouseButton.setOnClickListener(v -> {
            if (mScenarioID == 0L) {
                Snackbar.make(requireActivity().getWindow().getDecorView().getRootView(),
                        "Save before configuring load profile",
                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
            else {
                Intent intent = new Intent(getActivity(), LoadProfileActivity.class);
                intent.putExtra("ScenarioID", mScenarioID);
                intent.putExtra("ScenarioName", mScenario.getScenarioName());
                intent.putExtra("Edit", mEdit);
                startActivity(intent);
            }
        });

        mBatteryButton = requireView().findViewById(R.id.batteryButton);
        mBatteryButton.setOnClickListener(v -> {
            //Creating the instance of PopupMenu
            PopupMenu popup = new PopupMenu(requireActivity(), mBatteryButton, Gravity.CENTER_HORIZONTAL);
            //Inflating the Popup using xml file
            popup.getMenuInflater()
                    .inflate(R.menu.popup_menu_scenario, popup.getMenu());
            MenuItem divertMenuItem = popup.getMenu().findItem(R.id.divert);
            divertMenuItem.setVisible(false);

            //registering popup with OnMenuItemClickListener
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.settings) {
                    if (!mScenario.isHasInverters()) {
                        Snackbar.make(requireActivity().getWindow().getDecorView().getRootView(),
                                "Add at least one inverter before adding batteries",
                                Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    }
                    else {
                        Intent intent = new Intent(getActivity(), BatterySettingsActivity.class);
                        intent.putExtra("ScenarioID", mScenarioID);
                        intent.putExtra("ScenarioName", mScenario.getScenarioName());
                        intent.putExtra("Edit", mEdit | !mScenario.isHasBatteries());
                        startActivity(intent);
                    }
                }
                else {
                    if (!mScenario.isHasBatteries()) {
                        Snackbar.make(requireActivity().getWindow().getDecorView().getRootView(),
                                "Add at least one battery before load shifting",
                                Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    }
                    else {
                        Intent intent = new Intent(getActivity(), BatteryChargingActivity.class);
                        intent.putExtra("ScenarioID", mScenarioID);
                        intent.putExtra("ScenarioName", mScenario.getScenarioName());
                        intent.putExtra("Edit", mEdit | !mScenario.isHasBatteries());
                        startActivity(intent);
                    }
                }
                return true;
            });
            try {
                Field[] fields = popup.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(popup);
                        Class<?> classPopupHelper;
                        if (menuPopupHelper != null) {
                            classPopupHelper = Class.forName(menuPopupHelper
                                    .getClass().getName());
                            Method setForceIcons = classPopupHelper.getMethod(
                                    "setForceShowIcon", boolean.class);
                            setForceIcons.invoke(menuPopupHelper, true);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            popup.show(); //showing popup menu
        }); //closing the setOnClickListener method

        mTankButton = requireView().findViewById(R.id.tankButton);
        mTankButton.setOnClickListener(v -> {
            //Creating the instance of PopupMenu
            PopupMenu popup = new PopupMenu(requireActivity(), mTankButton);
            //Inflating the Popup using xml file
            popup.getMenuInflater()
                    .inflate(R.menu.popup_menu_scenario, popup.getMenu());

            //registering popup with OnMenuItemClickListener
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.settings) {
                    if (!mScenario.isHasLoadProfiles()) {
                        if (!(null == getView())) Snackbar.make(getView(),
                                "Create a load profile before configuring hot water system",
                                Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    }
                    else {
                        Intent intent = new Intent(getActivity(), WaterSettingsActivity.class);
                        intent.putExtra("ScenarioID", mScenarioID);
                        intent.putExtra("ScenarioName", mScenario.getScenarioName());
                        intent.putExtra("Edit", mEdit | !mScenario.isHasHWSystem());
                        startActivity(intent);
                    }
                }
                if (item.getItemId() == R.id.divert){
                    if (!mScenario.isHasHWSystem()) {
                        if (!(null == getView())) Snackbar.make(getView(),
                                "Configure hot water system, before diverting to it",
                                Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    }
                    else {
                        if (!(null == mScenarioComponents)){
                            HWDivert hwDivert = mScenarioComponents.hwDivert;
                            if (!(null == getActivity()) && !((ScenarioActivity)getActivity()).isSimulationInProgress()) {
                                if (null == hwDivert) {
                                    hwDivert = new HWDivert();
                                    hwDivert.setActive(false);
                                    mScenarioComponents.hwDivert = hwDivert;
                                }
                                hwDivert.setActive(!hwDivert.isActive());
                                mViewModel.saveHWDivert(mScenarioID, hwDivert);
                                new Thread(() -> {
                                    mViewModel.deleteSimulationDataForScenarioID(mScenarioID);
                                    mViewModel.deleteCostingDataForScenarioID(mScenarioID);
                                    SimulatorLauncher.simulateIfNeeded(getActivity().getApplicationContext());
                                }).start();
                                mMainHandler.postDelayed(this::updateScenarioComponents, 2000);
                                if (!(null == getView())) Snackbar.make(getView(),
                                        "Updating hot water diversion to " + (hwDivert.isActive() ? "enabled" : "disabled"),
                                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
                            }
                            else {
                                if (!(null == getView())) Snackbar.make(getView(),
                                        "Simulation in progress. Try again in a moment",
                                        Snackbar.LENGTH_LONG).setAction("Action", null).show();
                            }
                        }
                    }
                }
                else if (item.getItemId() == R.id.schedule) {
                    if (!mScenario.isHasHWSystem()) {
                        if (!(null == getView())) Snackbar.make(getView(),
                                "Configure hot water system, before diverting to it",
                                Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    }
                    else {
                        Intent intent = new Intent(getActivity(), WaterScheduleActivity.class);
                        intent.putExtra("ScenarioID", mScenarioID);
                        intent.putExtra("ScenarioName", mScenario.getScenarioName());
                        intent.putExtra("Edit", mEdit | !mScenario.isHasHWSystem());
                        startActivity(intent);
                    }
                }
                return true;
            });
            try {
                Field[] fields = popup.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(popup);
                        Class<?> classPopupHelper;
                        if (menuPopupHelper != null) {
                            classPopupHelper = Class.forName(menuPopupHelper
                                    .getClass().getName());
                            Method setForceIcons = classPopupHelper.getMethod(
                                    "setForceShowIcon", boolean.class);
                            setForceIcons.invoke(menuPopupHelper, true);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            popup.show(); //showing popup menu
        }); //closing the setOnClickListener method

        mCarButton = requireView().findViewById(R.id.carButton);
        mCarButton.setOnClickListener(v -> {
            //Creating the instance of PopupMenu
            PopupMenu popup = new PopupMenu(requireActivity(), mCarButton);
            //Inflating the Popup using xml file
            popup.getMenuInflater()
                    .inflate(R.menu.popup_menu_scenario, popup.getMenu());
            MenuItem settingsMenuItem = popup.getMenu().findItem(R.id.settings);
            settingsMenuItem.setVisible(false);

            //registering popup with OnMenuItemClickListener
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.schedule) {
                    if (!mScenario.isHasLoadProfiles()) {
                        if (!(null == getView())) Snackbar.make(getView(),
                                "Create a load profile before configuring EV charging",
                                Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    } else {
                        Intent intent = new Intent(getActivity(), EVScheduleActivity.class);
                        intent.putExtra("ScenarioID", mScenarioID);
                        intent.putExtra("ScenarioName", mScenario.getScenarioName());
                        intent.putExtra("Edit", mEdit | !mScenario.isHasEVCharges());
                        startActivity(intent);
                    }
                }
                if (item.getItemId() == R.id.divert) {
                    if (!mScenario.isHasInverters()) {
                        if (!(null == getView())) Snackbar.make(getView(),
                                "Configure an inverter before configuring EV diversion",
                                Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    }else {
                        Intent intent = new Intent(getActivity(), EVDivertActivity.class);
                        intent.putExtra("ScenarioID", mScenarioID);
                        intent.putExtra("ScenarioName", mScenario.getScenarioName());
                        intent.putExtra("Edit", mEdit | !mScenario.isHasEVCharges());
                        startActivity(intent);
                    }
                }
                return true;
            });
            try {
                Field[] fields = popup.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(popup);
                        Class<?> classPopupHelper;
                        if (menuPopupHelper != null) {
                            classPopupHelper = Class.forName(menuPopupHelper
                                    .getClass().getName());
                            Method setForceIcons = classPopupHelper.getMethod(
                                    "setForceShowIcon", boolean.class);
                            setForceIcons.invoke(menuPopupHelper, true);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            popup.show(); //showing popup menu
        }); //closing the setOnClickListener method
    }

    private void updateView() {
        if (!(null == mTableLayout) && !(null == mScenario) && !(null == getActivity())) {
            mTableLayout.removeAllViews();
            updateButtons();
            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams scenarioParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            scenarioParams.topMargin = 10;
            scenarioParams.rightMargin = 10;

            mHelpTable.removeAllViews();
            if (!mScenario.isHasLoadProfiles()) {
                mHelpTable.setShrinkAllColumns(true);
                mHelpTable.setStretchAllColumns(false);

                TextView help = new TextView(getActivity());
                help.setSingleLine(false);
                help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

                help.setText(R.string.NoScenarioText);

                mHelpTable.addView(help);
            }

            TableRow tableRow = new TableRow(getActivity());
            MaterialTextView a = new MaterialTextView(getActivity());
            if (mEdit) {
                // CREATE TABLE ROWS
                a.setText(R.string.UsageName);
                a.setPadding(10,25, 0, 25);
                EditText b = new EditText(getActivity());
                b.setText(mScenario.getScenarioName());
                b.setEnabled(true);
                b.setPadding(0,25, 0, 25);
                b.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        mScenario.setScenarioName(s.toString());
                        if (mScenarioNames.contains(s.toString())) {
                            if (!(null == getView())) Snackbar.make(getView(),
                                            s + " already exists. Change before saving", Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        }
                        ((ScenarioActivity) requireActivity()).setSaveNeeded(true);
                    }
                });
                tableRow.addView(a);
                tableRow.addView(b);
                mTableLayout.addView(tableRow);
            }
            else {
                // CREATE TABLE ROWS
                TextView b = new TextView(getActivity());
                b.setSingleLine(false);
                if (!(null == mBestCosting)) {
                    DecimalFormat df = new DecimalFormat("#.00");
                    String price =  "Best cost, €" + df.format(mBestCosting.getNet() /100);
                    a.setText(price);
                    String value = mBestCosting.getFullPlanName();
                    b.setText(value);
                }
                else {
                    a.setText(R.string.NoBestCost);
                    b.setText(R.string.BestSupplier);
                }
                a.setLayoutParams(scenarioParams);
                a.setPadding(0,20,0,20);
                b.setLayoutParams(scenarioParams);
                b.setPadding(0,20,0,20);
                tableRow.addView(a);
                tableRow.addView(b);
                mTableLayout.addView(tableRow);

                if (!(null == mSimKPIs)) {
                    tableRow = new TableRow(getActivity());
                    if (mSimKPIs.generated > 0) {
                        Map<String, Double> pieMap = new HashMap<>();
                        pieMap.put("Self Consume", mSimKPIs.generated - mSimKPIs.sold);
                        pieMap.put("Sold", mSimKPIs.sold);
                        PieChart pc = getPieChart("", pieMap, true);
                        tableRow.addView(pc);
                        pieMap = new HashMap<>();
                        pieMap.put("Self supplied", mSimKPIs.totalLoad - mSimKPIs.bought);
                        pieMap.put("Sold", mSimKPIs.bought);
                        PieChart pc2 = getPieChart("", pieMap, true);
                        tableRow.addView(pc2);
                    }
                    else {
                        MaterialTextView noGen = new MaterialTextView(getActivity());
                        noGen.setText(R.string.No_generation);
                        noGen.setPadding(0,20,0,20);
                        tableRow.addView(noGen);
                        MaterialTextView noSelf = new MaterialTextView(getActivity());
                        noSelf.setText(R.string.purchased_100);
                        noSelf.setPadding(0,20,0,20);
                        tableRow.addView(noSelf);
                    }
                    mTableLayout.addView(tableRow);

                    tableRow = new TableRow(getActivity());
                    Map<String, Double> pieMap = new HashMap<>();
                    pieMap.put("\uD83C\uDFE0", mSimKPIs.house);
                    pieMap.put("\uD83D\uDCA6", mSimKPIs.h20);
                    pieMap.put("\uD83D\uDE97", mSimKPIs.ev);
                    PieChart pc = getPieChart("Load", pieMap, false);
                    tableRow.addView(pc);
                    if (mSimKPIs.generated > 0) {
                        pieMap = new HashMap<>();
                        pieMap.put("\uD83D\uDE97", mSimKPIs.evDiv);
                        pieMap.put("\uD83D\uDCA6", mSimKPIs.h2oDiv);
                        pieMap.put("$$", mSimKPIs.sold);
                        pieMap.put("\uD83C\uDFE0", mSimKPIs.pvToLoad);
                        pieMap.put("\uD83D\uDD0B", mSimKPIs.pvToCharge);
                        PieChart pc2 = getPieChart("PV", pieMap, false);
                        tableRow.addView(pc2);
                    }
                    else {
                        MaterialTextView noGen = new MaterialTextView(getActivity());
                        noGen.setText("");
                        noGen.setPadding(0,20,0,20);
                        tableRow.addView(noGen);
                    }
                    mTableLayout.addView(tableRow);

                }
            }
        }
    }

    private PieChart getPieChart(String title, Map<String, Double> pieMap, boolean showPercent) {
        PieChart pieChart = new PieChart(getActivity());

        pieChart.getDescription().setEnabled(true);
        pieChart.setRotationEnabled(true);
        pieChart.setDragDecelerationFrictionCoef(0.9f);
        pieChart.setRotationAngle(270);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.setHoleColor(Color.parseColor("#000000"));

        ArrayList<PieEntry> pieEntries = new ArrayList<>();

        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#304567"));
        colors.add(Color.parseColor("#309967"));
        colors.add(Color.parseColor("#476567"));
        colors.add(Color.parseColor("#890567"));
        colors.add(Color.parseColor("#a35567"));
        colors.add(Color.parseColor("#ff5f67"));
        colors.add(Color.parseColor("#3ca567"));

        for(String type: pieMap.keySet()){
            pieEntries.add(new PieEntry(Objects.requireNonNull(pieMap.get(type)).floatValue(), type));
        }
        PieDataSet pieDataSet = new PieDataSet(pieEntries,"");
        pieDataSet.setValueTextSize(12f);
        pieDataSet.setColors(colors);
        PieData pieData = new PieData(pieDataSet);

        pieData.setDrawValues(showPercent);
        pieData.setValueFormatter(new PercentFormatter(pieChart));
        pieChart.setData(pieData);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawEntryLabels(false);

        Legend legend = pieChart.getLegend();
//        int color = Color.getColor("?android:textColorPrimary");
        int color = 0;
        if (!(null == getContext())) color = ContextCompat.getColor(getContext(), R.color.colorPrimaryDark);
        legend.setTextColor(color);

        pieChart.getDescription().setText(title);
        pieChart.getDescription().setTextSize(12f);
        pieChart.getDescription().setTextColor(color);
        pieChart.invalidate();
        pieChart.setMinimumHeight(500);
        return pieChart;
    }

    private void updateButtons() {
        /*
         It is possible that the view has not been created yet
         If the view is not ready, then this method will be called again when it is
        */
        View fragmentView = getView();
        if (null == fragmentView) return;

        boolean hasInverters = false;
        boolean hasLoadProfile = false;
        boolean hasPanels = false;
        boolean hasBatteries = false;
        boolean hasLoadShifts = false;
        boolean hasHWSystem = false;
        boolean hasHWDivert = false;
        boolean hasHWSchedules = false;
        boolean hasEVDivert = false;
        boolean hasEVCharges = false;
        if (mScenario.isHasInverters())
            if (!(null == mScenarioComponents) && mScenarioComponents.inverters.size() > 0)
                hasInverters = true;
        if (mScenario.isHasLoadProfiles()) hasLoadProfile = true;
        if (mScenario.isHasPanels())
            if (!(null == mScenarioComponents) && mScenarioComponents.panels.size() > 0)
                hasPanels = true;
        if (mScenario.isHasBatteries())
            if (!(null == mScenarioComponents) && mScenarioComponents.batteries.size() > 0)
                hasBatteries = true;
        if (mScenario.isHasLoadShifts())
            if (!(null == mScenarioComponents) && mScenarioComponents.loadShifts.size() > 0)
                hasLoadShifts = true;
        if (mScenario.isHasHWSystem()) hasHWSystem = true;
        if (mScenario.isHasHWDivert())
            if(!(null == mScenarioComponents) && !(null == mScenarioComponents.hwDivert))
                hasHWDivert = mScenarioComponents.hwDivert.isActive();
        if (mScenario.isHasHWSchedules())
            if (!(null == mScenarioComponents) && mScenarioComponents.hwSchedules.size() > 0)
                hasHWSchedules = true;
        if (mScenario.isHasEVDivert())
            if (!(null == mScenarioComponents) && mScenarioComponents.evDiverts.size() > 0)
                hasEVDivert = true;
        if (mScenario.isHasEVCharges())
            if (!(null == mScenarioComponents) && mScenarioComponents.evCharges.size() > 0)
                hasEVCharges = true;
        ImageView panelSun = fragmentView.findViewById(R.id.panelSun);
        ImageView panelLock = fragmentView.findViewById(R.id.panelLock);
        ImageView panelTick = fragmentView.findViewById(R.id.panelTick);
        if (mScenarioID == 0L || !(mScenario.isHasPanels())) mHasPanelData = false;
        panelSun.setVisibility(mHasPanelData ? View.VISIBLE : View.GONE);
        panelLock.setVisibility(hasInverters ? View.GONE: View.VISIBLE);
        panelTick.setVisibility(hasPanels ? View.VISIBLE : View.GONE);
        mPanelButton.setBackgroundColor(0);

        ImageView inverterTick = fragmentView.findViewById(R.id.inverterTick);
        ImageView inverterLock = fragmentView.findViewById(R.id.inverterLock);
        inverterLock.setVisibility(hasLoadProfile ? View.GONE : View.VISIBLE);
        inverterTick.setVisibility(hasInverters ? View.VISIBLE : View.GONE);
        mInverterButton.setBackgroundColor(0);

        ImageView houseTick = fragmentView.findViewById(R.id.houseTick);
        ImageView houseLock = fragmentView.findViewById(R.id.houseLock);
        houseTick.setVisibility(hasLoadProfile ? View.VISIBLE : View.GONE);
        houseLock.setVisibility((mScenarioID == 0) ? View.VISIBLE : View.GONE);
        if (hasLoadProfile) mHouseButton.setImageResource(R.drawable.housetick);
        else mHouseButton.setImageResource(R.drawable.house);
        mHouseButton.setBackgroundColor(0);

        ImageView batteryLock = fragmentView.findViewById(R.id.batteryLock);
        ImageView batterySettings = fragmentView.findViewById(R.id.batterySet);
        ImageView batterySchedule = fragmentView.findViewById(R.id.batteryScheduled);
        batteryLock.setVisibility(hasInverters ? View.GONE : View.VISIBLE);
        batterySettings.setVisibility(hasBatteries ? View.VISIBLE : View.GONE);
        batterySchedule.setVisibility(hasLoadShifts ? View.VISIBLE : View.GONE);
        mBatteryButton.setBackgroundColor(0);

        ImageView tankLock = fragmentView.findViewById(R.id.tankLock);
        ImageView tankSet = fragmentView.findViewById(R.id.tankSet);
        ImageView tankDivert = fragmentView.findViewById(R.id.tankDivert);
        ImageView tankScheduled = fragmentView.findViewById(R.id.tankScheduled);
        tankLock.setVisibility(hasLoadProfile ? View.GONE : View.VISIBLE);
        tankSet.setVisibility(hasHWSystem ? View.VISIBLE : View.GONE);
        tankDivert.setVisibility(hasHWDivert ? View.VISIBLE : View.GONE);
        tankScheduled.setVisibility(hasHWSchedules ? View.VISIBLE : View.GONE);
        if (hasHWSystem && (hasHWSchedules || hasHWDivert))
            mTankButton.setImageResource(R.drawable.waterwarm);
        else mTankButton.setImageResource(R.drawable.watercold);
        mTankButton.setBackgroundColor(0);

        ImageView carLock = fragmentView.findViewById(R.id.carLock);
        ImageView carDivert = fragmentView.findViewById(R.id.carDivert);
        ImageView carScheduled = fragmentView.findViewById(R.id.carScheduled);
        carLock.setVisibility(hasLoadProfile ? View.GONE : View.VISIBLE);
        carDivert.setVisibility(hasEVDivert ? View.VISIBLE : View.GONE);
        carScheduled.setVisibility(hasEVCharges ? View.VISIBLE : View.GONE);
        if (hasEVDivert || hasEVCharges)
            mCarButton.setImageResource(R.drawable.ev_on);
        else mCarButton.setImageResource(R.drawable.ev_off);
        mCarButton.setBackgroundColor(0);
    }

    public void setEditMode(boolean ed) {
        if (!mEdit) {
            mEdit = ed;
            ScenarioActivity scenarioActivity = ((ScenarioActivity) getActivity());
            if (!(null == scenarioActivity)) scenarioActivity.setEdit();
        }
    }
}