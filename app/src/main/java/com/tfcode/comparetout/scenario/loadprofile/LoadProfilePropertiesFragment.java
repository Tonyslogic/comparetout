package com.tfcode.comparetout.scenario.loadprofile;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.json.scenario.LoadProfileJson;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.LoadProfile;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.priceplan.PricePlanActivity;
import com.tfcode.comparetout.scenario.ScenarioActivity;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LoadProfilePropertiesFragment extends Fragment {
    private boolean mEdit = false;
    private List<View> mEditFields;

    private PricePlanNavViewModel mViewModel;
    private TableLayout mTableLayout;
    private Long mScenarioID;
    private LoadProfile mLoadProfile;

    public LoadProfilePropertiesFragment() {
        // Required empty public constructor
    }

    public static LoadProfilePropertiesFragment newInstance() {
        LoadProfilePropertiesFragment fragment = new LoadProfilePropertiesFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScenarioID = ((LoadProfileActivity) requireActivity()).getScenarioID();
        mEdit = ((LoadProfileActivity) requireActivity()).getEdit();
        mEditFields = new ArrayList<>();
        mViewModel = new ViewModelProvider(requireActivity()).get(PricePlanNavViewModel.class);
        mViewModel.getLoadProfile(mScenarioID).observe(this, profile -> {
            if (!(null == profile)) {
                System.out.println("LPPF Observed a change in live profile data " + profile.getId());
                mLoadProfile = profile;
            }
            else mLoadProfile = new LoadProfile();
            LoadProfileJson lpj = JsonTools.createLoadProfileJson(mLoadProfile);
            Type type = new TypeToken<LoadProfileJson>(){}.getType();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String loadProfileJsonString =  gson.toJson(lpj, type);
            ((LoadProfileActivity) requireActivity()).setLoadProfileJson(loadProfileJsonString);
            updateView();
        });
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
                System.out.println("LPPF.onOptionsItemSelected");
                if (menuItem.getItemId() == R.id.lp_save) {//add the function to perform here
                    System.out.println("save attempt");

                    long id = mLoadProfile.getId();
                    String loadProfileJsonString = ((LoadProfileActivity) requireActivity()).getLoadProfileJson();
                    Type type = new TypeToken<LoadProfileJson>(){}.getType();
                    LoadProfileJson lpj = new Gson().fromJson(loadProfileJsonString, type);
                    mLoadProfile = JsonTools.createLoadProfile(lpj);
                    mLoadProfile.setId(id);

                    mViewModel.saveLoadProfile(mScenarioID, mLoadProfile);
                    ((LoadProfileActivity) requireActivity()).setSaveNeeded(false);
                    return (false);
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
        a.setText("Annual usage (kWh)");
        EditText b = new EditText(getActivity());
        b.setText("" + mLoadProfile.getAnnualUsage());
        b.setEnabled(mEdit);
        b.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new LocalTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mLoadProfile.setAnnualUsage(Double.parseDouble(s.toString()));
                System.out.println("Annual usage changed to : " + mLoadProfile.getAnnualUsage());
                ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(planParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText("Hourly base load (kWh)");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText("" + mLoadProfile.getHourlyBaseLoad());
        b.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new LocalTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mLoadProfile.setHourlyBaseLoad(Double.parseDouble(s.toString()));
                System.out.println("Base load changed to : " + mLoadProfile.getAnnualUsage());
                ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(planParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText("Grid import max (kWh)");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText("" + mLoadProfile.getGridImportMax());
        b.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new LocalTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mLoadProfile.setGridImportMax(Double.parseDouble(s.toString()));
                System.out.println("Max import changed to : " + mLoadProfile.getAnnualUsage());
                ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(planParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText("Grid export max (kWh)");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText("" + mLoadProfile.getGridExportMax());
        b.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new LocalTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mLoadProfile.setGridExportMax(Double.parseDouble(s.toString()));
                System.out.println("Max export changed to : " + mLoadProfile.getAnnualUsage());
                ((LoadProfileActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(planParams);
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
}

