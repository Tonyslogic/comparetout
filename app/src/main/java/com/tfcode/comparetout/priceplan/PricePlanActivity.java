package com.tfcode.comparetout.priceplan;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.PricePlanNavViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.dbmodel.DayRate;
import com.tfcode.comparetout.dbmodel.DoubleHolder;
import com.tfcode.comparetout.dbmodel.IntHolder;
import com.tfcode.comparetout.dbmodel.PricePlan;

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

public class PricePlanActivity extends AppCompatActivity {

    private PricePlanNavViewModel mViewModel;

    ActivityResultLauncher<String> mLoadFromFile = registerForActivityResult(new ActivityResultContracts.GetContent(),
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
                            PricePlan p = createPricePlan(pp);
                            ArrayList<DayRate> drs = new ArrayList<>();
                            for (DayRateJson drj : pp.rates){
                                DayRate dr = createDayRate(drj);
                                drs.add(dr);
                            }
                            mViewModel.insertPricePlan(p, drs);
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            });

    private static PricePlan createPricePlan(PricePlanJsonFile pp) {
        PricePlan p = new PricePlan();
        p.setPlanName(pp.plan);
        p.setSupplier(pp.supplier);
        p.setFeed(pp.feed);
        p.setStandingCharges(pp.standingCharges);
        p.setSignUpBonus(pp.bonus);
        p.setActive(pp.active);
        p.setLastUpdate(pp.lastUpdate);
        p.setReference(pp.reference);
        return p;
    }

    private static DayRate createDayRate(DayRateJson drj){
        DayRate dr = new DayRate();
        if (drj.endDate == null) dr.setEndDate("12/31");
        else dr.setEndDate(drj.endDate);
        if (drj.startDate == null) dr.setStartDate("01/01");
        else dr.setStartDate(drj.startDate);
        IntHolder ih = new IntHolder();
        ih.ints = drj.days;
        dr.setDays(ih);
        DoubleHolder dh = new DoubleHolder();
        dh.doubles = drj.hours;
        dr.setHours(dh);
        return dr;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(PricePlanNavViewModel.class);

        setContentView(R.layout.activity_price_plan);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Price plan control");
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
                    Type type = new TypeToken<List<PricePlanJsonFile>>() {
                    }.getType();
                    List<PricePlanJsonFile> ppList = new Gson().fromJson(reader, type);
                    for (PricePlanJsonFile pp : ppList) {
                        System.out.println(pp.plan);
                        PricePlan p = createPricePlan(pp);
                        ArrayList<DayRate> drs = new ArrayList<>();
                        for (DayRateJson drj : pp.rates) {
                            DayRate dr = createDayRate(drj);
                            drs.add(dr);
                        }
                        mViewModel.insertPricePlan(p, drs);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            return(true);
        case R.id.export:
            //add the function to perform here
            System.out.println("Export attempt");
            return(true);
    }
        return(super.onOptionsItemSelected(item));
    }
}