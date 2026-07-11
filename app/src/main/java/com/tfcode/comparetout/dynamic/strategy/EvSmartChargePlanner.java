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

import com.tfcode.comparetout.model.scenario.EVCharge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The EV deadline strategy (plan Phase 7): reach the same state of charge by
 * departure at least cost. Each base EVCharge row defines a daily charging
 * need — its duration × draw — and the planner reschedules exactly that many
 * half-hours into the cheapest slots of the availability window
 * (home-arrival → departure deadline, typically overnight) using the dynamic
 * plan's prices, day by day across the year.
 *
 * <p>Sessions run per plug-in day (the base row's months/days filters).
 * An overnight window splits across two calendar dates: evening slots price
 * and land on day D, morning slots on day D+1. On the last day of the year
 * the morning half doesn't exist, so the session makes do with the evening
 * (charged before the deadline, just dearer). Emitted rows are single-purpose
 * EVCharge rows driven entirely by the v16 date+minute windows, coalesced
 * across consecutive identical days like the battery emitter.
 */
public final class EvSmartChargePlanner {

    /** Default home-arrival time: 18:00. */
    public static final int DEFAULT_ARRIVAL_MINUTE = 18 * 60;
    /** Default departure deadline: 08:00 (next morning). */
    public static final int DEFAULT_DEADLINE_MINUTE = 8 * 60;

    private EvSmartChargePlanner() {
    }

    /**
     * Plan replacement rows for every base row. {@code arrivalMinute >
     * deadlineMinute} means an overnight window (the default); otherwise the
     * window sits inside the plug-in day.
     */
    public static List<EVCharge> plan(List<EVCharge> baseCharges,
                                      StrategyYearRunner.HalfHourlyProvider buy,
                                      int arrivalMinute, int deadlineMinute) {
        List<EVCharge> planned = new ArrayList<>();
        for (EVCharge base : baseCharges) {
            planned.addAll(planOne(base, buy, arrivalMinute, deadlineMinute));
        }
        return planned;
    }

    private static List<EVCharge> planOne(EVCharge base, StrategyYearRunner.HalfHourlyProvider buy,
                                          int arrivalMinute, int deadlineMinute) {
        int slotsNeeded = Math.max(1,
                (base.getEffectiveEndMinute() - base.getEffectiveBeginMinute()) / 30);
        boolean overnight = arrivalMinute > deadlineMinute;

        // Chosen slots per actual calendar date (a session can span two).
        Map<LocalDate, boolean[]> chosen = new TreeMap<>();
        // Memoise each date's prices — consecutive sessions share a boundary day.
        Map<LocalDate, double[]> prices = new LinkedHashMap<>();

        LocalDate day = LocalDate.of(2001, 1, 1);
        while (day.getYear() == 2001) {
            if (appliesOn(base, day)) {
                planSession(day, slotsNeeded, overnight, arrivalMinute, deadlineMinute,
                        buy, prices, chosen);
            }
            day = day.plusDays(1);
        }
        return emit(base, chosen);
    }

    private static boolean appliesOn(EVCharge base, LocalDate day) {
        int dow = day.getDayOfWeek().getValue();
        if (dow == 7) dow = 0;
        return base.getMonths().months.contains(day.getMonthValue())
                && base.getDays().ints.contains(dow);
    }

    private static void planSession(LocalDate day, int slotsNeeded, boolean overnight,
                                    int arrivalMinute, int deadlineMinute,
                                    StrategyYearRunner.HalfHourlyProvider buy,
                                    Map<LocalDate, double[]> prices,
                                    Map<LocalDate, boolean[]> chosen) {
        // The candidate slots in chronological order: (date, slot, price).
        List<long[]> candidates = new ArrayList<>(); // [epochDay, slot], price parallel
        List<Double> candidatePrices = new ArrayList<>();
        double[] today = prices.computeIfAbsent(day, buy::halfHourly);
        if (overnight) {
            for (int s = arrivalMinute / 30; s < 48; s++) {
                candidates.add(new long[]{day.toEpochDay(), s});
                candidatePrices.add(today[s]);
            }
            LocalDate next = day.plusDays(1);
            if (next.getYear() == day.getYear()) {
                double[] tomorrow = prices.computeIfAbsent(next, buy::halfHourly);
                for (int s = 0; s < deadlineMinute / 30; s++) {
                    candidates.add(new long[]{next.toEpochDay(), s});
                    candidatePrices.add(tomorrow[s]);
                }
            }
        } else {
            for (int s = arrivalMinute / 30; s < deadlineMinute / 30; s++) {
                candidates.add(new long[]{day.toEpochDay(), s});
                candidatePrices.add(today[s]);
            }
        }
        if (candidates.isEmpty()) return;

        // Cheapest first; chronological on ties (charge sooner when equal).
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) order.add(i);
        order.sort(Comparator
                .comparingDouble((Integer i) -> candidatePrices.get(i))
                .thenComparingInt(i -> i));
        int take = Math.min(slotsNeeded, candidates.size());
        for (int i = 0; i < take; i++) {
            long[] pick = candidates.get(order.get(i));
            LocalDate date = LocalDate.ofEpochDay(pick[0]);
            chosen.computeIfAbsent(date, d -> new boolean[48])[(int) pick[1]] = true;
        }
    }

    /** Coalesce consecutive dates with identical slot masks into multi-day rows. */
    private static List<EVCharge> emit(EVCharge base, Map<LocalDate, boolean[]> chosen) {
        List<EVCharge> rows = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>(chosen.keySet());
        int i = 0;
        while (i < dates.size()) {
            LocalDate start = dates.get(i);
            boolean[] mask = chosen.get(start);
            int j = i;
            while (j + 1 < dates.size()
                    && dates.get(j + 1).equals(dates.get(j).plusDays(1))
                    && java.util.Arrays.equals(mask, chosen.get(dates.get(j + 1)))) {
                j++;
            }
            LocalDate end = dates.get(j);
            for (int[] window : windows(mask)) {
                EVCharge row = new EVCharge();
                row.setEvChargeIndex(0);
                row.setName(String.format(Locale.ROOT, "⚡ EV %s %02d:%02d–%02d:%02d",
                        base.getName(), window[0] / 60, window[0] % 60,
                        window[1] / 60, window[1] % 60));
                row.setDraw(base.getDraw());
                row.setBegin(window[0] / 60);
                row.setEnd(Math.max(window[0] / 60, (window[1] - 1) / 60));
                row.setStartDate(mmdd(start));
                row.setEndDate(mmdd(end));
                row.setBeginMinute(window[0]);
                row.setEndMinute(window[1]);
                rows.add(row);
            }
            i = j + 1;
        }
        return rows;
    }

    /** Contiguous true runs of the mask as [beginMinute, endMinute) pairs. */
    private static List<int[]> windows(boolean[] mask) {
        List<int[]> out = new ArrayList<>();
        int start = -1;
        for (int s = 0; s <= mask.length; s++) {
            boolean active = s < mask.length && mask[s];
            if (active && start < 0) start = s;
            if (!active && start >= 0) {
                out.add(new int[]{start * 30, s * 30});
                start = -1;
            }
        }
        return out;
    }

    private static String mmdd(LocalDate date) {
        return String.format(Locale.ROOT, "%02d/%02d", date.getMonthValue(), date.getDayOfMonth());
    }
}
