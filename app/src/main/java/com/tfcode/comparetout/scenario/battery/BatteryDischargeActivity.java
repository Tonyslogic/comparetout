package com.tfcode.comparetout.scenario.battery;

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

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
import com.tfcode.comparetout.model.json.scenario.DischargeToGridJson;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.Scenario2DischargeToGrid;
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

public class BatteryDischargeActivity extends AppCompatActivity {

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

    private int mNextAddedDischargeID = -1;

    private String mDischargeJsonString = "";
    private List<DischargeToGrid> mDischarges;
    private Map<Integer, List<DischargeToGrid>> mTabContents;
    private List<Long> mRemovedDischarges;
    private Map<Long, List<String>> mLinkedDischarges;

    final ActivityResultLauncher<String> mLoadDischargeFile =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                // Handle the returned Uri
                if (uri == null) return;
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(uri);
                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Type type = new TypeToken<List<DischargeToGridJson>>() {}.getType();
                    List<DischargeToGridJson> dischargeToGridJsons  = new Gson().fromJson(reader, type);
                    List<DischargeToGrid> dischargeList = JsonTools.createDischargeList(dischargeToGridJsons);
                    for (DischargeToGrid discharge: dischargeList) addDischarge(discharge);
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

    public void setupLinkedDischarges(int dischargeIndex) {
        if (mDischarges.isEmpty() || (null == mTabContents) || (null == mTabContents.get(dischargeIndex))) {
            return;
        }
        new Thread(() -> {
            if (mLinkedDischarges == null) mLinkedDischarges = new HashMap<>();
            for (DischargeToGrid discharge : mDischarges) {
                mLinkedDischarges.put(discharge.getD2gIndex(), mViewModel.getLinkedDischarges(discharge.getD2gIndex(), mScenarioID));
            }
        }).start();
    }

