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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile;
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;
import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HeatPump;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.Panel;
import com.tfcode.comparetout.model.scenario.ScenarioComponents;
import com.tfcode.comparetout.testdata.FullScenarioFixture;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * Full-fat JsonTools round trips ahead of its facade split (Phase C10):
 * entities → JSON → entities must preserve every component type, including
 * the newer optional fields (schedule windows, PV provenance, heat pump).
 * Pure JVM — no Robolectric needed.
 */
public class JsonToolsFullRoundTripTest {

    private static final double D = 1e-9;

    private static ScenarioComponents roundTrip(ScenarioComponents in) {
        ScenarioJsonFile sjf = JsonTools.createSingleScenarioJson(
                in.scenario, in.inverters, in.batteries, in.panels, in.hwSystem,
                in.loadProfile, in.loadShifts, in.discharges, in.evCharges,
                in.hwSchedules, in.hwDivert, in.evDiverts, in.heatPumps);
        String json = JsonTools.createSingleScenarioJsonString(sjf);
        ScenarioJsonFile parsed = new Gson().fromJson(json, ScenarioJsonFile.class);
        return JsonTools.createScenarioComponentList(
                Collections.singletonList(parsed)).get(0);
    }

    @Test
    public void fullFatScenarioSurvivesEntityJsonEntity() {
        ScenarioComponents in = FullScenarioFixture.components();
        ScenarioComponents out = roundTrip(in);

        assertEquals(in.scenario.getScenarioName(), out.scenario.getScenarioName());

        assertEquals(2, out.inverters.size());
        for (Inverter a : in.inverters) {
            Inverter b = byName(out.inverters, x -> x.getInverterName(), a.getInverterName());
            assertEquals(a.getMinExcess(), b.getMinExcess(), D);
            assertEquals(a.getMaxInverterLoad(), b.getMaxInverterLoad(), D);
            assertEquals(a.getMpptCount(), b.getMpptCount());
            assertEquals(a.getAc2dcLoss(), b.getAc2dcLoss());
            assertEquals(a.getDc2acLoss(), b.getDc2acLoss());
            assertEquals(a.getDc2dcLoss(), b.getDc2dcLoss());
            assertEquals(a.getDispatchMode(), b.getDispatchMode());
        }

        assertEquals(2, out.batteries.size());
        for (Battery a : in.batteries) {
            Battery b = byName(out.batteries, x -> x.getInverter(), a.getInverter());
            assertEquals(a.getBatterySize(), b.getBatterySize(), D);
            assertEquals(a.getDischargeStop(), b.getDischargeStop(), D);
            assertEquals(a.getMaxDischarge(), b.getMaxDischarge(), D);
            assertEquals(a.getMaxCharge(), b.getMaxCharge(), D);
            assertEquals(a.getStorageLoss(), b.getStorageLoss(), D);
        }

        assertEquals(2, out.panels.size());
        for (Panel a : in.panels) {
            Panel b = byName(out.panels, x -> x.getPanelName(), a.getPanelName());
            assertEquals(a.getPanelCount(), b.getPanelCount());
            assertEquals(a.getPanelkWp(), b.getPanelkWp());
            assertEquals(a.getAzimuth(), b.getAzimuth());
            assertEquals(a.getSlope(), b.getSlope());
            assertEquals(a.getLatitude(), b.getLatitude(), D);
            assertEquals(a.getLongitude(), b.getLongitude(), D);
            assertEquals(a.getInverter(), b.getInverter());
            assertEquals(a.getMppt(), b.getMppt());
            assertEquals("PV provenance must survive", a.getDataSource(), b.getDataSource());
            assertEquals(a.getDataStartDate(), b.getDataStartDate());
            assertEquals(a.getDataEndDate(), b.getDataEndDate());
            assertEquals(a.getSystemLoss(), b.getSystemLoss());
        }

        assertEquals(in.hwSystem.getHwCapacity(), out.hwSystem.getHwCapacity());
        assertEquals(in.hwSystem.getHwUsage(), out.hwSystem.getHwUsage());
        assertEquals(in.hwSystem.getHwIntake(), out.hwSystem.getHwIntake());
        assertEquals(in.hwSystem.getHwTarget(), out.hwSystem.getHwTarget());
        assertEquals(in.hwSystem.getHwLoss(), out.hwSystem.getHwLoss());
        assertEquals(in.hwSystem.getHwRate(), out.hwSystem.getHwRate(), D);

        assertEquals(in.loadProfile.getAnnualUsage(), out.loadProfile.getAnnualUsage(), D);
        assertEquals(in.loadProfile.getHourlyBaseLoad(), out.loadProfile.getHourlyBaseLoad(), D);
        assertEquals(in.loadProfile.getGridImportMax(), out.loadProfile.getGridImportMax(), D);
        assertEquals(in.loadProfile.getGridExportMax(), out.loadProfile.getGridExportMax(), D);

        assertEquals(2, out.loadShifts.size());
        for (LoadShift a : in.loadShifts) {
            LoadShift b = byName(out.loadShifts, x -> x.getName(), a.getName());
            assertEquals(a.getBegin(), b.getBegin());
            assertEquals(a.getEnd(), b.getEnd());
            assertEquals(a.getStopAt(), b.getStopAt(), D);
            assertEquals(a.getInverter(), b.getInverter());
            assertEquals("schedule window must survive", a.getStartDate(), b.getStartDate());
            assertEquals(a.getEndDate(), b.getEndDate());
            assertEquals(a.getBeginMinute(), b.getBeginMinute());
            assertEquals(a.getEndMinute(), b.getEndMinute());
        }

        assertEquals(2, out.discharges.size());
        for (DischargeToGrid a : in.discharges) {
            DischargeToGrid b = byName(out.discharges, x -> x.getName(), a.getName());
            assertEquals(a.getStopAt(), b.getStopAt(), D);
            assertEquals(a.getRate(), b.getRate(), D);
            assertEquals(a.getStartDate(), b.getStartDate());
            assertEquals(a.getBeginMinute(), b.getBeginMinute());
        }

        assertEquals(2, out.evCharges.size());
        for (EVCharge a : in.evCharges) {
            EVCharge b = byName(out.evCharges, x -> x.getName(), a.getName());
            assertEquals(a.getDraw(), b.getDraw(), D);
            assertEquals(a.getStartDate(), b.getStartDate());
            assertEquals(a.getEndMinute(), b.getEndMinute());
        }

        assertEquals(2, out.hwSchedules.size());
        for (HWSchedule a : in.hwSchedules) {
            HWSchedule b = byName(out.hwSchedules, x -> x.getName(), a.getName());
            assertEquals(a.getBegin(), b.getBegin());
            assertEquals(a.getEnd(), b.getEnd());
            assertEquals(a.getStartDate(), b.getStartDate());
            assertEquals(a.getBeginMinute(), b.getBeginMinute());
        }

        assertEquals(in.hwDivert.isActive(), out.hwDivert.isActive());

        assertEquals(1, out.evDiverts.size());
        EVDivert evd = out.evDiverts.get(0);
        EVDivert evdIn = in.evDiverts.get(0);
        assertEquals(evdIn.getName(), evd.getName());
        assertEquals(evdIn.isActive(), evd.isActive());
        assertEquals(evdIn.isEv1st(), evd.isEv1st());
        assertEquals(evdIn.getDailyMax(), evd.getDailyMax(), D);
        assertEquals(evdIn.getMinimum(), evd.getMinimum(), D);
        assertEquals(evdIn.getStartDate(), evd.getStartDate());
        assertEquals(evdIn.getBeginMinute(), evd.getBeginMinute());

        assertEquals(1, out.heatPumps.size());
        HeatPump hpIn = in.heatPumps.get(0);
        HeatPump hp = out.heatPumps.get(0);
        assertEquals(hpIn.getFuelType(), hp.getFuelType());
        assertEquals(hpIn.getFuelAnnual(), hp.getFuelAnnual(), D);
        assertEquals(hpIn.getCalorificValue(), hp.getCalorificValue(), D);
        assertEquals(hpIn.getBoilerEfficiency(), hp.getBoilerEfficiency(), D);
        assertEquals(hpIn.getDhwAnnualKWh(), hp.getDhwAnnualKWh(), D);
        assertEquals(hpIn.getSpaceHeatingFraction(), hp.getSpaceHeatingFraction());
        assertEquals(hpIn.getFloorAreaM2(), hp.getFloorAreaM2(), D);
        assertEquals(hpIn.getHeatLossIndex(), hp.getHeatLossIndex(), D);
        assertEquals(hpIn.getDesiredIndoorTemp(), hp.getDesiredIndoorTemp(), D);
        assertEquals(hpIn.getCurrentIndoorTemp(), hp.getCurrentIndoorTemp(), D);
        assertEquals(hpIn.getBalancePoint(), hp.getBalancePoint(), D);
        assertEquals(hpIn.getAlphaWind(), hp.getAlphaWind(), D);
        assertEquals(hpIn.getHeatingSeasonStart(), hp.getHeatingSeasonStart());
        assertEquals(hpIn.getHeatingSeasonEnd(), hp.getHeatingSeasonEnd());
        assertEquals(hpIn.getCopRated(), hp.getCopRated(), D);
        assertEquals(hpIn.getCopRefTemp(), hp.getCopRefTemp(), D);
        assertEquals(hpIn.getCopSlope(), hp.getCopSlope(), D);
        assertEquals(hpIn.getScop(), hp.getScop(), D);
        assertEquals(hpIn.getCapacityKw(), hp.getCapacityKw(), D);
        assertEquals(hpIn.isBackupHeater(), hp.isBackupHeater());
        assertEquals(hpIn.getLatitude(), hp.getLatitude(), D);
        assertEquals(hpIn.getLongitude(), hp.getLongitude(), D);
        assertEquals(hpIn.getWeatherSource(), hp.getWeatherSource());
    }

