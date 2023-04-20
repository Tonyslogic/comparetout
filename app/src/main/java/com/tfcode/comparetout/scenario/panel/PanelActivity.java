package com.tfcode.comparetout.scenario.panel;

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
import com.tfcode.comparetout.model.json.scenario.PanelJson;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.Scenario2Panel;
import com.tfcode.comparetout.scenario.ScenarioSelectDialog;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PanelActivity extends AppCompatActivity {

    private Handler mMainHandler;
    private ProgressBar mProgressBar;

    private ViewPager2 mViewPager;
    private Long mScenarioID = 0L;
    private boolean mEdit = false;
    private ComparisonUIViewModel mViewModel;
    private TabLayoutMediator mMediator;
    private Menu mMenu;
    private FloatingActionButton mFab;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    private String mPanelsJsonString = "";
    private List<Panel> mPanels;
    private List<Long> mRemovedPanels;

    final ActivityResultLauncher<String> mLoadPanelFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // Handle the returned Uri
                if (uri == null) return;
                InputStream is;
                try {
                    is = getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<List<PanelJson>>() {}.getType();
                    List<PanelJson> panelJsons  = new Gson().fromJson(reader, type);
                    List<Panel> panels = JsonTools.createPanelList(panelJsons);
                    for (Panel panel: panels) addPanel(panel);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panel);
        createProgressBar();
        mProgressBar.setVisibility(View.VISIBLE);

        Intent intent = getIntent();
        mScenarioID = intent.getLongExtra("ScenarioID", 0L);
        String mScenarioName = intent.getStringExtra("ScenarioName");
        mEdit = intent.getBooleanExtra("Edit", false);
        setContentView(R.layout.activity_panel);

        mViewPager = findViewById(R.id.panel_view_pager);

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Panels (" + mScenarioName + ")");

        mFab = findViewById(R.id.addPanel);
        mFab.setOnClickListener(view -> addNewPanel());
        if (mEdit) mFab.show();
        else mFab.hide();

        mViewModel.getAllPanelRelations().observe(this, relations -> {
            System.out.println("Observed a change in live panel relations ");
            for (Scenario2Panel scenario2Panel: relations) {
                if (scenario2Panel.getScenarioID() == mScenarioID) {
                    new Thread(() -> {
                        int iCountOld = 0;
                        if (!(null == mPanels)) iCountOld = mPanels.size();
                        refreshPanels();
                        int iCountNew = mPanels.size();
                        mMainHandler.post(() -> {if (!(null == mMediator)) refreshMediator();});
                        while (iCountOld < iCountNew) {
                            mMainHandler.post(() ->
                                    ((PanelViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount()));
                            iCountOld++;
                        }
                    }).start();
                    System.out.println("Refreshing the UI");
                    break;
                }
            }
        });

        new Thread(() -> {
            refreshPanels();
            mMainHandler.post(this::setupViewPager);
            mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
        }).start();
    }

    private void refreshPanels() {
        mPanels = mViewModel.getPanelsForScenario(mScenarioID);
        Type type = new TypeToken<List<PanelJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<PanelJson> panelJsons = JsonTools.createPanelListJson(mPanels);
        mPanelsJsonString =  gson.toJson(panelJsons, type);
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            ((PanelViewPageAdapter) mViewPager.getAdapter()).updateDBIndex();
    }

    private void addNewPanel() {
        Panel panel = new Panel();
        addPanel(panel);
    }

    private void addPanel(Panel panel) {
        mPanels.add(panel);
        refreshMediator();
        ((PanelViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
        setSaveNeeded(true);
    }

    private void deletePanel() {
        int pos = mViewPager.getCurrentItem();
        Panel removed = mPanels.remove(pos);
        if (null == mRemovedPanels) mRemovedPanels = new ArrayList<>();
        mRemovedPanels.add(removed.getPanelIndex());

        ((PanelViewPageAdapter) mViewPager.getAdapter()).delete(pos);

        refreshMediator();
        setSaveNeeded(true);
    }

    private void refreshMediator() {
        Type type = new TypeToken<List<PanelJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<PanelJson> panelJsons = JsonTools.createPanelListJson(mPanels);
        mPanelsJsonString =  gson.toJson(panelJsons, type);
        TabLayout tabLayout = findViewById(R.id.panel_tab_layout);
        mMediator.detach();
        ArrayList<String> tabTitlesList = new ArrayList<>();
        for (PanelJson pj: panelJsons) tabTitlesList.add("Panels");
        String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        );
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
        System.out.println("PanelActivity.onOptionsItemSelected");

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
            sendIntent.putExtra(Intent.EXTRA_TEXT, mPanelsJsonString);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return true;
        }
        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
            System.out.println("Import attempt");
            mLoadPanelFile.launch("*/*");
            return false;
        }
        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
            System.out.println("Save attempt, saving " + mPanels.size());
            mProgressBar.setVisibility(View.VISIBLE);
            new Thread(() -> {
                if (!(null == mRemovedPanels))for (Long panelID: mRemovedPanels) {
                    mViewModel.deletePanelFromScenario(panelID, mScenarioID);
                }
                for (Panel panel: mPanels) {
                    mViewModel.savePanelForScenario(mScenarioID, panel);
                }
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
                    new ScenarioSelectDialog(PanelActivity.this,
                            ScenarioSelectDialog.PANEL,
                            fromScenarioID -> mViewModel.copyPanelFromScenario(fromScenarioID, mScenarioID));
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
                    new ScenarioSelectDialog(PanelActivity.this,
                            ScenarioSelectDialog.PANEL,
                            fromScenarioID -> mViewModel.linkPanelFromScenario(fromScenarioID, mScenarioID));
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
            deletePanel();
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
        ((PanelViewPageAdapter)mViewPager.getAdapter()).setEdit(mEdit);
    }

    private void setupViewPager() {
        Type type = new TypeToken<List<PanelJson>>(){}.getType();
        List<PanelJson> panelJsons = new Gson().fromJson(mPanelsJsonString, type);
        int count = panelJsons.size();

        mViewPager.setAdapter(createPanelAdapter(count));
        mViewPager.setOffscreenPageLimit(4);
        System.out.println("setupViewPager " + count + " fragments");

        ArrayList<String> tabTitlesList = new ArrayList<>();
        if (panelJsons.size() == 0) tabTitlesList.add("Panel");
        else for (PanelJson pj: panelJsons) tabTitlesList.add("Panel");
        String[] tabTitles = tabTitlesList.toArray(new String[tabTitlesList.size()]);
        TabLayout tabLayout = findViewById(R.id.panel_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        );
        mMediator.attach();
    }

    private PanelViewPageAdapter createPanelAdapter(int count) {
        return new PanelViewPageAdapter(this, count);
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
            ((PanelViewPageAdapter) mViewPager.getAdapter()).setEdit(mEdit);
        }
    }

    public String getPanelJson() {
        return mPanelsJsonString;
    }

    public long getDBID(int panelTabIndex) {
        return mPanels.get(panelTabIndex).getPanelIndex();
    }

    public void updatePanelAtIndex(Panel panel, int panelIndex) {
        Panel removed = mPanels.remove(panelIndex);
        panel.setPanelIndex(removed.getPanelIndex());
        mPanels.add(panelIndex, panel);
        List<PanelJson> panelJsons = JsonTools.createPanelListJson(mPanels);
        Type type = new TypeToken<List<PanelJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        mPanelsJsonString =  gson.toJson(panelJsons, type);
    }

    // PROGRESS BAR
    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.panel_activity);
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