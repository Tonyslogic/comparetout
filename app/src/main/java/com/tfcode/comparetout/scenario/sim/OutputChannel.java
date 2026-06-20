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

/**
 * The output-row fields a component may contribute to. A capability returns
 * {@code (channel, value)} pairs and the engine routes each to the matching
 * {@code ScenarioSimulationData} setter — so the engine never hardcodes which component writes which
 * column, and a future component's new channel needs no change to the per-interval loop. See
 * {@code plans/sim/component.md}.
 */
public enum OutputChannel {
    /** Scheduled EV charge load (kWh) -> {@code setDirectEVcharge}. */
    DIRECT_EV_CHARGE,
    /** Scheduled immersion (hot water) load (kWh) -> {@code setImmersionLoad}. */
    IMMERSION_LOAD,
    /** Surplus PV diverted to hot water (kWh) -> {@code setKWHDivToWater}. */
    DIV_TO_WATER,
    /** Surplus PV diverted to EV (kWh) -> {@code setKWHDivToEV}. */
    DIV_TO_EV,
    /** Water temperature carried this interval (degrees) -> {@code setWaterTemp}. */
    WATER_TEMP,
    /**
     * Heat-pump <b>total</b> electrical load (kWh, heat pump + resistive backup) -> {@code setHeatPumpLoad}.
     * This is the value added to consumption / the KPI load sum.
     *
     * <p>The {@code HeatPumpComponent} emits this and the heat-pump channels below from Phase 2, but the
     * routing cases in {@link SimulationEngine#applyOutputs} and the backing {@code ScenarioSimulationData}
     * columns are added with the gated schema change (Phase 4 of {@code plans/hp/plan.md}). Until then these
     * channels are simply dropped by {@code applyOutputs} (its switch has no {@code default}), so the
     * heat-pump <i>energy</i> still flows via {@code DemandResult.kWh} but is not yet recorded for display.</p>
     */
    HEAT_PUMP_LOAD,
    /** Heat-pump resistive backup electrical (kWh) -> {@code setHeatPumpBackupLoad}. A subset of
     * {@link #HEAT_PUMP_LOAD}, so it must NOT be added again to the KPI load sum. */
    HEAT_PUMP_BACKUP_LOAD,
    /** Heat delivered (kWh thermal) -> {@code setHeatPumpHeat}. Thermal, not electrical — never summed into
     * the electrical KPI; effective COP is {@code heat ÷ load}. */
    HEAT_PUMP_HEAT,
    /** Calibrated heat-pump COP for the interval (ratio, temperature-driven) -> {@code setHeatPumpCop}. An
     * <b>averaged</b> driver: aggregate by mean over a graph's x-axis bucket (not summed, and not a carried
     * stock like {@link #WATER_TEMP}). */
    HEAT_PUMP_COP,
    /** Outdoor air temperature (°C) -> {@code setHeatPumpOutdoorTemp}. Explains the COP; aggregate by mean
     * over the x-axis bucket. */
    HEAT_PUMP_OUTDOOR_TEMP,
    /** Wind speed (m/s) -> {@code setHeatPumpWindSpeed}. Explains the infiltration demand; aggregate by mean
     * over the x-axis bucket. */
    HEAT_PUMP_WIND_SPEED
}
