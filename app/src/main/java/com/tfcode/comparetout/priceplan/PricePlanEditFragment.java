package com.tfcode.comparetout.priceplan;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.DayRate;
import com.tfcode.comparetout.model.PricePlan;
import com.tfcode.comparetout.model.json.JsonTools;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link PricePlanEditFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PricePlanEditFragment extends Fragment {

    private PricePlanNavViewModel mViewModel;
    private TableLayout mTableLayout;

    private String mFocus;
    private Long mPlanID;
    private boolean mEdit;
    private PricePlan mPricePlan;
    private List<DayRate> mDayRates;

    private List<View> mEditFields;

    public PricePlanEditFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PricePlanEditFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static PricePlanEditFragment newInstance() {
        PricePlanEditFragment fragment = new PricePlanEditFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mPlanID = ((PricePlanActivity) requireActivity()).getPlanID();
        mFocus = ((PricePlanActivity) requireActivity()).getFocusedPlan();
        mEdit = ((PricePlanActivity) requireActivity()).getEdit();
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
        mViewModel = new ViewModelProvider(requireActivity()).get(PricePlanNavViewModel.class);
//        });
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

//        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
//        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_plan_pager);
//        ArrayList<String> tabTitlesList = new ArrayList<>();
//        tabTitlesList.add("Plan details");
//        for (DayRate dr: mDayRates) tabTitlesList.add("DayRate");
//        ((PricePlanViewPageAdapter) viewPager.getAdapter()).setDayRateCount(mDayRates.size()+1);
//        String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
//        String[] tabTitles = {"Plan details", "Day rate", "Day rate"};
//        new TabLayoutMediator(tabLayout, viewPager,
//                (tab, position) -> tab.setText(tabTitles[position])
//        ).attach();

        updateView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("PricePlanEditFragment.onOptionsItemSelected");
        if (item.getItemId() == R.id.edit_a_plan) {//add the function to perform here
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

    public void setEditMode(boolean ed) {
        if (!mEdit) {
            mEdit = ed;
            for (View v : mEditFields) v.setEnabled(true);
            ((PricePlanActivity) requireActivity()).setEdit(true);
        }
    }

    public void updateView() {
        System.out.println("Updating PricePlanEditFragment " + mEdit);
        mTableLayout.removeAllViews();

        // CREATE PARAM FOR MARGINING
        TableRow.LayoutParams planParams = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        planParams.topMargin = 2;
        planParams.rightMargin = 2;

        // CREATE TABLE ROWS
        TableRow tableRow = new TableRow(getActivity());
        TextView a = new TextView(getActivity());
        a.setText("Supplier");
        EditText b = new EditText(getActivity());
        b.setText(mPricePlan.getSupplier());
        b.setEnabled(mEdit);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                System.out.println("Plan supplier edit lost focus");
                mPricePlan.setSupplier(((EditText)v).getText().toString());
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
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
        a.setText("Plan");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(mPricePlan.getPlanName());
        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                System.out.println("Plan name edit lost focus");
                mPricePlan.setPlanName( ((EditText)v).getText().toString());
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
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
        a.setText("Feed in rate (c)");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText("" + mPricePlan.getFeed());
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                System.out.println("Feed edit lost focus");
                mPricePlan.setFeed( Double.parseDouble(((EditText)v).getText().toString()));
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
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
        a.setText("Standing charges (€)");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText("" + mPricePlan.getStandingCharges());
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                System.out.println("Standing charged edit lost focus");
                mPricePlan.setStandingCharges( Double.parseDouble(((EditText)v).getText().toString()));
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
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
        a.setText("Sign up bonus (€)");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText("" + mPricePlan.getSignUpBonus());
        b.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                System.out.println("Bonus edit lost focus");
                mPricePlan.setSignUpBonus( Double.parseDouble(((EditText)v).getText().toString()));
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
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
        a.setText("Last update");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText("" + mPricePlan.getLastUpdate());
        b.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                System.out.println("Last update edit lost focus");
                mPricePlan.setLastUpdate( ((EditText)v).getText().toString());
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
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
        a.setText("Reference");
        b = new EditText(getActivity());
        b.setEnabled(mEdit);
        b.setText(mPricePlan.getReference());
        b.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE);
        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                System.out.println("Reference edit lost focus");
                mPricePlan.setReference( ((EditText)v).getText().toString());
                ((PricePlanActivity)requireActivity()).updateFocusedPlan(
                        JsonTools.createSinglePricePlanJsonObject(mPricePlan, mDayRates));
            }
        });
        a.setLayoutParams(planParams);
        b.setLayoutParams(planParams);
        tableRow.addView(a);
        tableRow.addView(b);
        mTableLayout.addView(tableRow);
        mEditFields.add(b);

    }
}