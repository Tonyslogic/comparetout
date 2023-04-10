package com.tfcode.comparetout.scenario.panel;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.snackbar.Snackbar;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelPVSummary;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.io.File;
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
    private boolean mPanelDataInDB = false;
    private boolean mFileCached = false;
    private boolean mLocationChanged = false;

    private static final String U1 = "https://re.jrc.ec.europa.eu/api/v5_2/seriescalc?lat="; // LATITUDE
    private static final String U2 = "&lon="; // LONGITUDE
    private static final String U3 = "&raddatabase=PVGIS-SARAH2&browser=1&outputformat=json&userhorizon=&usehorizon=1&angle="; //SLOPE
    private static final String U4 = "&aspect="; // AZIMUTH
    private static final String U5 = "&startyear=2020&endyear=2020&mountingplace=&optimalinclination=0&optimalangles=0&js=1&select_database_hourly=PVGIS-SARAH2&hstartyear=2020&hendyear=2020&trackingtype=0&hourlyangle="; // SLOPE
    private static final String U6 = "&hourlyaspect="; //AZIMUTH

    private long mDownloadID;

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher, as an instance variable.
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    mMainHandler.post(this::fetch);
                } else {
                    Snackbar.make(getWindow().getDecorView().getRootView(),
                                    "Unable to download, no permission!", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            });

    private BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Fetching the download id received with the broadcast
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            //Checking if the received broadcast is for our enqueued download by matching download id
            if (mDownloadID == id) {
                Snackbar.make(getWindow().getDecorView().getRootView(),
                                "Download complete, loading DB", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                scheduleLoad(context);
            }
        }
    };

    private void scheduleLoad(Context context) {
        SimulatorLauncher.storePVGISData(context, mPanel.getPanelIndex());
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("PVGIS")
            .observe(this, workInfos -> {
                for (WorkInfo workInfo: workInfos){
                    if (workInfo.getState() != null &&
                            workInfo.getState().isFinished() &&
                            workInfo.getTags().contains("com.tfcode.comparetout.scenario.panel.PVGISLoader")) {
                        System.out.println(workInfo.getTags().iterator().next());
                        mProgressBar.setVisibility(View.GONE);
                        fileExist();
                        mLocationChanged = false;
                        mMainHandler.post(this::updateView);
                    }
                }
            });
    }

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
            mLocationChanged = false;
            if (!(null == mPanel)) System.out.println(mPanel.getAzimuth());
            else System.out.println("DOH!");
            mMainHandler.post(this::updateView);
            mMainHandler.post(this::fileExist);
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
        }).start();
        mEditFields = new ArrayList<>();

        mViewModel.getPanelDataSummary().observe(this, summaries -> {
            mPanelDataInDB = false;
            for (PanelPVSummary summary: summaries) {
                if (summary.panelID == mPanel.getPanelIndex()){
                    mPanelDataInDB = true;
                    break;
                }
            }
            updateView();
        });

        registerReceiver(onDownloadComplete,new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(onDownloadComplete);
    }

    private void updateView() {
        System.out.println("PVGIS Activity, updateView"); // CREATE PARAM FOR MARGINING
        mTableLayout.removeAllViews();
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.topMargin = 2;
        params.rightMargin = 2;

        TextView downloadIndicator = new TextView(this);
        TextView dbRefreshIndicator = new TextView(this);

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
                    fileExist();
                    mLocationChanged = true;
                    setStatusTexts(downloadIndicator, dbRefreshIndicator);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Longitude", df.format(mPanel.getLongitude()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(df.format(mPanel.getLongitude())))) {
                    System.out.println("Longitude changed");
                    mPanel.setLongitude(getDoubleOrZero(s));
                    fileExist();
                    mLocationChanged = true;
                    setStatusTexts(downloadIndicator, dbRefreshIndicator);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Azimuth", String.valueOf(mPanel.getAzimuth()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mPanel.getAzimuth())))) {
                    System.out.println("Azimuth changed");
                    mPanel.setAzimuth(getIntegerOrZero(s));
                    fileExist();
                    mLocationChanged = true;
                    setStatusTexts(downloadIndicator, dbRefreshIndicator);
                }
            }
        }, params, integerType));
        mTableLayout.addView(createRow("Slope", String.valueOf(mPanel.getSlope()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mPanel.getSlope())))) {
                    System.out.println("Slope changed");
                    mPanel.setSlope(getIntegerOrZero(s));
                    fileExist();
                    mLocationChanged = true;
                    setStatusTexts(downloadIndicator, dbRefreshIndicator);
                }
            }
        }, params, integerType));

        TableRow tableRow = new TableRow(this);
        setStatusTexts(downloadIndicator, dbRefreshIndicator);
        downloadIndicator.setLayoutParams(params);
        dbRefreshIndicator.setLayoutParams(params);
        tableRow.addView(downloadIndicator);
        tableRow.addView(dbRefreshIndicator);
        mTableLayout.addView(tableRow);

        tableRow = new TableRow(this);
        Button location = new Button(this);
        location.setText("Update location");
        Button download = new Button(this);
        download.setText("Download data");
        download.setOnClickListener(v -> saveAndFetch() );
        tableRow.addView(location);
        tableRow.addView(download);
        mTableLayout.addView(tableRow);
    }

    private void setStatusTexts(TextView downloadIndicator, TextView dbRefreshIndicator) {
        if (mFileCached) downloadIndicator.setText("Data downloaded");
        else downloadIndicator.setText("Download necessary");
        if (mPanelDataInDB && ! mLocationChanged) dbRefreshIndicator.setText("Data in DB");
        else dbRefreshIndicator.setText("DB update necessary");
    }

    private String fileExist(){
        DecimalFormat df = new DecimalFormat("#.000");
        String latitude = df.format(mPanel.getLatitude());
        String longitude = df.format(mPanel.getLongitude());
        String filename = "PVGIS(" + latitude + ")(" + longitude +
                ")(" + mPanel.getSlope() + ")(" + mPanel.getAzimuth() + ")" ;
        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS + "/" + filename);
        mFileCached = file.exists();
        return filename;
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
            mViewModel.removeOldPanelData(mPanel.getPanelIndex());

            if (ContextCompat.checkSelfPermission(
                    this, "android.permission.WRITE_EXTERNAL_STORAGE") ==
                    PackageManager.PERMISSION_GRANTED) {
                mMainHandler.post(this::fetch);
            }
            else {
                requestPermissionLauncher.launch(
                        "android.permission.WRITE_EXTERNAL_STORAGE");
            }
        }).start();
    }

    private void fetch() {
        mProgressBar.setVisibility(View.VISIBLE);

        String fileName = fileExist();
        if (!mFileCached) {
            // U1 <LAT> U2 <LONG> U3 <SLOPE> U4 <AZIMUTH> U5 <SLOPE> U6 <AZIMUTH>
            int az = mPanel.getAzimuth();
            if (az > 180) az = 360 - az;
            DecimalFormat df = new DecimalFormat("#.000");
            String url = new StringBuilder()
                    .append(U1).append(df.format(mPanel.getLatitude()))
                    .append(U2).append(df.format(mPanel.getLongitude()))
                    .append(U3).append(mPanel.getSlope())
                    .append(U4).append(az)
                    .append(U5).append(mPanel.getSlope())
                    .append(U6).append(az).toString();
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(folder, fileName);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)// Visibility of the download Notification
                    .setDestinationUri(Uri.fromFile(file))// Uri of the destination file
                    .setTitle(fileName)// Title of the Download Notification
                    .setDescription("Downloading PVGIS data")// Description of the Download Notification
                    .setRequiresCharging(false)// Set if charging is required to begin the download
                    .setAllowedOverMetered(true)// Set if download is allowed on Mobile network
                    .setAllowedOverRoaming(true);// Set if download is allowed on roaming network
            DownloadManager downloadManager= (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            mDownloadID = downloadManager.enqueue(request);// enqueue puts the download request in the queue
        }
        else {
            scheduleLoad(this);
        }

    }

    @Override
    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce || !(mUnsavedChanges)) {
            super.onBackPressed();
            SimulatorLauncher.simulateIfNeeded(getApplicationContext());
            return;
        }

        this.mDoubleBackToExitPressedOnce = true;
        Snackbar.make(getWindow().getDecorView().getRootView(),
                        "Unsaved changes. Please click BACK again to discard and exit", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
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