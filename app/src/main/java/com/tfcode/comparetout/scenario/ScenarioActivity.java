package com.tfcode.comparetout.scenario;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

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
    private boolean mEdit = false;
    private PricePlanNavViewModel mViewModel;
    private ActionBar mActionBar;
    private TabLayoutMediator mMediator;
    private Menu mMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        scenarioID = intent.getLongExtra("ScenarioID", 0L);
        mEdit = intent.getBooleanExtra("edit", false);
        setContentView(R.layout.activity_scenario);

        viewPager = findViewById(R.id.view_scenario_pager);

        setupViewPager();

        mViewModel = new ViewModelProvider(this).get(PricePlanNavViewModel.class);
        mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Scenario");
    }

    // MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scenarios, menu);
        mMenu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem infoItem = menu.findItem((R.id.info_scenario));
        infoItem.setVisible(false);
        MenuItem saveItem = menu.findItem((R.id.save_scenario));
        saveItem.setVisible(false);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("ScenarioActivity.onOptionsItemSelected");

        if (item.getItemId() == R.id.share_scenario) {//add the function to perform here
            System.out.println("Share attempt");
            return false;
        }
        return false;
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

    public boolean getEdit() {
        return mEdit;
    }

    public void setEdit() {
        mEdit = true;
        MenuItem editMenuItem = mMenu.findItem(R.id.edit_scenario);
        editMenuItem.setVisible(false);
        MenuItem saveMenuItem = mMenu.findItem(R.id.save_scenario);
        saveMenuItem.setVisible(true);
    }
}