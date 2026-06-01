/*
 * Copyright (c) 2026. Tony Finnerty
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

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

/**
 * JSON → domain → Room → readback smoke test for price plans, exercising
 * the same code path as the app's "Load from JSON" feature. Catches any
 * regression in {@code JsonTools.createPricePlan}, {@code createDayRate},
 * the {@code MinuteRateRange.fromHours} fallback, or the DAO insert.
 *
 * Two fixtures: a single-rate flat plan and a multi-rate day/night plan.
 * Together they cover the simple path and the time-of-use band logic
 * which was the source of multiple regressions earlier this cycle.
 */
@RunWith(AndroidJUnit4.class)
public class SmokeImportTest {

    @Rule
    public final InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ToutcDB toutcDB;
    private PricePlanDAO pricePlanDAO;

    // Single-rate plan: 24×same-value hours, weekdays + weekends.
    private static final String FLAT_PLAN_JSON =
            "[{\"Supplier\":\"SmokeTest\",\"Plan\":\"FlatRate\",\"Feed\":15.0," +
            "\"Standing charges\":300.0,\"Bonus cash\":0.0," +
            "\"Rates\":[{\"Days\":[0,1,2,3,4,5,6]," +
            "\"Hours\":[25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0," +
            "25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0]}]," +
            "\"Active\":true,\"LastUpdate\":\"2026-01-01\"," +
            "\"Reference\":\"smoke-test\"}]";

    // Multi-rate plan: weekday day/night pattern.
    private static final String DAY_NIGHT_PLAN_JSON =
            "[{\"Supplier\":\"SmokeTest\",\"Plan\":\"DayNight\",\"Feed\":18.0," +
            "\"Standing charges\":320.0,\"Bonus cash\":0.0," +
            "\"Rates\":[{\"Days\":[1,2,3,4,5]," +
            "\"Hours\":[12.0,12.0,12.0,12.0,12.0,12.0,12.0,12.0," +
            "30.0,30.0,30.0,30.0,30.0,30.0,30.0,30.0,30.0," +
            "45.0,45.0,30.0,30.0,30.0,30.0,12.0,12.0]}," +
            "{\"Days\":[0,6]," +
            "\"Hours\":[12.0,12.0,12.0,12.0,12.0,12.0,12.0,12.0," +
            "25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0,25.0," +
            "30.0,30.0,25.0,25.0,25.0,25.0,12.0,12.0]}]," +
            "\"Active\":true,\"LastUpdate\":\"2026-01-01\"," +
            "\"Reference\":\"smoke-test\"}]";

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        toutcDB = Room.inMemoryDatabaseBuilder(context, ToutcDB.class)
                .allowMainThreadQueries()
                .build();
        pricePlanDAO = toutcDB.pricePlanDAO();
    }

    @After
    public void tearDown() {
        toutcDB.close();
    }

    @Test
    public void flatPlanRoundtrips() throws InterruptedException {
        importJson(FLAT_PLAN_JSON);
        Map<PricePlan, List<DayRate>> loaded =
                LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assertEquals("expected one plan after insert", 1, loaded.size());
        PricePlan p = loaded.keySet().iterator().next();
        assertEquals("FlatRate", p.getPlanName());
        assertEquals("SmokeTest", p.getSupplier());
        List<DayRate> rates = loaded.get(p);
        assertNotNull("day rates must be present", rates);
        assertFalse("at least one day-rate row", rates.isEmpty());
    }

    @Test
    public void dayNightPlanRoundtrips() throws InterruptedException {
        importJson(DAY_NIGHT_PLAN_JSON);
        Map<PricePlan, List<DayRate>> loaded =
                LiveDataTestUtil.getValue(pricePlanDAO.loadPricePlans());
        assertEquals(1, loaded.size());
        PricePlan p = loaded.keySet().iterator().next();
        assertEquals("DayNight", p.getPlanName());
        List<DayRate> rates = loaded.get(p);
        assertNotNull(rates);
        // Day-of-week tiling: weekdays + weekends = two groups expected.
        assertTrue("expected at least two day-rate rows for weekday/weekend split",
                rates.size() >= 2);
        // Verify minute-rate band reconstruction (fromHours fallback).
        for (DayRate dr : rates) {
            assertNotNull("each day-rate must have a minute-rate range", dr.getMinuteRateRange());
            assertFalse("minute-rate range must be populated",
                    dr.getMinuteRateRange().getRates().isEmpty());
        }
    }

    private void importJson(String json) {
        Type type = new TypeToken<List<PricePlanJsonFile>>() {}.getType();
        List<PricePlanJsonFile> ppList = new Gson().fromJson(json, type);
        assertNotNull("fixture JSON must parse", ppList);
        for (PricePlanJsonFile ppj : ppList) {
            PricePlan p = JsonTools.createPricePlan(ppj);
            ArrayList<DayRate> drs = new ArrayList<>();
            for (DayRateJson drj : ppj.rates) {
                drs.add(JsonTools.createDayRate(drj));
            }
            pricePlanDAO.addNewPricePlanWithDayRates(p, drs, false);
        }
    }
}
