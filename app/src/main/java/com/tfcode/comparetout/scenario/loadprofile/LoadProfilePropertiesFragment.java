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

package com.tfcode.comparetout.scenario.loadprofile;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;

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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.esbn.ImportESBNActivity;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.LoadProfileJson;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
                InputStream is = null;
                try {
                    is = requireActivity().getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<LoadProfileJson>() {}.getType();
                    LoadProfileJson lpj  = new Gson().fromJson(reader, type);
                    mLoadProfile = JsonTools.createLoadProfile(lpj);
                    mLoadProfile.setLoadProfileIndex(mLoadProfileID);
                    updateView();
                    updateMasterCopy();
                    ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }finally {
                    if (!(null == is)) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
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
                updateMasterCopy();
                checkForDataAndGenerateIfNeeded();
            }
            else {
                String loadProfileJsonString = ((LoadProfileActivity) requireActivity()).getLoadProfileJson();
                Type type = new TypeToken<LoadProfileJson>(){}.getType();
                LoadProfileJson lpj = new Gson().fromJson(loadProfileJsonString, type);
                mLoadProfile = JsonTools.createLoadProfile(lpj);
            }
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
                return false;
            }
        });
    }

    private void updateView() {
        mTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;

        if (!(null == getActivity())) {

            // CREATE TABLE ROWS
            TableRow tableRow = new TableRow(getActivity());
            MaterialTextView a = new MaterialTextView(getActivity());
            a.setText(R.string.DistributionSource);
            a.setPadding(10, 25, 0, 25);
            MaterialButton source = new MaterialButton(getActivity());
            source.setText(mLoadProfile.getDistributionSource());
            source.setEnabled(mEdit);
            source.setOnClickListener(v -> {
                SourceDialog sourceDialog = new SourceDialog(getActivity(), newSource -> {
                    switch (newSource) {
                        case SourceDialog.CUSTOM:
                            LoadProfile lLoadProfile = new LoadProfile();
                            mLoadProfile.setHourlyDist(lLoadProfile.getHourlyDist());
                            mLoadProfile.setDowDist(lLoadProfile.getDowDist());
                            mLoadProfile.setMonthlyDist(lLoadProfile.getMonthlyDist());
                            mLoadProfile.setDistributionSource("Custom");
                            if (mEdit) updateMasterCopy();
                            break;
                        case SourceDialog.SLP:
                            StandardLoadProfileDialog slpDialog = new StandardLoadProfileDialog(getActivity(), newSLP -> {
                                String loadProfileJsonString = null;
                                switch (newSLP) {
                                    case StandardLoadProfiles.URBAN_24:
                                        loadProfileJsonString = StandardLoadProfiles.SLP_24hr_urban;
                                        break;
                                    case StandardLoadProfiles.RURAL_24:
                                        loadProfileJsonString = StandardLoadProfiles.SLP_24hr_rural;
                                        break;
                                    case StandardLoadProfiles.URBAN_NIGHT:
                                        loadProfileJsonString = StandardLoadProfiles.SLP_NightSaver_urban;
                                        break;
                                    case StandardLoadProfiles.RURAL_NIGHT:
                                        loadProfileJsonString = StandardLoadProfiles.SLP_NightSaver_rural;
                                        break;
                                    case StandardLoadProfiles.URBAN_SMART:
                                        loadProfileJsonString = StandardLoadProfiles.SLP_Smart_urban;
                                        break;
                                    case StandardLoadProfiles.RURAL_SMART:
                                        loadProfileJsonString = StandardLoadProfiles.SLP_Smart_rural;
                                        break;
                                }
                                if (!(null == loadProfileJsonString)) {
                                    Type type = new TypeToken<LoadProfileJson>() {
                                    }.getType();
                                    LoadProfileJson lpj = new Gson().fromJson(loadProfileJsonString, type);
                                    LoadProfile slProfile = JsonTools.createLoadProfile(lpj);
                                    mLoadProfile.setHourlyDist(slProfile.getHourlyDist());
                                    mLoadProfile.setDowDist(slProfile.getDowDist());
                                    mLoadProfile.setMonthlyDist(slProfile.getMonthlyDist());
                                    mLoadProfile.setDistributionSource(newSLP);
                                    if (mEdit) updateMasterCopy();
                                }
                            });
                            slpDialog.show();
                            break;
                        case SourceDialog.HDF:
                            Intent intent = new Intent(getActivity(), ImportESBNActivity.class);
                            intent.putExtra("LoadProfileID", mLoadProfileID);
                            intent.putExtra("ScenarioID", mScenarioID);
                            startActivity(intent);
                            break;
                    }
                });
                sourceDialog.show();
            });
            tableRow.addView(a);
            tableRow.addView(source);
            mEditFields.add(source);
            mTableLayout.addView(tableRow);

            tableRow = new TableRow(getActivity());
            a = new MaterialTextView(getActivity());
            a.setText(R.string.AnnualUsage);
            a.setPadding(10, 25, 0, 25);
            EditText b = new EditText(getActivity());
            b.setText(String.format("%s", mLoadProfile.getAnnualUsage()));
            b.setEnabled(mEdit);
            b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            b.addTextChangedListener(new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    mLoadProfile.setAnnualUsage(getDoubleOrZero(s));
                    ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                }
            });
            b.setPadding(0, 25, 0, 25);
            tableRow.addView(a);
            tableRow.addView(b);
            mTableLayout.addView(tableRow);
            mEditFields.add(b);

            tableRow = new TableRow(getActivity());
            a = new MaterialTextView(getActivity());
            a.setText(R.string.HourlyBaseLoad);
            a.setPadding(10, 25, 0, 25);
            b = new EditText(getActivity());
            b.setEnabled(mEdit);
            b.setText(String.format("%s", mLoadProfile.getHourlyBaseLoad()));
            b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            b.addTextChangedListener(new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    try {
                        mLoadProfile.setHourlyBaseLoad(getDoubleOrZero(s));
                        ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                    } catch (NumberFormatException nfe) {
                        nfe.printStackTrace();
                    }
                }
            });
            b.setPadding(0, 25, 0, 25);
            tableRow.addView(a);
            tableRow.addView(b);
            mTableLayout.addView(tableRow);
            mEditFields.add(b);

            tableRow = new TableRow(getActivity());
            a = new MaterialTextView(getActivity());
            a.setText(R.string.GridImportMax);
            a.setPadding(10, 25, 0, 25);
            b = new EditText(getActivity());
            b.setEnabled(mEdit);
            b.setText(String.format("%s", mLoadProfile.getGridImportMax()));
            b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            b.addTextChangedListener(new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    mLoadProfile.setGridImportMax(getDoubleOrZero(s));
                    ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                }
            });
            b.setPadding(0, 25, 0, 25);
            tableRow.addView(a);
            tableRow.addView(b);
            mTableLayout.addView(tableRow);
            mEditFields.add(b);

            tableRow = new TableRow(getActivity());
            a = new MaterialTextView(getActivity());
            a.setText(R.string.GridExportMax);
            a.setPadding(10, 25, 0, 25);
            b = new EditText(getActivity());
            b.setEnabled(mEdit);
            b.setText(String.format("%s", mLoadProfile.getGridExportMax()));
            b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            b.addTextChangedListener(new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    mLoadProfile.setGridExportMax(getDoubleOrZero(s));
                    if (mEdit) ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
                }
            });
            b.setPadding(0, 25, 0, 25);
            tableRow.addView(a);
            tableRow.addView(b);
            mTableLayout.addView(tableRow);
            mEditFields.add(b);
        }
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
//        ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
        ((LoadProfileActivity) requireActivity()).propagateDistribution();
    }
}

