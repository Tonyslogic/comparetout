package com.tfcode.comparetout.priceplan;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.tfcode.comparetout.R;

public class PricePlanActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price_plan);
        getSupportActionBar().setTitle("Price plan control");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_prices, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.load:
            //add the function to perform here
            System.out.println("Import attempt");
            return(true);
        case R.id.export:
            //add the function to perform here
            System.out.println("Export attempt");
            return(true);
    }
        return(super.onOptionsItemSelected(item));
    }
}