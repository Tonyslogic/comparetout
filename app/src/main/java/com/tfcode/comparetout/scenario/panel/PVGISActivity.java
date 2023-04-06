package com.tfcode.comparetout.scenario.panel;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PVGISActivity extends AppCompatActivity {

    private ActionBar mActionBar;
    private Handler mMainHandler;
    private ProgressBar mProgressBar;
    private TableLayout mTableLayout;
    private Long mPanelID = 0L;
    private Panel mPanel;
    private String mScenarioName = "";
    private boolean mEdit = false;
    private List<View> mEditFields;
    private ComparisonUIViewModel mViewModel;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pvgis);
        createProgressBar();
        mProgressBar.setVisibility(View.VISIBLE);

        Intent intent = getIntent();
        mPanelID = intent.getLongExtra("PanelID", 0L);
        mEdit = intent.getBooleanExtra("Edit", false);

        System.out.println("mPanelID = " +mPanelID);

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("PVGIS data grabber");

        mTableLayout = findViewById(R.id.pvgis_table);
        mTableLayout.setShrinkAllColumns(true);
        mTableLayout.setStretchAllColumns(true);

        mProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            mPanel = mViewModel.getPanelForID(mPanelID);
            if (!(null == mPanel)) System.out.println(mPanel.getAzimuth());
            else System.out.println("DOH!");
            mMainHandler.post(this::updateView);
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
        }).start();
        mEditFields = new ArrayList<>();
    }

    private void updateView() {
        System.out.println("PVGIS Activity, updateView"); // CREATE PARAM FOR MARGINING
        mTableLayout.removeAllViews();
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.topMargin = 2;
        params.rightMargin = 2;

        int integerType = InputType.TYPE_CLASS_NUMBER;
        int doubleType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        int stringType = InputType.TYPE_CLASS_TEXT;
        DecimalFormat df = new DecimalFormat("#.000");

        // CREATE TABLE ROWS
        mTableLayout.addView(createRow("Latitude", df.format(mPanel.getLatitude()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(df.format(mPanel.getLatitude())))) {
                    System.out.println("Latitude changed");
                    mPanel.setLatitude(getDoubleOrZero(s));
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Longitude", df.format(mPanel.getLongitude()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(df.format(mPanel.getLongitude())))) {
                    System.out.println("Longitude changed");
                    mPanel.setLongitude(getDoubleOrZero(s));
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Azimuth", String.valueOf(mPanel.getAzimuth()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mPanel.getAzimuth())))) {
                    System.out.println("Azimuth changed");
                    mPanel.setAzimuth(getIntegerOrZero(s));
                }
            }
        }, params, integerType));
        mTableLayout.addView(createRow("Slope", String.valueOf(mPanel.getSlope()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mPanel.getSlope())))) {
                    System.out.println("Slope changed");
                    mPanel.setSlope(getIntegerOrZero(s));
                }
            }
        }, params, integerType));

        TableRow tableRow = new TableRow(this);
        Button location = new Button(this);
        location.setText("Update location");
        Button download = new Button(this);
        download.setText("Download data");
        download.setOnClickListener(v -> saveAndFetch() );
        tableRow.addView(location);
        tableRow.addView(download);
        mTableLayout.addView(tableRow);
    }

    private TableRow createRow(String title, String initialValue, AbstractTextWatcher action, TableRow.LayoutParams params, int inputType){
        TableRow tableRow = new TableRow(this);
        TextView a = new TextView(this);
        a.setText(title);
        EditText b = new EditText(this);
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

    private void saveAndFetch() {
        new Thread(() -> {
            mViewModel.updatePanel(mPanel);
            mMainHandler.post(this::fetch);
        }).start();
    }

    private void fetch() {
        SimulatorLauncher.downloadAndStorePVGIS(this, mPanel.getPanelIndex());
        mProgressBar.setVisibility(View.VISIBLE);

//        ListenableFuture<List<WorkInfo>> work = WorkManager.getInstance(this).getWorkInfosForUniqueWork("PVGIS"); // ListenableFuture<List<WorkInfo>>
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("PVGIS")
            .observe(this, workInfos -> {
                for (WorkInfo workInfo: workInfos){
                    if (workInfo.getState() != null &&
                            workInfo.getState().isFinished() &&
                            workInfo.getTags().contains("com.tfcode.comparetout.scenario.panel.PVGISLoader")) {
                        System.out.println(workInfo.getTags().iterator().next());
                        mProgressBar.setVisibility(View.GONE);
                    }
                }
            });
    }

    @Override
    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce || !(mUnsavedChanges)) {
            super.onBackPressed();
            SimulatorLauncher.simulateIfNeeded(getApplicationContext());
            return;
        }

        this.mDoubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Unsaved changes. Please click BACK again to discard and exit", Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> mDoubleBackToExitPressedOnce =false, 2000);
    }

    // PROGRESS BAR
    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.pvgis_activity);
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