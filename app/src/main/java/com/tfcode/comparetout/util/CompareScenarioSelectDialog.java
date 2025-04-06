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

package com.tfcode.comparetout.util;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.google.android.material.button.MaterialButton;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ComparisonSenarioRow;
import com.tfcode.comparetout.model.ToutcRepository;

import java.util.List;

public class CompareScenarioSelectDialog extends Dialog  {

    private TableLayout mTableLayout;
    private ProgressBar mProgressBar;
    private Handler mMainHandler;
    private final ToutcRepository mToutcRepository;
    private final CompareScenarioSelectDialogListener mScenarioSelectDialogListener;

    public CompareScenarioSelectDialog(@NonNull Context context, CompareScenarioSelectDialogListener listener) {
        super(context);
        mToutcRepository = new ToutcRepository((Application) context.getApplicationContext());
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
            List<ComparisonSenarioRow> scenarios = null;
            try {
                // Load from DB
                scenarios = mToutcRepository.getCompareScenarios();
                //
            } catch (Exception e) {
                e.printStackTrace();
            }
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
            List<ComparisonSenarioRow> finalScenarios = scenarios;
            mMainHandler.post(() -> updateView(finalScenarios));
        }).start();
    }

    private void updateView(List<ComparisonSenarioRow> scenarios) {
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

        // No comparison row
        {
            TableRow tableRow = new TableRow(getContext());
            MaterialButton button = new MaterialButton(getContext());
            button.setText("Remove comparison");
            button.setOnClickListener(v -> {
                mScenarioSelectDialogListener.scenarioSelected("None", null, 0L);
                dismiss();
            });

            tableRow.addView(button);
            mTableLayout.addView(tableRow);
        }

        // Scenario rows
        for (ComparisonSenarioRow scenario: scenarios){
            TableRow tableRow = new TableRow(getContext());
            MaterialButton button = new MaterialButton(getContext());
            button.setText(scenario.sysSN);
            button.setOnClickListener(v -> {
                mScenarioSelectDialogListener.scenarioSelected(scenario.sysSN, scenario.category, scenario.scenarioID);
                dismiss();
            });

            tableRow.addView(button);
            mTableLayout.addView(tableRow);
        }

        // And the cancel button
        {
            TableRow tableRow = new TableRow(getContext());
            MaterialButton button = new MaterialButton(getContext());
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
