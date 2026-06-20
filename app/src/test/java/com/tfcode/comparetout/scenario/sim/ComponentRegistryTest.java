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

package com.tfcode.comparetout.scenario.sim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSystem;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Phase A of the component-registration refactor (see {@code plans/sim/component.md}): proves the EV
 * scheduled charge behaves as a {@link DemandContributor} in isolation and that the
 * {@link ComponentRegistry} registers it. The byte-identical engine behaviour is separately guarded by
 * the golden master and {@code SimulationEngineScenarioInputsTest}.
 */
public class ComponentRegistryTest {

    /** Any interval works: a default EVCharge covers all days/months; begin/end gate the hour. */
    private static IntervalContext anyInterval() {
        // Mon (1), January (1), midday, an arbitrary epoch-day; interval = 5 minutes.
        return new IntervalContext(0L, 1, 1, 12 * 60, 0, 1d / 12d);
    }

    private static EVCharge allDayCharge(double drawKW) {
        EVCharge ev = new EVCharge();
        ev.setBegin(0);
        ev.setEnd(24);
        ev.setDraw(drawKW);
        return ev;
    }

    @Test
    public void evChargeContributesDrawOver12_andRoutesDirectEvChargeChannel() {
        EvChargeComponent component = new EvChargeComponent(Collections.singletonList(allDayCharge(6.0)));
        DemandResult result = component.demand(anyInterval());

        // Legacy arithmetic: draw / 12d (NOT draw * intervalHours) -> byte-identical.
        assertEquals(6.0 / 12d, result.kWh, 0d);
        assertEquals(6.0 / 12d, result.outputs.get(OutputChannel.DIRECT_EV_CHARGE), 0d);
    }

    @Test
    public void noScheduledCharge_stillRoutesZero_matchingLegacyAlwaysSetColumn() {
        EvChargeComponent empty = new EvChargeComponent(Collections.emptyList());
        DemandResult result = empty.demand(anyInterval());

        assertEquals(0d, result.kWh, 0d);
        // The legacy engine always called setDirectEVcharge, with 0 when nothing was scheduled.
        assertEquals(0d, result.outputs.get(OutputChannel.DIRECT_EV_CHARGE), 0d);
    }

    @Test
    public void nullChargeListIsTolerated() {
        DemandResult result = new EvChargeComponent(null).demand(anyInterval());
        assertEquals(0d, result.kWh, 0d);
    }

    @Test
    public void chargeOutsideScheduledHoursReportsZero() {
        EVCharge morningOnly = allDayCharge(6.0);
        morningOnly.setBegin(0);
        morningOnly.setEnd(6); // ends before midday
        DemandResult result = new EvChargeComponent(Collections.singletonList(morningOnly)).demand(anyInterval());
        assertEquals(0d, result.kWh, 0d);
    }

    private static ComponentRegistry registryWith(List<EVCharge> evCharges) {
        // Phase B build signature: no hot water configured here (HW exercised in HwComponentTest).
        return ComponentRegistry.build(null, null, null, evCharges, null, new HashMap<>());
    }

    @Test
    public void registryRegistersHotWaterThenEvDemandContributors() {
        ComponentRegistry registry = registryWith(Collections.singletonList(allDayCharge(6.0)));
        List<DemandContributor> contributors = registry.demandContributors();

        // Order is significant: hot water first, then EV charge (byte-identical load accumulation order).
        assertEquals("hot water + EV charge contributors", 2, contributors.size());
        assertEquals("hot water is registered first", HwComponent.class, contributors.get(0).getClass());
        assertEquals("EV charge is registered second", EvChargeComponent.class, contributors.get(1).getClass());
        assertEquals(6.0 / 12d, contributors.get(1).demand(anyInterval()).kWh, 0d);

        assertNotNull(registry.hotWater());
        assertNotNull(registry.evDivert());
    }

    /** A minimal heat-pump component (one-sample model) — enough to assert registration/order. */
    private static HeatPumpComponent tinyHeatPump() {
        HeatPumpDemandModel.WeatherSample s =
                new HeatPumpDemandModel.WeatherSample(0d, 0d, 0, 0, 1);
        HeatPumpDemandModel model =
                new HeatPumpDemandModel(new HeatPumpDemandModel.Config(), Collections.singletonList(s));
        return new HeatPumpComponent(model, new long[]{0L});
    }

