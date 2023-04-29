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

package com.tfcode.comparetout.model;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.JsonTools;
import com.tfcode.comparetout.model.json.priceplan.DayRateJson;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class PricePlanDAOTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private volatile ToutcDB toutcDB;
    private PricePlanDAO pricePlanDAO;
    private static final String testData = "[{\"Supplier\": \"BGE\", \"Plan\": \"Free time Sat\", \"Feed\": 0.0, \"Standing charges\": 285.42, \"Bonus cash\": 0.0, \"Rates\": [{\"Days\": [0, 1, 2, 3, 4, 5], \"Hours\": [17.47, 17.47, 17.47, 17.47, 17.47, 17.47, 17.47, 17.47, 23.77, 23.77, 23.77, 23.77, 23.77, 23.77, 23.77, 23.77, 23.77, 29.04, 29.04, 23.77, 23.77, 23.77, 23.77, 17.47, 17.47]}, {\"Days\": [6], \"Hours\": [17.47, 17.47, 17.47, 17.47, 17.47, 17.47, 17.47, 17.47, 23.77, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 29.04, 29.04, 23.77, 23.77, 23.77, 23.77, 17.47, 17.47]}], \"Active\": false, \"LastUpdate\": \"2022-08-06\", \"Reference\": \"https://www.bordgaisenergy.ie/home/our-plans\"}, {\"Supplier\": \"BGE\", \"Plan\": \"Green EV Smart\", \"Feed\": 18.5, \"Standing charges\": 418.78, \"Bonus cash\": 0.0, \"Rates\": [{\"Days\": [1, 2, 3, 4, 5], \"Hours\": [24.63, 24.63, 8.33, 8.33, 8.33, 24.63, 24.63, 24.63, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 45.54, 45.54, 32.66, 32.66, 32.66, 32.66, 24.63, 24.63]}, {\"Days\": [0, 6], \"Hours\": [24.63, 24.63, 8.33, 8.33, 8.33, 24.63, 24.63, 24.63, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 32.66, 24.63, 24.63]}], \"Active\": true, \"LastUpdate\": \"2022-09-12\", \"Reference\": \"https://www.bordgaisenergy.ie/home/our-plans/a0p4L000000OeitQAC\"}, {\"Supplier\": \"Electric Ireland\", \"Plan\": \"Home Electric+ Night boost\", \"Rates\": [{\"Days\": [0, 1, 2, 3, 4, 5, 6], \"Hours\": [21.55, 21.55, 12.65, 12.65, 21.55, 21.55, 21.55, 21.55, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 43.68, 21.55, 21.55]}], \"Feed\": 14.0, \"Standing charges\": 396.89, \"Bonus cash\": 0.0, \"Active\": true, \"LastUpdate\": \"2022-10-01\", \"Reference\": \"https://www.electricireland.ie/switch/new-customer/price-plans?priceType=E\"}, {\"Supplier\": \"Electric Ireland\", \"Plan\": \"Home Electric Weekender\", \"Rates\": [{\"Days\": [1, 2, 3, 4, 5], \"Hours\": [44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89]}, {\"Days\": [0, 6], \"Hours\": [44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 44.89, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 44.89, 44.89]}], \"Feed\": 14.0, \"Standing charges\": 359.16, \"Bonus cash\": 0.0, \"Active\": false, \"LastUpdate\": \"2022-10-01\", \"Reference\": \"https://www.electricireland.ie/switch/new-customer/price-plans?priceType=E\"}, {\"Supplier\": \"Energia\", \"Plan\": \"ElectricCar D/N\", \"Feed\": 19.6, \"Standing charges\": 296.4, \"Bonus cash\": 0.0, \"Rates\": [{\"Days\": [0, 1, 2, 3, 4, 5, 6], \"Hours\": [9.73, 9.73, 9.73, 9.73, 9.73, 9.73, 9.73, 9.73, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 33.8, 9.73]}], \"Active\": true, \"LastUpdate\": \"2022-09-11\", \"Reference\": \"https://www.energia.ie/products/price-plans?meterType=Standard&MeterTypeDDL=2\"}, {\"Supplier\": \"Energia\", \"Plan\": \"Home connect interactive\", \"Feed\": 18.0, \"Standing charges\": 236.62, \"Bonus cash\": 25.0, \"Rates\": [{\"Days\": [0, 1, 2, 3, 4, 5, 6], \"Hours\": [23.14, 23.14, 23.14, 23.14, 23.14, 23.14, 23.14, 23.14, 32.37, 32.37, 32.37, 32.37, 32.37, 32.37, 32.37, 32.37, 32.37, 32.92, 32.92, 32.37, 32.37, 32.37, 32.37, 23.14, 23.14]}], \"Active\": true, \"LastUpdate\": \"2022-09-11\", \"Reference\": \"https://www.energia.ie/products/price-plans?meterType=Smart\"}, {\"Supplier\": \"FloGas\", \"Plan\": \"GreenFuture 35%\", \"Feed\": 20.0, \"Standing charges\": 302.92, \"Bonus cash\": 220.0, \"Rates\": [{\"Days\": [0, 1, 2, 3, 4, 5, 6], \"Hours\": [22.64, 22.64, 22.64, 22.64, 22.64, 22.64, 22.64, 22.64, 29.04, 29.04, 29.04, 29.04, 29.04, 29.04, 29.04, 29.04, 29.04, 34.35, 34.35, 29.04, 29.04, 29.04, 29.04, 22.64, 22.64]}], \"Active\": true, \"LastUpdate\": \"2022-08-19\", \"Reference\": \"https://www.flogas.ie/residential/price-plans/smart-electricity/green-future-smart-35-discount-new.html\"}, {\"Supplier\": \"Pinergy\", \"Plan\": \"Lifestyle EV Drive Time\", \"Rates\": [{\"Days\": [0, 1, 2, 3, 4, 5, 6], \"Hours\": [43.34, 43.34, 6.43, 6.43, 6.43, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34, 43.34]}], \"Feed\": 21.0, \"Standing charges\": 283.47, \"Bonus cash\": 0.0, \"Active\": true, \"LastUpdate\": \"2022-09-05\", \"Reference\": \"https://pinergy.ie/terms-conditions/tariffs/\"}, {\"Supplier\": \"SSE Airtricity\", \"Plan\": \"Electricity 35\", \"Feed\": 14.0, \"Standing charges\": 240.9, \"Bonus cash\": 0.0, \"Rates\": [{\"Days\": [0, 1, 2, 3, 4, 5, 6], \"Hours\": [21.24, 21.24, 21.24, 21.24, 21.24, 21.24, 21.24, 21.24, 32.73, 32.73, 32.73, 32.73, 32.73, 32.73, 32.73, 32.73, 32.73, 41.35, 41.35, 32.73, 32.73, 32.73, 32.73, 21.24, 21.24]}], \"Active\": true, \"LastUpdate\": \"2022-10-01\", \"Reference\": \"https://www.sseairtricity.com/ie/home/products/electricity-top-discount/  https://www.sseairtricity.com/news/sse-airtricity-announces-price-change-in-response-to-sustained-increases-in-wholesale-energy-costs-26aug2022/\"}, {\"Supplier\": \"SSE Airtricity\", \"Plan\": \"D/N 35\", \"Feed\": 14.0, \"Standing charges\": 309.56, \"Bonus cash\": 0.0, \"Rates\": [{\"Days\": [0, 1, 2, 3, 4, 5, 6], \"Hours\": [12.24, 12.24, 12.24, 12.24, 12.24, 12.24, 12.24, 12.24, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 22.54, 12.24, 12.24]}], \"Active\": true, \"LastUpdate\": \"2022-08-06\", \"Reference\": \"https://www.sseairtricity.com/ie/home/products/electricity-top-discount/\"}]";

    @Before
    public void setUp()  {
        Context context = ApplicationProvider.getApplicationContext();
        context.startForegroundService(new Intent(context, ServedService.class));
        toutcDB = Room.inMemoryDatabaseBuilder(context, ToutcDB.class)
                .allowMainThreadQueries()
//                .setTransactionExecutor(Executors.newSingleThreadExecutor())
                .build(); //
        pricePlanDAO = toutcDB.pricePlanDAO();
    }

    @After
    public void tearDown() {
        toutcDB.close();
    }

