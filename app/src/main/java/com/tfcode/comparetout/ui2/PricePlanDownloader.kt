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

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads the community-maintained Irish supplier tariffs published on the
 * project's doc site and inserts them as price plans.
 *
 * This is the same payload + transform the legacy `MainActivity` "download"
 * menu used (`MainActivity.java:600-665`), ported to Kotlin/coroutines and
 * routed through the public [ToutcRepository] surface — no DAO/Java edits.
 *
 * Simple mode needs *real* tariffs to show a meaningful cost (fabricated
 * samples are unacceptable), but the list is community-maintained and **may be
 * out of date** — callers must surface that caveat. The price-plan editor stays
 * available so the user can correct anything that has drifted.
 */
@Singleton
class PricePlanDownloader @Inject constructor(
    private val repository: ToutcRepository
) {

    sealed class Result {
        /** [added] new plans inserted, [replaced] existing names overwritten
         * (only possible when clobber = true). */
        data class Loaded(val added: Int, val replaced: Int) : Result()
        /** The fetch succeeded but the list was empty. */
        data object Empty : Result()
        /** No connectivity / host unreachable — distinct so the UI can prompt a retry. */
        data object NoNetwork : Result()
        data class Failed(val error: Throwable) : Result()
    }

    /**
     * @param clobber when true, a downloaded plan whose name already exists
     *   overwrites it; when false (default) the existing plan is kept and the
     *   download is effectively additive.
     */
    suspend fun download(clobber: Boolean = false): Result = withContext(Dispatchers.IO) {
        try {
            val plans: List<PricePlanJsonFile> = URL(RATES_URL).openStream().use { stream ->
                InputStreamReader(stream).use { reader ->
                    Gson().fromJson(reader, object : TypeToken<List<PricePlanJsonFile>>() {}.type)
                }
            } ?: emptyList()

            if (plans.isEmpty()) return@withContext Result.Empty

            val existingNames: Set<String> =
                repository.allPricePlansNow?.mapNotNull { it.planName }?.toSet().orEmpty()
            var added = 0
            var replaced = 0
            plans.forEach { pp ->
                val plan = JsonTools.createPricePlan(pp)
                val drs = ArrayList<DayRate>()
                pp.rates?.forEach { drs.add(JsonTools.createDayRate(it)) }
                repository.insert(plan, drs, clobber)
                if (plan.planName in existingNames) replaced += 1 else added += 1
            }
            Result.Loaded(added, replaced)
        } catch (e: UnknownHostException) {
            Result.NoNetwork
        } catch (e: ConnectException) {
            Result.NoNetwork
        } catch (e: SocketTimeoutException) {
            Result.NoNetwork
        } catch (e: IOException) {
            Result.Failed(e)
        } catch (e: Exception) {
            Result.Failed(e)
        }
    }

    companion object {
        const val RATES_URL =
            "https://raw.githubusercontent.com/Tonyslogic/comparetout-doc/main/price-plans/rates.json"

        /**
         * Prompt the user copies into a general-purpose LLM to generate a
         * schema-conformant price-plan JSON for a supplier we don't carry in
         * the community list. The user edits the supplier name, runs it, then
         * pastes the result back via the "Paste JSON" import source. Not a
         * `const` because it interpolates a literal `$` for the JSON-schema
         * `$schema` key.
         */
        val LLM_PROMPT = """SUPPLIER: [INSERT SUPPLIER NAME HERE]
COUNTRY: [INSERT COUNTRY HERE]

==========================================================================
TASK
==========================================================================
Generate a JSON document of the first-year price experience for every
residential electricity smart-meter tariff offered by the supplier named
above, in the country named above. The audience is a switching-comparison
app whose users re-evaluate suppliers every ~12 months — so the headline
first-year cost for a new online sign-up taking the supplier's default
best discount bundle (typically direct debit + paperless billing where
that exists) is what matters, not the post-discount rollover rate.

Work in the country's own language and currency. Everything below that
mentions a specific country, regulator, tax rate, or comparison site is
an EXAMPLE — substitute the correct equivalent for the country named
above.

==========================================================================
SCHEMA (embedded — use field names, types, and enums exactly as defined)
==========================================================================
{
  "${'$'}schema": "http://json-schema.org/draft-07/schema#",
  "type": "array",
  "description": "List of electricity-only smart meter plans available in the target country's electricity market.",
  "items": {
    "type": "object",
    "properties": {
      "Active": {
        "type": "boolean",
        "description": "Indicates whether the plan is currently available for sign-up."
      },
      "Bonus cash": {
        "type": "number",
        "description": "Cash reward provided by the supplier for signing up (typically €0 if not offered)."
      },
      "DeemedExport": {
        "type": "boolean",
        "description": "Whether the plan includes deemed export payments for surplus electricity (typically false for electricity-only plans)."
      },
      "Feed": {
        "type": "number",
        "description": "Supplier feed-in tariff rate in cents per kWh for exporting excess electricity to the grid."
      },
      "LastUpdate": {
        "type": "string",
        "description": "The last date when this plan was updated in the system.",
        "examples": ["2025-06-07", "YYYY-MM-DD"]
      },
      "Plan": {
        "type": "string",
        "description": "The official name of the electricity plan as defined by the supplier."
      },
      "Rates": {
        "type": "array",
        "description": "A collection of rate structures defining different electricity costs based on days and times.",
        "items": {
          "type": "object",
          "properties": {
            "Days": {
              "type": "array",
              "items": { "type": "integer", "minimum": 0, "maximum": 6 },
              "description": "Days of the week the rate applies. 0 = Sunday, 6 = Saturday."
            },
            "startDate": {
              "type": "string",
              "description": "The start date when this rate is applicable.",
              "examples": ["01/01", "MM/DD"]
            },
            "endDate": {
              "type": "string",
              "description": "The end date after which this rate is no longer valid.",
              "examples": ["12/31", "MM/DD"]
            },
            "MinuteRange": {
              "type": "array",
              "description": "A breakdown of energy pricing throughout a 24-hour period using minute-based intervals.",
              "items": {
                "type": "object",
                "properties": {
                  "cost": {
                    "type": "number",
                    "description": "Electricity unit rate in cents per kWh for the specified time range."
                  },
                  "startMinute": {
                    "type": "integer",
                    "minimum": 0,
                    "maximum": 1440,
                    "description": "The minute of the day when the rate starts (0 = midnight, 1440 = end of day)."
                  },
                  "endMinute": {
                    "type": "integer",
                    "minimum": 0,
                    "maximum": 1440,
                    "description": "The minute of the day when the rate ends (e.g., 480 = 08:00 AM)."
                  }
                },
                "required": ["cost", "startMinute", "endMinute"]
              }
            }
          },
          "required": ["Days", "startDate", "endDate", "MinuteRange"]
        }
      },
      "Reference": {
        "type": "string",
        "format": "uri",
        "description": "A direct URL to the supplier’s official plan details or tariff page."
      },
      "Restrictions": {
        "type": "object",
        "description": "Limitations or conditions associated with the plan.",
        "properties": {
          "active": {
            "type": "boolean",
            "description": "Indicates whether any specific restrictions apply to this plan."
          },
          "restrictionEntries": {
            "type": "array",
            "description": "An array of restriction entries, such as usage limits or excess charges.",
            "items": {
              "type": "object",
              "properties": {
                "excessCost": {
                  "type": "number",
                  "description": "The cost per kWh exceeding a defined usage limit."
                },
                "limit": {
                  "type": "number",
                  "description": "Maximum allowable usage before excess charges apply."
                },
                "period": {
                  "type": "string",
                  "enum": ["Monthly", "Bimonthly", "Annual"],
                  "description": "Timeframe for restriction enforcement."
                },
                "scope": {
                  "type": "string",
                  "description": "The applicable rate affected by this restriction (e.g., must match a 'cost' value from MinuteRange in Rates).",
                  "examples": ["24.92", "40.75", "7.45"]
                }
              },
              "required": ["excessCost", "limit", "period", "scope"]
            }
          }
        },
        "required": ["active", "restrictionEntries"]
      },
      "Standing charges": {
        "type": "number",
        "description": "Fixed daily or monthly charge applied regardless of usage, typically covering grid maintenance."
      },
      "Supplier": {
        "type": "string",
        "description": "The electricity provider offering this plan (e.g., Bord Gáis Energy, SSE Airtricity)."
      }
    },
    "required": [
      "Active",
      "Bonus cash",
      "DeemedExport",
      "Feed",
      "LastUpdate",
      "Plan",
      "Rates",
      "Reference",
      "Restrictions",
      "Standing charges",
      "Supplier"
    ]
  }
}

==========================================================================
SUPPLIER STRING
==========================================================================
Use exactly the brand string found on the supplier's own main page. E.g.:
  IE: "Electric Ireland" | "Bord Gáis Energy" | "SSE Airtricity"
  UK: "Octopus Energy" | "British Gas" | "EDF" | "E.ON Next"
  DE: "E.ON" | "Vattenfall" | "EnBW" | "Yello"
Match the supplier's own spelling/casing for the country named above.

==========================================================================
SCOPE
==========================================================================
Cover every residential smart-meter electricity tariff from the named
supplier that is OPEN TO NEW SIGN-UPS today.
Skip: business plans, prepay/PAYG, oil/gas-only, discontinued plans.
Include: time-of-use, flat smart, EV smart, weekend smart, night-boost
  smart — any plan that requires a smart meter and is open to new
  residential customers. Create a plan for each variant, for example
  a user choice between Saturday and Sunday will result in two plans.

==========================================================================
SOURCES — priority order
==========================================================================
PRIMARY:   The supplier's own residential tariff page on their own domain.
SECONDARY: The supplier's official price list / price-change notification
           as published by, or filed with, the country's national energy
           regulator (e.g. CRU in Ireland, Ofgem in Great Britain,
           Bundesnetzagentur in Germany — use the correct regulator for
           the country named above). Use this when the primary page is
           JS-rendered, paywalled, or otherwise not text-extractable.

If the supplier's marketing page renders rates in images or JS only:
  • Fetch any linked tariff PDF directly and read it as a document.
  • If rates appear in an image, fetch the image URL and read it visually.
  • Note "(OCR'd from <URL>)" in the evidence table if OCR was used.

EXPLICITLY BANNED — do not visit, do not paraphrase:
  Third-party price-comparison aggregators (any site whose business is
  comparing tariffs), news articles, Reddit, forums, blog posts,
  search snippets.

CROSS CHECK ONLY — after completing BLOCK 1 from primary sources,
  verify unit rates and standing charges against the regulator or one
  reputable independent comparison site for that country. Note any
  deviation of more than 0.5 minor-currency-units/kWh or 1
  major-currency-unit/year in BLOCK 2.
  Do not use these sites as the primary source for any number.

If neither the supplier's own page nor a regulator source is reachable,
every plan from that supplier goes to BLOCK 2 with a specific reason
(name the exact page URL that failed and how it failed).

==========================================================================
DISCOUNTS — first-year, direct-debit + paperless, new sign-up
==========================================================================
Apply, in this order, every discount a new online sign-up qualifies for
by default:

  (a) % unit-rate discount conditional on DD + paperless →
      bake into MinuteRange cost values.
  (b) % standing-charge discount on the same conditions →
      bake into Standing charges.
  (c) Welcome credit / cashback (energy credit, bill credit, or direct
      cashback) → put in "Bonus cash" in major currency units.
      Loyalty points, non-monetary rewards (e.g. theatre vouchers) → 0.
  (d) Fixed-term contract discount → apply it.
  (e) If multiple discount bundles exist, apply the BEST one available
      to a new online sign-up with DD + paperless. Do not stack
      incompatible bundles.

If a supplier publishes rates with no DD/online-billing condition
(e.g. the published rate IS the rate regardless of payment method),
use the published rate as-is. No blending or condition logic needed.

If discount conditions are ambiguous (e.g. "from X%" with no disclosed
maximum), send that plan to BLOCK 2 with reason "discount conditions
unclear — [quote the exact ambiguous phrase from the source]".

If a discount applies for less than 12 months, blend across the year:
  rate_year = rate_disc × (months_disc/12) + rate_std × ((12−months_disc)/12)

Show all discount and VAT arithmetic in the scratchpad.

==========================================================================
UNITS & CURRENCY
==========================================================================
All monetary fields are plain numbers — no currency symbol. Use the
country's own currency throughout, and STATE in the scratchpad which
currency and which VAT/sales-tax rate you applied. Keep the unit basis
identical across every plan from this supplier.

Standing charges → MAJOR currency units per YEAR (e.g. €/year, £/year),
  INCLUDING domestic-electricity VAT/sales tax, AFTER discount.
  If quoted per day: × 365.  Per month: × 12.  Per quarter: × 4.

MinuteRange.cost → MINOR currency units per kWh (e.g. euro-cents, pence,
  US cents), INCLUDING tax, AFTER discount.

Tax: apply the country's prevailing domestic-electricity VAT/sales-tax
  rate. ex-tax → inc-tax: × (1 + rate). If the published rate is already
  tax-inclusive, use it as-is. Tax treatment unclear → BLOCK 2,
  reason "tax treatment unclear".

Feed → MINOR currency units per kWh. The total per-kWh amount credited to
  the customer for exported electricity, including any supplier top-up
  above the national baseline export/feed-in rate. No discount applies to
  Feed. Use 0 if the supplier offers no export/microgeneration credit.
  This figure is usually the same for all of a supplier's plans and lives
  on a separate page — search for it.

Bonus cash → MAJOR currency units (positive number or 0).

Regulatory pass-through levies that apply EQUALLY to all suppliers in the
  country (e.g. Ireland's PSO levy, and similar fixed government charges):
  EXCLUDE from Standing charges. Include such a charge only if it is
  genuinely supplier-specific.

==========================================================================
RATES STRUCTURE
==========================================================================
- Active: true for all plans in BLOCK 1 (open to new sign-ups).
- DeemedExport: false unless explicitly documented by the supplier.
- LastUpdate: today's date, YYYY-MM-DD.
- startDate / endDate for year-round plans: "01/01" / "12/31" (MM/DD).
- Days: 0=Sun, 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat.

MinuteRange tiling rules:
  1. Within each Rates entry, MinuteRange entries must tile 0→1440
     exactly with no gaps and no overlaps.
  2. If a rate band applies only on certain days (e.g. peak Mon–Fri
     only), create separate Rates entries for those day sets:
       Entry A: Days [1,2,3,4,5] with Day/Peak/Night bands
       Entry B: Days [0,6] with Day/Night bands only (no peak)
  3. If a time band crosses midnight (e.g. "Sat 8am to Mon 8am"),
     split it at midnight boundaries:
       • Sat 23:00–24:00 → Saturday entry, startMinute 1380 endMinute 1440
       • Sun 00:00–08:00 → Sunday entry, startMinute 0 endMinute 480
       • Sun 23:00–24:00 → Sunday entry, startMinute 1380 endMinute 1440
       • Mon 00:00–08:00 → Monday entry (part of Mon–Fri entry),
                           startMinute 0 endMinute 480
     Cross-midnight bands are always resolvable this way. Do NOT send
     a plan to BLOCK 2 solely because a band crosses midnight.
  4. A plan where the customer chooses their own time window
     (e.g. "pick your own free day") must have one entry per variant
     (see SCOPE). Each variant is a separate plan entry with its own
     fixed MinuteRange. Only send to BLOCK 2 if the number of variants
     cannot be determined from the source.

Restrictions: default {"active": false, "restrictionEntries": []}.

==========================================================================
STALENESS
==========================================================================
If the supplier's primary tariff page carries a "correct as of" date
more than 90 days before today AND no subsequent price-change
announcement confirms the rates are still current, keep that plan to
BLOCK 1 add a note in BLOCK 2:
  "Tariff page dated [X]; no price-change announcement found
   confirming rates are current as of [today]."

If a price-change announcement exists (even without a new tariff page),
note the announcement URL in the evidence table and use the announced
rates.

==========================================================================
EVIDENCE TABLE
==========================================================================
Before the JSON, emit a markdown table:

  | Supplier | Country | Source URL | Evidence form | One verified number |

Where:
  • Source URL is the exact supplier page, image URL, or PDF you fetched.
  • Evidence form: "HTML text", "OCR'd image <URL>", or "PDF table".
  • One verified number: one specific price you read from that source,
    with its unit and tax basis (e.g. "Smart standing charge 72.29c/day
    inc VAT" or "Fixed unit rate 24.50p/kWh inc VAT").

If you cannot produce a row for this supplier, every plan goes to
BLOCK 2 with reason "no primary source reachable" (or more specific).

==========================================================================
OUTPUT FORMAT — produce exactly in this order
==========================================================================
1. Evidence table (markdown).

2. Scratchpad block titled "--- SCRATCHPAD (strip before use) ---"
   containing: raw source values, discount arithmetic, VAT conversions,
   MinuteRange tiling verification (confirm each entry sums to 1440),
   and standing charge unit conversions.

3. ```json
   [ … schema-conformant array … ]"""
    }
}

/** Hilt EntryPoint so Composables can resolve [PricePlanDownloader] without an injected ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PricePlanDownloaderEntryPoint {
    fun pricePlanDownloader(): PricePlanDownloader
}
