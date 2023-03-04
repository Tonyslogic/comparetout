package com.tfcode.comparetout;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.priceplan.PricePlanActivity;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;

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

    ViewPager2 viewPager;
    private PricePlanNavViewModel mViewModel;

    final ActivityResultLauncher<String> mLoadFromFile = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    // Handle the returned Uri
                    if (uri == null) return;
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
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(createCardAdapter());
        mViewModel = new ViewModelProvider(this).get(PricePlanNavViewModel.class);

        /*
          Add price plan or scenario depending on the visible fragment
         */
        FloatingActionButton fab = findViewById(R.id.addSomething);
        fab.setOnClickListener(view -> {
            int pos = viewPager.getCurrentItem();
            if (pos == 0) {
                Intent intent = new Intent(MainActivity.this, PricePlanActivity.class);
                intent.putExtra("PlanID", 0L);
                intent.putExtra("Edit", false);
                intent.putExtra("Focus", JsonTools.createSinglePricePlanJsonObject(
                        new PricePlan(), new ArrayList<>()));
                startActivity(intent);
            }
            if (pos == 1) {
                Snackbar.make(view, "Here's a Snack bar", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
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
                    case 0:
                    case 1:
                        showFAB();
                        break;
                    default:
                        hideFAB();
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.load:
                //add the function to perform here
                System.out.println("Import attempt");
                mLoadFromFile.launch("*/*");
                return(true);

            case R.id.download:
                //add the function to perform here
                System.out.println("Download attempt");

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
                }).start();
                return(true);

            case R.id.share_plans:
                //add the function to perform here
                System.out.println("Export attempt ");

                String toShare = JsonTools.createPricePlanJson(Objects.requireNonNull(mViewModel.getAllPricePlans().getValue()));

                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, toShare);
                sendIntent.setType("text/json");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
                return(true);

            case R.id.share_scenarios:
                Snackbar.make(viewPager.getRootView(), "TODO: Export scenarios", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return(true);

            case R.id.share_comparison:
                Snackbar.make(viewPager.getRootView(), "TODO: Export comparisons", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return(true);
        }
        return(super.onOptionsItemSelected(item));
    }
}