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

package com.tfcode.comparetout.importers.esbn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.R;
import com.tfcode.comparetout.importers.ImportActivity;
import com.tfcode.comparetout.importers.ImportSystemSelection;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.util.ArrayList;
import java.util.Objects;

public class ImportESBNActivity extends AppCompatActivity implements ImportSystemSelection, ImportActivity {

    ViewPager2 mViewPager;

    private String mSerialNumber;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;

    private boolean mZoom = false;

    final ActivityResultLauncher<String> mLoadESBNHDFDataFromFile = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    // Handle the returned Uri
                    if (uri == null) return;
                    String uri_s = uri.toString();

                    Data inputData = new Data.Builder()
                            .putString(ImportWorker.KEY_SYSTEM_SN, mSerialNumber)
                            .putString(ImportWorker.KEY_URI, uri_s)
                            .build();
                    OneTimeWorkRequest importWorkRequest =
                            new OneTimeWorkRequest.Builder(ImportWorker.class)
                                    .setInputData(inputData)
                                    .addTag(mSerialNumber + "Import")
                                    .build();
                    WorkManager.getInstance(getApplicationContext()).pruneWork();
                    WorkManager
                            .getInstance(getApplicationContext())
                            .beginUniqueWork(mSerialNumber, ExistingWorkPolicy.APPEND, importWorkRequest)
                            .enqueue();
                }
            });

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

    public void hideFAB() {
        FloatingActionButton fab = findViewById(R.id.zoom);
        if (!(null == fab)) fab.hide();
    }

    @SuppressLint({"MissingInflatedId", "InflateParams"})
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
        mActionBar.setTitle("ESBN Smart Meter Importer");

        FloatingActionButton fab = findViewById(R.id.zoom);
        fab.setOnClickListener(view -> {
            ActionBar actionBar = getSupportActionBar();
            TabLayout tabLayout = findViewById(R.id.import_alpha_tab_layout);
            if (!(null == actionBar) && !(null == tabLayout)) {
                if (!(mZoom)) {
                    actionBar.hide();
                    tabLayout.setVisibility(View.GONE);
                    mZoom = true;
                }
                else {
                    actionBar.show();
                    tabLayout.setVisibility(View.VISIBLE);
                    mZoom = false;
                }}
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_importer, menu);
        int colour = Color.parseColor("White");
        menu.findItem(R.id.load).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        menu.findItem(R.id.export).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        menu.findItem(R.id.help).getIcon().setColorFilter(colour, PorterDuff.Mode.DST);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemID = item.getItemId();
        if (itemID == R.id.load) {
            if ((null == mSerialNumber)) {
                Snackbar.make(mViewPager.getRootView(), "No system selected", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return true;
            }
            mLoadESBNHDFDataFromFile.launch("*/*");
            return (true);
        }
        if (itemID == R.id.export) {
            if (null == mSerialNumber) {
                Snackbar.make(mViewPager.getRootView(), "No system selected", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return true;
            }
            System.out.println("Export attempt ");
            // start the  worker for the selected serial
//            Data inputData = new Data.Builder()
//                    .putString(ExportWorker.KEY_SYSTEM_SN, mSerialNumber)
//                    .build();
//            OneTimeWorkRequest exportWorkRequest =
//                    new OneTimeWorkRequest.Builder(ExportWorker.class)
//                            .setInputData(inputData)
//                            .addTag(mSerialNumber + "Export")
//                            .build();
//            WorkManager.getInstance(this).pruneWork();
//            WorkManager
//                    .getInstance(this)
//                    .beginUniqueWork(mSerialNumber, ExistingWorkPolicy.APPEND, exportWorkRequest)
//                    .enqueue();
            return (true);
        }
        if (itemID == R.id.help) {
            showHelp("https://appassets.androidplatform.net/assets/main/data/alphaess/help.html");
        }

        return(super.onOptionsItemSelected(item));
    }

    private void setupViewPager() {
        mViewPager.setAdapter(new ImportESBNViewPageAdapter(this, 3));
        ArrayList<String> tabTitlesList = new ArrayList<>();
        tabTitlesList.add("ESBN data overview");
        tabTitlesList.add("Data graphs");
        tabTitlesList.add("Generate usage");
        TabLayout tabLayout = findViewById(R.id.import_alpha_tab_layout);
        TabLayoutMediator mMediator = new TabLayoutMediator(tabLayout, mViewPager,
                (tab, position) -> tab.setText(tabTitlesList.get(position))
        );
        mMediator.attach();

        LinearLayout linearLayout = (LinearLayout)tabLayout.getChildAt(0);
        linearLayout.getChildAt(0).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/data/alphaess/overview_tab.html");
            return true;});
        linearLayout.getChildAt(1).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/data/alphaess/graphs_tab.html");
            return true;});
        linearLayout.getChildAt(2).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/data/alphaess/generate_tab.html");
            return true;});

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                showFAB();
            }
        });
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
        mHelpWindow.setWidth(getWindow().getDecorView().getWidth());
        mHelpWindow.showAtLocation(mViewPager.getRootView(), Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }

    @Override
    public String getSelectedSystemSN() {
        return mSerialNumber;
    }

    @Override
    public void setSelectedSystemSN(String serialNumber) {
        mSerialNumber = serialNumber;
        if (!(null == mViewPager) && !(null == mViewPager.getAdapter()))
            ((ImportSystemSelection)mViewPager.getAdapter()).setSelectedSystemSN(mSerialNumber);
    }
}