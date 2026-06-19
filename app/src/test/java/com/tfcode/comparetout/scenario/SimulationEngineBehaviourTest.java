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

package com.tfcode.comparetout.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.model.scenario.ScenarioSimulationData;
import com.tfcode.comparetout.model.scenario.SimulationInputData;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Black-box behavioural specs for {@link SimulationEngine#processOneRow}, exercised through the public
 * surface only (build inputs, run, assert on the output rows). These replace the directional assertions
 * the retired {@code SimulationWorkerHelperTest}/{@code …IntegrationTest}/{@code …Test} used to make while
 * poking {@code InputData}'s internal fields. Exact full-output behaviour is pinned separately by the
 * golden master; these read as intention-revealing specs.
 *
 * <p>Each scenario runs row 0 (which seeds SOC at the discharge-stop floor) then row 1, and asserts on
 * row 1 — matching the legacy convention of avoiding first-row initialisation artefacts.</p>
 */
public class SimulationEngineBehaviourTest {

    private static final ScenarioInputs NO_EXTRAS = new ScenarioInputs(null, null, null, null, null, 0d);

    private static SimulationInputData sid(double load, double tpv) {
        SimulationInputData s = new SimulationInputData();
        s.setDate("2001-01-01");
        s.setMinute("12:00");
        s.setMod(720);
        s.setDow(1);
        s.setDo2001(1);
        s.setLoad(load);
        s.setTpv(tpv);
        return s;
    }

    private static List<SimulationInputData> series(double l0, double p0, double l1, double p1) {
        List<SimulationInputData> s = new ArrayList<>();
        s.add(sid(l0, p0));
        s.add(sid(l1, p1));
        return s;
    }

    private static Inverter inverter(double minExcess, double maxInverterLoad, boolean lossless) {
        Inverter inv = new Inverter();
        inv.setInverterIndex(1);
        inv.setMinExcess(minExcess);
        inv.setMaxInverterLoad(maxInverterLoad);
        if (lossless) {
            inv.setDc2acLoss(0);
            inv.setAc2dcLoss(0);
            inv.setDc2dcLoss(0);
        }
        return inv;
    }

    private static Battery battery(double size, double maxCharge, double maxDischarge, double dischargeStopPct) {
        Battery b = new Battery();
        b.setBatterySize(size);
        b.setMaxCharge(maxCharge);
        b.setMaxDischarge(maxDischarge);
        b.setDischargeStop(dischargeStopPct);
        ChargeModel cm = new ChargeModel();
        cm.percent0 = 100;
        cm.percent12 = 100;
        cm.percent90 = 50;
        cm.percent100 = 0;
        b.setChargeModel(cm);
        return b;
    }

    /** Runs row 0 then row 1 for a single inverter and returns the row-1 output. */
    private static ScenarioSimulationData runRow1(Inverter inv, SimulationEngine.InputData iData) {
        Map<Inverter, SimulationEngine.InputData> map = new HashMap<>();
        map.put(inv, iData);
        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(1L, NO_EXTRAS, out, 0, map);
        SimulationEngine.processOneRow(1L, NO_EXTRAS, out, 1, map);
        assertEquals(2, out.size());
        return out.get(1);
    }

    private static SimulationEngine.InputData input(Inverter inv, List<SimulationInputData> s,
                                                    Battery b, SimulationEngine.ChargeFromGrid cfg) {
        return new SimulationEngine.InputData(inv, s, b, cfg, null);
    }

    @Test
    public void emptyInverterMapProducesNoOutput() {
        ArrayList<ScenarioSimulationData> out = new ArrayList<>();
        SimulationEngine.processOneRow(1L, NO_EXTRAS, out, 1, new HashMap<>());
        assertTrue("empty map -> graceful no-op", out.isEmpty());
    }

    @Test
    public void loadExceedingSupplyBuysFromGrid() {
        Inverter inv = inverter(0, 0, false);
        SimulationEngine.InputData iData = input(inv, series(3.0, 1.0, 3.1, 1.1), battery(10, 2, 3, 20), null);
        ScenarioSimulationData row = runRow1(inv, iData);
        assertTrue("buys when load exceeds PV + battery", row.getBuy() > 0);
        assertEquals(3.1, row.getLoad(), 1e-9);
        assertEquals(1.1, row.getPv(), 1e-9);
    }

    @Test
    public void surplusBelowMinExcessIsNotFed() {
        Inverter inv = inverter(0.5, 0, true);             // lossless, minExcess 0.5
        // battery full (dischargeStop 100 -> seeded to size) so no charging; surplus 0.3 < minExcess.
        SimulationEngine.InputData iData = input(inv, series(1.0, 1.3, 1.1, 1.4), battery(10, 2, 0, 100), null);
        ScenarioSimulationData row = runRow1(inv, iData);
        assertEquals("no feed below the minimum-excess threshold", 0.0, row.getFeed(), 1e-9);
        assertEquals("battery full -> no charge", 0.0, row.getPvToCharge(), 1e-9);
    }

    @Test
    public void surplusPvChargesBatteryAndRaisesSoc() {
        Inverter inv = inverter(0, 0, false);
        SimulationEngine.InputData iData = input(inv, series(1.0, 5.0, 1.2, 4.8), battery(10, 2, 3, 20), null);
        ScenarioSimulationData row = runRow1(inv, iData);
        assertTrue("PV surplus charges the battery", row.getPvToCharge() > 0);
        assertTrue("SOC rises above the discharge-stop floor (2.0)", row.getSOC() > 2.0);
    }

    @Test
    public void chargeFromGridRaisesGridToBatteryAndBuy() {
        LoadShift allDay = new LoadShift();
        allDay.setBegin(0);
        allDay.setEnd(24);
        allDay.setStopAt(90.0);
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(Collections.singletonList(allDay), 105120);
        Inverter inv = inverter(0, 0, false);
        SimulationEngine.InputData iData = input(inv, series(2.0, 1.0, 2.1, 0.9), battery(10, 2, 0, 20), cfg);
        ScenarioSimulationData row = runRow1(inv, iData);
        assertTrue("grid charges the battery", row.getGridToBattery() > 0);
        assertTrue("buy includes the grid charging", row.getBuy() > 1.2);
    }

    @Test
    public void batteryDoesNotDischargeBelowStop() {
        Inverter inv = inverter(0, 0, false);
        Battery b = battery(10, 2, 3, 20); // stop = 2.0 kWh; row 0 seeds SOC there
        b.setStorageLoss(5.0);
        SimulationEngine.InputData iData = input(inv, series(5.0, 0.0, 4.8, 0.0), b, null);
        ScenarioSimulationData row = runRow1(inv, iData);
        assertEquals("no discharge below the stop", 0.0, row.getBatToLoad(), 1e-9);
        assertEquals("SOC held at the stop", 2.0, row.getSOC(), 1e-9);
        assertEquals("all load bought from the grid", 4.8, row.getBuy(), 1e-9);
    }

    // NB: the AC-throughput cap on feed/export is covered exactly by
    // SimulationEngineBusModelTest.acRatingBindsAndCurtailsSurplusWithoutBattery (with a non-zero export
    // cap). It is not re-asserted here: these behaviour runs use exportMax = 0, so feed is always 0 and any
    // "feed <= cap" assertion would be vacuous. DC-DC charging deliberately bypasses the AC cap — see
    // SimulationEngineBusModelTest.dcDcChargingGrabsPvBeyondAcRating.

    @Test
    public void rowOutputReflectsItsOwnInput() {
        Inverter inv = inverter(0, 0, false);
        SimulationEngine.InputData iData = input(inv, series(1.0, 2.0, 1.5, 1.8), battery(10, 2, 3, 20), null);
        ScenarioSimulationData row = runRow1(inv, iData);
        assertEquals(1.5, row.getLoad(), 1e-9);
        assertEquals(1.8, row.getPv(), 1e-9);
    }
}
