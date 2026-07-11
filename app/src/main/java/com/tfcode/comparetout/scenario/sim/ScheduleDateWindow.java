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

import java.time.LocalDate;

/**
 * The v16 MM/DD date-window check shared by the HW/EV schedule components
 * (Phase 7 of the dynamic-tariff plan). Semantics match the battery engine's
 * {@code SimulationEngine.dayOfYearWindow}: inclusive calendar bounds, no
 * wrap-around (an end before the start never matches), malformed dates behave
 * like the pre-v16 default — the full year. Comparison uses month×100+day
 * keys, whose ordering equals day-of-year ordering in any calendar year, so
 * leap days need no special case.
 *
 * <p>{@code dayOfMonth <= 0} means "caller has no date" and disables the
 * check — the legacy 4-arg schedule lookups delegate that way, staying
 * byte-identical for every pre-v16 schedule row.
 */
public final class ScheduleDateWindow {

    private ScheduleDateWindow() {
    }

    /** True for the zero-touch defaults — the fast path that skips all parsing. */
    public static boolean isDefault(String startDate, String endDate) {
        return "01/01".equals(startDate) && "12/31".equals(endDate);
    }

    /** True when (month, dayOfMonth) falls inside the inclusive MM/DD window. */
    public static boolean contains(String startDate, String endDate, int month, int dayOfMonth) {
        if (dayOfMonth <= 0 || isDefault(startDate, endDate)) return true;
        int start = mmddKey(startDate);
        int end = mmddKey(endDate);
        if (start < 0 || end < 0) return true; // malformed → full year, like the engine
        int key = month * 100 + dayOfMonth;
        return start <= key && key <= end;
    }

    /** "MM/DD" → month×100+day, or −1 when malformed. */
    static int mmddKey(String mmdd) {
        if (null == mmdd) return -1;
        int slash = mmdd.indexOf('/');
        if (slash <= 0 || slash == mmdd.length() - 1) return -1;
        try {
            int month = Integer.parseInt(mmdd.substring(0, slash).trim());
            int day = Integer.parseInt(mmdd.substring(slash + 1).trim());
            return month * 100 + day;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** UTC day-of-month for a canonical interval instant. */
    public static int dayOfMonthUtc(long millis) {
        return LocalDate.ofEpochDay(Math.floorDiv(millis, 86_400_000L)).getDayOfMonth();
    }
}
