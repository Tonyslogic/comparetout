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

package com.tfcode.comparetout.importers.alphaess;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.alphaess.responses.GetEssListResponse;
import com.tfcode.comparetout.model.importers.alphaess.InverterDateRange;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;

public class ImportAlphaOverview extends Fragment {

    private ComparisonUIViewModel mViewModel;
    private Handler mMainHandler;
    private TableLayout mInputTable;
    private TableLayout mStatusTable;

    private Map<String, String> mInverterDateRangesBySN;

    private boolean mHasCredentials = true;
    private boolean mCredentialsAreGood = true;
    private static final String APP_ID_KEY = "app_id";
    private static final String APP_SECRET_KEY = "app_secret";
    private static final String GOOD_CREDENTIAL_KEY = "cred_good";
    private OpenAlphaESSClient mOpenAlphaESSClient;
    private String mSerialNumber;
    private List<String> mSerialNumbers;
    private boolean mSystemSelected = false;

    private boolean mFetchOngoing = false;
    private LiveData<List<WorkInfo>> mCatchupLiveDataForSN;
    private Observer<List<WorkInfo>> mCatchupWorkObserver;
    private String mFetchState;
    private LiveData<List<WorkInfo>> mDailyLiveDataForSN;
    private Observer<List<WorkInfo>> mDailyWorkObserver;

    // Saved state
    private static final String SERIAL_NUMBER = "SERIAL_NUMBER";
    private static final String HAS_CREDENTIALS = "HAS_CREDENTIALS";
    private static final String GOOD_CREDENTIALS = "GOOD_CREDENTIALS";
    
    private static String mAppID = null;
    private static String mAppSecret = null;


