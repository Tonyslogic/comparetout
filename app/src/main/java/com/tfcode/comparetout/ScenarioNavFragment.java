package com.tfcode.comparetout;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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

public class ScenarioNavFragment extends Fragment {

    private ComparisonUIViewModel mViewModel;
    private TableLayout mTableLayout;

    public static ScenarioNavFragment newInstance() {
        return new ScenarioNavFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ComparisonUIViewModel.class);
//        mViewModel.doAction();
        mViewModel.getAllScenarios().observe(this, scenarios -> {
            System.out.println("Observed a change in live scenario data " + scenarios.size());
            SimulatorLauncher.simulateIfNeeded(getContext());
            updateView(scenarios);
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
        mTableLayout.setShrinkAllColumns(false);
        mTableLayout.setStretchAllColumns(true);
        mTableLayout.setColumnShrinkable(1, true);
        mTableLayout.setColumnStretchable(1, false);

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {"Prices", "Scenarios", "Compare"};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();
    }

    private void updateView(List<Scenario> scenarios) {
        mTableLayout.removeAllViews();

        if (scenarios != null) {
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

                a.setBackgroundColor(Color.WHITE);
                b.setBackgroundColor(Color.WHITE);
                c.setBackgroundColor(Color.WHITE);
                d.setBackgroundColor(Color.WHITE);
                e.setBackgroundColor(Color.WHITE);

                // SET PADDING

                a.setPadding(10, 10, 10, 10);
                b.setPadding(10, 10, 10, 10);
                c.setPadding(10, 10, 10, 10);
                d.setPadding(10, 10, 10, 10);
                e.setPadding(10, 10, 10, 10);

                // SET TEXTVIEW TEXT

                b.setText(scenario.getScenarioName());
                c.setImageResource(android.R.drawable.ic_menu_delete);
                d.setImageResource(R.drawable.ic_menu_copy);
                e.setImageResource(android.R.drawable.ic_menu_view);

                a.setId((int) scenario.getId());
                c.setId((int) scenario.getId());
                d.setId((int) scenario.getId());
                e.setId((int) scenario.getId());

                a.setOnClickListener(v -> System.out.println("Select for comparison: " + v.getId()));

                c.setOnClickListener(v -> {
                    c.setBackgroundColor(Color.RED);
                    System.out.println("Delete: " + v.getId());
                });

                d.setOnClickListener(v -> System.out.println("Copy/Add: " + v.getId()));

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

                // ADD TABLEROW TO TABLELAYOUT
                mTableLayout.addView(tableRow);
            }
        }
    }

}