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

package com.tfcode.comparetout.importers.alphaess;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.util.ArrayList;
import java.util.Objects;

public class ImportAlphaActivity extends AppCompatActivity {

    ViewPager2 mViewPager;

    private String mSerialNumber;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAssetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(this))
                .build();

        setContentView(R.layout.activity_import_alpha);
        mViewPager = findViewById(R.id.import_alpha_view_pager);
        setupViewPager();


        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.popup_help, null);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);

        ActionBar mActionBar = Objects.requireNonNull(getSupportActionBar());
        mActionBar.setTitle("AlphaESS Importer");

    }

    private void setupViewPager() {
        mViewPager.setAdapter(new ImportAlphaViewPageAdapter(this, 3));
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("AlphaESS overview");
        tabTitlesList.add("Data graphs");
        tabTitlesList.add("Key stats");
        tabTitlesList.add("Highs and lows");
        TabLayout tabLayout = findViewById(R.id.import_alpha_tab_layout);
        TabLayoutMediator mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(tabTitlesList.get(position))
        );
        mMediator.attach();

        LinearLayout linearLayout = (LinearLayout)tabLayout.getChildAt(0);
        ((View)linearLayout.getChildAt(0)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/help.html");
            return true;});
        ((View)linearLayout.getChildAt(1)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/detail_tab.html");
            return true;});
        ((View)linearLayout.getChildAt(2)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/monthly_tab.html");
            return true;});
        ((View)linearLayout.getChildAt(3)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/scenario/annual_tab.html");
            return true;});
    }

    @Override
    public void onBackPressed() {
        if (mViewPager.getCurrentItem() == 0) {
            super.onBackPressed();
        }
        else mViewPager.setCurrentItem(mViewPager.getCurrentItem() - 1);
    }

    private void showHelp(String url) {
        mHelpWindow.setHeight((int) (getWindow().getDecorView().getHeight()*0.6));
        mHelpWindow.setWidth((int) (getWindow().getDecorView().getWidth()));
        mHelpWindow.showAtLocation(mViewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }

    public String getSelectedSystemSN() {
        return mSerialNumber;
    }

    public void setSelectedSystemSN(String serialNumber) {
        mSerialNumber = serialNumber;
        if (!(null == mViewPager.getAdapter()))
            ((ImportAlphaViewPageAdapter)mViewPager.getAdapter()).setSelectedSystemSN(mSerialNumber);
    }
}