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

import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.LoadShift;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The decisions→rows emitter: consecutive identical days coalesce into one
 * multi-day row, gaps split runs, discharge rows fan out per inverter with
 * that inverter's own rate cap.
 */
public class ScheduleEmitterTest {

    private static DayDecisions charging(int beginMinute, int endMinute) {
        return new DayDecisions(
                Collections.singletonList(
                        new DayDecisions.Window(beginMinute, endMinute, 100, 0)),
                Collections.emptyList(), 5);
    }

    private static DayDecisions exporting(int beginMinute, int endMinute, double rateKw) {
        return new DayDecisions(
                Collections.emptyList(),
                Collections.singletonList(
                        new DayDecisions.Window(beginMinute, endMinute, 20, rateKw)), 5);
    }

    private static DayDecisions nothing() {
        return new DayDecisions(Collections.emptyList(), Collections.emptyList(), 5);
    }

    private static Map<String, Double> oneInverter() {
        return Collections.singletonMap("AlphaESS", 2.7);
    }

    @Test
    public void identicalConsecutiveDaysCoalesceIntoOneRow() {
        Map<LocalDate, DayDecisions> days = new LinkedHashMap<>();
        days.put(LocalDate.of(2001, 1, 1), charging(120, 300));
        days.put(LocalDate.of(2001, 1, 2), charging(120, 300));
        days.put(LocalDate.of(2001, 1, 3), charging(120, 300));
        days.put(LocalDate.of(2001, 1, 4), charging(0, 180)); // different → new run
        ScheduleEmitter.Emitted emitted =
                ScheduleEmitter.emit(days, "Threshold", "AlphaESS", oneInverter());

        assertEquals(2, emitted.loadShifts.size());
        assertTrue(emitted.discharges.isEmpty());
        LoadShift first = emitted.loadShifts.get(0);
        assertEquals("01/01", first.getStartDate());
        assertEquals("01/03", first.getEndDate());
        assertEquals(120, first.getEffectiveBeginMinute());
        assertEquals(300, first.getEffectiveEndMinute());
        assertEquals(100d, first.getStopAt(), 0d);
        assertEquals("AlphaESS", first.getInverter());
        LoadShift second = emitted.loadShifts.get(1);
        assertEquals("01/04", second.getStartDate());
        assertEquals("01/04", second.getEndDate());
    }

    @Test
    public void emptyDaysSplitRuns() {
        Map<LocalDate, DayDecisions> days = new LinkedHashMap<>();
        days.put(LocalDate.of(2001, 3, 1), charging(60, 120));
        days.put(LocalDate.of(2001, 3, 2), nothing());
        days.put(LocalDate.of(2001, 3, 3), charging(60, 120));
        ScheduleEmitter.Emitted emitted =
                ScheduleEmitter.emit(days, "Threshold", "AlphaESS", oneInverter());
        assertEquals(2, emitted.loadShifts.size());
        assertEquals("03/01", emitted.loadShifts.get(0).getStartDate());
        assertEquals("03/01", emitted.loadShifts.get(0).getEndDate());
        assertEquals("03/03", emitted.loadShifts.get(1).getStartDate());
    }

    @Test
    public void allEmptyDaysEmitNothing() {
        Map<LocalDate, DayDecisions> days = new LinkedHashMap<>();
        LocalDate d = LocalDate.of(2001, 1, 1);
        while (d.getYear() == 2001) {
            days.put(d, nothing());
            d = d.plusDays(1);
        }
        ScheduleEmitter.Emitted emitted =
                ScheduleEmitter.emit(days, "Threshold", "AlphaESS", oneInverter());
        assertTrue(emitted.loadShifts.isEmpty());
        assertTrue(emitted.discharges.isEmpty());
    }

    @Test
    public void dischargeRowsFanOutPerInverterWithTheirOwnRateCap() {
        Map<String, Double> inverters = new TreeMap<>();
        inverters.put("East", 2.7);
        inverters.put("West", 5.0);
        Map<LocalDate, DayDecisions> days = new LinkedHashMap<>();
        days.put(LocalDate.of(2001, 11, 15), exporting(17 * 60 + 30, 19 * 60, 3.0));
        ScheduleEmitter.Emitted emitted =
                ScheduleEmitter.emit(days, "Rank-6/4", "East", inverters);

        assertEquals(2, emitted.discharges.size());
        List<Double> rates = new ArrayList<>();
        for (DischargeToGrid d2g : emitted.discharges) {
            assertEquals("11/15", d2g.getStartDate());
            assertEquals("11/15", d2g.getEndDate());
            assertEquals(17 * 60 + 30, d2g.getEffectiveBeginMinute());
            assertEquals(19 * 60, d2g.getEffectiveEndMinute());
            assertEquals(20d, d2g.getStopAt(), 0d);
            rates.add(d2g.getRate());
        }
        // East is capped by its own 2.7 kW; West by the 3.0 kW window rate.
        assertTrue(rates.contains(2.7));
        assertTrue(rates.contains(3.0));
    }

    @Test
    public void unsortedInputDatesStillCoalesceInCalendarOrder() {
        Map<LocalDate, DayDecisions> days = new LinkedHashMap<>();
        days.put(LocalDate.of(2001, 5, 2), charging(0, 60));
        days.put(LocalDate.of(2001, 5, 1), charging(0, 60));
        days.put(LocalDate.of(2001, 5, 3), charging(0, 60));
        ScheduleEmitter.Emitted emitted =
                ScheduleEmitter.emit(days, "Threshold", "AlphaESS", oneInverter());
        assertEquals(1, emitted.loadShifts.size());
        assertEquals("05/01", emitted.loadShifts.get(0).getStartDate());
        assertEquals("05/03", emitted.loadShifts.get(0).getEndDate());
    }
}
