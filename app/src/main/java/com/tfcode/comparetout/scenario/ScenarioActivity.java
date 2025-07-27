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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.util.EdgeInsets;
import com.tfcode.comparetout.util.GraphableActivity;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.util.InsetRespectingActivity;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScenarioActivity extends InsetRespectingActivity implements GraphableActivity {

    private ProgressBar mSimulationInProgressBar;
    private boolean mSimulationInProgress = false;

    private boolean mZoom = false;

    private static final String EDIT_KEY = "Edit";
    private static final String SCENARIO_KEY = "ScenarioID";

    ViewPager2 viewPager;
    private Long scenarioID = 0L;
    private boolean mEdit = false;
    private Menu mMenu;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;
    private MenuItem mCompareButton;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private void showCompare() {
        if (!(null == mCompareButton)) {
            mCompareButton.setVisible(viewPager.getCurrentItem() == 1);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EDIT_KEY, mEdit);
        outState.putLong(SCENARIO_KEY, scenarioID);
    }


    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyInsetsToView(R.id.scenario_tab_layout, EdgeInsets.Edge.TOP);
        applyInsetsToView(R.id.view_scenario_pager, EdgeInsets.Edge.RIGHT, EdgeInsets.Edge.BOTTOM);
        applyInsetsToGuidelines(R.id.top_inset_guideline, R.id.bottom_inset_guideline, 0, R.id.right_inset_guideline );
        if (!(null == savedInstanceState)) {
            scenarioID = savedInstanceState.getLong(SCENARIO_KEY);
            mEdit = savedInstanceState.getBoolean(EDIT_KEY);
        }
        else {
            Intent intent = getIntent();
            scenarioID = intent.getLongExtra("ScenarioID", 0L);
            mEdit = intent.getBooleanExtra("Edit", false);
        }

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                .build();

        setContentView(R.layout.activity_scenario);
        createSimulationFeedback();

        viewPager = findViewById(R.id.view_scenario_pager);

        setupViewPager();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() > 0) {
                    viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
                } else if (mUnsavedChanges && !mDoubleBackToExitPressedOnce) {
                    mDoubleBackToExitPressedOnce = true;
                    Snackbar.make(getWindow().getDecorView().getRootView(),
                                    "Unsaved changes. Please click BACK again to discard and exit", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> mDoubleBackToExitPressedOnce =false, 2000);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);

        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Usage");

        FloatingActionButton fab = findViewById(R.id.zoom_scenario);
        fab.setOnClickListener(view -> {
            ActionBar actionBar = getSupportActionBar();
            TabLayout tabLayout = findViewById(R.id.scenario_tab_layout);
            if (!(null == actionBar) && !(null == tabLayout)) {
                if (!(mZoom)) {
                    actionBar.hide();
                    tabLayout.setVisibility(View.GONE);
                    updateTopInsetTarget(tabLayout, viewPager);
                    mZoom = true;
                }
                else {
                    actionBar.show();
                    tabLayout.setVisibility(View.VISIBLE);
                    updateTopInsetTarget(tabLayout, viewPager);
                    mZoom = false;
                }}
        });
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
        mCompareButton = menu.findItem(R.id.compare);
        mCompareButton.setVisible(false);
        Objects.requireNonNull(menu.findItem(R.id.compare).getIcon()).setColorFilter(colour, PorterDuff.Mode.DST);
        mMenu.findItem(R.id.help).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        setMenuLongClick();
        return true;
    }

    private void setMenuLongClick() {
        new Handler().post(() -> {
            final View info = findViewById(R.id.info_scenario);
            if (info != null) {
                info.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/menu.html");
                    return true;
                });
            }
            final View edit_a_plan = findViewById(R.id.edit_scenario);
            if (edit_a_plan != null) {
                edit_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/menu.html");
                    return true;
                });
            }
            final View export_a_plan = findViewById(R.id.share_scenario);
            if (export_a_plan != null) {
                export_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/menu.html");
                    return true;
                });
            }
            final View save_a_plan = findViewById(R.id.save_scenario);
            if (save_a_plan != null) {
                save_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/menu.html");
                    return true;
                });
            }
            final View help = findViewById(R.id.help);
            if (help != null) {
                help.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/menu.html");
                    return true;
                });
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem infoItem = menu.findItem((R.id.info_scenario));
        infoItem.setVisible(false);
        MenuItem saveItem = menu.findItem((R.id.save_scenario));
        if (!(mEdit)) saveItem.setVisible(false);
        MenuItem editItem = menu.findItem((R.id.edit_scenario));
        if (mEdit) editItem.setVisible(false);
        setMenuLongClick();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.share_scenario) {//add the function to perform here
            if (scenarioID == 0) {
                Snackbar.make(getWindow().getDecorView().getRootView(), "Save before sharing", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
                return false;
            }

            new Thread(() -> {
                ComparisonUIViewModel viewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
                List<ScenarioComponents> scenarioComponentsList = viewModel.getAllScenariosForExport();
                ScenarioComponents scenarioComponents = null;
                for (ScenarioComponents fromList : scenarioComponentsList) {
                    if (fromList.scenario.getScenarioIndex() == scenarioID) {
                        scenarioComponents = fromList;
                        break;
                    }
                }

                List<ScenarioComponents> scenarioComponentsToShare = new ArrayList<>();
                scenarioComponentsToShare.add(scenarioComponents);

                String scenarioJsonString = JsonTools.createScenarioList(scenarioComponentsToShare);

                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, scenarioJsonString);
                sendIntent.setType("text/json");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            }).start();
            return true;
        }
        if (item.getItemId() == R.id.help) {
            showHelp("https://appassets.androidplatform.net/assets/scenario/help.html");
            return true;
        }
        return false;
    }


    private void setupViewPager() {
        viewPager.setAdapter(new ScenarioViewPageAdapter(this, 3));
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("Scenario overview");
        tabTitlesList.add("Graphs");
        tabTitlesList.add("Days");
        tabTitlesList.add("Months");
        tabTitlesList.add("Years");
        TabLayout tabLayout = findViewById(R.id.scenario_tab_layout);
        TabLayoutMediator mMediator = new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitlesList.get(position))
        );
        mMediator.attach();

        LinearLayout linearLayout = (LinearLayout)tabLayout.getChildAt(0);
        linearLayout.getChildAt(0).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/help.html");
            return true;});
        linearLayout.getChildAt(1).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/graphs_tab.html");
            return true;});
        linearLayout.getChildAt(2).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/detail_tab.html");
            return true;});
        linearLayout.getChildAt(3).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/monthly_tab.html");
            return true;});
        linearLayout.getChildAt(4).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/annual_tab.html");
            return true;});

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                showFAB();
                showCompare();
            }
        });
    }

    // FRAGMENT ACCESS METHODS
    long getScenarioID() {
        return scenarioID;
    }

    public boolean getEdit() {
        return mEdit;
    }

    public boolean isSimulationInProgress() {
        return mSimulationInProgress;
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

    // SIMULATION BAR
    private void createSimulationFeedback() {
        mSimulationInProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLargeInverse);
        ConstraintLayout constraintLayout = findViewById(R.id.activity_scenario);
        ConstraintSet set = new ConstraintSet();

        mSimulationInProgressBar.setId(View.generateViewId());  // cannot set id after add
        constraintLayout.addView(mSimulationInProgressBar,0);
        set.clone(constraintLayout);
        set.connect(mSimulationInProgressBar.getId(), ConstraintSet.BOTTOM, constraintLayout.getId(), ConstraintSet.BOTTOM, 60);
        set.connect(mSimulationInProgressBar.getId(), ConstraintSet.RIGHT, constraintLayout.getId(), ConstraintSet.RIGHT, 60);
        set.connect(mSimulationInProgressBar.getId(), ConstraintSet.LEFT, constraintLayout.getId(), ConstraintSet.LEFT, 60);
        set.applyTo(constraintLayout);
        mSimulationInProgressBar.setVisibility(View.GONE);

        observerSimulationWorker();
    }

    private void observerSimulationWorker() {
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("Simulation")
                .observe(this, workInfos -> {
                    for (WorkInfo workInfo: workInfos){
                        if ( workInfo.getState().isFinished() &&
                                ( workInfo.getTags().contains("com.tfcode.comparetout.CostingWorker" ))) {
                            mSimulationInProgressBar.setVisibility(View.GONE);
                            mSimulationInProgress = false;
                        }
                        if ( (workInfo.getState() == WorkInfo.State.ENQUEUED || workInfo.getState() == WorkInfo.State.RUNNING)
                                && ( workInfo.getTags().contains("com.tfcode.comparetout.scenario.loadprofile.GenerateMissingLoadDataWorker")
                                || workInfo.getTags().contains("com.tfcode.comparetout.scenario.SimulationWorker")
                                || workInfo.getTags().contains("com.tfcode.comparetout.CostingWorker" ))) {
                            mSimulationInProgressBar.setVisibility(View.VISIBLE);
                            mSimulationInProgress = true;
                        }
                    }
                });
    }

    private void showHelp(String url) {
        mHelpWindow.setHeight((int) (getWindow().getDecorView().getHeight()*0.6));
        mHelpWindow.setWidth(getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(viewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }

    @Override
    public String getSelectedSystemSN() {
        return String.valueOf(scenarioID);
    }

    @Override
    public void setSelectedSystemSN(String serialNumber) {

    }

    @Override
    public void hideFAB() {
        FloatingActionButton fab = findViewById(R.id.zoom_scenario);
        if (!(null == fab)) fab.hide();
    }

    private void showFAB() {
        FloatingActionButton fab = findViewById(R.id.zoom_scenario);
        if (!(null == fab))
            if ((viewPager.getCurrentItem() != 1) ) {
                fab.hide();
                if (mZoom) {
                    ActionBar actionBar = getSupportActionBar();
                    TabLayout tabLayout = findViewById(R.id.import_alpha_tab_layout);
                    if (!(null == actionBar) && !(null == tabLayout)) {
                        actionBar.show();
                        tabLayout.setVisibility(View.VISIBLE);
                        updateTopInsetTarget(tabLayout, viewPager);
                        mZoom = false;
                    }
                }
            } else {
                fab.show();
            }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            hideFAB();
        }
    }
}