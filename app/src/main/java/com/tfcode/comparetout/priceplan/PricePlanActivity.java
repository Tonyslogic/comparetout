package com.tfcode.comparetout.priceplan;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.DayRate;
import com.tfcode.comparetout.model.PricePlan;
import com.tfcode.comparetout.model.json.JsonTools;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_price_plan);
        viewPager = findViewById(R.id.view_plan_pager);
        viewPager.setAdapter(createPlanAdapter());

        mViewModel = new ViewModelProvider(this).get(PricePlanNavViewModel.class);
        Intent intent = getIntent();
        edit = intent.getBooleanExtra("Edit", false);
        planID = intent.getLongExtra("PlanID", 0L);
        focusedPlan = intent.getStringExtra("Focus");
        Objects.requireNonNull(getSupportActionBar()).setTitle("Price plan details");
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

    private PricePlanViewPageAdapter createPlanAdapter() {
        PricePlanViewPageAdapter ppa =  new PricePlanViewPageAdapter(this);
//        ppa.setDayRateCount(5);
        return ppa;
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