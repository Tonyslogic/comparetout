package com.tfcode.comparetout.scenario.loadprofile;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.scenario.ScenarioViewPageAdapter;

import java.util.ArrayList;
import java.util.Objects;

public class LoadProfileActivity extends AppCompatActivity {

    ViewPager2 viewPager;
    private Long scenarioID = 0L;
    private String scenarioName = "";
    private boolean mEdit = false;
//    private PricePlanNavViewModel mViewModel;
    private ActionBar mActionBar;
    private TabLayoutMediator mMediator;
    private Menu mMenu;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        scenarioID = intent.getLongExtra("ScenarioID", 0L);
        scenarioName = intent.getStringExtra("ScenarioName");
        mEdit = intent.getBooleanExtra("Edit", false);
        setContentView(R.layout.activity_load_profile);

        viewPager = findViewById(R.id.load_profile_view_pager);

        setupViewPager();
//
//        mViewModel = new ViewModelProvider(this).get(PricePlanNavViewModel.class);
        mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Load profile (" + scenarioName + ")");
    }

    private void setupViewPager() {
        viewPager.setAdapter(new LoadProfileViewPageAdapter(this, 4));
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("Profile");
        tabTitlesList.add("Daily");
        tabTitlesList.add("Monthly");
        tabTitlesList.add("Hourly");
        String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
        TabLayout tabLayout = findViewById(R.id.loadProfile_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
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
        Toast.makeText(this, "Unsaved changes. Please click BACK again to discard and exit", Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { mDoubleBackToExitPressedOnce =false;}}, 2000);
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
//        MenuItem editMenuItem = mMenu.findItem(R.id.edit_scenario);
//        editMenuItem.setVisible(false);
//        MenuItem saveMenuItem = mMenu.findItem(R.id.save_scenario);
//        saveMenuItem.setVisible(true);
    }

    public void setSaveNeeded(boolean saveNeeded){
        mUnsavedChanges = saveNeeded;
    }
}