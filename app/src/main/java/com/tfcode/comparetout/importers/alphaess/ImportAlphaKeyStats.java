/*
 * Copyright (c) 2023-2024. Tony Finnerty
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
import android.content.res.Configuration;
import android.graphics.Typeface;
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
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.ImportActivity;
import com.tfcode.comparetout.importers.ImportSystemSelection;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.model.importers.alphaess.KPIRow;
import com.tfcode.comparetout.model.importers.alphaess.KeyStatsRow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ImportAlphaKeyStats extends Fragment {

    private String mSystemSN;
    private ComparisonUIViewModel mViewModel;
    private Handler mMainHandler;

    private Map<String, Pair<String, String >> mInverterDateRangesBySN;

    private StepIntervalType mStepInterval = StepIntervalType.DAY;

    private String mFrom;
    private String mTo;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PARSER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MIDNIGHT = " 00:00:00";

    private ImageButton mIntervalButton;
    private PopupMenu mGraphConfigPopup;
    private ImageButton mFilterButton;
    private PopupMenu mFilterPopup;
    private int mMonthFilterSelection = 0;

    private TableLayout mKeyStatsTable;
    private TextView mPicks;
    private TextView mNoGraphDataTextView;

    private List<KeyStatsRow> mKeyStats;
    private KPIRow mKPIs;

    private static final String SYSTEM = "SYSTEM";
    private static final String FROM = "FROM";
    private static final String TO = "TO";
    private static final String STEP_INTERVAL = "STEP_INTERVAL";
    private static final String MONTH_FILTER = "MONTH_FILTER";

    public ImportAlphaKeyStats() {
        // Required empty public constructor
    }

    public static ImportAlphaKeyStats newInstance() {return new ImportAlphaKeyStats();}

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SYSTEM, mSystemSN);
        outState.putString(FROM, mFrom);
        outState.putString(TO, mTo);
        outState.putInt(STEP_INTERVAL, mStepInterval.ordinal());
        outState.putInt(MONTH_FILTER, mMonthFilterSelection);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mSystemSN = savedInstanceState.getString(SYSTEM);
            mFrom = savedInstanceState.getString(FROM);
            mTo = savedInstanceState.getString(TO);
            setSelectionText();
            mStepInterval = StepIntervalType.values()[savedInstanceState.getInt(STEP_INTERVAL)];
            mMonthFilterSelection = savedInstanceState.getInt(MONTH_FILTER);
        }
        if (null == mSystemSN)
            mSystemSN = ((ImportSystemSelection) requireActivity()).getSelectedSystemSN();

        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getLiveDateRanges(ComparisonUIViewModel.Importer.ALPHAESS).observe(this, dateRanges -> {
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
    }

    private void updateKPIs() {
        new Thread(() -> {
            mKeyStats = null;
            mKPIs = null;
            if (!(null == mViewModel)) {
                if (null == mFrom) mFrom = "1970-01-01";
                if (null == mTo) {
                    LocalDate now = LocalDate.now();
                    mTo = now.format(DATE_FORMAT);
                }
                if (null == mSystemSN) mSystemSN = ((ImportSystemSelection) requireActivity()).getSelectedSystemSN();
                // TODO: DB Query
                mKeyStats = mViewModel.getKeyStats(ComparisonUIViewModel.Importer.ALPHAESS, mFrom, mTo, mSystemSN);
                mKPIs = mViewModel.getKPIs(ComparisonUIViewModel.Importer.ALPHAESS, mFrom, mTo, mSystemSN);
                if (!(null == mMainHandler)) mMainHandler.post(this::updateView);
            }
        }).start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_import_alpha_key_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMainHandler = new Handler(Looper.getMainLooper());
        mPicks = view.findViewById((R.id.picks));
        setSelectionText();

        mNoGraphDataTextView = view.findViewById((R.id.alphaess_no_data));
        mKeyStatsTable = view.findViewById((R.id.alphaess_key_stats));

        mIntervalButton = view.findViewById((R.id.interval));
        setupPopupGraphConfigurationMenu();

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
                    }
                    else end = end.plusMonths(-1);
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
                    }
                    else end = end.plusMonths(1);
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

        mFilterButton = view.findViewById(R.id.filter);
        setupPopupFilterMenu();
    }

    @SuppressLint("SetTextI18n")
    private void setSelectionText() {
        if (!(null == mFrom) && !(null == mTo) && !(null == mPicks) && !(null == getView())) {
            mPicks.setText("Range: " + mFrom + "<->" + mTo);
            getView().findViewById((R.id.interval)).setEnabled(true);
            getView().findViewById((R.id.previous)).setEnabled(true);
            getView().findViewById(R.id.date).setEnabled(true);
            getView().findViewById(R.id.next).setEnabled(true);
            getView().findViewById(R.id.filter).setEnabled(true);
        }
        else {
            if (!(null == getView())) {
                getView().findViewById((R.id.interval)).setEnabled(false);
                getView().findViewById((R.id.previous)).setEnabled(false);
                getView().findViewById(R.id.date).setEnabled(false);
                getView().findViewById(R.id.next).setEnabled(false);
                getView().findViewById(R.id.filter).setEnabled(false);
            }
        }
    }

    private void setupPopupGraphConfigurationMenu() {
        if (null == mGraphConfigPopup) {
            mGraphConfigPopup = new PopupMenu(requireActivity(), mIntervalButton, Gravity.CENTER_HORIZONTAL);
            mGraphConfigPopup.getMenuInflater().inflate(R.menu.popup_menu_graph, mGraphConfigPopup.getMenu());
            mGraphConfigPopup.getMenu().findItem(R.id.display_scale).setVisible(false);
            mGraphConfigPopup.getMenu().findItem(R.id.display_hour).setVisible(false);
            mGraphConfigPopup.getMenu().findItem(R.id.display_week).setVisible(false);
            mGraphConfigPopup.getMenu().findItem(R.id.display_doy).setVisible(false);
            mGraphConfigPopup.getMenu().findItem(R.id.display_month).setVisible(false);
            mGraphConfigPopup.getMenu().findItem(R.id.display_year).setVisible(false);

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
        }
        mIntervalButton.setOnClickListener(v -> mGraphConfigPopup.show());
    }

    private void setupPopupFilterMenu() {
        if (null == mFilterPopup) {
            mFilterPopup = new PopupMenu(requireActivity(), mFilterButton, Gravity.CENTER_HORIZONTAL);
            mFilterPopup.getMenuInflater().inflate(R.menu.popup_menu_months, mFilterPopup.getMenu());
            switch (mMonthFilterSelection) {
                case 0: mFilterPopup.getMenu().findItem(R.id.all).setChecked(true); break;
                case 1: mFilterPopup.getMenu().findItem(R.id.jan).setChecked(true); break;
                case 2: mFilterPopup.getMenu().findItem(R.id.feb).setChecked(true); break;
                case 3: mFilterPopup.getMenu().findItem(R.id.mar).setChecked(true); break;
                case 4: mFilterPopup.getMenu().findItem(R.id.apr).setChecked(true); break;
                case 5: mFilterPopup.getMenu().findItem(R.id.may).setChecked(true); break;
                case 6: mFilterPopup.getMenu().findItem(R.id.jun).setChecked(true); break;
                case 7: mFilterPopup.getMenu().findItem(R.id.jul).setChecked(true); break;
                case 8: mFilterPopup.getMenu().findItem(R.id.aug).setChecked(true); break;
                case 9: mFilterPopup.getMenu().findItem(R.id.sep).setChecked(true); break;
                case 10: mFilterPopup.getMenu().findItem(R.id.oct).setChecked(true); break;
                case 11: mFilterPopup.getMenu().findItem(R.id.nov).setChecked(true); break;
                case 12: mFilterPopup.getMenu().findItem(R.id.dec).setChecked(true); break;
            }
        }
        mFilterPopup.setOnMenuItemClickListener(item -> {
            item.setChecked(!item.isChecked());
            int itemID = item.getItemId();

            if (itemID == R.id.all) mMonthFilterSelection = 0;
            if (itemID == R.id.jan) mMonthFilterSelection = 1;
            if (itemID == R.id.feb) mMonthFilterSelection = 2;
            if (itemID == R.id.mar) mMonthFilterSelection = 3;
            if (itemID == R.id.apr) mMonthFilterSelection = 4;
            if (itemID == R.id.may) mMonthFilterSelection = 5;
            if (itemID == R.id.jun) mMonthFilterSelection = 6;
            if (itemID == R.id.jul) mMonthFilterSelection = 7;
            if (itemID == R.id.aug) mMonthFilterSelection = 8;
            if (itemID == R.id.sep) mMonthFilterSelection = 9;
            if (itemID == R.id.oct) mMonthFilterSelection = 10;
            if (itemID == R.id.nov) mMonthFilterSelection = 11;
            if (itemID == R.id.dec) mMonthFilterSelection = 12;

            updateView();

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

    @SuppressLint("DefaultLocale")
    private void updateView() {
        if (null == getContext()) return;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            ImportActivity activity = (ImportActivity)getActivity();
            if (!(null == activity)) activity.hideFAB();
        }

        boolean showText = false;
        if (!(null == mKPIs) && !(null == mKeyStats)) {
            mKeyStatsTable.setVisibility(View.VISIBLE);
            mNoGraphDataTextView.setVisibility(View.INVISIBLE);
            mKeyStatsTable.removeAllViews();
            mKeyStatsTable.setShrinkAllColumns(false);
            mKeyStatsTable.setStretchAllColumns(true);
            mKeyStatsTable.setColumnShrinkable(0, true);
            mKeyStatsTable.setColumnStretchable(0, false);
            // Add the KPIS row
            createRow("Self consumption:", String.format("%.2f", mKPIs.selfConsumption) + "%",
                    "Self sufficiency:", String.format("%.2f", mKPIs.selfSufficiency) + "%", false);
            // Add the Key stats rows
            createRow("YY-MM", "PV Tot (kWh)", "Best (kWh)", "Worst (kWh)", true);
            for (KeyStatsRow keyStat : mKeyStats) {
                if ( (keyStat.month.endsWith(String.format("%02d", mMonthFilterSelection))) || mMonthFilterSelection == 0)
                    createRow(keyStat.month, keyStat.total, keyStat.best, keyStat.worst, false);
            }
        }
        else showText = true;

        if (showText || (null == mSystemSN)) {
            if (mKeyStatsTable != null) {
                mKeyStatsTable.setVisibility(View.INVISIBLE);
            }
            mNoGraphDataTextView.setVisibility(View.VISIBLE);
            mNoGraphDataTextView.setText(R.string.no_data_available);
        }
    }

    private void createRow(String col1, String col2, String col3, String col4, boolean title) {

        if (!(null == getActivity())) {
            TableRow tableRow;
            tableRow = new TableRow(getActivity());

            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            planParams.topMargin = 2;
            planParams.rightMargin = 2;

            TextView b = new TextView(getActivity());
            TextView c = new TextView(getActivity());
            TextView d = new TextView(getActivity());
            TextView e = new TextView(getActivity());

            if (title) {
                b.setTypeface(b.getTypeface(), Typeface.BOLD);
                c.setTypeface(c.getTypeface(), Typeface.BOLD);
                d.setTypeface(d.getTypeface(), Typeface.BOLD);
                e.setTypeface(e.getTypeface(), Typeface.BOLD);
            }

            // SET PARAMS
            b.setLayoutParams(planParams);
            c.setLayoutParams(planParams);
            d.setLayoutParams(planParams);
            e.setLayoutParams(planParams);

            // SET PADDING
            b.setPadding(10, 25, 10, 25);
            c.setPadding(10, 25, 10, 25);
            d.setPadding(10, 25, 10, 25);
            e.setPadding(10, 25, 10, 25);

            b.setText(col1);
            c.setText(col2);
            d.setText(col3);
            e.setText(col4);

            tableRow.addView(b);
            tableRow.addView(c);
            tableRow.addView(d);
            tableRow.addView(e);

            // ADD TABLEROW TO TABLELAYOUT
            mKeyStatsTable.addView(tableRow);
        }
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