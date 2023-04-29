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

package com.tfcode.comparetout.scenario;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.R;

import java.util.ArrayList;
import java.util.Objects;

public class ScenarioActivity extends AppCompatActivity {

    private static final String EDIT_KEY = "Edit";
    private static final String SCENARIO_KEY = "ScenarioID";

    ViewPager2 viewPager;
    private Long scenarioID = 0L;
    private boolean mEdit = false;
    private Menu mMenu;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EDIT_KEY, mEdit);
        outState.putLong(SCENARIO_KEY, scenarioID);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!(null == savedInstanceState)) {
            scenarioID = savedInstanceState.getLong(SCENARIO_KEY);
            mEdit = savedInstanceState.getBoolean(EDIT_KEY);
        }
        else {
            Intent intent = getIntent();
            scenarioID = intent.getLongExtra("ScenarioID", 0L);
            mEdit = intent.getBooleanExtra("Edit", false);
        }
        setContentView(R.layout.activity_scenario);

        viewPager = findViewById(R.id.view_scenario_pager);

        setupViewPager();

        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Scenario");
    }

    // MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scenarios, menu);
        mMenu = menu;
        int colour = Color.parseColor("White");
        mMenu.findItem(R.id.edit_scenario).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.share_scenario).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.info_scenario).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.save_scenario).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem infoItem = menu.findItem((R.id.info_scenario));
        infoItem.setVisible(false);
        MenuItem saveItem = menu.findItem((R.id.save_scenario));
        if (!(mEdit)) saveItem.setVisible(false);
        MenuItem editItem = menu.findItem((R.id.edit_scenario));
        if (mEdit) editItem.setVisible(false);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("ScenarioActivity.onOptionsItemSelected");

        if (item.getItemId() == R.id.share_scenario) {//add the function to perform here
            System.out.println("Share attempt");
            Snackbar.make(getWindow().getDecorView().getRootView(), "TODO: Share", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return false;
        }
        return false;
    }


    private void setupViewPager() {
        viewPager.setAdapter(new ScenarioViewPageAdapter(this, 2));
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("Scenario overview");
        tabTitlesList.add("Daily details");
        TabLayout tabLayout = findViewById(R.id.scenario_tab_layout);
        TabLayoutMediator mMediator = new TabLayoutMediator(tabLayout, viewPager,
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
                "Unsaved changes. Please click BACK again to discard and exit",
                     Snackbar.LENGTH_LONG).setAction("Action", null).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> mDoubleBackToExitPressedOnce =false, 2000);
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

    public void setSaveNeeded(boolean saveNeeded){
        mUnsavedChanges = saveNeeded;
        if (!saveNeeded){
            MenuItem editMenuItem = mMenu.findItem(R.id.edit_scenario);
            editMenuItem.setVisible(true);
            MenuItem saveMenuItem = mMenu.findItem(R.id.save_scenario);
            saveMenuItem.setVisible(false);
        }
    }
}