/*
 * Copyright (c) 2023-2024. Tony Finnerty
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

package com.tfcode.comparetout;

import static com.tfcode.comparetout.TOUTCApplication.FIRST_USE;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
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
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.priceplan.PricePlanActivity;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Single;

/**
 * Main Activity serving as the primary entry point and navigation hub for the TOUTC application.
 * 
 * This activity coordinates the entire user interface, managing a ViewPager2 that hosts multiple
 * fragments for different application sections (data management, usage, costs, and comparison).
 * It handles critical application lifecycle events, user onboarding, data import/export, and
 * provides centralized progress tracking for long-running operations.
 * 
 * Key responsibilities:
 * - Application initialization and first-launch setup with disclaimers
 * - Fragment navigation and state management through ViewPager2
 * - Data import/export functionality for price plans and scenarios
 * - Background work monitoring and progress indication
 * - Help system integration with asset-based web content
 * - Application menu management and user action handling
 * - Notification channel setup for background operations
 * 
 * The activity uses a ViewModel pattern to coordinate data operations and maintains
 * reactive subscriptions to provide real-time updates to the UI. It also implements
 * sophisticated error handling and user feedback mechanisms to ensure a smooth
 * user experience even when dealing with complex energy system data.
 * 
 * Fragment organization:
 * - DATA_MANAGEMENT_FRAGMENT: Import/export and data management tools
 * - USAGE_FRAGMENT: Energy usage scenarios and configuration
 * - COSTS_FRAGMENT: Price plan definition and management
 * - COMPARE_FRAGMENT: Cost comparison and analysis results
 */
