package com.tfcode.comparetout.priceplan;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.tfcode.comparetout.R;

public class PricePlanActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_plan);
        getSupportActionBar().setTitle("Price plan control");
    }
}