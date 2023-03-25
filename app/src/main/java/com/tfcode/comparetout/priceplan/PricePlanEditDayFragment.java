package com.tfcode.comparetout.priceplan;

import android.graphics.Color;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.DoubleHolder;
import com.tfcode.comparetout.model.priceplan.HourlyRate;
import com.tfcode.comparetout.model.priceplan.HourlyRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PricePlanEditDayFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PricePlanEditDayFragment extends Fragment {

    private ComparisonUIViewModel mViewModel;
    private TableLayout mTableLayout;

    private String mFocus;
    private Long mPlanID;
    private boolean mEdit;
    private PricePlan mPricePlan;
    private List<DayRate> mDayRates;

    private int mRateIndex;

    private List<View> mEditFields;

    public PricePlanEditDayFragment() {
        // Required empty public constructor
    }

    public static PricePlanEditDayFragment newInstance(Integer position) {
        PricePlanEditDayFragment fragment = new PricePlanEditDayFragment();
        fragment.mRateIndex = position - 1;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setHasOptionsMenu(true);
        mPlanID = ((PricePlanActivity) requireActivity()).getPlanID();
        mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
        mEdit = ((PricePlanActivity) requireActivity()).getEdit();
        mEditFields = new ArrayList<>();
        unpackmFocus();
        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
    }

    private void unpackmFocus() {
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
    @Override
    public void onResume() {
        super.onResume();
        System.out.println("PPEDF::onResume");
        try {
            DayRate thisFragmentsDayRate = mDayRates.get(mRateIndex);
            mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
            unpackmFocus();
            mDayRates.remove(mRateIndex);
            mDayRates.add(mRateIndex, thisFragmentsDayRate);
        } catch (IllegalStateException ise) {
            System.out.println("Fragment " + (mRateIndex + 1) + " was detached from activity during resume");
        }
    }

//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        System.out.println("PricePlanEditFragment.onOptionsItemSelected");
//        if (item.getItemId() == R.id.edit_a_plan) {//add the function to perform here
//            System.out.println("Edit attempt");
//            setEditMode(true);
//            return (false);
//        }
//        return true;
//    }

    public void setEditMode(boolean ed) {
        if (!mEdit) {
            mEdit = ed;
            if (!(null == mEditFields)) for (View v : mEditFields) v.setEnabled(true);
            PricePlanActivity ppa = ((PricePlanActivity) getActivity());
            if (!(null == ppa)) ppa.setEdit(true);
        }
    }

    public void refreshFocus() {
        try {
            mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
            unpackmFocus();
            updateView();
        } catch (IllegalStateException ise) {
            System.out.println("Fragment " + (mRateIndex + 1) + " was detached from activity during refresh");
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
        }
    }

    public void updateView() {
        System.out.println("Updating PricePlanEditDayFragment " + mRateIndex + ", " + mEdit);
        mTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;
//        planParams.width = 0;
//        planParams.weight = 1;

        // CREATE TABLE ROWS

        // Dates
        TableLayout datesTable = new TableLayout(getActivity());
        datesTable.setShrinkAllColumns(true);
        datesTable.setStretchAllColumns(true);
        TableRow datesRow = new TableRow(getActivity());
        {
            TableRow tableRow = new TableRow(getActivity());
            TextView a = new TextView(getActivity());
            a.setText("From (MM/DD)");
            tableRow.addView(a);
            EditText b = new EditText(getActivity());
            b.setText(mDayRates.get(mRateIndex).getStartDate());
            b.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    System.out.println("FROM edit lost focus");
                    mDayRates.get(mRateIndex).setStartDate( ((EditText)v).getText().toString());
                    updateFocusAndValidate();
                }
            });
            b.setEnabled(mEdit);
            tableRow.addView(b);
            TextView c = new TextView(getActivity());
            c.setText("To (MM/DD)");
            tableRow.addView(c);
            EditText d = new EditText(getActivity());
            d.setText(mDayRates.get(mRateIndex).getEndDate());
            d.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    System.out.println("TO edit lost focus");
                    mDayRates.get(mRateIndex).setEndDate( ((EditText)v).getText().toString());
                    updateFocusAndValidate();
                }
            });
            d.setEnabled(mEdit);
            tableRow.addView(d);
            mEditFields.add(b);
            mEditFields.add(d);
            datesTable.addView(tableRow);
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
            a.setText("Mon");
            tableRow.addView(a);
            TextView b = new TextView(getActivity());
            b.setText("Tue");
            tableRow.addView(b);
            TextView c = new TextView(getActivity());
            c.setText("Wed");
            tableRow.addView(c);
            TextView d = new TextView(getActivity());
            d.setText("Thu");
            tableRow.addView(d);
            TextView e = new TextView(getActivity());
            e.setText("Fri");
            tableRow.addView(e);
            TextView f = new TextView(getActivity());
            f.setText("Sat");
            tableRow.addView(f);
            TextView g = new TextView(getActivity());
            g.setText("Sun");
            tableRow.addView(g);
            daysTable.addView(tableRow);
        }
        // Days of week checkboxes
        {
            TableRow tableRow = new TableRow(getActivity());
            CheckBox c1 = new CheckBox(getActivity());
            c1.setChecked(mDayRates.get(mRateIndex).getDays().ints.contains(1));
            tableRow.addView(c1);
            c1.setOnClickListener(v -> updateDays(c1.isChecked(), 1));
            CheckBox c2 = new CheckBox(getActivity());
            c2.setChecked(mDayRates.get(mRateIndex).getDays().ints.contains(2));
            tableRow.addView(c2);
            c2.setOnClickListener(v -> updateDays(c2.isChecked(), 2));
            CheckBox c3 = new CheckBox(getActivity());
            c3.setChecked(mDayRates.get(mRateIndex).getDays().ints.contains(3));
            tableRow.addView(c3);
            c3.setOnClickListener(v -> updateDays(c3.isChecked(), 3));
            CheckBox c4 = new CheckBox(getActivity());
            c4.setChecked(mDayRates.get(mRateIndex).getDays().ints.contains(4));
            tableRow.addView(c4);
            c4.setOnClickListener(v -> updateDays(c4.isChecked(), 4));
            CheckBox c5 = new CheckBox(getActivity());
            c5.setChecked(mDayRates.get(mRateIndex).getDays().ints.contains(5));
            tableRow.addView(c5);
            c5.setOnClickListener(v -> updateDays(c5.isChecked(), 5));
            CheckBox c6 = new CheckBox(getActivity());
            c6.setChecked(mDayRates.get(mRateIndex).getDays().ints.contains(6));
            tableRow.addView(c6);
            c6.setOnClickListener(v -> updateDays(c6.isChecked(), 6));
            CheckBox c7 = new CheckBox(getActivity());
            c7.setChecked(mDayRates.get(mRateIndex).getDays().ints.contains(0));
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
        daysRow.addView(daysTable);
        mTableLayout.addView(daysRow);

        // SPACER
        {
            TableRow spacer = new TableRow(getActivity());
            TextView spacerTV = new TextView(getActivity());
            spacerTV.setSingleLine(true);
            spacerTV.setEllipsize(TextUtils.TruncateAt.END);
            spacerTV.setText("......................................................................" +
                    "...............................................................................");
            spacer.addView(spacerTV);
            mTableLayout.addView(spacer);
        }

        // PRICES Table
        DoubleHolder ratesList = mDayRates.get(mRateIndex).getHours();
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
                a.setText("From");
                a.setGravity(Gravity.CENTER);
                titleRow.addView(a);
                TextView b = new TextView(getActivity());
                b.setText("To");
                b.setGravity(Gravity.CENTER);
                titleRow.addView(b);
                TextView c = new TextView(getActivity());
                c.setText("€");
                c.setGravity(Gravity.CENTER);
                titleRow.addView(c);
                TextView d = new TextView(getActivity());
                d.setText("Del");
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
            for (HourlyRate hourlyRate: hourlyRateRange.getRates()){
                TableRow priceRow = new TableRow(getActivity());
                EditText from = new EditText(getActivity());
                EditText to = new EditText(getActivity());
                EditText price = new EditText(getActivity());
                ImageButton del = new ImageButton(getActivity());
                from.setText("" + hourlyRate.getBegin());
                from.setEnabled(false);
                priceRow.addView(from);
                to.setText("" + hourlyRate.getEnd());
                to.setInputType(InputType.TYPE_CLASS_NUMBER);
                to.setEnabled(mEdit);
                mEditFields.add(to);
                EditText finalNextFrom = nextFrom;
                to.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        Integer newToValue = Integer.parseInt(((EditText)v).getText().toString());
                        Integer fromValue = Integer.parseInt(from.getText().toString());
                        Double rate = Double.parseDouble((price.getText().toString()));
                        ratesList.update(fromValue, newToValue, rate);
                        System.out.println("TO_HOUR edit lost focus " + ratesList);
                        mDayRates.get(mRateIndex).setHours(ratesList);
                        if (!(null == finalNextFrom)) finalNextFrom.setText(newToValue.toString());
                        updateFocusAndValidate();
                    }
                });
                priceRow.addView(to);
                price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                price.setText("" + hourlyRate.getPrice());
                price.setEnabled(mEdit);
                mEditFields.add(price);
