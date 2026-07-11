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

import static java.lang.Double.min;

import com.tfcode.comparetout.model.scenario.EVDivert;

import java.util.List;
import java.util.Map;

/**
 * EV divert as a {@link SurplusSink} (Phase B of the component-registration refactor, see
 * {@code plans/sim/component.md}). It consumes surplus PV for EV charging, respecting the configured
 * minimum and the per-day cap, and owns the per-day accumulator.
 *
 * <p>Byte-identical reproduction of the legacy engine: the daily accumulator is the SAME map instance
 * held by {@code ScenarioInputs} (so totals carry across intervals exactly as before); the divert is
 * {@code min(feed, dailyMax - total)} gated by {@code feed > minimum / 12d} and a positive remaining
 * cap. The engine decides the ordering relative to hot-water divert (the {@code isEv1st()} flow), so
 * this component does only its own arithmetic.</p>
 */
public final class EvDivertComponent implements SurplusSink {

    private final List<EVDivert> evDiverts;
    private final Map<Integer, Double> dailyTotals;
    // Derive the UTC day-of-month only when some schedule actually carries a
    // v16 date window — the defaults path stays allocation-free per interval.
    private final boolean anyDateWindowed;

    public EvDivertComponent(List<EVDivert> evDiverts, Map<Integer, Double> dailyTotals) {
        this.evDiverts = evDiverts;
        this.dailyTotals = dailyTotals;
        boolean windowed = false;
        if (!(null == evDiverts)) for (EVDivert d : evDiverts) {
            if (!ScheduleDateWindow.isDefault(d.getStartDate(), d.getEndDate())) windowed = true;
        }
        this.anyDateWindowed = windowed;
    }

    /**
     * The EV divert scheduled and active for this interval, or null. Returns null for an inactive
     * divert, reproducing the legacy {@code if (evDivert != null) if (evDivert.isActive())} guard so
     * the engine can branch on {@code isEv1st()} only when a divert actually applies.
     */
    public EVDivert activeDivertOrNull(IntervalContext ctx) {
        int dayOfMonth = anyDateWindowed ? ScheduleDateWindow.dayOfMonthUtc(ctx.millis) : 0;
        EVDivert ev = scheduledDivertOrNull(evDiverts, ctx.dayOfWeek, ctx.month,
                dayOfMonth, ctx.minuteOfDay);
        return (null != ev && ev.isActive()) ? ev : null;
    }

    @Override
    public double absorb(double availableKWh, IntervalContext ctx) {
        EVDivert ev = activeDivertOrNull(ctx);
        if (null == ev) return 0d;
        double total = dailyTotals.getOrDefault(ctx.evDivertDay, 0d);
        double maxEVDivert = ev.getDailyMax() - total;
        if (availableKWh > ev.getMinimum() / 12d && maxEVDivert > 0) {
            double diverted = min(availableKWh, maxEVDivert);
            dailyTotals.put(ctx.evDivertDay, total + diverted);
            return diverted;
        }
        return 0d;
    }

    @Override
    public OutputChannel divertChannel() {
        return OutputChannel.DIV_TO_EV;
    }

    /**
     * The EV divert scheduled for the given time, or null (ignores active/inactive). Single source of
     * truth — {@code ScenarioInputs.getEVDivertOrNull} delegates here. Day-of-week 7 (Sun) normalises
     * to 0. {@code dayOfMonth <= 0} disables the v16 date-window check; the minute window uses the
     * effective minutes, which reproduce the legacy whole-hour exclusive-end check exactly when unset.
     */
    public static EVDivert scheduledDivertOrNull(List<EVDivert> evDiverts, int dayOfWeek,
                                                 int monthOfYear, int dayOfMonth, int minuteOfDay) {
        if (dayOfWeek == 7) dayOfWeek = 0;
        if (!(null == evDiverts)) for (EVDivert evDivert : evDiverts) {
            if (evDivert.getMonths().months.contains(monthOfYear) &&
                    evDivert.getDays().ints.contains(dayOfWeek) &&
                    ScheduleDateWindow.contains(evDivert.getStartDate(), evDivert.getEndDate(),
                            monthOfYear, dayOfMonth) &&
                    evDivert.getEffectiveBeginMinute() <= minuteOfDay &&
                    evDivert.getEffectiveEndMinute() > minuteOfDay) {
                return evDivert;
            }
        }
        return null;
    }

    /** Date-blind legacy lookup — kept for callers that have no interval date. */
    public static EVDivert scheduledDivertOrNull(List<EVDivert> evDiverts,
                                                 int dayOfWeek, int monthOfYear, int minuteOfDay) {
        return scheduledDivertOrNull(evDiverts, dayOfWeek, monthOfYear, 0, minuteOfDay);
    }
}
