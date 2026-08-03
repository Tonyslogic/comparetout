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

package com.tfcode.comparetout.util

import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.DynamicTerms
import com.tfcode.comparetout.model.priceplan.MinuteRateRange
import com.tfcode.comparetout.model.priceplan.PricePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The buy/sell partition every costing path must apply
 * (plans/region/import-plans.md §1.4 Defect B).
 *
 * The Compare tab used to build one RateLookup from a plan's whole DayRate list
 * and price export at the scalar feed, so a plan with time-varying export rates
 * costed differently on Compare than on the dashboard.
 */
class PlanPricerTest {

    private val delta = 1e-9

    /** A rate covering the whole year, priced [cost] all day. */
    private fun flat(cost: Double, rateType: Int) = DayRate().apply {
        startDate = "01/01"
        endDate = "12/31"
        minuteRateRange = MinuteRateRange().apply { add(0, 1440, cost) }
        this.rateType = rateType
    }

    /** A rate covering the whole year, [night] before 08:00 and [day] after. */
    private fun tou(night: Double, day: Double, rateType: Int) = DayRate().apply {
        startDate = "01/01"
        endDate = "12/31"
        minuteRateRange = MinuteRateRange().apply {
            add(0, 480, night)
            add(480, 1440, day)
        }
        this.rateType = rateType
    }

    private fun plan(feed: Double = 0.0) = PricePlan().apply {
        supplier = "Test"
        planName = "Plan"
        this.feed = feed
    }

    @Test
    fun exportRatesDoNotLeakIntoTheImportLookup() {
        val p = plan()
        val pricer = PlanPricer(p, listOf(
            flat(25.0, DayRate.RATE_BUY),
            flat(15.0, DayRate.RATE_SELL)
        ))
        // Both rates cover the whole year and every weekday. Built from the mixed
        // list, whichever DayRate landed last would answer import queries.
        assertEquals(25.0, pricer.buyRate(100, 600, 3, 1.0), delta)
        assertEquals(15.0, pricer.sellRate(100, 600, 3, 1.0), delta)
    }

    @Test
    fun exportIsPricedPerSlotWhenSellRatesExist() {
        val pricer = PlanPricer(plan(feed = 21.0), listOf(
            flat(25.0, DayRate.RATE_BUY),
            tou(night = 5.0, day = 15.0, rateType = DayRate.RATE_SELL)
        ))
        assertTrue(pricer.hasExportRates)
        assertEquals("night export slot", 5.0, pricer.sellRate(100, 120, 3, 1.0), delta)
        assertEquals("day export slot", 15.0, pricer.sellRate(100, 600, 3, 1.0), delta)
        // The scalar feed must NOT win when per-slot rates are present.
        assertEquals(21.0, plan(feed = 21.0).feed, delta)
    }

    @Test
    fun exportFallsBackToTheScalarFeedWithoutSellRates() {
        val pricer = PlanPricer(plan(feed = 21.0), listOf(flat(25.0, DayRate.RATE_BUY)))
        assertFalse(pricer.hasExportRates)
        assertEquals(21.0, pricer.sellRate(100, 120, 3, 1.0), delta)
        assertEquals(21.0, pricer.sellRate(200, 900, 5, 1.0), delta)
    }

    @Test
    fun aPendingDynamicPlanIsFlaggedRatherThanPricedAtZero() {
        val p = plan().apply {
            dynamicTerms = DynamicTerms().apply {
                market = "ISEM-DAM"; multiplier = 1.0; adder = 4.5
            }
        }
        // Terms present, no BUY rates yet — prices have not been downloaded.
        val pricer = PlanPricer(p, listOf(flat(15.0, DayRate.RATE_SELL)))
        assertTrue("caller must skip this plan", pricer.isPending)
        // Demonstrates why: an unguarded lookup prices every import row at zero,
        // which would rank the plan cheapest.
        assertEquals(0.0, pricer.buyRate(100, 600, 3, 1.0), delta)
    }

    @Test
    fun aMaterialisedPlanIsNotPending() {
        val p = plan().apply {
            dynamicTerms = DynamicTerms().apply {
                market = "ISEM-DAM"; multiplier = 1.0; adder = 4.5
            }
        }
        val pricer = PlanPricer(p, listOf(
            flat(25.0, DayRate.RATE_BUY),
            flat(15.0, DayRate.RATE_SELL)
        ))
        assertFalse(pricer.isPending)
        assertEquals(25.0, pricer.buyRate(100, 600, 3, 1.0), delta)
    }

    /** A plain non-dynamic plan with no rates at all is not "pending" — that
     *  concept only applies to dynamic plans awaiting materialisation. */
    @Test
    fun anEmptyNonDynamicPlanIsNotPending() {
        assertFalse(PlanPricer(plan(), emptyList()).isPending)
    }
}
