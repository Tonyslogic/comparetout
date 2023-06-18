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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.priceplan.PricePlanActivity;
import com.tfcode.comparetout.util.LocalContentWebViewClient;

import java.util.List;
import java.util.Map;

public class PricePlanNavFragment extends Fragment {

    private ComparisonUIViewModel mViewModel;
    private TableLayout mTableLayout;
    private Map<PricePlan, List<DayRate>> mPlans;

    private WebViewAssetLoader mAssetLoader;
    private View mPopupView;
    private PopupWindow mHelpWindow;
    private int mOrientation = Configuration.ORIENTATION_PORTRAIT;

    public static PricePlanNavFragment newInstance() {
        return new PricePlanNavFragment();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (!(null == getActivity())) {
            mOrientation = getActivity().getResources().getConfiguration().orientation;
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        else mOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        updateView();
    }
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!(null == getContext())) {
            mAssetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(getContext()))
                    .addPathHandler("/res/", new WebViewAssetLoader.ResourcesPathHandler(getContext()))
                    .build();
        }

        mViewModel = new ViewModelProvider(requireActivity()).get(ComparisonUIViewModel.class);
//        ((MainActivity)requireActivity()).startProgressIndicator();
        mViewModel.getAllPricePlans().observe(this, plans -> {
            System.out.println("Observed a change in live plans data " + plans.entrySet().size());
            SimulatorLauncher.simulateIfNeeded(getContext());
            mPlans = plans;
            updateView();
//            ((MainActivity)requireActivity()).stopProgressIndicator();
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mPopupView = inflater.inflate(R.layout.popup_help, container);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        mHelpWindow = new PopupWindow(mPopupView, width, height, focusable);
        return inflater.inflate(R.layout.fragment_price_plan_nav, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = requireView().findViewById(R.id.planTable);

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {getString(R.string.main_activity_usage), getString(R.string.main_activity_costs), getString(R.string.main_activity_compare)};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();

        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(0)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/scenarionav/tab.html");
            return true;});
        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(1)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/plannav/tab.html");
            return true;});
        ((View)((LinearLayout)tabLayout.getChildAt(0)).getChildAt(2)).setOnLongClickListener(v -> {
            showHelp("https://appassets.androidplatform.net/assets/main/compare/tab.html");
            return true;});
    }

    public void updateView() {
        mTableLayout.removeAllViews();
        if ((mPlans != null) && (mPlans.entrySet().size() > 0) && !(null == getActivity())) {
            mTableLayout.setShrinkAllColumns(false);
            mTableLayout.setStretchAllColumns(true);
            mTableLayout.setColumnShrinkable(1, true);
            mTableLayout.setColumnStretchable(1, false);

            for (Map.Entry<PricePlan, List<DayRate>> entry : mPlans.entrySet()) {
                PricePlan p = entry.getKey();

                // CREATE TABLE ROW
                TableRow tableRow;
                tableRow = new TableRow(getActivity());

                // CREATE PARAM FOR MARGINING
                TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
                planParams.topMargin = 2;
                planParams.rightMargin = 2;

                TableRow.LayoutParams textParams = new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT);
                planParams.topMargin = 2;
                planParams.rightMargin = 2;

                // CREATE TEXTVIEW

                String supplierPlan = String.format("%s:%s (%s)", p.getSupplier(), p.getPlanName(), p.getLastUpdate());

                CheckBox a = new CheckBox(getActivity());
                a.setChecked(entry.getKey().isActive());
                a.setGravity(Gravity.CENTER_VERTICAL);
                TextView b = new TextView(getActivity());
                b.setGravity(Gravity.CENTER_VERTICAL);
                b.setAutoSizeTextTypeUniformWithConfiguration(
                        1, 17, 1, TypedValue.COMPLEX_UNIT_SP);
                ImageButton c = new ImageButton(getActivity());
                ImageButton d = new ImageButton(getActivity());

                a.setContentDescription(String.format("%s %s %s %s", entry.getKey().isActive() ? "remove": "add", supplierPlan, entry.getKey().isActive() ? "from": "to", "comparison"));
                b.setContentDescription(String.format("%s, %s", "View or edit", supplierPlan));
                c.setContentDescription(String.format("%s, %s", "Delete", supplierPlan));
                d.setContentDescription(String.format("%s, %s", "Copy", supplierPlan));

                // SET PARAMS

                a.setLayoutParams(planParams);
                b.setLayoutParams(textParams);
                c.setLayoutParams(planParams);
                d.setLayoutParams(planParams);

                // SET BACKGROUND COLOR

                c.setBackgroundColor(0);
                d.setBackgroundColor(0);

                // SET PADDING

                a.setPadding(10, 10, 10, 10);
                b.setPadding(10, 10, 10, 10);
                c.setPadding(10, 10, 10, 10);
                d.setPadding(10, 10, 10, 10);

                // SET TEXTVIEW TEXT

                b.setText(supplierPlan);
                c.setImageResource(R.drawable.ic_baseline_delete_24);
                d.setImageResource(R.drawable.ic_baseline_content_copy_24);

                a.setId((int) p.getPricePlanIndex());
                c.setId((int) p.getPricePlanIndex());
                d.setId((int) p.getPricePlanIndex());

                a.setOnClickListener(v -> {
                    System.out.println("Select for comparison: " + v.getId() + " " + a.isChecked());
                    mViewModel.updatePricePlanActiveStatus(v.getId(), a.isChecked());
                });

                c.setOnClickListener(v -> {
                    tableRow.setBackgroundColor(Color.RED);
                    a.setBackgroundColor(Color.RED);
                    b.setBackgroundColor(Color.RED);
                    c.setBackgroundColor(Color.RED);
                    d.setBackgroundColor(Color.RED);
                    System.out.println("Delete: " + v.getId());
                    if (!(null == getActivity()) && ((MainActivity) getActivity()).isSimulationPassive()) {
                        AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
                        alert.setTitle("Delete entry");
                        alert.setMessage("Are you sure you want to delete?");
                        alert.setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            mViewModel.deletePricePlan(v.getId());
                            mViewModel.deleteRelatedCostings(v.getId());
                        });
                        alert.setNegativeButton(android.R.string.no, (dialog, which) -> {
                            // close dialog
                            dialog.cancel();
                            tableRow.setBackgroundColor(0);
                            a.setBackgroundColor(0);
                            b.setBackgroundColor(0);
                            c.setBackgroundColor(0);
                            d.setBackgroundColor(0);
                        });
                        alert.show();
                    }
                    else {
                        if (!(null == getView())) Snackbar.make(getView(),
                            "Cannot delete during simulation. Try again in a moment.", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }
                });

                d.setOnClickListener(v -> {
                    System.out.println("Copy/Add: " + v.getId());
                    Map<PricePlan, List<DayRate>> pricePlanMap = mViewModel.getAllPricePlans().getValue();
                    if (pricePlanMap != null) {
                        for (PricePlan pp : pricePlanMap.keySet()) {
                            if (pp.getPricePlanIndex() == v.getId()) {
                                System.out.println(pp.getPricePlanIndex() + "  " + v.getId());
                                PricePlan newPP = pp.copy();
                                mViewModel.insertPricePlan(newPP, pricePlanMap.get(pp));
                            }
                        }
                    }
                });

                b.setOnClickListener(v -> {
                    tableRow.setBackgroundColor(Color.LTGRAY);
                    a.setBackgroundColor(Color.LTGRAY);
                    b.setBackgroundColor(Color.LTGRAY);
                    c.setBackgroundColor(Color.LTGRAY);
                    d.setBackgroundColor(Color.LTGRAY);
                    System.out.println("View: " + v.getId());
                    Intent intent = new Intent(getActivity(), PricePlanActivity.class);
                    intent.putExtra("PlanID", p.getPricePlanIndex());
                    intent.putExtra("Edit", false);
                    intent.putExtra("Focus", JsonTools.createSinglePricePlanJsonObject(p, entry.getValue()));
                    startActivity(intent);
                });

                // HELP
                a.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/main/comparecheck.html");
                    return true;
                });
                b.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/main/plannav/name.html");
                    return true;
                });
                c.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/main/delete.html");
                    return true;
                });
                d.setOnLongClickListener(v -> {
                    showHelp("https://appassets.androidplatform.net/assets/main/copy.html");
                    return true;
                });

                // ADD TEXTVIEW TO TABLEROW

                tableRow.addView(a);
                tableRow.addView(b);
                tableRow.addView(c);
                tableRow.addView(d);

                // ADD TABLEROW TO TABLELAYOUT
                mTableLayout.addView(tableRow);
            }
        }
        else {
            //TODO Popup help
            mTableLayout.setShrinkAllColumns(true);
            mTableLayout.setStretchAllColumns(false);

            TextView help = new TextView(getActivity());
            help.setSingleLine(false);
            help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);

            help.setText(R.string.NoPricePlansText);

            mTableLayout.addView(help);

        }
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
        mHelpWindow.showAtLocation(mTableLayout, Gravity.CENTER, 0, 0);
        WebView webView = mPopupView.findViewById(R.id.helpWebView);

        webView.setWebViewClient(new LocalContentWebViewClient(mAssetLoader));
        webView.loadUrl(url);
    }

}