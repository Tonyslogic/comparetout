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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class TimeAxisTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");

    @Test
    public void arbitraryPeriod_countAndBoundaries() {
        long start = 1_000_000_000_000L;
        long step = TimeAxis.FIVE_MINUTES_MILLIS;
        long end = start + 3 * 60 * 60 * 1000L; // 3 hours
        TimeAxis axis = TimeAxis.fiveMinute(start, end);

        assertEquals(36, axis.intervalCount()); // 3h / 5min
        assertEquals(start, axis.intervalAt(0).getStartMillis());
        assertEquals(start + 35 * step, axis.intervalAt(35).getStartMillis());
        assertEquals(end, axis.intervalAt(35).getEndMillis());
    }

    @Test
    public void fullDay_isTwoEightyEightIntervals() {
        long start = 0L;
        long end = Duration.ofDays(1).toMillis();
        assertEquals(288, TimeAxis.fiveMinute(start, end).intervalCount());
    }

    @Test
    public void iteratorYieldsContiguousIntervals() {
        long start = 500L;
        long end = start + 4 * TimeAxis.FIVE_MINUTES_MILLIS;
        List<SimInterval> seen = new ArrayList<>();
        for (SimInterval interval : TimeAxis.fiveMinute(start, end)) {
            seen.add(interval);
        }
        assertEquals(4, seen.size());
        for (int i = 1; i < seen.size(); i++) {
            assertEquals("intervals must be contiguous",
                    seen.get(i - 1).getEndMillis(), seen.get(i).getStartMillis());
        }
        assertEquals(1d / 12d, seen.get(0).getHours(), 1e-9); // five minutes as a fraction of an hour
    }

    @Test
    public void emptyWhenStartEqualsEnd() {
        TimeAxis axis = TimeAxis.fiveMinute(42L, 42L);
        assertEquals(0, axis.intervalCount());
        assertFalse(axis.iterator().hasNext());
    }

    @Test
    public void trailingPartialStepIsNotEmitted() {
        long start = 0L;
        long end = TimeAxis.FIVE_MINUTES_MILLIS + 1; // one full step plus a sliver
        assertEquals(1, TimeAxis.fiveMinute(start, end).intervalCount());
    }

    /**
     * A fixed-millisecond step samples uniformly across a DST spring-forward: the count is exactly
     * the elapsed absolute time / step, with no skipped or duplicated samples (the bug the old
     * row-shift DST handling worked around). Europe/Dublin springs forward on 2001-03-25 01:00.
     */
    @Test
    public void uniformSamplingAcrossDstSpringForward() {
        ZonedDateTime startWall = ZonedDateTime.of(2001, 3, 25, 0, 0, 0, 0, DUBLIN);
        ZonedDateTime endWall = ZonedDateTime.of(2001, 3, 25, 4, 0, 0, 0, DUBLIN);
        TimeAxis axis = TimeAxis.fiveMinute(startWall, endWall);

        // Wall clock spans 4 hours but one hour is skipped by the clocks, so only 3 real hours elapse.
        assertEquals(3 * 12, axis.intervalCount());

        // Every step is exactly five minutes of absolute time.
        SimInterval first = axis.intervalAt(0);
        SimInterval second = axis.intervalAt(1);
        assertEquals(TimeAxis.FIVE_MINUTES_MILLIS, second.getStartMillis() - first.getStartMillis());
    }

    @Test
    public void iteratorIsExhaustible() {
        TimeAxis axis = TimeAxis.fiveMinute(0L, 2 * TimeAxis.FIVE_MINUTES_MILLIS);
        java.util.Iterator<SimInterval> it = axis.iterator();
        assertTrue(it.hasNext());
        it.next();
        assertTrue(it.hasNext());
        it.next();
        assertFalse(it.hasNext());
    }
}
