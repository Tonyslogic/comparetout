/*
 * Copyright (c) 2024. Tony Finnerty
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

package com.tfcode.comparetout.importers.homeassistant;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.util.EdgeInsets;
import com.tfcode.comparetout.util.GraphableActivity;
import com.tfcode.comparetout.util.InsetRespectingActivity;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.util.ArrayList;
import java.util.Objects;

public class ImportHomeAssistantActivity  extends InsetRespectingActivity implements GraphableActivity {
    ViewPager2 mViewPager;
    private boolean mZoom = false;
    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;
    private MenuItem mCompareButton;

    private void showFAB() {
        FloatingActionButton fab = findViewById(R.id.zoom);
        if (!(null == fab))
            if ((mViewPager.getCurrentItem() != 1) && (mViewPager.getCurrentItem() != 2)) {
                fab.hide();
                if (mZoom) {
                    ActionBar actionBar = getSupportActionBar();
                    TabLayout tabLayout = findViewById(R.id.import_alpha_tab_layout);
                    if (!(null == actionBar) && !(null == tabLayout)) {
                        actionBar.show();
                        tabLayout.setVisibility(View.VISIBLE);
                        updateTopInsetTarget(tabLayout, mViewPager);
                        mZoom = false;
                    }
                }
            } else {
                fab.show();
            }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            hideFAB();
        }
    }

    @Override
    public void hideFAB() {
        FloatingActionButton fab = findViewById(R.id.zoom);
        if (!(null == fab)) fab.hide();
    }

    private void showCompare() {
        if (!(null == mCompareButton)) {
            mCompareButton.setVisible(mViewPager.getCurrentItem() == 1);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @SuppressLint({"MissingInflatedId", "InflateParams"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyInsetsToView(R.id.import_alpha_tab_layout, EdgeInsets.Edge.TOP);
        setContentView(R.layout.activity_import_alpha);

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                .build();

        setContentView(R.layout.activity_import_alpha);
        mViewPager = findViewById(R.id.import_alpha_view_pager);
        setupViewPager();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mViewPager.getCurrentItem() > 0) {
                    mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
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

        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("Home Assistant Data");

        FloatingActionButton fab = findViewById(R.id.zoom);
        fab.setOnClickListener(view -> {
            ActionBar actionBar = getSupportActionBar();
            TabLayout tabLayout = findViewById(R.id.import_alpha_tab_layout);
            if (!(null == actionBar) && !(null == tabLayout)) {
                if (!(mZoom)) {
                    actionBar.hide();
                    tabLayout.setVisibility(View.GONE);
                    updateTopInsetTarget(tabLayout, mViewPager);
                    mZoom = true;
                }
                else {
                    actionBar.show();
                    tabLayout.setVisibility(View.VISIBLE);
                    updateTopInsetTarget(tabLayout, mViewPager);
                    mZoom = false;
                }}
        });
    }

    private void setupViewPager() {
        mViewPager.setAdapter(new ImportHomeAssistantViewPageAdapter(this, 3));
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("Overview");
        tabTitlesList.add("Graphs");
        tabTitlesList.add("Key stats");
        tabTitlesList.add("Generate usage");

        TabLayout tabLayout = findViewById(R.id.import_alpha_tab_layout);
        TabLayoutMediator mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(tabTitlesList.get(position))
        );
        mMediator.attach();

        LinearLayout linearLayout = (LinearLayout)tabLayout.getChildAt(0);
        linearLayout.getChildAt(0).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/data/ha/overview_tab.html");
            return true;});
        linearLayout.getChildAt(1).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/data/ha/graphs_tab.html");
            return true;});
        linearLayout.getChildAt(2).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/data/ha/keys_tab.html");
            return true;});
        linearLayout.getChildAt(3).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/data/ha/generate_tab.html");
            return true;});

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                showFAB();
                showCompare();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_importer, menu);
        int colour = Color.parseColor("White");
        menu.findItem(R.id.load).setVisible(false); //.getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        menu.findItem(R.id.export).setVisible(false); //.getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        mCompareButton = menu.findItem(R.id.compare);
        mCompareButton.setVisible(false);
        Objects.requireNonNull(menu.findItem(R.id.compare).getIcon()).setColorFilter(colour, PorterDuff.Mode.DST);
        Drawable icon = menu.findItem(R.id.help).getIcon();
        if (icon != null) icon.setColorFilter(colour, PorterDuff.Mode.DST);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemID = item.getItemId();

        if (itemID == R.id.help) {
            showHelp("https://appassets.androidplatform.net/assets/main/data/ha/help.html");
        }

        return(super.onOptionsItemSelected(item));
    }

    private void showHelp(String url) {
        mHelpWindow.setHeight((int) (getWindow().getDecorView().getHeight()*0.6));
        mHelpWindow.setWidth(getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(mViewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }

    @Override
    public String getSelectedSystemSN() {
        return "HomeAssistant";
    }

    @Override
    public void setSelectedSystemSN(String serialNumber) {

    }
}
