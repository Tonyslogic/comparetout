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

package com.tfcode.comparetout.scenario.battery;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.LoadShiftJson;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Scenario2LoadShift;
import com.tfcode.comparetout.scenario.ScenarioSelectDialog;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BatteryChargingActivity extends AppCompatActivity {

    private Handler mMainHandler;
    private ProgressBar mProgressBar;

    private ViewPager2 mViewPager;
    private Long mScenarioID = 0L;
    private boolean mEdit = false;
    private ComparisonUIViewModel mViewModel;
    private TabLayoutMediator mMediator;
    private boolean mRetryMediator = false;
    private Menu mMenu;
    private FloatingActionButton mFab;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    private int mNextAddedLoadShiftID = -1;

    private String mLoadShiftJsonString = "";
    private List<LoadShift> mLoadShifts;
    private Map<Integer, List<LoadShift>> mTabContents;
    private List<Long> mRemovedLoadShifts;
    private Map<Long, List<String>> mLinkedLoadShifts;

    final ActivityResultLauncher<String> mLoadLoadShiftFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // Handle the returned Uri
                if (uri == null) return;
                InputStream is;
                try {
                    is = getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<List<LoadShiftJson>>() {}.getType();
                    List<LoadShiftJson> loadShiftBatteryJsons  = new Gson().fromJson(reader, type);
                    List<LoadShift> loadShiftList = JsonTools.createLoadShiftList(loadShiftBatteryJsons);
                    for (LoadShift load: loadShiftList) addLoadShift(load);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                    Snackbar.make(mViewPager.getRootView(), "Unable to load", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            });

    public void setupLinkedLoadShifts(int loadShiftIndex) {
        if (mLoadShifts.isEmpty() || (null == mTabContents) || (null == mTabContents.get(loadShiftIndex))) {
            return;
        }
        new Thread(() -> {
            if (mLinkedLoadShifts == null) mLinkedLoadShifts = new HashMap<>();
            for (LoadShift ls : mLoadShifts) {
                mLinkedLoadShifts.put(ls.getLoadShiftIndex(), mViewModel.getLinkedLoadShifts(ls.getLoadShiftIndex(), mScenarioID));
            }
        }).start();
    }

    public List<String> getLinkedScenarios(Long loadShiftID) {
        if (null == mLinkedLoadShifts) return null;
        return mLinkedLoadShifts.get(loadShiftID);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery_charging);
        createProgressBar();
        mProgressBar.setVisibility(View.VISIBLE);

        Intent intent = getIntent();
        mScenarioID = intent.getLongExtra("ScenarioID", 0L);
        String mScenarioName = intent.getStringExtra("ScenarioName");
        mEdit = intent.getBooleanExtra("Edit", false);

        mViewPager = findViewById(R.id.battery_charging_view_pager);

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Load shift (" + mScenarioName + ")");

        mFab = findViewById(R.id.addLoadShift);
        mFab.setOnClickListener(view -> addNewLoadShift());
        if (mEdit) mFab.show();
        else mFab.hide();

        mViewModel.getAllLoadShiftRelations().observe(this, relations -> {
            System.out.println("Observed a change in live load shift relations ");
            for (Scenario2LoadShift loadShift: relations) {
                if (loadShift.getScenarioID() == mScenarioID) {
                    new Thread(() -> {
                        int iCountOld = 0;
                        if (!(null == mTabContents)) iCountOld = mTabContents.size();
                        refreshLoadShifts();
                        int iCountNew = mTabContents.size();
                        mMainHandler.post(() -> {if (!(null == mMediator)) refreshMediator();});
                        while (iCountOld < iCountNew) {
                            mMainHandler.post(() -> {
                                if (!(null == mViewPager.getAdapter())) {
                                    ((BatteryChargingViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
                                }
                            });
                            iCountOld++;
                        }
                    }).start();
                    System.out.println("Refreshing the UI");
                    break;
                }
            }
        });

        new Thread(() -> {
            refreshLoadShifts();
            mMainHandler.post(this::setupViewPager);
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
        }).start();

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                setupLinkedLoadShifts(position);
            }
        });
    }

    private void refreshLoadShifts() {
        mLoadShifts = mViewModel.getLoadShiftsForScenario(mScenarioID);
        sortLoadShiftsIntoTabs();
        Type type = new TypeToken<List<LoadShiftJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<LoadShiftJson> batteryJsons = JsonTools.createLoadShiftJson(mLoadShifts);
        mLoadShiftJsonString =  gson.toJson(batteryJsons, type);
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            ((BatteryChargingViewPageAdapter) mViewPager.getAdapter()).updateDBIndex();
    }

    private void sortLoadShiftsIntoTabs() {
        mTabContents = new HashMap<>();
        Integer maxKey = null;
        for (LoadShift loadShift : mLoadShifts) {
            boolean sorted = false;
            for (Map.Entry<Integer, List<LoadShift>> tabContent: mTabContents.entrySet()) {
                if (maxKey == null) maxKey = tabContent.getKey();
                else if (maxKey < tabContent.getKey()) maxKey = tabContent.getKey();
                if (tabContent.getValue().get(0) != null) {
                    if (tabContent.getValue().get(0).equalDateAndInverter(loadShift)) {
                        System.out.println("Comparing " + tabContent.getValue().get(0).toString());
                        tabContent.getValue().add(loadShift);
                        sorted = true;
                        break; // stop looking in the map, exit inner loop
                    }
                }
            }
            if (!sorted){
                if (null == maxKey) maxKey = 0;
                List<LoadShift> loadShifts = new ArrayList<>();
                loadShifts.add(loadShift);
                mTabContents.put(maxKey, loadShifts);
                maxKey++;
            }
        }
        System.out.println("Sorted " + mTabContents.size() + " from " + mLoadShifts.size() + " loadShifts in DB");
    }

    private void addNewLoadShift() {
        LoadShift loadShift = new LoadShift();
        loadShift.getDays().ints.remove(1);
        loadShift.setLoadShiftIndex(mNextAddedLoadShiftID);
        loadShift.setName("New loadShift " + mNextAddedLoadShiftID);
        mNextAddedLoadShiftID--;
        mLoadShifts.add(loadShift);
        List<LoadShift> temp = new ArrayList<>();
        temp.add(loadShift);

        int tabIndex = 0;
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            tabIndex = mViewPager.getAdapter().getItemCount();
        System.out.println("Adding mTabContent: " + tabIndex);
        mTabContents.put(tabIndex, temp);
        if (!(null == mViewPager.getAdapter())) {
            ((BatteryChargingViewPageAdapter) mViewPager.getAdapter()).add(tabIndex);
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator,200);
        setSaveNeeded(true);
    }

    public void getNextAddedLoadShiftID(LoadShift loadShift) {
        loadShift.setLoadShiftIndex(mNextAddedLoadShiftID);
        mLoadShifts.add(loadShift);
        mNextAddedLoadShiftID--;
    }

    private void addLoadShift(LoadShift loadShift) {
        mLoadShifts.add(loadShift);
        sortLoadShiftsIntoTabs();
        refreshMediator();
        if (!(null == mViewPager.getAdapter())) {
            ((BatteryChargingViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
        }
        setSaveNeeded(true);
    }

    private void deleteAllLoadShiftsInTab() {
        int pos = mViewPager.getCurrentItem();
        if (mLoadShifts.size() > 0) {

            List<LoadShift> loadShiftsToBeDeleted = mTabContents.get(pos);
            if (!(null == loadShiftsToBeDeleted))
                for (LoadShift loadShiftToBeDeleted : loadShiftsToBeDeleted) {
                    boolean removed = mLoadShifts.remove(loadShiftToBeDeleted);
                    if (null == mRemovedLoadShifts) mRemovedLoadShifts = new ArrayList<>();
                    if (removed) mRemovedLoadShifts.add(loadShiftToBeDeleted.getLoadShiftIndex());
                }

            if (!(null == mViewPager.getAdapter())) {
                ((BatteryChargingViewPageAdapter) mViewPager.getAdapter()).delete(pos);
            }

            new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator,500);
            setSaveNeeded(true);
        }
        else {
            Snackbar.make(getWindow().getDecorView().getRootView(),
                            "Nothing to delete!", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    }

    private void refreshMediator() {
        Type type = new TypeToken<List<LoadShiftJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<LoadShiftJson> loadShiftJson = JsonTools.createLoadShiftJson(mLoadShifts);
        mLoadShiftJsonString =  gson.toJson(loadShiftJson, type);
        TabLayout tabLayout = findViewById(R.id.battery_charging_tab_layout);
        mMediator.detach();
        try {
            mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                    (tab, position) -> tab.setText("Load shift")
            );
            mRetryMediator = false;
        }
        catch (ArrayIndexOutOfBoundsException aie) {
            aie.printStackTrace();
            if (!mRetryMediator) {
                mRetryMediator = true;
                new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator,2000);
            }
            else return;
        }
        mMediator.attach();
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
        mMenu.findItem(R.id.lp_delete).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
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
        MenuItem delItem = menu.findItem((R.id.lp_delete));
        if (mEdit) delItem.setVisible(true);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("BatteryChargingActivity.onOptionsItemSelected");

        if (item.getItemId() == R.id.lp_info) {//add the function to perform here
            System.out.println("Report status");
            Snackbar.make(getWindow().getDecorView().getRootView(),
                            "Status hint", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return false;
        }
        if (item.getItemId() == R.id.lp_edit) {//add the function to perform here
            System.out.println("Edit attempt");
            enableEdit();
            return false;
        }
        if (item.getItemId() == R.id.lp_share) {//add the function to perform here
            System.out.println("Share attempt");
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, mLoadShiftJsonString);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return true;
        }
        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
            System.out.println("Import attempt");
            mLoadLoadShiftFile.launch("*/*");
            return false;
        }
        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
            System.out.println("Save attempt, saving " + mLoadShifts.size());
            mProgressBar.setVisibility(View.VISIBLE);
            new Thread(() -> {
                if (!(null == mRemovedLoadShifts))for (Long loadShiftID: mRemovedLoadShifts) {
                    mViewModel.deleteLoadShiftFromScenario(loadShiftID, mScenarioID);
                }
                for (LoadShift loadShift: mLoadShifts) {
                    if (loadShift.getLoadShiftIndex() < 0) loadShift.setLoadShiftIndex(0);
                    mViewModel.saveLoadShiftForScenario(mScenarioID, loadShift);
                }
                mViewModel.deleteSimulationDataForScenarioID(mScenarioID);
                mViewModel.deleteCostingDataForScenarioID(mScenarioID);
                refreshLoadShifts();
                mMainHandler.post(this::setupViewPager);
                mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
            }).start();
            setSaveNeeded(false);
            return false;
        }
        if (item.getItemId() == R.id.lp_copy) {//add the function to perform here
            System.out.println("Copy attempt");
            if (mUnsavedChanges) {
                Snackbar.make(getWindow().getDecorView().getRootView(),
                                "Save changes first", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return false;
            }
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(this,
                            ScenarioSelectDialog.BATTERY_SHIFT,
                            fromScenarioID -> mViewModel.copyLoadShiftFromScenario(fromScenarioID, mScenarioID));
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_link) {//add the function to perform here
            System.out.println("Link attempt");
            if (mUnsavedChanges) {
                Snackbar.make(getWindow().getDecorView().getRootView(),
                                "Save changes first", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return false;
            }
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(this,
                            ScenarioSelectDialog.BATTERY_SHIFT,
                            fromScenarioID -> mViewModel.linkLoadShiftFromScenario(fromScenarioID, mScenarioID));
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_help) {//add the function to perform here
            System.out.println("Help attempt");
            Snackbar.make(getWindow().getDecorView().getRootView(),
                            "TODO: Help", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return false;
        }
        if (item.getItemId() == R.id.lp_delete) {//add the function to perform here
            System.out.println("Delete attempt");
            deleteAllLoadShiftsInTab();
            return false;
        }
        return false;
    }

    private void enableEdit() {
        mFab.show();
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
        MenuItem delItem = mMenu.findItem(R.id.lp_delete);
        delItem.setVisible(true);
        mEdit = true;
        if (!(null == mViewPager.getAdapter())) {
            ((BatteryChargingViewPageAdapter)mViewPager.getAdapter()).setEdit(mEdit);
        }
    }

    private void setupViewPager() {
        int count = mTabContents.size();

        mViewPager.setAdapter(createPanelAdapter(count));
        mViewPager.setOffscreenPageLimit(4);
        System.out.println("setupViewPager " + count + " fragments");

        TabLayout tabLayout = findViewById(R.id.battery_charging_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText("Battery")
        );
        mMediator.attach();
    }

    private BatteryChargingViewPageAdapter createPanelAdapter(int count) {
        return new BatteryChargingViewPageAdapter(this, count);
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
        return mScenarioID;
    }

    public boolean getEdit() {
        return mEdit;
    }

    public void setSaveNeeded(boolean saveNeeded){
        mUnsavedChanges = saveNeeded;
        if (!mUnsavedChanges) {
            mFab.hide();
            MenuItem saveItem = mMenu.findItem(R.id.lp_save);
            saveItem.setVisible(false);
            MenuItem loadItem = mMenu.findItem(R.id.lp_import);
            loadItem.setVisible(false);
            MenuItem copyItem = mMenu.findItem(R.id.lp_copy);
            copyItem.setVisible(false);
            MenuItem linkItem = mMenu.findItem(R.id.lp_link);
            linkItem.setVisible(false);
            MenuItem delItem = mMenu.findItem(R.id.lp_delete);
            delItem.setVisible(false);
            MenuItem editItem = mMenu.findItem(R.id.lp_edit);
            editItem.setVisible(true);
            mEdit = false;
            if (!(null == mViewPager.getAdapter())) {
                ((BatteryChargingViewPageAdapter) mViewPager.getAdapter()).setEdit(mEdit);
            }
        }
    }

    public List<LoadShift> getLoadShifts(int pos) {
        return mTabContents.get(pos);
    }

    public long getDatabaseID(int panelTabIndex) {
        return mLoadShifts.get(panelTabIndex).getLoadShiftIndex();
    }

    public void deleteLoadShiftAtIndex(LoadShift loadShift, int loadShiftTabIndex, long loadShiftID) {
        List<LoadShift> loadShiftsAtTab = mTabContents.get(loadShiftTabIndex);
        if (!(null == loadShiftsAtTab) && loadShiftsAtTab.size() == 1) {
            deleteAllLoadShiftsInTab();
        }
        else if (!(null == loadShiftsAtTab) && loadShiftID != 0) {
            for (LoadShift ls : loadShiftsAtTab) {
                if (ls.getLoadShiftIndex() == loadShiftID) {
                    System.out.println("Delete: " + ls);
                    boolean removedFromTab = loadShiftsAtTab.remove(loadShift);
                    boolean removedFromLeaderList = mLoadShifts.remove(loadShift);
                    if (null == mRemovedLoadShifts) mRemovedLoadShifts = new ArrayList<>();
                    if (removedFromTab && removedFromLeaderList) mRemovedLoadShifts.add(loadShiftID);
                    break;
                }
            }
        }
    }

    public void updateLoadShiftAtIndex(LoadShift loadShift, int loadShiftTabIndex, long loadShiftID) {

        System.out.println("From fragment: " + loadShift);
        List<LoadShift> loadShiftsAtTab = mTabContents.get(loadShiftTabIndex);
        // Update inverter, days, months
        if (!(null == loadShiftsAtTab) && loadShiftID == 0) {
            for (LoadShift ls : loadShiftsAtTab) {
                System.out.println("updating " + ls.getLoadShiftIndex() + " using " + loadShift.getLoadShiftIndex());
                ls.getMonths().months = new ArrayList<>(loadShift.getMonths().months);
                ls.getDays().ints = new ArrayList<>(loadShift.getDays().ints);
                ls.setInverter(loadShift.getInverter());
            }
        }
        // Update begin, end, stop
        if (!(null == loadShiftsAtTab) && loadShiftID != 0) {
            for (LoadShift ls : loadShiftsAtTab) {
                if (ls.getLoadShiftIndex() == loadShiftID) {
                    System.out.println("From activity: " + ls);
                    // Nothing to do here as the state is shared
                    break;
                }
            }
        }

        List<LoadShiftJson> batteryJsons = JsonTools.createLoadShiftJson(mLoadShifts);
        Type type = new TypeToken<List<LoadShiftJson>>() {
        }.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        mLoadShiftJsonString = gson.toJson(batteryJsons, type);

    }

    // PROGRESS BAR
    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.battery_charging_activity);
        ConstraintSet set = new ConstraintSet();

        mProgressBar.setId(View.generateViewId());  // cannot set id after add
        constraintLayout.addView(mProgressBar,0);
        set.clone(constraintLayout);
        set.connect(mProgressBar.getId(), ConstraintSet.TOP, constraintLayout.getId(), ConstraintSet.TOP, 60);
        set.connect(mProgressBar.getId(), ConstraintSet.BOTTOM, constraintLayout.getId(), ConstraintSet.BOTTOM, 60);
        set.connect(mProgressBar.getId(), ConstraintSet.RIGHT, constraintLayout.getId(), ConstraintSet.RIGHT, 60);
        set.connect(mProgressBar.getId(), ConstraintSet.LEFT, constraintLayout.getId(), ConstraintSet.LEFT, 60);
        set.applyTo(constraintLayout);
        mProgressBar.setVisibility(View.GONE);

        mMainHandler = new Handler(Looper.getMainLooper());
    }
}