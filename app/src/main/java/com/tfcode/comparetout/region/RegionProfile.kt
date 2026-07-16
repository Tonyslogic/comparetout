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
// The build flavor *is* the region: BuildConfig.REGION ("IE"/"GB"/"GLOBAL")
// selects a profile at class-load time and everything region-variant funnels
// through it — currency symbols, capability flags (deemed export / MIC),
// which importers are offered, and the community price-plan feed. Shared
// screens stay monolithic and *ask* the profile instead of being forked per
// flavor.
//
// Adding a region = add a profile + a flavor; no hunting through screens.
// The profile is deliberately not user-accessible: region is fixed by the
// installed edition (a SIM/locale mismatch only produces a one-time warning,
// see UI2MainActivity.maybeShowRegionMismatch).
//
// GLOBAL (the source edition, plans/source/plan.md §5 Q6) is region-less:
// every importer is offered, currency symbols follow the device locale, no
// mismatch warning, and no baked community feed — the download UI asks the
// user for a region instead (see communityFeedChoices).
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
    /** ISO 3166-1 alpha-2, uppercase — compared against [PricePlan.location] and the device SIM.
     * The global profile uses "GLOBAL", which matches nothing (see [isGlobal]). */
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

    /** True only for the region-less source edition: skip the region-mismatch
     * warning and ask the user for a region where one is needed (community feed). */
    val isGlobal: Boolean get() = regionCode == "GLOBAL"
}

/** A community tariff feed offered by the region-choice download UI. */
data class CommunityFeed(
    /** User-visible region name, e.g. "Ireland". */
    @JvmField val regionName: String,
    @JvmField val url: String,
    @JvmField val note: String
)

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

    /**
     * The region-less source edition (plans/source/plan.md §5 Q6): every
     * importer is offered (the region source-mask passes everything), the
     * IE-only plan/dashboard features stay (deemed export / MIC degrade to
     * no-ops where they don't apply), currency display follows the device
     * locale, and there is no baked community feed — the download UI asks
     * for a region via [communityFeedChoices] instead.
     */
    @JvmField
    val GLOBAL: RegionProfile = localeCurrency().let { (major, minor) ->
        RegionProfile(
            regionCode = "GLOBAL",
            editionName = "Global",
            currencySymbol = major,
            minorSymbol = minor,
            hasDeemedExport = true,
            hasMIC = true,
            hasOctopus = true,
            hasEsbn = true,
            pricePlanFeedUrl = null,
            pricePlanFeedNote = null,
            dynamicMarkets = emptyList()
        )
    }

    /** The profile for the installed edition — fixed at build time by the flavor. */
    @JvmField
    val current: RegionProfile = when (BuildConfig.REGION) {
        "GB" -> GB
        "GLOBAL" -> GLOBAL
        else -> IE
    }

    /**
     * The community tariff feeds the import UI should offer: a region-bound
     * edition gets its own feed (or nothing), the global edition gets every
     * feed the app knows about, labelled by region, so the download UI can
     * ask the user to pick one. Grows automatically as regions gain feeds.
     */
    @JvmStatic
    fun communityFeedChoices(region: RegionProfile = current): List<CommunityFeed> =
        (if (region.isGlobal) listOf(IE, GB) else listOf(region)).mapNotNull { r ->
            r.pricePlanFeedUrl?.let {
                CommunityFeed(r.editionName, it, r.pricePlanFeedNote ?: "")
            }
        }

    /**
     * Currency symbols for the device locale — display-only (plans carry raw
     * numbers). Minor units are mapped for the currencies the app has
     * conventions for; anything else falls back to the generic cent sign,
     * and a locale without a resolvable currency falls back to €/c.
     */
    private fun localeCurrency(): Pair<String, String> = runCatching {
        val cur = java.util.Currency.getInstance(java.util.Locale.getDefault())
        when (cur.currencyCode) {
            "EUR" -> "€" to "c"
            "GBP" -> "£" to "p"
            "USD" -> "$" to "¢"
            else -> cur.symbol to "¢"
        }
    }.getOrDefault("€" to "c")
}
