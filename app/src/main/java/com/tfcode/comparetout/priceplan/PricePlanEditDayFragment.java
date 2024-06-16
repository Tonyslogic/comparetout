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
import android.webkit.WebView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.priceplan.RangeRate;
import com.tfcode.comparetout.util.AbstractTextWatcher;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

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

        if (!(null == getContext())) {
            mAssetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(getContext()))
                    .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(getContext()))
                    .build();
        }
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
        mPopupView = inflater.inflate(R.layout.popup_help, container);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);
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
                if (menuItem.getItemId() == R.id.edit_a_plan) {//add the function to perform here
                    setEditMode(true);
                    return (false);
                }
                return true;
            }
        });
    }

    @Override
    public void onResume() {

        super.onResume();
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
        mRateIndex = newPosition -1;
        try {
            mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
            unpackmFocus();
            updateView();
        } catch (java.lang.IllegalStateException ise) {
            ise.printStackTrace();
        }
    }

    @SuppressLint("DefaultLocale")
    public void updateView() {
        mTableLayout.removeAllViews();
        mTableLayout.setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
            return true;
        });

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
            datesRow.setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/priceplan/dates.html");
                return true;
            });
            {
                DayRate dRate = mDayRates.get(mRateIndex);
                if (!(null == dRate)) {
                    TableRow tableRow = new TableRow(getActivity());
                    TextView a = new TextView(getActivity());
                    a.setText(R.string.FromFormat);
                    tableRow.addView(a);
                    EditText b = getEditTextForMMDD(dRate.getStartDate());
                    tableRow.addView(b);
                    TextView c = new TextView(getActivity());
                    c.setText(R.string.ToFormat);
                    tableRow.addView(c);
                    EditText d = getEditTextForMMDD(dRate.getEndDate());
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
            daysRow.setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
                return true;
            });
            {
                TableRow tableRowForDaySelectionTitles = getDaySelectionTitleRow();
                daysTable.addView(tableRowForDaySelectionTitles);
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
                    c1.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
                        return true;
                    });
                    c2.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
                        return true;
                    });
                    c3.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
                        return true;
                    });
                    c4.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
                        return true;
                    });
                    c5.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
                        return true;
                    });
                    c6.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
                        return true;
                    });
                    c7.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
                        return true;
                    });
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
                spacer.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                    return true;
                });
                mTableLayout.addView(spacer);
            }

            // PRICES Table
            DayRate dRate = mDayRates.get(mRateIndex);
            if (null == dRate) dRate = new DayRate();
