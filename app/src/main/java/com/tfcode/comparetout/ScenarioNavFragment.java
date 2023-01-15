package com.tfcode.comparetout;

import androidx.lifecycle.ViewModelProvider;

import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;

public class ScenarioNavFragment extends Fragment {

    private ScenarioNavViewModel mViewModel;
    private TableLayout mTableLayout;

    public static ScenarioNavFragment newInstance() {
        return new ScenarioNavFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ScenarioNavViewModel.class);
        mViewModel.doAction();
        // TODO: Use the ViewModel
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scenario_nav, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = (TableLayout) getView().findViewById(R.id.scenarioTable);

        List<Scenario> plans =mViewModel.getScenario().getValue();
        if (plans != null) {
            for (Scenario scenario : mViewModel.getScenario().getValue()) {

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

                a.setPadding(20, 20, 20, 20);
                b.setPadding(20, 20, 20, 20);
                c.setPadding(20, 20, 20, 20);
                d.setPadding(20, 20, 20, 20);
                e.setPadding(20, 20, 20, 20);

                // SET TEXTVIEW TEXT

                b.setText(scenario.name);
                c.setImageResource(android.R.drawable.ic_menu_delete);
                d.setImageResource(android.R.drawable.ic_menu_add);
                e.setImageResource(android.R.drawable.ic_menu_view);

                a.setId(scenario.id);
                c.setId(scenario.id);
                d.setId(scenario.id);
                e.setId(scenario.id);

                a.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {
                        System.out.println("Select for comparison: " + v.getId());
                    }
                });

                c.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {
                        c.setBackgroundColor(Color.RED);
                        System.out.println("Delete: " + v.getId());
                    }
                });

                d.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {
                        System.out.println("Copy/Add: " + v.getId());
                    }
                });

                e.setOnClickListener(new View.OnClickListener(){
                    @Override
                    public void onClick(View v) {
                        System.out.println("View: " + v.getId());
                    }
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
            TabLayout tabLayout = getActivity().findViewById(R.id.tab_layout);
            ViewPager2 viewPager = getActivity().findViewById(R.id.view_pager);

            String[] tabTitles = {"Prices", "Scenarios", "Compare"};
            new TabLayoutMediator(tabLayout, viewPager,
                    (tab, position) -> tab.setText(tabTitles[position])
            ).attach();
        }
    }

}