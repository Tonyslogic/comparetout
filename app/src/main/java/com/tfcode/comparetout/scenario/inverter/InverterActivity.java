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

package com.tfcode.comparetout.scenario.inverter;

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

import androidx.activity.OnBackPressedCallback;
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
import com.tfcode.comparetout.model.json.scenario.InverterJson;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.Scenario2Inverter;
import com.tfcode.comparetout.scenario.ScenarioSelectDialog;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InverterActivity extends AppCompatActivity {

    private Handler mMainHandler;
    private ProgressBar mProgressBar;
    private ProgressBar mSimulationInProgressBar;
    private boolean mSimulationInProgress = false;

    private ViewPager2 mViewPager;
    private Long mScenarioID = 0L;
    private boolean mEdit = false;
    private ComparisonUIViewModel mViewModel;
    private TabLayoutMediator mMediator;
    private Menu mMenu;
    private FloatingActionButton mFab;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private String mInvertersJsonString = "";
    private List<Inverter> mInverters;
    private List<Long> mRemovedInverters;

    private List<String> mLinkedScenarios = new ArrayList<>();

    final ActivityResultLauncher<String> mLoadInverterFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // Handle the returned Uri
                if (uri == null) return;
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<List<InverterJson>>() {}.getType();
                    List<InverterJson> inverterJsons  = new Gson().fromJson(reader, type);
                    List<Inverter> inverters = JsonTools.createInverterList(inverterJsons);
                    for (Inverter inverter: inverters) addInverter(inverter);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
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

    public void showLinkedFAB() {
        FloatingActionButton fab = findViewById(R.id.isLinked);
        fab.show();
    }

    public void hideLinkedFAB() {
        FloatingActionButton fab = findViewById(R.id.isLinked);
        fab.hide();
    }

    public void setupLinkedFAB(int inverterIndex) {
        if (mInverters.isEmpty()) {
            hideLinkedFAB();
            return;
        }
        Inverter toCheck = mInverters.get(inverterIndex);
        new Thread(() -> {
            mLinkedScenarios = mViewModel.getLinkedInverters(toCheck.getInverterIndex(), mScenarioID);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (mLinkedScenarios.isEmpty()) hideLinkedFAB();
                else showLinkedFAB();
            });
        }).start();

        FloatingActionButton fab = findViewById(R.id.isLinked);
        fab.setOnClickListener(view -> Snackbar.make(view, "Linked to " + mLinkedScenarios, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());
    }

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_inverter);
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

        setContentView(R.layout.activity_inverter);

        mViewPager = findViewById(R.id.inverter_view_pager);

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Inverters (" + mScenarioName + ")");

        mFab = findViewById(R.id.addInverter);
        mFab.setOnClickListener(view -> addNewInverter());
        if (mEdit) mFab.show();
        else mFab.hide();

        mViewModel.getAllInverterRelations().observe(this, relations -> {
            for (Scenario2Inverter scenario2Inverter: relations) {
                if (scenario2Inverter.getScenarioID() == mScenarioID) {
                    new Thread(() -> {
                        int iCountOld = mInverters.size();
                        refreshInverters();
                        int iCountNew = mInverters.size();
                        mMainHandler.post(() -> {if (!(null == mMediator)) refreshMediator();});
                        while (iCountOld < iCountNew) {
                            mMainHandler.post(() -> {
                                if (!(null == mViewPager.getAdapter())) {
                                    ((InverterViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
                                }
                            });
                            iCountOld++;
                        }
                    }).start();
                    break;
                }
            }
        });

        new Thread(() -> {
            refreshInverters();
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
                setupLinkedFAB(position);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mViewPager.getCurrentItem() > 0) {
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
                } else if (mUnsavedChanges && !mDoubleBackToExitPressedOnce) {
                    mDoubleBackToExitPressedOnce = true;
                    Snackbar.make(getWindow().getDecorView().getRootView(),
                                    "Unsaved changes. Please click BACK again to discard and exit", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> mDoubleBackToExitPressedOnce =false, 2000);
                } else {
                    setEnabled(false);
                    SimulatorLauncher.simulateIfNeeded(getApplicationContext());
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void refreshInverters() {
        mInverters = mViewModel.getInvertersForScenario(mScenarioID);
        Type type = new TypeToken<List<InverterJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<InverterJson> inverterJsons = JsonTools.createInverterListJson(mInverters);
        mInvertersJsonString =  gson.toJson(inverterJsons, type);
    }

    private void addNewInverter() {
        Inverter inverter = new Inverter();
        inverter.setInverterName("<New inverter>");
        addInverter(inverter);
    }

    private void addInverter(Inverter inverter) {
        mInverters.add(inverter);

        refreshMediator();
        if (!(null == mViewPager.getAdapter())) {
            ((InverterViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
        }
        setSaveNeeded(true);
    }

    private void deleteInverter() {
        int pos = mViewPager.getCurrentItem();
        if (mInverters.size() > 0) {
            Inverter removed = mInverters.remove(pos);
            if (null == mRemovedInverters) mRemovedInverters = new ArrayList<>();
            mRemovedInverters.add(removed.getInverterIndex());

            if (!(null == mViewPager.getAdapter())) {
                ((InverterViewPageAdapter) mViewPager.getAdapter()).delete(pos);
            }

//            refreshMediator();
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
        Type type = new TypeToken<List<InverterJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<InverterJson> inverterJsons = JsonTools.createInverterListJson(mInverters);
        mInvertersJsonString =  gson.toJson(inverterJsons, type);
        TabLayout tabLayout = findViewById(R.id.inverter_tab_layout);
        mMediator.detach();
        ArrayList<String> tabTitlesList = new ArrayList<>();
        for (InverterJson ij: inverterJsons) tabTitlesList.add(ij.name);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(tabTitlesList.get(position))
        );
        mMediator.attach();

        LinearLayout linearLayout = (LinearLayout)tabLayout.getChildAt(0);
        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            ((View) linearLayout.getChildAt(i)).setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/help.html");
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
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
                    return true;
                });
            }
            final View edit_a_plan = findViewById(R.id.lp_edit);
            if (edit_a_plan != null) {
                edit_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
                    return true;
                });
            }
            final View export_a_plan = findViewById(R.id.lp_share);
            if (export_a_plan != null) {
                export_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
                    return true;
                });
            }
            final View save_a_plan = findViewById(R.id.lp_save);
            if (save_a_plan != null) {
                save_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
                    return true;
                });
            }
            final View help = findViewById(R.id.lp_help);
            if (help != null) {
                help.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
                    return true;
                });
            }
            final View lpImport = findViewById(R.id.lp_import);
            if (lpImport != null) {
                lpImport.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
                    return true;
                });
            }
            final View copy = findViewById(R.id.lp_copy);
            if (copy != null) {
                copy.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
                    return true;
                });
            }
            final View link = findViewById(R.id.lp_link);
            if (link != null) {
                link.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
                    return true;
                });
            }
            final View del = findViewById(R.id.lp_delete);
            if (del != null) {
                del.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/menu.html");
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
        if (item.getItemId() == R.id.lp_info) {//add the function to perform here
            Snackbar.make(getWindow().getDecorView().getRootView(),
                            "TODO: Status hint" , Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return false;
        }
        if (item.getItemId() == R.id.lp_edit) {//add the function to perform here
            enableEdit();
            return false;
        }
        if (item.getItemId() == R.id.lp_share) {//add the function to perform here
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, mInvertersJsonString);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return true;
        }
        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
            mLoadInverterFile.launch("*/*");
            return false;
        }
        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
            mProgressBar.setVisibility(View.VISIBLE);
            if (!mSimulationInProgress) {
                new Thread(() -> {
                    if (!(null == mRemovedInverters))for (Long inverterID: mRemovedInverters) {
                        mViewModel.deleteInverterFromScenario(inverterID, mScenarioID);
                    }
                    for (Inverter inverter: mInverters) {
                        mViewModel.saveInverterForScenario(mScenarioID, inverter);
                    }
                    mViewModel.deleteSimulationDataForScenarioID(mScenarioID);
                    mViewModel.deleteCostingDataForScenarioID(mScenarioID);
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
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(InverterActivity.this,
                            ScenarioSelectDialog.INVERTER,
                            fromScenarioID -> mViewModel.copyInverterFromScenario(fromScenarioID, mScenarioID));
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_link) {//add the function to perform here
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(InverterActivity.this,
                            ScenarioSelectDialog.INVERTER,
                            fromScenarioID -> {
                        mViewModel.linkInverterFromScenario(fromScenarioID, mScenarioID);
                        showLinkedFAB();
                    });
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_help) {//add the function to perform here
            showHelp("https://appassets.androidplatform.net/assets/scenario/inverter/help.html");
            return false;
        }
        if (item.getItemId() == R.id.lp_delete) {//add the function to perform here
            deleteInverter();
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
            ((InverterViewPageAdapter)mViewPager.getAdapter()).setEdit(mEdit);
        }
    }

    private void setupViewPager() {
        Type type = new TypeToken<List<InverterJson>>(){}.getType();
        List<InverterJson> inverterJsons = new Gson().fromJson(mInvertersJsonString, type);
        int count = inverterJsons.size();

        mViewPager.setAdapter(createInverterAdapter(count));
        mViewPager.setOffscreenPageLimit(4);

        ArrayList<String> tabTitlesList = new ArrayList<>();
        if (inverterJsons.size() == 0) tabTitlesList.add("Inverter");
        else for (InverterJson ij: inverterJsons) tabTitlesList.add(ij.name);
        TabLayout tabLayout = findViewById(R.id.inverter_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(tabTitlesList.get(position))
        );
        mMediator.attach();
    }

    private InverterViewPageAdapter createInverterAdapter(int count) {
        return new InverterViewPageAdapter(this, count);
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
                ((InverterViewPageAdapter) mViewPager.getAdapter()).setEdit(mEdit);
            }
        }
    }

    public String getInverterJson() {
        return mInvertersJsonString;
    }

    public void updateInverterAtIndex(Inverter inverter, int inverterIndex) {
        Inverter removed = mInverters.remove(inverterIndex);
        inverter.setInverterIndex(removed.getInverterIndex());
        mInverters.add(inverterIndex, inverter);
        List<InverterJson> inverterJsons = JsonTools.createInverterListJson(mInverters);
        Type type = new TypeToken<List<InverterJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        mInvertersJsonString =  gson.toJson(inverterJsons, type);
    }

    // PROGRESS BAR
    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.inverter_activity);
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
        ConstraintLayout constraintLayout = findViewById(R.id.inverter_activity);
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
        mHelpWindow.setWidth((int) (getWindow().getDecorView().getWidth()));
        mHelpWindow.showAtLocation(mViewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }
}