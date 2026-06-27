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

package com.tfcode.comparetout.model.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tfcode.comparetout.model.json.scenario.HeatPumpJson;
import com.tfcode.comparetout.model.scenario.HeatPump;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** Phase-4 JSON round-trip for the heat pump (guide §5): entity → JSON → entity preserves the config. */
public class HeatPumpJsonTest {

    @Test
    public void roundTripPreservesConfig() {
        HeatPump hp = new HeatPump();
        hp.setFuelType("Natural gas");
        hp.setFuelAnnual(18000d);
        hp.setBoilerEfficiency(0.90d);
        hp.setSpaceHeatingFraction(0.75d);
        hp.setDesiredIndoorTemp(21d);
        hp.setCurrentIndoorTemp(18d);
        hp.setAlphaWind(0.05d);
        hp.setHeatingSeasonStart(274);
        hp.setHeatingSeasonEnd(120);
        hp.setScop(3.9d);
        hp.setCapacityKw(9d);
        hp.setBackupHeater(false);
        hp.setLatitude(53.34d);
        hp.setLongitude(-6.26d);
        hp.setWeatherSource("cds");

        List<HeatPumpJson> json = JsonTools.createHeatPumpListJson(Collections.singletonList(hp));
        HeatPump back = JsonTools.createHeatPumpList(json).get(0);

        assertEquals("Natural gas", back.getFuelType());
        assertEquals(18000d, back.getFuelAnnual(), 0d);
        assertEquals(0.90d, back.getBoilerEfficiency(), 0d);
        assertEquals(0.75d, back.getSpaceHeatingFraction(), 0d);
        assertEquals(21d, back.getDesiredIndoorTemp(), 0d);
        assertEquals(18d, back.getCurrentIndoorTemp(), 0d);
        assertEquals(0.05d, back.getAlphaWind(), 0d);
        assertEquals(Integer.valueOf(274), back.getHeatingSeasonStart());
        assertEquals(Integer.valueOf(120), back.getHeatingSeasonEnd());
        assertEquals(3.9d, back.getScop(), 0d);
        assertEquals(9d, back.getCapacityKw(), 0d);
        assertTrue(!back.isBackupHeater());
        assertEquals(-6.26d, back.getLongitude(), 0d);
        assertEquals("cds", back.getWeatherSource());
        assertEquals(24, back.getHourlyDist().dist.size());
        assertEquals(7, back.getDowDist().dowDist.size());
    }

    @Test
    public void roundTripPreservesNewBuildFabricInputs() {
        HeatPump hp = new HeatPump();
        hp.setFuelType("None");
        hp.setFuelAnnual(2300d);   // still present (import gate requires it), but unused for a new build
        hp.setFloorAreaM2(175d);
        hp.setHeatLossIndex(0.9d);

        List<HeatPumpJson> json = JsonTools.createHeatPumpListJson(Collections.singletonList(hp));
        HeatPump back = JsonTools.createHeatPumpList(json).get(0);

        assertEquals("None", back.getFuelType());
        assertEquals(175d, back.getFloorAreaM2(), 0d);
        assertEquals(0.9d, back.getHeatLossIndex(), 0d);
    }

    @Test
    public void emptyJsonObjectDoesNotMaterialiseADefaultHeatPump() {
        // A partial "{}" (no required fuel figure) must not become a fully-defaulted heat pump.
        HeatPumpJson empty = new Gson().fromJson("{}", HeatPumpJson.class);
        assertNull(JsonTools.createHeatPump(empty));
        assertTrue(JsonTools.createHeatPumpList(Collections.singletonList(empty)).isEmpty());
    }
}
