package com.tfcode.comparetout.priceplan;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
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
import java.util.Objects;

public class PricePlanActivity extends AppCompatActivity {

    ViewPager2 viewPager;
    private Menu mMenu;

    private PricePlanNavViewModel mViewModel;
    private Boolean edit = false;
    private Long planID = 0L;
    private String focusedPlan = "{}";
    private TabLayoutMediator mMediator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        edit = intent.getBooleanExtra("Edit", false);
        planID = intent.getLongExtra("PlanID", 0L);
        focusedPlan = intent.getStringExtra("Focus");

        setContentView(R.layout.activity_price_plan);
        viewPager = findViewById(R.id.view_plan_pager);
        setupViewPager();

        mViewModel = new ViewModelProvider(this).get(PricePlanNavViewModel.class);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Price plan details");

    }

    // PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER
    private void setupViewPager() {
        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
        int count = ppj.rates.size() + 1;
        viewPager.setAdapter(createPlanAdapter(count));
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
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem saveMenuItem = menu.findItem(R.id.save_a_plan);
        MenuItem editMenuItem = menu.findItem(R.id.edit_a_plan);
        MenuItem addDayRateItem = menu.findItem((R.id.add_a_day_rate));
        if (!edit) {
            saveMenuItem.setVisible(false);
            addDayRateItem.setVisible(false);
//            fab.hide();
        }
        if (edit) editMenuItem.setVisible(false);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("PricePlanActivity.onOptionsItemSelected");

        if (item.getItemId() == R.id.edit_a_plan) {//add the function to perform here
            System.out.println("Edit attempt");
            edit = true;
            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setVisible(true);
            MenuItem addDayRateItem = mMenu.findItem((R.id.add_a_day_rate));
            addDayRateItem.setVisible(true);
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
        if (item.getItemId() == R.id.add_a_day_rate) {
            System.out.println("Adding a dayRate");
            int pos = viewPager.getCurrentItem();
            Type type = new TypeToken<PricePlanJsonFile>() {
            }.getType();
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
}