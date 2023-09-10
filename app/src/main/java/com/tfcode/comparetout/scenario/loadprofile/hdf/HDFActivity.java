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

package com.tfcode.comparetout.scenario.loadprofile.hdf;

import static java.time.temporal.ChronoUnit.DAYS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;
import androidx.webkit.WebViewAssetLoader;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.opencsv.CSVReader;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.ToutcRepository;
import com.tfcode.comparetout.model.costings.Costings;
import com.tfcode.comparetout.model.costings.SubTotals;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.DOWDist;
import com.tfcode.comparetout.model.scenario.HourlyDist;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.MonthlyDist;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.util.LocalContentWebViewClient;
import com.tfcode.comparetout.util.RateLookup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

public class HDFActivity extends AppCompatActivity {

    private Handler mMainHandler;
    private ProgressBar mProgressBar;

    private ToutcRepository mToutcRepository;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private View mPopupPieView;
    private PopupWindow mPieChartWindow;
    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    private ComparisonUIViewModel mViewModel;
    private HDFViewModel mHDFViewModel;

    private Long mLoadProfileID = 0L;
    private Long mScenarioID = 0L;
    private static final String SCENARIO_KEY = "ScenarioID";
    private static final String PROFILE_KEY = "LoadProfileID";

    private TableLayout mHDFLoadTable = null;
    private TableLayout mHDFCostTable = null;

    private static final DateTimeFormatter HDF_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int READ_VALUE = 2;
    private static final int READ_TYPE = 3;
    private static final int READ_DATETIME = 4;
    private static final String EXPORT_READ_TYPE = "Export";

