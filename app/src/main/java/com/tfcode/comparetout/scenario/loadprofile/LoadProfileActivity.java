package com.tfcode.comparetout.scenario.loadprofile;

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
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.scenario.ScenarioSelectDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LoadProfileActivity extends AppCompatActivity {

    ViewPager2 mViewPager;
    private Long scenarioID = 0L;
    private boolean mEdit = false;
    private ComparisonUIViewModel mViewModel;
    private Menu mMenu;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    private String mLoadProfileJson = "";
    private final String mDistributionSource = "";

    private List<String> mLinkedScenarios = new ArrayList<>();

    private void showLinkedFAB() {
        FloatingActionButton fab = findViewById(R.id.isLinked);
        fab.show();
    }

    private void hideLinkedFAB() {
        FloatingActionButton fab = findViewById(R.id.isLinked);
        fab.hide();
    }

    private void setupLinkedFAB() {
        new Thread(() -> {
            mLinkedScenarios = mViewModel.getLinkedLoadProfiles(scenarioID);
            System.out.println("setupFAB " + mLinkedScenarios);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (mLinkedScenarios.isEmpty()) hideLinkedFAB();
            });
            }).start();

        FloatingActionButton fab = findViewById(R.id.isLinked);
        fab.setOnClickListener(view -> Snackbar.make(view, "Linked to " + mLinkedScenarios, Snackbar.LENGTH_LONG)
            .setAction("Action", null).show());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        scenarioID = intent.getLongExtra("ScenarioID", 0L);
        String scenarioName = intent.getStringExtra("ScenarioName");
        mEdit = intent.getBooleanExtra("Edit", false);
        setContentView(R.layout.activity_load_profile);

        mViewPager = findViewById(R.id.load_profile_view_pager);

        setupViewPager();

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Load profile (" + scenarioName + ")");

        setupLinkedFAB();
    }

    // MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_load_profile, menu);
        mMenu = menu;
        int colour = Color.parseColor("White");
        mMenu.findItem(R.id.lp_info).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.lp_edit).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.lp_share).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.lp_save).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.lp_import).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.lp_copy).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.lp_link).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.lp_help).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem infoItem = menu.findItem((R.id.lp_info));
        infoItem.setVisible(false);
        MenuItem saveItem = menu.findItem((R.id.lp_save));
        if (!(mEdit)) saveItem.setVisible(false);
        MenuItem loadItem = menu.findItem((R.id.lp_import));
        if (!(mEdit)) loadItem.setVisible(false);
        MenuItem copyItem = menu.findItem((R.id.lp_copy));
        if (!(mEdit)) copyItem.setVisible(false);
        MenuItem linkItem = menu.findItem((R.id.lp_link));
        if (!(mEdit)) linkItem.setVisible(false);
        MenuItem editItem = menu.findItem((R.id.lp_edit));
        if (mEdit) editItem.setVisible(false);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("ScenarioActivity.onOptionsItemSelected");

        if (item.getItemId() == R.id.lp_info) {//add the function to perform here
            System.out.println("Report status");
            Snackbar.make(getWindow().getDecorView().getRootView(),
                "Status hint", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return false;
        }
        if (item.getItemId() == R.id.lp_edit) {//add the function to perform here
            System.out.println("Edit attempt");
            MenuItem saveItem = mMenu.findItem(R.id.lp_save);
            saveItem.setVisible(true);
            MenuItem loadItem = mMenu.findItem(R.id.lp_import);
            loadItem.setVisible(true);
            MenuItem copyItem = mMenu.findItem(R.id.lp_copy);
            copyItem.setVisible(true);
            MenuItem linkItem = mMenu.findItem(R.id.lp_link);
            linkItem.setVisible(true);
            MenuItem editItem = mMenu.findItem(R.id.lp_edit);
            editItem.setVisible(false);
            mEdit = true;
            ((LoadProfileViewPageAdapter)mViewPager.getAdapter()).setEdit(mEdit);
            return false;
        }
        if (item.getItemId() == R.id.lp_share) {//add the function to perform here
            System.out.println("Share attempt");

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, mLoadProfileJson);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);

            return true;
        }
//        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
//            System.out.println("Import attempt");
//
//            mLoadLoadProfileFile.launch("*/*");
//
//            return true;
//        }
//        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
//            System.out.println("Save attempt");
//            return false;
//        }
        if (item.getItemId() == R.id.lp_copy) {//add the function to perform here
            System.out.println("Copy attempt");
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(LoadProfileActivity.this,
                            ScenarioSelectDialog.LOAD_PROFILE,
                            fromScenarioID -> mViewModel.copyLoadProfileFromScenario(fromScenarioID, scenarioID));
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_link) {//add the function to perform here
            System.out.println("Link attempt");
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(LoadProfileActivity.this,
                            ScenarioSelectDialog.LOAD_PROFILE,
                            fromScenarioID -> {
                                mViewModel.linkLoadProfileFromScenario(fromScenarioID, scenarioID);
                                showLinkedFAB();
                    });
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_help) {//add the function to perform here
            System.out.println("Help attempt");
            Snackbar.make(getWindow().getDecorView().getRootView(),
                "Help attempt", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
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
        TabLayoutMediator mMediator = new TabLayoutMediator(tabLayout, mViewPager,
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
        Snackbar.make(getWindow().getDecorView().getRootView(),
            "Unsaved changes. Please click BACK again to discard and exit", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();

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
            MenuItem loadItem = mMenu.findItem(R.id.lp_import);
            loadItem.setVisible(false);
            MenuItem copyItem = mMenu.findItem(R.id.lp_copy);
            copyItem.setVisible(false);
            MenuItem linkItem = mMenu.findItem(R.id.lp_link);
            linkItem.setVisible(false);
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