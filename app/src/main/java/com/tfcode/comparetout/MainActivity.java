package com.tfcode.comparetout;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.Explode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

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

    final ActivityResultLauncher<String> mLoadPricePlansFromFile = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    // Handle the returned Uri
                    if (uri == null) return;
                    mProgressBar.setVisibility(View.VISIBLE);
                    InputStream is;
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
                    InputStream is;
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
                    }
                    mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();


//        // Inside your activity (if you did not enable transitions in your theme)
//        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
//
//        // Set an exit transition
//        getWindow().setExitTransition(new Explode());

        setContentView(R.layout.activity_main);
        createProgressBar();

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
//                Snackbar.make(view, "Here's a Snack bar", Snackbar.LENGTH_LONG)
//                    .setAction("Action", null).show();
            }
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
                        }
                        break;
                    default:
                        hideFAB();
                        if (!(null == mMainMenu)) {
                            mMainMenu.findItem(R.id.load).setVisible(false);
                            mMainMenu.findItem(R.id.download).setVisible(false);
                        }
                        break;
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
        mMainMenu.findItem(R.id.load).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMainMenu.findItem(R.id.download).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mMainMenu.findItem(R.id.export).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        if (viewPager.getCurrentItem() == COMPARE_FRAGMENT) {
            mMainMenu.findItem(R.id.load).setVisible(false);
            mMainMenu.findItem(R.id.download).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int pos = viewPager.getCurrentItem();
        switch(item.getItemId()) {
            case R.id.load:
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

            case R.id.download:
                //add the function to perform here
                System.out.println("Download attempt");
                mProgressBar.setVisibility(View.VISIBLE);
                if (pos == COSTS_FRAGMENT) {
                    new Thread(() -> {
                        URL url;
                        try {
                            url = new URL("https://raw.githubusercontent.com/Tonyslogic/tout-compare/main/rates.json");
                            InputStreamReader reader = new InputStreamReader(url.openStream());
                            Type type = new TypeToken<List<PricePlanJsonFile>>() {}.getType();
                            List<PricePlanJsonFile> ppList = new Gson().fromJson(reader, type);
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
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                    }).start();
                    return(true);
                }
                if (pos == USAGE_FRAGMENT) {
                    mProgressBar.setVisibility(View.VISIBLE);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                        InputStream ins = getResources().openRawResource(
                                getResources().getIdentifier("scenarios", "raw", getPackageName()));
                        InputStreamReader reader = new InputStreamReader(ins, StandardCharsets.UTF_8);
                        Type type = new TypeToken<List<ScenarioJsonFile>>() {
                        }.getType();
                        List<ScenarioJsonFile> scenarioJsonFiles = new Gson().fromJson(reader, type);
                        List<ScenarioComponents> scs = JsonTools.createScenarioComponentList(scenarioJsonFiles);
                        for (ScenarioComponents sc : scs) {
                            mViewModel.insertScenario(sc);
                        }
                        mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                    }}).start();

                    return(true);
                }
                if (pos == COMPARE_FRAGMENT) {
                    Snackbar.make(viewPager.getRootView(), "TODO Hide download on compare tab", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    return(true);
                }


            case R.id.share_plans:
                //add the function to perform here
                System.out.println("Export attempt ");

                mProgressBar.setVisibility(View.VISIBLE);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String plansToShare = JsonTools.createPricePlanJson(mViewModel.getAllPricePlansForExport());
                        mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, plansToShare);
                        sendIntent.setType("text/json");

                        Intent shareIntent = Intent.createChooser(sendIntent, null);
                        startActivity(shareIntent);
                    }
                }).start();
                return(true);

            case R.id.share_scenarios:
                mProgressBar.setVisibility(View.VISIBLE);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String scenariosToShare = JsonTools.createScenarioList(Objects.requireNonNull(mViewModel.getAllScenariosForExport()));
                        mMainHandler.post(() -> mProgressBar.setVisibility(View.GONE));
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, scenariosToShare);
                        sendIntent.setType("text/json");

                        Intent shareIntent = Intent.createChooser(sendIntent, null);
                        startActivity(shareIntent);
                    }
                }).start();
                return(true);

            case R.id.share_comparison:
                Snackbar.make(viewPager.getRootView(), "TODO: Export comparisons", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return(true);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    public void startProgressIndicator() {mProgressBar.setVisibility(View.VISIBLE);}

    public void stopProgressIndicator() {mProgressBar.setVisibility(View.GONE);}
}