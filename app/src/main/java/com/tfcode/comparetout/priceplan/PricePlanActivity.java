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
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PricePlanActivity extends AppCompatActivity {

    ViewPager2 viewPager;
    private Menu mMenu;

    private ComparisonUIViewModel mViewModel;
    private Boolean edit = false;
    private Integer mPlanValidity = PricePlan.VALID_PLAN;
    private Long planID = 0L;
    private String focusedPlan = "{}";
    private TabLayoutMediator mMediator;
    private ActionBar mActionBar;
//    private ProgressBar mProgressBar;
//    private Handler mMainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        edit = intent.getBooleanExtra("Edit", false);
        planID = intent.getLongExtra("PlanID", 0L);
        focusedPlan = intent.getStringExtra("Focus");

        setContentView(R.layout.activity_price_plan);
        viewPager = findViewById(R.id.view_plan_pager);

//        createProgressBar();
        setupViewPager();

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Price plan details");
    }

//    private void createProgressBar() {
//        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
//        ConstraintLayout constraintLayout = findViewById(R.id.pp_activity);
//        ConstraintSet set = new ConstraintSet();
//
//        mProgressBar.setId(View.generateViewId());  // cannot set id after add
//        constraintLayout.addView(mProgressBar,0);
//        set.clone(constraintLayout);
//        set.connect(mProgressBar.getId(), ConstraintSet.TOP, constraintLayout.getId(), ConstraintSet.TOP, 60);
//        set.connect(mProgressBar.getId(), ConstraintSet.BOTTOM, constraintLayout.getId(), ConstraintSet.BOTTOM, 60);
//        set.connect(mProgressBar.getId(), ConstraintSet.RIGHT, constraintLayout.getId(), ConstraintSet.RIGHT, 60);
//        set.connect(mProgressBar.getId(), ConstraintSet.LEFT, constraintLayout.getId(), ConstraintSet.LEFT, 60);
//        set.applyTo(constraintLayout);
//        mProgressBar.setVisibility(View.GONE);
//
//        mMainHandler = new Handler(Looper.getMainLooper());
//    }

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
        for (DayRateJson dr: ppj.rates) tabTitlesList.add("Rates");
        String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
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
            p.setId(planID);
            ArrayList<DayRate> drs = new ArrayList<>();
            for (DayRateJson drj : pp.rates) {
                DayRate dr = JsonTools.createDayRate(drj);
                dr.setPricePlanId(planID);
                drs.add(dr);
            }
            mViewModel.updatePricePlan(p, drs);

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
                dayRates.remove(pos - 1);

                focusedPlan = JsonTools.createSinglePricePlanJsonObject(pricePlan, dayRates);

//                viewPager.setCurrentItem(pos - 1);
                ((PricePlanViewPageAdapter)viewPager.getAdapter()).delete(pos);

                ppj = new Gson().fromJson(focusedPlan, type);
                ArrayList<String> tabTitlesList = new ArrayList<>();
                tabTitlesList.add("Plan details");
                for (DayRateJson dr : ppj.rates) tabTitlesList.add("Rates");
                String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
                TabLayout tabLayout = findViewById(R.id.tab_layout);
                mMediator.detach();
                mMediator = new TabLayoutMediator(tabLayout, viewPager,
                        (tab, position) -> tab.setText(tabTitles[position])
                );
                mMediator.attach();
            }
            else {
                Snackbar.make(getWindow().getDecorView().getRootView(), "Try again from a RATES tab", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
            return true;
        }
        if (item.getItemId() == R.id.add_a_day_rate) {
            System.out.println("Adding a dayRate");
            int pos = viewPager.getAdapter().getItemCount(); //.getCurrentItem();
            Type type = new TypeToken<PricePlanJsonFile>() {}.getType();
            PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
            PricePlan pricePlan = JsonTools.createPricePlan(ppj);
            ArrayList<DayRate> dayRates = new ArrayList<>();
            for (DayRateJson drj : ppj.rates) {
                dayRates.add(JsonTools.createDayRate(drj));
            }
            DayRate newDayRate = new DayRate();
            newDayRate.setPricePlanId(pricePlan.getId());
            dayRates.add(newDayRate);

            focusedPlan = JsonTools.createSinglePricePlanJsonObject(pricePlan, dayRates);

            ppj = new Gson().fromJson(focusedPlan, type);
            ArrayList<String> tabTitlesList = new ArrayList<>();
            tabTitlesList.add("Plan details");
            for (DayRateJson dr : ppj.rates) tabTitlesList.add("Rates");
            String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
            TabLayout tabLayout = findViewById(R.id.tab_layout);
            mMediator.detach();
            mMediator = new TabLayoutMediator(tabLayout, viewPager,
                    (tab, position) -> tab.setText(tabTitles[position])
            );
            mMediator.attach();
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

//    public void startProgressIndicator() {mProgressBar.setVisibility(View.VISIBLE);}
//
//    public void stopProgressIndicator() {mProgressBar.setVisibility(View.GONE);}
}