    final ActivityResultLauncher<String> mLoadHDFFromFile = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    // Handle the returned Uri
                    if (uri == null) return;
                    mProgressBar.setVisibility(View.VISIBLE);
                    InputStream is = null;
                    try {
                        is = getContentResolver().openInputStream(uri);
                        InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                        CSVReader csvReader = new CSVReader(reader);
                        mHDFViewModel.setExports(new TreeMap<>());
                        mHDFViewModel.setImports(new TreeMap<>());
                        // skip header row
                        csvReader.readNext();
                        String[] nextLine;
                        while ((nextLine = csvReader.readNext()) != null) {
                            // nextLine[] is an array of values from the line
                            LocalDateTime readTime = LocalDateTime.parse(nextLine[READ_DATETIME], HDF_FORMAT);
                            Double reading = Double.parseDouble(nextLine[READ_VALUE]);
                            boolean export = nextLine[READ_TYPE].contains(EXPORT_READ_TYPE);
                            if (export) mHDFViewModel.getExports().put(readTime, reading);
                            else mHDFViewModel.getImports().put(readTime, reading);
                            if (mHDFViewModel.getFileStart() == null) mHDFViewModel.setFileStart(readTime);
                            if (mHDFViewModel.getFileEnd() == null) mHDFViewModel.setFileEnd(readTime);
                            if (readTime.isBefore(mHDFViewModel.getFileStart())) mHDFViewModel.setFileStart(readTime);
                            if (readTime.isAfter(mHDFViewModel.getFileEnd())) mHDFViewModel.setFileEnd(readTime);
                        }
                        mHDFViewModel.setAvailableDates(mHDFViewModel.getFileStart().format(DISPLAY_FORMAT) + " <-> " + mHDFViewModel.getFileEnd().format(DISPLAY_FORMAT));
                        mHDFViewModel.setLoaded(true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (!(null == is)) {
                            try {
                                is.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                    mMainHandler.post(() -> updateView());
                }
            });

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(SCENARIO_KEY, mScenarioID);
        outState.putLong(PROFILE_KEY, mLoadProfileID);
    }

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hdf);
        createProgressBar();
        mProgressBar.setVisibility(View.GONE);

        if (!(null == savedInstanceState)) {
            mScenarioID = savedInstanceState.getLong(SCENARIO_KEY);
            mLoadProfileID = savedInstanceState.getLong(PROFILE_KEY);
        }
        else {
            Intent intent = getIntent();
            mLoadProfileID = intent.getLongExtra("LoadProfileID", 0L);
            mScenarioID = intent.getLongExtra("ScenarioID", 0L);
        }


        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mHDFViewModel = new ViewModelProvider(this).get(HDFViewModel.class);
        mToutcRepository = new ToutcRepository(getApplication());

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                .build();

        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("ESBN HDF loader");

        mHDFLoadTable = findViewById(R.id.hdf_load_table);
        mHDFLoadTable.setShrinkAllColumns(true);
        mHDFLoadTable.setStretchAllColumns(true);

        mHDFCostTable = findViewById(R.id.hdf_cost_table);

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        mPopupPieView = inflater.inflate(R.layout.popup_compare, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        mPieChartWindow = new PopupWindow(mPopupPieView, width, height, true);
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);

        updateView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        mOrientation = getResources().getConfiguration().orientation;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_prices, menu);
        int colour = Color.parseColor("White");
        menu.findItem(R.id.help).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        menu.findItem(R.id.load).setVisible(false);
        menu.findItem(R.id.download).setVisible(false);
        menu.findItem(R.id.export).setVisible(false);
        menu.findItem(R.id.fetch).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.help) {
            showHelp("https://appassets.androidplatform.net/assets/scenario/load_profile/hdf.html");
        }
        return(super.onOptionsItemSelected(item));
    }

    private PricePlan findPricePlanByName(String name) {
        if (null == mHDFViewModel.getPlans()) return null;
        return mHDFViewModel.getPlans().stream().filter(s -> name.equals(s.getSupplier() + ":" + s.getPlanName())).findFirst().orElse(null);
    }

    private void updateCostView() {
        if (!(null == mHDFCostTable)){
            mHDFCostTable.removeAllViews();
            mHDFCostTable.setShrinkAllColumns(false);
            mHDFCostTable.setStretchAllColumns(true);
            mHDFCostTable.setColumnShrinkable(0, true);
            mHDFCostTable.setColumnStretchable(0, false);

            createRow("Supplier:Plan", "Net(€)",
                    "Buy(€)","Sell(€)", "Fixed(€)", true, null);

            ArrayList<Row> rows = new ArrayList<>();
            if (!(null == mHDFViewModel.getCostings()) && !mHDFViewModel.getCostings().isEmpty()
                    && !(null == mHDFViewModel.getPlans() && !mHDFViewModel.getPlans().isEmpty())) {
                for (Costings costing : mHDFViewModel.getCostings()) {
                    Row row = new Row();
                    row.fullName = costing.getFullPlanName();
                    PricePlan pricePlan = findPricePlanByName(row.fullName);
                    DecimalFormat df = new DecimalFormat("#.00");
                    row.net = df.format(costing.getNet() /100);
                    row.buy = df.format(costing.getBuy() / 100);
                    row.sell = df.format(costing.getSell() / 100);
                    if (!(null == pricePlan)) row.fixed =
                            df.format((pricePlan.getStandingCharges() / 365d) * (double) mHDFViewModel.getTotalDaysSelected());
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
                }catch (NumberFormatException nfe) {
                    ret = row1.net.compareTo(row2.net);
                }
                return ret;
            });
            for (Row row: rows){
                createRow(row.fullName, row.net, row.buy, row.sell, row.fixed, false, row.subTotals);
            }
        }
    }

    private void createRow(String planName, String net, String buy, String sell,
                           String fixed, boolean title, SubTotals subTotals) {

        TableRow tableRow;
        tableRow = new TableRow(this);
        tableRow.setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/load_profile/row.html");
            return true;});

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;

        TextView b = new TextView(this);
        TextView c = new TextView(this);
        TextView d = new TextView(this);
        TextView e = new TextView(this);
        TextView g = new TextView(this);

        if (title) {
            b.setTypeface(b.getTypeface(), Typeface.BOLD);
            c.setTypeface(c.getTypeface(), Typeface.BOLD);
            d.setTypeface(d.getTypeface(), Typeface.BOLD);
            e.setTypeface(e.getTypeface(), Typeface.BOLD);
            g.setTypeface(e.getTypeface(), Typeface.BOLD);
        }
        else {
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
        mHDFCostTable.addView(tableRow);
    }

    private void updateView() {
        updateCostView();
        if (!(null == mHDFLoadTable)) {
            mHDFLoadTable.removeAllViews();
            TableRow loadRow = new TableRow(this);
            MaterialButton loadButton = new MaterialButton(this);
            loadButton.setText(R.string.load_hdf_file);
            TextView loadStatus = new TextView(this);
            loadStatus.setText(mHDFViewModel.isLoaded() ? "Loaded" : "Not loaded");
            loadStatus.setGravity(Gravity.CENTER);
            loadButton.setOnClickListener(v -> {
                loadStatus.setText(R.string.loading);
                mLoadHDFFromFile.launch("*/*");
            });
            loadRow.addView(loadButton);
            loadRow.addView(loadStatus);
            mHDFLoadTable.addView(loadRow);

            TableRow availableDatesRow = new TableRow(this);
            TextView availableDatesPrompt = new TextView(this);
            availableDatesPrompt.setText(R.string.dates_available);
            availableDatesPrompt.setGravity(Gravity.CENTER);
            availableDatesPrompt.setPadding(0,25,0,25);
            TextView availableDates = new TextView(this);
            availableDates.setText(mHDFViewModel.isLoaded() ? mHDFViewModel.getAvailableDates(): "Awaiting data");
            availableDates.setGravity(Gravity.CENTER);
            availableDatesRow.addView(availableDatesPrompt);
            availableDatesRow.addView(availableDates);
            mHDFLoadTable.addView(availableDatesRow);

            TableRow selectDatesRow = new TableRow(this);
            MaterialButton selectDatesButton = new MaterialButton(this);
            selectDatesButton.setText(R.string.select_dates);
            selectDatesButton.setEnabled(mHDFViewModel.isLoaded());
            CalendarConstraints.Builder calendarConstraintsBuilder = new CalendarConstraints.Builder();
            if (mHDFViewModel.isLoaded()) {
                calendarConstraintsBuilder.setEnd(mHDFViewModel.getFileEnd().atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)).toInstant().toEpochMilli());
                calendarConstraintsBuilder.setStart(mHDFViewModel.getFileStart().atZone(ZoneId.ofOffset("UTC", ZoneOffset.UTC)).toInstant().toEpochMilli());
            }
            MaterialDatePicker.Builder<Pair<Long, Long>> materialDateBuilder = MaterialDatePicker.Builder
                    .dateRangePicker()
                    .setCalendarConstraints(calendarConstraintsBuilder.build());
            final MaterialDatePicker<Pair<Long, Long>> materialDatePicker = materialDateBuilder.build();
            selectDatesButton.setOnClickListener(v -> materialDatePicker.show(getSupportFragmentManager(), "MATERIAL_DATE_PICKER"));

            materialDatePicker.addOnPositiveButtonClickListener(selection -> {
                mHDFViewModel.setSelectedStart(LocalDateTime.ofInstant(Instant.ofEpochMilli(selection.first), ZoneId.ofOffset("UTC", ZoneOffset.UTC)));
                mHDFViewModel.setSelectedEnd(LocalDateTime.ofInstant(Instant.ofEpochMilli(selection.second), ZoneId.ofOffset("UTC", ZoneOffset.UTC)));

                long days = DAYS.between(mHDFViewModel.getSelectedStart(), mHDFViewModel.getSelectedEnd());
                mHDFViewModel.setHasSelectedDates(false);
                if(days > 366) mHDFViewModel.setSelectedDates("Too many days");
                if (days < 7) mHDFViewModel.setSelectedDates("Too few days");
                if (mHDFViewModel.getSelectedStart().isBefore(mHDFViewModel.getFileStart())) mHDFViewModel.setSelectedDates("Start not available");
                if (mHDFViewModel.getSelectedEnd().isAfter(mHDFViewModel.getFileEnd())) mHDFViewModel.setSelectedDates("End not available");
                if (days >= 7 && days <= 366 && !(mHDFViewModel.getSelectedStart().isBefore(mHDFViewModel.getFileStart()))
                        && !(mHDFViewModel.getSelectedEnd().isAfter(mHDFViewModel.getFileEnd()))) {
                    mHDFViewModel.setSelectedDates(mHDFViewModel.getSelectedStart().format(DISPLAY_FORMAT) + " <-> " + mHDFViewModel.getSelectedEnd().format(DISPLAY_FORMAT));
                    mHDFViewModel.setTotalDaysSelected(DAYS.between(mHDFViewModel.getSelectedStart(), mHDFViewModel.getSelectedEnd()));
                    mHDFViewModel.setHasSelectedDates(true);
                }
                updateView();
            });

            TextView selectedDates = new TextView(this);
            selectedDates.setText(mHDFViewModel.getSelectedDates());
            selectedDates.setGravity(Gravity.CENTER);
            selectDatesRow.addView(selectDatesButton);
            selectDatesRow.addView(selectedDates);
            mHDFLoadTable.addView(selectDatesRow);

            // Out of order so text can be used in action buttons
            TableRow costingRow = new TableRow(this);
            TableRow generationRow = new TableRow(this);

            TextView generationStatus = new TextView(this);
            generationStatus.setText(mHDFViewModel.isGenerated() ? "Done" : "Not requested");
            generationStatus.setGravity(Gravity.CENTER);
            TextView costingStatus = new TextView(this);
            costingStatus.setText(mHDFViewModel.isCosted() ? "Done": "Not requested");
            costingStatus.setGravity(Gravity.CENTER);

            MaterialButton generateLPButton = new MaterialButton(this);
            generateLPButton.setText(R.string.set_load_profile);
            generateLPButton.setEnabled(mHDFViewModel.hasSelectedDates());
            generateLPButton.setOnClickListener(v -> {
                generationStatus.setText(R.string.generating_load_profile);
                mHDFViewModel.setTotalSelectedImport(0d);
                Map<Integer, Double> dow = new HashMap<>();
                Map<Integer, Double> moy = new HashMap<>();
                Map<Integer, Double> hod = new HashMap<>();
                for (Map.Entry<LocalDateTime, Double> entry : mHDFViewModel.getImports().entrySet()) {
                    if (entry.getKey().isAfter(mHDFViewModel.getSelectedStart()) && entry.getKey().isBefore(mHDFViewModel.getSelectedEnd())) {
                        // The measurement is for the previous 1/2 hour
                        Double kWh = entry.getValue() / 2D;
                        mHDFViewModel.setTotalSelectedImport(mHDFViewModel.getTotalSelectedImport() + kWh);
                        LocalDateTime localDateTime = entry.getKey().minusMinutes(30L);
                        int day = localDateTime.getDayOfWeek().getValue();
                        Double dv = dow.get(day);
                        if (dv == null) dow.put(day, kWh);
                        else dow.put(day, dv + kWh);
                        int month = localDateTime.getMonthValue();
                        dv = moy.get(month);
                        if (dv == null) moy.put(month, kWh);
                        else moy.put(month, dv + kWh);
                        int hour = localDateTime.getHour();
                        dv = hod.get(hour);
                        if (dv == null) hod.put(hour, kWh);
                        else hod.put(hour, dv + kWh);
                    }
                }
                LoadProfile loadProfile = new LoadProfile();
                loadProfile.setAnnualUsage((mHDFViewModel.getTotalSelectedImport()/(double)mHDFViewModel.getTotalDaysSelected()) * 365);
                loadProfile.setDistributionSource("ESBN HDF");
                HourlyDist hd = new HourlyDist();
                List<Double> hourOfDayDist = new ArrayList<>();
                for (int i = 0; i < 24; i++) {
                    Double hv = hod.get(i);
                    if (!(null == hv)) hourOfDayDist.add((hv / mHDFViewModel.getTotalSelectedImport()) * 100);
                }
                hd.dist = hourOfDayDist;
                loadProfile.setHourlyDist(hd);
                DOWDist dd = new DOWDist();
                List<Double> dowDist = new ArrayList<>();
                Double dv = dow.get(7);
                if (!(null == dv)) dowDist.add((dv / mHDFViewModel.getTotalSelectedImport()) * 100); // 7 => 0, Sunday
                for (int i = 1; i < 7; i++) {
                    dv = dow.get(i);
                    if (!(null == dv)) dowDist.add((dv / mHDFViewModel.getTotalSelectedImport()) * 100);
                }
                dd.dowDist = dowDist;
                loadProfile.setDowDist(dd);
                MonthlyDist md = new MonthlyDist();
                List<Double> moyDist = new ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    Double mv = moy.get(i + 1);
                    if (!(null == mv)) moyDist.add((mv / mHDFViewModel.getTotalSelectedImport()) * 100);
                    else moyDist.add(((loadProfile.getAnnualUsage()/12D) / loadProfile.getAnnualUsage() ) * 100);
                }
                md.monthlyDist = moyDist;
                loadProfile.setMonthlyDist(md);
                mViewModel.saveLoadProfile(mScenarioID, loadProfile);
                mHDFViewModel.setGenerated(true);
                generationStatus.setText(mHDFViewModel.isGenerated() ? "Done" : "Not requested");
            });
            MaterialButton costHDFButton = new MaterialButton(this);
            costHDFButton.setText(R.string.cost_hdf_file);
            costHDFButton.setEnabled(mHDFViewModel.hasSelectedDates());
            costHDFButton.setOnClickListener(v -> {
                costingStatus.setText(R.string.costing_selection);
                mProgressBar.setVisibility(View.VISIBLE);
                new Thread(() -> {
                    mHDFViewModel.setCostings(new CopyOnWriteArrayList<>());
                    // Do the costing
                    // Load PricePlans
                    List<PricePlan> plans = mToutcRepository.getAllPricePlansNow();
                    mHDFViewModel.setPlans(plans);
                    double gridExportMax = mToutcRepository.getGridExportMaxForScenario(mScenarioID);
                    for (PricePlan pp : plans) {
                        RateLookup lookup = new RateLookup(
                                mToutcRepository.getAllDayRatesForPricePlanID(pp.getPricePlanIndex()));
                        Costings costing = new Costings();
                        costing.setScenarioID(mScenarioID);
                        Scenario scenario = mToutcRepository.getScenarioForID(mScenarioID);
                        if (!(null == scenario)) costing.setScenarioName(scenario.getScenarioName());
                        costing.setPricePlanID(pp.getPricePlanIndex());
                        costing.setFullPlanName(pp.getSupplier() + ":" + pp.getPlanName());
                        double buy = 0D;
                        double sell = 0D;
                        double net;
                        SubTotals subTotals = new SubTotals();
                        for (Map.Entry<LocalDateTime, Double> usage : mHDFViewModel.getImports().entrySet()) {
                            if (usage.getKey().isAfter(mHDFViewModel.getSelectedStart()) && usage.getKey().isBefore(mHDFViewModel.getSelectedEnd())) {
                                LocalDateTime ldt = usage.getKey();
                                int doy = ldt.getDayOfYear();
                                int mod = ldt.getHour() * 60 + ldt.getMinute();
                                int dow = ldt.getDayOfWeek().getValue();
                                double price = lookup.getRate(doy, mod, dow);
                                double rowBuy = price * usage.getValue() / 2D;
                                buy += rowBuy;
                                subTotals.addToPrice(price, usage.getValue() / 2D);
                            }
                        }
                        costing.setBuy(buy);
                        if (pp.isDeemedExport() && !(null == scenario) && scenario.isHasInverters()) {
                            sell = gridExportMax * 0.8148 * mHDFViewModel.getTotalDaysSelected() * pp.getFeed();
                            costing.setSell(sell);
                        }
                        else {
                            for (Map.Entry<LocalDateTime, Double> usage : mHDFViewModel.getExports().entrySet()) {
                                if (usage.getKey().isAfter(mHDFViewModel.getSelectedStart()) && usage.getKey().isBefore(mHDFViewModel.getSelectedEnd())) {
                                    double price = pp.getFeed();
                                    double rowSell = price * usage.getValue() / 2D;
                                    sell += rowSell;
                                }
                            }
                        }
                        costing.setSell(sell);
                        costing.setSubTotals(subTotals);
                        net = ((buy - sell) + (pp.getStandingCharges() * 100 * (mHDFViewModel.getTotalDaysSelected() / 365d)));
                        costing.setNet(net);
                        mHDFViewModel.getCostings().add(costing);
                        mMainHandler.post(this::updateCostView);
                    }
                    mHDFViewModel.setCosted(true);
                    mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                    mMainHandler.post(() -> costingStatus.setText(R.string.done));
                }).start();
            });

            generationRow.addView(generateLPButton);
            generationRow.addView(generationStatus);
            costingRow.addView(costHDFButton);
            costingRow.addView(costingStatus);

            if (!(mScenarioID == 0) ) mHDFLoadTable.addView(generationRow);
            mHDFLoadTable.addView(costingRow);

        }
    }

    // PROGRESS BAR
    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.hdf_activity);
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

    private void showHelp(String url) {
        mHelpWindow.setHeight((int) (getWindow().getDecorView().getHeight()*0.6));
        mHelpWindow.setWidth(getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(getWindow().getDecorView().getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }

    private void showPieChart(SubTotals subTotals, String planName) {
        if (mOrientation == Configuration.ORIENTATION_PORTRAIT)
            mPieChartWindow.setHeight((int) (getWindow().getDecorView().getHeight()*0.6));
        else
            mPieChartWindow.setWidth((int) (getWindow().getDecorView().getWidth()*0.6));
        mPieChartWindow.showAtLocation(getWindow().getDecorView(), Gravity.CENTER, 0, 0);
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

        for(String type: priceUnitsMap.keySet()){
            pieEntries.add(new PieEntry(Objects.requireNonNull(priceUnitsMap.get(type)).floatValue(), type));
        }
        PieDataSet pieDataSet = new PieDataSet(pieEntries,label);
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

class Row {
    String fullName;
    String net;
    String buy;
    String sell;
    SubTotals subTotals;
    String fixed;
}