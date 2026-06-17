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

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class SimTimeTest {

    private static final ZoneId DUBLIN = ZoneId.of("Europe/Dublin");

    @Test
    public void dateAndMinuteOfDayToEpochMillis_utc() {
        // 2001-01-01T00:00:00Z is a well-known epoch-millis value.
        long expected = LocalDateTime.of(2001, 1, 1, 0, 0)
                .atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(expected, SimTime.fromDateAndMinuteOfDay("2001-01-01", 0, ZoneOffset.UTC));
    }

    @Test
    public void minuteOfDayApplied() {
        long base = SimTime.fromDateAndMinuteOfDay("2001-06-15", 0, ZoneOffset.UTC);
        long at0725 = SimTime.fromDateAndMinuteOfDay("2001-06-15", 7 * 60 + 25, ZoneOffset.UTC);
        assertEquals(7 * 60 + 25, (at0725 - base) / 60000);
    }

    @Test
    public void roundTripOutsideDst() {
        long millis = SimTime.fromDateAndMinuteOfDay("2001-06-15", 12 * 60 + 30, DUBLIN);
        LocalDateTime back = SimTime.toLocalDateTime(millis, DUBLIN);
        assertEquals(LocalDateTime.of(2001, 6, 15, 12, 30), back);
        assertEquals(12 * 60 + 30, SimTime.minuteOfDay(millis, DUBLIN));
    }

    @Test
    public void minuteOfDayDependsOnZone() {
        // A fixed instant has a different minute-of-day in zones with different offsets.
        long millis = SimTime.fromDateAndMinuteOfDay("2001-06-15", 0, ZoneOffset.UTC);
        assertEquals(0, SimTime.minuteOfDay(millis, ZoneOffset.UTC));
        assertEquals(60, SimTime.minuteOfDay(millis, ZoneOffset.ofHours(1)));
    }
}
