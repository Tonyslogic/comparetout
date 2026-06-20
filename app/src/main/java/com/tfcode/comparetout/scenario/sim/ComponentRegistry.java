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

import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds a scenario's {@link SimComponent}s and offers capability views the engine uses instead of
 * hardcoding component blocks. The single registration point: {@link #build} maps scenario entities to
 * components, so adding a component is one implementation + one line here. See
 * {@code plans/sim/component.md}.
 *
 * <p>Built once per scenario and held by {@code ScenarioInputs}. Phases A–B register hot water and EV
 * (scheduled charge as a {@link DemandContributor}; immersion + the two diverts); later phases add the
 * inverter/battery/PV components. The hot-water and EV-divert components are exposed directly (not via a
 * generic sink list) because the legacy divert ordering is dynamic and stays orchestrated in the engine
 * — see {@link SurplusSink}.</p>
 */
public final class ComponentRegistry {

    private final List<DemandContributor> demandContributors;
    private final HwComponent hotWater;
    private final EvDivertComponent evDivert;

    private ComponentRegistry(List<DemandContributor> demandContributors,
                              HwComponent hotWater, EvDivertComponent evDivert) {
        this.demandContributors = demandContributors;
        this.hotWater = hotWater;
        this.evDivert = evDivert;
    }

    /**
     * Components that add scheduled demand to the load before the energy flow, iterated by the engine.
     * Order is significant: hot water precedes EV charge so the load accumulation order
     * (load, then water, then EV) is byte-identical with the legacy engine.
     */
    public List<DemandContributor> demandContributors() {
        return demandContributors;
    }

    /** The hot-water component (immersion demand + divert sink), for the engine's divert orchestration. */
    public HwComponent hotWater() {
        return hotWater;
    }

    /** The EV divert component, for the engine's divert orchestration. */
    public EvDivertComponent evDivert() {
        return evDivert;
    }

    /**
     * The surplus sinks for this interval, in the order they should consume the feed. This is the divert
     * analogue of {@link DispatchStrategy}: a config flag ({@code EVDivert.isEv1st()}) selects the order
     * — water-first vs EV-first — and the engine makes a single ordered pass. When no EV divert is active
     * only hot water is offered the surplus.
     *
     * <p>Replacing the legacy nested if/else with this single pass fixes the double-heat bug (water is
     * absorbed at most once) and removes the legacy quirk where, in the water-first path, EV could not
     * divert unless hot-water divert also applied — each sink now self-guards on its own constraints.</p>
     */
    public List<SurplusSink> divertOrder(IntervalContext ctx) {
        EVDivert active = evDivert.activeDivertOrNull(ctx);
        if (null == active) return Collections.singletonList(hotWater);
        return active.isEv1st()
                ? Arrays.asList(evDivert, hotWater)   // EV first, water mops the residual
                : Arrays.asList(hotWater, evDivert);  // water first, EV takes the residual
    }

    /**
     * Maps scenario inputs to components. Hot water and EV are always registered; they report zero
     * demand / zero divert when nothing is configured or scheduled, matching the legacy engine which
     * always wrote the corresponding output columns. {@code evDivertDailyTotals} is shared by reference
     * so the EV divert's per-day accumulation carries across intervals exactly as before.
     */
    public static ComponentRegistry build(HWSystem hwSystem, Boolean hwDivert, List<HWSchedule> hwSchedules,
                                          List<EVCharge> evCharges, List<EVDivert> evDiverts,
                                          Map<Integer, Double> evDivertDailyTotals) {
        return build(hwSystem, hwDivert, hwSchedules, evCharges, evDiverts, evDivertDailyTotals, null);
    }

    /**
     * As above, additionally registering a heat pump when one is supplied. The heat pump is added
     * <b>last</b> among the demand contributors (after hot water and EV charge), so existing scenarios —
     * which always pass {@code null} here until the heat-pump schema lands (Phase 4 of
     * {@code plans/hp/plan.md}) — keep their water-then-EV order and stay byte-identical. A {@code null}
     * heat pump registers nothing.
     */
    public static ComponentRegistry build(HWSystem hwSystem, Boolean hwDivert, List<HWSchedule> hwSchedules,
                                          List<EVCharge> evCharges, List<EVDivert> evDiverts,
                                          Map<Integer, Double> evDivertDailyTotals,
                                          HeatPumpComponent heatPump) {
        HwComponent hotWater = new HwComponent(hwSystem, hwDivert, hwSchedules);
        EvDivertComponent evDivert = new EvDivertComponent(evDiverts, evDivertDailyTotals);

        List<DemandContributor> demand = new ArrayList<>();
        demand.add(hotWater);                       // water first ...
        demand.add(new EvChargeComponent(evCharges)); // ... then EV charge (load add order)
        if (heatPump != null) demand.add(heatPump);   // ... then heat pump (new; null in pre-Phase-4 scenarios)
        return new ComponentRegistry(demand, hotWater, evDivert);
    }
}
