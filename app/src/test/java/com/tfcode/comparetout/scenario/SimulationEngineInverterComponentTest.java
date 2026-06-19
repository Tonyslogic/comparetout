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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.scenario.Battery;
import com.tfcode.comparetout.model.scenario.ChargeModel;
import com.tfcode.comparetout.model.scenario.Inverter;
import com.tfcode.comparetout.model.scenario.LoadShift;
import com.tfcode.comparetout.scenario.sim.InverterComponent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Black-box coverage of the inverter component's {@code Converter}/{@code Storage}/{@code GridExchange}
 * capabilities — the battery and converter math exercised entirely through the public capability surface
 * (no field access). Replaces the white-box {@code InputData} assertions that the retired
 * {@code SimulationWorkerTest}/{@code SimulationWorkerEdgeCaseTest} used to make against internal fields.
 */
public class SimulationEngineInverterComponentTest {

    private static final double TOL = 1e-9;

    private static Inverter inverter(int dc2ac, int ac2dc, int dc2dc) {
        Inverter inv = new Inverter();
        inv.setInverterIndex(1);
        inv.setDc2acLoss(dc2ac);
        inv.setAc2dcLoss(ac2dc);
        inv.setDc2dcLoss(dc2dc);
        return inv;
    }

    private static Battery battery(double size, double maxCharge, double maxDischarge, double dischargeStopPct,
                                   int p0, int p12, int p90, int p100) {
        Battery b = new Battery();
        b.setBatterySize(size);
        b.setMaxCharge(maxCharge);
        b.setMaxDischarge(maxDischarge);
        b.setDischargeStop(dischargeStopPct);
        ChargeModel cm = new ChargeModel();
        cm.percent0 = p0;
        cm.percent12 = p12;
        cm.percent90 = p90;
        cm.percent100 = p100;
        b.setChargeModel(cm);
        return b;
    }

    private static InverterComponent component(Inverter inv, Battery battery, SimulationEngine.ChargeFromGrid cfg) {
        return new SimulationEngine.InputData(inv, new ArrayList<>(), battery, cfg, null);
    }

    // ---- Converter: efficiencies derived from the inverter (kept fraction = (100 - loss)/100) ----

    @Test
    public void efficienciesAndIdentityDerivedFromInverter() {
        Inverter inv = inverter(5, 3, 2);
        inv.setInverterIndex(7);
        inv.setMaxInverterLoad(3.5);
        inv.setMinExcess(0.2);
        InverterComponent c = component(inv, null, null);

        assertEquals(7, c.inverterIndex());
        assertEquals(0.95, c.dc2acLoss(), TOL);
        assertEquals(0.97, c.ac2dcLoss(), TOL);
        assertEquals(0.98, c.dc2dcLoss(), TOL);
        assertEquals(3.5, c.maxInverterLoad(), TOL);
        assertEquals(0.2, c.minExcess(), TOL);
    }

    @Test
    public void hundredPercentLossGivesZeroEfficiency_zeroLossGivesUnity() {
        InverterComponent lossy = component(inverter(100, 100, 100), null, null);
        assertEquals(0.0, lossy.dc2acLoss(), TOL);
        assertEquals(0.0, lossy.ac2dcLoss(), TOL);
        assertEquals(0.0, lossy.dc2dcLoss(), TOL);

        InverterComponent lossless = component(inverter(0, 0, 0), null, null);
        assertEquals(1.0, lossless.dc2acLoss(), TOL);
        assertEquals(1.0, lossless.ac2dcLoss(), TOL);
        assertEquals(1.0, lossless.dc2dcLoss(), TOL);
    }

    @Test
    public void storageLossFromBattery_zeroWhenNoBattery() {
        Battery b = battery(10, 2, 2, 20, 100, 100, 50, 0);
        b.setStorageLoss(1.5);
        assertEquals(1.5, component(inverter(0, 0, 0), b, null).storageLoss(), TOL);
        assertEquals(0.0, component(inverter(0, 0, 0), null, null).storageLoss(), TOL);
    }

    // ---- Storage: discharge stop, charge capacity (taper), discharge capacity ----

