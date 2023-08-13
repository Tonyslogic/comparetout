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

package com.tfcode.comparetout;

import static com.tfcode.comparetout.TOUTCApplication.FIRST_USE;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
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

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.tfcode.comparetout.scenario.ScenarioActivity;
import com.tfcode.comparetout.scenario.loadprofile.hdf.HDFActivity;
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

import io.reactivex.Single;

public class MainActivity extends AppCompatActivity {

    public static final String CHANNEL_ID = "TOUTC-PROGRESS";
    public static final int USAGE_FRAGMENT = 0;
    public static final int COSTS_FRAGMENT = 1;
    public static final int COMPARE_FRAGMENT = 2;

    ViewPager2 viewPager;
    private ComparisonUIViewModel mViewModel;
    private Menu mMainMenu;
    private ProgressBar mProgressBar;
    private Handler mMainHandler;
    private ProgressBar mSimulationInProgressBar;
    private boolean mSimulationInProgress = false;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private boolean mFirstLaunch = true;

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
                        if (x != y && !y) System.out.println("Something is wrong with the properties");})
                .show();
    }

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
                            System.out.println(pp.plan);
                            PricePlan p = JsonTools.createPricePlan(pp);
                            ArrayList<DayRate> drs = new ArrayList<>();
                            for (DayRateJson drj : pp.rates){
                                DayRate dr = JsonTools.createDayRate(drj);
                                drs.add(dr);
                            }
                            mViewModel.insertPricePlan(p, drs);
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
                            mViewModel.insertScenario(sc);
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
                Intent intent = new Intent(MainActivity.this, ScenarioActivity.class);
                intent.putExtra("ScenarioID", 0L);
                intent.putExtra("Edit", true);
                startActivity(intent);
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
                            mMainMenu.findItem(R.id.estimate).setVisible(false);
                        }
                        break;
                    default:
                        hideFAB();
                        if (!(null == mMainMenu)) {
                            mMainMenu.findItem(R.id.load).setVisible(false);
                            mMainMenu.findItem(R.id.download).setVisible(false);
                            mMainMenu.findItem(R.id.estimate).setVisible(true);
                        }
                        break;
                }
                setMenuLongClick();
                if (mFirstLaunch) showDisclaimers((TOUTCApplication)getApplication() );
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
        mMainMenu.findItem(R.id.load).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMainMenu.findItem(R.id.download).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMainMenu.findItem(R.id.export).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMainMenu.findItem(R.id.help).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMainMenu.findItem(R.id.estimate).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        if (viewPager.getCurrentItem() == COMPARE_FRAGMENT) {
            mMainMenu.findItem(R.id.load).setVisible(false);
            mMainMenu.findItem(R.id.download).setVisible(false);
            mMainMenu.findItem(R.id.estimate).setVisible(true);
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
            if (pos == COSTS_FRAGMENT) {
                mLoadPricePlansFromFile.launch("*/*");
                return (true);
            }
            if (pos == USAGE_FRAGMENT) {
                mLoadScenariosFromFile.launch("*/*");
                return true;
            }
            if (pos == COMPARE_FRAGMENT) {
                Snackbar.make(viewPager.getRootView(), "This should never happen", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return true;
            }
        }

        if (itemID == R.id.download) {
            //add the function to perform here
            System.out.println("Download attempt");
            AtomicReference<String> urlString = new AtomicReference<>("https://raw.githubusercontent.com/Tonyslogic/comparetout-doc/main/price-plans/rates.json");
            if (pos == USAGE_FRAGMENT) urlString.set("https://raw.githubusercontent.com/Tonyslogic/comparetout-doc/main/usage-profiles/scenarios.json");

            DownloadDialog downloadDialog = new DownloadDialog(this, urlString.get(), newURL -> {
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
                                mViewModel.insertPricePlan(p, drs);
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
                                mViewModel.insertScenario(sc);
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

        if (itemID == R.id.estimate) {
            Intent intent = new Intent(this, HDFActivity.class);
            intent.putExtra("LoadProfileID", 0L);
            intent.putExtra("ScenarioID", 0L);
            startActivity(intent);
        }

        return(super.onOptionsItemSelected(item));
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() == 0) super.onBackPressed();
        else viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
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
        mHelpWindow.showAtLocation(viewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }
}