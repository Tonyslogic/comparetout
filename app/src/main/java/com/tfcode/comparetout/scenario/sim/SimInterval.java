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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A single simulation time step: a half-open interval {@code [start, start + duration)} expressed in
 * milliseconds since the Unix epoch.
 *
 * <p>Part of Phase 1 of the simulation-engine refactor (see {@code plans/sim/refactor.md}). This is
 * the unit the new engine iterates over instead of an opaque row index into a fixed year-2001 array.
 * It is an immutable value type with no dependency on Android, Room, or the existing model — pure and
 * unit-testable.</p>
 */
public final class SimInterval {

    private final long startMillis;
    private final long durationMillis;

    public SimInterval(long startMillis, long durationMillis) {
        if (durationMillis <= 0) {
            throw new IllegalArgumentException("durationMillis must be positive: " + durationMillis);
        }
        this.startMillis = startMillis;
        this.durationMillis = durationMillis;
    }

    /** Start of the interval, inclusive, in epoch milliseconds. */
    public long getStartMillis() {
        return startMillis;
    }

    /** Length of the interval in milliseconds. */
    public long getDurationMillis() {
        return durationMillis;
    }

    /** End of the interval, exclusive, in epoch milliseconds. */
    public long getEndMillis() {
        return startMillis + durationMillis;
    }

    /** The interval duration as a fraction of an hour (e.g. 5 minutes -&gt; 1/12), for kW -&gt; kWh scaling. */
    public double getHours() {
        return durationMillis / 3_600_000d;
    }

    /** The wall-clock start of this interval in the given zone. */
    public LocalDateTime startLocal(ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(startMillis), zone);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimInterval that = (SimInterval) o;
        return startMillis == that.startMillis && durationMillis == that.durationMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(startMillis, durationMillis);
    }

    @Override
    public String toString() {
        return "SimInterval{start=" + startMillis + ", duration=" + durationMillis + "}";
    }
}
