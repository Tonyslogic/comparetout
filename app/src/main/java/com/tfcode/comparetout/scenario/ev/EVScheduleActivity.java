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

package com.tfcode.comparetout.scenario.ev;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import com.tfcode.comparetout.model.json.scenario.EVChargeJson;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.Scenario2EVCharge;
import com.tfcode.comparetout.scenario.ScenarioSelectDialog;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

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

public class EVScheduleActivity extends AppCompatActivity {

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

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private int mNextAddedEVChargeID = -1;

    private String mEVScheduleJsonString = "";
    private List<EVCharge> mEVSchedules;
    private Map<Integer, List<EVCharge>> mTabContents;
    private List<Long> mRemovedEVCharges;
    private Map<Long, List<String>> mLinkedEVCharges;

    final ActivityResultLauncher<String> mLoadEVChargeFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // Handle the returned Uri
                if (uri == null) return;
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<List<EVChargeJson>>() {}.getType();
                    List<EVChargeJson> evChargeBatteryJsons  = new Gson().fromJson(reader, type);
                    List<EVCharge> evChargeList = JsonTools.createEVChargeList(evChargeBatteryJsons);
                    for (EVCharge load: evChargeList) addEVCharge(load);
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

    public void setupLinkedEVCharges(int evChargeIndex) {
        if (mEVSchedules.isEmpty() || (null == mTabContents) || (null == mTabContents.get(evChargeIndex))) {
            return;
        }
        new Thread(() -> {
            if (mLinkedEVCharges == null) mLinkedEVCharges = new HashMap<>();
            for (EVCharge evCharge : mEVSchedules) {
                mLinkedEVCharges.put(evCharge.getEvChargeIndex(), mViewModel.getLinkedEVCharges(evCharge.getEvChargeIndex(), mScenarioID));
            }
        }).start();
    }

