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
 * Scheduled grid exchange for an inverter (Phase C of the component-registration refactor, see
 * {@code plans/sim/component.md}): charge-from-grid (load shift) and forced discharge-to-grid. Exposes the
 * per-row schedule; the engine applies the energy within the bus solve.
 */
public interface GridExchange extends SimComponent {

    /** Whether charging the battery from the grid is scheduled this row. */
    boolean isChargeFromGrid(int row);

    /** Charge-from-grid stop-at threshold (percent of capacity) for this row. */
    double chargeFromGridStopAtPercent(int row);

    /** Whether forced discharge to the grid is scheduled this row. */
    boolean isForcedDischargeToGrid(int row);

    /** Forced-discharge stop-at threshold (percent of capacity) for this row. */
    double forcedDischargeStopAtPercent(int row);

    /** Forced-discharge rate (kW) for this row. */
    double forcedDischargeRate(int row);
}
