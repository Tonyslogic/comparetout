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

package com.tfcode.comparetout.scenario.panel;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;

import androidx.lifecycle.ViewModelProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.snackbar.Snackbar;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.PanelPVSummary;
import com.tfcode.comparetout.util.AbstractEPOFolderActivity;
import com.tfcode.comparetout.util.AbstractTextWatcher;
import com.tfcode.comparetout.util.ContractFileUtils;
import com.tfcode.comparetout.util.EdgeInsets;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.util.Objects;

public class PVGISActivity extends AbstractEPOFolderActivity {

    private Handler mMainHandler;
    private ProgressBar mProgressBar;
    private TableLayout mTableLayout;
    private TableLayout mStatusTable;
    private Long mPanelID = 0L;
    private Panel mPanel;
    private boolean mEdit = false;
    private ComparisonUIViewModel mViewModel;
    private boolean mPanelDataInDB = false;
    private boolean mFileCached = false;
    private boolean mLocationChanged = false;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private static final String U1 = "https://re.jrc.ec.europa.eu/api/v5_2/seriescalc?lat="; // LATITUDE
    private static final String U2 = "&lon="; // LONGITUDE
    private static final String U3 = "&raddatabase=PVGIS-SARAH2&browser=1&outputformat=json&userhorizon=&usehorizon=1&angle="; //SLOPE
    private static final String U4 = "&aspect="; // AZIMUTH
    private static final String U5 = "&startyear=2019&endyear=2019&mountingplace=&optimalinclination=0&optimalangles=0&js=1&select_database_hourly=PVGIS-SARAH2&hstartyear=2019&hendyear=2019&trackingtype=0&hourlyangle="; // SLOPE
    private static final String U6 = "&hourlyaspect="; //AZIMUTH

    private static final int STATE_ALL_GOOD = 0;
    private static final int STATE_LOCATION_CHANGED = 1;
    private static final int STATE_DOWNLOADING = 2;
    static final int STATE_LOADING_DB = 3;
    private static final int STATE_UPDATED = 4;
    private static final int STATE_UNKNOWN = 5;

    private DownloadCompleteObserver mDownloadCompleteObserver;

    private static final String TAG = "PVGISActivity";

    private boolean mGooglePlayServicesAvailable = false;
    private FusedLocationProviderClient mFusedLocationClient;

