package com.tfcode.comparetout;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.tfcode.comparetout.model.scenario.Scenario;
import com.tfcode.comparetout.scenario.ScenarioActivity;

import java.util.List;
import java.util.Objects;

public class ScenarioNavFragment extends Fragment {

    private ComparisonUIViewModel mViewModel;
    private TableLayout mTableLayout;

    public static ScenarioNavFragment newInstance() {
        return new ScenarioNavFragment();
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
//        mViewModel.doAction();
//        ((MainActivity)requireActivity()).startProgressIndicator();
        mViewModel.getAllScenarios().observe(this, scenarios -> {
            System.out.println("Observed a change in live scenario data " + scenarios.size());
            SimulatorLauncher.simulateIfNeeded(getContext());
            updateView(scenarios);
//            ((MainActivity)requireActivity()).stopProgressIndicator();
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scenario_nav, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTableLayout = requireView().findViewById(R.id.scenarioTable);

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {getString(R.string.main_activity_usage),
                getString(R.string.main_activity_costs),
                getString(R.string.main_activity_compare)};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();
    }

    private void updateView(List<Scenario> scenarios) {
        mTableLayout.removeAllViews();

        if (scenarios != null && scenarios.size() > 0) {
            mTableLayout.setShrinkAllColumns(false);
            mTableLayout.setStretchAllColumns(true);
            mTableLayout.setColumnShrinkable(1, true);
            mTableLayout.setColumnStretchable(1, false);
            for (Scenario scenario : scenarios) {

                // CREATE TABLE ROW
                TableRow tableRow;
                tableRow = new TableRow(getActivity());

                // CREATE PARAM FOR MARGINING
                TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
                planParams.topMargin = 2;
                planParams.rightMargin = 2;

                // CREATE TEXTVIEW

                CheckBox a = new CheckBox(getActivity());
                a.setChecked(scenario.isActive());
                TextView b = new TextView(getActivity());
                ImageButton c = new ImageButton(getActivity());
                ImageButton d = new ImageButton(getActivity());
                ImageButton e = new ImageButton(getActivity());

                // SET PARAMS

                a.setLayoutParams(planParams);
                b.setLayoutParams(planParams);
                c.setLayoutParams(planParams);
                d.setLayoutParams(planParams);
                e.setLayoutParams(planParams);

                // SET BACKGROUND COLOR

//                a.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);
//                b.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);
                c.setBackgroundColor(0);
                d.setBackgroundColor(0);
                e.setBackgroundColor(0);

                // SET PADDING

                a.setPadding(10, 10, 10, 10);
                b.setPadding(10, 10, 10, 10);
                c.setPadding(10, 10, 10, 10);
                d.setPadding(10, 10, 10, 10);
                e.setPadding(10, 10, 10, 10);

                // SET TEXTVIEW TEXT

                b.setText(scenario.getScenarioName());
                c.setImageResource(android.R.drawable.ic_menu_delete);
                d.setImageResource(R.drawable.baseline_content_copy_24 );
                e.setImageResource(android.R.drawable.ic_menu_view);

                a.setId((int) scenario.getId());
                c.setId((int) scenario.getId());
                d.setId((int) scenario.getId());
                e.setId((int) scenario.getId());

                a.setOnClickListener(v -> {
                    System.out.println("Select for comparison: " + v.getId() + " " + a.isChecked());
                    mViewModel.updateScenarioActiveStatus(v.getId(), a.isChecked());
                });

                c.setOnClickListener(v -> {
                    tableRow.setBackgroundColor(Color.RED);
                    a.setBackgroundColor(Color.RED);
                    b.setBackgroundColor(Color.RED);
                    c.setBackgroundColor(Color.RED);
                    d.setBackgroundColor(Color.RED);
                    e.setBackgroundColor(Color.RED);
                    System.out.println("Delete: " + v.getId());
                    mViewModel.deleteScenario(v.getId());
                });

                d.setOnClickListener(v -> {
                    System.out.println("Copy/Add: " + v.getId());
                    mViewModel.copyScenario(v.getId());
                });

                e.setOnClickListener(v -> {
                    System.out.println("View: " + v.getId());
                    Intent intent = new Intent(getActivity(), ScenarioActivity.class);
                    intent.putExtra("ScenarioID", Long.valueOf(v.getId()));
                    startActivity(intent);
                });

                // ADD TEXTVIEW TO TABLEROW

                tableRow.addView(a);
                tableRow.addView(b);
                tableRow.addView(c);
                tableRow.addView(d);
                tableRow.addView(e);

//                tableRow.setBackgroundColor(com.google.android.material.R.attr.backgroundColor);

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

            help.setText(new StringBuilder()
                .append("There are no usage profiles created.\n\nAt least one usage profile ")
                .append("is needed for the application to simulate and compare. \n\nUsage profiles ")
                .append("allow you to describe how you use electricity. You can add as many usage ")
                .append("profiles as you like.")
                .append("\n\nYou can add a usage profile manually using the '+' button in the bottom right, ")
                .append("and using the menu, download or load from a file. \n\nOnce created, you ")
                .append("can delete, copy and view/edit usage profiles from this tab. \n\n")
                .append("Selecting a usage will add it to the comparison tab.").toString());

            mTableLayout.addView(help);
        }
    }

}