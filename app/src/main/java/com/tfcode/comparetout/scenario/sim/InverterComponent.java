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

import com.tfcode.comparetout.model.scenario.SimulationInputData;

/**
 * An inverter as a uniform component (Phase C of the component-registration refactor, see
 * {@code plans/sim/component.md}). It bundles the inverter's capabilities — {@link Converter} (the bus
 * node), {@link Storage} (its battery), {@link Generator} (its PV), and {@link GridExchange} (its grid
 * schedules) — so the engine's DC/AC bus solve consults capability methods rather than reaching into a
 * concrete inverter representation. The coupled solve itself (shared AC load, export cap, per-inverter
 * headroom, dispatch order) stays in the engine; this interface is the seam, not a redistribution of the
 * physics.
 */
public interface InverterComponent extends Generator, Storage, Converter, GridExchange {

    /** The shared input-series row (load, PV, wall-clock keys) for the given index. */
    SimulationInputData inputRow(int row);

    /**
     * Prepares per-run state at the start of an interval: substitutes a null battery with the shared
     * empty battery, and on the first simulated interval seeds the SOC at the discharge-stop floor.
     */
    void prepareForRun(boolean firstInterval);
}