//            DoubleHolder ratesList = dRate.getHours();
            MinuteRateRange minuteRateRange = dRate.getMinuteRateRange();
            TableLayout pricesTable = new TableLayout(getActivity());
            pricesTable.setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                return true;
            });
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
                    a.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                        return true;
                    });
                    b.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                        return true;
                    });
                    c.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                        return true;
                    });
                    d.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                        return true;
                    });
                    pricesTable.addView(titleRow);
                }

                // HOURLY RATES
                minuteRateRange.getRates().sort(Comparator.comparingInt(RangeRate::getBegin));
                Collections.reverse(minuteRateRange.getRates());
                EditText nextFrom = null;
                for (RangeRate rangeRate : minuteRateRange.getRates()) {
                    TableRow priceRow = new TableRow(getActivity());
                    EditText from = new EditText(getActivity());
                    MaterialButton to = new MaterialButton(getActivity());
                    EditText price = new EditText(getActivity());
                    ImageButton del = new ImageButton(getActivity());
                    del.setOnLongClickListener(v -> {
                        showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                        return true;
                    });

                    from.setLayoutParams(textParams);
                    to.setLayoutParams(textParams);
                    price.setLayoutParams(textParams);
                    del.setLayoutParams(textParams);

                    from.setText(String.format("%02d:%02d", rangeRate.getBegin() / 60 , rangeRate.getBegin() % 60));
                    from.setEnabled(false);
                    priceRow.addView(from);
                    to.setText(String.format("%02d:%02d", rangeRate.getEnd() / 60 , rangeRate.getEnd() % 60));
//                    to.setInputType(InputType.TYPE_CLASS_NUMBER);
                    to.setEnabled(mEdit);
                    mEditFields.add(to);
                    EditText finalNextFrom = nextFrom;
                    to.setOnClickListener( v -> {
                        double rate = Double.parseDouble((price.getText().toString()));
                        Integer fromValue = getMinuteOfDayFromHHmm(from.getText().toString());
                        int toValue = getMinuteOfDayFromHHmm(to.getText().toString());
                        int to_hr = toValue / 60;
                        int to_min = toValue % 60;
                        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                                .setTimeFormat(TimeFormat.CLOCK_24H)
                                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                                .setTitleText("Select end time")
                                .setHour(to_hr)
                                .setMinute(to_min)
                                .build();
                        timePicker.addOnPositiveButtonClickListener( tp -> {
                            int hour = timePicker.getHour();
                            int minute = timePicker.getMinute();
                            int updatedToValue = hour * 60 + minute;
                            minuteRateRange.update(fromValue, updatedToValue, rate);
                            DayRate dayRate = mDayRates.get(mRateIndex);
                            if (!(null == dayRate)) dayRate.setMinuteRateRange(minuteRateRange);
                            if (!(null == finalNextFrom))
                                finalNextFrom.setText(String.format("%02d:%02d", hour, minute));
                            to.setText(String.format("%02d:%02d", hour, minute));
                            updateFocusAndValidate();
                        });
                        timePicker.show(getActivity().getSupportFragmentManager(), "TimePicker");
                    });
                    priceRow.addView(to);
                    price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    price.setText(String.format("%s", rangeRate.getPrice()));
                    price.setEnabled(mEdit);
                    mEditFields.add(price);
                    price.addTextChangedListener(new AbstractTextWatcher() {
                        @Override
                        public void afterTextChanged(Editable s) {
                            Double newValue = getDoubleOrZero(s);
                            Integer toValue = getMinuteOfDayFromHHmm(to.getText().toString());
                            Integer fromValue = getMinuteOfDayFromHHmm(from.getText().toString());
                            minuteRateRange.update(fromValue, toValue, newValue);
                            DayRate dayRate = mDayRates.get(mRateIndex);
                            if (!(null == dayRate)) dayRate.setMinuteRateRange(minuteRateRange);
                            updateFocusAndValidate();
                        }
                    });
                    price.setOnFocusChangeListener((v, focus) -> {
                        if (!focus) updateView();
                    });
                    priceRow.addView(price);
                    del.setImageResource(R.drawable.ic_baseline_delete_24);
                    del.setOnClickListener(v -> {
                        Integer toValue = getMinuteOfDayFromHHmm(to.getText().toString());
                        Integer fromValue = getMinuteOfDayFromHHmm(from.getText().toString());

                        minuteRateRange.remove(fromValue, toValue);
                        DayRate dayRate = mDayRates.get(mRateIndex);
                        if (!(null == dayRate)) dayRate.setMinuteRateRange(minuteRateRange);

                        updateFocusAndValidate();
                        updateView();
                    });
                    del.setBackgroundColor(0);
                    del.setContentDescription(String.format("Delete price %s from %s to %s", String.format("%s", rangeRate.getPrice()),
                            String.format("%d", rangeRate.getBegin()), String.format("%d", rangeRate.getEnd())) );
                    priceRow.addView(del);
                    del.setEnabled(mEdit);
                    mEditFields.add(del);

                    pricesTableEntries.add(priceRow);
                    nextFrom = from;
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
                spacer.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                    return true;
                });
                mTableLayout.addView(spacer);
            }

            // ADD NEW RATE
            {
                TableRow addPriceTableRow = new TableRow(getActivity());
                addPriceTableRow.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                    return true;
                });
                TableLayout addPriceTable = new TableLayout(getActivity());
                addPriceTable.setShrinkAllColumns(false);
                addPriceTable.setColumnShrinkable(3, true);
                addPriceTable.setStretchAllColumns(true);
                addPriceTable.setColumnStretchable(3, false);

                TableRow addPriceRow = new TableRow(getActivity());
                MaterialButton from = new MaterialButton(getActivity());
                MaterialButton to = new MaterialButton(getActivity());
                EditText price = new EditText(getActivity());
                ImageButton add = new ImageButton(getActivity());
                add.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/prices.html");
                    return true;
                });

                from.setOnClickListener( v -> {
                    int toValue = getMinuteOfDayFromHHmm(from.getText().toString());
                    int to_hr = toValue / 60;
                    int to_min = toValue % 60;
                    MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                            .setTimeFormat(TimeFormat.CLOCK_24H)
                            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                            .setTitleText("Select start time")
                            .setHour(to_hr)
                            .setMinute(to_min)
                            .build();
                    timePicker.addOnPositiveButtonClickListener( tp -> {
                        int hour = timePicker.getHour();
                        int minute = timePicker.getMinute();
                        from.setText(String.format("%02d:%02d", hour, minute));
                    });
                    timePicker.show(getActivity().getSupportFragmentManager(), "TimePicker");
                });

                to.setOnClickListener( v -> {
                    int toValue = getMinuteOfDayFromHHmm(to.getText().toString());
                    int to_hr = toValue / 60;
                    int to_min = toValue % 60;
                    MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                            .setTimeFormat(TimeFormat.CLOCK_24H)
                            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                            .setTitleText("Select end time")
                            .setHour(to_hr)
                            .setMinute(to_min)
                            .build();
                    timePicker.addOnPositiveButtonClickListener( tp -> {
                        int hour = timePicker.getHour();
                        int minute = timePicker.getMinute();
                        to.setText(String.format("%02d:%02d", hour, minute));
                    });
                    timePicker.show(getActivity().getSupportFragmentManager(), "TimePicker");
                });

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
                price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                add.setImageResource(android.R.drawable.ic_menu_add);
                add.setBackgroundColor(0);
                add.setOnClickListener(v -> {
                    double buyPrice = Double.parseDouble(price.getText().toString());
                    Integer toValue = getMinuteOfDayFromHHmm(to.getText().toString());
                    Integer fromValue = getMinuteOfDayFromHHmm(from.getText().toString());

                    minuteRateRange.insert(fromValue, toValue, buyPrice);
                    DayRate dayRate = mDayRates.get(mRateIndex);
                    if (!(null == dayRate)) dayRate.setMinuteRateRange(minuteRateRange);

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

    @NonNull
    private EditText getEditTextForMMDD(String theDate) {
        EditText b = new EditText(getActivity());
        b.setText(theDate);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                DayRate dayRate = mDayRates.get(mRateIndex);
                if (!(null == dayRate))
                    dayRate.setStartDate(((EditText) v).getText().toString());
                updateFocusAndValidate();
            }
        });
        b.setEnabled(mEdit);
        return b;
    }

    @NonNull
    private TableRow getDaySelectionTitleRow() {
        TableRow tableRow = new TableRow(getActivity());
        tableRow.setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/priceplan/days.html");
            return true;
        });
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
        return tableRow;
    }

    private Integer getMinuteOfDayFromHHmm(String time) {
        int ret;
        if (time == null || !time.matches("\\d{2}:\\d{2}")) {
            throw new IllegalArgumentException("Time must be in the format HH:mm");
        }

        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);

        ret = hours * 60 + minutes;
        return ret;
    }

    private void updateDays(boolean checked, Integer integer) {
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

    private void showHelp(String url) {
        mHelpWindow.setHeight((int) (requireActivity().getWindow().getDecorView().getHeight()*0.6));
        mHelpWindow.setWidth(requireActivity().getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(mTableLayout, Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }
}