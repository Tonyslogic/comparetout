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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The dispatch Strategy: mode codes resolve to the right singleton, an unknown/legacy code falls back to
 * the default (load&rarr;battery&rarr;grid), and each strategy reports its discharge ordering, mode, and id.
 */
public class DispatchStrategyTest {

    @Test
    public void modeZeroIsLoadBatteryGrid() {
        DispatchStrategy s = DispatchStrategy.fromMode(DispatchStrategy.MODE_LOAD_BATTERY_GRID);
        assertSame(DispatchStrategy.LOAD_BATTERY_GRID, s);
        assertEquals(0, s.mode());
        assertTrue("battery serves load before the grid", s.dischargeBatteryForLoad());
        assertEquals("LoadBatteryGrid", s.id());
    }

    @Test
    public void modeOneIsLoadGridBattery() {
        DispatchStrategy s = DispatchStrategy.fromMode(DispatchStrategy.MODE_LOAD_GRID_BATTERY);
        assertSame(DispatchStrategy.LOAD_GRID_BATTERY, s);
        assertEquals(1, s.mode());
        assertFalse("grid serves load, battery preserved", s.dischargeBatteryForLoad());
        assertEquals("LoadGridBattery", s.id());
    }

    @Test
    public void unknownModeFallsBackToDefault() {
        assertSame(DispatchStrategy.LOAD_BATTERY_GRID, DispatchStrategy.fromMode(99));
        assertSame(DispatchStrategy.LOAD_BATTERY_GRID, DispatchStrategy.fromMode(-1));
    }

    @Test
    public void modeCodesAreStable() {
        assertEquals(0, DispatchStrategy.MODE_LOAD_BATTERY_GRID);
        assertEquals(1, DispatchStrategy.MODE_LOAD_GRID_BATTERY);
        // round-trips: each singleton resolves back from its own mode code.
        assertSame(DispatchStrategy.LOAD_BATTERY_GRID,
                DispatchStrategy.fromMode(DispatchStrategy.LOAD_BATTERY_GRID.mode()));
        assertSame(DispatchStrategy.LOAD_GRID_BATTERY,
                DispatchStrategy.fromMode(DispatchStrategy.LOAD_GRID_BATTERY.mode()));
    }
}
