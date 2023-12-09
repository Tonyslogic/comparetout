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
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.importers.alphaess.IntervalRow;
import com.tfcode.comparetout.model.importers.alphaess.InverterDateRange;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ImportAlphaGraphs extends Fragment {

    private String mSystemSN;
    private ComparisonUIViewModel mViewModel;
    private Handler mMainHandler;

    private Map<String, Pair<String, String >> mInverterDateRangesBySN;

    private IntervalType mInterval = IntervalType.HOUR;
    private CalculationType mCalculation = CalculationType.SUM;
    private ChartView mChartView = ChartView.BAR;

    private String mFrom;
    private String mTo;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PARSER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MIDNIGHT = " 00:00:00";

    private MaterialButton mIntervalButton;
    private MaterialButton mModeButton;
    private ImageButton mFilterButton;
    private PopupMenu mFilterPopup;

    private BarChart mBarChart;
    private LineChart mLineChart;
    private PieChart mPieChart;
    private TextView mPicks;
    private TextView mNoGraphDataTextView;

    private List<IntervalRow> mGraphData;

    private static final String SHOW_LOAD = "SHOW_LOAD";
    private static final String SHOW_FEED = "SHOW_FEED";
    private static final String SHOW_BUY = "SHOW_BUY";
    private static final String SHOW_PV = "SHOW_PV";
    private static final String SYSTEM = "SYSTEM";
    private static final String FROM = "FROM";
    private static final String TO = "TO";
    private static final String INTERVAL = "INTERVAL";
    private static final String CALCULATION = "CALCULATION";
    private static final String CHART = "CHART";

    private boolean mShowLoad = true;
    private boolean mShowFeed = true;
    private boolean mShowBuy = true;
    private boolean mShowPV = true;

    private int mFilterCount = 4;

    public ImportAlphaGraphs() {
        // Required empty public constructor
    }

    public static ImportAlphaGraphs newInstance() {
        return new ImportAlphaGraphs();
    }

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
        outState.putString(SYSTEM, mSystemSN);
        outState.putString(FROM, mFrom);
        outState.putString(TO, mTo);
        outState.putInt(INTERVAL, mInterval.ordinal());
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

            mFilterCount = 0;
            if (mShowLoad) mFilterCount++;
            if (mShowFeed) mFilterCount++;
            if (mShowBuy) mFilterCount++;
            if (mShowPV) mFilterCount++;

            mSystemSN = savedInstanceState.getString(SYSTEM);
            mFrom = savedInstanceState.getString(FROM);
            mTo = savedInstanceState.getString(TO);
            mInterval = IntervalType.values()[savedInstanceState.getInt(INTERVAL)];
            mCalculation = CalculationType.values()[savedInstanceState.getInt(CALCULATION)];
            mChartView = ChartView.values()[savedInstanceState.getInt(CHART)];
        }
        if (null == mSystemSN)
            mSystemSN = ((ImportAlphaActivity) requireActivity()).getSelectedSystemSN();

        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getLiveDateRanges().observe(this, dateRanges -> {
            if (null == mInverterDateRangesBySN) mInverterDateRangesBySN = new HashMap<>();
            for (InverterDateRange inverterDateRange : dateRanges) {
                mInverterDateRangesBySN.put(inverterDateRange.sysSn, new Pair<>(inverterDateRange.startDate, inverterDateRange.finishDate));
            }
            if (!(null == mSystemSN) && !(null == mInverterDateRangesBySN.get(mSystemSN))) {
                mFrom = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).first;
                mTo = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).second;
                mMainHandler.post(this::setSelectionText);
                mMainHandler.post(this::updateKPIs);
            }
        });
        updateKPIs();
    }

    private void updateKPIs() {
        new Thread(() -> {
            if (!(null == mViewModel)) {
                if (null == mFrom) mFrom = "1970-01-01";
                if (null == mTo) {
                    LocalDate now = LocalDate.now();
                    mTo = now.format(DATE_FORMAT);
                }
                if (null == mSystemSN) mSystemSN = ((ImportAlphaActivity) requireActivity()).getSelectedSystemSN();
                switch (mCalculation) {
                    case SUM:
                        switch (mInterval) {
                            case HOUR:
                                mGraphData = mViewModel.getSumHour(mSystemSN, mFrom, mTo);
                                break;
                            case DOY:
                                mGraphData = mViewModel.getSumDOY(mSystemSN, mFrom, mTo);
                                break;
                            case WEEK:
                                mGraphData = mViewModel.getSumDOW(mSystemSN, mFrom, mTo);
                                break;
                            case MNTH:
                                mGraphData = mViewModel.getSumMonth(mSystemSN, mFrom, mTo);
                                break;
                            case YEAR:
                                mGraphData = mViewModel.getSumYear(mSystemSN, mFrom, mTo);
                                break;
                        }
                        break;
                    case AVG:
                        switch (mInterval) {
                            case HOUR:
                                mGraphData = mViewModel.getAvgHour(mSystemSN, mFrom, mTo);
                                break;
                            case DOY:
                                mGraphData = mViewModel.getAvgDOY(mSystemSN, mFrom, mTo);
                                break;
                            case WEEK:
                                mGraphData = mViewModel.getAvgDOW(mSystemSN, mFrom, mTo);
                                break;
                            case MNTH:
                                mGraphData = mViewModel.getAvgMonth(mSystemSN, mFrom, mTo);
                                break;
                            case YEAR:
                                mGraphData = mViewModel.getAvgYear(mSystemSN, mFrom, mTo);
                                break;
                        }
                        break;
                }

                System.out.println("ImportAlphaGraphs::updateKPIs input: " + mSystemSN + ", " + mFrom + ", " + mTo);
                System.out.println("ImportAlphaGraphs::updateKPIs returned: " + mGraphData.size());
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
        mPieChart = view.findViewById((R.id.alphaess_pie_chart));

        mIntervalButton = view.findViewById((R.id.interval));
        mIntervalButton.setOnClickListener(v -> {
            mInterval = mInterval.next();
            mIntervalButton.setText(mInterval.toString());
            setSelectionText();
            updateKPIs();
        });

        mModeButton = view.findViewById(R.id.mode);
        mModeButton.setOnClickListener(v -> {
            mCalculation = mCalculation.next();
            setSelectionText();
            mModeButton.setText(mCalculation.toString());
            updateKPIs();
        });

        ImageButton mPreviousButton = view.findViewById((R.id.previous));
        mPreviousButton.setOnClickListener(v -> {
            LocalDateTime start = LocalDateTime.parse(mFrom + MIDNIGHT, PARSER_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(mTo + MIDNIGHT, PARSER_FORMATTER);
            switch (mInterval) {
                case DOY:
                case HOUR:
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
                case MNTH:
                    // + 1 month
                    start = start.plusMonths(-1);
                    mFrom = start.format(DATE_FORMAT);
                    end = end.plusMonths(-1);
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
            if (!(null == mSystemSN) && !(null == mInverterDateRangesBySN) && !(null == mInverterDateRangesBySN.get(mSystemSN)) ) {
                String first = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).first;
                String second = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).second;
                System.out.println("Range available: " + first + ", " + second);
                LocalDateTime last = LocalDateTime.parse(second  + MIDNIGHT, PARSER_FORMATTER);
                ZonedDateTime lastz = last.atZone(ZoneId.systemDefault());
                calendarConstraintsBuilder.setEnd(lastz.toInstant().toEpochMilli());
                LocalDateTime start = LocalDateTime.parse(first  + MIDNIGHT, PARSER_FORMATTER);
                ZonedDateTime startz = start.atZone(ZoneId.systemDefault());
                calendarConstraintsBuilder.setStart(startz.toInstant().toEpochMilli());
            }
            else System.out.println("No range available");

            MaterialDatePicker.Builder<Pair<Long, Long>> materialDateBuilder = MaterialDatePicker.Builder
                    .dateRangePicker()
                    .setCalendarConstraints(calendarConstraintsBuilder.build());
            final MaterialDatePicker<Pair<Long, Long>> materialDatePicker = materialDateBuilder.build();
            materialDatePicker.show(getParentFragmentManager(), "MATERIAL_DATE_PICKER");

            materialDatePicker.addOnPositiveButtonClickListener(selection -> {
                mFrom = LocalDateTime.ofInstant(Instant.ofEpochMilli(selection.first), ZoneId.systemDefault()).format(DATE_FORMAT);
                mTo = LocalDateTime.ofInstant(Instant.ofEpochMilli(selection.second), ZoneId.systemDefault()).format(DATE_FORMAT);
                setSelectionText();
            });
            updateKPIs();
        });

        ImageButton mNextButton = view.findViewById(R.id.next);
        mNextButton.setOnClickListener(v -> {
            LocalDateTime start = LocalDateTime.parse(mFrom + MIDNIGHT, PARSER_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(mTo + MIDNIGHT, PARSER_FORMATTER);
            switch (mInterval) {
                case DOY:
                case HOUR:
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
                case MNTH:
                    // + 1 month
                    start = start.plusMonths(1);
                    mFrom = start.format(DATE_FORMAT);
                    end = end.plusMonths(1);
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
        mChartTypeButton.setOnClickListener(v -> {
            mChartView = mChartView.next();
            setSelectionText();
            updateKPIs();
        });

        mFilterButton = view.findViewById(R.id.filter);
        setupPopupFilterMenu();
    }

    @SuppressLint("SetTextI18n")
    private void setSelectionText() {
        mPicks.setText("Range: " + mFrom + "<->" + mTo);
    }

    private void setupPopupFilterMenu() {
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
            mFilterPopup.getMenu().findItem(R.id.hwSchedule).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.soc).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.hwTemp).setVisible(false);
            mFilterPopup.getMenu().findItem(R.id.hwDivert).setVisible(false);
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

            updateKPIs();
            // Keep the popup menu open
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            item.setActionView(new View(getActivity()));
            item.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return false;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    return false;
                }
            });
            return false;
        });

        mFilterButton.setOnClickListener(v -> mFilterPopup.show());
    }

    private void updateView() {
        boolean showText = false;
        if (!(null == mGraphData) && !mGraphData.isEmpty()) {
            switch (mChartView) {
                case BAR:
                    mBarChart.setVisibility(View.VISIBLE);
                    mLineChart.setVisibility(View.INVISIBLE);
                    mPieChart.setVisibility(View.INVISIBLE);
                    mNoGraphDataTextView.setVisibility(View.INVISIBLE);
                    buildBarChart();
                    break;
                case LINE:
                    mBarChart.setVisibility(View.INVISIBLE);
                    mLineChart.setVisibility(View.VISIBLE);
                    mPieChart.setVisibility(View.INVISIBLE);
                    mNoGraphDataTextView.setVisibility(View.INVISIBLE);
                    buildLineChart();
                    break;
                case PIE:
                    mBarChart.setVisibility(View.INVISIBLE);
                    mLineChart.setVisibility(View.INVISIBLE);
                    mPieChart.setVisibility(View.VISIBLE);
                    mNoGraphDataTextView.setVisibility(View.INVISIBLE);
                    break;
            }
        }
        else showText = true;

        if (showText || (null == mSystemSN)) {
            if (mBarChart != null) {
                mBarChart.setVisibility(View.INVISIBLE);
            }
            if (mLineChart != null) {
                mLineChart.setVisibility(View.INVISIBLE);
            }
            if (mPieChart != null) {
                mPieChart.setVisibility(View.INVISIBLE);
            }
            mNoGraphDataTextView.setVisibility(View.VISIBLE);
            mNoGraphDataTextView.setText(R.string.no_data_available);
        }
    }

    private void buildLineChart() {
        mLineChart.clear();

        mLineChart.getAxisLeft().setTextColor(Color.DKGRAY); // left y-axis
        mLineChart.getAxisRight().setTextColor(Color.DKGRAY); // right y-axis
        mLineChart.getXAxis().setEnabled(false);//setTextColor(Color.DKGRAY);
        mLineChart.getLegend().setTextColor(Color.DKGRAY);
        mLineChart.getDescription().setTextColor(Color.DKGRAY);

        mLineChart.getAxisLeft().setAxisMinimum(0F);
        mLineChart.getAxisRight().setAxisMinimum(0F);
        mLineChart.getAxisLeft().setSpaceTop(5F);
        mLineChart.getAxisRight().setSpaceTop(5F);

        ArrayList<Entry> loadEntries = new ArrayList<>();
        ArrayList<Entry> feedEntries = new ArrayList<>();
        ArrayList<Entry> buyEntries = new ArrayList<>();
        ArrayList<Entry> pvEntries = new ArrayList<>();
        for (IntervalRow intervalRow : mGraphData) {
            loadEntries.add(new Entry(intervalRow.interval, (float) intervalRow.load));
            feedEntries.add(new Entry(intervalRow.interval, (float) intervalRow.feed));
            buyEntries.add(new Entry(intervalRow.interval, (float) intervalRow.buy));
            pvEntries.add(new Entry(intervalRow.interval, (float) intervalRow.pv));
        }

        LineDataSet loadSet = configureLine(loadEntries, Color.BLUE, "Load");
        LineDataSet feedSet = configureLine(feedEntries, Color.YELLOW, "Feed");
        LineDataSet buySet = configureLine(buyEntries, Color.DKGRAY, "Buy");
        LineDataSet pvSet = configureLine(pvEntries, Color.RED, "PV");

        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        if (mShowLoad) dataSets.add(loadSet);
        if (mShowFeed) dataSets.add(feedSet);
        if (mShowBuy) dataSets.add(buySet);
        if (mShowPV) dataSets.add(pvSet);

        LineData data = new LineData(dataSets);
        mLineChart.setData(data);

        mLineChart.getDescription().setEnabled(false);

        mLineChart.invalidate();
        mLineChart.refreshDrawableState();
    }

    private LineDataSet configureLine(ArrayList<Entry> loadEntries, int color, String label) {
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
        lineDataSet.setDrawFilled(false);
        lineDataSet.setFormLineWidth(1f);
        lineDataSet.setFormLineDashEffect(new DashPathEffect(new float[]{10f, 5f}, 0f));
        lineDataSet.setFormSize(15.f);
        lineDataSet.setFillColor(color);
        lineDataSet.setAxisDependency(mLineChart.getAxisLeft().getAxisDependency());
        return lineDataSet;
    }

    private void buildBarChart() {
        System.out.println("ImportAlphaGraphs::buildBarChart");
        mBarChart.clear();

        XAxis xAxis = mBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);

        switch (mInterval) {
            case WEEK:
                final String[] xLabels = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",};
                final ArrayList<String> xLabel = new ArrayList<>(Arrays.asList(xLabels));
                xAxis.setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        if ((int)value > xLabel.size()) return "??";
                        else return xLabel.get((int) value);
                    }
                });
                break;
            case MNTH:
            case YEAR:
                xAxis.setValueFormatter(new ValueFormatter() {
                    @Override
                    public String getFormattedValue(float value) {
                        if ((int)value > mGraphData.size()) return "??";
                        else return Integer.toString(mGraphData.get((int) value).interval);
                    }
                });
                break;
            case HOUR:
            case DOY:
                xAxis.setValueFormatter(null);
        }

        mBarChart.getAxisLeft().setTextColor(Color.DKGRAY); // left y-axis
        mBarChart.getAxisRight().setTextColor(Color.DKGRAY); // right y-axis
        mBarChart.getXAxis().setTextColor(Color.DKGRAY);
        mBarChart.getLegend().setTextColor(Color.DKGRAY);
        mBarChart.getDescription().setTextColor(Color.DKGRAY);

        ArrayList<BarEntry> loadEntries = new ArrayList<>();
        ArrayList<BarEntry> feedEntries = new ArrayList<>();
        ArrayList<BarEntry> buyEntries = new ArrayList<>();
        ArrayList<BarEntry> pvEntries = new ArrayList<>();
        for (int i = 0; i < mGraphData.size(); i++) {
            loadEntries.add(new BarEntry(i, (float) mGraphData.get(i).load));
            feedEntries.add(new BarEntry(i, (float) mGraphData.get(i).feed));
            buyEntries.add(new BarEntry(i, (float) mGraphData.get(i).buy));
            pvEntries.add(new BarEntry(i, (float) mGraphData.get(i).pv));
        }

        BarDataSet loadSet;
        BarDataSet feedSet;
        BarDataSet buySet;
        BarDataSet pvSet;

        loadSet = new BarDataSet(loadEntries, "Load");
        loadSet.setColor(Color.BLUE);
        feedSet = new BarDataSet(feedEntries, "Feed");
        feedSet.setColor(Color.DKGRAY);
        buySet = new BarDataSet(buyEntries, "Buy");
        buySet.setColor(Color.GREEN);
        pvSet = new BarDataSet(pvEntries, "PV");
        pvSet.setColor(Color.RED);

        ArrayList<IBarDataSet> dataSets = new ArrayList<>();
        if (mShowLoad) dataSets.add(loadSet);
        if (mShowFeed) dataSets.add(feedSet);
        if (mShowBuy) dataSets.add(buySet);
        if (mShowPV) dataSets.add(pvSet);

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
        if (!(null == mSystemSN) && !(null == mInverterDateRangesBySN.get(mSystemSN))) {
            mFrom = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).first;
            mTo = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).second;
        }
        setSelectionText();
        updateKPIs();
    }
}