    @Test
    public void scenarioListStringRoundTrips() {
        ScenarioComponents in = FullScenarioFixture.components();
        String listJson = JsonTools.createScenarioList(Collections.singletonList(in));

        Type type = new TypeToken<List<ScenarioJsonFile>>() {}.getType();
        List<ScenarioJsonFile> parsed = new Gson().fromJson(listJson, type);
        List<ScenarioComponents> back = JsonTools.createScenarioComponentList(parsed);

        assertEquals(1, back.size());
        assertEquals(in.scenario.getScenarioName(), back.get(0).scenario.getScenarioName());
        assertEquals(1, back.get(0).heatPumps.size());
        assertEquals(2, back.get(0).panels.size());
    }

    @Test
    public void pricePlanSurvivesEntityJsonEntity() {
        PricePlan pp = new PricePlan();
        pp.setSupplier("Fixture Energy");
        pp.setPlanName("Fixture · Day/Night");
        pp.setFeed(21.5);
        pp.setStandingCharges(220.75);
        pp.setSignUpBonus(50.0);
        pp.setReference("https://example.invalid/fixture");
        pp.setLastUpdate("2026-07-19");

        DayRate dr = new DayRate();
        dr.setStartDate("01/01");
        dr.setEndDate("12/31");
        MinuteRateRange mrr = new MinuteRateRange();
        mrr.add(0, 480, 18.0);
        mrr.add(480, 1440, 34.5);
        dr.setMinuteRateRange(mrr);

        String json = JsonTools.createSinglePricePlanJsonObject(
                pp, Collections.singletonList(dr));
        PricePlanJsonFile parsed = new Gson().fromJson(json, PricePlanJsonFile.class);

        PricePlan ppOut = JsonTools.createPricePlan(parsed);
        assertEquals(pp.getSupplier(), ppOut.getSupplier());
        assertEquals(pp.getPlanName(), ppOut.getPlanName());
        assertEquals(pp.getFeed(), ppOut.getFeed(), D);
        assertEquals(pp.getStandingCharges(), ppOut.getStandingCharges(), D);
        assertEquals(pp.getSignUpBonus(), ppOut.getSignUpBonus(), D);
        assertEquals(pp.getReference(), ppOut.getReference());

        assertNotNull(parsed.rates);
        assertEquals(1, parsed.rates.size());
        DayRate drOut = JsonTools.createDayRate(parsed.rates.get(0));
        assertEquals("01/01", drOut.getStartDate());
        assertEquals("12/31", drOut.getEndDate());
        assertEquals(dr.getMinuteRateRange().lookup(0),
                drOut.getMinuteRateRange().lookup(0), D);
        assertEquals(dr.getMinuteRateRange().lookup(500),
                drOut.getMinuteRateRange().lookup(500), D);
    }

