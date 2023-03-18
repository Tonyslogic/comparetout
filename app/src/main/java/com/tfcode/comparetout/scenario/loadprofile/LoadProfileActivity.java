package com.tfcode.comparetout.scenario.loadprofile;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.scenario.SimulationWorker;

import java.util.ArrayList;
import java.util.Objects;

public class LoadProfileActivity extends AppCompatActivity {

    ViewPager2 mViewPager;
    private Long scenarioID = 0L;
    private String scenarioName = "";
    private boolean mEdit = false;
//    private ComparisonUIViewModel mViewModel;
    private ActionBar mActionBar;
    private TabLayoutMediator mMediator;
    private Menu mMenu;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    private String mLoadProfileJson = "";
    private String mDistributionSource = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        scenarioID = intent.getLongExtra("ScenarioID", 0L);
        scenarioName = intent.getStringExtra("ScenarioName");
        mEdit = intent.getBooleanExtra("Edit", false);
        setContentView(R.layout.activity_load_profile);

        mViewPager = findViewById(R.id.load_profile_view_pager);

        setupViewPager();
//
//        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Load profile (" + scenarioName + ")");
    }

    // MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_load_profile, menu);
        mMenu = menu;
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem infoItem = menu.findItem((R.id.lp_info));
        infoItem.setVisible(false);
        MenuItem saveItem = menu.findItem((R.id.lp_save));
        if (!(mEdit)) saveItem.setVisible(false);
        MenuItem editItem = menu.findItem((R.id.lp_edit));
        if (mEdit) editItem.setVisible(false);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("ScenarioActivity.onOptionsItemSelected");

        if (item.getItemId() == R.id.lp_info) {//add the function to perform here
            System.out.println("Report status");
            Toast.makeText(this, "Status hint", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (item.getItemId() == R.id.lp_edit) {//add the function to perform here
            System.out.println("Edit attempt");
            MenuItem saveItem = mMenu.findItem(R.id.lp_save);
            saveItem.setVisible(true);
            MenuItem editItem = mMenu.findItem(R.id.lp_edit);
            editItem.setVisible(false);
            mEdit = true;
            ((LoadProfileViewPageAdapter)mViewPager.getAdapter()).setEdit(mEdit);
            return false;
        }
        if (item.getItemId() == R.id.lp_share) {//add the function to perform here
            System.out.println("Share attempt");
            Toast.makeText(this, "TODO: Share", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
            System.out.println("Import attempt");
            Toast.makeText(this, "TODO: Import", Toast.LENGTH_SHORT).show();
            return false;
        }
//        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
//            System.out.println("Save attempt");
//            Toast.makeText(this, "TODO: Save", Toast.LENGTH_SHORT).show();
//            return false;
//        }
        if (item.getItemId() == R.id.lp_copy) {//add the function to perform here
            System.out.println("Copy attempt");
            Toast.makeText(this, "TODO: Copy", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (item.getItemId() == R.id.lp_link) {//add the function to perform here
            System.out.println("Link attempt");
            Toast.makeText(this, "TODO: Link", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (item.getItemId() == R.id.lp_help) {//add the function to perform here
            System.out.println("Help attempt");
            Toast.makeText(this, "TODO: Help", Toast.LENGTH_SHORT).show();
            return false;
        }
        return false;
    }

    // VIEW PAGER
    private void setupViewPager() {
        mViewPager.setAdapter(new LoadProfileViewPageAdapter(this, 4));
        mViewPager.setOffscreenPageLimit(4);
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("Profile");
        tabTitlesList.add("Daily");
        tabTitlesList.add("Monthly");
        tabTitlesList.add("Hourly");
        String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
        TabLayout tabLayout = findViewById(R.id.loadProfile_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        );
        mMediator.attach();
    }

    @Override
    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce || !(mUnsavedChanges)) {
            super.onBackPressed();
            SimulatorLauncher.simulateIfNeeded(getApplicationContext());
            return;
        }

        this.mDoubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Unsaved changes. Please click BACK again to discard and exit", Toast.LENGTH_SHORT).show();

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
    }

    public void setSaveNeeded(boolean saveNeeded){
        mUnsavedChanges = saveNeeded;
        if (!mUnsavedChanges) {
            MenuItem saveItem = mMenu.findItem(R.id.lp_save);
            saveItem.setVisible(false);
            MenuItem editItem = mMenu.findItem(R.id.lp_edit);
            editItem.setVisible(true);
            mEdit = false;
            ((LoadProfileViewPageAdapter) mViewPager.getAdapter()).setEdit(mEdit);
        }
    }

    public String getLoadProfileJson() {
        return mLoadProfileJson;
    }

    public void setLoadProfileJson(String mLoadProfileJson) {
        this.mLoadProfileJson = mLoadProfileJson;
    }

    public void propagateDistribution() {
        ((LoadProfileViewPageAdapter) mViewPager.getAdapter()).updateDistributionFromLeader();
    }
}