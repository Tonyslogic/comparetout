package com.tfcode.comparetout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.tfcode.comparetout.priceplan.PricePlanActivity;

public class MainActivity extends AppCompatActivity {

    ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(createCardAdapter());

        FloatingActionButton fab = findViewById(R.id.addSomething);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int pos = viewPager.getCurrentItem();
                if (pos == 0) {
                    Intent intent = new Intent(MainActivity.this, PricePlanActivity.class);
                    startActivity(intent);
                }
                if (pos == 1) {
                    Snackbar.make(view, "Here's a Snackbar", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                }
            }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                switch (position) {
                    case 0:
                    case 1:
                        showFAB();
                        break;
                    default:
                        hideFAB();
                        break;
                }
            }
        });
    }

    private ViewPagerAdapter createCardAdapter() {
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        return adapter;
    }

    private void showFAB() {
        FloatingActionButton fab = findViewById(R.id.addSomething);
        fab.show();
    }

    private void hideFAB() {
        FloatingActionButton fab = findViewById(R.id.addSomething);
        fab.hide();
    }
}