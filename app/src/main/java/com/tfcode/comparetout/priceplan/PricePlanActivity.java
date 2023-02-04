package com.tfcode.comparetout.priceplan;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.MainActivity;
import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.DayRate;
import com.tfcode.comparetout.model.PricePlan;
import com.tfcode.comparetout.model.json.JsonTools;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PricePlanActivity extends AppCompatActivity {

    ViewPager2 viewPager;

    private PricePlanNavViewModel mViewModel;
    private Boolean edit = false;
    private Boolean copy = false;
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

        /*
          Add price plan or scenario depending on the visible fragment
         */
        FloatingActionButton fab = findViewById(R.id.addDayRate);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = viewPager.getCurrentItem();
                Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
                PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
                PricePlan pricePlan = JsonTools.createPricePlan(ppj);
                ArrayList<DayRate> dayRates = new ArrayList<>();
                for (DayRateJson drj : ppj.rates) {
                    dayRates.add(JsonTools.createDayRate(drj));
                }
                dayRates.add(pos, new DayRate());

                focusedPlan = JsonTools.createSinglePricePlanJsonObject(pricePlan, dayRates);
                //
                ppj = new Gson().fromJson(focusedPlan, type);
                ArrayList<String> tabTitlesList = new ArrayList<>();
                tabTitlesList.add("Plan details");
                for (DayRateJson dr: ppj.rates) tabTitlesList.add("Rates");
                String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
                TabLayout tabLayout = findViewById(R.id.tab_layout);
                mMediator.detach();
                mMediator = new TabLayoutMediator(tabLayout, viewPager,
                        (tab, position) -> tab.setText(tabTitles[position])
                );
                mMediator.attach();
                //
                ((PricePlanViewPageAdapter)viewPager.getAdapter()).add(pos);

//                Snackbar.make(view, "Tried to add a day rate @ "+ pos, Snackbar.LENGTH_LONG)
//                            .setAction("Action", null).show();
            }
        });
    }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_plans, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("PricePlanActivity.onOptionsItemSelected");
        // Only set the return to true if the event is handled here
        // TODO: Handle the save and share
        return false;
    }

    private PricePlanViewPageAdapter createPlanAdapter(int count) {
        return new PricePlanViewPageAdapter(this, count);
    }

    long getPlanID() {
        return planID;
    }

    public String getFocusedPlan() {
        return focusedPlan;
    }

    public boolean getEdit() {
        return edit;
    }
}