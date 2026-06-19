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
 * A bus node: the inverter (Phase C of the component-registration refactor, see
 * {@code plans/sim/component.md}). Exposes the AC throughput cap, the DC/AC/DC-DC conversion efficiencies
 * (kept fractions, e.g. 0.95 for a 5% loss), the dispatch strategy, and identity for ordering. The bus
 * solve consults these; the orchestration (the coupled per-inverter/shared-AC solve) stays in the engine.
 */
public interface Converter extends SimComponent {

    /** Inverter index — the deterministic order in which inverters are resolved. */
    long inverterIndex();

    /** Maximum AC throughput (kW); multiplied by the interval hours gives the per-interval AC cap. */
    double maxInverterLoad();

    /** Minimum export (kWh) below which micro-export to the grid is suppressed. */
    double minExcess();

    /** DC&rarr;AC conversion efficiency (kept fraction). */
    double dc2acLoss();

    /** AC&rarr;DC conversion efficiency (kept fraction). */
    double ac2dcLoss();

    /** DC&rarr;DC conversion efficiency (kept fraction). */
    double dc2dcLoss();

    /** This inverter's dispatch strategy (battery-before-grid vs grid-before-battery). */
    DispatchStrategy dispatchStrategy();
}