//    @Test
//    public void addNewPricePlan() throws InterruptedException {
//
////        pricePlanDAO.deleteAll();
//        loadTestData();
//        Type type = new TypeToken<List<PricePlanJsonFile>>() {}.getType();
//        List<PricePlanJsonFile> ppList = new Gson().fromJson(testData, type);
//        PricePlan p = null;
//        long id = 0;
//        for (PricePlanJsonFile pp : ppList) {
//            p = JsonTools.createPricePlan(pp);
//            p.setPlanName(p.getPlanName() + "_1");
//            System.out.println("Inp: " + p.getPlanName() + "::" + p.getSupplier());
//            id = pricePlanDAO.addNewPricePlan(p);
//            System.out.println("The plan got id: " + id);
//            break;
//        }
//
//        Map<PricePlan, List<DayRate>> res = pricePlanDAO.loadPricePlan(id);
//        System.out.println("The query for "+ id + " returned: " + res.size());
//        for (PricePlan pp : res.keySet()) System.out.println("Res: " + pp.getPlanName() + "::" + pp.getSupplier());
////        assertTrue(res.keySet().contains(p));
//        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
//        System.out.println("The query returned: " + mpp.size());
//        for (PricePlan pp : mpp.keySet()) System.out.println("Res: " + pp.getPlanName() + "::" + pp.getSupplier());
//        assertTrue(mpp.keySet().contains(p));
//    }

    @Test
    public void deleteAll() throws InterruptedException {
        loadTestData();
        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assertEquals(mpp.keySet().size(), 10);
        pricePlanDAO.deleteAll();
        mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assertEquals(mpp.keySet().size(), 0);
    }

    @Test
    public void loadPricePlans() throws InterruptedException {
        loadTestData();
        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assertEquals(mpp.keySet().size(), 10);
    }

    private void loadTestData() {
        Type type = new TypeToken<List<PricePlanJsonFile>>() {}.getType();
        List<PricePlanJsonFile> ppList = new Gson().fromJson(testData, type);
        PricePlan p;
        for (PricePlanJsonFile pp : ppList) {
            p = JsonTools.createPricePlan(pp);
            ArrayList<DayRate> drs = new ArrayList<>();
            for (DayRateJson drj : pp.rates) {
                DayRate dr = JsonTools.createDayRate(drj);
                drs.add(dr);
            }
            pricePlanDAO.addNewPricePlanWithDayRates(p, drs);
        }
    }

