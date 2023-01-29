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
import com.tfcode.comparetout.dbmodel.DayRate;
import com.tfcode.comparetout.dbmodel.PricePlan;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PricePlanNavFragment extends Fragment {

    private PricePlanNavViewModel mViewModel;
    private TableLayout mTableLayout;

    public static PricePlanNavFragment newInstance() {
        return new PricePlanNavFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(PricePlanNavViewModel.class);
//        mViewModel.doAction();
        // TODO: Use the ViewModel
        mViewModel.getAllPricePlans().observe(this, plans -> {
            // Update the cached copy of the words in the adapter.
//            adapter.submitList(plans);
            updateView();
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_price_plan_nav, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTableLayout = (TableLayout) requireView().findViewById(R.id.planTable);
        mTableLayout.setShrinkAllColumns(false);
        mTableLayout.setStretchAllColumns(false);
        mTableLayout.setColumnShrinkable(1, true);

        TabLayout tabLayout = requireActivity().findViewById(R.id.tab_layout);
        ViewPager2 viewPager = requireActivity().findViewById(R.id.view_pager);

        String[] tabTitles = {"Prices", "Scenarios", "Compare"};
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();
    }

    public void updateView() {
        Map<PricePlan, List<DayRate>> plans = mViewModel.getAllPricePlans().getValue();
        if (plans != null) {
            for (Map.Entry<PricePlan, List<DayRate>> entry : plans.entrySet()) {
                Plan p = new Plan();
                p.id = entry.getKey().getId();
                p.supplier = entry.getKey().getSupplier();
                p.plan = entry.getKey().getPlanName();

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

                b.setText(p.supplier + ":"+ p.plan);
                c.setImageResource(android.R.drawable.ic_menu_delete);
                d.setImageResource(R.drawable.ic_menu_copy);
                e.setImageResource(android.R.drawable.ic_menu_view);

                a.setId((int) p.id);
                c.setId((int) p.id);
                d.setId((int) p.id);
                e.setId((int) p.id);

                a.setOnClickListener(v -> System.out.println("Select for comparison: " + v.getId()));

                c.setOnClickListener(v -> {
                    c.setBackgroundColor(Color.RED);
                    System.out.println("Delete: " + v.getId());
                });

                d.setOnClickListener(v -> System.out.println("Copy/Add: " + v.getId()));

                e.setOnClickListener(v -> System.out.println("View: " + v.getId()));

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