    public List<String> getLinkedScenarios(Long evChargeID) {
        if (null == mLinkedEVCharges) return null;
        return mLinkedEVCharges.get(evChargeID);
    }

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evschedule);
        createSimulationFeedback();
        createProgressBar();
        mProgressBar.setVisibility(View.VISIBLE);

        Intent intent = getIntent();
        mScenarioID = intent.getLongExtra("ScenarioID", 0L);
        String mScenarioName = intent.getStringExtra("ScenarioName");
        mEdit = intent.getBooleanExtra("Edit", false);

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                .build();

        mViewPager = findViewById(R.id.ev_charging_view_pager);

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("EV Charge (" + mScenarioName + ")");

        mFab = findViewById(R.id.addEVCharge);
        mFab.setOnClickListener(view -> addNewEVCharge());
        if (mEdit) mFab.show();
        else mFab.hide();

        mViewModel.getAllEVChargeRelations().observe(this, relations -> {
            System.out.println("Observed a change in live ev charge relations ");
            for (Scenario2EVCharge evCharge: relations) {
                if (evCharge.getScenarioID() == mScenarioID) {
                    new Thread(() -> {
                        int iCountOld = 0;
                        if (!(null == mTabContents)) iCountOld = mTabContents.size();
                        refreshEVCharges();
                        int iCountNew = mTabContents.size();
                        mMainHandler.post(() -> {if (!(null == mMediator)) refreshMediator();});
                        while (iCountOld < iCountNew) {
                            mMainHandler.post(() -> {
                                if (!(null == mViewPager.getAdapter())) {
                                    ((EVScheduleViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
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
            refreshEVCharges();
            mMainHandler.post(this::setupViewPager);
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
        }).start();

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                setupLinkedEVCharges(position);
            }
        });
    }

    private void refreshEVCharges() {
        mEVSchedules = mViewModel.getEVChargesForScenario(mScenarioID);
        sortEVChargesIntoTabs();
        Type type = new TypeToken<List<EVChargeJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<EVChargeJson> evChargeJson = JsonTools.createEVChargeJson(mEVSchedules);
        mEVScheduleJsonString =  gson.toJson(evChargeJson, type);
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            ((EVScheduleViewPageAdapter) mViewPager.getAdapter()).updateDBIndex();
    }

    private void sortEVChargesIntoTabs() {
        mTabContents = new HashMap<>();
        Integer maxKey = null;
        for (EVCharge evCharge : mEVSchedules) {
            boolean sorted = false;
            for (Map.Entry<Integer, List<EVCharge>> tabContent: mTabContents.entrySet()) {
                if (maxKey == null) maxKey = tabContent.getKey();
                else if (maxKey < tabContent.getKey()) maxKey = tabContent.getKey();
                if (tabContent.getValue().get(0) != null) {
                    if (tabContent.getValue().get(0).equalDate(evCharge)) {
                        System.out.println("Comparing " + tabContent.getValue().get(0).toString());
                        tabContent.getValue().add(evCharge);
                        sorted = true;
                        break; // stop looking in the map, exit inner loop
                    }
                }
            }
            if (!sorted){
                if (null == maxKey) maxKey = 0;
                List<EVCharge> evCharges = new ArrayList<>();
                evCharges.add(evCharge);
                mTabContents.put(maxKey, evCharges);
                maxKey++;
            }
        }
        System.out.println("Sorted " + mTabContents.size() + " from " + mEVSchedules.size() + " evCharges in DB");
    }

    private void addNewEVCharge() {
        EVCharge evCharge = new EVCharge();
        evCharge.getDays().ints.remove(1);
        evCharge.setEvChargeIndex(mNextAddedEVChargeID);
        evCharge.setName("New evCharge " + mNextAddedEVChargeID);
        mNextAddedEVChargeID--;
        mEVSchedules.add(evCharge);
        List<EVCharge> temp = new ArrayList<>();
        temp.add(evCharge);

        int tabIndex = 0;
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            tabIndex = mViewPager.getAdapter().getItemCount();
        System.out.println("Adding mTabContent: " + tabIndex);
        mTabContents.put(tabIndex, temp);
        if (!(null == mViewPager.getAdapter())) {
            ((EVScheduleViewPageAdapter) mViewPager.getAdapter()).add(tabIndex);
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator,200);
        setSaveNeeded(true);
    }

    public void getNextAddedEVChargeID(EVCharge evCharge) {
        evCharge.setEvChargeIndex(mNextAddedEVChargeID);
        mEVSchedules.add(evCharge);
        mNextAddedEVChargeID--;
    }

    private void addEVCharge(EVCharge evCharge) {
        mEVSchedules.add(evCharge);
        sortEVChargesIntoTabs();
        refreshMediator();
        if (!(null == mViewPager.getAdapter())) {
            ((EVScheduleViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
        }
        setSaveNeeded(true);
    }

    private void deleteAllEVChargesInTab() {
        int pos = mViewPager.getCurrentItem();
        if (mEVSchedules.size() > 0) {

            List<EVCharge> evChargesToBeDeleted = mTabContents.get(pos);
            if (!(null == evChargesToBeDeleted))
                for (EVCharge evChargeToBeDeleted : evChargesToBeDeleted) {
                    boolean removed = mEVSchedules.remove(evChargeToBeDeleted);
                    if (null == mRemovedEVCharges) mRemovedEVCharges = new ArrayList<>();
                    if (removed) mRemovedEVCharges.add(evChargeToBeDeleted.getEvChargeIndex());
                }

            if (!(null == mViewPager.getAdapter())) {
                ((EVScheduleViewPageAdapter) mViewPager.getAdapter()).delete(pos);
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
        Type type = new TypeToken<List<EVChargeJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<EVChargeJson> evChargeJson = JsonTools.createEVChargeJson(mEVSchedules);
        mEVScheduleJsonString =  gson.toJson(evChargeJson, type);
        TabLayout tabLayout = findViewById(R.id.ev_charging_tab_layout);
        mMediator.detach();
        try {
            mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                    (tab, position) -> tab.setText("EV Charge")
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

        LinearLayout linearLayout = (LinearLayout)tabLayout.getChildAt(0);
        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            ((View) linearLayout.getChildAt(i)).setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/help.html");
                return true;
            });
        }
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
        setMenuLongClick();
        return true;
    }

    private void setMenuLongClick() {
        new Handler().post(() -> {
            final View info = findViewById(R.id.lp_info);
            if (info != null) {
                info.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/menu.html");
                    return true;
                });
            }
            final View edit_a_plan = findViewById(R.id.lp_edit);
            if (edit_a_plan != null) {
                edit_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/menu.html");
                    return true;
                });
            }
            final View export_a_plan = findViewById(R.id.lp_share);
            if (export_a_plan != null) {
                export_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/menu.html");
                    return true;
                });
            }
            final View save_a_plan = findViewById(R.id.lp_save);
            if (save_a_plan != null) {
                save_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/menu.html");
                    return true;
                });
            }
            final View help = findViewById(R.id.lp_help);
            if (help != null) {
                help.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/menu.html");
                    return true;
                });
            }
            final View lpImport = findViewById(R.id.lp_import);
            if (lpImport != null) {
                lpImport.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/menu.html");
                    return true;
                });
            }
            final View copy = findViewById(R.id.lp_copy);
            if (copy != null) {
                copy.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/menu.html");
                    return true;
                });
            }
            final View link = findViewById(R.id.lp_link);
            if (link != null) {
                link.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_schedule/menu.html");
                    return true;
                });
            }
            final View del = findViewById(R.id.lp_delete);
            if (del != null) {
                del.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/menu.html");
                    return true;
                });
            }
        });
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
        setMenuLongClick();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        System.out.println("EVScheduleActivity.onOptionsItemSelected");

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
            sendIntent.putExtra(Intent.EXTRA_TEXT, mEVScheduleJsonString);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return true;
        }
        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
            System.out.println("Import attempt");
            mLoadEVChargeFile.launch("*/*");
            return false;
        }
        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
            System.out.println("Save attempt, saving " + mEVSchedules.size());
            mProgressBar.setVisibility(View.VISIBLE);
            if (!mSimulationInProgress) {
                new Thread(() -> {
                    if (!(null == mRemovedEVCharges))for (Long evChargeID: mRemovedEVCharges) {
                        mViewModel.deleteEVChargeFromScenario(evChargeID, mScenarioID);
                    }
                    for (EVCharge evCharge: mEVSchedules) {
                        if (evCharge.getEvChargeIndex() < 0) evCharge.setEvChargeIndex(0);
                        mViewModel.saveEVChargeForScenario(mScenarioID, evCharge);
                    }
                    mViewModel.deleteSimulationDataForScenarioID(mScenarioID);
                    mViewModel.deleteCostingDataForScenarioID(mScenarioID);
                    refreshEVCharges();
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
                            ScenarioSelectDialog.BATTERY_SHIFT,
                            fromScenarioID -> mViewModel.copyEVChargeFromScenario(fromScenarioID, mScenarioID));
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
                            fromScenarioID -> mViewModel.linkEVChargeFromScenario(fromScenarioID, mScenarioID));
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_help) {//add the function to perform here
            showHelp("https://appassets.androidplatform.net/assets/scenario/ev_schedule/help.html");
            return false;
        }
        if (item.getItemId() == R.id.lp_delete) {//add the function to perform here
            System.out.println("Delete attempt");
            deleteAllEVChargesInTab();
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
            ((EVScheduleViewPageAdapter)mViewPager.getAdapter()).setEdit(mEdit);
        }
    }

    private void setupViewPager() {
        int count = mTabContents.size();

        mViewPager.setAdapter(createPanelAdapter(count));
        mViewPager.setOffscreenPageLimit(4);
        System.out.println("setupViewPager " + count + " fragments");

        TabLayout tabLayout = findViewById(R.id.ev_charging_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText("Charge")
        );
        mMediator.attach();
    }

    private EVScheduleViewPageAdapter createPanelAdapter(int count) {
        return new EVScheduleViewPageAdapter(this, count);
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
                ((EVScheduleViewPageAdapter) mViewPager.getAdapter()).setEdit(mEdit);
            }
        }
    }

    public List<EVCharge> getEVCharges(int pos) {
        return mTabContents.get(pos);
    }

    public long getDatabaseID(int panelTabIndex) {
        return mEVSchedules.get(panelTabIndex).getEvChargeIndex();
    }

    public void deleteEVChargeAtIndex(EVCharge evCharge, int evChargeTabIndex, long evChargeID) {
        List<EVCharge> evChargesAtTab = mTabContents.get(evChargeTabIndex);
        if (!(null == evChargesAtTab) && evChargesAtTab.size() == 1) {
            deleteAllEVChargesInTab();
        }
        else if (!(null == evChargesAtTab) && evChargeID != 0) {
            for (EVCharge ls : evChargesAtTab) {
                if (ls.getEvChargeIndex() == evChargeID) {
                    System.out.println("Delete: " + ls);
                    boolean removedFromTab = evChargesAtTab.remove(evCharge);
                    boolean removedFromLeaderList = mEVSchedules.remove(evCharge);
                    if (null == mRemovedEVCharges) mRemovedEVCharges = new ArrayList<>();
                    if (removedFromTab && removedFromLeaderList) mRemovedEVCharges.add(evChargeID);
                    break;
                }
            }
            setSaveNeeded(true);
        }
    }

    public void updateEVChargeAtIndex(EVCharge evCharge, int evChargeTabIndex, long evChargeID) {

        System.out.println("From fragment: " + evCharge);
        List<EVCharge> evChargesAtTab = mTabContents.get(evChargeTabIndex);
        // Update name, days, months
        if (!(null == evChargesAtTab) && evChargeID == 0) {
            for (EVCharge evc : evChargesAtTab) {
                System.out.println("updating " + evc.getEvChargeIndex() + " using " + evCharge.getEvChargeIndex());
                evc.getMonths().months = new ArrayList<>(evCharge.getMonths().months);
                evc.getDays().ints = new ArrayList<>(evCharge.getDays().ints);
                evc.setName(evCharge.getName());
            }
        }
        // Update begin, end, stop
        if (!(null == evChargesAtTab) && evChargeID != 0) {
            for (EVCharge ls : evChargesAtTab) {
                if (ls.getEvChargeIndex() == evChargeID) {
                    System.out.println("From activity: " + ls);
                    // Nothing to do here as the state is shared
                    break;
                }
            }
        }

        List<EVChargeJson> batteryJsons = JsonTools.createEVChargeJson(mEVSchedules);
        Type type = new TypeToken<List<EVChargeJson>>() {
        }.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        mEVScheduleJsonString = gson.toJson(batteryJsons, type);
    }

    // PROGRESS BAR
    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.ev_schedule_activity);
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
        ConstraintLayout constraintLayout = findViewById(R.id.ev_schedule_activity);
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

    private void showHelp(String url) {
        mHelpWindow.setHeight((int) (getWindow().getDecorView().getHeight()*0.6));
        mHelpWindow.setWidth((int) (getWindow().getDecorView().getWidth()));
        mHelpWindow.showAtLocation(mViewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }
}