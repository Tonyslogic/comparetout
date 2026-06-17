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

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An ordered, uniform sequence of {@link SimInterval}s covering the half-open range
 * {@code [start, end)} at a fixed step.
 *
 * <p>Phase 1 of the simulation-engine refactor (see {@code plans/sim/refactor.md}). This is the
 * Iterator that replaces the engine's hardcoded "loop over 105,120 rows of year 2001". It lets the
 * simulation run over an arbitrary period and keys every step by milliseconds-since-epoch.</p>
 *
 * <p>Sampling is uniform in absolute time (fixed millisecond step), not wall-clock time. That is the
 * deliberate fix for the old DST handling: a fixed-millis step neither skips nor repeats samples
 * across a daylight-saving transition. Callers that need the wall-clock representation convert each
 * interval's start via {@link SimInterval#startLocal(ZoneId)} / {@link SimTime}.</p>
 */
public final class TimeAxis implements Iterable<SimInterval> {

    /** The simulation's native cadence: five minutes, in milliseconds. */
    public static final long FIVE_MINUTES_MILLIS = Duration.ofMinutes(5).toMillis();

    private final long startMillis;
    private final long endMillis; // exclusive
    private final long stepMillis;
    private final int count;

    /**
     * @param startMillis start of the range, inclusive (epoch millis)
     * @param endMillis   end of the range, exclusive (epoch millis)
     * @param stepMillis  interval length in milliseconds (positive)
     */
    public TimeAxis(long startMillis, long endMillis, long stepMillis) {
        if (stepMillis <= 0) {
            throw new IllegalArgumentException("stepMillis must be positive: " + stepMillis);
        }
        if (endMillis < startMillis) {
            throw new IllegalArgumentException("endMillis must not be before startMillis");
        }
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.stepMillis = stepMillis;
        // Whole steps that start strictly before end. A trailing partial step is not emitted.
        this.count = (int) ((endMillis - startMillis) / stepMillis);
    }

    /** A five-minute axis over {@code [start, end)}. */
    public static TimeAxis fiveMinute(long startMillis, long endMillis) {
        return new TimeAxis(startMillis, endMillis, FIVE_MINUTES_MILLIS);
    }

    /**
     * A five-minute axis spanning the given wall-clock range in a zone. The endpoints are resolved
     * to instants in {@code zone}; iteration then proceeds by fixed millisecond steps.
     */
    public static TimeAxis fiveMinute(ZonedDateTime startInclusive, ZonedDateTime endExclusive) {
        return fiveMinute(startInclusive.toInstant().toEpochMilli(), endExclusive.toInstant().toEpochMilli());
    }

    /** The number of whole intervals in this axis. */
    public int intervalCount() {
        return count;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public long getStepMillis() {
        return stepMillis;
    }

    /** The interval at the given index (0-based). */
    public SimInterval intervalAt(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("index " + index + " out of [0," + count + ")");
        }
        return new SimInterval(startMillis + (long) index * stepMillis, stepMillis);
    }

    @Override
    public Iterator<SimInterval> iterator() {
        return new Iterator<SimInterval>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return i < count;
            }

            @Override
            public SimInterval next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return intervalAt(i++);
            }
        };
    }
}
