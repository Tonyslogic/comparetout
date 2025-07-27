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

package com.tfcode.comparetout.importers;

import static java.time.temporal.ChronoUnit.DAYS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.ListenableFuture;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.TOUTCApplication;
import com.tfcode.comparetout.importers.alphaess.CatchUpWorker;
import com.tfcode.comparetout.importers.alphaess.DailyWorker;
import com.tfcode.comparetout.importers.alphaess.SelectSerialDialog;
import com.tfcode.comparetout.importers.alphaess.SelectSerialDialogListener;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.costings.SubTotals;
import com.tfcode.comparetout.model.importers.CostInputRow;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.util.GraphableActivity;
import com.tfcode.comparetout.util.LocalContentWebViewClient;
import com.tfcode.comparetout.util.RateLookup;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import io.reactivex.rxjava3.core.Single;

public abstract class ImportOverviewFragment extends Fragment {
    // Saved state
    private static final String SERIAL_NUMBER = "SERIAL_NUMBER";
    private static final String HAS_CREDENTIALS = "HAS_CREDENTIALS";
    private static final String GOOD_CREDENTIALS = "GOOD_CREDENTIALS";
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PARSER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MIDNIGHT = " 00:00:00";
    protected static String mAppID = null;
    protected static String mAppSecret = null;
    protected String TAG;
    protected ComparisonUIViewModel.Importer mImporterType;
    protected String APP_ID_KEY;
    protected String APP_SECRET_KEY;
    protected String GOOD_CREDENTIAL_KEY;
    protected String SYSTEM_LIST_KEY;
    protected String SYSTEM_PREVIOUSLY_SELECTED;
    private final String PREVIOUS_SELECTION_START = "ImportSelectionStart";
    private final String PREVIOUS_SELECTION_END = "ImportSelectionEnd";
    private LocalDateTime mStartLDT;
    private LocalDateTime mEndLDT;
    protected List<String> mSerialNumbers;
    private ComparisonUIViewModel mViewModel;
    private CostViewModel mCostViewModel;
    private ToutcRepository mToutcRepository;
    protected Handler mMainHandler;
    private TableLayout mInputTable;
    private TableLayout mStatusTable;
    private Map<String, String> mInverterDateRangesBySN;
    private boolean mHasCredentials = true;
    protected boolean mCredentialsAreGood = true;
    protected String mSerialNumber;
    protected boolean mSystemSelected = false;
    protected boolean mFetchOngoing = false;
    private LiveData<List<WorkInfo>> mCatchupLiveDataForSN;
    private Observer<List<WorkInfo>> mCatchupWorkObserver;
    private String mFetchState;
    private LiveData<List<WorkInfo>> mDailyLiveDataForSN;
    private Observer<List<WorkInfo>> mDailyWorkObserver;
    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;
    private View mPopupPieView;
    private PopupWindow mPieChartWindow;
    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;
    protected int mSelectSystemText = R.string.SelectSystem;

    @Override
    public void onResume() {
        super.onResume();
        mOrientation = getResources().getConfiguration().orientation;
    }

