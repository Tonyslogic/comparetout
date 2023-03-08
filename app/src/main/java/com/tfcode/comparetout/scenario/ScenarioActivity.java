package com.tfcode.comparetout.scenario;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.priceplan.PricePlanViewPageAdapter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Objects;

public class ScenarioActivity extends AppCompatActivity {

    ViewPager2 viewPager;
    private Long scenarioID = 0L;
    private PricePlanNavViewModel mViewModel;
    private ActionBar mActionBar;
    private TabLayoutMediator mMediator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        scenarioID = intent.getLongExtra("ScenarioID", 0L);
        setContentView(R.layout.activity_scenario);

        viewPager = findViewById(R.id.view_scenario_pager);

        setupViewPager();

        mViewModel = new ViewModelProvider(this).get(PricePlanNavViewModel.class);
        mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Scenario");
    }
    private void setupViewPager() {
        viewPager.setAdapter(new ScenarioViewPageAdapter(this, 2));
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("Scenario overview");
        tabTitlesList.add("Scenario details");
        String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
        TabLayout tabLayout = findViewById(R.id.scenario_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        );
        mMediator.attach();
    }

    // FRAGMENT ACCESS METHODS
    long getScenarioID() {
        return scenarioID;
    }
}