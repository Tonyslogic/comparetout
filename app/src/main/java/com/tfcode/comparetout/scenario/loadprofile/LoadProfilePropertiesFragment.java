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

package com.tfcode.comparetout.scenario.loadprofile;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.LoadProfileJson;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoadProfilePropertiesFragment extends Fragment {
    private boolean mEdit = false;
    private List<View> mEditFields;
    private boolean mSaved = false;

    private ComparisonUIViewModel mViewModel;
    private TableLayout mTableLayout;
    private Long mScenarioID;
    private LoadProfile mLoadProfile;
    private Long mLoadProfileID = 0L;

    final ActivityResultLauncher<String> mLoadLoadProfileFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // Handle the returned Uri
                if (uri == null) return;
                InputStream is;
                try {
                    is = requireActivity().getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<LoadProfileJson>() {}.getType();
                    LoadProfileJson lpj  = new Gson().fromJson(reader, type);
                    mLoadProfile = JsonTools.createLoadProfile(lpj);
                    mLoadProfile.setLoadProfileIndex(mLoadProfileID);
                    updateView();
                    updateMasterCopy();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            });

    public LoadProfilePropertiesFragment() {
        // Required empty public constructor
    }

    public static LoadProfilePropertiesFragment newInstance() {
        return new LoadProfilePropertiesFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScenarioID = ((LoadProfileActivity) requireActivity()).getScenarioID();
        mEdit = ((LoadProfileActivity) requireActivity()).getEdit();
        mEditFields = new ArrayList<>();
        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getLoadProfile(mScenarioID).observe(this, profile -> {
            if (!(null == profile)) {
                mLoadProfile = profile;
                checkForDataAndGenerateIfNeeded();
            }
            else mLoadProfile = new LoadProfile();
            LoadProfileJson lpj = JsonTools.createLoadProfileJson(mLoadProfile);
            Type type = new TypeToken<LoadProfileJson>(){}.getType();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String loadProfileJsonString =  gson.toJson(lpj, type);
            ((LoadProfileActivity) requireActivity()).setLoadProfileJson(loadProfileJsonString);
            mSaved = false;
            if (!mLoadProfileID.equals(mLoadProfile.getLoadProfileIndex())) {
                mLoadProfileID = mLoadProfile.getLoadProfileIndex();
            }
            updateView();
        });
    }

    private void checkForDataAndGenerateIfNeeded() {
        Data.Builder builder = new Data.Builder();
        builder.putLong("LoadProfileID", mLoadProfile.getLoadProfileIndex());
        builder.putBoolean("DeleteFirst", mSaved );
        WorkRequest genLPDataWorkRequest =
                new OneTimeWorkRequest.Builder(DeleteLoadDataFromProfileWorker.class)
                        .setInputData(builder.build())
                        .build();
        if (!(null == getContext()))
            WorkManager
                .getInstance(getContext())
                .enqueue(genLPDataWorkRequest);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_load_profile_properties, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupMenu();
        mTableLayout = requireView().findViewById(R.id.loadProfileDetails);
        if (!(null == mLoadProfile)) updateView();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity()))
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void setupMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {}

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.lp_save) {
                    String loadProfileJsonString = ((LoadProfileActivity) requireActivity()).getLoadProfileJson();
                    Type type = new TypeToken<LoadProfileJson>(){}.getType();
                    LoadProfileJson lpj = new Gson().fromJson(loadProfileJsonString, type);
                    LoadProfile loadProfile = JsonTools.createLoadProfile(lpj);
                    mLoadProfile.setDowDist(loadProfile.getDowDist());
                    mLoadProfile.setHourlyDist(loadProfile.getHourlyDist());
                    mLoadProfile.setMonthlyDist(loadProfile.getMonthlyDist());

                    mViewModel.saveLoadProfile(mScenarioID, mLoadProfile);
                    mSaved = true;
                    ((LoadProfileActivity) requireActivity()).setSaveNeeded(false);
                    return (false);
                }

                if (menuItem.getItemId() == R.id.lp_import) {//add the function to perform here
                    mLoadLoadProfileFile.launch("*/*");

                    return true;
                }
                return true;
            }
        });
    }

    private void updateView() {
        System.out.println("Updating LoadProfilePropertiesFragment " + mEdit);
        mTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;

        // CREATE TABLE ROWS
        TableRow tableRow = new TableRow(getActivity());
        TextView a = new TextView(getActivity());
        a.setText(R.string.DistributionSource);
        a.setMinimumHeight(80);
        a.setHeight(80);
        Spinner spinner = new Spinner(getActivity());
        ArrayList<String> spinnerContent = new ArrayList<>();
        spinnerContent.add("Custom");
        spinnerContent.addAll(Arrays.asList(StandardLoadProfiles.standardLoadProfiles));
//        {"SLP 24hr Urban", "SLP 24hr Rural", "SLP Night saver Urban", "SLP Night saver Rural", "SLP Smart Urban", "SLP Smart Rural"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, spinnerContent);
        spinner.setAdapter(spinnerAdapter);
        spinner.setEnabled(mEdit);
        int index = spinnerContent.indexOf(mLoadProfile.getDistributionSource());
        spinner.setSelection(index);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String loadProfileJsonString = null;
                switch (position) {
                    case 0: break;
                    case 1: loadProfileJsonString = StandardLoadProfiles.SLP_24hr_urban; break;
                    case 2: loadProfileJsonString = StandardLoadProfiles.SLP_24hr_rural; break;
                    case 3: loadProfileJsonString = StandardLoadProfiles.SLP_NightSaver_urban; break;
                    case 4: loadProfileJsonString = StandardLoadProfiles.SLP_NightSaver_rural; break;
                    case 5: loadProfileJsonString = StandardLoadProfiles.SLP_Smart_urban; break;
                    case 6: loadProfileJsonString = StandardLoadProfiles.SLP_Smart_rural; break;
                }
                if (!(null == loadProfileJsonString)) {
                    Type type = new TypeToken<LoadProfileJson>() {}.getType();
                    LoadProfileJson lpj = new Gson().fromJson(loadProfileJsonString, type);
                    LoadProfile slProfile = JsonTools.createLoadProfile(lpj);
                    mLoadProfile.setHourlyDist(slProfile.getHourlyDist());
                    mLoadProfile.setDowDist(slProfile.getDowDist());
                    mLoadProfile.setMonthlyDist(slProfile.getMonthlyDist());
                    mLoadProfile.setDistributionSource(spinnerContent.get(position));
                    if (mEdit) updateMasterCopy();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });
        spinner.setPadding(20, 20, 20, 20);
        tableRow.addView(a);
        tableRow.addView(spinner);
        mTableLayout.addView(tableRow);
        mEditFields.add(spinner);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.AnnualUsage);
        a.setMinimumHeight(80);
        a.setHeight(80);
        EditText b = new EditText(getActivity());
        b.setText(String.format("%s", mLoadProfile.getAnnualUsage()));
        b.setEnabled(mEdit);
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mLoadProfile.setAnnualUsage(getDoubleOrZero(s));
                System.out.println("Annual usage changed to : " + mLoadProfile.getAnnualUsage());
                ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setPadding(20, 20, 20, 20);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.HourlyBaseLoad);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(String.format("%s", mLoadProfile.getHourlyBaseLoad()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    mLoadProfile.setHourlyBaseLoad(getDoubleOrZero(s));
                    System.out.println("Base load changed to : " + mLoadProfile.getHourlyBaseLoad());
                    ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                } catch (NumberFormatException nfe) {
                    nfe.printStackTrace();
                }
            }
        });
        a.setLayoutParams(planParams);
        b.setPadding(20, 20, 20, 20);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.GridImportMax);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(String.format("%s", mLoadProfile.getGridImportMax()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mLoadProfile.setGridImportMax(getDoubleOrZero(s));
                System.out.println("Max import changed to : " + mLoadProfile.getAnnualUsage());
                ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setPadding(20, 20, 20, 20);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.GridExportMax);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(String.format("%s", mLoadProfile.getGridExportMax()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mLoadProfile.setGridExportMax(getDoubleOrZero(s));
                System.out.println("Max export changed to : " + mLoadProfile.getAnnualUsage());
                if (mEdit) ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setPadding(20, 20, 20, 20);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        for (View v : mEditFields) {
            v.setEnabled(mEdit);
        }
    }

    private void updateMasterCopy() {
        Type type = new TypeToken<LoadProfileJson>(){}.getType();
        LoadProfileJson lpj = JsonTools.createLoadProfileJson(mLoadProfile);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String newLoadProfileJsonString =  gson.toJson(lpj, type);
        ((LoadProfileActivity) requireActivity()).setLoadProfileJson(newLoadProfileJsonString); //, mLoadProfile.getDistributionSource());
        ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
        ((LoadProfileActivity) requireActivity()).propagateDistribution();
    }
}

