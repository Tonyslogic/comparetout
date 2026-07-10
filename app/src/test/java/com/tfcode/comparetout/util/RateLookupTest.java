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

package com.tfcode.comparetout.util;

import static org.junit.Assert.assertEquals;

import com.tfcode.comparetout.model.IntHolder;
import com.tfcode.comparetout.model.priceplan.DayRate;
import com.tfcode.comparetout.model.priceplan.MinuteRateRange;
import com.tfcode.comparetout.model.priceplan.PricePlan;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Regression tests for the RateLookup constructor's date-range handling.
 * <p>
 * The constructor seeds each date range's day-of-week map from the floor entry.
 * It must COPY that map: reusing the reference lets a later range overwrite the
 * rates of every earlier range (all keys end up sharing one map), which mis-costs
 * seasonal plans depending on DAO row order and breaks per-day dynamic tariff
 * plans (365 single-day ranges) entirely.
 */
public class RateLookupTest {

    private static final double DELTA = 1e-9;
    private static final int WEDNESDAY = 3;
    private static final int SUNDAY = 0;

    private static DayRate flatRate(String start, String end, double cost, Integer... days) {
        DayRate dr = new DayRate();
        dr.setStartDate(start);
        dr.setEndDate(end);
        if (days.length > 0) {
            IntHolder holder = new IntHolder();
            holder.ints = new ArrayList<>(Arrays.asList(days));
            dr.setDays(holder);
        }
        MinuteRateRange mrr = new MinuteRateRange();
        mrr.add(0, 1440, cost);
        dr.setMinuteRateRange(mrr);
        return dr;
    }

    @Test
    public void seasonalRangesKeepTheirOwnRates() {
        List<DayRate> winterFirst = Arrays.asList(
                flatRate("01/01", "05/31", 10.0),
                flatRate("06/01", "12/31", 20.0));
        RateLookup lookup = new RateLookup(new PricePlan(), winterFirst);
        assertEquals(10.0, lookup.getRate(15, 600, WEDNESDAY, 0.1), DELTA);  // mid-January
        assertEquals(20.0, lookup.getRate(196, 600, WEDNESDAY, 0.1), DELTA); // mid-July
    }

    @Test
    public void seasonalRangesKeepTheirOwnRatesRegardlessOfRowOrder() {
        // The DAO does not guarantee date order; both orders must cost identically.
        List<DayRate> summerFirst = Arrays.asList(
                flatRate("06/01", "12/31", 20.0),
                flatRate("01/01", "05/31", 10.0));
        RateLookup lookup = new RateLookup(new PricePlan(), summerFirst);
        assertEquals(10.0, lookup.getRate(15, 600, WEDNESDAY, 0.1), DELTA);
        assertEquals(20.0, lookup.getRate(196, 600, WEDNESDAY, 0.1), DELTA);
    }

    @Test
    public void dayOfWeekSplitWithinOneRangeMerges() {
        // Same date range split across two DayRates (weekday/weekend) must merge
        // into one day-of-week map — the copy fix must not break this.
        List<DayRate> split = Arrays.asList(
                flatRate("01/01", "12/31", 8.0, 1, 2, 3, 4, 5),
                flatRate("01/01", "12/31", 12.0, 0, 6));
        RateLookup lookup = new RateLookup(new PricePlan(), split);
        assertEquals(8.0, lookup.getRate(100, 600, WEDNESDAY, 0.1), DELTA);
        assertEquals(12.0, lookup.getRate(100, 600, SUNDAY, 0.1), DELTA);
    }

    @Test
    public void threeRangesMiddleRangeIsNotOverwrittenByLast() {
        List<DayRate> ranges = Arrays.asList(
                flatRate("01/01", "03/31", 10.0),
                flatRate("04/01", "09/30", 20.0),
                flatRate("10/01", "12/31", 30.0));
        RateLookup lookup = new RateLookup(new PricePlan(), ranges);
        assertEquals(10.0, lookup.getRate(30, 600, WEDNESDAY, 0.1), DELTA);
        assertEquals(20.0, lookup.getRate(150, 600, WEDNESDAY, 0.1), DELTA);
        assertEquals(30.0, lookup.getRate(300, 600, WEDNESDAY, 0.1), DELTA);
    }

    @Test
    public void singleDayRangesEachCostTheirOwnDay() {
        // The dynamic-tariff shape: 365 one-day ranges, each with its own price.
        List<DayRate> perDay = new ArrayList<>();
        LocalDate day = LocalDate.of(2001, 1, 1);
        while (day.getYear() == 2001) {
            String monthDay = String.format("%02d/%02d", day.getMonthValue(), day.getDayOfMonth());
            perDay.add(flatRate(monthDay, monthDay, day.getDayOfYear()));
            day = day.plusDays(1);
        }
        RateLookup lookup = new RateLookup(new PricePlan(), perDay);
        assertEquals(1.0, lookup.getRate(1, 600, WEDNESDAY, 0.1), DELTA);
        assertEquals(100.0, lookup.getRate(100, 600, WEDNESDAY, 0.1), DELTA);
        assertEquals(365.0, lookup.getRate(365, 600, WEDNESDAY, 0.1), DELTA);
    }
}
