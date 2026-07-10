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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.IntHolder;
import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.LoadShift;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Covers the per-row schedule precompute classes {@code ChargeFromGrid} and {@code ForceDischargeToGrid}:
 * the populated lists are the right size, empty schedules leave every interval inactive, zero rows are
 * handled, and multiple overlapping windows build without error. (Migrated from the retired
 * {@code SimulationWorkerTest}/{@code …EdgeCaseTest}/{@code …AdvancedTest}.)
 */
public class SimulationEngineScheduleTest {

    private static final int YEAR_ROWS = 105120; // 288 intervals/day * 365 days

    private static LoadShift loadShift(int begin, int end, double stopAt) {
        LoadShift ls = new LoadShift();
        ls.setBegin(begin);
        ls.setEnd(end);
        ls.setStopAt(stopAt);
        return ls;
    }

    @Test
    public void chargeFromGrid_sizedAndConstructsWithMultipleWindows() {
        SimulationEngine.ChargeFromGrid cfg = new SimulationEngine.ChargeFromGrid(
                Arrays.asList(loadShift(10, 14, 80.0), loadShift(18, 22, 90.0)), YEAR_ROWS);
        assertNotNull(cfg.mCFG);
        assertNotNull(cfg.mStopAt);
        assertEquals(YEAR_ROWS, cfg.mCFG.size());
        assertEquals(YEAR_ROWS, cfg.mStopAt.size());
    }

    @Test
    public void chargeFromGrid_emptyScheduleIsAllInactive() {
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(new ArrayList<>(), YEAR_ROWS);
        assertEquals(YEAR_ROWS, cfg.mCFG.size());
        for (int i = 0; i < YEAR_ROWS; i++) {
            assertFalse(cfg.mCFG.get(i));
            assertEquals(0.0, cfg.mStopAt.get(i), 0d);
        }
    }

    @Test
    public void chargeFromGrid_zeroRows() {
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(Collections.singletonList(loadShift(0, 24, 50.0)), 0);
        assertEquals(0, cfg.mCFG.size());
        assertEquals(0, cfg.mStopAt.size());
    }

    @Test
    public void forceDischargeToGrid_sizedAndConstructsWithMultipleWindows() {
        List<DischargeToGrid> discharges = Arrays.asList(new DischargeToGrid(), new DischargeToGrid());
        SimulationEngine.ForceDischargeToGrid fd =
                new SimulationEngine.ForceDischargeToGrid(discharges, YEAR_ROWS);
        assertNotNull(fd.mD2G);
        assertNotNull(fd.mStopAt);
        assertNotNull(fd.mRate);
        assertEquals(YEAR_ROWS, fd.mD2G.size());
        assertEquals(YEAR_ROWS, fd.mStopAt.size());
        assertEquals(YEAR_ROWS, fd.mRate.size());
    }

    @Test
    public void forceDischargeToGrid_emptyScheduleIsAllInactive() {
        SimulationEngine.ForceDischargeToGrid fd =
                new SimulationEngine.ForceDischargeToGrid(new ArrayList<>(), YEAR_ROWS);
        for (int i = 0; i < YEAR_ROWS; i++) {
            assertFalse(fd.mD2G.get(i));
            assertEquals(0.0, fd.mStopAt.get(i), 0d);
            assertEquals(0.0, fd.mRate.get(i), 0d);
        }
    }

    @Test
    public void forceDischargeToGrid_zeroRows() {
        SimulationEngine.ForceDischargeToGrid fd =
                new SimulationEngine.ForceDischargeToGrid(Collections.singletonList(new DischargeToGrid()), 0);
        assertEquals(0, fd.mD2G.size());
        assertEquals(0, fd.mStopAt.size());
        assertEquals(0, fd.mRate.size());
    }

    // ---- Population logic: the day/month/hour matching that fills the schedules. The 2001 calendar
    //      starts Monday 2001-01-01 00:00; at 5-minute steps, hour H on day-of-year D is rows
    //      [D*288 + H*12 .. +11]. Row 120 = Jan-1 10:00, row 144 = Jan-1 12:00, row 432 = Jan-2 12:00. ----

    @Test
    public void chargeFromGrid_activatesOnlyWithinTheHourWindow_andCarriesStopAt() {
        SimulationEngine.ChargeFromGrid cfg = new SimulationEngine.ChargeFromGrid(
                Collections.singletonList(loadShift(10, 14, 80.0)), YEAR_ROWS); // default = all days/months
        assertFalse("09:55 is before the window", cfg.mCFG.get(119));
        assertTrue("10:00 is inside the window", cfg.mCFG.get(120));
        assertEquals("stop-at carried for active rows", 80.0, cfg.mStopAt.get(120), 0d);
        assertTrue("14:55 is still inside (end is inclusive by hour)", cfg.mCFG.get(179));
        assertFalse("15:00 is past the end hour", cfg.mCFG.get(180));
    }

