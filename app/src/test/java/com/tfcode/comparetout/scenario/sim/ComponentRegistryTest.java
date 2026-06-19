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

import com.tfcode.comparetout.model.scenario.EVCharge;

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

    @Test
    public void lookupMatchesSchedule() {
        List<EVCharge> charges = Collections.singletonList(allDayCharge(6.0));
        assertNotNull(EvChargeComponent.scheduledChargeOrNull(charges, 1, 1, 12 * 60));
        // Sunday is stored as 0; passing 7 must normalise to 0 and still match the all-days default.
        assertNotNull(EvChargeComponent.scheduledChargeOrNull(charges, 7, 1, 12 * 60));
        assertNull(EvChargeComponent.scheduledChargeOrNull(Collections.emptyList(), 1, 1, 12 * 60));
    }
}
