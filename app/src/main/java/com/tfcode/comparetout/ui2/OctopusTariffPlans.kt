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

import android.content.Context
import com.google.gson.Gson
import com.tfcode.comparetout.dynamic.OctopusAgileRateSource
import com.tfcode.comparetout.importers.octopus.OctopusException
import com.tfcode.comparetout.importers.octopus.OctopusRestClient
import com.tfcode.comparetout.importers.octopus.OctopusSystem
import com.tfcode.comparetout.importers.octopus.responses.ProductDetailResponse
import com.tfcode.comparetout.importers.octopus.responses.RatesResponse
import com.tfcode.comparetout.model.IntHolder
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.priceplan.DynamicTermsJson
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.DoubleHolder
import com.tfcode.comparetout.model.priceplan.MinuteRateRange
import com.tfcode.comparetout.model.priceplan.PricePlan
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates real supplier plans from the public Octopus Energy tariff API for
 * one GSP region ("A".."P"): every import product open for sign-up, excluding
 * Tracker (daily repricing without a published half-hourly series) and
 * prepay/business/restricted products. Agile products become terms-only
 * pending DYNAMIC plans (Phase 8): the DynamicTariffWorker fetches the
 * backtest year's half-hourly prices and materialises them in the background.
 *
 * The export rate on every generated plan is the region's Outgoing Fixed rate
 * (a labelled assumption — see the plan's reference note). Standing charges
 * convert from pence/day to the app's pounds-per-year convention; unit rates
 * stay pence/kWh (the minor-units convention costing expects).
 *
 * Time-of-use windows (Go, Cosy, ...) are derived from yesterday's
 * standard-unit-rates series interpreted in Europe/London — the wall-clock
 * the tariffs are defined in. Dual-register (Economy-7) products carry no
 * window times on the API and are skipped.
 *
 * All methods are synchronous — callers are workers (OctopusCatchUpWorker via
 * the Hilt EntryPoint) or viewmodels on Dispatchers.IO.
 */
@Singleton
class OctopusTariffPlans @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ToutcRepository,
    private val favouriteStore: FavouritePlanStore
) {

    sealed class Result {
        data class Loaded(
            val added: Int, val existing: Int, val skipped: Int,
            /** Agile plans handed to DynamicTariffWorker (materialise in the background). */
            val queued: Int = 0
        ) : Result()
        data object NoRegion : Result()
        data class Failed(val error: Throwable) : Result()
    }

    private enum class AgileOutcome { QUEUED, EXISTING, SKIPPED }

    /** Resolves a UK postcode to its region letter ("C"), or null if unknown. */
    fun resolveRegionBlocking(postcode: String): String? {
        val group = OctopusRestClient().getGridSupplyPointGroup(postcode) ?: return null
        return group.removePrefix("_").takeIf { it.length == 1 && it[0] in 'A'..'P' }
    }

    /**
     * Fetches and inserts plans for every eligible open product in [region].
     * When [currentTariffCode] is supplied (the importer flow), its product is
     * generated even if closed for sign-up and its plan becomes the favourite
     * — but only when no favourite is already set.
     */
    fun generateForRegionBlocking(region: String, currentTariffCode: String? = null): Result {
        return try {
            val client = OctopusRestClient()
            val gsp = "_${region.uppercase()}"
            val products = client.products
            val feedRate = outgoingFixedRate(client, products, gsp)

            val existingNames: Set<String> =
                repository.allPricePlansNow?.mapNotNull { it.planName }?.toSet().orEmpty()

            var added = 0
            var existing = 0
            var skipped = 0
            var queued = 0

            // Agile is no longer excluded: its products become terms-only
            // pending dynamic plans, materialised in the background (Phase 8).
            val openImports = products.filter {
                it.direction == "IMPORT" && it.availableTo == null &&
                        !it.isTracker && !it.isPrepay && !it.isBusiness && !it.isRestricted
            }
            val codes = openImports.map { it.code }.toMutableList()

            // The user's current product may be closed for sign-up — include it.
            val currentProductCode = currentTariffCode
                ?.let { OctopusSystem.productCodeFromTariffCode(it) }
            if (currentProductCode != null && currentProductCode !in codes)
                codes.add(currentProductCode)

            var currentPlanName: String? = null
            for (code in codes) {
                // Polite spacing between public-API bursts.
                Thread.sleep(POLITE_DELAY_MS)
                if (code.startsWith("AGILE")) {
                    val outcome = try {
                        queueAgilePlan(client, code, gsp, region.uppercase(), feedRate,
                            existingNames) { name ->
                            if (code == currentProductCode) currentPlanName = name
                        }
                    } catch (e: OctopusException) {
                        AgileOutcome.SKIPPED
                    }
                    when (outcome) {
                        AgileOutcome.QUEUED -> queued++
                        AgileOutcome.EXISTING -> existing++
                        AgileOutcome.SKIPPED -> skipped++
                    }
                    continue
                }
                val plan = try {
                    buildPlan(client, code, gsp, feedRate)
                } catch (e: OctopusException) {
                    skipped++; continue
                }
                if (plan == null) { skipped++; continue }
                if (code == currentProductCode) currentPlanName = plan.first.planName
                if (plan.first.planName in existingNames) {
                    existing++
                } else {
                    repository.insert(plan.first, ArrayList(plan.second), false)
                    added++
                }
            }

            if (currentPlanName != null) favouriteIfUnset(currentPlanName)

            Result.Loaded(added, existing, skipped, queued)
        } catch (t: Throwable) {
            Result.Failed(t)
        }
    }

    /**
     * Hands an Agile product to [DynamicTariffWorker] as a terms-only pending
     * dynamic plan: market `GB-AGILE-<region>`, terms ×1 +0 (Agile's published
     * rates are already retail pence/kWh), backtest year = the last complete
     * calendar year. The worker fetches the year's half-hourly prices and
     * inserts the materialised plan; the existing-name guard keeps the
     * catch-up worker's repeat calls idempotent (no re-clobbering a plan the
     * user may have regenerated for a different year).
     */
    private fun queueAgilePlan(
        client: OctopusRestClient,
        productCode: String,
        gsp: String,
        region: String,
        feedRate: Double,
        existingNames: Set<String>,
        onCurrentPlan: (String) -> Unit
    ): AgileOutcome {
        val detail = client.getProductDetail(productCode)
        val regional = detail.singleRegisterElectricityTariffs?.get(gsp) ?: return AgileOutcome.SKIPPED
        val tariff = regional["direct_debit_monthly"] ?: regional.values.firstOrNull()
            ?: return AgileOutcome.SKIPPED
        val tariffCode = tariff.code ?: return AgileOutcome.SKIPPED
        val planName = detail.displayName ?: productCode
        onCurrentPlan(planName)
        if (planName in existingNames) return AgileOutcome.EXISTING

        val standingPencePerDay = firstRate(
            client.getStandingCharges(productCode, tariffCode, yesterdayFrom(), yesterdayTo())
        ) ?: 0.0
        val year = LocalDate.now().year - 1

        val ppj = PricePlanJsonFile()
        ppj.supplier = SUPPLIER
        ppj.plan = planName
        ppj.feed = feedRate
        ppj.standingCharges = standingPencePerDay * 365.0 / 100.0   // pence/day → £/year
        ppj.active = true
        ppj.reference = "Auto-generated from the Octopus API ($tariffCode). " +
                "Agile: half-hourly prices, backtested against $year. " +
                "Export rate assumes Outgoing Fixed."
        val terms = DynamicTermsJson()
        terms.market = OctopusAgileRateSource.MARKET_PREFIX + region
        terms.multiplier = 1.0
        terms.adder = 0.0
        terms.year = year
        ppj.dynamic = terms
        DynamicTariffWorker.enqueue(context, Gson().toJson(ppj), planName, year)
        return AgileOutcome.QUEUED
    }

    /**
     * One plan (with day rates) for [productCode] in [gsp], or null when the
     * product has no single-register tariff there or its rate pattern is not
     * representable as a repeating day (dynamic safety net).
     */
    private fun buildPlan(
        client: OctopusRestClient,
        productCode: String,
        gsp: String,
        feedRate: Double
    ): Pair<PricePlan, List<DayRate>>? {
        val detail = client.getProductDetail(productCode)
        if (detail.isTracker || detail.isPrepay || detail.isBusiness) return null
        val regional = detail.singleRegisterElectricityTariffs?.get(gsp) ?: return null
        val tariff = regional["direct_debit_monthly"] ?: regional.values.firstOrNull() ?: return null
        val tariffCode = tariff.code ?: return null

        val segments = dailySegments(client, productCode, tariffCode) ?: return null
        val standingPencePerDay = firstRate(
            client.getStandingCharges(productCode, tariffCode, yesterdayFrom(), yesterdayTo())
        ) ?: 0.0

        val plan = PricePlan()
        plan.supplier = SUPPLIER
        plan.planName = detail.displayName ?: productCode
        plan.standingCharges = standingPencePerDay * 365.0 / 100.0   // pence/day → £/year
        plan.feed = feedRate
        plan.isDeemedExport = false
        plan.reference = "Auto-generated from the Octopus API ($tariffCode). " +
                "Export rate assumes Outgoing Fixed."
        plan.isActive = true

        val dayRate = DayRate()
        dayRate.days = IntHolder()               // all days
        val mrr = MinuteRateRange()
        segments.forEach { (begin, end, cost) -> mrr.add(begin, end, cost) }
        dayRate.minuteRateRange = mrr
        val hours = DoubleHolder()
        for (h in 0..24) hours.doubles[h] = mrr.lookup(minOf(h * 60, 1439))
        dayRate.hours = hours

        return plan to listOf(dayRate)
    }

    /**
     * Yesterday's unit-rate entries mapped onto minutes-of-day in Europe/London.
     * Returns null when the pattern is dynamic (too many distinct windows) or
     * does not tile the full day.
     */
    private fun dailySegments(
        client: OctopusRestClient,
        productCode: String,
        tariffCode: String
    ): List<Triple<Int, Int, Double>>? {
        val zone = LONDON
        val dayStart = LocalDate.now(zone).minusDays(1).atStartOfDay(zone)
        val dayEnd = dayStart.plusDays(1)

        val rates = client.getStandardUnitRates(
            productCode, tariffCode,
            dayStart.toInstant().toString(), dayEnd.toInstant().toString()
        ).filter { it.paymentMethod == null || it.paymentMethod.equals("DIRECT_DEBIT", true) }
        if (rates.isEmpty()) return null
        // A repeating tariff has a handful of windows per day; anything more is
        // effectively dynamic and cannot live in a DayRate.
        if (rates.size > MAX_DAILY_WINDOWS) return null

        val segments = mutableListOf<Triple<Int, Int, Double>>()
        for (rate in rates) {
            val from = rate.validFrom?.let { parseInstant(it) } ?: Instant.MIN
            val to = rate.validTo?.let { parseInstant(it) } ?: Instant.MAX
            val clippedFrom = maxOf(from, dayStart.toInstant())
            val clippedTo = minOf(to, dayEnd.toInstant())
            if (!clippedFrom.isBefore(clippedTo)) continue
            val beginMin = minuteOfDay(clippedFrom, zone, dayStart.toLocalDate())
            val endMin =
                if (clippedTo == dayEnd.toInstant()) 1440
                else minuteOfDay(clippedTo, zone, dayStart.toLocalDate())
            if (beginMin < endMin) segments.add(Triple(beginMin, endMin, rate.valueIncVat))
        }
        if (segments.isEmpty()) return null

        // The segments must tile 0..1440 exactly — refuse partial coverage
        // rather than inserting a plan that fails validatePlan.
        val covered = segments.sortedBy { it.first }
        var cursor = 0
        for ((begin, end, _) in covered) {
            if (begin != cursor) return null
            cursor = end
        }
        if (cursor != 1440) return null
        return covered
    }

    /** The region's open Outgoing Fixed export rate (pence/kWh), or 0.0. */
    private fun outgoingFixedRate(
        client: OctopusRestClient,
        products: List<com.tfcode.comparetout.importers.octopus.responses.ProductsResponse.Product>,
        gsp: String
    ): Double {
        val outgoing = products.filter { it.direction == "EXPORT" && it.availableTo == null }
        val fixed = outgoing.firstOrNull { it.code.contains("OUTGOING-FIX") }
            ?: outgoing.firstOrNull { it.code.contains("OUTGOING") && !it.code.contains("AGILE") }
            ?: return 0.0
        return try {
            val detail = client.getProductDetail(fixed.code)
            val tariff = detail.singleRegisterElectricityTariffs?.get(gsp)?.values?.firstOrNull()
            tariff?.standardUnitRateIncVat
                ?: tariff?.code?.let { code ->
                    firstRate(client.getStandardUnitRates(fixed.code, code, yesterdayFrom(), yesterdayTo()))
                } ?: 0.0
        } catch (e: OctopusException) {
            0.0
        }
    }

    /** Makes [planName] the favourite when no favourite is currently set. */
    private fun favouriteIfUnset(planName: String) {
        runBlocking { favouriteStore.ensureLoaded() }
        if (favouriteStore.id.value != null) return
        val planId = repository.allPricePlansNow
            ?.firstOrNull { it.supplier == SUPPLIER && it.planName == planName }
            ?.pricePlanIndex ?: return
        favouriteStore.setFavourite(planId)
    }

    private fun firstRate(rates: List<RatesResponse.Rate>): Double? =
        rates.firstOrNull { it.paymentMethod == null || it.paymentMethod.equals("DIRECT_DEBIT", true) }
            ?.valueIncVat

    private fun parseInstant(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

    private fun minuteOfDay(instant: Instant, zone: ZoneId, day: LocalDate): Int {
        val local = instant.atZone(zone)
        if (local.toLocalDate() != day) return if (local.toLocalDate() < day) 0 else 1440
        return local.hour * 60 + local.minute
    }

    private fun yesterdayFrom(): String =
        LocalDate.now(LONDON).minusDays(1).atStartOfDay(LONDON).toInstant().toString()

    private fun yesterdayTo(): String =
        LocalDate.now(LONDON).atStartOfDay(LONDON).toInstant().toString()

    private companion object {
        const val SUPPLIER = "Octopus Energy"
        const val MAX_DAILY_WINDOWS = 12
        const val POLITE_DELAY_MS = 200L
        val LONDON: ZoneId = ZoneId.of("Europe/London")
    }
}

/** Hilt EntryPoint so non-Hilt callers (OctopusCatchUpWorker) can resolve [OctopusTariffPlans]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OctopusTariffPlansEntryPoint {
    fun octopusTariffPlans(): OctopusTariffPlans
}
