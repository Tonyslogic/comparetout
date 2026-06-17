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
 * Strategy for how an inverter meets the household load that PV alone cannot cover.
 *
 * <p>Phase 3 of the simulation-engine refactor (see {@code plans/sim/refactor.md} and
 * {@code plans/sim/phase3-design.md}). After PV has served the load and charged the battery, the
 * remaining load is met from the battery and/or the grid. This Strategy decides the order:</p>
 * <ul>
 *   <li>{@link #LOAD_BATTERY_GRID} — discharge the battery before importing from the grid (the
 *       historical default; unchanged behaviour for existing scenarios).</li>
 *   <li>{@link #LOAD_GRID_BATTERY} — import from the grid and preserve the battery (e.g. to hold charge
 *       for a peak tariff window). Wired but not yet user-selectable; persistence + UI is Phase 4.</li>
 * </ul>
 *
 * <p>The Strategy is meaningful only now that energy is resolved per inverter (Phase 3); under the
 * previous single aggregate decision there was nowhere natural to vary it per inverter.</p>
 */
public interface DispatchStrategy {

    /** Persisted mode for {@link #LOAD_BATTERY_GRID} (matches {@code Inverter.DISPATCH_LOAD_BATTERY_GRID}). */
    int MODE_LOAD_BATTERY_GRID = 0;
    /** Persisted mode for {@link #LOAD_GRID_BATTERY} (matches {@code Inverter.DISPATCH_LOAD_GRID_BATTERY}). */
    int MODE_LOAD_GRID_BATTERY = 1;

    /** Whether the battery is discharged to serve household load before importing from the grid. */
    boolean dischargeBatteryForLoad();

    /** Short stable identifier (for logging / future persistence). */
    String id();

    /** Persisted dispatch-mode code for this strategy (see the {@code MODE_*} constants). */
    int mode();

    /** Default order: load → battery → grid. */
    DispatchStrategy LOAD_BATTERY_GRID = new DispatchStrategy() {
        @Override public boolean dischargeBatteryForLoad() { return true; }
        @Override public String id() { return "LoadBatteryGrid"; }
        @Override public int mode() { return MODE_LOAD_BATTERY_GRID; }
    };

    /** Alternative order: load → grid → battery (battery preserved). */
    DispatchStrategy LOAD_GRID_BATTERY = new DispatchStrategy() {
        @Override public boolean dischargeBatteryForLoad() { return false; }
        @Override public String id() { return "LoadGridBattery"; }
        @Override public int mode() { return MODE_LOAD_GRID_BATTERY; }
    };

    /**
     * Resolves a persisted dispatch-mode code to its strategy. Unknown/legacy values fall back to the
     * default {@link #LOAD_BATTERY_GRID}, so existing inverters keep their historical behaviour.
     */
    static DispatchStrategy fromMode(int mode) {
        return (mode == MODE_LOAD_GRID_BATTERY) ? LOAD_GRID_BATTERY : LOAD_BATTERY_GRID;
    }
}