    private final ActivityResultLauncher<String[]> locationPermissionRequest =
        registerForActivityResult(new ActivityResultContracts
                    .RequestMultiplePermissions(), result -> {
                Boolean fineLocationGranted = result.getOrDefault(
                        "android.permission.ACCESS_FINE_LOCATION", false);
                Boolean coarseLocationGranted = result.getOrDefault(
                        "android.permission.ACCESS_COARSE_LOCATION",false);
                if (fineLocationGranted != null && fineLocationGranted) {
                    // Precise location access granted.
                    updateLocation();
                } else if (coarseLocationGranted != null && coarseLocationGranted) {
                    // Only approximate location access granted.
                    updateLocation();
                } else {
                    Snackbar.make(getWindow().getDecorView().getRootView(),
                                    "No access to location services", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        );

    @SuppressLint("MissingPermission")
    private void updateLocation() {
        mFusedLocationClient.getLastLocation()
        .addOnSuccessListener(this, location -> {
            // Got last known location. In some rare situations this can be null.
            if (location != null) {
                // Logic to handle location object
                mPanel.setLatitude(location.getLatitude());
                mPanel.setLongitude(location.getLongitude());
                fileExist();
                mLocationChanged = true;
                updateView();
                mMainHandler.post(()->updateStatusView(STATE_LOCATION_CHANGED));
            }
        });

    }

    void scheduleLoad(Context context) {
        SimulatorLauncher.storePVGISData(context, mPanel.getPanelIndex(), getEPOFolderUri());
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("PVGIS")
            .observe(this, workInfos -> {
                for (WorkInfo workInfo: workInfos){
                    if (workInfo.getState().isFinished() && workInfo.getTags().contains("com.tfcode.comparetout.scenario.panel.PVGISLoader")) {
                        mProgressBar.setVisibility(View.GONE);
                        fileExist();
                        mLocationChanged = false;
                        mMainHandler.post(this::updateView);
                        mMainHandler.post(() -> updateStatusView(STATE_UPDATED));
                        mMainHandler.post(this::loadCompleteFeedback);
                    }
                }
            });
    }

    private void loadCompleteFeedback()  {
        Snackbar.make(getWindow().getDecorView().getRootView(),
                        "PVGIS Data updated in DB", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }


    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyInsetsToView(R.id.pvgis_table, EdgeInsets.Edge.TOP);
        setContentView(R.layout.activity_pvgis);
        createProgressBar();
        mProgressBar.setVisibility(View.VISIBLE);

        mGooglePlayServicesAvailable =
                (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this.getApplicationContext()) == ConnectionResult.SUCCESS );
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        Intent intent = getIntent();
        mPanelID = intent.getLongExtra("PanelID", 0L);
        mEdit = intent.getBooleanExtra("Edit", false);

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                .build();

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("PVGIS data grabber");

        mTableLayout = findViewById(R.id.pvgis_table);
        mTableLayout.setShrinkAllColumns(true);
        mTableLayout.setStretchAllColumns(true);

        mStatusTable = findViewById(R.id.pvgis_status);
        mStatusTable.setShrinkAllColumns(true);
        mStatusTable.setStretchAllColumns(true);

        mProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            mPanel = mViewModel.getPanelForID(mPanelID);
            mLocationChanged = false;
            mMainHandler.post(this::updateView);
            mMainHandler.post(this::fileExist);
            if (mFileCached && mPanelDataInDB) mMainHandler.post(() -> updateStatusView(STATE_ALL_GOOD));
            else mMainHandler.post(() -> updateStatusView(STATE_UNKNOWN));
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
        }).start();

        mViewModel.getPanelDataSummary().observe(this, summaries -> {
            mPanelDataInDB = false;
            for (PanelPVSummary summary: summaries) {
                if (summary.panelID == mPanel.getPanelIndex()){
                    mPanelDataInDB = true;
                    break;
                }
            }
            updateView();
            if (mFileCached && mPanelDataInDB) mMainHandler.post(() -> updateStatusView(STATE_ALL_GOOD));
        });

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);