    @Test
    public void dischargeStopScalesWithPercentAndSize() {
        assertEquals(2.0, component(inverter(0, 0, 0), battery(10, 2, 3, 20, 100, 100, 50, 0), null).getDischargeStop(), TOL);
        assertEquals(0.0, component(inverter(0, 0, 0), battery(10, 2, 3, 0, 100, 100, 50, 0), null).getDischargeStop(), TOL);
        assertEquals(10.0, component(inverter(0, 0, 0), battery(10, 2, 3, 100, 100, 100, 50, 0), null).getDischargeStop(), TOL);
    }

    @Test
    public void chargeCapacityFollowsChargeModelTaperAcrossSocRegions() {
        // Big battery so the charge-model taper (not the remaining headroom) is the binding limit.
        Battery b = battery(100, 10, 10, 0, 100, 80, 30, 0);
        InverterComponent c = component(inverter(0, 0, 0), b, null);

        c.setSoc(6);    assertEquals("0-12% region uses percent0", 10.0, c.getChargeCapacity(), TOL);
        c.setSoc(12);   assertEquals("at 12% still percent0", 10.0, c.getChargeCapacity(), TOL);
        c.setSoc(50);   assertEquals("12-90% region uses percent12", 8.0, c.getChargeCapacity(), TOL);
        c.setSoc(90);   assertEquals("at 90% still percent12", 8.0, c.getChargeCapacity(), TOL);
        c.setSoc(90.1); assertEquals("above 90% uses percent90", 3.0, c.getChargeCapacity(), TOL);
        c.setSoc(100);  assertEquals("at 100% no charge", 0.0, c.getChargeCapacity(), TOL);
    }

    @Test
    public void chargeCapacityBoundedByRemainingHeadroom() {
        Battery b = battery(10, 2, 2, 0, 100, 100, 50, 0);
        InverterComponent c = component(inverter(0, 0, 0), b, null);
        c.setSoc(9.5);
        assertEquals("headroom (0.5) binds below the taper (1.0)", 0.5, c.getChargeCapacity(), TOL);
    }

    @Test
    public void noBatteryHasZeroCapacities() {
        InverterComponent c = component(inverter(0, 0, 0), null, null);
        assertEquals(0.0, c.soc(), TOL);
        assertEquals(0.0, c.getChargeCapacity(), TOL);
        assertEquals(0.0, c.getDischargeCapacity(0), TOL);
    }

    @Test
    public void dischargeCapacityBoundedByRateAndStop_neverNegative() {
        Battery b = battery(10, 2, 3, 20, 100, 100, 50, 0); // stop = 2.0 kWh, maxDischarge = 3.0
        InverterComponent c = component(inverter(0, 0, 0), b, null);

        c.setSoc(5);  assertEquals("min(rate, soc-stop) = min(3, 3)", 3.0, c.getDischargeCapacity(0), TOL);
        c.setSoc(10); assertEquals("rate binds: min(3, 8)", 3.0, c.getDischargeCapacity(0), TOL);
        c.setSoc(2);  assertEquals("at stop -> nothing", 0.0, c.getDischargeCapacity(0), TOL);

        // SOC below the (zero) stop must never yield negative or more than is present.
        Battery deep = battery(10, 2, 15, 0, 100, 100, 50, 0);
        InverterComponent d = component(inverter(0, 0, 0), deep, null);
        d.setSoc(0.1);
        assertTrue(d.getDischargeCapacity(0) >= 0);
        assertTrue(d.getDischargeCapacity(0) <= d.soc());
    }

    // ---- GridExchange: charge-from-grid schedule ----

    @Test
    public void chargeFromGridReflectsSchedule() {
        InverterComponent noSchedule = component(inverter(0, 0, 0), null, null);
        assertFalse(noSchedule.isChargeFromGrid(0));

        LoadShift allDay = new LoadShift();
        allDay.setBegin(0);
        allDay.setEnd(24);
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(Collections.singletonList(allDay), 105120);
        InverterComponent scheduled = component(inverter(0, 0, 0), null, cfg);
        assertTrue(scheduled.isChargeFromGrid(0));
    }

    @Test
    public void chargeFromGridSuppressesDischarge() {
        LoadShift allDay = new LoadShift();
        allDay.setBegin(0);
        allDay.setEnd(24);
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(Collections.singletonList(allDay), 105120);
        InverterComponent c = component(inverter(0, 0, 0), battery(10, 2, 3, 0, 100, 100, 50, 0), cfg);
        c.setSoc(5);
        assertEquals("no discharge while charging from grid", 0.0, c.getDischargeCapacity(0), TOL);
    }
}
