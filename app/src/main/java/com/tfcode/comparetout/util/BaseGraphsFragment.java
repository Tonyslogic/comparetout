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

package com.tfcode.comparetout.util;

import static com.tfcode.comparetout.importers.alphaess.IntervalType.DOY;
import static com.tfcode.comparetout.importers.alphaess.IntervalType.HOUR;
import static com.tfcode.comparetout.importers.alphaess.IntervalType.MNTH;
import static com.tfcode.comparetout.importers.alphaess.IntervalType.WEEK;
import static com.tfcode.comparetout.importers.alphaess.IntervalType.YEAR;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.icu.text.DecimalFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textview.MaterialTextView;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.ChartView;
import com.tfcode.comparetout.importers.alphaess.CalculationType;
import com.tfcode.comparetout.importers.alphaess.IntervalType;
import com.tfcode.comparetout.importers.alphaess.StepIntervalType;
import com.tfcode.comparetout.model.importers.IntervalRow;
import com.tfcode.comparetout.model.importers.InverterDateRange;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public abstract class BaseGraphsFragment extends Fragment {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PARSER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MIDNIGHT = " 00:00:00";
    private static final String SHOW_LOAD = "SHOW_LOAD";
    private static final String SHOW_FEED = "SHOW_FEED";
    private static final String SHOW_BUY = "SHOW_BUY";
    private static final String SHOW_PV = "SHOW_PV";


    private static final String SHOW_PV2BAT = "SHOW_PV2BAT";
    private static final String SHOW_PV2LOAD = "SHOW_PV2LOAD";
    private static final String SHOW_BAT2LOAD = "SHOW_BAT2LOAD";
    private static final String SHOW_GRID2BAT = "SHOW_GRID2BAT";
    private static final String SHOW_EVSCHEDULE = "SHOW_EVSCHEDULE";
    private static final String SHOW_EVDIVERT = "SHOW_EVDIVERT";
    private static final String SHOW_HWSCHEDULE = "SHOW_HWSCHEDULE";
    private static final String SHOW_HWDIVERT = "SHOW_HWDIVERT";

    private static final String SYSTEM = "SYSTEM";
    private static final String FROM = "FROM";
    private static final String TO = "TO";
    private static final String INTERVAL = "INTERVAL";
    private static final String STEP_INTERVAL = "STEP_INTERVAL";
    private static final String CALCULATION = "CALCULATION";
    private static final String CHART = "CHART";
    private String mSystemSN;
    private ComparisonUIViewModel mViewModel;
    private Handler mMainHandler;
    private Map<String, Pair<String, String>> mInverterDateRangesBySN;
    private IntervalType mDisplayInterval = HOUR;
    private StepIntervalType mStepInterval = StepIntervalType.DAY;
    private CalculationType mCalculation = CalculationType.SUM;
    private ChartView mChartView = ChartView.BAR;
    private String mFrom;
    private String mTo;
    private ImageButton mIntervalButton;
    private ImageButton mModeButton;
    private ImageButton mFilterButton;
    protected PopupMenu mFilterPopup;
    private PopupMenu mGraphConfigPopup;
    private BarChart mBarChart;
    private LineChart mLineChart;
    private TableLayout mPieCharts;
    private TableLayout mTableChart;
    private TextView mPicks;
    private TextView mNoGraphDataTextView;
    private List<IntervalRow> mGraphData;

    protected boolean mShowLoad = true;
    protected boolean mShowFeed = true;
    protected boolean mShowBuy = true;
    protected boolean mShowPV = true;


    protected boolean mShowPv2bat = false;
    protected boolean mShowPv2load = false;
    protected boolean mShowBat2load = false;
    protected boolean mShowGrid2Bat = false;
    protected boolean mShowEVSchedule = false;
    protected boolean mShowEVDivert = false;
    protected boolean mShowHWSchedule = false;
    protected boolean mShowHWDivert = false;

    protected int mFilterCount = 4;
    protected ComparisonUIViewModel.Importer mImporterType;

    private static final int ORANGE = 0xFFFFA500;
    private static final int PURPLE = 0xFF800080;
    private static final int BROWN = 0xFFA52A2A;
    private static final int PALE_YELLOW_GREEN = 0xFFE0F2D2;
    private static final int DEEP_TEAL = 0xFF005666;

    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity()))
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_LOAD, mShowLoad);
        outState.putBoolean(SHOW_FEED, mShowFeed);
        outState.putBoolean(SHOW_BUY, mShowBuy);
        outState.putBoolean(SHOW_PV, mShowPV);

        outState.putBoolean(SHOW_PV2BAT, mShowPv2bat);
        outState.putBoolean(SHOW_PV2LOAD, mShowPv2load);
        outState.putBoolean(SHOW_BAT2LOAD, mShowBat2load);
        outState.putBoolean(SHOW_GRID2BAT, mShowGrid2Bat);
        outState.putBoolean(SHOW_EVSCHEDULE, mShowEVSchedule);
        outState.putBoolean(SHOW_EVDIVERT, mShowEVDivert);
        outState.putBoolean(SHOW_HWSCHEDULE, mShowHWSchedule);
        outState.putBoolean(SHOW_HWDIVERT, mShowHWDivert);

        outState.putString(SYSTEM, mSystemSN);
        outState.putString(FROM, mFrom);
        outState.putString(TO, mTo);
        outState.putInt(INTERVAL, mDisplayInterval.ordinal());
        outState.putInt(STEP_INTERVAL, mStepInterval.ordinal());
        outState.putInt(CALCULATION, mCalculation.ordinal());
        outState.putInt(CHART, mChartView.ordinal());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mShowLoad = savedInstanceState.getBoolean(SHOW_LOAD);
            mShowFeed = savedInstanceState.getBoolean(SHOW_FEED);
            mShowBuy = savedInstanceState.getBoolean(SHOW_BUY);
            mShowPV = savedInstanceState.getBoolean(SHOW_PV);

            mShowPv2bat = savedInstanceState.getBoolean(SHOW_PV2BAT);
            mShowPv2load = savedInstanceState.getBoolean(SHOW_PV2LOAD);
            mShowBat2load = savedInstanceState.getBoolean(SHOW_BAT2LOAD);
            mShowGrid2Bat = savedInstanceState.getBoolean(SHOW_GRID2BAT);
            mShowEVSchedule = savedInstanceState.getBoolean(SHOW_EVSCHEDULE);
            mShowEVDivert = savedInstanceState.getBoolean(SHOW_EVDIVERT);
            mShowHWSchedule = savedInstanceState.getBoolean(SHOW_HWSCHEDULE);
            mShowHWDivert = savedInstanceState.getBoolean(SHOW_HWDIVERT);

            mFilterCount = 0;
            if (mShowLoad) mFilterCount++;
            if (mShowFeed) mFilterCount++;
            if (mShowBuy) mFilterCount++;
            if (mShowPV) mFilterCount++;

            if (mShowPv2bat) mFilterCount++;
            if (mShowPv2load) mFilterCount++;
            if (mShowBat2load) mFilterCount++;
            if (mShowGrid2Bat) mFilterCount++;
            if (mShowEVSchedule) mFilterCount++;
            if (mShowEVDivert) mFilterCount++;
            if (mShowHWSchedule) mFilterCount++;
            if (mShowHWDivert) mFilterCount++;

            mSystemSN = savedInstanceState.getString(SYSTEM);
            mFrom = savedInstanceState.getString(FROM);
            mTo = savedInstanceState.getString(TO);
            setSelectionText();
            mDisplayInterval = IntervalType.values()[savedInstanceState.getInt(INTERVAL)];
            mStepInterval = StepIntervalType.values()[savedInstanceState.getInt(STEP_INTERVAL)];
            mCalculation = CalculationType.values()[savedInstanceState.getInt(CALCULATION)];
            mChartView = ChartView.values()[savedInstanceState.getInt(CHART)];
        }
        if (null == mSystemSN)
            mSystemSN = ((GraphableActivity) requireActivity()).getSelectedSystemSN();

        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getLiveDateRanges(mImporterType).observe(this, dateRanges -> {
            if (null == mInverterDateRangesBySN) mInverterDateRangesBySN = new HashMap<>();
            for (InverterDateRange inverterDateRange : dateRanges) {
                mInverterDateRangesBySN.put(inverterDateRange.sysSn, new Pair<>(inverterDateRange.startDate, inverterDateRange.finishDate));
            }
            if (!(null == mSystemSN) && !(null == mInverterDateRangesBySN.get(mSystemSN))) {
                if (null == savedInstanceState) {
                    mFrom = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).first;
                    mTo = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).second;
                    mMainHandler.post(this::setSelectionText);
                }
                mMainHandler.post(this::updateKPIs);
            }
        });
        updateKPIs();
    }

    private void updateKPIs() {
        new Thread(() -> {
            mGraphData = null;
            if (!(null == mViewModel)) {
                if (null == mFrom) mFrom = "1970-01-01";
                if (null == mTo) {
                    LocalDate now = LocalDate.now();
                    mTo = now.format(DATE_FORMAT);
                }
                if (null == mSystemSN)
                    mSystemSN = ((GraphableActivity) requireActivity()).getSelectedSystemSN();
                switch (mCalculation) {
                    case SUM:
                        switch (mDisplayInterval) {
                            case HOUR:
                                mGraphData = mViewModel.getSumHour(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                            case DOY:
                                mGraphData = mViewModel.getSumDOY(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                            case WEEK:
                                mGraphData = mViewModel.getSumDOW(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                            case MNTH:
                                mGraphData = mViewModel.getSumMonth(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                            case YEAR:
                                mGraphData = mViewModel.getSumYear(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                        }
                        break;
                    case AVG:
                        switch (mDisplayInterval) {
                            case HOUR:
                                mGraphData = mViewModel.getAvgHour(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                            case DOY:
                                mGraphData = mViewModel.getAvgDOY(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                            case WEEK:
                                mGraphData = mViewModel.getAvgDOW(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                            case MNTH:
                                mGraphData = mViewModel.getAvgMonth(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                            case YEAR:
                                mGraphData = mViewModel.getAvgYear(mImporterType, mSystemSN, mFrom, mTo);
                                break;
                        }
                        break;
                }

                if (!(null == mMainHandler)) mMainHandler.post(this::updateView);
            }
        }).start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_import_alpha_daily, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMainHandler = new Handler(Looper.getMainLooper());
        mPicks = view.findViewById((R.id.picks));
        setSelectionText();

        mNoGraphDataTextView = view.findViewById((R.id.alphaess_no_data));
        mBarChart = view.findViewById((R.id.alphaess_bar_chart));
        mLineChart = view.findViewById((R.id.alphaess_line_chart));
        mPieCharts = view.findViewById((R.id.alphaess_pie_chart));
        mTableChart = view.findViewById((R.id.alphaess_table_chart));

        mIntervalButton = view.findViewById((R.id.interval));
        setupPopupGraphConfigurationMenu();

        mModeButton = view.findViewById(R.id.mode);
        if (mCalculation == CalculationType.SUM) mModeButton.setImageResource(R.drawable.sigma);
        else mModeButton.setImageResource(R.drawable.value_average);
        mModeButton.setOnClickListener(v -> {
            mCalculation = mCalculation.next();
            setSelectionText();
            if (mCalculation == CalculationType.SUM) mModeButton.setImageResource(R.drawable.sigma);
            else mModeButton.setImageResource(R.drawable.value_average);
            updateKPIs();
        });

        ImageButton mPreviousButton = view.findViewById((R.id.previous));
        mPreviousButton.setOnClickListener(v -> {
            LocalDateTime start = LocalDateTime.parse(mFrom + MIDNIGHT, PARSER_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(mTo + MIDNIGHT, PARSER_FORMATTER);
            switch (mStepInterval) {
                case DAY:
                    // + 1 day
                    start = start.plusDays(-1);
                    mFrom = start.format(DATE_FORMAT);
                    end = end.plusDays(-1);
                    mTo = end.format(DATE_FORMAT);
                    break;
                case WEEK:
                    start = start.plusDays(-7);
                    mFrom = start.format(DATE_FORMAT);
                    end = end.plusDays(-7);
                    mTo = end.format(DATE_FORMAT);
                    break;
                case MONTH:
                    // + 1 month
                    start = start.plusMonths(-1);
                    mFrom = start.format(DATE_FORMAT);
                    int daysInMonth = YearMonth.of(end.getYear(), end.getMonthValue()).lengthOfMonth();
                    if (daysInMonth == end.getDayOfMonth()) {
                        end = end.plusMonths(-1);
                        int lastDay = YearMonth.of(end.getYear(), end.getMonth()).lengthOfMonth();
                        end = LocalDateTime.of(end.getYear(), end.getMonthValue(), lastDay, 23, 59);
                    } else end = end.plusMonths(-1);
                    mTo = end.format(DATE_FORMAT);
                    break;
                case YEAR:
                    // + 1 month
                    start = start.plusYears(-1);
                    mFrom = start.format(DATE_FORMAT);
                    end = end.plusYears(-1);
                    mTo = end.format(DATE_FORMAT);
                    break;
            }
            setSelectionText();
            updateKPIs();
        });

        ImageButton mSelectionButton = view.findViewById(R.id.date);
        mSelectionButton.setOnClickListener(v -> {

            CalendarConstraints.Builder calendarConstraintsBuilder = new CalendarConstraints.Builder();
            if (!(null == mSystemSN) && !(null == mInverterDateRangesBySN) && !(null == mInverterDateRangesBySN.get(mSystemSN))) {
                String first = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).first;
                String second = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).second;
                System.out.println("Range available: " + first + ", " + second);
                LocalDateTime last = LocalDateTime.parse(second + MIDNIGHT, PARSER_FORMATTER);
                ZonedDateTime lastz = last.atZone(ZoneId.systemDefault());
                calendarConstraintsBuilder.setEnd(lastz.toInstant().toEpochMilli());
                LocalDateTime start = LocalDateTime.parse(first + MIDNIGHT, PARSER_FORMATTER);
                ZonedDateTime startz = start.atZone(ZoneId.systemDefault());
                calendarConstraintsBuilder.setStart(startz.toInstant().toEpochMilli());
            } else System.out.println("No range available");

            MaterialDatePicker.Builder<Pair<Long, Long>> materialDateBuilder = MaterialDatePicker.Builder
                    .dateRangePicker()
                    .setCalendarConstraints(calendarConstraintsBuilder.build());
            final MaterialDatePicker<Pair<Long, Long>> materialDatePicker = materialDateBuilder.build();
            materialDatePicker.show(getParentFragmentManager(), "MATERIAL_DATE_PICKER");

            materialDatePicker.addOnPositiveButtonClickListener(selection -> {
                mFrom = LocalDateTime.ofInstant(Instant.ofEpochMilli(selection.first), ZoneId.systemDefault()).format(DATE_FORMAT);
                mTo = LocalDateTime.ofInstant(Instant.ofEpochMilli(selection.second), ZoneId.systemDefault()).format(DATE_FORMAT);
                setSelectionText();
                updateKPIs();
            });
        });

        ImageButton mNextButton = view.findViewById(R.id.next);
        mNextButton.setOnClickListener(v -> {
            LocalDateTime start = LocalDateTime.parse(mFrom + MIDNIGHT, PARSER_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(mTo + MIDNIGHT, PARSER_FORMATTER);
            switch (mStepInterval) {
                case DAY:
                    // + 1 day
                    start = start.plusDays(1);
                    mFrom = start.format(DATE_FORMAT);
                    end = end.plusDays(1);
                    mTo = end.format(DATE_FORMAT);
                    break;
                case WEEK:
                    start = start.plusDays(7);
                    mFrom = start.format(DATE_FORMAT);
                    end = end.plusDays(7);
                    mTo = end.format(DATE_FORMAT);
                    break;
                case MONTH:
                    // + 1 month
                    start = start.plusMonths(1);
                    mFrom = start.format(DATE_FORMAT);
                    int daysInMonth = YearMonth.of(end.getYear(), end.getMonthValue()).lengthOfMonth();
                    if (daysInMonth == end.getDayOfMonth()) {
                        end = end.plusMonths(1);
                        int lastDay = YearMonth.of(end.getYear(), end.getMonth()).lengthOfMonth();
                        end = LocalDateTime.of(end.getYear(), end.getMonthValue(), lastDay, 23, 59);
                    } else end = end.plusMonths(1);
                    mTo = end.format(DATE_FORMAT);
                    break;
                case YEAR:
                    // + 1 month
                    start = start.plusYears(1);
                    mFrom = start.format(DATE_FORMAT);
                    end = end.plusYears(1);
                    mTo = end.format(DATE_FORMAT);
                    break;
            }
            setSelectionText();
            updateKPIs();
        });

        ImageButton mChartTypeButton = view.findViewById(R.id.chartType);
        if (mChartView == ChartView.BAR) mChartTypeButton.setImageResource(R.drawable.line_chart);
        if (mChartView == ChartView.LINE) mChartTypeButton.setImageResource(R.drawable.piechart_25);
        if (mChartView == ChartView.PIE) mChartTypeButton.setImageResource(R.drawable.barchart);
        if (mChartView == ChartView.TABLE) mChartTypeButton.setImageResource(R.drawable.table);
        mChartTypeButton.setOnClickListener(v -> {
            mChartView = mChartView.next();
            if (mChartView == ChartView.BAR) mChartTypeButton.setImageResource(R.drawable.line_chart);
            if (mChartView == ChartView.LINE)
                mChartTypeButton.setImageResource(R.drawable.piechart_25);
            if (mChartView == ChartView.PIE)
                mChartTypeButton.setImageResource(R.drawable.table);
            if (mChartView == ChartView.TABLE)
                mChartTypeButton.setImageResource(R.drawable.barchart);
            setSelectionText();
            updateKPIs();
        });

        mFilterButton = view.findViewById(R.id.filter);
        setupPopupFilterMenu();
    }

    @SuppressLint("SetTextI18n")
    private void setSelectionText() {
        if (!(null == mFrom) && !(null == mTo) && !(null == mPicks))
            mPicks.setText("Range: " + mFrom + "<->" + mTo);
    }

    private void setupPopupGraphConfigurationMenu() {
        if (null == mGraphConfigPopup) {
            mGraphConfigPopup = new PopupMenu(requireActivity(), mIntervalButton, Gravity.CENTER_HORIZONTAL);
            mGraphConfigPopup.getMenuInflater().inflate(R.menu.popup_menu_graph, mGraphConfigPopup.getMenu());

            switch (mDisplayInterval) {
                case HOUR:
                    mGraphConfigPopup.getMenu().findItem(R.id.display_hour).setChecked(true);
                    break;
                case WEEK:
                    mGraphConfigPopup.getMenu().findItem(R.id.display_week).setChecked(true);
                    break;
                case DOY:
                    mGraphConfigPopup.getMenu().findItem(R.id.display_doy).setChecked(true);
                    break;
                case MNTH:
                    mGraphConfigPopup.getMenu().findItem(R.id.display_month).setChecked(true);
                    break;
                case YEAR:
                    mGraphConfigPopup.getMenu().findItem(R.id.display_year).setChecked(true);
                    break;
            }
            switch (mStepInterval) {
                case DAY:
                    mGraphConfigPopup.getMenu().findItem(R.id.step_day).setChecked(true);
                    break;
                case WEEK:
                    mGraphConfigPopup.getMenu().findItem(R.id.step_week).setChecked(true);
                    break;
                case MONTH:
                    mGraphConfigPopup.getMenu().findItem(R.id.step_month).setChecked(true);
                    break;
                case YEAR:
                    mGraphConfigPopup.getMenu().findItem(R.id.step_year).setChecked(true);
                    break;
            }


            mGraphConfigPopup.setOnMenuItemClickListener(item -> {
                item.setChecked(!item.isChecked());
                int itemID = item.getItemId();

                if (itemID == R.id.display_hour) mDisplayInterval = HOUR;
                if (itemID == R.id.display_week) mDisplayInterval = WEEK;
                if (itemID == R.id.display_doy) mDisplayInterval = DOY;
                if (itemID == R.id.display_month) mDisplayInterval = MNTH;
                if (itemID == R.id.display_year) mDisplayInterval = YEAR;

                if (itemID == R.id.step_day) mStepInterval = StepIntervalType.DAY;
                if (itemID == R.id.step_week) mStepInterval = StepIntervalType.WEEK;
                if (itemID == R.id.step_month) mStepInterval = StepIntervalType.MONTH;
                if (itemID == R.id.step_year) mStepInterval = StepIntervalType.YEAR;

                updateKPIs();
                // Keep the popup menu open
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
                item.setActionView(new View(getActivity()));
                item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                        return false;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                        return false;
                    }
                });
                return false;
            });
        }
        mIntervalButton.setOnClickListener(v -> mGraphConfigPopup.show());
    }

    protected void setupPopupFilterMenu() {
        if (null == mFilterPopup) {
            //Creating the instance of PopupMenu
            mFilterPopup = new PopupMenu(requireActivity(), mFilterButton, Gravity.CENTER_HORIZONTAL);
            mFilterPopup.getMenuInflater()
                    .inflate(R.menu.popup_menu_filter, mFilterPopup.getMenu());
            mFilterPopup.getMenu().findItem(R.id.load).setChecked(mShowLoad);
            mFilterPopup.getMenu().findItem(R.id.feed).setChecked(mShowFeed);
            mFilterPopup.getMenu().findItem(R.id.buy).setChecked(mShowBuy);
            mFilterPopup.getMenu().findItem(R.id.pv).setChecked(mShowPV);
            mFilterPopup.getMenu().findItem(R.id.pv2bat).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.pv2load).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.bat2load).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.gridToBattery).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.evSchedule).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.hwSchedule).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.evDivert).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.hwDivert).setVisible(false);

            mFilterPopup.getMenu().findItem(R.id.soc).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.hwTemp).setVisible(false);
        }

        mFilterPopup.setOnMenuItemClickListener(item -> {
            item.setChecked(!item.isChecked());
            int itemID = item.getItemId();
            if (itemID == R.id.load) {
                mShowLoad = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.feed) {
                mShowFeed = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.buy) {
                mShowBuy = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.pv) {
                mShowPV = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }

            if (itemID == R.id.pv2bat) {
                mShowPv2bat = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.pv2load) {
                mShowPv2load = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.bat2load) {
                mShowBat2load = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.gridToBattery) {
                mShowGrid2Bat = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.evSchedule) {
                mShowEVSchedule = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.hwSchedule) {
                mShowHWSchedule = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.evDivert) {
                mShowEVDivert = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }
            if (itemID == R.id.hwDivert) {
                mShowHWDivert = item.isChecked();
                mFilterCount = item.isChecked() ? mFilterCount + 1 : mFilterCount - 1;
            }

            updateKPIs();
            // Keep the popup menu open
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            item.setActionView(new View(getActivity()));
            item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                    return false;
                }

                @Override
                public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                    return false;
                }
            });
            return false;
        });

        mFilterButton.setOnClickListener(v -> mFilterPopup.show());
    }

    private void updateView() {
        if (null == getContext()) return;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            GraphableActivity activity = (GraphableActivity) getActivity();
            if (!(null == activity)) activity.hideFAB();
        }

        boolean showText = false;
        if (!(null == mGraphData) && !mGraphData.isEmpty()) {
            try {
                switch (mChartView) {
                    case BAR:
                        mBarChart.setVisibility(View.VISIBLE);
                        mLineChart.setVisibility(View.INVISIBLE);
                        mPieCharts.setVisibility(View.INVISIBLE);
                        mTableChart.setVisibility(View.INVISIBLE);
                        mNoGraphDataTextView.setVisibility(View.INVISIBLE);
                        if (mFilterCount > 0) buildBarChart();
                        else {
                            mBarChart.setVisibility(View.INVISIBLE);
                            mNoGraphDataTextView.setVisibility(View.VISIBLE);
                            mNoGraphDataTextView.setText(R.string.empty_filter);
                        }
                        break;
                    case LINE:
                        mBarChart.setVisibility(View.INVISIBLE);
                        mLineChart.setVisibility(View.VISIBLE);
                        mPieCharts.setVisibility(View.INVISIBLE);
                        mTableChart.setVisibility(View.INVISIBLE);
                        mNoGraphDataTextView.setVisibility(View.INVISIBLE);
                        if (mFilterCount > 0) buildLineChart();
                        else {
                            mLineChart.setVisibility(View.INVISIBLE);
                            mNoGraphDataTextView.setVisibility(View.VISIBLE);
                            mNoGraphDataTextView.setText(R.string.empty_filter);
                        }
                        break;
                    case PIE:
                        mBarChart.setVisibility(View.INVISIBLE);
                        mLineChart.setVisibility(View.INVISIBLE);
                        mPieCharts.setVisibility(View.VISIBLE);
                        mTableChart.setVisibility(View.INVISIBLE);
                        mNoGraphDataTextView.setVisibility(View.INVISIBLE);
                        if (mFilterCount > 0) buildPieCharts();
                        else {
                            mPieCharts.setVisibility(View.INVISIBLE);
                            mNoGraphDataTextView.setVisibility(View.VISIBLE);
                            mNoGraphDataTextView.setText(R.string.empty_filter);
                        }
                        break;
                    case TABLE:
                        mBarChart.setVisibility(View.INVISIBLE);
                        mLineChart.setVisibility(View.INVISIBLE);
                        mPieCharts.setVisibility(View.INVISIBLE);
                        mTableChart.setVisibility(View.VISIBLE);
                        mNoGraphDataTextView.setVisibility(View.INVISIBLE);
                        if (mFilterCount > 0) buildTableChart();
                        else {
                            mTableChart.setVisibility(View.INVISIBLE);
                            mNoGraphDataTextView.setVisibility(View.VISIBLE);
                            mNoGraphDataTextView.setText(R.string.empty_filter);
                        }
                        break;
                }
            } catch (NullPointerException npe) {
                npe.printStackTrace();
                mBarChart.setVisibility(View.INVISIBLE);
                mLineChart.setVisibility(View.INVISIBLE);
                mPieCharts.setVisibility(View.INVISIBLE);
                mNoGraphDataTextView.setVisibility(View.VISIBLE);
                mNoGraphDataTextView.setText(R.string.data_load_race_mesage);
            }
        } else showText = true;

        if (showText || (null == mSystemSN)) {
            if (mBarChart != null) {
                mBarChart.setVisibility(View.INVISIBLE);
            }
            if (mLineChart != null) {
                mLineChart.setVisibility(View.INVISIBLE);
            }
            if (mPieCharts != null) {
                mPieCharts.setVisibility(View.INVISIBLE);
            }
            if (mTableChart != null) {
                mTableChart.setVisibility(View.INVISIBLE);
            }
            mNoGraphDataTextView.setVisibility(View.VISIBLE);
            mNoGraphDataTextView.setText(R.string.no_data_available);
        }
    }

    private void buildTableChart() {
        Activity activity = getActivity();
        if (!(null == mTableChart) && (!(null == activity))) {
            mTableChart.removeAllViews();
            mTableChart.setStretchAllColumns(true);
            TableRow titleRow = new TableRow(activity);
            String displayInterval = getString(R.string.hour);
            switch (mDisplayInterval) {
                case HOUR:
                    displayInterval = getString(R.string.hour);
                    break;
                case DOY:
                    displayInterval = getString(R.string.DOY);
                    break;
                case WEEK:
                    displayInterval = getString(R.string.Week);
                    break;
                case MNTH:
                    displayInterval = getString(R.string.monthly);
                    break;
                case YEAR:
                    displayInterval = getString(R.string.annually);
                    break;
            }
            titleRow.addView(getTableChartTitleHeader(displayInterval, activity));
            if (mShowLoad) titleRow.addView(getTableChartTitleHeader(getString(R.string.load) + "(kWh)", activity));
            if (mShowPV) titleRow.addView(getTableChartTitleHeader(getString(R.string.pv) + "(kWh)", activity));
            if (mShowBuy) titleRow.addView(getTableChartTitleHeader(getString(R.string.buy) + "(kWh)", activity));
            if (mShowFeed) titleRow.addView(getTableChartTitleHeader(getString(R.string.feed) + "(kWh)", activity));

            if (mShowPv2bat) titleRow.addView(getTableChartTitleHeader("PV2Bat" + "(kWh)", activity));
            if (mShowPv2load) titleRow.addView(getTableChartTitleHeader("PV2Load" + "(kWh)", activity));
            if (mShowBat2load) titleRow.addView(getTableChartTitleHeader("Bat2Load" + "(kWh)", activity));
            if (mShowGrid2Bat) titleRow.addView(getTableChartTitleHeader("Grid2Bat" + "(kWh)", activity));
            if (mShowEVSchedule) titleRow.addView(getTableChartTitleHeader("EVSchedule" + "(kWh)", activity));
            if (mShowEVDivert) titleRow.addView(getTableChartTitleHeader("EVDivert" + "(kWh)", activity));
            if (mShowHWSchedule) titleRow.addView(getTableChartTitleHeader("HWSchedule" + "(kWh)", activity));
            if (mShowHWDivert) titleRow.addView(getTableChartTitleHeader("HWDivert" + "(kWh)", activity));
            mTableChart.addView(titleRow);

            for (IntervalRow intervalRow : mGraphData) {
                TableRow row = new TableRow(activity);
                row.addView(getTableChartIntervalContent(intervalRow.interval, activity));
                if (mShowLoad) row.addView(getTableChartContent(intervalRow.load, activity));
                if (mShowPV) row.addView(getTableChartContent(intervalRow.pv, activity));
                if (mShowBuy) row.addView(getTableChartContent(intervalRow.buy, activity));
                if (mShowFeed) row.addView(getTableChartContent(intervalRow.feed, activity));

                if (mShowPv2bat) row.addView(getTableChartContent(intervalRow.pv2bat, activity));
                if (mShowPv2load) row.addView(getTableChartContent(intervalRow.pv2load, activity));
                if (mShowBat2load) row.addView(getTableChartContent(intervalRow.bat2load, activity));
                if (mShowGrid2Bat) row.addView(getTableChartContent(intervalRow.grid2bat, activity));
                if (mShowEVSchedule) row.addView(getTableChartContent(intervalRow.evSchedule, activity));
                if (mShowEVDivert) row.addView(getTableChartContent(intervalRow.evDivert, activity));
                if (mShowHWSchedule) row.addView(getTableChartContent(intervalRow.hwSchedule, activity));
                if (mShowHWDivert) row.addView(getTableChartContent(intervalRow.hwDivert, activity));
                mTableChart.addView(row);
            }
        }
    }

    private View getTableChartIntervalContent(String interval, Activity activity) {
        String value = "";

        switch (mDisplayInterval) {
            case HOUR:
            case DOY: value = interval; break;
            case WEEK:
                switch (interval) {
                    case "0": value = getString(R.string.Sun); break;
                    case "1": value = getString(R.string.Mon); break;
                    case "2": value = getString(R.string.Tue); break;
                    case "3": value = getString(R.string.Wed); break;
                    case "4": value = getString(R.string.Thu); break;
                    case "5": value = getString(R.string.Fri); break;
                    case "6": value = getString(R.string.Sat); break;
                }
                break;
            case MNTH:
                switch (mCalculation) {
                    case SUM: value = interval; break;
                    case AVG:
                        switch (interval) {
                            case "01": value = getString(R.string.Jan); break;
                            case "02": value = getString(R.string.Feb); break;
                            case "03": value = getString(R.string.Mar); break;
                            case "04": value = getString(R.string.Apr); break;
                            case "05": value = getString(R.string.May); break;
                            case "06": value = getString(R.string.Jun); break;
                            case "07": value = getString(R.string.Jul); break;
                            case "08": value = getString(R.string.Aug); break;
                            case "09": value = getString(R.string.Sep); break;
                            case "10": value = getString(R.string.Oct); break;
                            case "11": value = getString(R.string.Nov); break;
                            case "12": value = getString(R.string.Dec); break;
                        }
                }
                break;
            case YEAR:
                switch (mCalculation) {
                    case SUM: value = interval; break;
                    case AVG: value = "All";
                }
                break;
        }
        MaterialTextView materialTextView = new MaterialTextView(activity);
        materialTextView.setText(value);
        return materialTextView;
    }

    private View getTableChartContent(double value, Activity activity) {
        MaterialTextView materialTextView = new MaterialTextView(activity);
        materialTextView.setText(String.valueOf(Math.round(value*100)/100D));
        return materialTextView;
    }

    private View getTableChartTitleHeader(String title, @NonNull Activity activity) {
        MaterialTextView materialTextView = new MaterialTextView(activity);
        materialTextView.setText(title);
        return materialTextView;
    }

    private void buildPieCharts() {
        if (!(null == mPieCharts) && (!(null == getActivity()))) {
            mPieCharts.removeAllViews();
            mPieCharts.setStretchAllColumns(true);
            Map<String, Double> loadMap = new HashMap<>();
            Map<String, Double> pvMap = new HashMap<>();
            Map<String, Double> buyMap = new HashMap<>();
            Map<String, Double> feedMap = new HashMap<>();

            Map<String, Double> pv2batMap = new HashMap<>();
            Map<String, Double> pv2loadMap = new HashMap<>();
            Map<String, Double> bat2loadMap = new HashMap<>();
            Map<String, Double> grid2batMap = new HashMap<>();
            Map<String, Double> evScheduleMap = new HashMap<>();
            Map<String, Double> evDivertMap = new HashMap<>();
            Map<String, Double> hwScheduleMap = new HashMap<>();
            Map<String, Double> hwDivertMap = new HashMap<>();

            double load = 0D;
            double pv = 0D;
            double buy = 0D;
            double feed = 0D;

            double pv2bat = 0D;
            double pv2load = 0D;
            double bat2load = 0D;
            double grid2bat = 0D;
            double evSchedule = 0D;
            double evDivert = 0D;
            double hwSchedule = 0D;
            double hwDivert = 0D;
            switch (mDisplayInterval) {
                case YEAR:
                    if (mCalculation == CalculationType.SUM) {
                        for (IntervalRow intervalRow : mGraphData) {
                            loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.load);
                            pvMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv);
                            buyMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.buy);
                            feedMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.feed);

                            pv2batMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv2bat);
                            pv2loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv2load);
                            bat2loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.bat2load);
                            grid2batMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.grid2bat);
                            evScheduleMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.evSchedule);
                            evDivertMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.evDivert);
                            hwScheduleMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.hwSchedule);
                            hwDivertMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.hwDivert);

                            load += intervalRow.load;
                            pv += intervalRow.pv;
                            buy += intervalRow.buy;
                            feed += intervalRow.feed;

                            pv2bat += intervalRow.pv2bat;
                            pv2load += intervalRow.pv2load;
                            bat2load += intervalRow.bat2load;
                            grid2bat += intervalRow.grid2bat;
                            evSchedule += intervalRow.evSchedule;
                            evDivert += intervalRow.evDivert;
                            hwSchedule += intervalRow.hwSchedule;
                            hwDivert += intervalRow.hwDivert;
                        }
                    } else {
                        for (IntervalRow intervalRow : mGraphData) {
                            loadMap.put("All", intervalRow.load);
                            pvMap.put("All", intervalRow.pv);
                            buyMap.put("All", intervalRow.buy);
                            feedMap.put("All", intervalRow.feed);

                            pv2batMap.put("All", intervalRow.pv2bat);
                            pv2loadMap.put("All", intervalRow.pv2load);
                            bat2loadMap.put("All", intervalRow.bat2load);
                            grid2batMap.put("All", intervalRow.grid2bat);
                            evScheduleMap.put("All", intervalRow.evSchedule);
                            evDivertMap.put("All", intervalRow.evDivert);
                            hwScheduleMap.put("All", intervalRow.hwSchedule);
                            hwDivertMap.put("All", intervalRow.hwDivert);

                            load += intervalRow.load;
                            pv += intervalRow.pv;
                            buy += intervalRow.buy;
                            feed += intervalRow.feed;

                            pv2bat += intervalRow.pv2bat;
                            pv2load += intervalRow.pv2load;
                            bat2load += intervalRow.bat2load;
                            grid2bat += intervalRow.grid2bat;
                            evSchedule += intervalRow.evSchedule;
                            evDivert += intervalRow.evDivert;
                            hwSchedule += intervalRow.hwSchedule;
                            hwDivert += intervalRow.hwDivert;
                        }
                    }
                    break;
                case MNTH:
                    if (mCalculation == CalculationType.SUM) {
                        for (IntervalRow intervalRow : mGraphData) {
                            if (intervalRow.load > 0)
                                loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.load);
                            if (intervalRow.pv > 0)
                                pvMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv);
                            if (intervalRow.buy > 0)
                                buyMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.buy);
                            if (intervalRow.feed > 0)
                                feedMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.feed);

                            if (intervalRow.pv2bat > 0)
                                pv2batMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv2bat);
                            if (intervalRow.pv2load > 0)
                                pv2loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv2load);
                            if (intervalRow.bat2load > 0)
                                bat2loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.bat2load);
                            if (intervalRow.grid2bat > 0)
                                grid2batMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.grid2bat);
                            if (intervalRow.hwSchedule > 0)
                                hwScheduleMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.hwSchedule);
                            if (intervalRow.hwDivert > 0)
                                hwDivertMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.hwDivert);
                            if (intervalRow.evSchedule > 0)
                                evScheduleMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.evSchedule);
                            if (intervalRow.evDivert > 0)
                                evDivertMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.evDivert);

                            load += intervalRow.load;
                            pv += intervalRow.pv;
                            buy += intervalRow.buy;
                            feed += intervalRow.feed;

                            pv2bat += intervalRow.pv2bat;
                            pv2load += intervalRow.pv2load;
                            bat2load += intervalRow.bat2load;
                            grid2bat += intervalRow.grid2bat;
                            evSchedule += intervalRow.evSchedule;
                            evDivert += intervalRow.evDivert;
                            hwSchedule += intervalRow.hwSchedule;
                            hwDivert += intervalRow.hwDivert;
                        }
                    } else {
                        final String[] xLabels = {"NaM", "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",};
                        final ArrayList<String> xLabel = new ArrayList<>(Arrays.asList(xLabels));
                        for (IntervalRow intervalRow : mGraphData) {
                            if (intervalRow.load > 0)
                                loadMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.load);
                            if (intervalRow.pv > 0)
                                pvMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.pv);
                            if (intervalRow.buy > 0)
                                buyMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.buy);
                            if (intervalRow.feed > 0)
                                feedMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.feed);

                            if (intervalRow.pv2bat > 0)
                                pv2batMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.pv2bat);
                            if (intervalRow.pv2load > 0)
                                pv2loadMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.pv2load);
                            if (intervalRow.bat2load > 0)
                                bat2loadMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.bat2load);
                            if (intervalRow.grid2bat > 0)
                                grid2batMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.grid2bat);
                            if (intervalRow.evSchedule > 0)
                                evScheduleMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.evSchedule);
                            if (intervalRow.evDivert > 0)
                                evDivertMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.evDivert);
                            if (intervalRow.hwSchedule > 0)
                                hwScheduleMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.hwSchedule);
                            if (intervalRow.hwDivert > 0)
                                hwDivertMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.hwDivert);

                            load += intervalRow.load;
                            pv += intervalRow.pv;
                            buy += intervalRow.buy;
                            feed += intervalRow.feed;

                            pv2bat += intervalRow.pv2bat;
                            pv2load += intervalRow.pv2load;
                            bat2load += intervalRow.bat2load;
                            grid2bat += intervalRow.grid2bat;
                            evSchedule += intervalRow.evSchedule;
                            evDivert += intervalRow.evDivert;
                            hwSchedule += intervalRow.hwSchedule;
                            hwDivert += intervalRow.hwDivert;
                        }
                    }
                    break;
                case WEEK:
                    final String[] xLabels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",};
                    final ArrayList<String> xLabel = new ArrayList<>(Arrays.asList(xLabels));
                    for (IntervalRow intervalRow : mGraphData) {
                        if (intervalRow.load > 0)
                            loadMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.load);
                        if (intervalRow.pv > 0)
                            pvMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.pv);
                        if (intervalRow.buy > 0)
                            buyMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.buy);
                        if (intervalRow.feed > 0)
                            feedMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.feed);

                        if (intervalRow.pv2bat > 0)
                            pv2batMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.pv2bat);
                        if (intervalRow.pv2load > 0)
                            pv2loadMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.pv2load);
                        if (intervalRow.bat2load > 0)
                            bat2loadMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.bat2load);
                        if (intervalRow.grid2bat > 0)
                            grid2batMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.grid2bat);
                        if (intervalRow.evSchedule > 0)
                            evScheduleMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.evSchedule);
                        if (intervalRow.evDivert > 0)
                            evDivertMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.evDivert);
                        if (intervalRow.hwSchedule > 0)
                            hwScheduleMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.hwSchedule);
                        if (intervalRow.hwDivert > 0)
                            hwDivertMap.put(xLabel.get(Integer.parseInt(intervalRow.interval)), intervalRow.hwDivert);

                        load += intervalRow.load;
                        pv += intervalRow.pv;
                        buy += intervalRow.buy;
                        feed += intervalRow.feed;

                        pv2bat += intervalRow.pv2bat;
                        pv2load += intervalRow.pv2load;
                        bat2load += intervalRow.bat2load;
                        grid2bat += intervalRow.grid2bat;
                        evSchedule += intervalRow.evSchedule;
                        evDivert += intervalRow.evDivert;
                        hwSchedule += intervalRow.hwSchedule;
                        hwDivert += intervalRow.hwDivert;
                    }
                    break;
                case DOY:
                case HOUR:
                    for (IntervalRow intervalRow : mGraphData) {
                        if (intervalRow.load > 0)
                            loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.load);
                        if (intervalRow.pv > 0)
                            pvMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv);
                        if (intervalRow.buy > 0)
                            buyMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.buy);
                        if (intervalRow.feed > 0)
                            feedMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.feed);

                        if (intervalRow.pv2bat > 0)
                            pv2batMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv2bat);
                        if (intervalRow.pv2load > 0)
                            pv2loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.pv2load);
                        if (intervalRow.bat2load > 0)
                            bat2loadMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.bat2load);
                        if (intervalRow.grid2bat > 0)
                            grid2batMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.grid2bat);
                        if (intervalRow.evSchedule > 0)
                            evScheduleMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.evSchedule);
                        if (intervalRow.evDivert > 0)
                            evDivertMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.evDivert);
                        if (intervalRow.hwSchedule > 0)
                            hwScheduleMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.hwSchedule);
                        if (intervalRow.hwDivert > 0)
                            hwDivertMap.put(Integer.valueOf(intervalRow.interval).toString(), intervalRow.hwDivert);

                        load += intervalRow.load;
                        pv += intervalRow.pv;
                        buy += intervalRow.buy;
                        feed += intervalRow.feed;

                        pv2bat += intervalRow.pv2bat;
                        pv2load += intervalRow.pv2load;
                        bat2load += intervalRow.bat2load;
                        grid2bat += intervalRow.grid2bat;
                        evSchedule += intervalRow.evSchedule;
                        evDivert += intervalRow.evDivert;
                        hwSchedule += intervalRow.hwSchedule;
                        hwDivert += intervalRow.hwDivert;
                    }
                    break;

            }

            PieChart loadPie = getPieChart("Load (" + new DecimalFormat("0.00").format(load) + " kWh)", loadMap);
            PieChart pvPie = getPieChart("PV (" + new DecimalFormat("0.00").format(pv) + " kWh)", pvMap);
            PieChart buyPie = getPieChart("Buy (" + new DecimalFormat("0.00").format(buy) + " kWh)", buyMap);
            PieChart feedPie = getPieChart("Feed (" + new DecimalFormat("0.00").format(feed) + " kWh)", feedMap);

            PieChart pv2batPie = getPieChart("PV2Bat (" + new DecimalFormat("0.00").format(pv2bat) + " kWh)", pv2batMap);
            PieChart pv2LoadPie = getPieChart("PV2Load (" + new DecimalFormat("0.00").format(pv2load) + " kWh)", pv2loadMap);
            PieChart bat2LoadPie = getPieChart("Bat2Load (" + new DecimalFormat("0.00").format(bat2load) + " kWh)", bat2loadMap);
            PieChart grid2BatPie = getPieChart("Grid2Bat (" + new DecimalFormat("0.00").format(grid2bat) + " kWh)", grid2batMap);
            PieChart evSchedulePie = getPieChart("EVSchedule (" + new DecimalFormat("0.00").format(evSchedule) + " kWh)", evScheduleMap);
            PieChart evDivertPie = getPieChart("EVDivert (" + new DecimalFormat("0.00").format(evDivert) + " kWh)", evDivertMap);
            PieChart hwSchedulePie = getPieChart("HWSchedule (" + new DecimalFormat("0.00").format(hwSchedule) + " kWh)", hwScheduleMap);
            PieChart hwDivertPie = getPieChart("HWDivert (" + new DecimalFormat("0.00").format(hwDivert) + " kWh)", hwDivertMap);

            List<PieChart> piesToShow = new ArrayList<>();
            if (mShowLoad) piesToShow.add(loadPie);
            if (mShowPV) piesToShow.add(pvPie);
            if (mShowBuy) piesToShow.add(buyPie);
            if (mShowFeed) piesToShow.add(feedPie);

            if (mShowPv2bat) piesToShow.add(pv2batPie);
            if (mShowPv2load) piesToShow.add(pv2LoadPie);
            if (mShowBat2load) piesToShow.add(bat2LoadPie);
            if (mShowGrid2Bat) piesToShow.add(grid2BatPie);
            if (mShowEVSchedule) piesToShow.add(evSchedulePie);
            if (mShowEVDivert) piesToShow.add(evDivertPie);
            if (mShowHWSchedule) piesToShow.add(hwSchedulePie);
            if (mShowHWDivert) piesToShow.add(hwDivertPie);

            int toShowInRow = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 2;
            TableRow aRow = new TableRow(getActivity());
            int shownInRow = 0;
            for (PieChart pie : piesToShow) {
                aRow.addView(pie);
                shownInRow++;
                if (shownInRow == toShowInRow) {
                    mPieCharts.addView(aRow);
                    aRow = new TableRow(getActivity());
                    shownInRow = 0;
                }
            }
            if (shownInRow > 0) {
                mPieCharts.addView(aRow);
            }
        }
    }

    private PieChart getPieChart(String title, Map<String, Double> pieMap) {
        PieChart pieChart = new PieChart(getActivity());

        pieChart.getDescription().setEnabled(true);
        pieChart.setRotationEnabled(true);
        pieChart.setDragDecelerationFrictionCoef(0.9f);
        pieChart.setRotationAngle(270);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.setHoleColor(Color.parseColor("#000000"));

        ArrayList<PieEntry> pieEntries = new ArrayList<>();

        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#304567"));
        colors.add(Color.parseColor("#309967"));
        colors.add(Color.parseColor("#476567"));
        colors.add(Color.parseColor("#890567"));
        colors.add(Color.parseColor("#a35567"));
        colors.add(Color.parseColor("#ff5f67"));
        colors.add(Color.parseColor("#3ca567"));

        for (String type : pieMap.keySet()) {
            pieEntries.add(new PieEntry(Objects.requireNonNull(pieMap.get(type)).floatValue(), type));
        }
        PieDataSet pieDataSet = new PieDataSet(pieEntries, "");
        pieDataSet.setValueTextSize(12f);
        pieDataSet.setColors(colors);
        PieData pieData = new PieData(pieDataSet);

        pieData.setDrawValues(false);
        pieData.setValueFormatter(new PercentFormatter(pieChart));
        pieChart.setData(pieData);
        pieChart.setUsePercentValues(true);
        pieChart.setDrawEntryLabels(pieMap.size() <= 24);

        Legend legend = pieChart.getLegend();
//        int color = Color.getColor("?android:textColorPrimary");
        int color = 0;
        if (!(null == getContext()))
            color = ContextCompat.getColor(getContext(), R.color.colorPrimaryDark);
        legend.setTextColor(color);
        legend.setEnabled(false);

        pieChart.getDescription().setText(title);
        pieChart.getDescription().setTextSize(12f);
        pieChart.getDescription().setTextColor(color);
        pieChart.invalidate();
        pieChart.setMinimumHeight(600);
        return pieChart;
    }

    private void buildLineChart() {
        mLineChart.clear();

        mLineChart.getLegend().setTextSize(17f);
        mLineChart.getLegend().setForm(Legend.LegendForm.CIRCLE);

        mLineChart.getAxisLeft().setTextColor(DEEP_TEAL); // left y-axis
        mLineChart.getAxisRight().setTextColor(DEEP_TEAL); // right y-axis
        mLineChart.getXAxis().setEnabled(false);//setTextColor(DEEP_TEAL);
        mLineChart.getLegend().setTextColor(DEEP_TEAL);
        mLineChart.getDescription().setTextColor(DEEP_TEAL);

        mLineChart.getAxisLeft().setAxisMinimum(0F);
        mLineChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                value = ((int) (value * 100.0)) / 100.0f;
                return (((value == (long)value) ? String.format(Locale.UK,"%d", (long)value) : String.format("%s", value)) + " kWh");
            }
        });
        mLineChart.getAxisRight().setAxisMinimum(0F);
        mLineChart.getAxisRight().setDrawLabels(false);
        mLineChart.getAxisLeft().setSpaceTop(5F);
        mLineChart.getAxisRight().setSpaceTop(5F);

        ArrayList<Entry> loadEntries = new ArrayList<>();
        ArrayList<Entry> feedEntries = new ArrayList<>();
        ArrayList<Entry> buyEntries = new ArrayList<>();
        ArrayList<Entry> pvEntries = new ArrayList<>();

        ArrayList<Entry> pv2batEntries = new ArrayList<>();
        ArrayList<Entry> pv2loadEntries = new ArrayList<>();
        ArrayList<Entry> bat2loadEntries = new ArrayList<>();
        ArrayList<Entry> grid2batEntries = new ArrayList<>();
        ArrayList<Entry> evScheduleEntries = new ArrayList<>();
        ArrayList<Entry> evDivertEntries = new ArrayList<>();
        ArrayList<Entry> hwScheduleEntries = new ArrayList<>();
        ArrayList<Entry> hwDivertEntries = new ArrayList<>();
        for (IntervalRow intervalRow : mGraphData) {
            loadEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.load));
            feedEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.feed));
            buyEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.buy));
            pvEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.pv));

            pv2batEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.pv2bat));
            pv2loadEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.pv2load));
            bat2loadEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.bat2load));
            grid2batEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.grid2bat));
            evScheduleEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.evSchedule));
            evDivertEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.evDivert));
            hwScheduleEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.hwSchedule));
            hwDivertEntries.add(new Entry(Float.parseFloat(intervalRow.interval), (float) intervalRow.hwDivert));
        }

        LineDataSet loadSet = configureLine(loadEntries, Color.BLUE, "Load");
        LineDataSet feedSet = configureLine(feedEntries, Color.YELLOW, "Feed");
        LineDataSet buySet = configureLine(buyEntries, Color.CYAN, "Buy");
        LineDataSet pvSet = configureLine(pvEntries, Color.RED, "PV");

        LineDataSet pv2batSet = configureLine(pv2batEntries, Color.GREEN, "PV2Bat");
        LineDataSet pv2loadSet = configureLine(pv2loadEntries, Color.GRAY, "PV2Load");
        LineDataSet bat2loadSet = configureLine(bat2loadEntries, DEEP_TEAL, "Bat2Load");
        LineDataSet grid2batSet = configureLine(grid2batEntries, Color.MAGENTA, "Grid2Bat");
        LineDataSet evScheduleSet = configureLine(evScheduleEntries, ORANGE, "EVSchedule");
        LineDataSet evDivertSet = configureLine(evDivertEntries, PALE_YELLOW_GREEN, "EVDivert");
        LineDataSet hwScheduleSet = configureLine(hwScheduleEntries, PURPLE, "HWSchedule");
        LineDataSet hwDivertSet = configureLine(hwDivertEntries, BROWN, "HWDivert");

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        if (mShowLoad) dataSets.add(loadSet);
        if (mShowFeed) dataSets.add(feedSet);
        if (mShowBuy) dataSets.add(buySet);
        if (mShowPV) dataSets.add(pvSet);

        if (mShowPv2bat) dataSets.add(pv2batSet);
        if (mShowPv2load) dataSets.add(pv2loadSet);
        if (mShowBat2load) dataSets.add(bat2loadSet);
        if (mShowGrid2Bat) dataSets.add(grid2batSet);
        if (mShowEVSchedule) dataSets.add(evScheduleSet);
        if (mShowEVDivert) dataSets.add(evDivertSet);
        if (mShowHWSchedule) dataSets.add(hwScheduleSet);
        if (mShowHWDivert) dataSets.add(hwDivertSet);

        LineData data = new LineData(dataSets);
        mLineChart.setData(data);

        mLineChart.getDescription().setEnabled(false);

        mLineChart.invalidate();
        mLineChart.refreshDrawableState();
    }

    private LineDataSet configureLine(ArrayList<Entry> loadEntries, int color, String label) {
        int lcolor = 0;
        if (!(null == getContext()))
            lcolor = ContextCompat.getColor(getContext(), R.color.colorPrimaryDark);

        LineDataSet lineDataSet;
        lineDataSet = new LineDataSet(loadEntries, label);
        lineDataSet.setDrawIcons(false);
        lineDataSet.enableDashedLine(10f, 5f, 0f);
        lineDataSet.enableDashedHighlightLine(10f, 5f, 0f);
        lineDataSet.setColor(color);
        lineDataSet.setCircleColor(color);
        lineDataSet.setLineWidth(1f);
        lineDataSet.setCircleRadius(3f);
        lineDataSet.setDrawCircleHole(false);
        lineDataSet.setValueTextSize(9f);
        lineDataSet.setValueTextColor(lcolor);
        lineDataSet.setDrawFilled(false);
        lineDataSet.setFormLineWidth(1f);
        lineDataSet.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
        lineDataSet.setFormSize(15.f);
        lineDataSet.setFillColor(color);
        lineDataSet.setAxisDependency(mLineChart.getAxisLeft().getAxisDependency());
        return lineDataSet;
    }

    private void buildBarChart() {
        mBarChart.clear();

        XAxis xAxis = mBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);

        mBarChart.getLegend().setTextSize(17f);
        mBarChart.getLegend().setForm(Legend.LegendForm.CIRCLE);

        switch (mDisplayInterval) {
            case WEEK:
                final String[] xLabels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",};
                final ArrayList<String> xLabel = new ArrayList<>(Arrays.asList(xLabels));
                xAxis.setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        if ((int) value >= xLabel.size()) return "??";
                        else return xLabel.get((int) value);
                    }
                });
                break;
            case MNTH:
            case YEAR:
                xAxis.setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        if (!(null == mGraphData)) {
                            if ((int) value >= mGraphData.size()) return "??";
                            else return mGraphData.get((int) value).interval;
                        } else return "??";
                    }
                });
                break;
            case HOUR:
            case DOY:
                xAxis.setValueFormatter(null);
        }

        mBarChart.getAxisLeft().setTextColor(DEEP_TEAL); // left y-axis
        mBarChart.getAxisRight().setDrawLabels(false); //.setTextColor(DEEP_TEAL); // right y-axis
        mBarChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                value = ((int) (value * 100.0)) / 100.0f;
                return (((value == (long)value) ?
                        String.format(Locale.UK, "%d", (long)value) : String.format("%s", value)) + " kWh");
            }
        });
        mBarChart.getXAxis().setTextColor(DEEP_TEAL);
        mBarChart.getLegend().setTextColor(DEEP_TEAL);
        mBarChart.getDescription().setTextColor(DEEP_TEAL);

        ArrayList<BarEntry> loadEntries = new ArrayList<>();
        ArrayList<BarEntry> feedEntries = new ArrayList<>();
        ArrayList<BarEntry> buyEntries = new ArrayList<>();
        ArrayList<BarEntry> pvEntries = new ArrayList<>();

        ArrayList<BarEntry> pv2batEntries = new ArrayList<>();
        ArrayList<BarEntry> pv2loadEntries = new ArrayList<>();
        ArrayList<BarEntry> bat2loadEntries = new ArrayList<>();
        ArrayList<BarEntry> grid2batEntries = new ArrayList<>();
        ArrayList<BarEntry> evScheduleEntries = new ArrayList<>();
        ArrayList<BarEntry> evDivertEntries = new ArrayList<>();
        ArrayList<BarEntry> hwScheduleEntries = new ArrayList<>();
        ArrayList<BarEntry> hwDivertEntries = new ArrayList<>();

        for (int i = 0; i < mGraphData.size(); i++) {
            loadEntries.add(new BarEntry(i, (float) mGraphData.get(i).load));
            feedEntries.add(new BarEntry(i, (float) mGraphData.get(i).feed));
            buyEntries.add(new BarEntry(i, (float) mGraphData.get(i).buy));
            pvEntries.add(new BarEntry(i, (float) mGraphData.get(i).pv));

            pv2batEntries.add(new BarEntry(i, (float) mGraphData.get(i).pv2bat));
            pv2loadEntries.add(new BarEntry(i, (float) mGraphData.get(i).pv2load));
            bat2loadEntries.add(new BarEntry(i, (float) mGraphData.get(i).bat2load));
            grid2batEntries.add(new BarEntry(i, (float) mGraphData.get(i).grid2bat));
            evScheduleEntries.add(new BarEntry(i, (float) mGraphData.get(i).evSchedule));
            evDivertEntries.add(new BarEntry(i, (float) mGraphData.get(i).evDivert));
            hwScheduleEntries.add(new BarEntry(i, (float) mGraphData.get(i).hwSchedule));
            hwDivertEntries.add(new BarEntry(i, (float) mGraphData.get(i).hwDivert));
        }

        BarDataSet loadSet = new BarDataSet(loadEntries, "Load");
        loadSet.setColor(Color.BLUE);
        BarDataSet feedSet = new BarDataSet(feedEntries, "Feed");
        feedSet.setColor(Color.YELLOW);
        BarDataSet buySet = new BarDataSet(buyEntries, "Buy");
        buySet.setColor(Color.CYAN);
        BarDataSet pvSet = new BarDataSet(pvEntries, "PV");
        pvSet.setColor(Color.RED);

        BarDataSet pv2batSet = new BarDataSet(pv2batEntries, "PV2Bat");
        pv2batSet.setColor(Color.GREEN);
        BarDataSet pv2loadSet = new BarDataSet(pv2loadEntries, "PV2Load");
        pv2loadSet.setColor(Color.GRAY);
        BarDataSet bat2loadSet = new BarDataSet(bat2loadEntries, "Bat2Load");
        bat2loadSet.setColor(DEEP_TEAL);
        BarDataSet grid2batSet = new BarDataSet(grid2batEntries, "Grid2Bat");
        grid2batSet.setColor(Color.MAGENTA);
        BarDataSet evScheduleSet = new BarDataSet(evScheduleEntries, "EVSchedule");
        evScheduleSet.setColor(ORANGE);
        BarDataSet evDivertSet = new BarDataSet(evDivertEntries, "EVDivert");
        evDivertSet.setColor(PALE_YELLOW_GREEN);
        BarDataSet hwScheduleSet = new BarDataSet(hwScheduleEntries, "HWSchedule");
        hwScheduleSet.setColor(PURPLE);
        BarDataSet hwDivertSet = new BarDataSet(hwDivertEntries, "HWDivert");
        hwDivertSet.setColor(BROWN);

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        if (mShowLoad) dataSets.add(loadSet);
        if (mShowFeed) dataSets.add(feedSet);
        if (mShowBuy) dataSets.add(buySet);
        if (mShowPV) dataSets.add(pvSet);

        if (mShowPv2bat) dataSets.add(pv2batSet);
        if (mShowPv2load) dataSets.add(pv2loadSet);
        if (mShowBat2load) dataSets.add(bat2loadSet);
        if (mShowGrid2Bat) dataSets.add(grid2batSet);
        if (mShowEVSchedule) dataSets.add(evScheduleSet);
        if (mShowEVDivert) dataSets.add(evDivertSet);
        if (mShowHWSchedule) dataSets.add(hwScheduleSet);
        if (mShowHWDivert) dataSets.add(hwDivertSet);

        BarData data = new BarData(dataSets);
        data.setValueTextSize(10f);
        data.setDrawValues(false);
        mBarChart.getDescription().setEnabled(false);
        mBarChart.setData(data);

        if (mFilterCount > 1) {
            //data
            float groupSpace = 0.00f;
            float barSpace; // x2 dataset
            float barWidth; // x2 dataset
            // (0.46 + 0.02) * 2 + 0.04 = 1.00 -> interval per "group"
            // (0.22 + 0.02) * 4 + 0.05
            // (barWidth + barSpace) * elementsInGroup + groupSpace = 1

            float section = 0.96f / (float) mFilterCount;
            barSpace = section - section / (float) mFilterCount;
            barWidth = section - barSpace;

            data.setBarWidth(barWidth);
            mBarChart.groupBars(-0.5F, groupSpace, barSpace);
        }

        mBarChart.invalidate();
        mBarChart.refreshDrawableState();
    }

    public void setSelectedSystemSN(String serialNumber) {
        mSystemSN = serialNumber;
        if (!(null == mSystemSN) && !(null == mInverterDateRangesBySN) && !(null == mInverterDateRangesBySN.get(mSystemSN))) {
            mFrom = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).first;
            mTo = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).second;
        }
        setSelectionText();
        updateKPIs();
    }
}
