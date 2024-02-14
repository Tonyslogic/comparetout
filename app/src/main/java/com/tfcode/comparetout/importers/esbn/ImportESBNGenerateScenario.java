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

package com.tfcode.comparetout.importers.esbn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.ImportSystemSelection;
import com.tfcode.comparetout.model.importers.InverterDateRange;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ImportESBNGenerateScenario extends Fragment {

    private String mSystemSN;
    private Handler mMainHandler;

    private Map<String, Pair<String, String >> mInverterDateRangesBySN;

    private String mFrom;
    private String mTo;
    private String mDBLast;
    private boolean mDatesOK = false;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter PARSER_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MIDNIGHT = " 00:00:00";

    private MaterialButton mGenerateUsage;
    private TextView mGenSelectedDates;
    private TextView mGenStatus;

    private static final String LP = "LP";
    private static final String SYSTEM = "SYSTEM";
    private static final String FROM = "FROM";
    private static final String TO = "TO";
    private static final String OK_DATES = "OK_DATES";

    private boolean mLP = true;

    public ImportESBNGenerateScenario() {
        // Required empty public constructor
    }

    public static ImportESBNGenerateScenario newInstance() {
        return new ImportESBNGenerateScenario();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(LP, mLP);
        outState.putString(SYSTEM, mSystemSN);
        outState.putString(FROM, mFrom);
        outState.putString(TO, mTo);
        outState.putBoolean(OK_DATES, mDatesOK);
    }
    @SuppressLint("InflateParams")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mLP = savedInstanceState.getBoolean(LP);
            mSystemSN = savedInstanceState.getString(SYSTEM);
            mFrom = savedInstanceState.getString(FROM);
            mTo = savedInstanceState.getString(TO);
            mDatesOK = savedInstanceState.getBoolean(OK_DATES);
        }
        if (null == mSystemSN)
            mSystemSN = ((ImportSystemSelection) requireActivity()).getSelectedSystemSN();
        ComparisonUIViewModel mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);

        mViewModel.getLiveDateRanges(ComparisonUIViewModel.Importer.ESBNHDF).observe(this, dateRanges -> {
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_import_esbn_generate_scenario, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMainHandler = new Handler(Looper.getMainLooper());

        MaterialButton mDateSelection = view.findViewById(R.id.gen_pick_range);
        mGenerateUsage = view.findViewById(R.id.gen_scenario);
        mGenSelectedDates = view.findViewById(R.id.gen_selected_dates);
        mGenStatus = view.findViewById(R.id.gen_selected_status);
        ImportESBNActivity esbnActivity = ((ImportESBNActivity)getActivity());
        if (!(null == esbnActivity) && (esbnActivity.getScenarioID() != 0)) {
            mGenerateUsage.setText(R.string.generate_profile);
        }

        mDateSelection.setOnClickListener(v -> {
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
                }
            });
            startPicker.show(getParentFragmentManager(), "FETCH_START_DATE_PICKER");
        });

        mGenerateUsage.setOnClickListener(v -> {

            Context context = getContext();
            if ((null == context) || (esbnActivity == null)) return;

            Data inputData = new Data.Builder()
                    .putString(GenerationWorker.KEY_SYSTEM_SN, mSystemSN)
                    .putBoolean(GenerationWorker.LP, mLP)
                    .putString(GenerationWorker.FROM, mFrom)
                    .putString(GenerationWorker.TO, mTo)
                    .putLong(GenerationWorker.SCENARIO_ID, esbnActivity.getScenarioID())
                    .putLong(GenerationWorker.LOAD_PROFILE_ID, esbnActivity.getLoadProfileID())
                    .build();
            OneTimeWorkRequest generationWorkRequest =
                    new OneTimeWorkRequest.Builder(GenerationWorker.class)
                            .setInputData(inputData)
                            .addTag(mSystemSN)
                            .build();

            WorkManager.getInstance(context).pruneWork();
            WorkManager
                    .getInstance(context)
                    .beginUniqueWork(mSystemSN, ExistingWorkPolicy.APPEND, generationWorkRequest)
                    .enqueue();

            // set up the observer for the selected systems workers
            LiveData<List<WorkInfo>> mCatchupLiveDataForSN = WorkManager.getInstance(context)
                    .getWorkInfosByTagLiveData(mSystemSN);
            Observer<List<WorkInfo>> mCatchupWorkObserver = workInfos -> {
                for (WorkInfo wi : workInfos) {
                    String mFetchState;
                    if ( (!(null == wi)) &&
                            ((wi.getState() == WorkInfo.State.RUNNING) ||
                                    (wi.getState() == WorkInfo.State.SUCCEEDED)) ) {
                        Data progress = wi.getProgress();
                        mFetchState = progress.getString(GenerationWorker.PROGRESS);
                        if (null == mFetchState) mFetchState = "Unknown state";
                        String finalMFetchState = mFetchState;
                        mMainHandler.post(() -> mGenStatus.setText(finalMFetchState));
                    }
                }
            };
            mCatchupLiveDataForSN.observe((LifecycleOwner) context, mCatchupWorkObserver);
        });
        mGenerateUsage.setEnabled(mLP && mDatesOK);
        setSelectionText();
    }

    @SuppressLint("SetTextI18n")
    private void setSelectionText() {
        if (!(null == mFrom) && !(null == mTo) && !(null == mGenSelectedDates))
            mGenSelectedDates.setText(mDatesOK ? mFrom + "<->" + mTo : "Out of range");
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
