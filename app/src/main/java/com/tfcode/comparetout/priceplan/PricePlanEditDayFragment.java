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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.DoubleHolder;
import com.tfcode.comparetout.model.priceplan.HourlyRate;
import com.tfcode.comparetout.model.priceplan.HourlyRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.util.AbstractTextWatcher;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PricePlanEditDayFragment extends Fragment {

    private TableLayout mTableLayout;

    private String mFocus;
    private boolean mEdit;
    private PricePlan mPricePlan;
    private Map<Integer, DayRate> mDayRates;

    private int mRateIndex;

    private List<View> mEditFields;

    private static final String FOCUS = "FOCUS";
    private static final String EDIT = "EDIT";
    private static final String INDEX = "INDEX";

    public PricePlanEditDayFragment() {
        // Required empty public constructor
    }

    public static PricePlanEditDayFragment newInstance(Integer position) {
        PricePlanEditDayFragment fragment = new PricePlanEditDayFragment();
        fragment.mRateIndex = position - 1;
        return fragment;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EDIT, mEdit);
        outState.putString(FOCUS, mFocus);
        outState.putInt(INDEX, mRateIndex);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mEdit = savedInstanceState.getBoolean(EDIT);
            mFocus = savedInstanceState.getString(FOCUS);
            mRateIndex = savedInstanceState.getInt(INDEX);
            mEditFields = new ArrayList<>();
            unpackmFocus();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            mEdit = savedInstanceState.getBoolean(EDIT);
            mFocus = savedInstanceState.getString(FOCUS);
        }
        else {
            mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
            mEdit = ((PricePlanActivity) requireActivity()).getEdit();
        }
        mEditFields = new ArrayList<>();
        unpackmFocus();
    }

    private void unpackmFocus() {
        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        PricePlanJsonFile ppj = new Gson().fromJson(mFocus, type);
        mPricePlan = JsonTools.createPricePlan(ppj);
        mDayRates = new HashMap<>();
        int idx = 0;
        for (DayRateJson drj : ppj.rates){
            DayRate dr = JsonTools.createDayRate(drj);
            mDayRates.put(idx, dr);
            idx++;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_price_plan_edit_day, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = requireView().findViewById(R.id.planEditDayTable);
        mTableLayout.setShrinkAllColumns(true);
        mTableLayout.setStretchAllColumns(true);
        updateView();
        setupMenu();
    }

    private void setupMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {}

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                System.out.println("PricePlanEditFragment.onOptionsItemSelected");
                if (menuItem.getItemId() == R.id.edit_a_plan) {//add the function to perform here
                    System.out.println("Edit attempt");
                    setEditMode(true);
                    return (false);
                }
                return true;
            }
        });
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity()))
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        try {
            DayRate thisFragmentsDayRate = mDayRates.get(mRateIndex);
            mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
            unpackmFocus();
            mDayRates.remove(mRateIndex);
            mDayRates.put(mRateIndex, thisFragmentsDayRate);

        } catch (IllegalStateException ise) {
            System.out.println("Fragment " + (mRateIndex + 1) + " was detached from activity during resume");
        }
    }

    public void setEditMode(boolean ed) {
        mEdit = ed;
        if (!(null == mEditFields)) for (View v : mEditFields) v.setEnabled(mEdit);
    }

    public void refreshFocus() {
        try {
            mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
            unpackmFocus();
            updateView();
        } catch (IllegalStateException ise) {
            System.out.println("Fragment " + (mRateIndex + 1) + " was detached from activity during refresh");
            ise.printStackTrace();
        }
    }

    public void dayRateDeleted(Integer newPosition) {
        System.out.println("Updating fragment index from " + mRateIndex + " to " + (newPosition -1));
        mRateIndex = newPosition -1;
        try {
            mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
            unpackmFocus();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            System.out.println("Fragment " + (mRateIndex + 1) + " was detached from activity during delete");
            ise.printStackTrace();
        }
    }

    @SuppressLint("DefaultLocale")
    public void updateView() {
        mTableLayout.removeAllViews();

        if (!(null == getActivity())) {

            // CREATE PARAM FOR MARGINING
            TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            planParams.topMargin = 2;
            planParams.rightMargin = 2;

            TableRow.LayoutParams textParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
            textParams.topMargin = 2;
            textParams.rightMargin = 2;

            // CREATE TABLE ROWS

            // Dates
            TableLayout datesTable = new TableLayout(getActivity());
            datesTable.setShrinkAllColumns(true);
            datesTable.setStretchAllColumns(true);
            TableRow datesRow = new TableRow(getActivity());
            {
                DayRate dRate = mDayRates.get(mRateIndex);
                if (!(null == dRate)) {
                    TableRow tableRow = new TableRow(getActivity());
                    TextView a = new TextView(getActivity());
                    a.setText(R.string.FromFormat);
                    tableRow.addView(a);
                    EditText b = new EditText(getActivity());
                    b.setText(dRate.getStartDate());
                    b.setOnFocusChangeListener((v, hasFocus) -> {
                        if (!hasFocus) {
                            System.out.println("FROM edit lost focus");
                            DayRate dayRate = mDayRates.get(mRateIndex);
                            if (!(null == dayRate))
                                dayRate.setStartDate(((EditText) v).getText().toString());
                            updateFocusAndValidate();
                        }
                    });
                    b.setEnabled(mEdit);
                    tableRow.addView(b);
                    TextView c = new TextView(getActivity());
                    c.setText(R.string.ToFormat);
                    tableRow.addView(c);
                    EditText d = new EditText(getActivity());
                    d.setText(dRate.getEndDate());
                    d.setOnFocusChangeListener((v, hasFocus) -> {
                        if (!hasFocus) {
                            System.out.println("TO edit lost focus");
                            DayRate dayRate = mDayRates.get(mRateIndex);
                            if (!(null == dayRate))
                                dayRate.setEndDate(((EditText) v).getText().toString());
                            updateFocusAndValidate();
                        }
                    });
                    d.setEnabled(mEdit);
                    tableRow.addView(d);
                    mEditFields.add(b);
                    mEditFields.add(d);
                    datesTable.addView(tableRow);
                }
            }
            datesRow.addView(datesTable);
            mTableLayout.addView(datesRow);

            // Days of week Names
            TableLayout daysTable = new TableLayout(getActivity());
            daysTable.setShrinkAllColumns(true);
            daysTable.setStretchAllColumns(true);
            TableRow daysRow = new TableRow(getActivity());
            {
                TableRow tableRow = new TableRow(getActivity());
                TextView a = new TextView(getActivity());
                a.setText(R.string.Mon);
                tableRow.addView(a);
                TextView b = new TextView(getActivity());
                b.setText(R.string.Tue);
                tableRow.addView(b);
                TextView c = new TextView(getActivity());
                c.setText(R.string.Wed);
                tableRow.addView(c);
                TextView d = new TextView(getActivity());
                d.setText(R.string.Thu);
                tableRow.addView(d);
                TextView e = new TextView(getActivity());
                e.setText(R.string.Fri);
                tableRow.addView(e);
                TextView f = new TextView(getActivity());
                f.setText(R.string.Sat);
                tableRow.addView(f);
                TextView g = new TextView(getActivity());
                g.setText(R.string.Sun);
                tableRow.addView(g);
                daysTable.addView(tableRow);
            }
            // Days of week checkboxes
            {
                DayRate dayRate = mDayRates.get(mRateIndex);
                if (!(null == dayRate)) {
                    TableRow tableRow = new TableRow(getActivity());
                    CheckBox c1 = new MaterialCheckBox(getActivity());
                    c1.setChecked(dayRate.getDays().ints.contains(1));
                    c1.setContentDescription("Rates apply to Monday");
                    c1.setGravity(Gravity.CENTER_VERTICAL);
                    tableRow.addView(c1);
                    c1.setOnClickListener(v -> updateDays(c1.isChecked(), 1));
                    CheckBox c2 = new MaterialCheckBox(getActivity());
                    c2.setChecked(dayRate.getDays().ints.contains(2));
                    c2.setContentDescription("Rates apply to Tuesday");
                    c2.setGravity(Gravity.CENTER_VERTICAL);
                    tableRow.addView(c2);
                    c2.setOnClickListener(v -> updateDays(c2.isChecked(), 2));
                    CheckBox c3 = new MaterialCheckBox(getActivity());
                    c3.setChecked(dayRate.getDays().ints.contains(3));
                    c3.setContentDescription("Rates apply to Wednesday");
                    c3.setGravity(Gravity.CENTER_VERTICAL);
                    tableRow.addView(c3);
                    c3.setOnClickListener(v -> updateDays(c3.isChecked(), 3));
                    CheckBox c4 = new MaterialCheckBox(getActivity());
                    c4.setChecked(dayRate.getDays().ints.contains(4));
                    c4.setContentDescription("Rates apply to Thursday");
                    c4.setGravity(Gravity.CENTER_VERTICAL);
                    tableRow.addView(c4);
                    c4.setOnClickListener(v -> updateDays(c4.isChecked(), 4));
                    CheckBox c5 = new MaterialCheckBox(getActivity());
                    c5.setChecked(dayRate.getDays().ints.contains(5));
                    c5.setContentDescription("Rates apply to Friday");
                    c5.setGravity(Gravity.CENTER_VERTICAL);
                    tableRow.addView(c5);
                    c5.setOnClickListener(v -> updateDays(c5.isChecked(), 5));
                    CheckBox c6 = new MaterialCheckBox(getActivity());
                    c6.setChecked(dayRate.getDays().ints.contains(6));
                    c6.setContentDescription("Rates apply to Saturday");
                    c6.setGravity(Gravity.CENTER_VERTICAL);
                    tableRow.addView(c6);
                    c6.setOnClickListener(v -> updateDays(c6.isChecked(), 6));
                    CheckBox c7 = new MaterialCheckBox(getActivity());
                    c7.setChecked(dayRate.getDays().ints.contains(0));
                    c7.setContentDescription("Rates apply to Sunday");
                    c7.setGravity(Gravity.CENTER_VERTICAL);
                    tableRow.addView(c7);
                    c7.setOnClickListener(v -> updateDays(c7.isChecked(), 0));
                    c1.setEnabled(mEdit);
                    c2.setEnabled(mEdit);
                    c3.setEnabled(mEdit);
                    c4.setEnabled(mEdit);
                    c5.setEnabled(mEdit);
                    c6.setEnabled(mEdit);
                    c7.setEnabled(mEdit);
                    mEditFields.add(c1);
                    mEditFields.add(c2);
                    mEditFields.add(c3);
                    mEditFields.add(c4);
                    mEditFields.add(c5);
                    mEditFields.add(c6);
                    mEditFields.add(c7);
                    daysTable.addView(tableRow);
                }
            }
            daysRow.addView(daysTable);
            mTableLayout.addView(daysRow);

            // SPACER
            {
                TableRow spacer = new TableRow(getActivity());
                TextView spacerTV = new TextView(getActivity());
                spacerTV.setSingleLine(true);
                spacerTV.setEllipsize(TextUtils.TruncateAt.END);
                spacerTV.setText(R.string.Spacer);
                spacerTV.setContentDescription("Separator element for price table");
                spacer.addView(spacerTV);
                mTableLayout.addView(spacer);
            }

            // PRICES Table
            DayRate dRate = mDayRates.get(mRateIndex);
            if (null == dRate) dRate = new DayRate();
            DoubleHolder ratesList = dRate.getHours();
            TableLayout pricesTable = new TableLayout(getActivity());
            pricesTable.setShrinkAllColumns(false);
            pricesTable.setColumnShrinkable(3, true);
            pricesTable.setStretchAllColumns(true);
            pricesTable.setColumnStretchable(3, false);
            ArrayList<TableRow> pricesTableEntries = new ArrayList<>();
            TableRow pricesTableRow = new TableRow(getActivity());
            {
                // TITLES
                {
                    TableRow titleRow = new TableRow(getActivity());
                    TextView a = new TextView(getActivity());
                    a.setText(R.string.From);
                    a.setGravity(Gravity.CENTER);
                    titleRow.addView(a);
                    TextView b = new TextView(getActivity());
                    b.setText(R.string.To);
                    b.setGravity(Gravity.CENTER);
                    titleRow.addView(b);
                    TextView c = new TextView(getActivity());
                    c.setText("¢");
                    c.setGravity(Gravity.CENTER);
                    titleRow.addView(c);
                    TextView d = new TextView(getActivity());
                    d.setText(R.string.Del);
                    d.setGravity(Gravity.CENTER);
                    titleRow.addView(d);
                    pricesTable.addView(titleRow);
                }

                // HOURLY RATES
                HourlyRateRange hourlyRateRange =
                        new HourlyRateRange(ratesList);
                Collections.reverse(hourlyRateRange.getRates());
                EditText nextFrom = null;
                EditText nextPrice = null;
                for (HourlyRate hourlyRate : hourlyRateRange.getRates()) {
                    TableRow priceRow = new TableRow(getActivity());
                    EditText from = new EditText(getActivity());
                    EditText to = new EditText(getActivity());
                    EditText price = new EditText(getActivity());
                    ImageButton del = new ImageButton(getActivity());

                    from.setLayoutParams(textParams);
                    to.setLayoutParams(textParams);
                    price.setLayoutParams(textParams);
                    del.setLayoutParams(textParams);

                    from.setText(String.format("%d", hourlyRate.getBegin()));
                    from.setContentDescription(String.format("From %s", String.format("%d", hourlyRate.getBegin()) ));
                    from.setEnabled(false);
                    priceRow.addView(from);
                    to.setText(String.format("%d", hourlyRate.getEnd()));
                    to.setContentDescription(String.format("To %s", String.format("%d", hourlyRate.getEnd()) ));
                    to.setInputType(InputType.TYPE_CLASS_NUMBER);
                    to.setEnabled(mEdit);
                    mEditFields.add(to);
                    EditText finalNextFrom = nextFrom;
                    to.addTextChangedListener(new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            Integer newToValue = getIntegerOrZero(s);
                            Integer fromValue = Integer.parseInt(from.getText().toString());
                            Double rate = Double.parseDouble((price.getText().toString()));
                            ratesList.update(fromValue, newToValue, rate);
                            System.out.println("TO_HOUR edit lost focus " + ratesList);
                            DayRate dayRate = mDayRates.get(mRateIndex);
                            if (!(null == dayRate)) dayRate.setHours(ratesList);
                            if (!(null == finalNextFrom))
                                finalNextFrom.setText(String.format("%d", newToValue));
                            updateFocusAndValidate();
                        }
                    });
                    priceRow.addView(to);
                    price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    price.setText(String.format("%s", hourlyRate.getPrice()));
                    price.setContentDescription(String.format("Rate %s from %s to %s", String.format("%s", hourlyRate.getPrice()),
                            String.format("%d", hourlyRate.getBegin()),
                            String.format("%d", hourlyRate.getEnd()) ));
                    price.setEnabled(mEdit);
                    mEditFields.add(price);
                    price.addTextChangedListener(new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            Double newValue = getDoubleOrZero(s);
                            Integer toValue = Integer.parseInt(to.getText().toString());
                            Integer fromValue = Integer.parseInt(from.getText().toString());
                            ratesList.update(fromValue, toValue, newValue);
                            System.out.println("PRICE edit lost focus " + ratesList);
                            DayRate dayRate = mDayRates.get(mRateIndex);
                            if (!(null == dayRate)) dayRate.setHours(ratesList);
                            updateFocusAndValidate();
                        }
                    });
                    priceRow.addView(price);
                    del.setImageResource(R.drawable.ic_baseline_delete_24);
                    EditText finalNextPrice = nextPrice;
                    del.setOnClickListener(v -> {
                        System.out.println("delete: " + v.getId());
                        double newValue = 0;
                        if (!(null == finalNextPrice))
                            newValue = Double.parseDouble(finalNextPrice.getText().toString());
                        Integer toValue = Integer.parseInt(to.getText().toString());
                        Integer fromValue = Integer.parseInt(from.getText().toString());
                        ratesList.update(fromValue, toValue, newValue);
                        System.out.println("PRICE edit lost focus " + ratesList);
                        DayRate dayRate = mDayRates.get(mRateIndex);
                        if (!(null == dayRate)) dayRate.setHours(ratesList);
                        updateFocusAndValidate();
                        updateView();
                    });
                    del.setBackgroundColor(0);
                    del.setContentDescription(String.format("Delete price %s from %s to %s", String.format("%s", hourlyRate.getPrice()),
                            String.format("%d", hourlyRate.getBegin()), String.format("%d", hourlyRate.getEnd())) );
                    priceRow.addView(del);
                    del.setEnabled(mEdit);
                    mEditFields.add(del);

                    pricesTableEntries.add(priceRow);
                    nextFrom = from;
                    nextPrice = price;
                }
                Collections.reverse(pricesTableEntries);
                for (TableRow tr : pricesTableEntries) {
                    pricesTable.addView(tr);
                }
            }
            pricesTableRow.addView(pricesTable);
            mTableLayout.addView(pricesTableRow);

            // SPACER
            {
                TableRow spacer = new TableRow(getActivity());
                TextView spacerTV = new TextView(getActivity());
                spacerTV.setSingleLine(true);
                spacerTV.setEllipsize(TextUtils.TruncateAt.END);
                spacerTV.setText(R.string.Spacer);
                spacerTV.setContentDescription("Separator element for adding");
                spacer.addView(spacerTV);
                mTableLayout.addView(spacer);
            }

            // ADD NEW RATE
            {
                TableRow addPriceTableRow = new TableRow(getActivity());
                TableLayout addPriceTable = new TableLayout(getActivity());
                addPriceTable.setShrinkAllColumns(false);
                addPriceTable.setColumnShrinkable(3, true);
                addPriceTable.setStretchAllColumns(true);
                addPriceTable.setColumnStretchable(3, false);

                TableRow addPriceRow = new TableRow(getActivity());
                EditText from = new EditText(getActivity());
                EditText to = new EditText(getActivity());
                EditText price = new EditText(getActivity());
                ImageButton add = new ImageButton(getActivity());

                from.setLayoutParams(textParams);
                to.setLayoutParams(textParams);
                price.setLayoutParams(textParams);
                add.setContentDescription("Add new price");

                from.setText(R.string.Hour0);
                to.setText(R.string.Hour24);
                price.setText(R.string.NewRateDouble);
                from.setEnabled(mEdit);
                to.setEnabled(mEdit);
                price.setEnabled(mEdit);
                add.setEnabled(mEdit);
                from.setInputType(InputType.TYPE_CLASS_NUMBER);
                to.setInputType(InputType.TYPE_CLASS_NUMBER);
                price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                add.setImageResource(android.R.drawable.ic_menu_add);
                add.setBackgroundColor(0);
                add.setOnClickListener(v -> {
                    Double buyPrice = Double.parseDouble(price.getText().toString());
                    Integer toValue = Integer.parseInt(to.getText().toString());
                    Integer fromValue = Integer.parseInt(from.getText().toString());
                    ratesList.update(fromValue, toValue, buyPrice);
                    System.out.println("Adding an hourly rate " + ratesList);
                    DayRate dayRate = mDayRates.get(mRateIndex);
                    if (!(null == dayRate)) dayRate.setHours(ratesList);
                    updateFocusAndValidate();
                    updateView();
                });
                mEditFields.add(from);
                mEditFields.add(to);
                mEditFields.add(price);
                mEditFields.add(add);
                addPriceRow.addView(from);
                addPriceRow.addView(to);
                addPriceRow.addView(price);
                addPriceRow.addView(add);

                addPriceTable.addView(addPriceRow);
                addPriceTableRow.addView(addPriceTable);
                mTableLayout.addView(addPriceTableRow);
            }
        }
    }

    private void updateDays(boolean checked, Integer integer) {
        System.out.println("Select day: " + integer + " " + checked);
        DayRate dayRate = mDayRates.get(mRateIndex);
        if (!(null == dayRate)) {
            if (checked) {
                if (!dayRate.getDays().ints.contains(integer))
                    dayRate.getDays().ints.add(integer);
            } else {
                dayRate.getDays().ints.remove(integer);
            }
            updateFocusAndValidate();
        }
    }

    private void updateFocusAndValidate() {
        PricePlanActivity ppa = (PricePlanActivity)requireActivity();
        ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, new ArrayList<>(mDayRates.values())));
        ppa.setPlanValidity(mPricePlan.validatePlan(new ArrayList<>(mDayRates.values())));
        ppa.setSaveNeeded(true);
    }
}