//    @Test
//    public void addNewDayRate() {
//    }

    @Test
    public void addNewPricePlanWithDayRates() throws InterruptedException {
        loadTestData();
        Type type = new TypeToken<List<PricePlanJsonFile>>() {}.getType();
        List<PricePlanJsonFile> ppList = new Gson().fromJson(testData, type);
        PricePlan p = null;
        for (PricePlanJsonFile pp : ppList) {
            p = JsonTools.createPricePlan(pp);
            p.setPlanName(p.getPlanName() + "_1");
            ArrayList<DayRate> drs = new ArrayList<>();
            for (DayRateJson drj : pp.rates) {
                DayRate dr = JsonTools.createDayRate(drj);
                drs.add(dr);
            }
            pricePlanDAO.addNewPricePlanWithDayRates(p, drs);
            break;
        }
        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assertEquals(mpp.keySet().size(), 11);
        assertTrue(mpp.containsKey(p));
    }

//    @Test
//    public void clearDayRates() {
//    }
//
//    @Test
//    public void clearPricePlans() {
//    }

    @Test
    public void loadPricePlan() {
    }

    @Test
    public void deletePricePlan() throws InterruptedException {
        pricePlanDAO.deleteAll();
        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assertEquals(0, mpp.keySet().size());

        Type type = new TypeToken<List<PricePlanJsonFile>>() {}.getType();
        List<PricePlanJsonFile> ppList = new Gson().fromJson(testData, type);
        PricePlan p = null;
        for (PricePlanJsonFile pp : ppList) {
            p = JsonTools.createPricePlan(pp);
            p.setPlanName(p.getPlanName() + "_1");
            ArrayList<DayRate> drs = new ArrayList<>();
            for (DayRateJson drj : pp.rates) {
                DayRate dr = JsonTools.createDayRate(drj);
                drs.add(dr);
            }
            pricePlanDAO.addNewPricePlanWithDayRates(p, drs);
            break;
        }

        mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assert p != null;
        PricePlan pp = mpp.keySet().stream().filter(p::equals).findAny().orElse(null);
        assert pp != null;
        long ppID = pp.getPricePlanIndex();
        System.out.println("Got ID for " + pp.getPlanName() + " as " + ppID);

        pricePlanDAO.deletePricePlan(ppID);

        mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());

        assertFalse(mpp.containsKey(p));
    }

//    @Test
//    public void deleteDayRates() throws InterruptedException {
//        pricePlanDAO.deleteAll();
//        loadTestData();
//
//        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
//        PricePlan pp = mpp.keySet().iterator().next();
//        List<DayRate> drs = mpp.get(pp);
//        assertEquals(2, drs.size());
//        DayRate dr = drs.get(0);
//        pricePlanDAO.deleteDayRates(pp.getId());
//        mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
//        PricePlan ppo = mpp.keySet().iterator().next();
//        List<DayRate> drs0 = mpp.get(ppo);
//        assertEquals(0, drs0.size());
//    }

//    @Test
//    public void deletePricePlanRow() throws InterruptedException {
//
//    }

    @Test
    public void delpp() throws InterruptedException {
        pricePlanDAO.deleteAll();
        loadTestData();

        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        PricePlan pp = mpp.keySet().iterator().next();
        pricePlanDAO.deletePricePlan(pp);
        mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assertFalse(mpp.containsKey(pp));
    }

//    @Test
//    public void updatePricePlanActiveStatus() {
//    }

//    @Test
//    public void updatePricePlan() {
//    }
//
//    @Test
//    public void updateDayRate() {
//    }

    @Test
    public void updatePricePlanWithDayRates() throws InterruptedException {
        pricePlanDAO.deleteAll();
        PricePlan pp = new PricePlan();
        pp.setPlanName("TESTNAME");
        DayRate dr = new DayRate();
        ArrayList<DayRate> drs = new ArrayList<>();
        drs.add(dr);
        pricePlanDAO.updatePricePlanWithDayRates(pp, drs);
        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        PricePlan ppo = mpp.keySet().iterator().next();
        assertEquals("TESTNAME", ppo.getPlanName());
    }

    @Test
    public void getNameForPlanID() throws InterruptedException {
        pricePlanDAO.deleteAll();
        loadTestData();

        Map<PricePlan, List<DayRate>> mpp = LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        PricePlan pp = mpp.keySet().iterator().next();
        assertEquals("Free time Sat", pp.getPlanName());
    }
}