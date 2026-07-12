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

package com.tfcode.comparetout.dynamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class SeriesNormaliserTest {

    private static final double DELTA = 1e-9;

    /** An hourly (60-minute era) auction day: 24 periods from 23:00Z the day before. */
    private static SemopxDayResultCsv.DayResult hourlyDay(LocalDate auctionDate, double eurPerMwh)
            throws IOException {
        long start = auctionDate.atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
        StringBuilder stamps = new StringBuilder();
        StringBuilder prices = new StringBuilder();
        for (int h = 0; h < 24; h++) {
            if (h > 0) {
                stamps.append(';');
                prices.append(';');
            }
            stamps.append(java.time.Instant.ofEpochMilli(start + h * 3_600_000L));
            prices.append(String.valueOf(eurPerMwh).replace('.', ','));
        }
        return SemopxDayResultCsv.parse(String.join("\n",
                "Market Area;ROI-DA", "Index prices;60;EUR",
                stamps.toString(), prices.toString()));
    }

    private static List<SemopxDayResultCsv.DayResult> fullMonthHourly(
            int year, int month, double eurPerMwh) throws IOException {
        List<SemopxDayResultCsv.DayResult> days = new ArrayList<>();
        LocalDate first = LocalDate.of(year, month, 1).minusDays(1);
        LocalDate last = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            days.add(hourlyDay(d, eurPerMwh));
        }
        return days;
    }

    @Test
    public void hourlyDaysExpandToAFullHalfHourlyMonthInCents() throws IOException {
        SeriesNormaliser.MonthSeries month = SeriesNormaliser.assembleMonth(
                2019, 2, fullMonthHourly(2019, 2, 65.0));
        assertNotNull(month);
        assertEquals(28 * 48, month.utcMillis.length);
        // EUR/MWh ÷ 10 → c/kWh; every half-hour carries the hour's price.
        assertEquals(6.5, month.centsPerKwh[0], DELTA);
        assertEquals(6.5, month.centsPerKwh[1], DELTA);
        assertEquals(SeriesNormaliser.monthStartMillis(2019, 2), month.utcMillis[0]);
        assertEquals(SeriesNormaliser.monthStartMillis(2019, 2) + SeriesNormaliser.HALF_HOUR_MILLIS,
                month.utcMillis[1]);
        assertEquals(0, month.gapFilled);
        // Periods outside the month (the 23:00Z tail of the last auction) were dropped.
        assertEquals(SeriesNormaliser.monthEndMillis(2019, 2) - SeriesNormaliser.HALF_HOUR_MILLIS,
                month.utcMillis[month.utcMillis.length - 1]);
    }

    @Test
    public void smallGapsAreFilledWithThePreviousValueAndCounted() throws IOException {
        List<SemopxDayResultCsv.DayResult> days = fullMonthHourly(2019, 3, 50.0);
        days.remove(15); // one whole missing auction day = 48 periods (~3% of March)
        SeriesNormaliser.MonthSeries month = SeriesNormaliser.assembleMonth(2019, 3, days);
        assertNotNull(month);
        assertEquals(31 * 48, month.utcMillis.length);
        assertEquals(48, month.gapFilled);
        for (double c : month.centsPerKwh) assertEquals(5.0, c, DELTA);
    }

    @Test
    public void aMonthMissingMoreThanTenPercentIsNotCovered() throws IOException {
        List<SemopxDayResultCsv.DayResult> days = fullMonthHourly(2019, 4, 50.0);
        // Remove five days (~16% of April) — beyond the invent-nothing threshold.
        for (int i = 0; i < 5; i++) days.remove(10);
        assertNull(SeriesNormaliser.assembleMonth(2019, 4, days));
    }

    @Test
    public void leadingGapIsBackfilledFromTheFirstKnownPrice() throws IOException {
        List<SemopxDayResultCsv.DayResult> days = fullMonthHourly(2019, 5, 40.0);
        days.remove(0); // the previous-month auction that covers 00:00-23:00Z on the 1st
        SeriesNormaliser.MonthSeries month = SeriesNormaliser.assembleMonth(2019, 5, days);
        assertNotNull(month);
        assertEquals(4.0, month.centsPerKwh[0], DELTA);
        assertEquals(46, month.gapFilled); // 23 missing hours at the front = 46 half-hours
    }

    @Test
    public void republishedAuctionsOverwriteEarlierValues() throws IOException {
        List<SemopxDayResultCsv.DayResult> days = fullMonthHourly(2019, 6, 30.0);
        days.add(hourlyDay(LocalDate.of(2019, 6, 10), 99.0)); // republication, later in list
        SeriesNormaliser.MonthSeries month = SeriesNormaliser.assembleMonth(2019, 6, days);
        assertNotNull(month);
        // 2019-06-10 23:30Z falls inside the republished auction day.
        long t = LocalDate.of(2019, 6, 10).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli();
        int idx = (int) ((t - SeriesNormaliser.monthStartMillis(2019, 6))
                / SeriesNormaliser.HALF_HOUR_MILLIS);
        assertEquals(9.9, month.centsPerKwh[idx], DELTA);
    }

    @Test
    public void assembleRangeGridsAndClipsAPartialMonthBoundary() throws IOException {
        // A rolling window's boundary month: only days 15..30 (June) are in range.
        // Auctions of the 14th..30th are supplied (the 14th covers the 15th 00:00Z).
        List<SemopxDayResultCsv.DayResult> days = new ArrayList<>();
        for (LocalDate d = LocalDate.of(2023, 6, 14); !d.isAfter(LocalDate.of(2023, 6, 30));
             d = d.plusDays(1)) {
            days.add(hourlyDay(d, 70.0));
        }
        long start = LocalDate.of(2023, 6, 15).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long end = LocalDate.of(2023, 7, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        SeriesNormaliser.GridSeries grid = SeriesNormaliser.assembleRange(start, end, days);

        assertNotNull(grid);
        // 16 days (15th..30th inclusive) × 48 half-hours; clipped to the span exactly.
        assertEquals(16 * 48, grid.utcMillis.length);
        assertEquals(start, grid.utcMillis[0]);
        assertEquals(end - SeriesNormaliser.HALF_HOUR_MILLIS,
                grid.utcMillis[grid.utcMillis.length - 1]);
        assertEquals(7.0, grid.centsPerKwh[0], DELTA);
        assertEquals(0, grid.gapFilled);
    }
}
