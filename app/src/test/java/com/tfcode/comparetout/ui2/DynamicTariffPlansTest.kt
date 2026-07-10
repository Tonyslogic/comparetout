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

package com.tfcode.comparetout.ui2

import com.tfcode.comparetout.dynamic.RateSeries
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.DynamicTerms
import com.tfcode.comparetout.model.priceplan.PricePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The pure materialisation logic: series + terms → 365 single-day DayRates.
 * Repository insertion is exercised end-to-end in Phase-3 checkpoints; here
 * the generated shape must pass [PricePlan.validatePlan] and honour the
 * terms transform.
 */
class DynamicTariffPlansTest {

    private val delta = 1e-9

    /** A full-year half-hourly series; price per slot from [priceOf] (date, slotIndex). */
    private fun yearSeries(year: Int, priceOf: (LocalDate, Int) -> Double): RateSeries {
        val entries = ArrayList<RateSeries.Entry>()
        var date = LocalDate.of(year, 1, 1)
        while (date.year == year) {
            val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            for (slot in 0 until 48) {
                entries.add(RateSeries.Entry(dayStart + slot * 1_800_000L, priceOf(date, slot)))
            }
            date = date.plusDays(1)
        }
        return RateSeries("ISEM-DAM", year, entries, emptyList(), 0, "test series")
    }

    private fun terms(): DynamicTerms = DynamicTerms().apply {
        market = "ISEM-DAM"
        multiplier = 1.0
        adder = 4.5
    }

    @Test
    fun leapDayIsDroppedAndTheYearValidates() {
        val series = yearSeries(2024) { _, _ -> 10.0 } // 366 days in the source year
        val rates = DynamicTariffPlans.buildBuyDayRates(series, terms())
        assertEquals(365, rates.size)
        assertTrue(rates.none { it.startDate == "02/29" })
        val plan = PricePlan()
        plan.dynamicTerms = terms()
        assertEquals(PricePlan.VALID_PLAN, plan.validatePlan(rates))
        // Single-day ranges: startDate == endDate, all days-of-week, BUY.
        assertTrue(rates.all { it.startDate == it.endDate })
        assertTrue(rates.all { it.rateType == DayRate.RATE_BUY })
    }

    @Test
    fun theTermsTransformAndCapApply() {
        val withCap = terms().apply { cap = 20.0 }
        // 10 c/kWh wholesale in slot 0, 40 c/kWh in the evening slots.
        val series = yearSeries(2025) { _, slot -> if (slot >= 34) 40.0 else 10.0 }
        val rates = DynamicTariffPlans.buildBuyDayRates(series, withCap)
        val jan1 = rates.first { it.startDate == "01/01" }
        assertEquals(14.5, jan1.minuteRateRange.lookup(0), delta)          // 10×1 + 4.5
        assertEquals(20.0, jan1.minuteRateRange.lookup(17 * 60 + 30), delta) // capped
        // Legacy hours snapshot mirrors the minute ranges.
        assertEquals(14.5, jan1.hours.doubles[0], delta)
    }

    @Test
    fun adjacentEqualPricesMergeIntoOneRange() {
        val series = yearSeries(2025) { _, slot -> if (slot < 24) 10.0 else 20.0 }
        val rates = DynamicTariffPlans.buildBuyDayRates(series, terms())
        val ranges = rates.first().minuteRateRange.rates
        assertEquals(2, ranges.size)
        assertEquals(0, ranges[0].begin)
        assertEquals(720, ranges[0].end)
        assertEquals(1440, ranges[1].end)
    }

    @Test
    fun sellRatesUseTheFeedTransformUnclamped() {
        val feedTerms = terms().apply {
            cap = 20.0 // import cap must NOT clamp export prices
            feedMultiplier = 0.9
            feedAdder = 0.0
        }
        val series = yearSeries(2025) { _, _ -> 40.0 }
        val sells = DynamicTariffPlans.buildSellDayRates(series, feedTerms)
        assertEquals(365, sells.size)
        assertTrue(sells.all { it.rateType == DayRate.RATE_SELL })
        assertEquals(36.0, sells.first().minuteRateRange.lookup(0), delta)
        // And a buy+sell dynamic plan still validates.
        val plan = PricePlan()
        plan.dynamicTerms = feedTerms
        val all = ArrayList(DynamicTariffPlans.buildBuyDayRates(series, feedTerms))
        all.addAll(sells)
        assertEquals(PricePlan.VALID_PLAN, plan.validatePlan(all))
    }
}
