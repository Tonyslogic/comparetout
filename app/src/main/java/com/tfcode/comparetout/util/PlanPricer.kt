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
import com.tfcode.comparetout.model.priceplan.PricePlan

/**
 * One price plan's import and export rate lookups, partitioned by
 * [DayRate.rateType].
 *
 * A plan's `DayRate` list mixes BUY (import) and SELL (export) rows since v16.
 * Three rules apply everywhere a plan is costed, and each of them was being
 * broken somewhere before this class existed (plans/region/import-plans.md §1.4):
 *
 *  1. The import lookup is built from **BUY rates only**. Handing the mixed list
 *     to one [RateLookup] lets export prices answer import queries.
 *  2. Export is priced per-slot from the SELL rates when the plan has any, and
 *     falls back to the scalar [PricePlan.feed] when it does not — the pre-v16
 *     model, still the norm for hand-entered plans.
 *  3. A plan that [PricePlan.isPendingDynamic] cannot be priced at all: with no
 *     BUY rates every row costs 0 and the plan ranks cheapest. Callers must check
 *     [isPending] and skip, exactly as `CostingWorker` does.
 *
 * Restriction/tier state lives inside the underlying [RateLookup] and is
 * stateful across calls, so a pricer instance belongs to one costing pass over
 * one subject — do not share one across subjects or reuse it for a second pass.
 */
class PlanPricer @JvmOverloads constructor(
    private val plan: PricePlan,
    dayRates: List<DayRate>,
    startDoy: Int = 0
) {

    /** True when the plan's prices have not been materialised yet — do not cost it. */
    val isPending: Boolean = plan.isPendingDynamic(dayRates)

    /** True when export is priced per-slot rather than from the scalar feed. */
    val hasExportRates: Boolean

    private val buyLookup: RateLookup =
        RateLookup(plan, DayRate.buyRates(dayRates)).also { it.setStartDOY(startDoy) }

    private val sellLookup: RateLookup?

    init {
        val sells = DayRate.sellRates(dayRates)
        hasExportRates = sells.isNotEmpty()
        sellLookup = if (sells.isEmpty()) null
                     else RateLookup(plan, sells).also { it.setStartDOY(startDoy) }
    }

    /** Import price (minor units per kWh) for a slot. [kwh] drives tier restrictions. */
    fun buyRate(dayOf2001: Int, minuteOfDay: Int, dayOfWeek: Int, kwh: Double): Double =
        buyLookup.getRate(dayOf2001, minuteOfDay, dayOfWeek, kwh)

    /** Export price (minor units per kWh) for a slot, or the scalar feed. */
    fun sellRate(dayOf2001: Int, minuteOfDay: Int, dayOfWeek: Int, kwh: Double): Double =
        sellLookup?.getRate(dayOf2001, minuteOfDay, dayOfWeek, kwh) ?: plan.feed
}