    @Test
    public void chargeFromGrid_respectsDayOfWeekFilter() {
        LoadShift mondayOnly = loadShift(0, 24, 50.0);
        IntHolder mondays = new IntHolder();
        mondays.ints = new ArrayList<>();
        mondays.ints.add(1); // 1 = Monday (Sunday is 0); 2001-01-01 is a Monday
        mondayOnly.setDays(mondays);
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(Collections.singletonList(mondayOnly), YEAR_ROWS);

        assertTrue("Jan-1 (Monday) is active", cfg.mCFG.get(144));
        assertFalse("Jan-2 (Tuesday) is not", cfg.mCFG.get(432));
    }

    @Test
    public void forceDischargeToGrid_populatesRateAndStopAtWithinWindow() {
        DischargeToGrid d2g = new DischargeToGrid();
        d2g.setBegin(10);
        d2g.setEnd(14);
        d2g.setRate(5.0);
        d2g.setStopAt(30.0);
        SimulationEngine.ForceDischargeToGrid fd =
                new SimulationEngine.ForceDischargeToGrid(Collections.singletonList(d2g), YEAR_ROWS);

        assertFalse(fd.mD2G.get(119));
        assertTrue(fd.mD2G.get(120));
        assertEquals(5.0, fd.mRate.get(120), 0d);
        assertEquals(30.0, fd.mStopAt.get(120), 0d);
        assertFalse(fd.mD2G.get(180));
    }

    // ---- v16 date-aware, minute-granular windows (dormant on defaults — the
    //      tests above are the defaults' behaviour guard). ----

    /** Row index of (2001-MM-DD, HH:MM) on the 5-minute grid. */
    private static int row(int month, int day, int hour, int minute) {
        int doy = java.time.LocalDate.of(2001, month, day).getDayOfYear();
        return (doy - 1) * 288 + hour * 12 + minute / 5;
    }

    @Test
    public void chargeFromGrid_singleDayMinuteWindowPopulatesExactSlots() {
        // The plan's Phase-4 checkpoint: a 02:30–04:00 window on one date
        // populates exactly slots [30..48) of that day.
        LoadShift ls = loadShift(2, 4, 80.0);
        ls.setStartDate("06/15");
        ls.setEndDate("06/15");
        ls.setBeginMinute(150);
        ls.setEndMinute(240);
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(Collections.singletonList(ls), YEAR_ROWS);

        assertFalse("02:25 is before the window", cfg.mCFG.get(row(6, 15, 2, 25)));
        assertTrue("02:30 starts the window", cfg.mCFG.get(row(6, 15, 2, 30)));
        assertTrue("03:55 is the last active slot", cfg.mCFG.get(row(6, 15, 3, 55)));
        assertFalse("04:00 is past the (exclusive) end", cfg.mCFG.get(row(6, 15, 4, 0)));
        assertFalse("other days are outside the date window", cfg.mCFG.get(row(6, 16, 2, 30)));
        assertFalse("other days are outside the date window", cfg.mCFG.get(row(6, 14, 2, 30)));
        int active = 0;
        for (Boolean b : cfg.mCFG) if (b) active++;
        assertEquals("exactly 18 five-minute slots (90 minutes) all year", 18, active);
    }

    @Test
    public void chargeFromGrid_dateRangeLimitsTheLegacyHourWindow() {
        LoadShift ls = loadShift(2, 4, 80.0); // legacy hours, minutes stay -1
        ls.setStartDate("06/01");
        ls.setEndDate("06/30");
        SimulationEngine.ChargeFromGrid cfg =
                new SimulationEngine.ChargeFromGrid(Collections.singletonList(ls), YEAR_ROWS);

        assertTrue("inside the date range at 03:00", cfg.mCFG.get(row(6, 15, 3, 0)));
        assertTrue("end hour stays inclusive (04:55)", cfg.mCFG.get(row(6, 15, 4, 55)));
        assertFalse("05:00 is past the end hour", cfg.mCFG.get(row(6, 15, 5, 0)));
        assertFalse("May 31 is before the range", cfg.mCFG.get(row(5, 31, 3, 0)));
        assertFalse("July 1 is past the range", cfg.mCFG.get(row(7, 1, 3, 0)));
    }

    @Test
    public void forceDischargeToGrid_dateAndMinuteWindowsApply() {
        DischargeToGrid d2g = new DischargeToGrid();
        d2g.setBegin(17);
        d2g.setEnd(19);
        d2g.setRate(5.0);
        d2g.setStopAt(30.0);
        d2g.setStartDate("11/01");
        d2g.setEndDate("12/31");
        d2g.setBeginMinute(17 * 60 + 30);
        d2g.setEndMinute(19 * 60);
        SimulationEngine.ForceDischargeToGrid fd =
                new SimulationEngine.ForceDischargeToGrid(Collections.singletonList(d2g), YEAR_ROWS);

        assertFalse("17:00 is before the minute window", fd.mD2G.get(row(11, 15, 17, 0)));
        assertTrue("17:30 starts the window", fd.mD2G.get(row(11, 15, 17, 30)));
        assertEquals(5.0, fd.mRate.get(row(11, 15, 17, 30)), 0d);
        assertFalse("19:00 is past the (exclusive) end", fd.mD2G.get(row(11, 15, 19, 0)));
        assertFalse("October is outside the date range", fd.mD2G.get(row(10, 15, 17, 30)));
    }
}
