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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.SimulatorLauncher;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.scenario.HWSystemJson;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.model.scenario.Scenario2HWSystem;
import com.tfcode.comparetout.scenario.ScenarioSelectDialog;
import com.tfcode.comparetout.util.AbstractTextWatcher;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WaterSettingsActivity extends AppCompatActivity {

    private Handler mMainHandler;
    private ProgressBar mSimulationInProgressBar;
    private boolean mSimulationInProgress = false;
    private Menu mMenu;
    private TableLayout mTableSettings;
    private TableLayout mUseTable;

    private Long mScenarioID = 0L;
    private boolean mEdit = false;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;
    private ComparisonUIViewModel mViewModel;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private String mHWSystemJsonString = "";
    private HWSystem mHWSystem;

    private List<String> mLinkedScenarios = new ArrayList<>();

    final ActivityResultLauncher<String> mLoadHWSettingsFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // Handle the returned Uri
                if (uri == null) return;
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<HWSystemJson>() {}.getType();
                    HWSystemJson hwSystemJson  = new Gson().fromJson(reader, type);
                    mHWSystem = JsonTools.createHWSystem(hwSystemJson);
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    mHWSystemJsonString =  gson.toJson(hwSystemJson, type);
                    mMainHandler.post(this::updateView);
                    mMainHandler.post(() -> setSaveNeeded(true));
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

    public void setupLinkedFAB() {
        if (null == mHWSystem) {
            hideLinkedFAB();
            return;
        }
        new Thread(() -> {
            mLinkedScenarios = mViewModel.getLinkedHWSystems(mHWSystem.getHwSystemIndex(), mScenarioID);
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
        setContentView(R.layout.activity_water_settings);
        createSimulationFeedback();

        Intent intent = getIntent();
        mScenarioID = intent.getLongExtra("ScenarioID", 0L);
        String scenarioName = intent.getStringExtra("ScenarioName");
        mEdit = intent.getBooleanExtra("Edit", false);

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                .build();

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("HW System (" + scenarioName + ")");

        mTableSettings = findViewById(R.id.water_settings_table);
        mTableSettings.setShrinkAllColumns(true);
        mTableSettings.setStretchAllColumns(true);

        mUseTable = findViewById(R.id.water_use_table);
        mTableSettings.setShrinkAllColumns(true);
        mTableSettings.setStretchAllColumns(true);

        loadFromDB();

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);

        mViewModel.getAllHWSystemRelations().observe(this, relations -> {
            for (Scenario2HWSystem scenario2hwSystem: relations) {
                if (scenario2hwSystem.getScenarioID() == mScenarioID) {
                    loadFromDB();
                    break;
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
               if (mUnsavedChanges && !mDoubleBackToExitPressedOnce) {
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

    private void loadFromDB() {
        new Thread(() -> {
            mHWSystem = mViewModel.getHWSystemForScenarioID(mScenarioID);
            if (null == mHWSystem) mHWSystem = new HWSystem();
            Type type = new TypeToken<HWSystemJson>(){}.getType();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            HWSystemJson hwSystemJson = JsonTools.createHWSystemJson(mHWSystem);
            mHWSystemJsonString =  gson.toJson(hwSystemJson, type);
            setupLinkedFAB();
            mMainHandler.post(this::updateView);
        }).start();
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
        setMenuLongClick();
        return true;
    }

    private void setMenuLongClick() {
        new Handler().post(() -> {
            final View info = findViewById(R.id.lp_info);
            if (info != null) {
                info.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/menu.html");
                    return true;
                });
            }
            final View edit_a_plan = findViewById(R.id.lp_edit);
            if (edit_a_plan != null) {
                edit_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/menu.html");
                    return true;
                });
            }
            final View export_a_plan = findViewById(R.id.lp_share);
            if (export_a_plan != null) {
                export_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/menu.html");
                    return true;
                });
            }
            final View save_a_plan = findViewById(R.id.lp_save);
            if (save_a_plan != null) {
                save_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/menu.html");
                    return true;
                });
            }
            final View help = findViewById(R.id.lp_help);
            if (help != null) {
                help.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/menu.html");
                    return true;
                });
            }
            final View lpImport = findViewById(R.id.lp_import);
            if (lpImport != null) {
                lpImport.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/menu.html");
                    return true;
                });
            }
            final View copy = findViewById(R.id.lp_copy);
            if (copy != null) {
                copy.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/menu.html");
                    return true;
                });
            }
            final View link = findViewById(R.id.lp_link);
            if (link != null) {
                link.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/menu.html");
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
        delItem.setVisible(false);
        setMenuLongClick();
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.lp_info) {//add the function to perform here
            Snackbar.make(getWindow().getDecorView().getRootView(),
                            "Status hint", Snackbar.LENGTH_LONG)
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
            sendIntent.putExtra(Intent.EXTRA_TEXT, mHWSystemJsonString);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return true;
        }
        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
            mLoadHWSettingsFile.launch("*/*");
            return false;
        }
        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
            if (!mSimulationInProgress) {
                new Thread(() -> {
                    mViewModel.saveHWSystemForScenario(mScenarioID, mHWSystem);
                    mViewModel.deleteSimulationDataForScenarioID(mScenarioID);
                    mViewModel.deleteCostingDataForScenarioID(mScenarioID);
                }).start();
                setSaveNeeded(false);
            }
            else {
                Snackbar.make(getWindow().getDecorView().getRootView(),
                                "Cannot save during simulation. Try again in a moment.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
            return false;
        }
        if (item.getItemId() == R.id.lp_copy) {//add the function to perform here
            if (mUnsavedChanges) {
                Snackbar.make(getWindow().getDecorView().getRootView(),
                                "Save changes first", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return false;
            }
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(this,
                            ScenarioSelectDialog.TANK,
                            fromScenarioID -> mViewModel.copyHWSettingsFromScenario(fromScenarioID, mScenarioID));
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_link) {//add the function to perform here
            if (mUnsavedChanges) {
                Snackbar.make(getWindow().getDecorView().getRootView(),
                                "Save changes first", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return false;
            }
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(this,
                            ScenarioSelectDialog.TANK,
                            fromScenarioID -> mViewModel.linkHWSystemFromScenario(fromScenarioID, mScenarioID));
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_help) {//add the function to perform here
            showHelp("https://appassets.androidplatform.net/assets/scenario/water_settings/help.html");
            return false;
        }
        return false;
    }

    private void enableEdit() {
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
        updateView();
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
            updateView();
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateView() {
        mTableSettings.removeAllViews();
        mUseTable.removeAllViews();
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
        params.topMargin = 2;
        params.rightMargin = 2;


        int integerType = InputType.TYPE_CLASS_NUMBER;
        DecimalFormat df = new DecimalFormat("#");

        // CREATE SETTINGS TABLE ROWS
        {
            mTableSettings.addView(createRow("HW heating capacity", String.valueOf(mHWSystem.getHwCapacity()), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mHWSystem.getHwCapacity())))) {
                        mHWSystem.setHwCapacity(getIntegerOrZero(s));
                        setSaveNeeded(true);
                    }
                }
            }, params, integerType));
            mTableSettings.addView(createRow("Intake temperature (°C)", String.valueOf(mHWSystem.getHwIntake()), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mHWSystem.getHwIntake())))) {
                        mHWSystem.setHwIntake(getIntegerOrZero(s));
                        setSaveNeeded(true);
                    }
                }
            }, params, integerType));
            mTableSettings.addView(createRow("Daily heat loss (°C)", String.valueOf(mHWSystem.getHwLoss()), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mHWSystem.getHwLoss())))) {
                        mHWSystem.setHwLoss(getIntegerOrZero(s));
                        setSaveNeeded(true);
                    }
                }
            }, params, integerType));
            mTableSettings.addView(createRow("Immersion rating (kW)", String.valueOf(mHWSystem.getHwRate()), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mHWSystem.getHwRate())))) {
                        mHWSystem.setHwRate(getIntegerOrZero(s));
                        setSaveNeeded(true);
                    }
                }
            }, params, integerType));
            mTableSettings.addView(createRow("Target temperature (°C)", String.valueOf(mHWSystem.getHwTarget()), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mHWSystem.getHwTarget())))) {
                        mHWSystem.setHwTarget(getIntegerOrZero(s));
                        setSaveNeeded(true);
                    }
                }
            }, params, integerType));
            mTableSettings.addView(createRow("Daily usage", String.valueOf(mHWSystem.getHwUsage()), new AbstractTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (!(s.toString().equals(String.valueOf(mHWSystem.getHwUsage())))) {
                        mHWSystem.setHwUsage(getIntegerOrZero(s));
                        setSaveNeeded(true);
                    }
                }
            }, params, integerType));
        }

        // CREATE USE TABLE ROWS
        {
            TableRow titleRow = new TableRow(this);
            titleRow.setPadding(0,40,0,0);
            TextView atTitle = new TextView(this);
            atTitle.setText("At (hr)");
            titleRow.addView(atTitle);
            TextView useTitle = new TextView(this);
            useTitle.setText("Use (%)");
            titleRow.addView(useTitle);
            TextView deleteTitle = new TextView(this);
            deleteTitle.setText(R.string.Delete);
            deleteTitle.setGravity(Gravity.CENTER);
            titleRow.addView(deleteTitle);
            mUseTable.addView(titleRow);

            int index = 0;
            for (ArrayList<Double> hwUse: mHWSystem.getHwUse().getUsage()) {
                TableRow useRow = new TableRow(this);
                EditText atHour = new EditText(this);
                atHour.setText(df.format(hwUse.get(0)));
                atHour.setMinimumHeight(80);
                atHour.setHeight(80);
                atHour.setPadding(2, 25, 2, 25);
                atHour.setInputType(integerType);
                atHour.setEnabled(mEdit);

                EditText use = new EditText(this);
                use.setText(df.format(hwUse.get(1)));
                use.setMinimumHeight(80);
                use.setHeight(80);
                use.setPadding(2, 25, 2, 25);
                use.setInputType(integerType);
                use.setEnabled(mEdit);

                ImageButton delete = new ImageButton(this);
                delete.setImageResource(R.drawable.ic_baseline_delete_24);
                delete.setContentDescription("Delete this hot water usage");
                delete.setBackgroundColor(0);
                delete.setEnabled(mEdit);
                int finalIndex = index;
                delete.setOnClickListener(v -> {
                    mHWSystem.getHwUse().getUsage().remove(finalIndex);
                    setSaveNeeded(true);
                    updateView();
                });

                useRow.addView(atHour);
                useRow.addView(use);
                useRow.addView(delete);
                mUseTable.addView(useRow);

                index++;
            }

            // Add an add row
            if (mEdit){
                TableRow addRow = new TableRow(this);
                addRow.setBackgroundResource(R.drawable.row_border);
                EditText at = new EditText(this);
                EditText use = new EditText(this);
                ImageButton add = new ImageButton(this);
                add.setImageResource(android.R.drawable.ic_menu_add);
                add.setContentDescription("Add a new hot water use");
                add.setBackgroundColor(0);
                at.setMinimumHeight(80);
                at.setHeight(80);
                use.setMinimumHeight(80);
                use.setHeight(80);

                at.setEnabled(mEdit);
                use.setEnabled(mEdit);
                add.setEnabled(mEdit);

                at.setText("2");
                at.setInputType(integerType);
                use.setText("20");
                use.setInputType(integerType);

                add.setOnClickListener(v -> {
                    mHWSystem.getHwUse().addUse(Double.parseDouble(at.getText().toString()), Double.parseDouble(use.getText().toString()));
                    setSaveNeeded(true);
                    updateView();
                });

                addRow.addView(at);
                addRow.addView(use);
                addRow.addView(add);

                mUseTable.addView(addRow);
            }
        }
    }

    private TableRow createRow(String title, String initialValue, AbstractTextWatcher action, TableRow.LayoutParams params, int inputType){
        TableRow tableRow = new TableRow(this);
        TextView a = new TextView(this);
        a.setText(title);
        a.setMinimumHeight(80);
        a.setHeight(80);
        EditText b = new EditText(this);
        b.setText(initialValue);
        b.setEnabled(mEdit);
        b.addTextChangedListener(action);
        b.setInputType(inputType);

        a.setLayoutParams(params);
        b.setPadding(2, 25, 2, 25);
        tableRow.addView(a);
        tableRow.addView(b);
        return tableRow;
    }

    // SIMULATION BAR
    private void createSimulationFeedback() {
        mSimulationInProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLargeInverse);
        ConstraintLayout constraintLayout = findViewById(R.id.water_settings_activity);
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
        mHelpWindow.showAtLocation(getWindow().getDecorView().getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }
}