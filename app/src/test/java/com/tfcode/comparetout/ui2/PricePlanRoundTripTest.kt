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

import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.DynamicTerms
import com.tfcode.comparetout.model.priceplan.MinuteRateRange
import com.tfcode.comparetout.model.priceplan.PricePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The UI2 wizard's editor round trip (plans/region/import-plans.md §1.4 Defect A).
 *
 * The editor edits the BUY set only. Every other rate on the plan — today that
 * is the SELL/export set produced by the dynamic and Octopus generators — must
 * survive `toBuilder` → `toEntities` byte-for-byte, because the wizard offers no
 * way to author or even see it.
 *
 * Before the fix, `toBuilder` read every rate regardless of `rateType` and
 * `toEntities` emitted fresh `DayRate` objects (which default to `RATE_BUY`), so
 * opening an Agile plan and pressing Save converted its export prices into a
 * second set of import prices.
 */
class PricePlanRoundTripTest {

    private fun rate(
        start: String, end: String, cost: Double, rateType: Int, dbId: Long = 0L
    ) = DayRate().apply {
        startDate = start
        endDate = end
        minuteRateRange = MinuteRateRange().apply { add(0, 1440, cost) }
        this.rateType = rateType
        if (dbId > 0L) dayRateIndex = dbId
    }

    private fun plan(index: Long = 7L) = PricePlan().apply {
        pricePlanIndex = index
        supplier = "Octopus Energy"
        planName = "Agile"
    }

    @Test
    fun editorShowsOnlyBuyRates() {
        val rates = listOf(
            rate("01/01", "12/31", 25.0, DayRate.RATE_BUY, dbId = 1L),
            rate("01/01", "12/31", 15.0, DayRate.RATE_SELL, dbId = 2L)
        )
        val builder = plan().toBuilder(rates)

        assertEquals("only the BUY rate becomes an editor card", 1, builder.dayRates.size)
        assertEquals(25.0, builder.dayRates[0].bands[0].price, 1e-9)
        assertEquals("the SELL rate is carried, not shown", 1, builder.passthroughRates.size)
    }

    @Test
    fun sellRatesSurviveASaveUnchanged() {
        val rates = listOf(
            rate("01/01", "12/31", 25.0, DayRate.RATE_BUY, dbId = 1L),
            rate("01/01", "06/30", 15.0, DayRate.RATE_SELL, dbId = 2L),
            rate("07/01", "12/31", 9.5, DayRate.RATE_SELL, dbId = 3L)
        )

        val (savedPlan, savedRates) = plan().toBuilder(rates).toEntities()

        val buys = DayRate.buyRates(savedRates)
        val sells = DayRate.sellRates(savedRates)
        assertEquals("the BUY set is unchanged", 1, buys.size)
        assertEquals("both SELL rates come back as SELL", 2, sells.size)

        // Identity is preserved so PricePlanDAO's differential update treats them
        // as existing rows rather than deleting them as "removed".
        assertEquals(setOf(2L, 3L), sells.map { it.dayRateIndex }.toSet())
        assertTrue("carried rates stay bound to the plan",
            sells.all { it.pricePlanId == savedPlan.pricePlanIndex })

        // Prices survive, and the seasonal split is not flattened.
        assertEquals(15.0, sells.first { it.startDate == "01/01" }
            .minuteRateRange.lookup(600), 1e-9)
        assertEquals(9.5, sells.first { it.startDate == "07/01" }
            .minuteRateRange.lookup(600), 1e-9)
    }

    /** The saved plan must still validate — the pre-fix bug produced two
     *  overlapping BUY sets, which `validatePlan` rejects. */
    @Test
    fun aSavedPlanWithExportRatesStillValidates() {
        val rates = listOf(
            rate("01/01", "12/31", 25.0, DayRate.RATE_BUY, dbId = 1L),
            rate("01/01", "12/31", 15.0, DayRate.RATE_SELL, dbId = 2L)
        )
        val (savedPlan, savedRates) = plan().toBuilder(rates).toEntities()
        assertEquals(PricePlan.VALID_PLAN, savedPlan.validatePlan(savedRates))
    }

    /** Dynamic plans route their whole rate set through saveDynamic's keepRates.
     *  Carrying them here as well would duplicate every generated row on Save. */
    @Test
    fun dynamicPlansCarryNothingThroughTheBuilder() {
        val dynamic = plan().apply {
            dynamicTerms = DynamicTerms().apply {
                market = "GB-AGILE-C"; multiplier = 1.0; adder = 0.0
            }
        }
        val rates = listOf(
            rate("01/01", "01/01", 25.0, DayRate.RATE_BUY, dbId = 1L),
            rate("01/01", "01/01", 15.0, DayRate.RATE_SELL, dbId = 2L)
        )
        val builder = dynamic.toBuilder(rates)

        assertTrue("no editor cards for a dynamic plan", builder.dayRates.isEmpty())
        assertTrue("no passthrough either", builder.passthroughRates.isEmpty())
        assertTrue("so a Save emits no rates at all", builder.toEntities().second.isEmpty())
    }

    /** A plan with no BUY rates still opens with one blank editable card, so the
     *  wizard is never empty. */
    @Test
    fun aPlanWithOnlySellRatesStillOffersABlankCard() {
        val rates = listOf(rate("01/01", "12/31", 15.0, DayRate.RATE_SELL, dbId = 2L))
        val builder = plan().toBuilder(rates)
        assertEquals(1, builder.dayRates.size)
        assertEquals(1, builder.passthroughRates.size)
    }
}
