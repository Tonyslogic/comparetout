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

package com.tfcode.comparetout.priceplan;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.ComparisonUIViewModel;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.util.EdgeInsets;
import com.tfcode.comparetout.util.InsetRespectingActivity;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PricePlanActivity extends InsetRespectingActivity {

    private static final String EDIT_KEY = "Edit";
    private static final String FOCUSED_PLAN = "FOCUSED_PLAN";
    private static final String PLAN_VALIDITY = "PLAN_VALIDITY";
    private static final String PLAN_ID = "PLAN_ID";
    private static final String UNSAVED = "UNSAVED";

    ViewPager2 viewPager;
    private Menu mMenu;

    private ComparisonUIViewModel mViewModel;
    private Boolean edit = false;
    private Integer mPlanValidity = PricePlan.VALID_PLAN;
    private Long planID = 0L;
    private String focusedPlan = "{}";
    private TabLayoutMediator mMediator;
    private boolean mRetryMediator = false;
    private ActionBar mActionBar;
    private boolean mDoubleBackToExitPressedOnce = false;
    private boolean mUnsavedChanges = false;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(EDIT_KEY, edit);
        outState.putLong(PLAN_ID, planID);
        outState.putString(FOCUSED_PLAN, focusedPlan);
        outState.putBoolean(UNSAVED, mUnsavedChanges);
        outState.putInt(PLAN_VALIDITY, mPlanValidity);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        edit = savedInstanceState.getBoolean(EDIT_KEY);
        planID = savedInstanceState.getLong(PLAN_ID);
        focusedPlan = savedInstanceState.getString(FOCUSED_PLAN);
        mUnsavedChanges = savedInstanceState.getBoolean(UNSAVED);
        mPlanValidity = savedInstanceState.getInt(PLAN_VALIDITY);
    }

    @SuppressLint("InflateParams")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyInsetsToView(R.id.tab_layout, EdgeInsets.Edge.TOP);
        applyInsetsToView(R.id.view_plan_pager, EdgeInsets.Edge.BOTTOM, EdgeInsets.Edge.RIGHT);
        if (!(null == savedInstanceState)) {
            edit = savedInstanceState.getBoolean(EDIT_KEY);
            planID = savedInstanceState.getLong(PLAN_ID);
            focusedPlan = savedInstanceState.getString(FOCUSED_PLAN);
            mUnsavedChanges = savedInstanceState.getBoolean(UNSAVED);
            mPlanValidity = savedInstanceState.getInt(PLAN_VALIDITY);
        }
        else {
            Intent intent = getIntent();
            edit = intent.getBooleanExtra("Edit", false);
            planID = intent.getLongExtra("PlanID", 0L);
            focusedPlan = intent.getStringExtra("Focus");
        }

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                .build();

        setContentView(R.layout.activity_price_plan);
        viewPager = findViewById(R.id.view_plan_pager);

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

        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
        mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Price plan details");
    }

    // PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER PAGE ADAPTER
    private void setupViewPager() {
        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
        int count = ppj.rates.size() + 1;

        viewPager.setAdapter(createPlanAdapter(count));
        viewPager.setOffscreenPageLimit(4);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        try {
            mMediator = new TabLayoutMediator(tabLayout, viewPager,
                    (tab, position) -> tab.setText((position == 0) ? "Plan details" : "Rates")
            );
            mRetryMediator = false;
        }
        catch (ArrayIndexOutOfBoundsException aie) {
            aie.printStackTrace();
            if (!mRetryMediator) {
                mRetryMediator = true;
                new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator,1000);
            }
            else return;
        }
        mMediator.attach();


        LinearLayout linearLayout = (LinearLayout)tabLayout.getChildAt(0);
        linearLayout.getChildAt(0).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/priceplan/detailtab.html");
            return true;});
        for (int i = 1; i < linearLayout.getChildCount(); i++) {
            linearLayout.getChildAt(i).setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/priceplan/ratestab.html");
                return true;
            });
        }
    }

    private PricePlanViewPageAdapter createPlanAdapter(int count) {
        return new PricePlanViewPageAdapter(this, count);
    }

    // MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_plans, menu);
        mMenu = menu;
        int colour = Color.parseColor("White");
        PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(colour, PorterDuff.Mode.DST);
        for (int i : new int[]{R.id.info, R.id.edit_a_plan, R.id.add_a_day_rate, R.id.export_a_plan, R.id.del_a_day_rate, R.id.save_a_plan, R.id.help}) {
            Drawable icon = menu.findItem(i).getIcon();
            if (!(null == icon)) icon.setColorFilter(colorFilter);
        }
        setMenuLongClick();
        return true;
    }

    private void setMenuLongClick() {
        new Handler(Looper.getMainLooper()).post(() -> {
            final View info = findViewById(R.id.info);
            if (info != null) {
                info.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/price_plan_menu.html");
                    return true;
                });
            }
            final View edit_a_plan = findViewById(R.id.edit_a_plan);
            if (edit_a_plan != null) {
                edit_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/price_plan_menu.html");
                    return true;
                });
            }
            final View add_a_day_rate = findViewById(R.id.add_a_day_rate);
            if (add_a_day_rate != null) {
                add_a_day_rate.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/price_plan_menu.html");
                    return true;
                });
            }
            final View export_a_plan = findViewById(R.id.export_a_plan);
            if (export_a_plan != null) {
                export_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/price_plan_menu.html");
                    return true;
                });
            }
            final View del_a_day_rate = findViewById(R.id.del_a_day_rate);
            if (del_a_day_rate != null) {
                del_a_day_rate.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/price_plan_menu.html");
                    return true;
                });
            }
            final View save_a_plan = findViewById(R.id.save_a_plan);
            if (save_a_plan != null) {
                save_a_plan.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/priceplan/price_plan_menu.html");
                    return true;
                });
            }
        });
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        MenuItem saveMenuItem = menu.findItem(R.id.save_a_plan);
        MenuItem editMenuItem = menu.findItem(R.id.edit_a_plan);
        MenuItem addDayRateItem = menu.findItem((R.id.add_a_day_rate));
        MenuItem delDayRateItem = menu.findItem((R.id.del_a_day_rate));
        MenuItem infoItem = menu.findItem((R.id.info));
        infoItem.setVisible(false);
        if (!edit) {
            saveMenuItem.setVisible(false);
            addDayRateItem.setVisible(false);
            delDayRateItem.setVisible(false);
        }
        if (edit) editMenuItem.setVisible(false);
        checkPlanValidity();
        setMenuLongClick();
        return super.onPrepareOptionsMenu(menu);
    }

    private void checkPlanValidity() {
        Type type = new TypeToken<PricePlanJsonFile>(){}.getType();
        PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
        PricePlan pp = JsonTools.createPricePlan(ppj);
        List<DayRate> drs = new ArrayList<>();
        for (DayRateJson drj : ppj.rates){
            DayRate dr = JsonTools.createDayRate(drj);
            drs.add(dr);
        }
        setPlanValidity(pp.validatePlan(drs));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.edit_a_plan) {//add the function to perform here
            edit = true;
            setPlanValidity(mPlanValidity);
            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setVisible(true);
            MenuItem addDayRateItem = mMenu.findItem(R.id.add_a_day_rate);
            addDayRateItem.setVisible(true);
            MenuItem delDayRateItem = mMenu.findItem(R.id.del_a_day_rate);
            delDayRateItem.setVisible(true);
            item.setVisible(false);
            if (!(null == viewPager.getAdapter()))
                ((PricePlanViewPageAdapter)viewPager.getAdapter()).setEdit(true);
            return (false);
        }
        if (item.getItemId() == R.id.export_a_plan){
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "[" + focusedPlan + "]");
            sendIntent.setType("text/json");

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
            return (true);
        }
        if (item.getItemId() == R.id.save_a_plan){
            Type type = new TypeToken<PricePlanJsonFile>() {}.getType();
            PricePlanJsonFile pp = new Gson().fromJson(focusedPlan, type);
            PricePlan p = JsonTools.createPricePlan(pp);
            p.setPricePlanIndex(planID);
            ArrayList<DayRate> drs = new ArrayList<>();
            for (DayRateJson drj : pp.rates) {
                DayRate dr = JsonTools.createDayRate(drj);
                dr.setPricePlanId(planID);
                drs.add(dr);
            }
            mViewModel.updatePricePlan(p, drs);
            setSaveNeeded(false);

            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setVisible(false);
            MenuItem addDayRateItem = mMenu.findItem(R.id.add_a_day_rate);
            addDayRateItem.setVisible(false);
            MenuItem delDayRateItem = mMenu.findItem(R.id.del_a_day_rate);
            delDayRateItem.setVisible(false);
            MenuItem editItem = mMenu.findItem(R.id.edit_a_plan);
            editItem.setVisible(true);
            if (!(null == viewPager.getAdapter()))
                ((PricePlanViewPageAdapter) viewPager.getAdapter()).setEdit(false);

            setMenuLongClick();
            return (true);
        }
        if (item.getItemId() == R.id.del_a_day_rate) {
            int pos = viewPager.getCurrentItem();
            if (pos > 0) {
                Type type = new TypeToken<PricePlanJsonFile>() {}.getType();
                PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
                PricePlan pricePlan = JsonTools.createPricePlan(ppj);
                ArrayList<DayRate> dayRates = new ArrayList<>();
                for (DayRateJson drj : ppj.rates) {
                    dayRates.add(JsonTools.createDayRate(drj));
                }
                dayRates.remove(pos - 1);

                focusedPlan = JsonTools.createSinglePricePlanJsonObject(pricePlan, dayRates);

                if (!(null == viewPager.getAdapter()))
                    ((PricePlanViewPageAdapter)viewPager.getAdapter()).delete(pos);

//                ppj = new Gson().fromJson(focusedPlan, type);
                new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator, 200);
            }
            else {
                Snackbar.make(getWindow().getDecorView().getRootView(), "Try again from a RATES tab", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
            return true;
        }
        if (item.getItemId() == R.id.add_a_day_rate) {
            int pos = 0;
            if (!(null == viewPager.getAdapter()))
                pos = viewPager.getAdapter().getItemCount(); //.getCurrentItem();
            Type type = new TypeToken<PricePlanJsonFile>() {}.getType();
            PricePlanJsonFile ppj = new Gson().fromJson(focusedPlan, type);
            PricePlan pricePlan = JsonTools.createPricePlan(ppj);
            ArrayList<DayRate> dayRates = new ArrayList<>();
            for (DayRateJson drj : ppj.rates) {
                dayRates.add(JsonTools.createDayRate(drj));
            }
            DayRate newDayRate = new DayRate();
            newDayRate.setPricePlanId(pricePlan.getPricePlanIndex());
            dayRates.add(newDayRate);

            focusedPlan = JsonTools.createSinglePricePlanJsonObject(pricePlan, dayRates);

            refreshMediator();
            //
            ((PricePlanViewPageAdapter) viewPager.getAdapter()).add(pos);

            return true;
        }
        if (item.getItemId() == R.id.info) {
            Snackbar.make(getWindow().getDecorView().getRootView(), PricePlan.getInvalidReason(mPlanValidity), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
        if (item.getItemId() == R.id.help) {
            showHelp("https://appassets.androidplatform.net/assets/priceplan/help.html");
        }
        return false;
    }

    private void refreshMediator() {
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        mMediator.detach();
        try {
            mMediator = new TabLayoutMediator(tabLayout, viewPager,
                    (tab, position) -> tab.setText((position == 0) ? "Plan details" : "Rates")
            );
            mRetryMediator = false;
        }
        catch (ArrayIndexOutOfBoundsException aie) {
            aie.printStackTrace();
            if (!mRetryMediator) {
                mRetryMediator = true;
                new Handler(Looper.getMainLooper()).postDelayed(this::refreshMediator,1000);
            }
            else return;
        }
        mMediator.attach();

        LinearLayout linearLayout = (LinearLayout)tabLayout.getChildAt(0);
        linearLayout.getChildAt(0).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/priceplan/detailtab.html");
            return true;});
        for (int i = 1; i < linearLayout.getChildCount(); i++) {
            linearLayout.getChildAt(i).setOnLongClickListener(v -> {
                showHelp("https://appassets.androidplatform.net/assets/priceplan/ratestab.html");
                return true;
            });
        }
    }

    // FRAGMENT ACCESS METHODS
    long getPlanID() {
        return planID;
    }

    public String getFocusedPlan() {
        return focusedPlan;
    }

    public void updateFocusedPlan(String updatedPlan) {
        focusedPlan = updatedPlan;
    }

    public void refreshRestrictions() {
        if (!(null == viewPager) && !(null == viewPager.getAdapter()))
            ((PricePlanViewPageAdapter) viewPager.getAdapter()).refreshRestrictions();
    }

    public boolean getEdit() {
        return edit;
    }

    public void setEdit(boolean ed){
        edit = ed;
    }

    public void setPlanValidity(int validityCode) {
        mPlanValidity = validityCode;
        boolean valid = (mPlanValidity == PricePlan.VALID_PLAN);
        if (!valid) {
            // disable save & share
            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setEnabled(false);
            MenuItem exportDayRateItem = mMenu.findItem(R.id.export_a_plan);
            exportDayRateItem.setEnabled(false);
            mActionBar.setBackgroundDrawable(new ColorDrawable(getColor(R.color.app_bar_nok)));
            MenuItem infoItem = mMenu.findItem(R.id.info);
            infoItem.setVisible(true);
        }
        if (edit && valid) {
            // enable save & share
            MenuItem saveMenuItem = mMenu.findItem(R.id.save_a_plan);
            saveMenuItem.setEnabled(true);
            MenuItem exportDayRateItem = mMenu.findItem(R.id.export_a_plan);
            exportDayRateItem.setEnabled(true);
            mActionBar.setBackgroundDrawable(new ColorDrawable(getColor(R.color.app_bar_ok)));
            MenuItem infoItem = mMenu.findItem(R.id.info);
            infoItem.setVisible(false);
        }
        setMenuLongClick();
    }

    public void setSaveNeeded(boolean saveNeeded){
        mUnsavedChanges = saveNeeded;
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