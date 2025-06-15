/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.homeassistant;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.importers.InverterDateRange;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FixHADataActivity extends AppCompatActivity {

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Spinner mDataSourceSpinner;
    private TextView mSelectedDatesText;
    private MaterialButton mSelectDatesButton;
    private MaterialButton mFixDataButton;
    private MaterialButton mCancelButton;
    
    private ComparisonUIViewModel mViewModel;
    private LocalDateTime mSelectedStart;
    private LocalDateTime mSelectedEnd;
    private List<String> mAvailableDataSources;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fix_ha_data);

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);

        initializeViews();
        setupDataSources();
        setupButtons();
    }

    private void initializeViews() {
        mDataSourceSpinner = findViewById(R.id.dataSourceSpinner);
        mSelectedDatesText = findViewById(R.id.selectedDatesText);
        mSelectDatesButton = findViewById(R.id.selectDatesButton);
        mFixDataButton = findViewById(R.id.fixDataButton);
        mCancelButton = findViewById(R.id.cancelButton);

        mSelectedDatesText.setText("No dates selected");
        mFixDataButton.setEnabled(false);
    }

    private void setupDataSources() {
        mAvailableDataSources = new ArrayList<>();
        
        // Check for AlphaESS data
        try {
            List<InverterDateRange> alphaRanges = 
                mViewModel.getLiveDateRanges(ComparisonUIViewModel.Importer.ALPHAESS).getValue();
            if (alphaRanges != null && !alphaRanges.isEmpty()) {
                mAvailableDataSources.add("AlphaESS");
            }
        } catch (Exception e) {
            // Ignore exceptions when checking for data
        }
        
        // Check for ESBN HDF data
        try {
            List<InverterDateRange> esbnRanges = 
                mViewModel.getLiveDateRanges(ComparisonUIViewModel.Importer.ESBNHDF).getValue();
            if (esbnRanges != null && !esbnRanges.isEmpty()) {
                mAvailableDataSources.add("ESBN Smart Meter");
            }
        } catch (Exception e) {
            // Ignore exceptions when checking for data
        }

        if (mAvailableDataSources.isEmpty()) {
            mAvailableDataSources.add("No data sources available");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, mAvailableDataSources);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mDataSourceSpinner.setAdapter(adapter);
    }

    private void setupButtons() {
        mSelectDatesButton.setOnClickListener(v -> showDatePicker());
        
        mFixDataButton.setOnClickListener(v -> {
            String selectedSource = (String) mDataSourceSpinner.getSelectedItem();
            if (selectedSource != null && mSelectedStart != null && mSelectedEnd != null) {
                performDataFix(selectedSource);
            }
        });
        
        mCancelButton.setOnClickListener(v -> finish());
    }

    private void showDatePicker() {
        MaterialDatePicker.Builder<Pair<Long, Long>> builder = MaterialDatePicker.Builder
                .dateRangePicker()
                .setTitleText("Select date range to fix");
        
        MaterialDatePicker<Pair<Long, Long>> picker = builder.build();
        
        picker.addOnPositiveButtonClickListener(selection -> {
            mSelectedStart = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(selection.first), 
                ZoneId.ofOffset("UTC", ZoneOffset.UTC));
            mSelectedEnd = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(selection.second), 
                ZoneId.ofOffset("UTC", ZoneOffset.UTC)).plusDays(1);
            
            String startStr = mSelectedStart.format(DISPLAY_FORMAT);
            String endStr = mSelectedEnd.minusDays(1).format(DISPLAY_FORMAT);
            mSelectedDatesText.setText(startStr + " to " + endStr);
            
            updateFixButtonState();
        });
        
        picker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void updateFixButtonState() {
        boolean hasValidSource = mDataSourceSpinner.getSelectedItem() != null && 
            !mDataSourceSpinner.getSelectedItem().toString().equals("No data sources available");
        boolean hasValidDates = mSelectedStart != null && mSelectedEnd != null;
        
        mFixDataButton.setEnabled(hasValidSource && hasValidDates);
    }

    private void performDataFix(String selectedSource) {
        // TODO: Implement the actual data fixing logic
        // This would involve:
        // 1. Reading data from the selected source for the specified date range
        // 2. Identifying gaps or errors in HA data for the same period
        // 3. Replacing or filling the HA data with data from the selected source
        // 4. Updating the database with the fixed data
        
        String message = String.format("Data fix requested:\nSource: %s\nDates: %s to %s\n\nActual implementation coming soon!",
            selectedSource, 
            mSelectedStart.format(DISPLAY_FORMAT),
            mSelectedEnd.minusDays(1).format(DISPLAY_FORMAT));
        
        Snackbar.make(findViewById(android.R.id.content), 
            "Fix operation initiated for " + selectedSource, 
            Snackbar.LENGTH_LONG).show();
        
        // For now, just show a success message and close
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Fix HA Data")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .show();
    }
}