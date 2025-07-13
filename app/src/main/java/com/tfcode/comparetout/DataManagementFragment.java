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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.importers.alphaess.ImportAlphaActivity;
import com.tfcode.comparetout.importers.esbn.ImportESBNActivity;
import com.tfcode.comparetout.importers.homeassistant.ImportHomeAssistantActivity;
import com.tfcode.comparetout.util.EdgeInsets;
import com.tfcode.comparetout.util.InsetRespectingFragment;
import com.tfcode.comparetout.util.LocalContentWebViewClient;


public class DataManagementFragment extends InsetRespectingFragment {

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;
    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    private MaterialButton mESBN_HDF;
    private MaterialButton mAlphaESS;
    private MaterialButton mHomeAssistant;
    private MaterialButton mSolisCloud;
    private LinearLayout mLinearLayout;

    public DataManagementFragment() {
        // Required empty public constructor
    }

    public static DataManagementFragment newInstance() {return new DataManagementFragment();}


    @Override
    public void onResume() {
        super.onResume();
        if (!(null == getActivity())) {
            mOrientation = getActivity().getResources().getConfiguration().orientation;
        }
        else mOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!(null == getContext())) {
            mAssetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(getContext()))
                    .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(getContext()))
                    .build();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mPopupView = inflater.inflate(R.layout.popup_help, container);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_data_management, container, false);
//        applyInsetsToView(R.id.tab_layout, EdgeInsets.Edge.TOP);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mESBN_HDF = view.findViewById(R.id.esbnHDFLoader);
        mAlphaESS = view.findViewById(R.id.alphaESSLoader);
        mHomeAssistant = view.findViewById(R.id.haLoader);
        mSolisCloud = view.findViewById(R.id.solisLoader);

        setupButtons();

        mLinearLayout = view.findViewById(R.id.dataLayout);

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {getString(R.string.DataTabName), getString(R.string.main_activity_usage), getString(R.string.main_activity_costs), getString(R.string.main_activity_compare)};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();

        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(MainActivity.USAGE_FRAGMENT)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/scenarionav/tab.html");
            return true;});
        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(MainActivity.COSTS_FRAGMENT)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/plannav/tab.html");
            return true;});
        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(MainActivity.COMPARE_FRAGMENT)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/compare/tab.html");
            return true;});
        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(MainActivity.DATA_MANAGEMENT_FRAGMENT)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/datatab/tab.html");
            return true;});
    }

    private void setupButtons() {
        mESBN_HDF.setOnClickListener(v -> {
            Activity activity = getActivity();
            if (!(null == activity)) {
//                Intent intent = new Intent(activity, HDFActivity.class);
                Intent intent = new Intent(activity, ImportESBNActivity.class);
                intent.putExtra("LoadProfileID", 0L);
                intent.putExtra("ScenarioID", 0L);
                startActivity(intent);
            }
        });

        mAlphaESS.setOnClickListener(v -> {
            Activity activity = getActivity();
            if (!(null == activity)) {
                Intent intent = new Intent(activity, ImportAlphaActivity.class);
                startActivity(intent);
            }
        });

        mHomeAssistant.setOnClickListener(v -> {
            Activity activity = getActivity();
            if (!(null == activity)) {
                Intent intent = new Intent(activity, ImportHomeAssistantActivity.class);
                startActivity(intent);
            }
        });

        mSolisCloud.setOnClickListener(v -> {
            Activity activity = getActivity();
            if (!(null == activity)) {
                showHelp("https://appassets.androidplatform.net/assets/main/datatab/new.html");
            }
        });
    }

    private void showHelp(String url) {
        if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
            mHelpWindow.setHeight((int) (requireActivity().getWindow().getDecorView().getHeight()*0.6));
            mHelpWindow.setWidth((int) (requireActivity().getWindow().getDecorView().getWidth()));
        }
        else {
            mHelpWindow.setWidth((int) (requireActivity().getWindow().getDecorView().getWidth() * 0.6));
            mHelpWindow.setHeight((int) (requireActivity().getWindow().getDecorView().getHeight()));
        }
        mHelpWindow.showAtLocation(mLinearLayout, Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }
}