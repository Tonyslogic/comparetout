package com.tfcode.comparetout;

import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
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
import com.tfcode.comparetout.model.DayRate;
import com.tfcode.comparetout.model.PricePlan;
import com.tfcode.comparetout.priceplan.PricePlanActivity;

import java.util.List;
import java.util.Map;

public class PricePlanNavFragment extends Fragment {

    private PricePlanNavViewModel mViewModel;
    private TableLayout mTableLayout;

    public static PricePlanNavFragment newInstance() {
        return new PricePlanNavFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(requireActivity()).get(PricePlanNavViewModel.class);
        mViewModel.getAllPricePlans().observe(this, plans -> {
            System.out.println("Observed a change in live plans data " + plans.entrySet().size());
            updateView(plans);
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
        mTableLayout = requireView().findViewById(R.id.planTable);
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

    public void updateView(Map<PricePlan, List<DayRate>> plans) {
        mTableLayout.removeAllViews();
        if (plans != null) {
            for (Map.Entry<PricePlan, List<DayRate>> entry : plans.entrySet()) {
                PricePlan p = entry.getKey();

                // CREATE TABLE ROW
                TableRow tableRow;
                tableRow = new TableRow(getActivity());

                // CREATE PARAM FOR MARGINING
                TableRow.LayoutParams planParams = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
                planParams.topMargin = 2;
                planParams.rightMargin = 2;

                // CREATE TEXTVIEW

                CheckBox a = new CheckBox(getActivity());
                a.setChecked(entry.getKey().isActive());
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

                b.setText(p.getSupplier() + ":"+ p.getPlanName() + " (" + p.getLastUpdate() + ")");
                c.setImageResource(android.R.drawable.ic_menu_delete);
                d.setImageResource(R.drawable.ic_menu_copy);
                e.setImageResource(android.R.drawable.ic_menu_view);

                a.setId((int) p.getId());
                c.setId((int) p.getId());
                d.setId((int) p.getId());
                e.setId((int) p.getId());

                a.setOnClickListener(v -> {
                    System.out.println("Select for comparison: " + v.getId() + " " + a.isChecked());
                    mViewModel.updatePricePlanActiveStatus(v.getId(), a.isChecked());
                    Map<PricePlan, List<DayRate>> pricePlanMap = mViewModel.getAllPricePlans().getValue();
                });

                c.setOnClickListener(v -> {
                    a.setBackgroundColor(Color.RED);
                    b.setBackgroundColor(Color.RED);
                    c.setBackgroundColor(Color.RED);
                    d.setBackgroundColor(Color.RED);
                    e.setBackgroundColor(Color.RED);
                    System.out.println("Delete: " + v.getId());
                    mViewModel.deletePricePlan(v.getId());
                });

                d.setOnClickListener(v -> {
                    System.out.println("Copy/Add: " + v.getId());
                    Map<PricePlan, List<DayRate>> pricePlanMap = mViewModel.getAllPricePlans().getValue();
                    if (pricePlanMap != null) {
                        for (PricePlan pp : pricePlanMap.keySet()) {
                            if (pp.getId() == v.getId()) {
                                System.out.println(pp.getId() + "  " + v.getId());
                                PricePlan newPP = pp.copy();
                                mViewModel.insertPricePlan(newPP, pricePlanMap.get(pp));
                            }
                        }
                    }
                });

                e.setOnClickListener(v -> {
                    System.out.println("View: " + v.getId());
                    Intent intent = new Intent(getActivity(), PricePlanActivity.class);
                    intent.putExtra("PlanID", Long.valueOf(v.getId()));
                    intent.putExtra("Edit", false);
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