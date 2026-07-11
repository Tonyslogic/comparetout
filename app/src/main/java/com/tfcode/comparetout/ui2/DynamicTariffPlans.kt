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

import com.tfcode.comparetout.dynamic.HistoricalRateSource
import com.tfcode.comparetout.dynamic.RateSeries
import com.tfcode.comparetout.model.IntHolder
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.DoubleHolder
import com.tfcode.comparetout.model.priceplan.DynamicTerms
import com.tfcode.comparetout.model.priceplan.MinuteRateRange
import com.tfcode.comparetout.model.priceplan.PricePlan
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Materialises a dynamic tariff: supplier terms + one year of fetched
 * wholesale prices → a costable [PricePlan] with 365 single-day BUY DayRates
 * (`startDate == endDate == MM/DD`, all days of week, per-half-hour ranges),
 * plus SELL DayRates when the terms carry a feed transform. The mirror of
 * [OctopusTariffPlans] for wholesale-tracking offers.
 *
 * Conventions (plan §2c): leap 02/29 is dropped (the sim year is 2001);
 * mapping a real year onto the 2001 calendar by MM/DD misaligns weekdays —
 * accepted, same convention as the CDS weather profile, noted in the plan
 * reference. Series must be complete ([RateSeries.isComplete]) — missing
 * months are reported, never zero-filled.
 *
 * All methods are synchronous — callers are workers or Dispatchers.IO.
 */
