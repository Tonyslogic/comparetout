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

import com.tfcode.comparetout.model.scenario.EVCharge;
import com.tfcode.comparetout.model.scenario.EVDivert;
import com.tfcode.comparetout.model.scenario.HWSchedule;
import com.tfcode.comparetout.model.scenario.HWSystem;
import com.tfcode.comparetout.scenario.sim.ComponentRegistry;
import com.tfcode.comparetout.scenario.sim.EvChargeComponent;
import com.tfcode.comparetout.scenario.sim.EvDivertComponent;
import com.tfcode.comparetout.scenario.sim.HeatPumpComponent;
import com.tfcode.comparetout.scenario.sim.HwComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scenario-level simulation inputs that are NOT bound to a particular inverter.
 *
 * <p>Phase 2b of the simulation-engine refactor (see {@code plans/sim/refactor.md}). The domain model
 * is that <b>load, hot water, and EV are scenario-level</b>: hot water and EV charging/divert
 * contribute to the household load but are tracked separately and are not connected to any specific
 * inverter. Only the inverter itself, its battery, its PV, and its charge/discharge schedules
 * ({@code ChargeFromGrid} / {@code ForceDischargeToGrid}) are inverter-bound.</p>
 *
 * <p>Previously the engine read these via {@code firstInputData} — "whichever inverter happens to be
 * first in the map" — which was both conceptually wrong and a source of {@code HashMap}-order
 * non-determinism when inverters carried different configuration. The engine now reads them from a
 * single {@code ScenarioInputs} built once per scenario. The hot-water/EV schedule queries that used
 * to live on {@code SimulationEngine.InputData} live here.</p>
 *
 * <p>State note: {@link #mEVDivertDailyTotals} is mutable per-day accumulation; a single
 * {@code ScenarioInputs} instance is shared across all intervals of a scenario so the totals carry.</p>
 */
public class ScenarioInputs {

    public final HWSystem mHWSystem;
    public final Boolean mHWDivert;
    public final List<HWSchedule> mHWSchedules;

    public final List<EVCharge> mEVCharges;
    public final List<EVDivert> mEVDiverts;
    public final Map<Integer, Double> mEVDivertDailyTotals;

    /** Maximum grid export for the scenario (from the load profile), in kW. */
    public final double exportMax;

    /**
     * Per-scenario component registry, built once here and consulted by the engine each interval
     * (component-registration refactor, see {@code plans/sim/component.md}). Phase A registers the EV
     * scheduled charge as a demand contributor; later phases add hot water and the inverter/battery/PV
     * components. {@code ScenarioInputs} <i>holds</i> the registry (it stays the scenario-level inputs
     * object); it does not become it.
     */
    public final ComponentRegistry registry;

    public ScenarioInputs(HWSystem hwSystem, Boolean hwDivert, List<HWSchedule> hwSchedules,
                          List<EVCharge> evCharges, List<EVDivert> evDiverts, double exportMax) {
        this(hwSystem, hwDivert, hwSchedules, evCharges, evDiverts, new HashMap<>(), exportMax);
    }

    /**
     * Full constructor allowing an existing daily-totals map to be supplied — used by the legacy
     * compatibility path so per-day EV-divert accumulation carries across intervals exactly as before.
     */
    public ScenarioInputs(HWSystem hwSystem, Boolean hwDivert, List<HWSchedule> hwSchedules,
                          List<EVCharge> evCharges, List<EVDivert> evDiverts,
                          Map<Integer, Double> evDivertDailyTotals, double exportMax) {
        this.mHWSystem = hwSystem;
        this.mHWDivert = hwDivert;
        this.mHWSchedules = hwSchedules;
        this.mEVCharges = evCharges;
        this.mEVDiverts = evDiverts;
        this.mEVDivertDailyTotals = evDivertDailyTotals;
        this.exportMax = exportMax;
        this.registry = ComponentRegistry.build(hwSystem, hwDivert, hwSchedules,
                evCharges, evDiverts, this.mEVDivertDailyTotals);
    }

    /**
     * As the basic constructor, additionally registering a pre-built heat-pump component (Phase 4 of
     * {@code plans/hp/plan.md}). The {@link HeatPumpComponent} is built by the worker (it needs the sim grid
     * and the weather source); a {@code null} heat pump registers nothing, so existing scenarios are
     * unaffected.
     */
    public ScenarioInputs(HWSystem hwSystem, Boolean hwDivert, List<HWSchedule> hwSchedules,
                          List<EVCharge> evCharges, List<EVDivert> evDiverts, double exportMax,
                          HeatPumpComponent heatPump) {
        this.mHWSystem = hwSystem;
        this.mHWDivert = hwDivert;
        this.mHWSchedules = hwSchedules;
        this.mEVCharges = evCharges;
        this.mEVDiverts = evDiverts;
        this.mEVDivertDailyTotals = new HashMap<>();
        this.exportMax = exportMax;
        this.registry = ComponentRegistry.build(hwSystem, hwDivert, hwSchedules,
                evCharges, evDiverts, this.mEVDivertDailyTotals, heatPump);
    }

    /**
     * Determines if hot water heating is scheduled for the given time.
     * @param dayOfWeek Day of week (0=Sunday).
     * @param monthOfYear Month of year.
     * @param minuteOfDay Minute of day.
     * @return true if heating is scheduled, false otherwise.
     */
    public boolean isHotWaterHeatingScheduled(int dayOfWeek, int monthOfYear, int minuteOfDay) {
        // Single source of truth: the hot water component owns the schedule lookup (Phase B).
        return HwComponent.isHotWaterHeatingScheduled(mHWSchedules, dayOfWeek, monthOfYear, minuteOfDay);
    }

    /**
     * Checks if EV charging is scheduled for the given time.
     * @param dayOfWeek Day of week (0=Sunday).
     * @param monthOfYear Month of year.
     * @param minuteOfDay Minute of day.
     * @return The EVCharge if scheduled, null otherwise.
     */
    public EVCharge isEVCharging(int dayOfWeek, int monthOfYear, int minuteOfDay) {
        // Single source of truth: the EV charge component owns the schedule lookup (Phase A).
        return EvChargeComponent.scheduledChargeOrNull(mEVCharges, dayOfWeek, monthOfYear, minuteOfDay);
    }

    /**
     * Gets the EVDivert scheduled for the given time, if any.
     * @param dayOfWeek Day of week (0=Sunday).
     * @param monthOfYear Month of year.
     * @param minuteOfDay Minute of day.
     * @return The EVDivert if scheduled, null otherwise.
     */
    public EVDivert getEVDivertOrNull(int dayOfWeek, int monthOfYear, int minuteOfDay) {
        // Single source of truth: the EV divert component owns the schedule lookup (Phase B).
        return EvDivertComponent.scheduledDivertOrNull(mEVDiverts, dayOfWeek, monthOfYear, minuteOfDay);
    }
}