        mDownloadCompleteObserver = new DownloadCompleteObserver(getApplicationContext(), new Handler(Looper.getMainLooper()), this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_prices, menu);
        int colour = Color.parseColor("White");
        MenuItem mi = menu.findItem(R.id.help);
        if (!(null == mi) && !(null == mi.getIcon()))
            mi.getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        menu.findItem(R.id.load).setVisible(false);
        menu.findItem(R.id.download).setVisible(false);
        menu.findItem(R.id.export).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.help) {
            showHelp();
        }
        return(super.onOptionsItemSelected(item));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDownloadCompleteObserver.unregister();
    }
    void updateStatusView(int state) {
        mStatusTable.removeAllViews();
        TextView status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(20f);
        switch (state) {
            case STATE_ALL_GOOD: status.setText(R.string.panel_data_all_good);
            break;
            case STATE_LOCATION_CHANGED: status.setText(R.string.panel_data_location_change);
            break;
            case STATE_DOWNLOADING: status.setText(R.string.panel_data_downloading);
            break;
            case STATE_LOADING_DB: status.setText(R.string.panel_data_loading);
            break;
            case STATE_UPDATED: status.setText(R.string.panel_data_updated);
            break;
            case STATE_UNKNOWN:
            default: status.setText(R.string.checking_for_panel_data);
        }
        mStatusTable.addView(status);
    }

    private void updateView() {
        mTableLayout.removeAllViews();
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.topMargin = 2;
        params.rightMargin = 2;

        TextView downloadIndicator = new TextView(this);
        TextView dbRefreshIndicator = new TextView(this);


        int integerType = InputType.TYPE_CLASS_NUMBER;
        int doubleType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        DecimalFormat df = new DecimalFormat("#.000");

        // CREATE TABLE ROWS
        mTableLayout.addView(createRow("Latitude", df.format(mPanel.getLatitude()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(df.format(mPanel.getLatitude())))) {
                    mPanel.setLatitude(getDoubleOrZero(s));
                    fileExist();
                    mLocationChanged = true;
                    updateStatusView(STATE_LOCATION_CHANGED);
                    setStatusTexts(downloadIndicator, dbRefreshIndicator);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Longitude", df.format(mPanel.getLongitude()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(df.format(mPanel.getLongitude())))) {
                    mPanel.setLongitude(getDoubleOrZero(s));
                    fileExist();
                    mLocationChanged = true;
                    updateStatusView(STATE_LOCATION_CHANGED);
                    setStatusTexts(downloadIndicator, dbRefreshIndicator);
                }
            }
        }, params, doubleType));
        mTableLayout.addView(createRow("Azimuth", String.valueOf(mPanel.getAzimuth()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mPanel.getAzimuth())))) {
                    mPanel.setAzimuth(getIntegerOrZero(s));
                    fileExist();
                    mLocationChanged = true;
                    updateStatusView(STATE_LOCATION_CHANGED);
                    setStatusTexts(downloadIndicator, dbRefreshIndicator);
                }
            }
        }, params, integerType));
        mTableLayout.addView(createRow("Slope", String.valueOf(mPanel.getSlope()), new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mPanel.getSlope())))) {
                    mPanel.setSlope(getIntegerOrZero(s));
                    fileExist();
                    mLocationChanged = true;
                    updateStatusView(STATE_LOCATION_CHANGED);
                    setStatusTexts(downloadIndicator, dbRefreshIndicator);
                }
            }
        }, params, integerType));

        TableRow tableRow = new TableRow(this);
        setStatusTexts(downloadIndicator, dbRefreshIndicator);
        downloadIndicator.setLayoutParams(params);
        downloadIndicator.setMinimumHeight(80);
        downloadIndicator.setHeight(80);
        dbRefreshIndicator.setLayoutParams(params);
        dbRefreshIndicator.setMinimumHeight(80);
        dbRefreshIndicator.setHeight(80);
        tableRow.addView(downloadIndicator);
        tableRow.addView(dbRefreshIndicator);
        mTableLayout.addView(tableRow);

        tableRow = new TableRow(this);
        Button location = new Button(this);
        location.setText(R.string.UpdateLocation);
        location.setEnabled(mGooglePlayServicesAvailable);
        location.setOnClickListener(v -> updateLatAndLong());
        Button download = new Button(this);
        download.setText(R.string.DownloadData);
        download.setOnClickListener(v -> saveAndFetch() );
        tableRow.addView(location);
        tableRow.addView(download);
        mTableLayout.addView(tableRow);
    }

    private void updateLatAndLong() {
        if (ContextCompat.checkSelfPermission(
                this, "android.permission.ACCESS_COARSE_LOCATION") ==
                PackageManager.PERMISSION_GRANTED) {
            updateLocation();
        }
        else {
            locationPermissionRequest.launch(new String[] {
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION"
            });
        }
    }

    private void setStatusTexts(TextView downloadIndicator, TextView dbRefreshIndicator) {
        if (mFileCached) downloadIndicator.setText(R.string.DataDownloaded);
        else downloadIndicator.setText(R.string.DownloadNecessary);
        if (mPanelDataInDB && ! mLocationChanged) dbRefreshIndicator.setText(R.string.DataInDB);
        else dbRefreshIndicator.setText(R.string.DBUpdateNecessary);
    }

    private void noPermissions() {
        Log.i(TAG, "No permission for file access");
        Snackbar.make(getWindow().getDecorView().getRootView(),
                        "PVGIS data grabber does not work without write permission", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    private String fileExist(){
        final String[] filename = {getFilename()};
        PVGISActivity _this = this;
        if (!(null == getEPOFolderUri()) && isWriteAccessPresent()) {
            filename[0] = getCachedFilename(filename[0]);
        }
        else {
            pickFolderWithPermission(created -> {
                if (created) {
                    filename[0] = getCachedFilename(filename[0]);
                    mMainHandler.post(_this::updateView);
                }
                else mMainHandler.post(_this::noPermissions);
            });
        }
        return filename[0];
    }

    private String getCachedFilename(String filename) {
        Uri fileUri = ContractFileUtils.findFileInFolderTree(this, getEPOFolderUri(), filename);
        mFileCached = !(null == fileUri);
        if (mFileCached) filename = ContractFileUtils.getFileNameFromUri(this, fileUri);
        return filename;
    }

    @NonNull
    private String getFilename() {
        DecimalFormat df = new DecimalFormat("#.000");
        String latitude = df.format(mPanel.getLatitude());
        String longitude = df.format(mPanel.getLongitude());
        return "PVGIS(" + latitude + ")(" + longitude +
                ")(" + mPanel.getSlope() + ")(" + mPanel.getAzimuth() + ")";
    }

    private TableRow createRow(String title, String initialValue, AbstractTextWatcher action, TableRow.LayoutParams params, int inputType){
        TableRow tableRow = new TableRow(this);
        TextView a = new TextView(this);
        a.setText(title);
        a.setMinimumHeight(80);
        a.setHeight(80);
        EditText b = new EditText(this);
        b.setText(initialValue);
        b.setEnabled(mEdit);
        b.addTextChangedListener(action);
        b.setInputType(inputType);

        a.setLayoutParams(params);
        b.setPadding(20, 20, 20, 20);
        tableRow.addView(a);
        tableRow.addView(b);
        return tableRow;
    }

    private void saveAndFetch() {
        if (!isWriteAccessPresent()) {
            pickFolderWithPermission(this::folderCreatedWithPermission);
        }
        else {
            new Thread(() -> folderCreatedWithPermission(true)).start();
        }
    }

    private void folderCreatedWithPermission(boolean created){
        if (created) {
            mViewModel.updatePanel(mPanel);
            mViewModel.removeOldPanelData(mPanel.getPanelIndex());
            mMainHandler.post(this::fetch);
        }
        else {
            mMainHandler.post(this::noPermissions);
        }
    }

    private void fetch() {
        mProgressBar.setVisibility(View.VISIBLE);
        updateStatusView(STATE_DOWNLOADING);

        String fileName = fileExist();
        if (!mFileCached) {
            // U1 <LAT> U2 <LONG> U3 <SLOPE> U4 <AZIMUTH> U5 <SLOPE> U6 <AZIMUTH>
            int az = mPanel.getAzimuth();
            if (az > 180) az = 360 - az;
            DecimalFormat df = new DecimalFormat("#.000");
            String url = U1 + df.format(mPanel.getLatitude()) +
                    U2 + df.format(mPanel.getLongitude()) +
                    U3 + mPanel.getSlope() +
                    U4 + az +
                    U5 + mPanel.getSlope() +
                    U6 + az;

            try {
                Uri destinationFileUri = ContractFileUtils.createJSONFileInEPOFolder(getContentResolver(), getEPOFolderUri(), fileName);
                downloadAndCopy(fileName, url, destinationFileUri);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "FileNotFoundException when creating a file for download");
            } catch (IllegalArgumentException e1) {
                Log.e(TAG, "IllegalArgumentException when creating a file for download");
            }
        }
        else {
            updateStatusView(STATE_LOADING_DB);
            scheduleLoad(this);
        }
    }

    private void downloadAndCopy(String fileName, String url, Uri fileUri) {
        File tempFile = new File(this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)// Visibility of the download Notification
                .setVisibleInDownloadsUi(true)
                .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setTitle(fileName)// Title of the Download Notification
                .setDescription("Downloading PVGIS data")// Description of the Download Notification
                .setRequiresCharging(false)// Set if charging is required to begin the download
                .setAllowedOverMetered(true)// Set if download is allowed on Mobile network
                .setAllowedOverRoaming(true);// Set if download is allowed on roaming network
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long mDownloadID = downloadManager.enqueue(request);// enqueue puts the download request in the queue
        mDownloadCompleteObserver.register(mDownloadID, tempFile, fileUri);
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

    private void showHelp() {
        mHelpWindow.setHeight((int) (getWindow().getDecorView().getHeight()*0.6));
        mHelpWindow.setWidth(getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(getWindow().getDecorView().getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl("https://appassets.androidplatform.net/assets/scenario/panel/pvgis_help.html");
    }
}