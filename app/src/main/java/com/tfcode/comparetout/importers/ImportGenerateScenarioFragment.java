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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.importers.InverterDateRange;
import com.tfcode.comparetout.util.AbstractTextWatcher;
import com.tfcode.comparetout.util.GraphableActivity;

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
import java.util.stream.Collectors;

public abstract class ImportGenerateScenarioFragment extends Fragment {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PARSER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MIDNIGHT = " 00:00:00";
    private static final String LP = "LP";
    private static final String INV = "INV";
    private static final String PAN = "PAN";
    private static final String PAN_D = "PAN_D";
    private static final String BAT = "BAT";
    private static final String BAT_SCH = "BAT_SCH";
    private static final String SYSTEM = "SYSTEM";
    private static final String FROM = "FROM";
    private static final String TO = "TO";
    protected static final String PANEL_COUNTS = "PANEL_COUNTS";
    private static final String OK_DATES = "OK_DATES";
    protected ComparisonUIViewModel.Importer mImporterType;
    protected String mSystemSN;
    protected Handler mMainHandler;
    private Map<String, Pair<String, String>> mInverterDateRangesBySN;
    protected String mFrom;
    protected String mTo;
    private String mDBLast;
    private boolean mDatesOK = false;
    private MaterialButton mGenerateUsage;
    private TextView mGenSelectedDates;
    protected TextView mGenStatus;
    private MaterialCheckBox mGenLoadProfile;
    private MaterialCheckBox mGenInverter;
    private TableLayout mGenInverterInput;
    private MaterialCheckBox mGenPanels;
    private TableLayout mGenPanelInput;
    private MaterialCheckBox mGenPanelData;
    private MaterialCheckBox mGenBattery;
    protected MaterialCheckBox mGenBatterySchedule;
    protected boolean mLP;
    protected boolean mINV;
    protected boolean mPNL;
    protected boolean mPNLD;
    protected boolean mBAT;
    protected boolean mBATS;
    protected int mMPPTCountValue = 2;
    protected List<Integer> mStringPanelCount = new ArrayList<>();
    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    public ImportGenerateScenarioFragment() {
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(LP, mLP);
        outState.putBoolean(INV, mINV);
        outState.putBoolean(PAN, mPNL);
        outState.putBoolean(PAN_D, mPNLD);
        outState.putBoolean(BAT, mBAT);
        outState.putBoolean(BAT_SCH, mBATS);
        outState.putString(SYSTEM, mSystemSN);
        outState.putString(FROM, mFrom);
        outState.putString(TO, mTo);
        outState.putBoolean(OK_DATES, mDatesOK);
        String serializedPanelCounts = mStringPanelCount.stream()
                .map(Object::toString)
                .reduce("", (str, num) -> str.isEmpty() ? num : str + "," + num);
        outState.putString(PANEL_COUNTS, serializedPanelCounts);
    }

