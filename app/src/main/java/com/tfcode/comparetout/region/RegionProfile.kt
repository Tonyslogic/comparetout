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

package com.tfcode.comparetout.region

import com.tfcode.comparetout.BuildConfig

// ──────────────────────────────────────────────────────────────────────────
// Region editions (plans/region/survey.md §3c).
//
// The build flavor *is* the region: BuildConfig.REGION ("IE"/"GB") selects a
// profile at class-load time and everything region-variant funnels through it
// — currency symbols, capability flags (deemed export / MIC), which importers
// are offered, and the community price-plan feed. Shared screens stay
// monolithic and *ask* the profile instead of being forked per flavor.
//
// Adding a region = add a profile + a flavor; no hunting through screens.
// The profile is deliberately not user-accessible: region is fixed by the
// installed edition (a SIM/locale mismatch only produces a one-time warning,
// see UI2MainActivity.maybeShowRegionMismatch).
// ──────────────────────────────────────────────────────────────────────────

/**
 * A wholesale market whose historical prices this edition can fetch on-device
 * for dynamic-tariff materialisation. Resolved to a [com.tfcode.comparetout.dynamic.HistoricalRateSource]
 * by id; UI entry points hide when the region lists none.
 */
data class DynamicMarket(
    /** Stable descriptor id carried by DynamicTerms.market, e.g. "ISEM-DAM". */
    @JvmField val id: String,
    /** User-visible name, e.g. "I-SEM Day-Ahead (SEMOpx)". */
    @JvmField val displayName: String,
    /** Attribution / licensing note shown on the generate sheet and in plan references. */
    @JvmField val attribution: String
)

data class RegionProfile(
    /** ISO 3166-1 alpha-2, uppercase — compared against [PricePlan.location] and the device SIM. */
    @JvmField val regionCode: String,
    /** User-visible edition name for the region-mismatch warning. */
    @JvmField val editionName: String,
    /** Major currency unit symbol: "€" / "£". */
    @JvmField val currencySymbol: String,
    /** Minor currency unit symbol: "c" / "p" (euro-cents / pence). */
    @JvmField val minorSymbol: String,
    /** Deemed/estimated micro-generation export payments — IE-only concept. */
    @JvmField val hasDeemedExport: Boolean,
    /** Maximum Import Capacity breach reporting — IE grid concept. */
    @JvmField val hasMIC: Boolean,
    /** Octopus Energy importer + tariff browser (GB supplier). */
    @JvmField val hasOctopus: Boolean,
    /** ESBN smart-meter HDF importer (IE meter operator). */
    @JvmField val hasEsbn: Boolean,
    /**
     * Community-maintained supplier tariff feed for this region, or null when
     * no feed exists yet (the download entry points hide themselves).
     */
    @JvmField val pricePlanFeedUrl: String?,
    /** Caveat shown beside the community feed entry (null when no feed). */
    @JvmField val pricePlanFeedNote: String?,
    /** Wholesale markets available for dynamic tariffs; empty = feature hidden. */
    @JvmField val dynamicMarkets: List<DynamicMarket> = emptyList()
) {
    /** Rate unit for per-kWh prices: "c/kWh" / "p/kWh". */
    val rateUnit: String get() = "$minorSymbol/kWh"
}

object RegionProfiles {

    @JvmField
    val IE = RegionProfile(
        regionCode = "IE",
        editionName = "Ireland",
        currencySymbol = "€",
        minorSymbol = "c",
        hasDeemedExport = true,
        hasMIC = true,
        hasOctopus = false,
        hasEsbn = true,
        pricePlanFeedUrl =
            "https://raw.githubusercontent.com/Tonyslogic/comparetout-doc/main/price-plans/rates.json",
        pricePlanFeedNote = "Community-maintained Irish supplier tariffs — may be out of " +
            "date. You can edit any plan after importing.",
        dynamicMarkets = listOf(
            DynamicMarket(
                id = "ISEM-DAM",
                displayName = "I-SEM Day-Ahead (SEMOpx)",
                attribution = "Prices fetched on this device from SEMOpx public reports " +
                    "(reports.semopx.com), provided AS IS by SEMOpx. Not redistributed."
            )
        )
    )

    @JvmField
    val GB = RegionProfile(
        regionCode = "GB",
        editionName = "Great Britain",
        currencySymbol = "£",
        minorSymbol = "p",
        hasDeemedExport = false,
        hasMIC = false,
        hasOctopus = true,
        hasEsbn = false,
        // No community feed for GB yet — the Octopus tariff browser covers
        // open tariffs; a curated feed can be added here when one exists.
        pricePlanFeedUrl = null,
        pricePlanFeedNote = null
    )

    /** The profile for the installed edition — fixed at build time by the flavor. */
    @JvmField
    val current: RegionProfile = if (BuildConfig.REGION == "GB") GB else IE
}