@Singleton
class DynamicTariffPlans @Inject constructor(
    private val repository: ToutcRepository
) {

    sealed class Result {
        data class Generated(val planName: String, val gapFilled: Int) : Result()
        data class Incomplete(val missingMonths: List<Int>) : Result()
        data class Failed(val error: Throwable) : Result()
    }

    /**
     * Fetches [year] from [source] and (re)inserts the materialised plan.
     * [plan] carries the identity (supplier/planName), scalar fields and the
     * terms; it may be a brand-new plan from the generate sheet or an imported
     * terms-only pending plan. Insertion clobbers any same-named plan, so
     * regeneration (new year, edited terms) replaces cleanly, and the existing
     * plan-changed invalidation seam re-costs every scenario.
     */
    fun materialiseBlocking(source: HistoricalRateSource, plan: PricePlan, year: Int,
                            startMonth: Int = 1): Result {
        val terms = plan.dynamicTerms
            ?: return Result.Failed(IllegalArgumentException("plan has no dynamic terms"))
        if (!terms.isComplete)
            return Result.Failed(IllegalArgumentException("dynamic terms are incomplete"))
        return try {
            // A 12-month window starting (year, startMonth) — each month appears
            // once, so it tiles the sim's 2001 calendar exactly like a full year.
            val series = source.fetchWindow(year, startMonth, 12)
            if (!series.isComplete) return Result.Incomplete(series.missingMonths)

            terms.year = year
            terms.periodStartMonth = startMonth
            terms.sourceRef = series.sourceRef
            val rates = ArrayList(buildBuyDayRates(series, terms))
            if (terms.feedMultiplier != null || terms.feedAdder != null) {
                rates.addAll(buildSellDayRates(series, terms))
            }
            plan.pricePlanIndex = 0
            plan.isDeemedExport = false
            plan.reference = referenceFor(plan, series, terms)
            repository.insert(plan, rates, true)
            Result.Generated(plan.planName, series.gapFilledCount)
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }

    /** A human-readable terms digest for default plan names: "×1.00 +4.50c, cap 45c". */
    fun termsDigest(terms: DynamicTerms): String {
        val sb = StringBuilder()
        sb.append("×").append(String.format(Locale.ROOT, "%.2f", terms.multiplier ?: 1.0))
        sb.append(" +").append(String.format(Locale.ROOT, "%.2f", terms.adder ?: 0.0)).append("c")
        terms.cap?.let { sb.append(", cap ").append(String.format(Locale.ROOT, "%.0f", it)).append("c") }
        terms.floor?.let { sb.append(", floor ").append(String.format(Locale.ROOT, "%.0f", it)).append("c") }
        return sb.toString()
    }

    private fun referenceFor(plan: PricePlan, series: RateSeries, terms: DynamicTerms): String {
        val feedNote = if (terms.feedMultiplier != null || terms.feedAdder != null)
            "export = wholesale × ${terms.feedMultiplier ?: 1.0} + ${terms.feedAdder ?: 0.0}c"
        else "export = scalar feed ${plan.feed}c"
        val startMonth = terms.periodStartMonth ?: 1
        val window = if (startMonth == 1) "${series.year}"
            else "${series.year}-%02d..%d-%02d".format(
                startMonth, series.year + 1, startMonth - 1)
        return "Generated dynamic tariff: unit price = wholesale × ${terms.multiplier} + " +
                "${terms.adder}c" +
                (terms.cap?.let { ", capped at ${it}c" } ?: "") +
                (terms.floor?.let { ", floored at ${it}c" } ?: "") +
                "; $feedNote. Wholesale: ${series.marketId} $window " +
                "(${series.sourceRef}). ${series.gapFilledCount} half-hours gap-filled. " +
                "Weekdays follow the calendar dates of the window, not the sim year's — " +
                "same convention as the weather profile."
    }

    companion object {

        /** 365 single-day BUY DayRates (02/29 dropped), prices via the terms transform. */
        fun buildBuyDayRates(series: RateSeries, terms: DynamicTerms): List<DayRate> =
            buildDayRates(series, DayRate.RATE_BUY) { terms.unitPrice(it) }

        /**
         * SELL DayRates from the same wholesale series. The feed transform is
         * deliberately unclamped (cap/floor are retail import bounds) — a
         * negative wholesale half-hour prices export at a loss, which is what
         * a pass-through export tariff does.
         */
        fun buildSellDayRates(series: RateSeries, terms: DynamicTerms): List<DayRate> {
            val multiplier = terms.feedMultiplier ?: 1.0
            val adder = terms.feedAdder ?: 0.0
            return buildDayRates(series, DayRate.RATE_SELL) { it * multiplier + adder }
        }

        private fun buildDayRates(
            series: RateSeries,
            rateType: Int,
            transform: (Double) -> Double
        ): List<DayRate> {
            // Bucket the half-hourly UTC series by calendar date.
            val byDate = LinkedHashMap<LocalDate, MutableList<Pair<Int, Double>>>()
            for (entry in series.entries) {
                val zoned = Instant.ofEpochMilli(entry.utcMillis).atZone(ZoneOffset.UTC)
                val date = zoned.toLocalDate()
                if (date.monthValue == 2 && date.dayOfMonth == 29) continue // sim year is 2001
                byDate.getOrPut(date) { mutableListOf() }
                    .add(zoned.hour * 60 + zoned.minute to transform(entry.importCentsPerKwh))
            }
            val rates = ArrayList<DayRate>(byDate.size)
            for ((date, slots) in byDate) {
                slots.sortBy { it.first }
                val dayRate = DayRate()
                val monthDay = String.format(Locale.ROOT, "%02d/%02d", date.monthValue, date.dayOfMonth)
                dayRate.startDate = monthDay
                dayRate.endDate = monthDay
                dayRate.days = IntHolder() // all days — the range is a single date anyway
                dayRate.rateType = rateType
                dayRate.minuteRateRange = rangesFor(slots)
                // Legacy hours snapshot (Octopus generator precedent) so pre-minute
                // consumers of DayRate.hours see sensible values.
                val hours = DoubleHolder()
                for (h in 0..24) hours.doubles[h] = dayRate.minuteRateRange.lookup(minOf(h * 60, 1439))
                dayRate.hours = hours
                rates.add(dayRate)
            }
            return rates
        }

        /** Half-hour slots → minute ranges, merging adjacent equal prices, tiling 0..1440. */
        private fun rangesFor(slots: List<Pair<Int, Double>>): MinuteRateRange {
            val mrr = MinuteRateRange()
            if (slots.isEmpty()) return mrr
            var begin = 0
            var price = slots.first().second
            for (i in 1 until slots.size) {
                val (minute, cost) = slots[i]
                if (cost != price) {
                    mrr.add(begin, minute, price)
                    begin = minute
                    price = cost
                }
            }
            mrr.add(begin, 1440, price)
            return mrr
        }
    }
}

/** Hilt EntryPoint so non-Hilt callers (the materialise worker) can resolve [DynamicTariffPlans]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DynamicTariffPlansEntryPoint {
    fun dynamicTariffPlans(): DynamicTariffPlans
}
