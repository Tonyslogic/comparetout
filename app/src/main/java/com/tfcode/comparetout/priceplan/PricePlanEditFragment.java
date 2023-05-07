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

package com.tfcode.comparetout.priceplan;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PricePlanEditFragment extends Fragment {

    private Set<PricePlan> mPricePlans = new HashSet<>();
    private TableLayout mTableLayout;

    private boolean mEdit;
    private Long mPlanID;
    private String mFocus;
    private PricePlan mPricePlan;
    private List<DayRate> mDayRates;

    private List<View> mEditFields;

    private static final String PLAN_ID = "PLAN_ID";
    private static final String FOCUS = "FOCUS";
    private static final String EDIT = "EDIT";

    public PricePlanEditFragment() {
        // Required empty public constructor
    }

    public static PricePlanEditFragment newInstance() {
        PricePlanEditFragment fragment = new PricePlanEditFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EDIT, mEdit);
        outState.putLong(PLAN_ID, mPlanID);
        outState.putString(FOCUS, mFocus);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mEdit = savedInstanceState.getBoolean(EDIT);
            mFocus = savedInstanceState.getString(FOCUS);
            mPlanID = savedInstanceState.getLong(PLAN_ID);
            setOrResetState();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mEdit = savedInstanceState.getBoolean(EDIT);
            mFocus = savedInstanceState.getString(FOCUS);
            mPlanID = savedInstanceState.getLong(PLAN_ID);
        }
        else {
            mPlanID = ((PricePlanActivity) requireActivity()).getPlanID();
            mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
            mEdit = ((PricePlanActivity) requireActivity()).getEdit();
        }
        setOrResetState();
    }

    private void setOrResetState() {
        mEditFields = new ArrayList<>();
        System.out.println("Plan id = " + mPlanID);
        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        PricePlanJsonFile ppj = new Gson().fromJson(mFocus, type);
        mPricePlan = JsonTools.createPricePlan(ppj);
        System.out.println("Plan:" + mPricePlan.getPlanName());
        mDayRates = new ArrayList<>();
        for (DayRateJson drj : ppj.rates){
            DayRate dr = JsonTools.createDayRate(drj);
            mDayRates.add(dr);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_price_plan_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = requireView().findViewById(R.id.planEditTable);
        ComparisonUIViewModel mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getAllPricePlans().observe(getViewLifecycleOwner(), plans -> mPricePlans = plans.keySet());
        updateView();
        setupMenu();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity()))
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void setupMenu() {
//        MenuHost menuHost = ;
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
//                Don't inflate the menu, the activity already did this'
//                menuInflater.inflate(R.menu.menu_plans, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                System.out.println("PricePlanEditFragment.onOptionsItemSelected");
                if (menuItem.getItemId() == R.id.edit_a_plan) {//add the function to perform here
                    System.out.println("Edit attempt");
                    if (!mEdit) {
                        mEdit = true;
                        for (View v : mEditFields) v.setEnabled(true);
                        ((PricePlanActivity) requireActivity()).setEdit(true);
                    }
                    return false;
                }
                return true;
            }
        });
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        if (!(null == mEditFields)) for (View v : mEditFields) v.setEnabled(mEdit);
    }

    public void updateView() {
        System.out.println("Updating PricePlanEditFragment " + mEdit);
        mTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;

        TableRow.LayoutParams textParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
        textParams.topMargin = 2;
        textParams.rightMargin = 2;

        // CREATE TABLE ROWS
        TableRow tableRow = new TableRow(getActivity());
        TextView a = new TextView(getActivity());
        a.setText(R.string.Supplier);
        a.setMinimumHeight(80);
        a.setHeight(80);
        EditText b = new EditText(getActivity());
        b.setText(mPricePlan.getSupplier());
        b.setEnabled(mEdit);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("Plan supplier edit lost focus");
                mPricePlan.setSupplier(s.toString());
                PricePlanActivity ppa = ((PricePlanActivity)requireActivity());
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ppa.setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.Plan);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(mPricePlan.getPlanName());
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("Plan name edit lost focus");
                mPricePlan.setPlanName( s.toString());
                PricePlanActivity ppa = ((PricePlanActivity)requireActivity());
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                if (!(mPricePlans == null)) {
                    int nameOK = mPricePlan.checkNameUsageIn(mPricePlans);
                    if (nameOK != PricePlan.VALID_PLAN) ppa.setPlanValidity(nameOK);
                    else {
                        int validity = mPricePlan.validatePlan(mDayRates);
                        ppa.setPlanValidity(validity);
                    }
                }
                else System.out.println("mPricePlans is null ==> need another way to get the list of plans");
                ppa.setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.FeedInRate);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(String.format("%s", mPricePlan.getFeed()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("Feed edit lost focus");
                mPricePlan.setFeed( getDoubleOrZero(s));
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ((PricePlanActivity)requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.StandingCharges);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(String.format("%s", mPricePlan.getStandingCharges()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("Standing charged edit lost focus");
                mPricePlan.setStandingCharges( getDoubleOrZero(s));
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ((PricePlanActivity)requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.SignUpBonus);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(String.format("%s", mPricePlan.getSignUpBonus()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("Bonus edit lost focus");
                mPricePlan.setSignUpBonus(getDoubleOrZero(s));
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ((PricePlanActivity)requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.LastUpdate);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(String.format("%s", mPricePlan.getLastUpdate()));
        b.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("Last update edit lost focus");
                mPricePlan.setLastUpdate(s.toString());
                PricePlanActivity ppa = ((PricePlanActivity)requireActivity());
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ppa.setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        a = new TextView(getActivity());
        a.setText(R.string.Reference);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(mPricePlan.getReference());
        b.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("Reference edit lost focus");
                mPricePlan.setReference(s.toString());
                PricePlanActivity ppa = ((PricePlanActivity)requireActivity());
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ppa.setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);
    }
}