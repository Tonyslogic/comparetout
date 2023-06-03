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

package com.tfcode.comparetout.scenario.water;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

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
import com.tfcode.comparetout.model.json.scenario.HWScheduleJson;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.Scenario2HWSchedule;
import com.tfcode.comparetout.scenario.ScenarioSelectDialog;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WaterScheduleActivity extends AppCompatActivity {

    private Handler mMainHandler;
    private ProgressBar mProgressBar;
    private ProgressBar mSimulationInProgressBar;
    private boolean mSimulationInProgress = false;

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

    private int mNextAddedScheduleID = -1;

    private String mHWScheduleJsonString = "";
    private List<HWSchedule> mHWSchedules;
    private Map<Integer, List<HWSchedule>> mTabContents;
    private List<Long> mRemovedHWSchedules;
    private Map<Long, List<String>> mLinkedHWSchedules;

    final ActivityResultLauncher<String> mHWSchedulesFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // Handle the returned Uri
                if (uri == null) return;
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<List<HWScheduleJson>>() {}.getType();
                    List<HWScheduleJson> hWScheduleJsons  = new Gson().fromJson(reader, type);
                    List<HWSchedule> scheduleList = JsonTools.createHWScheduleList(hWScheduleJsons);
                    for (HWSchedule hwSchedule: scheduleList) addHWSchedule(hwSchedule);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                    Snackbar.make(mViewPager.getRootView(), "Unable to load", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }finally {
                    if (!(null == is)) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

    public void setupLinkedHWSchedules(int hwScheduleIndex) {
        if (mHWSchedules.isEmpty() || (null == mTabContents) || (null == mTabContents.get(hwScheduleIndex))) {
            return;
        }
        new Thread(() -> {
            if (mLinkedHWSchedules == null) mLinkedHWSchedules = new HashMap<>();
            for (HWSchedule hwSchedule : mHWSchedules) {
                mLinkedHWSchedules.put(hwSchedule.getHwScheduleIndex(), mViewModel.getLinkedHWSchedules(hwSchedule.getHwScheduleIndex(), mScenarioID));
            }
        }).start();
    }

    public List<String> getLinkedScenarios(Long hwScheduleID) {
        if (null == mLinkedHWSchedules) return null;
        return mLinkedHWSchedules.get(hwScheduleID);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water_schedule);
        createSimulationFeedback();
        createProgressBar();
        mProgressBar.setVisibility(View.VISIBLE);

        Intent intent = getIntent();
        mScenarioID = intent.getLongExtra("ScenarioID", 0L);
        String mScenarioName = intent.getStringExtra("ScenarioName");
        mEdit = intent.getBooleanExtra("Edit", false);

        mViewPager = findViewById(R.id.water_schedule_view_pager);

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Hot water schedule (" + mScenarioName + ")");

        mFab = findViewById(R.id.addSchedule);
        mFab.setOnClickListener(view -> addNewHWSchedule());
        if (mEdit) mFab.show();
        else mFab.hide();

        mViewModel.getAllHWScheduleRelations().observe(this, relations -> {
            System.out.println("Observed a change in live hot water schedule relations ");
            for (Scenario2HWSchedule hwSchedule: relations) {
                if (hwSchedule.getScenarioID() == mScenarioID) {
                    new Thread(() -> {
                        int iCountOld = 0;
                        if (!(null == mTabContents)) iCountOld = mTabContents.size();
                        refreshHWSchedules();
                        int iCountNew = mTabContents.size();
                        mMainHandler.post(() -> {if (!(null == mMediator)) refreshMediator();});
                        while (iCountOld < iCountNew) {
                            mMainHandler.post(() -> {
                                if (!(null == mViewPager.getAdapter())) {
                                    ((WaterScheduleViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
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
            refreshHWSchedules();
            mMainHandler.post(this::setupViewPager);
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
        }).start();

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                setupLinkedHWSchedules(position);
            }
        });
    }

    private void refreshHWSchedules() {
        mHWSchedules = mViewModel.getHWSchedulesForScenario(mScenarioID);
        sortHWSchedulesIntoTabs();
        Type type = new TypeToken<List<HWScheduleJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<HWScheduleJson> hwScheduleJsons = JsonTools.createHWScheduleJson(mHWSchedules);
        mHWScheduleJsonString =  gson.toJson(hwScheduleJsons, type);
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            ((WaterScheduleViewPageAdapter) mViewPager.getAdapter()).updateDBIndex();
    }

    private void sortHWSchedulesIntoTabs() {
        mTabContents = new HashMap<>();
        Integer maxKey = null;
        for (HWSchedule hwSchedule : mHWSchedules) {
            boolean sorted = false;
            for (Map.Entry<Integer, List<HWSchedule>> tabContent: mTabContents.entrySet()) {
                if (maxKey == null) maxKey = tabContent.getKey();
                else if (maxKey < tabContent.getKey()) maxKey = tabContent.getKey();
                if (tabContent.getValue().get(0) != null) {
                    if (tabContent.getValue().get(0).equalDate(hwSchedule)) {
                        System.out.println("Comparing " + tabContent.getValue().get(0).toString());
                        tabContent.getValue().add(hwSchedule);
                        sorted = true;
                        break; // stop looking in the map, exit inner loop
                    }
                }
            }
            if (!sorted){
                if (null == maxKey) maxKey = 0;
                List<HWSchedule> hwSchedules = new ArrayList<>();
                hwSchedules.add(hwSchedule);
                mTabContents.put(maxKey, hwSchedules);
                maxKey++;
            }
        }
        System.out.println("Sorted " + mTabContents.size() + " from " + mHWSchedules.size() + " hot water schedules in DB");
    }

    private void addNewHWSchedule() {
        HWSchedule hwSchedule = new HWSchedule();
        hwSchedule.getDays().ints.remove(1);
        hwSchedule.setHwScheduleIndex(mNextAddedScheduleID);
        hwSchedule.setName("New hwSchedule " + mNextAddedScheduleID);
        mNextAddedScheduleID--;
        mHWSchedules.add(hwSchedule);
        List<HWSchedule> temp = new ArrayList<>();
        temp.add(hwSchedule);

        int tabIndex = 0;
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            tabIndex = mViewPager.getAdapter().getItemCount();
        System.out.println("Adding mTabContent: " + tabIndex);
        mTabContents.put(tabIndex, temp);
        if (!(null == mViewPager.getAdapter())) {
            ((WaterScheduleViewPageAdapter) mViewPager.getAdapter()).add(tabIndex);
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator,200);
        setSaveNeeded(true);
    }

    public void getNextAddedHWScheduleID(HWSchedule hwSchedule) {
        hwSchedule.setHwScheduleIndex(mNextAddedScheduleID);
        mHWSchedules.add(hwSchedule);
        mNextAddedScheduleID--;
    }

    private void addHWSchedule(HWSchedule hwSchedule) {
        mHWSchedules.add(hwSchedule);
        sortHWSchedulesIntoTabs();
        refreshMediator();
        if (!(null == mViewPager.getAdapter())) {
            ((WaterScheduleViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
        }
        setSaveNeeded(true);
    }

    private void deleteAllHWSchedulesInTab() {
        int pos = mViewPager.getCurrentItem();
        if (mHWSchedules.size() > 0) {

            List<HWSchedule> hwSchedulesToBeDeleted = mTabContents.get(pos);
            if (!(null == hwSchedulesToBeDeleted))
                for (HWSchedule hwScheduleToBeDeleted : hwSchedulesToBeDeleted) {
                    boolean removed = mHWSchedules.remove(hwScheduleToBeDeleted);
                    if (null == mRemovedHWSchedules) mRemovedHWSchedules = new ArrayList<>();
                    if (removed) mRemovedHWSchedules.add(hwScheduleToBeDeleted.getHwScheduleIndex());
                }

            if (!(null == mViewPager.getAdapter())) {
                ((WaterScheduleViewPageAdapter) mViewPager.getAdapter()).delete(pos);
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
        Type type = new TypeToken<List<HWScheduleJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<HWScheduleJson> loadShiftJson = JsonTools.createHWScheduleJson(mHWSchedules);
        mHWScheduleJsonString =  gson.toJson(loadShiftJson, type);
        TabLayout tabLayout = findViewById(R.id.water_schedule_tab_layout);
        mMediator.detach();
        try {
            mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                    (tab, position) -> tab.setText("Schedule")
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
        System.out.println("WaterScheduleActivity.onOptionsItemSelected");

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
            sendIntent.putExtra(Intent.EXTRA_TEXT, mHWScheduleJsonString);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return true;
        }
        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
            System.out.println("Import attempt");
            mHWSchedulesFile.launch("*/*");
            return false;
        }
        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
            System.out.println("Save attempt, saving " + mHWSchedules.size());
            mProgressBar.setVisibility(View.VISIBLE);
            if (!mSimulationInProgress) {
                new Thread(() -> {
                    if (!(null == mRemovedHWSchedules))for (Long hwScheduleID: mRemovedHWSchedules) {
                        mViewModel.deleteHWScheduleFromScenario(hwScheduleID, mScenarioID);
                    }
                    for (HWSchedule hwSchedule: mHWSchedules) {
                        if (hwSchedule.getHwScheduleIndex() < 0) hwSchedule.setHwScheduleIndex(0);
                        mViewModel.saveHWScheduleForScenario(mScenarioID, hwSchedule);
                    }
                    mViewModel.deleteSimulationDataForScenarioID(mScenarioID);
                    mViewModel.deleteCostingDataForScenarioID(mScenarioID);
                    refreshHWSchedules();
                    mMainHandler.post(this::setupViewPager);
                    mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                }).start();
                setSaveNeeded(false);
            }
            else {
                mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                Snackbar.make(getWindow().getDecorView().getRootView(),
                                "Cannot save during simulation. Try again in a moment.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
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
                            ScenarioSelectDialog.TANK_SHIFT,
                            fromScenarioID -> mViewModel.copyHWScheduleFromScenario(fromScenarioID, mScenarioID));
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
                            ScenarioSelectDialog.TANK_SHIFT,
                            fromScenarioID -> mViewModel.linkHWScheduleFromScenario(fromScenarioID, mScenarioID));
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
            deleteAllHWSchedulesInTab();
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
            ((WaterScheduleViewPageAdapter)mViewPager.getAdapter()).setEdit(mEdit);
        }
    }

    private void setupViewPager() {
        int count = mTabContents.size();

        mViewPager.setAdapter(createPanelAdapter(count));
        mViewPager.setOffscreenPageLimit(4);
        System.out.println("setupViewPager " + count + " fragments");

        TabLayout tabLayout = findViewById(R.id.water_schedule_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText("Schedule")
        );
        mMediator.attach();
    }

    private WaterScheduleViewPageAdapter createPanelAdapter(int count) {
        return new WaterScheduleViewPageAdapter(this, count);
    }

    @Override
    public void onBackPressed() {
        if (mViewPager.getCurrentItem() == 0) {
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
        else mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
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
                ((WaterScheduleViewPageAdapter) mViewPager.getAdapter()).setEdit(mEdit);
            }
        }
    }

    public List<HWSchedule> getHWSchedules(int pos) {
        return mTabContents.get(pos);
    }

    public long getDatabaseID(int panelTabIndex) {
        return mHWSchedules.get(panelTabIndex).getHwScheduleIndex();
    }

    public void deleteHWScheduleAtIndex(HWSchedule loadShift, int hwScheduleTabIndex, long hwScheduleID) {
        List<HWSchedule> hwSchedulesAtTab = mTabContents.get(hwScheduleTabIndex);
        if (!(null == hwSchedulesAtTab) && hwSchedulesAtTab.size() == 1) {
            deleteAllHWSchedulesInTab();
        }
        else if (!(null == hwSchedulesAtTab) && hwScheduleID != 0) {
            for (HWSchedule hws : hwSchedulesAtTab) {
                if (hws.getHwScheduleIndex() == hwScheduleID) {
                    System.out.println("Delete: " + hws);
                    boolean removedFromTab = hwSchedulesAtTab.remove(loadShift);
                    boolean removedFromLeaderList = mHWSchedules.remove(loadShift);
                    if (null == mRemovedHWSchedules) mRemovedHWSchedules = new ArrayList<>();
                    if (removedFromTab && removedFromLeaderList) mRemovedHWSchedules.add(hwScheduleID);
                    break;
                }
            }
            setSaveNeeded(true);
        }
    }

    public void updateHWScheduleAtIndex(HWSchedule hwSchedule, int hwScheduleTabIndex, long hwScheduleID) {

        System.out.println("From fragment: " + hwSchedule);
        List<HWSchedule> hwSchedulesAtTab = mTabContents.get(hwScheduleTabIndex);
        // Update days, months
        if (!(null == hwSchedulesAtTab) && hwScheduleID == 0) {
            for (HWSchedule hws : hwSchedulesAtTab) {
                System.out.println("updating " + hws.getHwScheduleIndex() + " using " + hwSchedule.getHwScheduleIndex());
                hws.getMonths().months = new ArrayList<>(hwSchedule.getMonths().months);
                hws.getDays().ints = new ArrayList<>(hwSchedule.getDays().ints);
            }
        }
        // Update begin, end, stop
        if (!(null == hwSchedulesAtTab) && hwScheduleID != 0) {
            for (HWSchedule hws : hwSchedulesAtTab) {
                if (hws.getHwScheduleIndex() == hwScheduleID) {
                    System.out.println("From activity: " + hws);
                    // Nothing to do here as the state is shared
                    break;
                }
            }
        }

        List<HWScheduleJson> hwScheduleJsons = JsonTools.createHWScheduleJson(mHWSchedules);
        Type type = new TypeToken<List<HWScheduleJson>>() {}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        mHWScheduleJsonString = gson.toJson(hwScheduleJsons, type);
    }

    // PROGRESS BAR
    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.water_schedule_activity);
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

    // SIMULATION BAR
    private void createSimulationFeedback() {
        mSimulationInProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLargeInverse);
        ConstraintLayout constraintLayout = findViewById(R.id.water_schedule_activity);
        ConstraintSet set = new ConstraintSet();

        mSimulationInProgressBar.setId(View.generateViewId());  // cannot set id after add
        constraintLayout.addView(mSimulationInProgressBar,0);
        set.clone(constraintLayout);
        set.connect(mSimulationInProgressBar.getId(), ConstraintSet.BOTTOM, constraintLayout.getId(), ConstraintSet.BOTTOM, 60);
        set.connect(mSimulationInProgressBar.getId(), ConstraintSet.RIGHT, constraintLayout.getId(), ConstraintSet.RIGHT, 60);
        set.connect(mSimulationInProgressBar.getId(), ConstraintSet.LEFT, constraintLayout.getId(), ConstraintSet.LEFT, 60);
        set.applyTo(constraintLayout);
        mSimulationInProgressBar.setVisibility(View.GONE);

        mMainHandler = new Handler(Looper.getMainLooper());
        observerSimulationWorker();
    }

    private void observerSimulationWorker() {
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("Simulation")
                .observe(this, workInfos -> {
                    System.out.println("Observing simulation change " + workInfos.size());
                    for (WorkInfo workInfo: workInfos){
                        if ( workInfo.getState().isFinished() &&
                                ( workInfo.getTags().contains("com.tfcode.comparetout.CostingWorker" ))) {
                            System.out.println(workInfo.getTags().iterator().next());
                            mSimulationInProgressBar.setVisibility(View.GONE);
                            mSimulationInProgress = false;
                        }
                        if ( (workInfo.getState() == WorkInfo.State.ENQUEUED || workInfo.getState() == WorkInfo.State.RUNNING)
                                && ( workInfo.getTags().contains("com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker")
                                || workInfo.getTags().contains("com.tfcode.comparetout.scenario.SimulationWorker")
                                || workInfo.getTags().contains("com.tfcode.comparetout.CostingWorker" ))) {
                            System.out.println(workInfo.getTags().iterator().next());
                            mSimulationInProgressBar.setVisibility(View.VISIBLE);
                            mSimulationInProgress = true;
                        }
                    }
                });
    }

}