    @Test
    public void heatPumpRegistersLastWhenSupplied() {
        HeatPumpComponent hp = tinyHeatPump();
        ComponentRegistry registry = ComponentRegistry.build(
                null, null, null, Collections.singletonList(allDayCharge(6.0)), null, new HashMap<>(), hp);
        List<DemandContributor> contributors = registry.demandContributors();

        assertEquals("hot water + EV charge + heat pump", 3, contributors.size());
        assertEquals("hot water first", HwComponent.class, contributors.get(0).getClass());
        assertEquals("EV charge second", EvChargeComponent.class, contributors.get(1).getClass());
        assertSame("heat pump registered last", hp, contributors.get(2));
    }

    @Test
    public void nullHeatPumpRegistersNothing() {
        // The pre-Phase-4 path: existing scenarios pass null and keep the water-then-EV pair only.
        ComponentRegistry sixArg = registryWith(Collections.singletonList(allDayCharge(6.0)));
        ComponentRegistry sevenArgNull = ComponentRegistry.build(
                null, null, null, Collections.singletonList(allDayCharge(6.0)), null, new HashMap<>(), null);
        assertEquals(2, sixArg.demandContributors().size());
        assertEquals(2, sevenArgNull.demandContributors().size());
    }

    @Test
    public void lookupMatchesSchedule() {
        List<EVCharge> charges = Collections.singletonList(allDayCharge(6.0));
        assertNotNull(EvChargeComponent.scheduledChargeOrNull(charges, 1, 1, 12 * 60));
        // Sunday is stored as 0; passing 7 must normalise to 0 and still match the all-days default.
        assertNotNull(EvChargeComponent.scheduledChargeOrNull(charges, 7, 1, 12 * 60));
        assertNull(EvChargeComponent.scheduledChargeOrNull(Collections.emptyList(), 1, 1, 12 * 60));
    }

    // ---- divertOrder: the per-interval surplus-sink ordering (the divert analogue of the dispatch
    //      strategy). Previously only covered indirectly by the two divert goldens. ----

    private static EVDivert activeDivert(boolean ev1st, boolean active) {
        EVDivert ev = new EVDivert();
        ev.setBegin(0);
        ev.setEnd(24);
        ev.setActive(active);
        ev.setEv1st(ev1st);
        ev.setMinimum(0d);
        ev.setDailyMax(100d);
        return ev;
    }

    /** Hot water present (divert on) + the given EV diverts. */
    private static ComponentRegistry divertRegistry(List<EVDivert> diverts) {
        return ComponentRegistry.build(new HWSystem(), true, null, null, diverts, new HashMap<>());
    }

    @Test
    public void divertOrder_waterOnlyWhenNoActiveEvDivert() {
        ComponentRegistry r = divertRegistry(null);
        List<SurplusSink> order = r.divertOrder(anyInterval());
        assertEquals(1, order.size());
        assertSame("only hot water consumes surplus", r.hotWater(), order.get(0));
    }

    @Test
    public void divertOrder_inactiveEvDivertIsWaterOnly() {
        ComponentRegistry r = divertRegistry(Collections.singletonList(activeDivert(true, false)));
        List<SurplusSink> order = r.divertOrder(anyInterval());
        assertEquals(1, order.size());
        assertSame(r.hotWater(), order.get(0));
    }

    @Test
    public void divertOrder_evFirstWhenIsEv1st() {
        ComponentRegistry r = divertRegistry(Collections.singletonList(activeDivert(true, true)));
        List<SurplusSink> order = r.divertOrder(anyInterval());
        assertEquals(2, order.size());
        assertSame("EV consumes first", r.evDivert(), order.get(0));
        assertSame("then water mops the residual", r.hotWater(), order.get(1));
    }

    @Test
    public void divertOrder_waterFirstWhenNotEv1st() {
        ComponentRegistry r = divertRegistry(Collections.singletonList(activeDivert(false, true)));
        List<SurplusSink> order = r.divertOrder(anyInterval());
        assertEquals(2, order.size());
        assertSame("water consumes first", r.hotWater(), order.get(0));
        assertSame("then EV takes the residual", r.evDivert(), order.get(1));
    }
}