    @Test
    public void sampleAssetPlansParseThroughTheProductionPath() {
        // cwd for unit tests is app/ (the golden-master harness relies on it).
        Type type = new TypeToken<List<PricePlanJsonFile>>() {}.getType();
        List<PricePlanJsonFile> plans;
        try (java.io.Reader r = new java.io.InputStreamReader(
                new java.io.FileInputStream("src/main/assets/samples/sample_price_plans.json"),
                java.nio.charset.StandardCharsets.UTF_8)) {
            plans = new Gson().fromJson(r, type);
        } catch (java.io.IOException e) {
            throw new AssertionError("sample_price_plans.json unreadable", e);
        }
        assertTrue(plans.size() >= 2);
        for (PricePlanJsonFile p : plans) {
            PricePlan pp = JsonTools.createPricePlan(p);
            assertNotNull(pp.getPlanName());
            assertTrue(pp.getStandingCharges() > 0);
            assertNotNull(p.rates);
            assertTrue(p.rates.size() >= 1);
            assertNotNull(JsonTools.createDayRate(p.rates.get(0)));
        }
    }

    private interface Namer<T> { String name(T t); }

    private static <T> T byName(List<T> list, Namer<T> namer, String name) {
        for (T t : list) if (name.equals(namer.name(t))) return t;
        throw new AssertionError("No element named '" + name + "'");
    }
}