    private void loadSettingsFromDataStore() {
        new Thread(() -> {
            Activity activity = getActivity();
            if (!(null == activity)) {
            Preferences.Key<String> appIdKey = PreferencesKeys.stringKey(APP_ID_KEY);
            TOUTCApplication application = ((TOUTCApplication)activity.getApplication());
            Single<String> value = application.getDataStore()
                    .data().firstOrError()
                    .map(prefs -> prefs.get(appIdKey)).onErrorReturnItem("null");
            String appId =  value.blockingGet();
            Preferences.Key<String> appSecretKey = PreferencesKeys.stringKey(APP_SECRET_KEY);
            Single<String> value2 = application.getDataStore()
                    .data().firstOrError()
                    .map(prefs -> prefs.get(appSecretKey)).onErrorReturnItem("null");

            Preferences.Key<String> goodKey = PreferencesKeys.stringKey(GOOD_CREDENTIAL_KEY);
            Single<String> value3 = application.getDataStore()
                    .data().firstOrError()
                    .map(prefs -> prefs.get(goodKey)).onErrorReturnItem("False");
            String goodCreds =  value3.blockingGet();

            String appSecret =  value2.blockingGet();
            if (("null".equals(appId)) || ("null".equals(appSecret))) {
                mHasCredentials = false;
                mCredentialsAreGood = false;
            }
            else {
                // TODO Decrypt the stored keys
                mOpenAlphaESSClient = new OpenAlphaESSClient(appId, appSecret);
                try {
                    reLoadSystemList();
                    mMainHandler.post(this::updateView);
                } catch (AlphaESSException e) {
                    e.printStackTrace();
                }
                mCredentialsAreGood = !(goodCreds.equals("False"));
                mAppID = appId;
                mAppSecret = appSecret;
            }
        }}).start();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SERIAL_NUMBER, mSerialNumber);
        outState.putBoolean(HAS_CREDENTIALS, mHasCredentials);
        outState.putBoolean(GOOD_CREDENTIALS, mCredentialsAreGood);
    }

    public ImportAlphaOverview() {
        // Required empty public constructor
    }

    public static ImportAlphaOverview newInstance() { return new ImportAlphaOverview();}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mSerialNumber = savedInstanceState.getString(SERIAL_NUMBER);
            mHasCredentials = savedInstanceState.getBoolean(HAS_CREDENTIALS);
            mCredentialsAreGood = savedInstanceState.getBoolean(GOOD_CREDENTIALS);
        }
        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getLiveDateRanges().observe(this, dateRanges -> {
            if (null == mInverterDateRangesBySN) mInverterDateRangesBySN = new HashMap<>();
            for (InverterDateRange inverterDateRange : dateRanges) {
                mInverterDateRangesBySN.put(inverterDateRange.sysSn, inverterDateRange.startDate +" <-> " + inverterDateRange.finishDate);
                mMainHandler.post(ImportAlphaOverview.this::updateView);
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_import_alpha_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());
        mInputTable = view.findViewById(R.id.alpha_input_table);
        mStatusTable = view.findViewById(R.id.alpha_status_table);
        loadSettingsFromDataStore();
        updateView();
    }

    private void updateView() {
        Activity activity = getActivity();
        Context context = getContext();
        if (!(null == mInputTable) && !(null == mStatusTable) && !(null == activity) && !(null == context)) {
            mInputTable.removeAllViews();
            mStatusTable.removeAllViews();

            // Credentials
            TableRow credentialRow = new TableRow(activity);
            MaterialButton loadButton = new MaterialButton(activity);
            loadButton.setText(R.string.SetCredentials);
            TextView credentialStatus = new TextView(activity);
            credentialStatus.setText(mHasCredentials ? mCredentialsAreGood ? "Set" : "Set, bad" : "Not set");
            credentialStatus.setGravity(Gravity.CENTER);
            loadButton.setOnClickListener(v -> {
                        credentialStatus.setText(R.string.loading);
                        getCredentials((TOUTCApplication)activity.getApplication());
                    });
            credentialRow.addView(loadButton);
            credentialRow.addView(credentialStatus);
            mInputTable.addView(credentialRow);

            // System selection
            TableRow systemSelectionRow = new TableRow(activity);

            MaterialButton systemButton = new MaterialButton(activity);
            systemButton.setText(R.string.SelectSystem);
            systemButton.setEnabled(mCredentialsAreGood && !(null == mSerialNumbers));
            TextView systemStatus = new TextView(activity);
            systemStatus.setText(!(null == mSerialNumber)? mSerialNumber : (null == mSerialNumbers) ? "None registered" : "Not set");
            systemStatus.setGravity(Gravity.CENTER);
            systemButton.setOnClickListener(v -> {
                systemStatus.setText(R.string.loading);
                getSerialNumber();
            });
            systemSelectionRow.addView(systemButton);
            systemSelectionRow.addView(systemStatus);
            mInputTable.addView(systemSelectionRow);

//            // Add System
//            TableRow addSystemRow = new TableRow(activity);
//
//            MaterialButton addSystemButton = new MaterialButton(activity);
//            addSystemButton.setText("Add system");
//            addSystemButton.setEnabled(mCredentialsAreGood && !(null == mSerialNumbers));
//            TextView addSystemStatus = new TextView(activity);
//            addSystemStatus.setText("Not requested");
//            addSystemStatus.setGravity(Gravity.CENTER);
//            addSystemButton.setOnClickListener(v -> {
//                addSystemStatus.setText("Adding...");
//                // TODO New dialog to register
//                if (!(null == getView())) Snackbar.make(getView(),
//                                "TODO Add a system using the app", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            });
//            addSystemRow.addView(addSystemButton);
//            addSystemRow.addView(addSystemStatus);
//            mInputTable.addView(addSystemRow);

            // Fetch/update
            TableRow addFetchRow = new TableRow(activity);

            MaterialButton addFetchButton = new MaterialButton(activity);
            addFetchButton.setEnabled(mSystemSelected);
            addFetchButton.setText(mFetchOngoing ? "Stop fetching" : "Fetch data");
            TextView addFetchStatus = new TextView(activity);
            addFetchStatus.setText((null == mFetchState) ? getContext().getString(R.string.Idle) : mFetchState);
            addFetchStatus.setGravity(Gravity.CENTER);
            addFetchButton.setOnClickListener(v -> {
                if (mFetchOngoing) {
                    if (!(null == getContext())) {
                        WorkManager.getInstance(getContext()).cancelAllWorkByTag(mSerialNumber);
                        WorkManager.getInstance(getContext()).cancelAllWorkByTag(mSerialNumber + "daily");
                        mFetchOngoing = false;
                        addFetchButton.setText(R.string.FetchData);
                        mFetchState = getContext().getString(R.string.Idle);
                        updateView();
                    }
                }
                else {
                    addFetchStatus.setText((null == mFetchState) ? "Waiting for worker" : mFetchState);
                    MaterialDatePicker<Long> startPicker = MaterialDatePicker.Builder.datePicker()
                            .setTitleText("Select fetch start")
                            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                            .build();
                    startPicker.addOnPositiveButtonClickListener(startDateAsLong -> {
                        if (!(null == startDateAsLong)) {
                            startWorkers(mSerialNumber, startDateAsLong);
                        } else {
                            addFetchStatus.setText((null == mFetchState) ? context.getString(R.string.Idle) : mFetchState);
                            if (!(null == getView())) Snackbar.make(getView(),
                                            "Positive selection with no date!", Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        }
                    });
                    startPicker.addOnNegativeButtonClickListener(c -> {
                        if (!(null == getContext())) {
                            String idle = getContext().getString(R.string.Idle);
                            addFetchStatus.setText((null == mFetchState && !(null == idle)) ? idle : mFetchState);
                        }
                    });
                    startPicker.show(getParentFragmentManager(), "FETCH_START_DATE_PICKER");
                }
            });
            addFetchRow.addView(addFetchButton);
            addFetchRow.addView(addFetchStatus);
            mInputTable.addView(addFetchRow);
            
            // Date range
            TableRow availableDatesRow = new TableRow(activity);
            TextView availableDatesPrompt = new TextView(activity);
            availableDatesPrompt.setText(R.string.dates_available);
            availableDatesPrompt.setGravity(Gravity.CENTER);
            availableDatesPrompt.setPadding(0,25,0,25);
            TextView availableDates = new TextView(activity);
            String availableDateValue = "Awaiting data";
            if (!(null == mInverterDateRangesBySN) && !(null == mInverterDateRangesBySN.get(mSerialNumber))) {
                availableDateValue = mInverterDateRangesBySN.get(mSerialNumber);
            }
            availableDates.setText(availableDateValue);
            availableDates.setGravity(Gravity.CENTER);
            availableDatesRow.addView(availableDatesPrompt);
            availableDatesRow.addView(availableDates);
            mInputTable.addView(availableDatesRow);

            // Action row
            TableRow actionRow = new TableRow(activity);
            MaterialButton clearButton = new MaterialButton(activity);
            clearButton.setText(R.string.RemoveData);
            clearButton.setEnabled(!mFetchOngoing && !(null == mSerialNumber));
            clearButton.setOnClickListener(v -> {

                AlertDialog.Builder alert = new AlertDialog.Builder(activity);
                alert.setTitle("Delete system data");
                alert.setMessage("Are you sure you want to delete?");
                alert.setPositiveButton(android.R.string.yes, (dialog, which) -> mViewModel.clearInverterBySN(mSerialNumber));
                alert.setNegativeButton(android.R.string.no, (dialog, which) -> dialog.cancel());
                alert.show();

            });
            MaterialButton costButton = new MaterialButton(activity);
            costButton.setText(R.string.CostData);
            assert availableDateValue != null;
            costButton.setEnabled(!(availableDateValue.equals("Awaiting data")));
            costButton.setOnClickListener(v -> {
                if (!(null == getView())) Snackbar.make(getView(),
                                "TODO costing inverter data", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            });
            actionRow.addView(clearButton);
            actionRow.addView(costButton);
            mInputTable.addView(actionRow);
        }
    }

    private void startWorkers(String serialNumber, Object startDate) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Context context = getContext();
        if (!(null == context)) {
            // start the catchup worker for the selected serial
            Data inputData = new Data.Builder()
                    .putString(CatchUpWorker.KEY_APP_ID, mAppID)
                    .putString(CatchUpWorker.KEY_APP_SECRET, mAppSecret)
                    .putString(CatchUpWorker.KEY_SYSTEM_SN, serialNumber)
                    .putString(CatchUpWorker.KEY_START_DATE, format.format(startDate))
                    .build();
            OneTimeWorkRequest catchupWorkRequest =
                    new OneTimeWorkRequest.Builder(CatchUpWorker.class)
                            .setInputData(inputData)
                            .addTag(serialNumber)
                            .build();

            WorkManager.getInstance(context).pruneWork();
            WorkManager
                    .getInstance(context)
                    .beginUniqueWork(serialNumber, ExistingWorkPolicy.APPEND, catchupWorkRequest)
                    .enqueue();

            // start the daily worker for the selected serial
            Data dailyData = new Data.Builder()
                    .putString(DailyWorker.KEY_APP_ID, mAppID)
                    .putString(DailyWorker.KEY_APP_SECRET, mAppSecret)
                    .putString(DailyWorker.KEY_SYSTEM_SN, serialNumber)
                    .build();
            int delay = 25 - LocalDateTime.now().getHour(); // Going for 01:00 <-> 02:00
            PeriodicWorkRequest dailyWorkRequest =
                    new PeriodicWorkRequest.Builder(DailyWorker.class, 1, TimeUnit.DAYS)
                            .setInputData(dailyData)
                            .setInitialDelay(delay, TimeUnit.HOURS)
                            .addTag(serialNumber + "daily")
                            .build();
            WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(serialNumber + "daily", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, dailyWorkRequest);

            mFetchOngoing = true;
        }
    }

    private void getSerialNumber() {
        Context context = getContext();
        if (!(null == context)) {
            SelectSerialDialog selectSerialDialog = new SelectSerialDialog(getContext(), mSerialNumbers, new SelectSerialDialogListener() {
                @Override
                public void serialSelected(String serial) {
                    mSerialNumber = serial;
                    ((ImportAlphaActivity) requireActivity()).setSelectedSystemSN(mSerialNumber);
                    mSystemSelected = true;
                    // Cleanup if needed
                    if (!(null == mCatchupLiveDataForSN) && !(null == mCatchupWorkObserver)) {
                        mCatchupLiveDataForSN.removeObserver(mCatchupWorkObserver);
                        mFetchState = null;
                    }
                    if (!(null == mDailyLiveDataForSN) && !(null == mDailyWorkObserver)) {
                        mDailyLiveDataForSN.removeObserver(mDailyWorkObserver);
                    }
                    // set up the observer for the selected systems workers
                    mCatchupLiveDataForSN = WorkManager.getInstance(context)
                            .getWorkInfosByTagLiveData(mSerialNumber);
                    mCatchupWorkObserver = workInfos -> {
                        for (WorkInfo wi : workInfos) {
                            if ((!(null == wi)) && (wi.getState() == WorkInfo.State.RUNNING)) {
                                Data progress = wi.getProgress();
                                mFetchState = progress.getString(DailyWorker.PROGRESS);
                                if (null == mFetchState) mFetchState = "Catchup done";
                                mMainHandler.post(ImportAlphaOverview.this::updateView);
                            }
                            if ((!(null == wi)) && (wi.getState() == WorkInfo.State.SUCCEEDED)) {
                                mFetchState = "Catchup done";
                                mMainHandler.post(() -> updateView());
                            }
                        }
                    };
                    mCatchupLiveDataForSN.observe((AppCompatActivity) context, mCatchupWorkObserver);

                    mDailyLiveDataForSN = WorkManager.getInstance(context)
                            .getWorkInfosByTagLiveData(mSerialNumber + "daily");
                    mDailyWorkObserver = workInfos -> {
                        for (WorkInfo wi : workInfos) {
                            if ((!(null == wi)) && (wi.getState() == WorkInfo.State.ENQUEUED)) {
                                Data progress = wi.getProgress();
                                mFetchState = progress.getString(CatchUpWorker.PROGRESS);
                                if (null == mFetchState) mFetchState = "Daily fetch waiting";
                                mMainHandler.post(ImportAlphaOverview.this::updateView);
                            }
                            if ((!(null == wi)) && (wi.getState() == WorkInfo.State.RUNNING)) {
                                Data progress = wi.getProgress();
                                mFetchState = progress.getString(CatchUpWorker.PROGRESS);
                                if (null == mFetchState) mFetchState = "Daily fetching";
                                mMainHandler.post(ImportAlphaOverview.this::updateView);
                            }
                            if ((!(null == wi)) && (wi.getState() == WorkInfo.State.SUCCEEDED)) {
                                mFetchState = "Daily fetching done";
                                mMainHandler.post(ImportAlphaOverview.this::updateView);
                            }
                        }
                    };
                    mDailyLiveDataForSN.observe((AppCompatActivity) context, mDailyWorkObserver);

                    boolean d = isWorkScheduled(mSerialNumber + "daily");
                    boolean c = isWorkScheduled(mSerialNumber);
                    mFetchOngoing = d || c;

                    mMainHandler.post(() -> updateView());
                }

                @Override
                public void canceled() {
                    mMainHandler.post(() -> updateView());
                }
            });
            selectSerialDialog.show();
        }
    }

    private boolean isWorkScheduled(String tag) {
        Context context = getContext();
        if (!(null == context)) {
            WorkManager instance = WorkManager.getInstance(context);
            ListenableFuture<List<WorkInfo>> statuses = instance.getWorkInfosByTag(tag);
            try {
                boolean running = false;
                List<WorkInfo> workInfoList = statuses.get();
                for (WorkInfo workInfo : workInfoList) {
                    WorkInfo.State state = workInfo.getState();
                    running = state == WorkInfo.State.RUNNING | state == WorkInfo.State.ENQUEUED;
                }
                return running;
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private void getCredentials(TOUTCApplication application) {
        Context context = getContext();
        if (!(null == context)) {
            CredentialDialog credentialDialog = new CredentialDialog(context, new CredentialDialogListener() {
                @Override
                public void credentialSpecified(String appId, String appSecret) {
                    // TODO Encrypt the keys
                    boolean x = application.putStringValueIntoDataStore(APP_ID_KEY, appId);
                    boolean y = application.putStringValueIntoDataStore(APP_SECRET_KEY, appSecret);
                    mHasCredentials = true;
                    updateView();
                    if (x != y && !y) System.out.println("Something is wrong with the properties");
                    else {
                        new Thread(() -> {
                            try {
                                mOpenAlphaESSClient = new OpenAlphaESSClient(appId, appSecret);
                                reLoadSystemList();
                                mCredentialsAreGood = true;
                                application.putStringValueIntoDataStore(GOOD_CREDENTIAL_KEY, "True");
                            } catch (AlphaESSException e) {
                                mCredentialsAreGood = false;
                                application.putStringValueIntoDataStore(GOOD_CREDENTIAL_KEY, "False");
                                e.printStackTrace();
                            }
                            mMainHandler.post(() -> updateView());
                        }).start();
                    }
                }

                @Override
                public void canceled() {
                    mMainHandler.post(() -> updateView());
                }
            });
            credentialDialog.show();
        }
    }

    private void reLoadSystemList() throws AlphaESSException {
        List<GetEssListResponse.DataItem> systems = mOpenAlphaESSClient.getEssList().data;
        mSerialNumbers = new ArrayList<>();
        for (GetEssListResponse.DataItem system : systems) {
            mSerialNumbers.add(system.sysSn);
        }
    }
}