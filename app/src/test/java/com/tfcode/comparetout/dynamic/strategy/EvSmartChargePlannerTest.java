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

package com.tfcode.comparetout.dynamic.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.tfcode.comparetout.model.IntHolder;
import com.tfcode.comparetout.model.scenario.EVCharge;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The EV deadline planner: same energy per session, moved to the cheapest
 * half-hours before the departure deadline.
 */
public class EvSmartChargePlannerTest {

    /** 4-hour session (begin 2, end 6 → 8 half-hours) at 7 kW, every day. */
    private static EVCharge nightlySession() {
        EVCharge base = new EVCharge();
        base.setName("Commuter");
        base.setBegin(2);
        base.setEnd(6);
        base.setDraw(7.0);
        return base;
    }

    /** Cheap 00:00–04:00 (slots 0..7), dear elsewhere — every day the same. */
    private static final StrategyYearRunner.HalfHourlyProvider CHEAP_EARLY_MORNING = date -> {
        double[] a = new double[48];
        Arrays.fill(a, 20);
        for (int s = 0; s < 8; s++) a[s] = 5;
        return a;
    };

    @Test
    public void cheapestMorningSlotsWinAndCoalesceAcrossTheYear() {
        List<EVCharge> planned = EvSmartChargePlanner.plan(
                Collections.singletonList(nightlySession()), CHEAP_EARLY_MORNING,
                EvSmartChargePlanner.DEFAULT_ARRIVAL_MINUTE,
                EvSmartChargePlanner.DEFAULT_DEADLINE_MINUTE);

        // Sessions Jan 1..Dec 30 all pick tomorrow's 00:00–04:00. Dec 31's
        // session has no tomorrow, so it charges in its own evening — which
        // gives Dec 31 a different slot mask (inherited morning + its own
        // evening) and splits it out of the long run: three rows.
        assertEquals(3, planned.size());
        EVCharge yearRow = planned.get(0);
        assertEquals("01/02", yearRow.getStartDate());
        assertEquals("12/30", yearRow.getEndDate());
        assertEquals(0, yearRow.getEffectiveBeginMinute());
        assertEquals(240, yearRow.getEffectiveEndMinute());
        assertEquals(7.0, yearRow.getDraw(), 0d);
        EVCharge lastMorning = planned.get(1);
        assertEquals("12/31", lastMorning.getStartDate());
        assertEquals("12/31", lastMorning.getEndDate());
        assertEquals(0, lastMorning.getEffectiveBeginMinute());
        EVCharge lastEvening = planned.get(2);
        assertEquals("12/31", lastEvening.getStartDate());
        assertEquals("12/31", lastEvening.getEndDate());
        assertTrue("the fallback slots sit in the evening window",
                lastEvening.getEffectiveBeginMinute() >= 18 * 60);
    }

    @Test
    public void energyPerSessionIsPreserved() {
        List<EVCharge> planned = EvSmartChargePlanner.plan(
                Collections.singletonList(nightlySession()), CHEAP_EARLY_MORNING,
                EvSmartChargePlanner.DEFAULT_ARRIVAL_MINUTE,
                EvSmartChargePlanner.DEFAULT_DEADLINE_MINUTE);
        // 8 half-hours per session, 365 sessions → 365 × 240 charging minutes.
        long minutes = 0;
        for (EVCharge row : planned) {
            long days = countDays(row.getStartDate(), row.getEndDate());
            minutes += days * (row.getEffectiveEndMinute() - row.getEffectiveBeginMinute());
        }
        assertEquals(365L * 240, minutes);
    }

    @Test
    public void daysOfWeekFilterLimitsTheSessions() {
        EVCharge base = nightlySession();
        IntHolder mondays = new IntHolder();
        mondays.ints = new ArrayList<>();
        mondays.ints.add(1); // Monday plug-ins only
        base.setDays(mondays);
        List<EVCharge> planned = EvSmartChargePlanner.plan(
                Collections.singletonList(base), CHEAP_EARLY_MORNING,
                EvSmartChargePlanner.DEFAULT_ARRIVAL_MINUTE,
                EvSmartChargePlanner.DEFAULT_DEADLINE_MINUTE);
        // Monday-evening sessions charge on Tuesday mornings (single-day rows,
        // never consecutive), except Dec 31 (a Monday) which has no tomorrow.
        for (EVCharge row : planned) {
            assertEquals(row.getStartDate(), row.getEndDate());
            LocalDate date = dateOf(row.getStartDate());
            if (date.equals(LocalDate.of(2001, 12, 31))) continue; // evening fallback
            assertEquals(DayOfWeek.TUESDAY, date.getDayOfWeek());
        }
    }

    @Test
    public void sameDayWindowStaysInsideTheDay() {
        // Arrival 08:00, deadline 18:00 — a daytime charger (e.g. workplace).
        List<EVCharge> planned = EvSmartChargePlanner.plan(
                Collections.singletonList(nightlySession()), CHEAP_EARLY_MORNING,
                8 * 60, 18 * 60);
        for (EVCharge row : planned) {
            assertTrue(row.getEffectiveBeginMinute() >= 8 * 60);
            assertTrue(row.getEffectiveEndMinute() <= 18 * 60);
        }
    }

    @Test
    public void tiesChargeSoonerNotLater() {
        // Flat prices: the cheapest N slots are simply the earliest available.
        StrategyYearRunner.HalfHourlyProvider flat = date -> {
            double[] a = new double[48];
            Arrays.fill(a, 20);
            return a;
        };
        List<EVCharge> planned = EvSmartChargePlanner.plan(
                Collections.singletonList(nightlySession()), flat,
                EvSmartChargePlanner.DEFAULT_ARRIVAL_MINUTE,
                EvSmartChargePlanner.DEFAULT_DEADLINE_MINUTE);
        // 8 slots from 18:00 onward on the plug-in day itself.
        EVCharge first = planned.get(0);
        assertEquals(18 * 60, first.getEffectiveBeginMinute());
        assertEquals(22 * 60, first.getEffectiveEndMinute());
        assertEquals("01/01", first.getStartDate());
        assertEquals("12/31", first.getEndDate());
        assertEquals(1, planned.size());
    }

    private static long countDays(String startMmdd, String endMmdd) {
        return dateOf(endMmdd).toEpochDay() - dateOf(startMmdd).toEpochDay() + 1;
    }

    private static LocalDate dateOf(String mmdd) {
        String[] bits = mmdd.split("/");
        return LocalDate.of(2001, Integer.parseInt(bits[0]), Integer.parseInt(bits[1]));
    }
}
