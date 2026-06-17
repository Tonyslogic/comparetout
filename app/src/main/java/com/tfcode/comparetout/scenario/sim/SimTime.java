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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Boundary conversions between the existing model's wall-clock fields
 * ({@code date} + minute-of-day) and milliseconds-since-epoch.
 *
 * <p>Phase 1 of the simulation-engine refactor (see {@code plans/sim/refactor.md}). The persisted
 * rows ({@code loadprofiledata} / {@code paneldata} / {@code scenariosimulationdata}) already carry a
 * nullable {@code millisSinceEpoch} column. This helper lets the engine work in millis while
 * tolerating legacy rows whose column is still NULL: derive the value from {@code date} + {@code mod}
 * at the I/O boundary.</p>
 *
 * <p>The zone is always passed explicitly. Choosing the simulation's canonical zone (and then
 * populating the stored column) is a later, deliberately-gated step; nothing here writes output yet.</p>
 */
public final class SimTime {

    private SimTime() {
    }

    /** Convert an ISO local date ({@code yyyy-MM-dd}) plus a minute-of-day to epoch millis in {@code zone}. */
    public static long fromDateAndMinuteOfDay(String isoDate, int minuteOfDay, ZoneId zone) {
        LocalDateTime ldt = LocalDate.parse(isoDate).atStartOfDay().plusMinutes(minuteOfDay);
        return toEpochMillis(ldt, zone);
    }

    /**
     * Resolve a wall-clock {@link LocalDateTime} to epoch millis in {@code zone}. DST gaps/overlaps
     * are resolved by {@link java.time.ZonedDateTime}'s default rules.
     */
    public static long toEpochMillis(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime.atZone(zone).toInstant().toEpochMilli();
    }

    /** The wall-clock {@link LocalDateTime} for an epoch-millis instant in {@code zone}. */
    public static LocalDateTime toLocalDateTime(long epochMillis, ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone);
    }

    /** Minute-of-day (0&ndash;1439) for an epoch-millis instant in {@code zone}. */
    public static int minuteOfDay(long epochMillis, ZoneId zone) {
        LocalDateTime ldt = toLocalDateTime(epochMillis, zone);
        return ldt.getHour() * 60 + ldt.getMinute();
    }
}
