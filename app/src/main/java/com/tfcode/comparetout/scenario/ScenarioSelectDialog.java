package com.tfcode.comparetout.scenario;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.scenario.Scenario;

import java.util.List;

public class ScenarioSelectDialog extends Dialog  {

    private TableLayout mTableLayout;
    private ProgressBar mProgressBar;
    private Handler mMainHandler;
    private final ToutcRepository mToutcRepository;
    private final int mAspect;
    private final ScenarioSelectDialogListener mScenarioSelectDialogListener;

    public static final int LOAD_PROFILE = 0;
    public static final int PANEL = 1;
    public static final int INVERTER = 2;
    public static final int BATTERY = 3;
    public static final int BATTERY_SHIFT = 4;
    public static final int TANK = 5;
    public static final int TANK_SHIFT = 6;
    public static final int TANK_DIVERT = 7;
    public static final int CAR_SHIFT = 8;
    public static final int CAR_DIVERT = 9;

    public ScenarioSelectDialog(@NonNull Context context, int aspect, ScenarioSelectDialogListener listener) {
        super(context);
        mToutcRepository = new ToutcRepository((Application) context.getApplicationContext());
        mAspect = aspect;
        mScenarioSelectDialogListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_scenario_select);
        createProgressBar();
        mTableLayout = findViewById(R.id.scenario_select_table);
        mProgressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            List<Scenario> scenarios = null;
            try {
                // Load from DB
                scenarios = mToutcRepository.getScenarios();
                //
            } catch (Exception e) {
                e.printStackTrace();
            }
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
            List<Scenario> finalScenarios = scenarios;
            mMainHandler.post(() -> updateView(finalScenarios));
        }).start();
    }

    private void updateView(List<Scenario> scenarios){
        System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
        mTableLayout.removeAllViews();
        mTableLayout.setShrinkAllColumns(false);
        mTableLayout.setStretchAllColumns(true);
        mTableLayout.setColumnShrinkable(0, true);
        mTableLayout.setColumnStretchable(0, false);

        // Title row
        {
            TableRow titleRow = new TableRow(getContext());
            TextView scenarioTitle = new TextView(getContext());
            scenarioTitle.setText(R.string.scenario_with_aspect);
            titleRow.setGravity(Gravity.CENTER_HORIZONTAL);
            scenarioTitle.setGravity(Gravity.CENTER_HORIZONTAL);
            titleRow.addView(scenarioTitle);
            mTableLayout.addView(titleRow);
        }

        // Scenario rows
        for (Scenario scenario: scenarios){
            TableRow tableRow = new TableRow(getContext());
            Button button = new Button(getContext());
            button.setText(scenario.getScenarioName());
            button.setOnClickListener(v -> {
                mScenarioSelectDialogListener.scenarioSelected(scenario.getScenarioIndex());
                dismiss();
            });

            switch (mAspect){
                case LOAD_PROFILE:
                    if(scenario.isHasLoadProfiles()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case PANEL:
                    if(scenario.isHasPanels()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case INVERTER:
                    if(scenario.isHasInverters()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case BATTERY:
                    if(scenario.isHasBatteries()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case BATTERY_SHIFT:
                    if(scenario.isHasLoadShifts()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case TANK:
                    if(scenario.isHasHWSystem()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case TANK_SHIFT:
                    if(scenario.isHasHWSchedules()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case TANK_DIVERT:
                    if(scenario.isHasHWDivert()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case CAR_SHIFT:
                    if(scenario.isHasEVCharges()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
                case CAR_DIVERT:
                    if(scenario.isHasEVDivert()) {
                        tableRow.addView(button);
                        mTableLayout.addView(tableRow);
                    }
                    break;
            }
        }

        // And the cancel button
        {
            TableRow tableRow = new TableRow(getContext());
            Button button = new Button(getContext());
            button.setText(R.string.cancel);
            button.setOnClickListener(v -> dismiss());
            tableRow.addView(button);
            mTableLayout.addView(tableRow);
        }
    }

    private void createProgressBar() {
        mProgressBar = new ProgressBar(this.getContext(), null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.scenario_select);
        ConstraintSet set = new ConstraintSet();

        mProgressBar.setId(View.generateViewId());  // cannot set id after add
        constraintLayout.addView(mProgressBar,0);
        set.clone(constraintLayout);
        set.connect(mProgressBar.getId(), ConstraintSet.TOP, constraintLayout.getId(), ConstraintSet.TOP, 60);
        set.connect(mProgressBar.getId(), ConstraintSet.BOTTOM, constraintLayout.getId(), ConstraintSet.BOTTOM, 60);
        set.connect(mProgressBar.getId(), ConstraintSet.RIGHT, constraintLayout.getId(), ConstraintSet.RIGHT, 60);
        set.connect(mProgressBar.getId(), ConstraintSet.LEFT, constraintLayout.getId(), ConstraintSet.LEFT, 60);
        set.applyTo(constraintLayout);
        mProgressBar.setVisibility(View.GONE);

        mMainHandler = new Handler(Looper.getMainLooper());
    }
}
