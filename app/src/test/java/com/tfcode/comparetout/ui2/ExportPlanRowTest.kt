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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list row's derived state (plans/region/import-plans.md §4.2, §4.4).
 *
 * Two things the UI leans on: the average rate that ranks plans for the
 * cheapest-pair seed, and the live compatibility check that decides which pairs
 * are tickable.
 */
class ExportPlanRowTest {

    private fun band(begin: Int, end: Int, price: Double) = Triple(begin, end, price)

    private fun rate(rateType: Int, vararg bands: Triple<Int, Int, Double>) = DayRate().apply {
        startDate = "01/01"
        endDate = "12/31"
        minuteRateRange = MinuteRateRange().apply {
            bands.forEach { (b, e, p) -> add(b, e, p) }
        }
        this.rateType = rateType
    }

    private fun plan(direction: Int = PricePlan.DIRECTION_IMPORT) = PricePlan().apply {
        supplier = "TestCo"
        planName = "Plan"
        this.direction = direction
    }

    private fun row(
        id: Long, supplier: String, name: String,
        direction: Int = PricePlan.DIRECTION_IMPORT,
        tags: List<String> = emptyList(),
        avg: Double? = 20.0,
        active: Boolean = true
    ) = PricePlanListRow(
        planId = id, supplier = supplier, planName = name, reference = "",
        standingCharges = 0.0, feed = 0.0, signUpBonus = 0.0, rateCount = 1,
        deemedExport = false, lastUpdate = "", active = active,
        direction = direction, compatibleWith = tags, averageRate = avg
    )

    // ── average rate ────────────────────────────────────────────────────────

    @Test
    fun averageIsMinuteWeightedNotBandCounted() {
        // 8h at 8c + 16h at 32c → (8·480 + 32·960) / 1440 = 24.0, not (8+32)/2.
        val avg = averageRateOf(plan(),
            listOf(rate(DayRate.RATE_BUY, band(0, 480, 8.0), band(480, 1440, 32.0))))
        assertEquals(24.0, avg!!, 1e-9)
    }

    /** An export plan averages its SELL rates; its BUY set is empty by design. */
    @Test
    fun anExportPlanAveragesItsOwnDirection() {
        val exp = plan(PricePlan.DIRECTION_EXPORT)
        val rates = listOf(rate(DayRate.RATE_SELL, band(0, 1440, 15.0)))
        assertEquals(15.0, averageRateOf(exp, rates)!!, 1e-9)
    }

    /** An import plan carrying generated SELL rates averages only the BUY side —
     *  mixing them would misrank the plan. */
    @Test
    fun mixedRateSetsDoNotContaminateTheAverage() {
        val rates = listOf(
            rate(DayRate.RATE_BUY, band(0, 1440, 30.0)),
            rate(DayRate.RATE_SELL, band(0, 1440, 5.0)))
        assertEquals(30.0, averageRateOf(plan(), rates)!!, 1e-9)
    }

    /** Null, not zero: a pending plan must be excluded from ranking rather than
     *  treated as free and picked as "cheapest". */
    @Test
    fun aPlanWithNoRatesInItsDirectionHasNoAverage() {
        val pending = plan().apply {
            dynamicTerms = DynamicTerms().apply {
                market = "ISEM-DAM"; multiplier = 1.0; adder = 4.5
            }
        }
        assertNull(averageRateOf(pending, emptyList()))
        assertNull("SELL rates do not give an import plan an average",
            averageRateOf(pending, listOf(rate(DayRate.RATE_SELL, band(0, 1440, 15.0)))))
    }

    // ── pairing compatibility ───────────────────────────────────────────────

    @Test
    fun anUntaggedExportRowPairsWithAnything() {
        val exp = row(2, "AnyCo", "Open", PricePlan.DIRECTION_EXPORT)
        assertTrue(exp.isOpenMarket)
        assertTrue(exp.pairsWith(row(1, "Octopus Energy", "Flexible")))
    }

    @Test
    fun aTaggedExportRowPairsOnlyWithItsSupplier() {
        val exp = row(2, "Octopus Energy", "Outgoing Fixed",
            PricePlan.DIRECTION_EXPORT, listOf("Octopus Energy:*"))
        assertTrue(exp.pairsWith(row(1, "Octopus Energy", "Flexible")))
        assertFalse(exp.pairsWith(row(3, "EDF", "Standard")))
    }

    /** Live matching: the check runs against the import row's current name, so a
     *  rename is picked up without touching the stored tags. */
    @Test
    fun renamingAnImportPlanIsPickedUpImmediately() {
        val exp = row(2, "Octopus Energy", "Outgoing",
            PricePlan.DIRECTION_EXPORT, listOf("Octopus Energy:Flexible"))
        assertTrue(exp.pairsWith(row(1, "Octopus Energy", "Flexible")))
        assertFalse("the renamed plan no longer matches the specific tag",
            exp.pairsWith(row(1, "Octopus Energy", "Flexible v2")))
    }

    @Test
    fun pairingIsExportToImportOnly() {
        val imp = row(1, "Octopus Energy", "Flexible")
        assertFalse("an import row is never the export side", imp.pairsWith(imp))
        val exp = row(2, "AnyCo", "Open", PricePlan.DIRECTION_EXPORT)
        assertFalse("two export rows do not pair", exp.pairsWith(
            row(3, "Other", "Export", PricePlan.DIRECTION_EXPORT)))
    }

    // ── what the seed would pick ────────────────────────────────────────────

    /**
     * The seed picks the cheapest import (lowest average buy) and the best export
     * (HIGHEST average sell — export is income, so the comparison inverts).
     */
    @Test
    fun seedRankingPrefersCheapestImportAndHighestExport() {
        val imports = listOf(
            row(1, "A", "Pricey", avg = 30.0),
            row(2, "B", "Cheap", avg = 18.0))
        val exports = listOf(
            row(10, "X", "Low", PricePlan.DIRECTION_EXPORT, avg = 4.0),
            row(11, "Y", "High", PricePlan.DIRECTION_EXPORT, avg = 21.0))

        assertEquals(2L, imports.minByOrNull { it.averageRate!! }!!.planId)
        assertEquals(11L, exports.maxByOrNull { it.averageRate!! }!!.planId)
    }

    @Test
    fun rowsWithoutAnAverageAreNotRankable() {
        val rows = listOf(
            row(1, "A", "Pending", avg = null),
            row(2, "B", "Priced", avg = 18.0))
        val rankable = rows.filter { it.averageRate != null }
        assertEquals(1, rankable.size)
        assertEquals(2L, rankable[0].planId)
    }
}
