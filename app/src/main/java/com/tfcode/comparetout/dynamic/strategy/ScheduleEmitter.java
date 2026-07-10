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

import com.tfcode.comparetout.model.scenario.DischargeToGrid;
import com.tfcode.comparetout.model.scenario.LoadShift;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a year of {@link DayDecisions} into schedule rows for a generated
 * scenario. Consecutive days with identical windows coalesce into one
 * multi-day row (startDate..endDate, MM/DD) — row-count hygiene for the
 * scenario editors and the engine's per-group precompute.
 *
 * <p>Rows use the v16 minute-granular fields; the legacy whole-hour
 * begin/end are set to the enclosing hours purely for display (the engine
 * ignores them once beginMinute/endMinute are explicit). Days/months filters
 * stay at their defaults (all) — the date window does the limiting.
 *
 * <p>Charge windows become LoadShift rows on a single inverter name (the
 * engine applies every LoadShift to each battery inverter regardless of the
 * name — it only groups by it). Discharge windows become one DischargeToGrid
 * row per battery-bearing inverter, because the engine filters those by
 * inverter name; each row's kW rate is its own inverter's limit, capped by
 * the window rate.
 */
public final class ScheduleEmitter {

    public static final class Emitted {
        public final List<LoadShift> loadShifts;
        public final List<DischargeToGrid> discharges;

        Emitted(List<LoadShift> loadShifts, List<DischargeToGrid> discharges) {
            this.loadShifts = loadShifts;
            this.discharges = discharges;
        }
    }

    private ScheduleEmitter() {
    }

    public static Emitted emit(Map<LocalDate, DayDecisions> days, String strategyName,
                               String chargeInverterName,
                               Map<String, Double> dischargeRateKwByInverter) {
        List<LoadShift> loadShifts = new ArrayList<>();
        List<DischargeToGrid> discharges = new ArrayList<>();

        List<LocalDate> dates = new ArrayList<>(days.keySet());
        dates.sort(LocalDate::compareTo);

        int i = 0;
        while (i < dates.size()) {
            LocalDate start = dates.get(i);
            DayDecisions decisions = days.get(start);
            int j = i;
            while (j + 1 < dates.size()
                    && dates.get(j + 1).equals(dates.get(j).plusDays(1))
                    && sameWindows(decisions, days.get(dates.get(j + 1)))) {
                j++;
            }
            LocalDate end = dates.get(j);
            if (!(null == decisions) && !decisions.isEmpty()) {
                emitRun(decisions, start, end, strategyName, chargeInverterName,
                        dischargeRateKwByInverter, loadShifts, discharges);
            }
            i = j + 1;
        }
        return new Emitted(loadShifts, discharges);
    }

    private static boolean sameWindows(DayDecisions a, DayDecisions b) {
        if (null == a || null == b) return false;
        return a.chargeWindows.equals(b.chargeWindows)
                && a.dischargeWindows.equals(b.dischargeWindows);
    }

    private static void emitRun(DayDecisions decisions, LocalDate start, LocalDate end,
                                String strategyName, String chargeInverterName,
                                Map<String, Double> dischargeRateKwByInverter,
                                List<LoadShift> loadShifts, List<DischargeToGrid> discharges) {
        String startDate = mmdd(start);
        String endDate = mmdd(end);
        for (DayDecisions.Window w : decisions.chargeWindows) {
            LoadShift ls = new LoadShift();
            ls.setLoadShiftIndex(0);
            ls.setName(rowName(strategyName, "charge", w));
            ls.setInverter(chargeInverterName);
            ls.setBegin(w.beginMinute / 60);
            ls.setEnd(Math.max(w.beginMinute / 60, (w.endMinute - 1) / 60));
            ls.setStopAt(w.stopAtPercent);
            ls.setStartDate(startDate);
            ls.setEndDate(endDate);
            ls.setBeginMinute(w.beginMinute);
            ls.setEndMinute(w.endMinute);
            loadShifts.add(ls);
        }
        for (DayDecisions.Window w : decisions.dischargeWindows) {
            for (Map.Entry<String, Double> inverter : dischargeRateKwByInverter.entrySet()) {
                DischargeToGrid d2g = new DischargeToGrid();
                d2g.setD2gIndex(0);
                d2g.setName(rowName(strategyName, "export", w));
                d2g.setInverter(inverter.getKey());
                d2g.setBegin(w.beginMinute / 60);
                d2g.setEnd(Math.max(w.beginMinute / 60, (w.endMinute - 1) / 60));
                d2g.setStopAt(w.stopAtPercent);
                d2g.setRate(w.rateKw > 0 ? Math.min(w.rateKw, inverter.getValue()) : inverter.getValue());
                d2g.setStartDate(startDate);
                d2g.setEndDate(endDate);
                d2g.setBeginMinute(w.beginMinute);
                d2g.setEndMinute(w.endMinute);
                discharges.add(d2g);
            }
        }
    }

    private static String rowName(String strategyName, String kind, DayDecisions.Window w) {
        return String.format(Locale.ROOT, "⚡ %s %s %s–%s",
                strategyName, kind, hhmm(w.beginMinute), hhmm(w.endMinute));
    }

    private static String hhmm(int minuteOfDay) {
        return String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private static String mmdd(LocalDate date) {
        return String.format(Locale.ROOT, "%02d/%02d", date.getMonthValue(), date.getDayOfMonth());
    }
}