    @SuppressLint("InflateParams")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mLP = savedInstanceState.getBoolean(LP);
            mINV = savedInstanceState.getBoolean(INV);
            mPNL = savedInstanceState.getBoolean(PAN);
            mPNLD = savedInstanceState.getBoolean(PAN_D);
            mBAT = savedInstanceState.getBoolean(BAT);
            mBATS = savedInstanceState.getBoolean(BAT_SCH);
            mSystemSN = savedInstanceState.getString(SYSTEM);
            mFrom = savedInstanceState.getString(FROM);
            mTo = savedInstanceState.getString(TO);
            mDatesOK = savedInstanceState.getBoolean(OK_DATES);
            mStringPanelCount = Arrays.stream(savedInstanceState.getString(PANEL_COUNTS).split(","))
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        }
        if (null == mSystemSN)
            mSystemSN = ((GraphableActivity) requireActivity()).getSelectedSystemSN();
        ComparisonUIViewModel mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);

        mViewModel.getLiveDateRanges(mImporterType).observe(this, dateRanges -> {
            if (null == mInverterDateRangesBySN) mInverterDateRangesBySN = new HashMap<>();
            for (InverterDateRange inverterDateRange : dateRanges) {
                mInverterDateRangesBySN.put(inverterDateRange.sysSn, new Pair<>(inverterDateRange.startDate, inverterDateRange.finishDate));
            }
            if (!(null == mSystemSN) && !(null == mInverterDateRangesBySN.get(mSystemSN))) {
                if (null == savedInstanceState) {
                    mFrom = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).first;
                    mTo = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).second;
                    mDBLast = mTo;
                    mMainHandler.post(this::setSelectionText);
                }
            }
        });

        // HELP
        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(requireContext()))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(requireContext()))
                .build();
        LayoutInflater inflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_import_alpha_scenario_geeration, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMainHandler = new Handler(Looper.getMainLooper());

        MaterialButton mDateSelection = view.findViewById(R.id.gen_pick_range);
        mGenerateUsage = view.findViewById(R.id.gen_scenario);
        mGenSelectedDates = view.findViewById(R.id.gen_selected_dates);
        mGenStatus = view.findViewById(R.id.gen_selected_status);
        mGenLoadProfile = view.findViewById(R.id.gen_lp);
        mGenInverter = view.findViewById(R.id.gen_inv);
        mGenInverterInput = view.findViewById(R.id.gen_inv_detail);
        EditText mMPPTCount = view.findViewById(R.id.gen_mppt_count);
        mGenPanels = view.findViewById(R.id.gen_panels);
        mGenPanelInput = view.findViewById(R.id.gen_panel_detail);
        mGenPanelData = view.findViewById(R.id.gen_panel_data);
        mGenBattery = view.findViewById(R.id.gen_battery);
        mGenBatterySchedule = view.findViewById(R.id.gen_battery_schedule);

        mDateSelection.setOnClickListener(v -> {
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

            MaterialDatePicker<Long> startPicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select generation start")
                    .setCalendarConstraints(calendarConstraintsBuilder.build())
                    .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                    .build();
            startPicker.addOnPositiveButtonClickListener(startDateAsLong -> {
                LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startDateAsLong), ZoneId.systemDefault());
                mFrom = ldt.format(DATE_FORMAT);
                mTo = ldt.plusYears(1).plusDays(-1).format(DATE_FORMAT);
                LocalDate ldtTo = LocalDate.parse(mTo);
                LocalDate ldtLast = LocalDate.parse((null == mDBLast) ? "1970-01-01" : mDBLast);
                mDatesOK = (ldtTo.isBefore(ldtLast.plusDays(1)));
                setSelectionText();
                {
                    mGenerateUsage.setEnabled(mLP && mDatesOK);
                    mGenLoadProfile.setEnabled(mDatesOK);
                    mGenInverter.setEnabled(mLP && mDatesOK);
                    mGenPanels.setEnabled(mINV && mDatesOK);
                    mGenPanelData.setEnabled(mPNL && mDatesOK);
                    mGenBattery.setEnabled(mINV && mDatesOK);
                    mGenBatterySchedule.setEnabled(mBAT && mDatesOK);
                }
            });
            startPicker.show(getParentFragmentManager(), "FETCH_START_DATE_PICKER");
        });

        mGenerateUsage.setOnClickListener(v -> generateUsage());
        mGenerateUsage.setEnabled(mLP && mDatesOK);
        setSelectionText();

        mGenLoadProfile.setChecked(mLP);
        mGenLoadProfile.setEnabled(mDatesOK);
        mGenLoadProfile.setOnClickListener(v -> {
            mLP = mGenLoadProfile.isChecked();
            mGenInverter.setEnabled(mLP);
            mGenerateUsage.setEnabled(mLP);
        });

        mGenInverter.setChecked(mINV);
        mGenInverter.setEnabled(mLP && mDatesOK);
        mGenInverter.setOnClickListener(v -> {
            mINV = mGenInverter.isChecked();
            mGenPanels.setEnabled(mINV && mDatesOK);
            mGenBattery.setEnabled(mINV && mDatesOK);
            mGenInverterInput.setVisibility(mINV ? View.VISIBLE : View.GONE);
        });

        mGenInverterInput.setVisibility(mINV ? View.VISIBLE : View.GONE);

        mMPPTCount.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!(s.toString().equals(String.valueOf(mMPPTCountValue)))) {
                    mMPPTCountValue = getIntegerOrZero(s);
                    updateView();
                }
            }
        });

        mGenPanels.setChecked(mPNL);
        mGenPanels.setEnabled(mINV && mDatesOK);
        mGenPanels.setOnClickListener(v -> {
            mPNL = mGenPanels.isChecked();
            mGenPanelData.setEnabled(mPNL && mDatesOK);
            mGenPanelInput.setVisibility(mPNL ? View.VISIBLE : View.GONE);
        });

        mGenPanelInput.setVisibility(mPNL ? View.VISIBLE : View.GONE);

        mGenPanelData.setChecked(mPNLD);
        mGenPanelData.setEnabled(mPNL && mDatesOK);
        mGenPanelData.setOnClickListener(v -> mPNLD = mGenPanelData.isChecked());

        mGenBattery.setChecked(mBAT);
        mGenBattery.setEnabled(mINV && mDatesOK);
        mGenBattery.setOnClickListener(v -> {
            mBAT = mGenBattery.isChecked();
            mGenBatterySchedule.setEnabled(mBAT && mDatesOK);
        });

        mGenBatterySchedule.setChecked(mBATS);
        mGenBatterySchedule.setEnabled(mBAT && mDatesOK);
        mGenBatterySchedule.setOnClickListener(v -> mBATS = mGenBatterySchedule.isChecked());
        updateView();
    }

    abstract protected void generateUsage();

    @SuppressLint("SetTextI18n")
    private void setSelectionText() {
        if (!(null == mFrom) && !(null == mTo) && !(null == mGenSelectedDates))
            mGenSelectedDates.setText(mDatesOK ? mFrom + "<->" + mTo : "Out of range");
    }

    private void updateView() {
        if ((null == mGenPanelInput) || (null == getActivity())) return;
        mGenPanelInput.removeAllViews();
        while (mStringPanelCount.size() < mMPPTCountValue) mStringPanelCount.add(7);
        for (int i = 0; i < mMPPTCountValue; i++) {
            TableRow stringRow = new TableRow(getActivity());
            TextView stringPrompt = new TextView(getActivity());
            EditText stringValue = new EditText(getActivity());

            stringPrompt.setText(getString(R.string.string_panel_count, String.valueOf(i + 1)));
            int finalI = i;
            stringValue.setText(String.valueOf(mStringPanelCount.get(i)));
            stringValue.setInputType(InputType.TYPE_CLASS_NUMBER);
            stringValue.addTextChangedListener(new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mStringPanelCount.get(finalI))))) {
                        mStringPanelCount.set(finalI, getIntegerOrZero(s));
                    }
                }
            });

            stringRow.addView(stringPrompt);
            stringRow.addView(stringValue);
            mGenPanelInput.addView(stringRow);
        }
    }

    public void setSelectedSystemSN(String serialNumber) {
        mSystemSN = serialNumber;
        if (!(null == mSystemSN) && !(null == mInverterDateRangesBySN) && !(null == mInverterDateRangesBySN.get(mSystemSN))) {
            mFrom = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).first;
            mTo = Objects.requireNonNull(mInverterDateRangesBySN.get(mSystemSN)).second;
            mDBLast = mTo;
        }
        setSelectionText();
    }
}
