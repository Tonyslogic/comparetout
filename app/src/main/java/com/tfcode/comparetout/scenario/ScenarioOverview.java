package com.tfcode.comparetout.scenario;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TableLayout;

import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.priceplan.PricePlanActivity;

import java.util.List;

public class ScenarioOverview extends Fragment {

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

        mViewModel = new ViewModelProvider(requireActivity()).get(PricePlanNavViewModel.class);
        mViewModel.getAllScenarios().observe(this, scenarios -> {
            System.out.println("Observed a change in live scenario data " + scenarios.size());
            mScenario = findByID(scenarios, mScenarioID);
            System.out.println("Updated scenario to  " + mScenario.getScenarioName());
            updateView();
        });
    }

    private static Scenario findByID(List<Scenario> scenarios, Long id) {
        return scenarios.stream().filter(s -> id.equals(s.getId())).findFirst().orElse(null);
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
        mPanelButton = requireView().findViewById(R.id.panelButton);
        mInverterButton = requireView().findViewById(R.id.inverterButton);
        mHouseButton = requireView().findViewById(R.id.houseButton);
        mBatteryButton = requireView().findViewById(R.id.batteryButton);
        mTankButton = requireView().findViewById(R.id.tankButton);
        mCarButton = requireView().findViewById(R.id.carButton);
        mClockButton = requireView().findViewById(R.id.clockButton);
        mDivertButton = requireView().findViewById(R.id.divertButton);
        updateView();
    }

    private void updateView() {
        if (!(null == mTableLayout) && !(null == mScenario)) {
            System.out.println("Updating the scenario overview " + mScenario.isHasPanels());
            mTableLayout.removeAllViews();
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
    }
}