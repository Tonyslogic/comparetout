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

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.priceplan.RangeRate;
import com.tfcode.comparetout.model.priceplan.Restriction;
import com.tfcode.comparetout.model.priceplan.Restrictions;
import com.tfcode.comparetout.util.AbstractTextWatcher;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PricePlanEditFragment extends Fragment {

    private Set<PricePlan> mPricePlans = new HashSet<>();
    private TableLayout mPropertiesTable;
    private TableLayout mRestrictionsTable;
    private MaterialCheckBox mRestrictionsApply;

    private boolean mEdit;
    private Long mPlanID;
    private String mFocus;
    private PricePlan mPricePlan;
    private List<DayRate> mDayRates;

    private List<View> mEditFields;

    private static final String PLAN_ID = "PLAN_ID";
    private static final String FOCUS = "FOCUS";
    private static final String EDIT = "EDIT";

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

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

        if (!(null == getContext())) {
            mAssetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(getContext()))
                    .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(getContext()))
                    .build();
        }
        setOrResetState();
    }

    private void setOrResetState() {
        mEditFields = new ArrayList<>();
        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        PricePlanJsonFile ppj = new Gson().fromJson(mFocus, type);
        mPricePlan = JsonTools.createPricePlan(ppj);
        mDayRates = new ArrayList<>();
        for (DayRateJson drj : ppj.rates){
            DayRate dr = JsonTools.createDayRate(drj);
            mDayRates.add(dr);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mPopupView = inflater.inflate(R.layout.popup_help, container);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_price_plan_edit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPropertiesTable = requireView().findViewById(R.id.planEditTable);
        mRestrictionsTable = requireView().findViewById(R.id.planRestrictionTable);
        mRestrictionsApply = requireView().findViewById(R.id.restrictionsApply);

        ComparisonUIViewModel mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
        mViewModel.getAllPricePlans().observe(getViewLifecycleOwner(), plans -> mPricePlans = plans.keySet());
        updateView();
        setupMenu();
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
                if (menuItem.getItemId() == R.id.edit_a_plan) {//add the function to perform here
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
        mPropertiesTable.removeAllViews();
        mPropertiesTable.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        mRestrictionsTable.removeAllViews();
        mRestrictionsTable.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;

        TableRow.LayoutParams textParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
        textParams.topMargin = 2;
        textParams.rightMargin = 2;

        if (!(null == getActivity())) {
            createPropertiesTableRows(planParams);
            createRestrictionsTableRows(planParams);
        }
    }

    private void createRestrictionsTableRows(TableRow.LayoutParams planParams) {
        Activity activity = getActivity();
        if ((activity == null)) throw new AssertionError();
        {
            // Active Restrictions
            if (!mEditFields.contains(mRestrictionsApply)) mEditFields.add(mRestrictionsApply);
            mRestrictionsApply.setEnabled(mEdit);
            mRestrictionsApply.setChecked(mPricePlan.getRestrictions().isActive());
            mRestrictionsApply.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mPricePlan.getRestrictions().setActive(isChecked);
                PricePlanActivity ppa = (PricePlanActivity) activity;
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ppa.setSaveNeeded(true);
            });
        }

        {
            // Title Row, and all costs
            TableRow tableRow = new TableRow(activity);
            MaterialTextView cost = new MaterialTextView(activity);
            MaterialTextView annually = new MaterialTextView(activity);
            MaterialTextView monthly = new MaterialTextView(activity);
            MaterialTextView bimonthly = new MaterialTextView(activity);
            MaterialTextView limit = new MaterialTextView(activity);
            MaterialTextView revised = new MaterialTextView(activity);

            cost.setText(R.string.cost);
            annually.setText(R.string.annually);
            monthly.setText(R.string.monthly);
            bimonthly.setText(R.string.bimonthly);
            limit.setText(R.string.limit);
            revised.setText(R.string.revised);

            tableRow.addView(cost);
            tableRow.addView(annually);
            tableRow.addView(monthly);
            tableRow.addView(bimonthly);
            tableRow.addView(limit);
            tableRow.addView(revised);

            cost.setLayoutParams(planParams);
            annually.setLayoutParams(planParams);
            monthly.setLayoutParams(planParams);
            bimonthly.setLayoutParams(planParams);
            limit.setLayoutParams(planParams);
            revised.setLayoutParams(planParams);

            mRestrictionsTable.addView(tableRow);
        }
        {
            // Restriction entries

            // Build a set of restrictions for this plan
            Set<Double> rates = new HashSet<>();
            for (DayRate dr : mDayRates) {
                ArrayList<RangeRate> x = dr.getMinuteRateRange().getRates();
                for (RangeRate r : x) {
                    rates.add(r.getPrice());
                }
            }
            List<Double> orderedRates = new ArrayList<>(rates);
            Collections.sort(orderedRates);

            for (Double r : orderedRates) {
                TableRow tableRow = new TableRow(activity);
                MaterialCheckBox cost = new MaterialCheckBox(activity);
                MaterialRadioButton annually = new MaterialRadioButton(activity);
                MaterialRadioButton monthly = new MaterialRadioButton(activity);
                MaterialRadioButton bimonthly = new MaterialRadioButton(activity);
                EditText limit = new EditText(activity);
                EditText revised = new EditText(activity);

                limit.setInputType(InputType.TYPE_CLASS_NUMBER );
                revised.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

                mEditFields.add(cost);
                mEditFields.add(annually);
                mEditFields.add(monthly);
                mEditFields.add(bimonthly);
                mEditFields.add(limit);
                mEditFields.add(revised);

                cost.setEnabled(mEdit);
                annually.setEnabled(mEdit);
                monthly.setEnabled(mEdit);
                bimonthly.setEnabled(mEdit);
                limit.setEnabled(mEdit);
                revised.setEnabled(mEdit);

                cost.setText(String.valueOf(r));
                Restriction restriction = mPricePlan.getRestrictions().getRestrictionForCost(String.valueOf(r));
                if (null == restriction) {
                    cost.setChecked(false);
                    annually.setChecked(false);
                    monthly.setChecked(false);
                    bimonthly.setChecked(false);
                    limit.setText("");
                    revised.setText("");
                }
                else {
                    cost.setChecked(true);
                    annually.setChecked(restriction.getPeriodicity() == Restriction.RestrictionType.annual);
                    monthly.setChecked(restriction.getPeriodicity() == Restriction.RestrictionType.monthly);
                    bimonthly.setChecked(restriction.getPeriodicity() == Restriction.RestrictionType.bimonthly);
                    Pair<Integer, Double> pair = restriction.getRestrictionForCost(String.valueOf(r));
                    limit.setText(String.valueOf(pair.first));
                    revised.setText(String.valueOf(pair.second));
                }

                cost.setOnClickListener(v -> {
                    if (annually.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.annual, limit.getText().toString(), revised.getText().toString());
                    if (monthly.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.monthly, limit.getText().toString(), revised.getText().toString());
                    if (bimonthly.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.bimonthly, limit.getText().toString(), revised.getText().toString());
                });
                annually.setOnClickListener(v -> {
                    annually.setChecked(true);
                    monthly.setChecked(false);
                    bimonthly.setChecked(false);
                    setRestriction(r, cost.isChecked(), Restriction.RestrictionType.annual, limit.getText().toString(), revised.getText().toString());
                });
                monthly.setOnClickListener(v -> {
                    monthly.setChecked(true);
                    annually.setChecked(false);
                    bimonthly.setChecked(false);
                    setRestriction(r, cost.isChecked(), Restriction.RestrictionType.monthly, limit.getText().toString(), revised.getText().toString());
                });
                bimonthly.setOnClickListener(v -> {
                    bimonthly.setChecked(true);
                    monthly.setChecked(false);
                    annually.setChecked(false);
                    setRestriction(r, cost.isChecked(), Restriction.RestrictionType.bimonthly, limit.getText().toString(), revised.getText().toString());
                });
                limit.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable editable) {
                        Integer limit = getIntegerOrZero(editable);
                        if (annually.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.annual, limit.toString(), revised.getText().toString());
                        if (monthly.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.monthly, limit.toString(), revised.getText().toString());
                        if (bimonthly.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.bimonthly, limit.toString(), revised.getText().toString());
                    }
                });
                revised.addTextChangedListener(new AbstractTextWatcher() {
                    @Override
                    public void afterTextChanged(Editable editable) {
                        Double revisedRate = getDoubleOrZero(editable);
                        if (annually.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.annual, limit.getText().toString(), revisedRate.toString());
                        if (monthly.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.monthly, limit.getText().toString(), revisedRate.toString());
                        if (bimonthly.isChecked()) setRestriction(r, cost.isChecked(), Restriction.RestrictionType.bimonthly, limit.getText().toString(), revisedRate.toString());
                    }
                });


                tableRow.addView(cost);
                tableRow.addView(annually);
                tableRow.addView(monthly);
                tableRow.addView(bimonthly);
                tableRow.addView(limit);
                tableRow.addView(revised);

                cost.setLayoutParams(planParams);
                annually.setLayoutParams(planParams);
                monthly.setLayoutParams(planParams);
                bimonthly.setLayoutParams(planParams);

                mRestrictionsTable.addView(tableRow);
            }
        }
    }

    private void setRestriction(Double rate, boolean rateChecked, Restriction.RestrictionType restrictionType, String limitString, String revisedRateString) {

        if ((null == restrictionType) || (null == rate) || (null == limitString) || (null == revisedRateString)) return;

        Restrictions restrictions = mPricePlan.getRestrictions();
        List<Restriction> restrictionsList = restrictions.getRestrictions();

        Restrictions newRestrictions = new Restrictions();
        newRestrictions.setActive(restrictions.isActive());
        List<Restriction> newRestrictionsList = new ArrayList<>();

        int limit  = 0;
        double revisedRate = 0D;
        try {
            limit = Integer.parseInt(limitString);
            revisedRate = Double.parseDouble(revisedRateString);
        }
        catch (NumberFormatException e) {
            // do nothing
        }

        Map<Restriction.RestrictionType, Restriction> restrictionMap = new HashMap<>();

        // Populate the new restrictions list with unmodified entries
        for (Restriction originalRestriction : restrictionsList) {
            Restriction.RestrictionType recurrence = originalRestriction.getPeriodicity();
            Map<String, Pair<Integer, Double>> sneaks = originalRestriction.getRestrictionEntries();
            for (Map.Entry<String, Pair<Integer, Double>> entry : sneaks.entrySet()) {
                if (!(entry.getKey().equals(String.valueOf(rate)))) {
                    Restriction newR = restrictionMap.getOrDefault(recurrence, new Restriction());
                    assert newR != null; // It will never be because of the getOrDefault
                    newR.addEntry(recurrence, entry.getKey(), entry.getValue().first, entry.getValue().second);
                    restrictionMap.put(recurrence, newR);
                }
            }
        }
        // Add the new entry if checked
        if (rateChecked) {
            Restriction newR = restrictionMap.getOrDefault(restrictionType, new Restriction());
            assert newR != null; // It will never be because of the getOrDefault
            newR.addEntry(restrictionType, String.valueOf(rate), limit, revisedRate);
            restrictionMap.put(restrictionType, newR);
        }

        // Build the newRestrictionsList from the restrictionMap
        for (Map.Entry<Restriction.RestrictionType, Restriction> entry : restrictionMap.entrySet()) {
            newRestrictionsList.add(entry.getValue());
        }

        // set/update the restrictions
        newRestrictions.setRestrictions(newRestrictionsList);
        mPricePlan.setRestrictions(newRestrictions);

        PricePlanActivity ppa = (PricePlanActivity) requireActivity();
        ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
        ppa.setSaveNeeded(true);
    }

    private void createPropertiesTableRows(TableRow.LayoutParams planParams) {
        
        if (null == getActivity()) return;
        
        // CREATE TABLE ROWS
        TableRow tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        MaterialTextView a = new MaterialTextView(getActivity());
        a.setText(R.string.Supplier);
        a.setMinimumHeight(80);
        a.setHeight(80);
        EditText b = new EditText(getActivity());
        b.setText(mPricePlan.getSupplier());
        b.setEnabled(mEdit);
        b.setPadding(0, 25, 0, 25);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mPricePlan.setSupplier(s.toString());
                PricePlanActivity ppa = ((PricePlanActivity) requireActivity());
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ppa.setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
//        b.setLayoutParams(planParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mPropertiesTable.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        a = new MaterialTextView(getActivity());
        a.setText(R.string.Plan);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setPadding(0, 25, 0, 25);
        b.setText(mPricePlan.getPlanName());
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mPricePlan.setPlanName(s.toString());
                PricePlanActivity ppa = ((PricePlanActivity) requireActivity());
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                if (!(mPricePlans == null)) {
                    int nameOK = mPricePlan.checkNameUsageIn(mPricePlans);
                    if (nameOK != PricePlan.VALID_PLAN) ppa.setPlanValidity(nameOK);
                    else {
                        int validity = mPricePlan.validatePlan(mDayRates);
                        ppa.setPlanValidity(validity);
                    }
                }
                ppa.setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
//        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mPropertiesTable.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        a = new MaterialTextView(getActivity());
        a.setText(R.string.FeedInRate);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setPadding(0, 25, 0, 25);
        b.setText(String.format("%s", mPricePlan.getFeed()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mPricePlan.setFeed(getDoubleOrZero(s));
                ((PricePlanActivity) requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ((PricePlanActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
//        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mPropertiesTable.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        a = new MaterialTextView(getActivity());
        a.setText(R.string.StandingCharges);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setPadding(0, 25, 0, 25);
        b.setText(String.format("%s", mPricePlan.getStandingCharges()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mPricePlan.setStandingCharges(getDoubleOrZero(s));
                ((PricePlanActivity) requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ((PricePlanActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
//        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mPropertiesTable.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        a = new MaterialTextView(getActivity());
        a.setText(R.string.SignUpBonus);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setPadding(0, 25, 0, 25);
        b.setText(String.format("%s", mPricePlan.getSignUpBonus()));
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mPricePlan.setSignUpBonus(getDoubleOrZero(s));
                ((PricePlanActivity) requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ((PricePlanActivity) requireActivity()).setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
//        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mPropertiesTable.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        a = new MaterialTextView(getActivity());
        a.setText(R.string.LastUpdate);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setPadding(0, 25, 0, 25);
        b.setText(String.format("%s", mPricePlan.getLastUpdate()));
        b.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mPricePlan.setLastUpdate(s.toString());
                PricePlanActivity ppa = ((PricePlanActivity) requireActivity());
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ppa.setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
//        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mPropertiesTable.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        a = new MaterialTextView(getActivity());
        a.setText(R.string.Reference);
        a.setMinimumHeight(80);
        a.setHeight(80);
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setPadding(0, 25, 0, 25);
        b.setText(mPricePlan.getReference());
        b.setSingleLine(false);
        b.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        b.setHorizontallyScrolling(false);
        b.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE);
        b.addTextChangedListener(new AbstractTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                mPricePlan.setReference(s.toString());
                PricePlanActivity ppa = ((PricePlanActivity) requireActivity());
                ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
                ppa.setSaveNeeded(true);
            }
        });
        a.setLayoutParams(planParams);
//        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mPropertiesTable.addView(tableRow);
        mEditFields.add(b);

        tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp();
            return true;
        });
        a = new MaterialTextView(getActivity());
        a.setText(R.string.deemed_export_calculation);
        a.setMinimumHeight(80);
        a.setHeight(80);
        MaterialCheckBox cb = getMaterialCheckBoxForDeemedExport();
        a.setLayoutParams(planParams);
//        b.setLayoutParams(textParams);
        tableRow.addView(a);
        tableRow.addView(cb);
        mPropertiesTable.addView(tableRow);
        mEditFields.add(cb);
    }

    @NonNull
    private MaterialCheckBox getMaterialCheckBoxForDeemedExport() {
        if (null == getActivity()) throw new AssertionError();
        MaterialCheckBox cb = new MaterialCheckBox(getActivity());
        cb.setEnabled(mEdit);
        cb.setPadding(0, 25, 0, 25);
        cb.setGravity(Gravity.START);
        cb.setChecked(mPricePlan.isDeemedExport());
        cb.setOnClickListener(v -> {
            mPricePlan.setDeemedExport(cb.isChecked());
            PricePlanActivity ppa = ((PricePlanActivity) requireActivity());
            ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
            ppa.setSaveNeeded(true);
        });
        cb.setText(R.string.use_deemed_export);
        return cb;
    }

    private void showHelp() {
        mHelpWindow.setHeight((int) (requireActivity().getWindow().getDecorView().getHeight()*0.6));
        mHelpWindow.setWidth(requireActivity().getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(mPropertiesTable, Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl("https://appassets.androidplatform.net/assets/priceplan/table.html");
    }
}