    public List<String> getLinkedScenarios(Long dischargeID) {
        if (null == mLinkedDischarges) return null;
        return mLinkedDischarges.get(dischargeID);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_battery_discharge);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.discharge_activity), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
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

        mViewPager = findViewById(R.id.discharge_view_pager);

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Discharge (" + mScenarioName + ")");

        mFab = findViewById(R.id.addDischarge);
        mFab.setOnClickListener(view -> addNewDischarge());
        if (mEdit) mFab.show();
        else mFab.hide();

        mViewModel.getAllDischargeRelations().observe(this, relations -> {
            for (Scenario2DischargeToGrid discharge: relations) {
                if (discharge.getScenarioID() == mScenarioID) {
                    new Thread(() -> {
                        int iCountOld = 0;
                        if (!(null == mTabContents)) iCountOld = mTabContents.size();
                        refreshDischarges();
                        int iCountNew = mTabContents.size();
                        mMainHandler.post(() -> {if (!(null == mMediator)) refreshMediator();});
                        while (iCountOld < iCountNew) {
                            mMainHandler.post(() -> {
                                if (!(null == mViewPager.getAdapter())) {
                                    ((BatteryDischargeViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
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
            refreshDischarges();
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
                setupLinkedDischarges(position);
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

    private void refreshDischarges() {
        mDischarges = mViewModel.getDischargesForScenario(mScenarioID);
        sortDischargesIntoTabs();
        Type type = new TypeToken<List<DischargeToGridJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<DischargeToGridJson> dischargeJson = JsonTools.createDischargeJson(mDischarges);
        mDischargeJsonString =  gson.toJson(dischargeJson, type);
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            ((BatteryDischargeViewPageAdapter) mViewPager.getAdapter()).updateDBIndex();
    }

    private void sortDischargesIntoTabs() {
        mTabContents = new HashMap<>();
        Integer maxKey = null;
        for (DischargeToGrid discharge : mDischarges) {
            boolean sorted = false;
            for (Map.Entry<Integer, List<DischargeToGrid>> tabContent: mTabContents.entrySet()) {
                if (maxKey == null) maxKey = tabContent.getKey();
                else if (maxKey < tabContent.getKey()) maxKey = tabContent.getKey();
                if (tabContent.getValue().get(0) != null) {
                    if (tabContent.getValue().get(0).equalDate(discharge)) {
                        tabContent.getValue().add(discharge);
                        sorted = true;
                        break; // stop looking in the map, exit inner loop
                    }
                }
            }
            if (!sorted){
                if (null == maxKey) maxKey = 0;
                List<DischargeToGrid> dischargeToGrids = new ArrayList<>();
                dischargeToGrids.add(discharge);
                mTabContents.put(maxKey, dischargeToGrids);
                maxKey++;
            }
        }
    }

    private void addNewDischarge() {
        DischargeToGrid discharge = new DischargeToGrid();
        discharge.setD2gIndex(mNextAddedDischargeID);
        discharge.setName("New discharge " + mNextAddedDischargeID);
        mNextAddedDischargeID--;
        mDischarges.add(discharge);
        List<DischargeToGrid> temp = new ArrayList<>();
        temp.add(discharge);

        int tabIndex = 0;
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            tabIndex = mViewPager.getAdapter().getItemCount();
        mTabContents.put(tabIndex, temp);
        if (!(null == mViewPager.getAdapter())) {
            ((BatteryDischargeViewPageAdapter) mViewPager.getAdapter()).add(tabIndex);
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator,200);
        setSaveNeeded(true);
    }

    public void getNextAddedDischargeID(DischargeToGrid dischargeToGrid) {
        dischargeToGrid.setD2gIndex(mNextAddedDischargeID);
        mDischarges.add(dischargeToGrid);
        mNextAddedDischargeID--;
    }

    private void addDischarge(DischargeToGrid dischargeToGrid) {
        mDischarges.add(dischargeToGrid);
        sortDischargesIntoTabs();
        refreshMediator();
        if (!(null == mViewPager.getAdapter())) {
            ((BatteryDischargeViewPageAdapter) mViewPager.getAdapter()).add(mViewPager.getAdapter().getItemCount());
        }
        setSaveNeeded(true);
    }

    private void deleteAllDischargesInTab() {
        int pos = mViewPager.getCurrentItem();
        if (!mDischarges.isEmpty()) {

            List<DischargeToGrid> dischargesToBeDeleted = mTabContents.get(pos);
            if (!(null == dischargesToBeDeleted))
                for (DischargeToGrid dischargeToBeDeleted : dischargesToBeDeleted) {
                    boolean removed = mDischarges.remove(dischargeToBeDeleted);
                    if (null == mRemovedDischarges) mRemovedDischarges = new ArrayList<>();
                    if (removed) mRemovedDischarges.add(dischargeToBeDeleted.getD2gIndex());
                }

            if (!(null == mViewPager.getAdapter())) {
                ((BatteryDischargeViewPageAdapter) mViewPager.getAdapter()).delete(pos);
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
        Type type = new TypeToken<List<DischargeToGridJson>>(){}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<DischargeToGridJson> dischargeJson = JsonTools.createDischargeJson(mDischarges);
        mDischargeJsonString =  gson.toJson(dischargeJson, type);
        TabLayout tabLayout = findViewById(R.id.discharge_tab_layout);
        mMediator.detach();
        try {
            mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                    (tab, position) -> tab.setText("Discharge")
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
            linearLayout.getChildAt(i).setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/help.html");
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
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
                    return true;
                });
            }
            final View edit_a_plan = findViewById(R.id.lp_edit);
            if (edit_a_plan != null) {
                edit_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
                    return true;
                });
            }
            final View export_a_plan = findViewById(R.id.lp_share);
            if (export_a_plan != null) {
                export_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
                    return true;
                });
            }
            final View save_a_plan = findViewById(R.id.lp_save);
            if (save_a_plan != null) {
                save_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
                    return true;
                });
            }
            final View help = findViewById(R.id.lp_help);
            if (help != null) {
                help.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
                    return true;
                });
            }
            final View lpImport = findViewById(R.id.lp_import);
            if (lpImport != null) {
                lpImport.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
                    return true;
                });
            }
            final View copy = findViewById(R.id.lp_copy);
            if (copy != null) {
                copy.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
                    return true;
                });
            }
            final View link = findViewById(R.id.lp_link);
            if (link != null) {
                link.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
                    return true;
                });
            }
            final View del = findViewById(R.id.lp_delete);
            if (del != null) {
                del.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/menu.html");
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
            sendIntent.putExtra(Intent.EXTRA_TEXT, mDischargeJsonString);
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return true;
        }
        if (item.getItemId() == R.id.lp_import) {//add the function to perform here
            mLoadDischargeFile.launch("*/*");
            return false;
        }
        if (item.getItemId() == R.id.lp_save) {//add the function to perform here
            mProgressBar.setVisibility(View.VISIBLE);
            if (!mSimulationInProgress) {
                new Thread(() -> {
                    if (!(null == mRemovedDischarges))for (Long dischargeID : mRemovedDischarges) {
                        mViewModel.deleteDischargeFromScenario(dischargeID, mScenarioID);
                    }
                    for (DischargeToGrid discharge: mDischarges) {
                        if (discharge.getD2gIndex() < 0) discharge.setD2gIndex(0);
                        mViewModel.saveDischargeForScenario(mScenarioID, discharge);
                    }
                    mViewModel.deleteSimulationDataForScenarioID(mScenarioID);
                    mViewModel.deleteCostingDataForScenarioID(mScenarioID);
                    refreshDischarges();
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
            if (mUnsavedChanges) {
                Snackbar.make(getWindow().getDecorView().getRootView(),
                                "Save changes first", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return false;
            }
            ScenarioSelectDialog scenarioSelectDialog =
                    new ScenarioSelectDialog(this,
                            ScenarioSelectDialog.DISCHARGE,
                            fromScenarioID -> mViewModel.copyDischargeFromScenario(fromScenarioID, mScenarioID));
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
                            ScenarioSelectDialog.DISCHARGE,
                            fromScenarioID -> mViewModel.linkDischargeFromScenario(fromScenarioID, mScenarioID));
            scenarioSelectDialog.show();
            return false;
        }
        if (item.getItemId() == R.id.lp_help) {//add the function to perform here
            showHelp("https://appassets.androidplatform.net/assets/scenario/discharge/help.html");
            return false;
        }
        if (item.getItemId() == R.id.lp_delete) {//add the function to perform here
            deleteAllDischargesInTab();
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
            ((BatteryDischargeViewPageAdapter)mViewPager.getAdapter()).setEdit(mEdit);
        }
    }

    private void setupViewPager() {
        int count = mTabContents.size();

        mViewPager.setAdapter(createPanelAdapter(count));
        mViewPager.setOffscreenPageLimit(4);

        TabLayout tabLayout = findViewById(R.id.discharge_tab_layout);
        mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText("Discharge")
        );
        mMediator.attach();
    }

    private BatteryDischargeViewPageAdapter createPanelAdapter(int count) {
        return new BatteryDischargeViewPageAdapter(this, count);
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
                ((BatteryDischargeViewPageAdapter) mViewPager.getAdapter()).setEdit(mEdit);
            }
        }
    }

    public List<DischargeToGrid> getDischarges(int pos) {
        return mTabContents.get(pos);
    }

    public long getDatabaseID(int panelTabIndex) {
        return mDischarges.get(panelTabIndex).getD2gIndex();
    }

    public void deleteDischargeAtIndex(DischargeToGrid dischargeToGrid, int dischargeTabIndex, long dischargeID) {
        List<DischargeToGrid> dischargesAtTab = mTabContents.get(dischargeTabIndex);
        if (!(null == dischargesAtTab) && dischargesAtTab.size() == 1) {
            deleteAllDischargesInTab();
        }
        else if (!(null == dischargesAtTab) && dischargeID != 0) {
            for (DischargeToGrid ls : dischargesAtTab) {
                if (ls.getD2gIndex() == dischargeID) {
                    boolean removedFromTab = dischargesAtTab.remove(dischargeToGrid);
                    boolean removedFromLeaderList = mDischarges.remove(dischargeToGrid);
                    if (null == mRemovedDischarges) mRemovedDischarges = new ArrayList<>();
                    if (removedFromTab && removedFromLeaderList) mRemovedDischarges.add(dischargeID);
                    break;
                }
            }
            setSaveNeeded(true);
        }
    }

    public void updateDischargeAtIndex(DischargeToGrid dischargeToGrid, int dischargeTabIndex, long dischargeID) {

        List<DischargeToGrid> dischargesAtTab = mTabContents.get(dischargeTabIndex);
        // Update name, days, months
        if (!(null == dischargesAtTab) && dischargeID == 0) {
            for (DischargeToGrid discharge : dischargesAtTab) {
                discharge.getMonths().months = new ArrayList<>(dischargeToGrid.getMonths().months);
                discharge.getDays().ints = new ArrayList<>(dischargeToGrid.getDays().ints);
                discharge.setName(dischargeToGrid.getName());
            }
        }
        // Update begin, end, stop
        if (!(null == dischargesAtTab) && dischargeID != 0) {
            for (DischargeToGrid discharge : dischargesAtTab) {
                if (discharge.getD2gIndex() == dischargeID) {
                    // Nothing to do here as the state is shared
                    break;
                }
            }
        }

        List<DischargeToGridJson> dischargeJson = JsonTools.createDischargeJson(mDischarges);
        Type type = new TypeToken<List<DischargeToGridJson>>() {}.getType();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        mDischargeJsonString = gson.toJson(dischargeJson, type);
    }

    // PROGRESS BAR
    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.discharge_activity);
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
        ConstraintLayout constraintLayout = findViewById(R.id.discharge_activity);
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
        mHelpWindow.setWidth(getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(mViewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }

    public long getScenarioID() {
        return mScenarioID;
    }
}