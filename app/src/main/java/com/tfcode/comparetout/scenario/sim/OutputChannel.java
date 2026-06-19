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
    WATER_TEMP
}
