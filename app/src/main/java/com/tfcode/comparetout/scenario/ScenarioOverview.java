package com.tfcode.comparetout.scenario;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

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
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.scenario.loadprofile.LoadProfileActivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

public class ScenarioOverview extends Fragment {

    private ActionBar mActionBar;

    private ComparisonUIViewModel mViewModel;
    private ImageButton mPanelButton;
    private ImageButton mInverterButton;
    private ImageButton mHouseButton;
    private ImageButton mBatteryButton;
    private ImageButton mTankButton;
    private ImageButton mCarButton;
    private TableLayout mTableLayout;
    private Long mScenarioID;
    private Scenario mScenario;
    private List<Scenario> mScenarios;
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
        if (mEdit) ((ScenarioActivity) requireActivity()).setSaveNeeded(true);

        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getAllScenarios().observe(this, scenarios -> {
            mScenarios = scenarios;
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
            mActionBar.setTitle("Usage: " + mScenario.getScenarioName());
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
        SimulatorLauncher.simulateIfNeeded(getContext());
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
                   ((ScenarioActivity) requireActivity()).setSaveNeeded(false);
                    return (false);
                }
                return true;
            }
        });
    }

    private void setupButtons() {
        mPanelButton = requireView().findViewById(R.id.panelButton);
        mPanelButton.setOnClickListener(v -> Snackbar.make(requireActivity().getWindow().getDecorView().getRootView(),
          "Panel editor to be done",
           Snackbar.LENGTH_LONG).setAction("Action", null).show());
        mInverterButton = requireView().findViewById(R.id.inverterButton);
        mInverterButton.setOnClickListener(v -> Snackbar.make(requireActivity().getWindow().getDecorView().getRootView(),
                "Inverter editor to be done",
                Snackbar.LENGTH_LONG).setAction("Action", null).show());
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
                Toast.makeText(
                        requireActivity(),
                        "You Clicked : " + item.getTitle(),
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            });
            try {
                Field[] fields = popup.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(popup);
                        Class<?> classPopupHelper = Class.forName(menuPopupHelper
                                .getClass().getName());
                        Method setForceIcons = classPopupHelper.getMethod(
                                "setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
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
                Toast.makeText(
                        requireActivity(),
                        "You Clicked : " + item.getTitle(),
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            });
            try {
                Field[] fields = popup.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(popup);
                        Class<?> classPopupHelper = Class.forName(menuPopupHelper
                                .getClass().getName());
                        Method setForceIcons = classPopupHelper.getMethod(
                                "setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
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
                Toast.makeText(
                        requireActivity(),
                        "You Clicked : " + item.getTitle(),
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            });
            try {
                Field[] fields = popup.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if ("mPopup".equals(field.getName())) {
                        field.setAccessible(true);
                        Object menuPopupHelper = field.get(popup);
                        Class<?> classPopupHelper = Class.forName(menuPopupHelper
                                .getClass().getName());
                        Method setForceIcons = classPopupHelper.getMethod(
                                "setForceShowIcon", boolean.class);
                        setForceIcons.invoke(menuPopupHelper, true);
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
        if (!(null == mTableLayout) && !(null == mScenario)) {
            mTableLayout.removeAllViews();
            updateButtons();
            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams scenarioParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            scenarioParams.topMargin = 10;
            scenarioParams.rightMargin = 10;

            if (mScenario.isHasLoadProfiles()) {
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
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            mScenario.setScenarioName(s.toString());
                            System.out.println("Scenario name changed to : " + mScenario.getScenarioName());
                            if (findByName(mScenarios, s.toString()) != 0) {
                                Snackbar.make(getView(),
                                                s.toString() + " already exists. Change before saving", Snackbar.LENGTH_LONG)
                                        .setAction("Action", null).show();
                            }
                            ((ScenarioActivity) requireActivity()).setSaveNeeded(true);
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
            else {
                mTableLayout.setShrinkAllColumns(true);
                mTableLayout.setStretchAllColumns(false);

                TextView help = new TextView(getActivity());
                help.setSingleLine(false);
                help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

                help.setText(new StringBuilder()
                    .append("The usage profile captures how electricity is used. ")
                    .append("Use the six buttons to configure the usage profile.\n\n")
                    .append("Usage profiles must include at least a load profile for the house ")
                    .append("(yellow highlight when missing). ")
                    .append("Where you specify hourly, daily and monthly distribution as well as the ")
                    .append("annual and base loads. There are several sources for distribution.\n\n")
                    .append("This tab is also where you can describe your (planned) solar installation.")
                    .append("Panel(s) and inverter(s) are needed for electricity generation. \n\n")
                    .append("Batteries, hot water systems and electric vehicles can also be ")
                    .append("configured, scheduled and, for water and EV's, diverted.\n\n")
                    .append("Once configured and saved, a simulation will run. Simulation results will  ")
                    .append("appear in the details tab shortly after. Simulation progress is ")
                    .append("visible in the notification area. ").toString());

                mTableLayout.addView(help);
            }
        }
    }

    private void updateButtons() {
        if (mScenario.isHasPanels()) mPanelButton.setImageResource(R.drawable.solarpaneltick);
        else mPanelButton.setImageResource(R.drawable.solarpanel);
        if (mScenario.isHasInverters()) mInverterButton.setImageResource(R.drawable.invertertick);
        else mInverterButton.setImageResource(R.drawable.inverter);
        if (mScenario.isHasLoadProfiles()) {
            mHouseButton.setImageResource(R.drawable.housetick);
            mHouseButton.setBackgroundColor(Color.WHITE);
        }
        else {
            mHouseButton.setImageResource(R.drawable.house);
            mHouseButton.setBackgroundColor(Color.YELLOW);
        }

        if (mScenario.isHasBatteries()) {
            mBatteryButton.setImageResource(R.drawable.battery_set);
            if (mScenario.isHasLoadShifts()) mBatteryButton.setImageResource(R.drawable.battery_scheduled);
        }
        else mBatteryButton.setImageResource(R.drawable.battery_not_set);

        if (mScenario.isHasHWSystem()) {
            mTankButton.setImageResource(R.drawable.tank_set);
            if (mScenario.isHasHWSchedules()) {
                mTankButton.setImageResource(R.drawable.tank_set_scheduled);
                if (mScenario.isHasHWDivert()) mTankButton.setImageResource(R.drawable.tank_set_scheduled_diverted);
            }
            else if (mScenario.isHasHWDivert()) mTankButton.setImageResource(R.drawable.tank_set_scheduled_diverted);
        }
        else mTankButton.setImageResource(R.drawable.tank_not_set);

        if (mScenario.isHasEVCharges()) {
            mCarButton.setImageResource(R.drawable.car_scheduled);
            if (mScenario.isHasEVDivert()) mCarButton.setImageResource(R.drawable.car_scheduled_diverted);
        }
        else if (mScenario.isHasEVDivert()) mCarButton.setImageResource(R.drawable.car_diverted);
        else mCarButton.setImageResource(R.drawable.car_not_set);
    }

    public void setEditMode(boolean ed) {
        if (!mEdit) {
            mEdit = ed;
            ScenarioActivity scenarioActivity = ((ScenarioActivity) getActivity());
            if (!(null == scenarioActivity)) scenarioActivity.setEdit();
        }
    }
}