    private void loadSettingsFromDataStore() {
        new Thread(() -> {
            Activity activity = getActivity();
            if (!(null == activity)) {
                Preferences.Key<String> appIdKey = PreferencesKeys.stringKey(APP_ID_KEY);
                TOUTCApplication application = ((TOUTCApplication) activity.getApplication());
                Single<String> value = application.getDataStore()
                        .data().firstOrError()
                        .map(prefs -> prefs.get(appIdKey)).onErrorReturnItem("null");
                String appId = value.blockingGet();

                Preferences.Key<String> appSecretKey = PreferencesKeys.stringKey(APP_SECRET_KEY);
                Single<String> value2 = application.getDataStore()
                        .data().firstOrError()
                        .map(prefs -> prefs.get(appSecretKey)).onErrorReturnItem("null");
                String appSecret = value2.blockingGet();

                Preferences.Key<String> goodKey = PreferencesKeys.stringKey(GOOD_CREDENTIAL_KEY);
                Single<String> value3 = application.getDataStore()
                        .data().firstOrError()
                        .map(prefs -> prefs.get(goodKey)).onErrorReturnItem("False");
                String goodCreds = value3.blockingGet();
                mCredentialsAreGood = !(goodCreds.equals("False"));

                Preferences.Key<String> startKey = PreferencesKeys.stringKey(PREVIOUS_SELECTION_START);
                Single<String> value4 = application.getDataStore()
                        .data().firstOrError()
                        .map(prefs -> prefs.get(startKey)).onErrorReturnItem("");
                String startDate = value4.blockingGet();

                Preferences.Key<String> endKey = PreferencesKeys.stringKey(PREVIOUS_SELECTION_END);
                Single<String> value6 = application.getDataStore()
                        .data().firstOrError()
                        .map(prefs -> prefs.get(endKey)).onErrorReturnItem("");
                String endDate = value6.blockingGet();

                try {
                    mStartLDT = LocalDateTime.parse(startDate + MIDNIGHT, PARSER_FORMATTER);
                    mEndLDT = LocalDateTime.parse(endDate + MIDNIGHT, PARSER_FORMATTER).plusDays(1);
                } catch (Exception ignored) {}

                loadSystemListFromPreferences(application);

                Preferences.Key<String> previousKey = PreferencesKeys.stringKey(SYSTEM_PREVIOUSLY_SELECTED);
                Single<String> value5 = application.getDataStore()
                        .data().firstOrError()
                        .map(prefs -> prefs.get(previousKey)).onErrorReturnItem("");
                mSerialNumber = value5.blockingGet();
                if ("".equals(mSerialNumber)) mSerialNumber = null;
                if (!(null == mSerialNumber) && !(null == getContext())) {
                    mMainHandler.post(() -> serialUpdated(getContext()));
                    mCostViewModel.setDBStart(LocalDateTime.parse("1970-01-02" + MIDNIGHT, PARSER_FORMATTER));
                    mCostViewModel.setDBEnd(LocalDateTime.now());
                }

                if (("null".equals(appId)) || ("null".equals(appSecret))) {
                    mHasCredentials = false;
                    mCredentialsAreGood = false;
                } else {
                    // Decrypt the stored keys
                    try {
                        appId = TOUTCApplication.decryptString(appId);
                        appSecret = TOUTCApplication.decryptString(appSecret);
                    } catch (Exception e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                    try {
                        reloadClient(appId, appSecret);
                        mMainHandler.post(this::updateView);
                    } catch (ImportException e) {
                        e.printStackTrace();
                    }
                    mAppID = appId;
                    mAppSecret = appSecret;
                }
            }
        }).start();
    }

    protected abstract void loadSystemListFromPreferences(TOUTCApplication application);

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SERIAL_NUMBER, mSerialNumber);
        outState.putBoolean(HAS_CREDENTIALS, mHasCredentials);
        outState.putBoolean(GOOD_CREDENTIALS, mCredentialsAreGood);
    }

    @SuppressLint("InflateParams")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mSerialNumber = savedInstanceState.getString(SERIAL_NUMBER);
            mHasCredentials = savedInstanceState.getBoolean(HAS_CREDENTIALS);
            mCredentialsAreGood = savedInstanceState.getBoolean(GOOD_CREDENTIALS);
            if (!(null == mSerialNumber) && !(mSerialNumber.isEmpty()))
                serialUpdated(this.getContext());
        }
        mToutcRepository = new ToutcRepository(requireActivity().getApplication());
        mCostViewModel = new ViewModelProvider(this).get(CostViewModel.class);
        if (!(null == savedInstanceState)) {
            mCostViewModel.setDBStart(LocalDateTime.parse("1970-01-02" + MIDNIGHT, PARSER_FORMATTER));
            mCostViewModel.setDBEnd(LocalDateTime.now());
        }
        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getLiveDateRanges(mImporterType).observe(this, dateRanges -> {
            if (null == mInverterDateRangesBySN) mInverterDateRangesBySN = new HashMap<>();
            for (InverterDateRange inverterDateRange : dateRanges) {
                mInverterDateRangesBySN.put(inverterDateRange.sysSn, inverterDateRange.startDate + " <-> " + inverterDateRange.finishDate);
                if (!(null == mSerialNumber) && inverterDateRange.sysSn.equals(mSerialNumber)) {
                    mCostViewModel.setDBStart(LocalDateTime.parse(inverterDateRange.startDate + MIDNIGHT, PARSER_FORMATTER));
                    mCostViewModel.setDBEnd(LocalDateTime.parse(inverterDateRange.finishDate + MIDNIGHT, PARSER_FORMATTER));
                    if (!(null == mStartLDT)) mCostViewModel.setSelectedStart(mStartLDT);
                    if (!(null == mEndLDT)) mCostViewModel.setSelectedEnd(mEndLDT);
                    if (!(null == mStartLDT) && !(null == mEndLDT)) {
                        mCostViewModel.setTotalDaysSelected(DAYS.between(mCostViewModel.getSelectedStart(), mCostViewModel.getSelectedEnd()));
                        String start = mStartLDT.format(DISPLAY_FORMAT);
                        String end = mEndLDT.plusDays(-1).format(DISPLAY_FORMAT);
                        mCostViewModel.setSelectedDates(start + " <-> " + end);
                        mCostViewModel.setCostings(null);
                        mCostViewModel.setReadyToCost(true);
                    }
                }
                mMainHandler.post(ImportOverviewFragment.this::updateView);
            }
        });

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(requireContext()))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(requireContext()))
                .build();

        LayoutInflater inflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        mPopupPieView = inflater.inflate(R.layout.popup_compare, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        mPieChartWindow = new PopupWindow(mPopupPieView, width, height, true);
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);
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

    protected void updateView() {
        Activity activity = getActivity();
        Context context = getContext();
        if (!(null == mInputTable) && !(null == mStatusTable) && !(null == activity) && !(null == context)) {
            mInputTable.removeAllViews();
            mStatusTable.removeAllViews();

            // Credentials
            TableRow credentialRow = getCredentialsRow(activity);
            mInputTable.addView(credentialRow);

            // System selection
            TableRow systemSelectionRow = getSystemSelectionRow(activity, mCredentialsAreGood);
            mInputTable.addView(systemSelectionRow);

            // Fetch/update
            TableRow addFetchRow = new TableRow(activity);

            MaterialButton addFetchButton = new MaterialButton(activity);
            addFetchButton.setEnabled(mSystemSelected);
            addFetchButton.setText(mFetchOngoing ? "Stop fetching" : "Fetch data");
            TextView addFetchStatus = new TextView(activity);
            addFetchStatus.setText((null == mFetchState) ? getContext().getString(R.string.Idle) : mFetchState);
            addFetchStatus.setGravity(Gravity.CENTER);
            addFetchButton.setOnClickListener(v -> fetchOnClickDelegate(addFetchButton, addFetchStatus, context));
            addFetchRow.addView(addFetchButton);
            addFetchRow.addView(addFetchStatus);
            mInputTable.addView(addFetchRow);

            // Date range
            TableRow availableDatesRow = new TableRow(activity);
            TextView availableDatesPrompt = new TextView(activity);
            availableDatesPrompt.setText(R.string.dates_available);
            availableDatesPrompt.setGravity(Gravity.CENTER);
            availableDatesPrompt.setPadding(0, 25, 0, 25);
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

            TableRow selectDatesRow = new TableRow(activity);
            MaterialButton selectDatesButton = new MaterialButton(activity);
            selectDatesButton.setText(R.string.select_dates);
            selectDatesButton.setEnabled(!(null == mInverterDateRangesBySN) && !(null == mInverterDateRangesBySN.get(mSerialNumber)));
            CalendarConstraints.Builder calendarConstraintsBuilder = new CalendarConstraints.Builder();
            if (null == mCostViewModel.getDBEnd()) mCostViewModel.setDBEnd(LocalDateTime.now());
            if (null == mCostViewModel.getDBStart()) mCostViewModel.setDBStart(LocalDateTime.now());
            calendarConstraintsBuilder.setEnd(mCostViewModel.getDBEnd().atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)).toInstant().toEpochMilli());
            calendarConstraintsBuilder.setStart(mCostViewModel.getDBStart().atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)).toInstant().toEpochMilli());
            MaterialDatePicker.Builder<Pair<Long, Long>> materialDateBuilder = MaterialDatePicker.Builder
                    .dateRangePicker()
                    .setCalendarConstraints(calendarConstraintsBuilder.build());
            final MaterialDatePicker<Pair<Long, Long>> materialDatePicker = materialDateBuilder.build();
            selectDatesButton.setOnClickListener(v -> materialDatePicker.show(getParentFragmentManager(), "MATERIAL_DATE_PICKER"));

            materialDatePicker.addOnPositiveButtonClickListener(selection -> {
                mCostViewModel.setSelectedStart(LocalDateTime.ofInstant(Instant.ofEpochMilli(selection.first), ZoneId.ofOffset("UTC", ZoneOffset.UTC)));
                mCostViewModel.setSelectedEnd(LocalDateTime.ofInstant(Instant.ofEpochMilli(selection.second), ZoneId.ofOffset("UTC", ZoneOffset.UTC)).plusDays(1));
                mCostViewModel.setTotalDaysSelected(DAYS.between(mCostViewModel.getSelectedStart(), mCostViewModel.getSelectedEnd()));

                mStartLDT = mCostViewModel.getSelectedStart();
                mEndLDT = mCostViewModel.getSelectedEnd();

                LocalDateTime selectedStart = mCostViewModel.getSelectedStart();
                LocalDateTime selectedEnd = mCostViewModel.getSelectedEnd();
                LocalDateTime dbStart = mCostViewModel.getDBStart();
                LocalDateTime dbEnd = mCostViewModel.getDBEnd();

                long days = DAYS.between(selectedStart, selectedEnd);
                if (days < 1 || selectedStart.isBefore(dbStart) || selectedEnd.isAfter(dbEnd.plusDays(1))) {
                    if (days < 1) {
                        mCostViewModel.setSelectedDates("Too few days");
                    } else if (selectedStart.isBefore(dbStart)) {
                        mCostViewModel.setSelectedDates("Start not available");
                    } else {
                        mCostViewModel.setSelectedDates("End not available");
                    }
                    mCostViewModel.setReadyToCost(false);
                } else {
                    String start = selectedStart.format(DISPLAY_FORMAT);
                    String end = selectedEnd.plusDays(-1).format(DISPLAY_FORMAT);
                    mCostViewModel.setSelectedDates(start + " <-> " + end);
                    mCostViewModel.setCostings(null);
                    mCostViewModel.setReadyToCost(true);

                    if (!(null == getActivity()) && !(null == getActivity().getApplication())) {
                        TOUTCApplication application = (TOUTCApplication) getActivity().getApplication();
                        boolean x = application.putStringValueIntoDataStore(PREVIOUS_SELECTION_START, start);
                        if (!x)
                            System.out.println("ImportAlphaOverview::materialDatePicker.addOnPositiveButtonClickListener, failed to store start");
                        x = application.putStringValueIntoDataStore(PREVIOUS_SELECTION_END, end);
                        if (!x)
                            System.out.println("ImportAlphaOverview::materialDatePicker.addOnPositiveButtonClickListener, failed to store end");
                    }
                }
                updateView();
            });

            TextView selectedDates = new TextView(activity);
            selectedDates.setText(mCostViewModel.getSelectedDates());
            selectedDates.setGravity(Gravity.CENTER);
            selectDatesRow.addView(selectDatesButton);
            selectDatesRow.addView(selectedDates);
            mInputTable.addView(selectDatesRow);

            // Action row
            TableRow actionRow = getActionRow(activity, availableDateValue);
            mInputTable.addView(actionRow);
        }
        updateCostView();
    }

    protected void fetchOnClickDelegate(MaterialButton addFetchButton, TextView addFetchStatus, Context context) {
        fetchOnClickImplementation(addFetchButton, addFetchStatus, context);
    }

    protected void fetchOnClickImplementation(MaterialButton addFetchButton, TextView addFetchStatus, Context context) {
        if (mFetchOngoing) {
            cancelOngoingFetch(addFetchButton);
        } else {
            addFetchStatus.setText((null == mFetchState) ? "Waiting for worker" : mFetchState);
            MaterialDatePicker<Long> startPicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select fetch start")
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();
            startPicker.addOnPositiveButtonClickListener(startDateAsLong -> {
                if (!(null == startDateAsLong) && !("".equals(mSerialNumber))) {
                    startWorkers(mSerialNumber, startDateAsLong);
                } else {
                    addFetchStatus.setText((null == mFetchState) ? context.getString(R.string.Idle) : mFetchState);
                    if (!(null == getView())) Snackbar.make(getView().getRootView(),
                                    "Positive selection with no date!", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            });
            startPicker.addOnNegativeButtonClickListener(c -> {
                if (!(null == getContext())) {
                    String idle = getContext().getString(R.string.Idle);
                    addFetchStatus.setText(null == mFetchState ? idle : mFetchState);
                }
            });
            startPicker.show(getParentFragmentManager(), "FETCH_START_DATE_PICKER");
        }
    }

    protected void cancelOngoingFetch(MaterialButton addFetchButton) {
        if (!(null == getContext())) {
            WorkManager.getInstance(getContext()).cancelAllWorkByTag(mSerialNumber);
            WorkManager.getInstance(getContext()).cancelAllWorkByTag(mSerialNumber + "daily");
            mFetchOngoing = false;
            addFetchButton.setText(R.string.FetchData);
            mFetchState = getContext().getString(R.string.Idle);
            updateView();
        }
    }

    @NonNull
    private TableRow getCredentialsRow(Activity activity) {
        TableRow credentialRow = new TableRow(activity);
        MaterialButton loadButton = new MaterialButton(activity);
        loadButton.setText(R.string.SetCredentials);
        TextView credentialStatus = new TextView(activity);
        credentialStatus.setText(mHasCredentials ? mCredentialsAreGood ? "Set" : "Set, bad" : "Not set");
        credentialStatus.setGravity(Gravity.CENTER);
        loadButton.setOnClickListener(v -> getCredentialsWithWarning (credentialStatus, (TOUTCApplication) activity.getApplication()));
        credentialRow.addView(loadButton);
        credentialRow.addView(credentialStatus);
        return credentialRow;
    }

    protected void getCredentialsWithWarning(TextView credentialStatus, TOUTCApplication application) {
        credentialStatus.setText(R.string.loading);
        getCredentials(application);
    }

    @NonNull
    private TableRow getActionRow(Activity activity, String availableDateValue) {
        TableRow actionRow = new TableRow(activity);
        MaterialButton clearButton = new MaterialButton(activity);
        clearButton.setText(R.string.RemoveData);
        clearButton.setEnabled(!mFetchOngoing && !(null == mSerialNumber));
        clearButton.setOnClickListener(v -> {
            AlertDialog.Builder alert = new AlertDialog.Builder(activity);
            alert.setTitle("Delete system data");
            alert.setMessage("Are you sure you want to delete?");
            alert.setPositiveButton("All data", (dialog, which) -> {
                    mViewModel.clearInverterBySN(mImporterType, mSerialNumber);
                    removePreferencesForSN(mSerialNumber);
            });
            alert.setNeutralButton("Selected dates", (dialog, which) -> mViewModel.deleteInverterDatesBySN(mImporterType, mSerialNumber,
                    mCostViewModel.getSelectedStart().minusDays(1),
                    mCostViewModel.getSelectedEnd().minusDays(1)));
            alert.setNegativeButton(android.R.string.no, (dialog, which) -> dialog.cancel());
            alert.show();

        });
        MaterialButton costButton = new MaterialButton(activity);
        costButton.setText(R.string.CostData);
        assert availableDateValue != null;
        costButton.setEnabled(mCostViewModel.isReadyToCost());
        costButton.setOnClickListener(v -> new Thread(() -> costSelection(costButton)).start());
        actionRow.addView(clearButton);
        actionRow.addView(costButton);
        return actionRow;
    }

    protected void removePreferencesForSN(String mSerialNumber) {
        // Normally a do nothing method.
        // Overridden when some state needs to be cleared from the preferences
    }

    protected TableRow getSystemSelectionRow(Activity activity, boolean mCredentialsAreGood) {
        TableRow systemSelectionRow = new TableRow(activity);

        MaterialButton systemButton = new MaterialButton(activity);
        systemButton.setText(mSelectSystemText);
        systemButton.setEnabled(mCredentialsAreGood && !(null == mSerialNumbers));
        TextView systemStatus = new TextView(activity);
        systemStatus.setText(!(null == mSerialNumber) ? mSerialNumber : (null == mSerialNumbers) ? "None registered" : "Not set");
        systemStatus.setGravity(Gravity.CENTER);
        systemButton.setOnClickListener(v -> {
            systemStatus.setText(R.string.loading);
            getSerialNumber();
        });
        systemSelectionRow.addView(systemButton);
        systemSelectionRow.addView(systemStatus);
        return systemSelectionRow;
    }

    private void costSelection(MaterialButton costButton) {
        mMainHandler.post(() -> costButton.setText(R.string.loading));
        loadSelectedDatesIntoCostViewModel();
        mMainHandler.post(() -> costButton.setText(R.string.CostData));
        mCostViewModel.setCostings(new CopyOnWriteArrayList<>());
        // Do the costing
        // Load PricePlans
        List<PricePlan> plans = mToutcRepository.getAllPricePlansNow();
        mCostViewModel.setPlans(plans);
        for (PricePlan pp : plans) {
            RateLookup lookup = new RateLookup(pp,
                    mToutcRepository.getAllDayRatesForPricePlanID(pp.getPricePlanIndex()));
            lookup.setStartDOY(mCostViewModel.getSelectedStart().getDayOfYear());
            Costings costing = new Costings();
            costing.setScenarioID(0L);
            costing.setPricePlanID(pp.getPricePlanIndex());
            costing.setFullPlanName(pp.getSupplier() + ":" + pp.getPlanName());
            double buy = 0D;
            double sell = 0D;
            double net;
            SubTotals subTotals = new SubTotals();
            for (Map.Entry<LocalDateTime, Double> usage : mCostViewModel.getImports().entrySet()) {
                if (usage.getKey().isAfter(mCostViewModel.getSelectedStart()) && usage.getKey().isBefore(mCostViewModel.getSelectedEnd())) {
                    LocalDateTime ldt = usage.getKey();
                    int doy = ldt.getDayOfYear();
                    int mod = ldt.getHour() * 60 + ldt.getMinute();
                    int dow = ldt.getDayOfWeek().getValue();
                    double price = lookup.getRate(doy, mod, dow, usage.getValue());
                    double rowBuy = price * usage.getValue();
                    buy += rowBuy;
                    subTotals.addToPrice(price, usage.getValue());
                }
            }
            costing.setBuy(buy);
            for (Map.Entry<LocalDateTime, Double> usage : mCostViewModel.getExports().entrySet()) {
                if (usage.getKey().isAfter(mCostViewModel.getSelectedStart()) && usage.getKey().isBefore(mCostViewModel.getSelectedEnd())) {
                    double price = pp.getFeed();
                    double rowSell = price * usage.getValue();
                    sell += rowSell;
                }
            }
            costing.setSell(sell);
            costing.setSubTotals(subTotals);
            net = ((buy - sell) + (pp.getStandingCharges() * 100 * (mCostViewModel.getTotalDaysSelected() / 365d)));
            costing.setNet(net);
            mCostViewModel.getCostings().add(costing);
            mMainHandler.post(this::updateCostView);
        }
    }

    protected abstract void startWorkers(String serialNumber, Object startDate);

    private void getSerialNumber() {
        Context context = getContext();
        if (!(null == context)) {
            SelectSerialDialog selectSerialDialog = new SelectSerialDialog(getContext(), mSerialNumbers, new SelectSerialDialogListener() {
                @Override
                public void serialSelected(String serial) {
                    mSerialNumber = serial;
                    serialUpdated(context);
                }

                @Override
                public void canceled() {
                    mMainHandler.post(() -> updateView());
                }
            });
            selectSerialDialog.show();
        }
    }

    protected void serialUpdated(Context context) {
        ((GraphableActivity) requireActivity()).setSelectedSystemSN(mSerialNumber);
        mSystemSelected = true;

        if (!(null == getActivity()) && !(null == getActivity().getApplication())) {
            TOUTCApplication application = (TOUTCApplication) getActivity().getApplication();
            boolean x = application.putStringValueIntoDataStore(SYSTEM_PREVIOUSLY_SELECTED, mSerialNumber);
            if (!x)
                System.out.println("ImportAlphaOverview::serialUpdated, failed to store mSerialNumber");
        }

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
                    mMainHandler.post(ImportOverviewFragment.this::updateView);
                }
                if ((!(null == wi)) && (wi.getState() == WorkInfo.State.SUCCEEDED)) {
                    mFetchState = "Catchup done";
                    mMainHandler.post(this::updateView);
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
                    mMainHandler.post(ImportOverviewFragment.this::updateView);
                }
                if ((!(null == wi)) && (wi.getState() == WorkInfo.State.RUNNING)) {
                    Data progress = wi.getProgress();
                    mFetchState = progress.getString(CatchUpWorker.PROGRESS);
                    if (null == mFetchState) mFetchState = "Daily fetching";
                    mMainHandler.post(ImportOverviewFragment.this::updateView);
                }
                if ((!(null == wi)) && (wi.getState() == WorkInfo.State.SUCCEEDED)) {
                    mFetchState = "Daily fetching done";
                    mMainHandler.post(ImportOverviewFragment.this::updateView);
                }
            }
        };
        mDailyLiveDataForSN.observe((AppCompatActivity) context, mDailyWorkObserver);

        boolean d = isWorkScheduled(mSerialNumber + "daily");
        boolean c = isWorkScheduled(mSerialNumber);
        mFetchOngoing = d || c;

        if (!(null == mSerialNumber) && !(null == mInverterDateRangesBySN)) {
            String range = mInverterDateRangesBySN.get(mSerialNumber);
            String[] parts;
            if (range != null) {
                parts = range.split(" <-> ");
                mCostViewModel.setDBStart(LocalDateTime.parse(parts[0] + MIDNIGHT, PARSER_FORMATTER));
                mCostViewModel.setDBEnd(LocalDateTime.parse(parts[1] + MIDNIGHT, PARSER_FORMATTER));
            }
        }

        if (!(null == mMainHandler)) mMainHandler.post(this::updateView);
    }

    private void loadSelectedDatesIntoCostViewModel() {

        if (!(null == mInverterDateRangesBySN) && !(null == mSerialNumber) && !(null == mInverterDateRangesBySN.get(mSerialNumber))) {
            String range = mInverterDateRangesBySN.get(mSerialNumber);

            String[] parts = new String[2];
            if (!(null == range)) {
                parts = range.split(" <-> ");
            }
            List<CostInputRow> costInputRows = mViewModel.getSelectedAlphaESSData(mImporterType, mSerialNumber, parts[0], parts[1]);
            mCostViewModel.setExports(new TreeMap<>());
            mCostViewModel.setImports(new TreeMap<>());
            for (CostInputRow costInputRow : costInputRows) {
                LocalDateTime readTime = LocalDateTime.parse(costInputRow.dateTime, PARSER_FORMATTER);
                mCostViewModel.getExports().put(readTime, costInputRow.feed);
                mCostViewModel.getImports().put(readTime, costInputRow.buy);
            }
            mMainHandler.post(this::updateView);
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

    protected void getCredentials(TOUTCApplication application) {
        Context context = getContext();
        if (!(null == context)) {
            CredentialDialog credentialDialog = new CredentialDialog(context, new CredentialDialogListener() {
                @Override
                public void credentialSpecified(String appId, String appSecret) {
                    // Encrypt the keys
                    if (!(null == appId) && !(null == appSecret)) try {
                        String encryptedAppId = TOUTCApplication.encryptString(appId);
                        String encryptedAppSecret = TOUTCApplication.encryptString(appSecret);
                        boolean x = application.putStringValueIntoDataStore(APP_ID_KEY, encryptedAppId);
                        boolean y = application.putStringValueIntoDataStore(APP_SECRET_KEY, encryptedAppSecret);
                        if (x != y && !y) Log.e(TAG, "Something is wrong with the properties");
                    } catch (Exception e) {
                        Log.e(TAG, Log.getStackTraceString(e));
                    }
                    mAppID = appId;
                    mAppSecret = appSecret;
                    mHasCredentials = true;
                    updateView();
                    new Thread(() -> {
                        try {
                            reloadClient(appId, appSecret);
                            mCredentialsAreGood = true;
                            application.putStringValueIntoDataStore(GOOD_CREDENTIAL_KEY, "True");
                        } catch (ImportException e) {
                            mCredentialsAreGood = false;
                            application.putStringValueIntoDataStore(GOOD_CREDENTIAL_KEY, "False");
                            Log.e(TAG, Log.getStackTraceString(e));
                        }
                        mMainHandler.post(() -> updateView());
                    }).start();
                }

                @Override
                public void canceled() {
                    mMainHandler.post(() -> updateView());
                }
            });
            setCredentialPrompt(credentialDialog);
            credentialDialog.show();
        }
    }

    protected abstract void setCredentialPrompt(CredentialDialog credentialDialog);

    protected abstract void reloadClient(String appId, String appSecret) throws ImportException;

    private void updateCostView() {
        if (!(null == mStatusTable)) {
            mStatusTable.removeAllViews();
            mStatusTable.setShrinkAllColumns(false);
            mStatusTable.setStretchAllColumns(true);
            mStatusTable.setColumnShrinkable(0, true);
            mStatusTable.setColumnStretchable(0, false);

            createRow("Supplier:Plan", "Net(€)",
                    "Buy(€)", "Sell(€)", "Fixed(€)", true, null);

            ArrayList<Row> rows = new ArrayList<>();
            if (!(null == mCostViewModel.getCostings()) && !mCostViewModel.getCostings().isEmpty()
                    && !(null == mCostViewModel.getPlans() && !mCostViewModel.getPlans().isEmpty())) {
                for (Costings costing : mCostViewModel.getCostings()) {
                    Row row = new Row();
                    row.fullName = costing.getFullPlanName();
                    PricePlan pricePlan = findPricePlanByName(row.fullName);
                    DecimalFormat df = new DecimalFormat("#.00");
                    row.net = df.format(costing.getNet() / 100);
                    row.buy = df.format(costing.getBuy() / 100);
                    row.sell = df.format(costing.getSell() / 100);
                    if (!(null == pricePlan)) row.fixed =
                            df.format((pricePlan.getStandingCharges() / 365d) * (double) mCostViewModel.getTotalDaysSelected());
                    else row.fixed = df.format(0d);
                    row.subTotals = costing.getSubTotals();
                    rows.add(row);
                }
            }
            // sort rows {"Scenario", "Plan", "Net", "Buy", "Sell"}
            rows.sort((row1, row2) -> {
                int ret;
                try {
                    ret = Double.valueOf(row1.net).compareTo(Double.valueOf(row2.net));
                } catch (NumberFormatException nfe) {
                    ret = row1.net.compareTo(row2.net);
                }
                return ret;
            });
            for (Row row : rows) {
                createRow(row.fullName, row.net, row.buy, row.sell, row.fixed, false, row.subTotals);
            }
        }
    }

    private PricePlan findPricePlanByName(String name) {
        if (null == mCostViewModel.getPlans()) return null;
        return mCostViewModel.getPlans().stream().filter(s -> name.equals(s.getSupplier() + ":" + s.getPlanName())).findFirst().orElse(null);
    }

    private void createRow(String planName, String net, String buy, String sell,
                           String fixed, boolean title, SubTotals subTotals) {

        if (!(null == getActivity())) {
            TableRow tableRow;
            tableRow = new TableRow(getActivity());
            tableRow.setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/TODO.html");
                return true;
            });

            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            planParams.topMargin = 2;
            planParams.rightMargin = 2;

            TextView b = new TextView(getActivity());
            TextView c = new TextView(getActivity());
            TextView d = new TextView(getActivity());
            TextView e = new TextView(getActivity());
            TextView g = new TextView(getActivity());

            if (title) {
                b.setTypeface(b.getTypeface(), Typeface.BOLD);
                c.setTypeface(c.getTypeface(), Typeface.BOLD);
                d.setTypeface(d.getTypeface(), Typeface.BOLD);
                e.setTypeface(e.getTypeface(), Typeface.BOLD);
                g.setTypeface(e.getTypeface(), Typeface.BOLD);
            } else {
                tableRow.setOnClickListener(v -> showPieChart(subTotals, planName));
            }

            // SET PARAMS
            b.setLayoutParams(planParams);
            c.setLayoutParams(planParams);
            d.setLayoutParams(planParams);
            e.setLayoutParams(planParams);
            g.setLayoutParams(planParams);

            // SET PADDING
            b.setPadding(10, 25, 10, 25);
            c.setPadding(10, 25, 10, 25);
            d.setPadding(10, 25, 10, 25);
            e.setPadding(10, 25, 10, 25);
            g.setPadding(10, 25, 10, 25);

            b.setText(planName);
            c.setText(net);
            d.setText(buy);
            e.setText(sell);
            g.setText(fixed);

            tableRow.addView(b);
            tableRow.addView(c);
            tableRow.addView(d);
            tableRow.addView(e);
            tableRow.addView(g);

            // ADD TABLEROW TO TABLELAYOUT
            mStatusTable.addView(tableRow);
        }
    }

    private void showHelp(String url) {
        if (!(null == getActivity())) {
            mHelpWindow.setHeight((int) (getActivity().getWindow().getDecorView().getHeight() * 0.6));
            mHelpWindow.setWidth(getActivity().getWindow().getDecorView().getWidth());
            mHelpWindow.showAtLocation(getActivity().getWindow().getDecorView().getRootView(), Gravity.CENTER, 0, 0);
            WebView webView = mPopupView.findViewById(R.id.helpWebView);

            webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
            webView.loadUrl(url);
        }
    }

    private void showPieChart(SubTotals subTotals, String planName) {
        if (!(null == getActivity())) {
            if (mOrientation == Configuration.ORIENTATION_PORTRAIT)
                mPieChartWindow.setHeight((int) (getActivity().getWindow().getDecorView().getHeight() * 0.6));
            else
                mPieChartWindow.setWidth((int) (getActivity().getWindow().getDecorView().getWidth() * 0.6));
            mPieChartWindow.showAtLocation(getActivity().getWindow().getDecorView(), Gravity.CENTER, 0, 0);
            PieChart mPieChart = mPopupPieView.findViewById(R.id.price_breakdown);

            mPieChart.getDescription().setEnabled(true);
            mPieChart.getDescription().setText(planName);
            mPieChart.setRotationEnabled(true);
            mPieChart.setDragDecelerationFrictionCoef(0.9f);
            mPieChart.setRotationAngle(0);
            mPieChart.setHighlightPerTapEnabled(true);
            mPieChart.setHoleColor(Color.parseColor("#000000"));

            ArrayList<PieEntry> pieEntries = new ArrayList<>();
            String label = "Cost/kWh";

            Map<String, Double> priceUnitsMap = new HashMap<>();
            for (Double price : subTotals.getPrices()) {
                priceUnitsMap.put(price.toString() + "¢", subTotals.getSubTotalForPrice(price));
            }
            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(Color.parseColor("#304567"));
            colors.add(Color.parseColor("#309967"));
            colors.add(Color.parseColor("#476567"));
            colors.add(Color.parseColor("#890567"));
            colors.add(Color.parseColor("#a35567"));
            colors.add(Color.parseColor("#ff5f67"));
            colors.add(Color.parseColor("#3ca567"));

            for (String type : priceUnitsMap.keySet()) {
                pieEntries.add(new PieEntry(Objects.requireNonNull(priceUnitsMap.get(type)).floatValue(), type));
            }
            PieDataSet pieDataSet = new PieDataSet(pieEntries, label);
            pieDataSet.setValueTextSize(12f);
            pieDataSet.setColors(colors);
            PieData pieData = new PieData(pieDataSet);
            pieData.setDrawValues(true);
            pieData.setValueFormatter(new PercentFormatter(mPieChart));
            mPieChart.setData(pieData);
            mPieChart.setUsePercentValues(true);
            mPieChart.invalidate();
        }
    }
}


class Row {
    String fullName;
    String net;
    String buy;
    String sell;
    SubTotals subTotals;
    String fixed;
}