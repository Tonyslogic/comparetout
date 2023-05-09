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

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PricePlanActivity extends AppCompatActivity {

    private static final String EDIT_KEY = "Edit";
    private static final String FOCUSED_PLAN = "FOCUSED_PLAN";
    private static final String PLAN_VALIDITY = "PLAN_VALIDITY";
    private static final String PLAN_ID = "PLAN_ID";
    private static final String UNSAVED = "UNSAVED";

    ViewPager2 viewPager;
    private Menu mMenu;

    private ComparisonUIViewModel mViewModel;
    private Boolean edit = false;
    private Integer mPlanValidity = PricePlan.VALID_PLAN;
    private Long planID = 0L;
    private String focusedPlan = "{}";
    private TabLayoutMediator mMediator;
    private ActionBar mActionBar;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EDIT_KEY, edit);
        outState.putLong(PLAN_ID, planID);
        outState.putString(FOCUSED_PLAN, focusedPlan);
        outState.putBoolean(UNSAVED, mUnsavedChanges);
        outState.putInt(PLAN_VALIDITY, mPlanValidity);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        edit = savedInstanceState.getBoolean(EDIT_KEY);
        planID = savedInstanceState.getLong(PLAN_ID);
        focusedPlan = savedInstanceState.getString(FOCUSED_PLAN);
        mUnsavedChanges = savedInstanceState.getBoolean(UNSAVED);
        mPlanValidity = savedInstanceState.getInt(PLAN_VALIDITY);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            edit = savedInstanceState.getBoolean(EDIT_KEY);
            planID = savedInstanceState.getLong(PLAN_ID);
            focusedPlan = savedInstanceState.getString(FOCUSED_PLAN);
            mUnsavedChanges = savedInstanceState.getBoolean(UNSAVED);
            mPlanValidity = savedInstanceState.getInt(PLAN_VALIDITY);
        }
        else {
            Intent intent = getIntent();
            edit = intent.getBooleanExtra("Edit", false);
            planID = intent.getLongExtra("PlanID", 0L);
            focusedPlan = intent.getStringExtra("Focus");
        }

        setContentView(R.layout.activity_price_plan);
        viewPager = findViewById(R.id.view_plan_pager);

        setupViewPager();

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Price plan details");
    }

    // PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER
    private void setupViewPager() {
        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
        int count = ppj.rates.size() + 1;

        viewPager.setAdapter(createPlanAdapter(count));
        viewPager.setOffscreenPageLimit(4);
        System.out.println("setupViewPager " + count + " fragments");

        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("Plan details");
        for (DayRateJson ignored : ppj.rates) tabTitlesList.add("Rates");
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitlesList.get(position))
        );
        mMediator.attach();
    }

    private PricePlanViewPageAdapter createPlanAdapter(int count) {
        return new PricePlanViewPageAdapter(this, count);
    }

    // MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_plans, menu);
        mMenu = menu;
        int colour = Color.parseColor("White");
        mMenu.findItem(R.id.info).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.edit_a_plan).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.add_a_day_rate).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.export_a_plan).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.del_a_day_rate).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.save_a_plan).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem saveMenuItem = menu.findItem(R.id.save_a_plan);
        MenuItem editMenuItem = menu.findItem(R.id.edit_a_plan);
        MenuItem addDayRateItem = menu.findItem((R.id.add_a_day_rate));
        MenuItem delDayRateItem = menu.findItem((R.id.del_a_day_rate));
        MenuItem infoItem = menu.findItem((R.id.info));
        infoItem.setVisible(false);
        if (!edit) {
            saveMenuItem.setVisible(false);
            addDayRateItem.setVisible(false);
            delDayRateItem.setVisible(false);
        }
        if (edit) editMenuItem.setVisible(false);
        checkPlanValidity();
        return super.onPrepareOptionsMenu(menu);
    }

    private void checkPlanValidity() {
        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
        PricePlan pp = JsonTools.createPricePlan(ppj);
        List<DayRate> drs = new ArrayList<>();
        for (DayRateJson drj : ppj.rates){
            DayRate dr = JsonTools.createDayRate(drj);
            drs.add(dr);
        }
        setPlanValidity(pp.validatePlan(drs));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("PricePlanActivity.onOptionsItemSelected");

        if (item.getItemId() == R.id.edit_a_plan) {//add the function to perform here
            System.out.println("Edit attempt");
            edit = true;
            setPlanValidity(mPlanValidity);
            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setVisible(true);
            MenuItem addDayRateItem = mMenu.findItem(R.id.add_a_day_rate);
            addDayRateItem.setVisible(true);
            MenuItem delDayRateItem = mMenu.findItem(R.id.del_a_day_rate);
            delDayRateItem.setVisible(true);
            item.setVisible(false);
            if (!(null == viewPager.getAdapter()))
                ((PricePlanViewPageAdapter)viewPager.getAdapter()).setEdit(true);
            return (false);
        }
        if (item.getItemId() == R.id.export_a_plan){
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, focusedPlan);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return (true);
        }
        if (item.getItemId() == R.id.save_a_plan){
            System.out.println("Saving the changed plan");
            Type type = new TypeToken<PricePlanJsonFile>() {}.getType();
            PricePlanJsonFile pp = new Gson().fromJson(focusedPlan, type);
            System.out.println(pp.plan);
            PricePlan p = JsonTools.createPricePlan(pp);
            p.setPricePlanIndex(planID);
            ArrayList<DayRate> drs = new ArrayList<>();
            for (DayRateJson drj : pp.rates) {
                DayRate dr = JsonTools.createDayRate(drj);
                dr.setPricePlanId(planID);
                drs.add(dr);
            }
            mViewModel.updatePricePlan(p, drs);
            setSaveNeeded(false);

            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setVisible(false);
            MenuItem addDayRateItem = mMenu.findItem(R.id.add_a_day_rate);
            addDayRateItem.setVisible(false);
            MenuItem delDayRateItem = mMenu.findItem(R.id.del_a_day_rate);
            delDayRateItem.setVisible(false);
            MenuItem editItem = mMenu.findItem(R.id.edit_a_plan);
            editItem.setVisible(true);
            if (!(null == viewPager.getAdapter()))
                ((PricePlanViewPageAdapter) viewPager.getAdapter()).setEdit(false);

            return (true);
        }
        if (item.getItemId() == R.id.del_a_day_rate) {
            int pos = viewPager.getCurrentItem();
            if (pos > 0) {
                System.out.println("Deleting a dayRate @ position " + pos);
                Type type = new TypeToken<PricePlanJsonFile>() {}.getType();
                PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
                PricePlan pricePlan = JsonTools.createPricePlan(ppj);
                ArrayList<DayRate> dayRates = new ArrayList<>();
                for (DayRateJson drj : ppj.rates) {
                    dayRates.add(JsonTools.createDayRate(drj));
                }
                System.out.println("REMOVING DAY RATE. TOTAL BEFORE: " + dayRates.size());
                dayRates.remove(pos - 1);

                focusedPlan = JsonTools.createSinglePricePlanJsonObject(pricePlan, dayRates);

                if (!(null == viewPager.getAdapter()))
                    ((PricePlanViewPageAdapter)viewPager.getAdapter()).delete(pos);

                ppj = new Gson().fromJson(focusedPlan, type);
//                refreshMediator(ppj);
                PricePlanJsonFile finalPpj = ppj;
                new Handler(Looper.getMainLooper()).postDelayed(() -> refreshMediator(finalPpj), 200);
            }
            else {
                Snackbar.make(getWindow().getDecorView().getRootView(), "Try again from a RATES tab", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
            return true;
        }
        if (item.getItemId() == R.id.add_a_day_rate) {
            System.out.println("Adding a dayRate");
            int pos = 0;
            if (!(null == viewPager.getAdapter()))
                pos = viewPager.getAdapter().getItemCount(); //.getCurrentItem();
            Type type = new TypeToken<PricePlanJsonFile>() {}.getType();
            PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
            PricePlan pricePlan = JsonTools.createPricePlan(ppj);
            ArrayList<DayRate> dayRates = new ArrayList<>();
            for (DayRateJson drj : ppj.rates) {
                dayRates.add(JsonTools.createDayRate(drj));
            }
            System.out.println("ADDING DAY RATE. TOTAL BEFORE: " + dayRates.size());
            DayRate newDayRate = new DayRate();
            newDayRate.setPricePlanId(pricePlan.getPricePlanIndex());
            dayRates.add(newDayRate);
            System.out.println("ADDING DAY RATE. TOTAL NOW: " + dayRates.size());

            focusedPlan = JsonTools.createSinglePricePlanJsonObject(pricePlan, dayRates);

            ppj = new Gson().fromJson(focusedPlan, type);
            refreshMediator(ppj);
            //
            ((PricePlanViewPageAdapter) viewPager.getAdapter()).add(pos);

            return true;
        }
        if (item.getItemId() == R.id.info) {
            System.out.println("Rendering info");
            Snackbar.make(getWindow().getDecorView().getRootView(), PricePlan.getInvalidReason(mPlanValidity), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
        return false;
    }

    private void refreshMediator(PricePlanJsonFile ppj) {
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("Plan details");
        for (DayRateJson ignored : ppj.rates) tabTitlesList.add("Rates");
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        mMediator.detach();
        mMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitlesList.get(position))
        );
        mMediator.attach();
    }

    @Override
    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce || !(mUnsavedChanges)) {
            super.onBackPressed();
            return;
        }

        this.mDoubleBackToExitPressedOnce = true;
        Snackbar.make(getWindow().getDecorView().getRootView(),
                        "Unsaved changes. Please click BACK again to discard and exit", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> mDoubleBackToExitPressedOnce =false, 2000);
    }

    // FRAGMENT ACCESS METHODS
    long getPlanID() {
        return planID;
    }

    public String getFocusedPlan() {
        return focusedPlan;
    }

    public void updateFocusedPlan(String updatedPlan) {
        focusedPlan = updatedPlan;
    }

    public boolean getEdit() {
        return edit;
    }

    public void setEdit(boolean ed){
        edit = ed;
    }

    public void setPlanValidity(int validityCode) {
        System.out.println("PPA Setting validity to: " + validityCode);
        mPlanValidity = validityCode;
        boolean valid = (mPlanValidity == PricePlan.VALID_PLAN);
        if (!valid) {
            // disable save & share
            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setEnabled(false);
            MenuItem exportDayRateItem = mMenu.findItem(R.id.export_a_plan);
            exportDayRateItem.setEnabled(false);
            mActionBar.setBackgroundDrawable(new ColorDrawable(getColor(R.color.app_bar_nok)));
            MenuItem infoItem = mMenu.findItem(R.id.info);
            infoItem.setVisible(true);
        }
        if (edit && valid) {
            // enable save & share
            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setEnabled(true);
            MenuItem exportDayRateItem = mMenu.findItem(R.id.export_a_plan);
            exportDayRateItem.setEnabled(true);
            mActionBar.setBackgroundDrawable(new ColorDrawable(getColor(R.color.app_bar_ok)));
            MenuItem infoItem = mMenu.findItem(R.id.info);
            infoItem.setVisible(false);
        }
    }

    public void setSaveNeeded(boolean saveNeeded){
        mUnsavedChanges = saveNeeded;
    }

}