public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID = "TOUTC-PROGRESS";
    public static final int USAGE_FRAGMENT = 1;
    public static final int COSTS_FRAGMENT = 2;
    public static final int COMPARE_FRAGMENT = 3;
    public static final int DATA_MANAGEMENT_FRAGMENT = 0;

    ViewPager2 viewPager;
    private ComparisonUIViewModel mViewModel;
    private Menu mMainMenu;
    private ProgressBar mProgressBar;
    private Handler mMainHandler;
    private ProgressBar mSimulationInProgressBar;
    private boolean mSimulationInProgress = false;
    private boolean clobberPlansAndScenarios = false;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private boolean mFirstLaunch = true;
    private boolean mFirstLaunchDialogRendered = false;

    /**
     * Load user preferences from the encrypted DataStore.
     * 
     * Retrieves the first-launch flag from secure storage to determine
     * whether to show the application disclaimers. Uses reactive programming
     * to handle the asynchronous data retrieval gracefully.
     */
    private void loadSettingsFromDataStore() {
        new Thread(() -> {
            Preferences.Key<String> FirstUse = PreferencesKeys.stringKey(FIRST_USE);
            TOUTCApplication application = ((TOUTCApplication)getApplication());
            Single<String> value = application.getDataStore()
                    .data().firstOrError()
                    .map(prefs -> prefs.get(FirstUse)).onErrorReturnItem("True");
            String ret =  value.blockingGet();
            System.out.println("Got a value from the dataStore:" + ret);
            if (ret.equals("False")) {
//                mMainHandler.post(() -> showDisclaimers(application));
                mFirstLaunch = false;
            }
        }).start();
    }

    /**
     * Display application disclaimers and warnings to first-time users.
     * 
     * Shows a comprehensive dialog explaining the limitations of the energy
     * calculations, data accuracy considerations, and legal disclaimers.
     * This ensures users understand that the app provides estimations for
     * exploration purposes rather than professional financial advice.
     * 
     * Upon acceptance, marks the user as having seen the disclaimers and
     * stores this preference securely for future launches.
     * 
     * @param application the application instance for DataStore access
     */
    private void showDisclaimers(TOUTCApplication application) {
        new MaterialAlertDialogBuilder(this)
                .setMessage("Solar data is variable. This app uses historical solar data in estimations.\n\n" +
                        "Solar panels may be shaded. This app makes no attempt to consider shading.\n\n" +
                        "Price plan accuracy determines calculation accuracy. Check price plan details.\n\n" +
                        "Price plans do not include Public Services Obligations. Estimates will not include this.\n\n" +
                        "Price plans do not include rate usage limitations or complex contracts. Estimates will be " +
                        "wrong where limitations are exceeded or conditions triggered.\n\n" +
                        "All estimates are based on user input. If the input is bad, the output will be too.\n\n" +
                        "This app provides the ability to explore possibilities, estimations are not advice (financial or otherwise). " +
                        "The app is provided as-is, no representation or warranty of any kind, express or implied, regarding " +
                        "the accuracy, adequacy, validity, reliability, availability, or completeness of any information is made.\n\n" +
                        "Enjoy!" )
                .setPositiveButton("Ok", (dialog, which) -> {
                        boolean x = application.putStringValueIntoDataStore(FIRST_USE, "False");
                        boolean y = application.putStringValueIntoDataStore("Test", "True");
                        mFirstLaunch = false;
                        if (x != y && !y) System.out.println("Something is wrong with the properties");})
                .show();
    }

    /**
     * Activity result launcher for importing price plan data from JSON files.
     * 
     * Handles the complete import process for price plans, including:
     * - File selection and reading from external storage
     * - JSON deserialization using Gson with proper type handling
     * - Database insertion with optional overwrite capability
     * - Progress indication during import operations
     * - Cleanup of associated costing data when plans are updated
     * 
     * The import process supports bulk operations and provides robust error
     * handling to prevent data corruption during the import process.
     */
    final ActivityResultLauncher<String> mLoadPricePlansFromFile = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    // Handle the returned Uri
                    if (uri == null) return;
                    mProgressBar.setVisibility(View.VISIBLE);
                    InputStream is = null;
                    try {
                        is = getContentResolver().openInputStream(uri);
                        InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                        Type type = new TypeToken<List<PricePlanJsonFile>>(){}.getType();
                        List<PricePlanJsonFile> ppList = new Gson().fromJson(reader, type);
                        for(PricePlanJsonFile pp : ppList){
                            // Process each price plan in the imported file
                            System.out.println(pp.plan);
                            PricePlan p = JsonTools.createPricePlan(pp);
                            ArrayList<DayRate> drs = new ArrayList<>();
                            for (DayRateJson drj : pp.rates){
                                DayRate dr = JsonTools.createDayRate(drj);
                                drs.add(dr);
                            }
                            // Insert with optional overwriting based on user preference
                            mViewModel.insertPricePlan(p, drs, clobberPlansAndScenarios);
                            // Clear associated cost calculations as they're now invalid
                            mViewModel.removeCostingsForPricePlan(p.getPricePlanIndex());
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    finally {
                        if (!(null == is)) {
                            try {
                                is.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                }
            });

    /**
     * Activity result launcher for importing scenario data from JSON files.
     * 
     * Manages the import of complete energy system scenarios including all
     * associated component data (inverters, batteries, panels, loads, etc.).
     * The import process reconstructs complex scenario relationships and
     * ensures data integrity across multiple database tables.
     * 
     * Provides the same robust error handling and progress indication as
     * price plan imports, with cleanup of dependent calculation data.
     */
    final ActivityResultLauncher<String> mLoadScenariosFromFile = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    // Handle the returned Uri
                    if (uri == null) return;
                    mProgressBar.setVisibility(View.VISIBLE);
                    InputStream is = null;
                    try {
                        is = getContentResolver().openInputStream(uri);
                        InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
                        List<ScenarioJsonFile> scenarioJsonFiles = new Gson().fromJson(reader, type);
                        List<ScenarioComponents> scs = JsonTools.createScenarioComponentList(scenarioJsonFiles);
                        for (ScenarioComponents sc : scs) {
                            mViewModel.insertScenario(sc, clobberPlansAndScenarios);
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (NullPointerException e) {
                        Snackbar.make(viewPager.getRootView(), "File did not parse", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    } finally {
                        if (!(null == is)) {
                            try {
                                is.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                }
            });

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
//                .penaltyDeath()
                .build());
        super.onCreate(savedInstanceState);
        createNotificationChannel();

        loadSettingsFromDataStore();

        mAssetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                    .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                    .build();

        setContentView(R.layout.activity_main);
        createSimulationFeedback();
        createProgressBar();

        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);

        viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(createCardAdapter());
        viewPager.setCurrentItem(MainActivity.DATA_MANAGEMENT_FRAGMENT);
        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);

        /*
          Add price plan or scenario depending on the visible fragment
         */

        FloatingActionButton fab = findViewById(R.id.addSomething);
        fab.setOnClickListener(view -> {
            int pos = viewPager.getCurrentItem();
            if (pos == COSTS_FRAGMENT) {
                Intent intent = new Intent(MainActivity.this, PricePlanActivity.class);
                intent.putExtra("PlanID", 0L);
                intent.putExtra("Edit", false);
                intent.putExtra("Focus", JsonTools.createSinglePricePlanJsonObject(
                        new PricePlan(), new ArrayList<>()));
                startActivity(intent);
            }
            if (pos == USAGE_FRAGMENT) {
                if (!(null == viewPager.getAdapter()))
                    ((ViewPagerAdapter)viewPager.getAdapter()).addNewScenario();
            }
        });
        fab.setOnLongClickListener(v -> {
            int pos = viewPager.getCurrentItem();
            if (pos == COSTS_FRAGMENT) {
                showHelp("https://appassets.androidplatform.net/assets/main/addcost.html");
            }
            if (pos == USAGE_FRAGMENT) {
                showHelp("https://appassets.androidplatform.net/assets/main/addscenario.html");
            }
            return true;
        });

        /*
          Enable/Disable the floating 'add' button based on the visible fragment
         */
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                switch (position) {
                    case COSTS_FRAGMENT:
                    case USAGE_FRAGMENT:
                        showFAB();
                        if (!(null == mMainMenu)) {
                            mMainMenu.findItem(R.id.load).setVisible(true);
                            mMainMenu.findItem(R.id.download).setVisible(true);
                            mMainMenu.findItem(R.id.export).setVisible(true);
                        }
                        break;
                    case DATA_MANAGEMENT_FRAGMENT:
                        hideFAB();
                        if (!(null == mMainMenu)) {
                            mMainMenu.findItem(R.id.load).setVisible(false);
                            mMainMenu.findItem(R.id.download).setVisible(false);
                            mMainMenu.findItem(R.id.export).setVisible(false);
                        }
                        break;
                    case COMPARE_FRAGMENT:
                        hideFAB();
                        if (!(null == mMainMenu)) {
                            mMainMenu.findItem(R.id.load).setVisible(false);
                            mMainMenu.findItem(R.id.download).setVisible(false);
                            mMainMenu.findItem(R.id.export).setVisible(true);
                        }
                        break;
                }
                setMenuLongClick();
                if (mFirstLaunch && !(mFirstLaunchDialogRendered)) {
                    showDisclaimers((TOUTCApplication)getApplication() );
                    mFirstLaunchDialogRendered = true;
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewPager.getCurrentItem() > 0) {
                    viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private ViewPagerAdapter createCardAdapter() {
        return new ViewPagerAdapter(this);
    }

    private void showFAB() {
        FloatingActionButton fab = findViewById(R.id.addSomething);
        fab.show();
    }

    private void hideFAB() {
        FloatingActionButton fab = findViewById(R.id.addSomething);
        fab.hide();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_prices, menu);
        mMainMenu = menu;
        int colour = Color.parseColor("White");
        for (int i : new int[]{R.id.load, R.id.download, R.id.export, R.id.help}) {
            Drawable icon = mMainMenu.findItem(i).getIcon();
            if (!(null == icon)) icon.setColorFilter(colour, PorterDuff.Mode.DST);
        }
        if (viewPager.getCurrentItem() == COMPARE_FRAGMENT) {
            for (int i : new int[]{R.id.load, R.id.download}) {
                mMainMenu.findItem(i).setVisible(false);
            }
        }
        if (viewPager.getCurrentItem() == DATA_MANAGEMENT_FRAGMENT) {
            for (int i : new int[]{R.id.load, R.id.download, R.id.export}) {
                mMainMenu.findItem(i).setVisible(false);
            }
        }
        setMenuLongClick();
        return true;
    }

    private void setMenuLongClick() {
        new Handler().post(() -> {
            final View export = findViewById(R.id.export);
            if (export != null) {
                export.setOnLongClickListener(v -> {
                    int pos = viewPager.getCurrentItem();
                    if (pos == USAGE_FRAGMENT) showHelp("https://appassets.androidplatform.net/assets/main/exportscenario.html");
                    if (pos == COSTS_FRAGMENT) showHelp("https://appassets.androidplatform.net/assets/main/exportcosts.html");
                    if (pos == COMPARE_FRAGMENT) showHelp("https://appassets.androidplatform.net/assets/main/exportcomparison.html");
                    return true;
                });
            }
            final View load = findViewById(R.id.load);
            if (load != null) {
                load.setOnLongClickListener(v -> {
                    int pos = viewPager.getCurrentItem();
                    if (pos == USAGE_FRAGMENT) showHelp("https://appassets.androidplatform.net/assets/main/loadscenario.html");
                    if (pos == COSTS_FRAGMENT) showHelp("https://appassets.androidplatform.net/assets/main/loadcosts.html");
                    return true;
                });
            }
            final View download = findViewById(R.id.download);
            if (download != null) {
                download.setOnLongClickListener(v -> {
                    int pos = viewPager.getCurrentItem();
                    if (pos == USAGE_FRAGMENT) showHelp("https://appassets.androidplatform.net/assets/main/downloadscenario.html");
                    if (pos == COSTS_FRAGMENT) showHelp("https://appassets.androidplatform.net/assets/main/downloadcosts.html");
                    return true;
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int pos = viewPager.getCurrentItem();
        int itemID = item.getItemId();
        if (itemID == R.id.load) {
            //add the function to perform here
            System.out.println("Import attempt");
            if (pos == COMPARE_FRAGMENT) {
                Snackbar.make(viewPager.getRootView(), "This should never happen", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return true;
            }
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Load behaviour");
            alert.setMessage("Do you want to replace existing entries?");
            alert.setPositiveButton("Replace", (dialog, which) -> {
                clobberPlansAndScenarios = true;
                if (pos == COSTS_FRAGMENT) mLoadPricePlansFromFile.launch("*/*");
                if (pos == USAGE_FRAGMENT) mLoadScenariosFromFile.launch("*/*");
            });
            alert.setNegativeButton("Keep", (dialog, which) -> {
                clobberPlansAndScenarios = false;
                if (pos == COSTS_FRAGMENT) mLoadPricePlansFromFile.launch("*/*");
                if (pos == USAGE_FRAGMENT) mLoadScenariosFromFile.launch("*/*");
            });
            alert.show();
            return true;
        }

        if (itemID == R.id.download) {
            //add the function to perform here
            System.out.println("Download attempt");
            AtomicReference<String> urlString = new AtomicReference<>("https://raw.githubusercontent.com/Tonyslogic/comparetout-doc/main/price-plans/rates.json");
            if (pos == USAGE_FRAGMENT) urlString.set("https://raw.githubusercontent.com/Tonyslogic/comparetout-doc/main/usage-profiles/scenarios.json");

            DownloadDialog downloadDialog = new DownloadDialog(this, urlString.get(), (newURL, clobber) -> {
                urlString.set(newURL);
                mProgressBar.setVisibility(View.VISIBLE);
                if (pos == COSTS_FRAGMENT) {
                    String finalUrlString = urlString.get();
                    new Thread(() -> {
                        URL url;
                        InputStreamReader reader;
                        try {
                            url = new URL(finalUrlString);
                            reader = new InputStreamReader(url.openStream());
                            Type type = new TypeToken<List<PricePlanJsonFile>>() {
                            }.getType();
                            List<PricePlanJsonFile> ppList = new Gson().fromJson(reader, type);
                            reader.close();
                            for (PricePlanJsonFile pp : ppList) {
                                System.out.println(pp.plan);
                                PricePlan p = JsonTools.createPricePlan(pp);
                                ArrayList<DayRate> drs = new ArrayList<>();
                                for (DayRateJson drj : pp.rates) {
                                    DayRate dr = JsonTools.createDayRate(drj);
                                    drs.add(dr);
                                }
                                mViewModel.insertPricePlan(p, drs, clobber);
                            }
                            reader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                    }).start();
                }
                if (pos == USAGE_FRAGMENT) {
                    mProgressBar.setVisibility(View.VISIBLE);
                    String finalUrlString1 = urlString.get();
                    new Thread(() -> {
                        URL url;
                        InputStreamReader reader;
                        try {
                            url = new URL(finalUrlString1);
                            reader = new InputStreamReader(url.openStream());
                            Type type = new TypeToken<List<ScenarioJsonFile>>() {
                            }.getType();
                            List<ScenarioJsonFile> scenarioJsonFiles = new Gson().fromJson(reader, type);
                            List<ScenarioComponents> scs = JsonTools.createScenarioComponentList(scenarioJsonFiles);
                            for (ScenarioComponents sc : scs) {
                                mViewModel.insertScenario(sc, clobber);
                            }
                            reader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                    }).start();
                }
            });
            downloadDialog.show();

            return true;
        }

        if (itemID == R.id.share_plans) {
            //add the function to perform here
            System.out.println("Export attempt ");

            mProgressBar.setVisibility(View.VISIBLE);

            new Thread(() -> {
                String plansToShare = JsonTools.createPricePlanJson(mViewModel.getAllPricePlansForExport());
                mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, plansToShare);
                sendIntent.setType("text/json");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            }).start();
            return (true);
        }

        if (itemID == R.id.share_scenarios) {
            mProgressBar.setVisibility(View.VISIBLE);

            new Thread(() -> {
                String scenariosToShare = JsonTools.createScenarioList(Objects.requireNonNull(mViewModel.getAllScenariosForExport()));
                mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, scenariosToShare);
                sendIntent.setType("text/json");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            }).start();
            return (true);
        }

        if (itemID == R.id.share_comparison) {
            mProgressBar.setVisibility(View.VISIBLE);

            new Thread(() -> {
                List<String> comparisonsRows = mViewModel.getAllComparisonsNow();
                StringBuilder comparisonsBuilder = new StringBuilder().append("Usage, Plan, Net, Buy, Sell, Fixed, Bonus\n");
                for(String comparison: comparisonsRows) comparisonsBuilder.append(comparison).append("\n");
                mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, comparisonsBuilder.toString());
                sendIntent.setType("text/csv");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            }).start();
                return(true);
        }

        if (itemID == R.id.help) {
            showHelp("https://appassets.androidplatform.net/assets/main/help.html");
        }

        return(super.onOptionsItemSelected(item));
    }

    private void createProgressBar() {
        mProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        ConstraintLayout constraintLayout = findViewById(R.id.main_activity);
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

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    // SIMULATION BAR
    public boolean isSimulationPassive() {
        return !mSimulationInProgress;
    }

    private void createSimulationFeedback() {
        mSimulationInProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleLargeInverse);
        ConstraintLayout constraintLayout = findViewById(R.id.main_activity);
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
        mHelpWindow.setWidth(getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(viewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }
}