//                price.addTextChangedListener(new TextWatcher() {
//                    @Override
//                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//
//                    }
//
//                    @Override
//                    public void onTextChanged(CharSequence s, int start, int before, int count) {
//
//                    }
//
//                    @Override
//                    public void afterTextChanged(Editable s) {
//
//                    }
//                });
                price.setOnFocusChangeListener((v, hasFocus) -> {
                    if (!hasFocus) {
                        Double newValue = Double.parseDouble(((EditText)v).getText().toString());
                        Integer toValue = Integer.parseInt(to.getText().toString());
                        Integer fromValue = Integer.parseInt(from.getText().toString());
                        ratesList.update(fromValue, toValue, newValue);
                        System.out.println("PRICE edit lost focus " + ratesList);
                        mDayRates.get(mRateIndex).setHours(ratesList);
                        updateFocusAndValidate();
                    }
                });
                priceRow.addView(price);
                del.setImageResource(android.R.drawable.ic_menu_delete);
                EditText finalNextPrice = nextPrice;
                del.setOnClickListener(v -> {
                    System.out.println("delete: " + v.getId());
                    Double newValue = (double) 0;
                    if (!(null == finalNextPrice))
                        newValue = Double.parseDouble(finalNextPrice.getText().toString());
                    Integer toValue = Integer.parseInt(to.getText().toString());
                    Integer fromValue = Integer.parseInt(from.getText().toString());
                    ratesList.update(fromValue, toValue, newValue);
                    System.out.println("PRICE edit lost focus " + ratesList);
                    mDayRates.get(mRateIndex).setHours(ratesList);
                    updateFocusAndValidate();
                    updateView();
                });
                del.setBackgroundColor(0);
                priceRow.addView(del);
                del.setEnabled(mEdit);
                mEditFields.add(del);

                pricesTableEntries.add(priceRow);
                nextFrom = from;
                nextPrice = price;
            }
            Collections.reverse(pricesTableEntries);
            for (TableRow tr: pricesTableEntries){
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
            spacerTV.setText("......................................................................" +
                    "...............................................................................");
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

            from.setText("0");
            to.setText("24");
            price.setText("11.1");
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
                mDayRates.get(mRateIndex).setHours(ratesList);
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

    private void updateDays(boolean checked, Integer integer) {
        System.out.println("Select day: " + integer + " " + checked);
        if (checked) {
            if (!mDayRates.get(mRateIndex).getDays().ints.contains(integer))
                mDayRates.get(mRateIndex).getDays().ints.add(integer);
        }
        else {
            mDayRates.get(mRateIndex).getDays().ints.remove(integer);
        }
        updateFocusAndValidate();
    }

    private void updateFocusAndValidate() {
        PricePlanActivity ppa = (PricePlanActivity)requireActivity();
        ppa.updateFocusedPlan(JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
        ppa.setPlanValidity(mPricePlan.validatePlan(mDayRates));
    }
}