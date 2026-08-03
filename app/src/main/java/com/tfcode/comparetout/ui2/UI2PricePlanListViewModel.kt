package com.tfcode.comparetout.ui2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import androidx.lifecycle.map
import com.tfcode.comparetout.model.priceplan.CompatibilityTags
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.PlanCombination
import com.tfcode.comparetout.model.priceplan.PricePlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────
// UI2 supplier-plan list — read-only view + actions (delete, favourite).
// The list is sourced from getAllPricePlans() LiveData so it reacts to any
// changes made by the wizard or by data imports.
// ──────────────────────────────────────────────────────────────────────────

/** One row in the supplier-plan list. */
data class PricePlanListRow(
    val planId: Long,
    val supplier: String,
    val planName: String,
    val reference: String,
    val standingCharges: Double,
    val feed: Double,
    val signUpBonus: Double,
    val rateCount: Int,
    val deemedExport: Boolean,
    val lastUpdate: String,
    val active: Boolean,
    val location: String = "",
    val hasRestrictions: Boolean = false,
    /** Generated wholesale-tracking plan (carries DynamicTerms). */
    val isDynamic: Boolean = false,
    /** Dynamic plan whose prices have not been materialised yet. */
    val isPending: Boolean = false,
    /** First calendar year of the backtest window, when set. */
    val dynamicYear: Int? = null,
    /** First month (1-12) of the 12-month window; null == a legacy Jan–Dec year. */
    val dynamicPeriodStartMonth: Int? = null,
    /** [PricePlan.DIRECTION_IMPORT] / [PricePlan.DIRECTION_EXPORT]. */
    val direction: Int = PricePlan.DIRECTION_IMPORT,
    /** Export plans only: pairing tags. Empty = open market. */
    val compatibleWith: List<String> = emptyList(),
    /**
     * Minute-weighted mean of this plan's own rates, or null when it has none
     * (a pending dynamic plan). Derived, never stored — it is microseconds over
     * rates already in memory, so no cached column is needed (§4.4).
     */
    val averageRate: Double? = null
) {
    val isExport: Boolean get() = direction == PricePlan.DIRECTION_EXPORT
    val isOpenMarket: Boolean get() = compatibleWith.isEmpty()

    /**
     * May this export plan be paired with [importRow]? Evaluated live against the
     * import plan's CURRENT supplier/name, so renaming a plan can never silently
     * break or fabricate a pairing. Delegates to the model's tag grammar rather
     * than re-implementing the wildcard rules in the UI.
     */
    fun pairsWith(importRow: PricePlanListRow): Boolean {
        if (!isExport || importRow.isExport) return false
        if (isOpenMarket) return true
        return compatibleWith.any {
            CompatibilityTags.matchesTag(it, importRow.supplier, importRow.planName)
        }
    }
    /** True when [location] is set and differs from the device's country. */
    fun locationMismatch(deviceCountry: String): Boolean =
        isLocationMismatch(location, deviceCountry)
}

/**
 * Minute-weighted mean price across a plan's own rate set — the cheap ranking
 * used by the cheapest-pair default seed and shown on the export accordion.
 * Null when the plan has no rates in its own direction (a pending dynamic plan),
 * which is the signal to exclude it from ranking rather than treat it as free.
 */
fun averageRateOf(plan: PricePlan, rates: List<DayRate>): Double? {
    val own = plan.primaryRates(rates)
    var weighted = 0.0
    var minutes = 0L
    own.forEach { dr ->
        val bands = dr.minuteRateRange?.rates.orEmpty()
        bands.forEach { rr ->
            val span = (rr.end - rr.begin).coerceAtLeast(0)
            weighted += rr.price * span
            minutes += span
        }
    }
    return if (minutes == 0L) null else weighted / minutes
}

/** A plan is location-mismatched only when BOTH sides are known and differ. */
fun isLocationMismatch(location: String, deviceCountry: String): Boolean =
    location.isNotBlank() && deviceCountry.isNotBlank() &&
            !location.equals(deviceCountry, ignoreCase = true)

/** SIM country → network country → locale. Uppercase ISO 3166-1 alpha-2, or "". */
fun resolveDeviceCountry(context: android.content.Context): String {
    val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as? android.telephony.TelephonyManager
    val sim = tm?.simCountryIso.orEmpty()
    if (sim.isNotBlank()) return sim.uppercase()
    val network = tm?.networkCountryIso.orEmpty()
    if (network.isNotBlank()) return network.uppercase()
    return java.util.Locale.getDefault().country.uppercase()
}

@HiltViewModel
class UI2PricePlanListViewModel @Inject constructor(
    application: Application,
    private val repository: ToutcRepository,
    private val favouriteStore: FavouritePlanStore
) : AndroidViewModel(application) {

    private val _rows = MutableStateFlow<List<PricePlanListRow>>(emptyList())
    val rows: StateFlow<List<PricePlanListRow>> = _rows.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val favouriteId = favouriteStore.id.asLiveData()

    /** Device country the location filter compares against (fixed per session). */
    val deviceCountry: String = resolveDeviceCountry(application)

    init {
        viewModelScope.launch(Dispatchers.IO) { favouriteStore.ensureLoaded() }
        viewModelScope.launch(Dispatchers.Main) {
            repository.allPricePlans.asFlow().collect { map: Map<PricePlan, List<DayRate>>? ->
                val entries = map ?: emptyMap()
                _rows.value = entries.entries.map { (plan, drs) ->
                    PricePlanListRow(
                        planId = plan.pricePlanIndex,
                        supplier = plan.supplier,
                        planName = plan.planName,
                        reference = plan.reference,
                        standingCharges = plan.standingCharges,
                        feed = plan.feed,
                        signUpBonus = plan.signUpBonus,
                        rateCount = drs.size,
                        deemedExport = plan.isDeemedExport,
                        lastUpdate = plan.lastUpdate,
                        active = plan.isActive,
                        location = plan.location,
                        hasRestrictions = plan.restrictions?.isActive == true &&
                                plan.restrictions?.restrictions.orEmpty().isNotEmpty(),
                        isDynamic = plan.isDynamic,
                        isPending = plan.isPendingDynamic(drs),
                        dynamicYear = plan.dynamicTerms?.year,
                        dynamicPeriodStartMonth = plan.dynamicTerms?.periodStartMonth,
                        direction = plan.direction,
                        compatibleWith = plan.compatibleWith?.tags.orEmpty(),
                        averageRate = averageRateOf(plan, drs)
                    )
                }.sortedWith(compareBy({ it.supplier.lowercase() }, { it.planName.lowercase() }))
                // Drop the favourite if the plan it points to has been deleted.
                favouriteStore.reconcile(entries.keys.map { it.pricePlanIndex })
                // Location filter: plans for another country are auto-DEACTIVATED so
                // costing/compare skip them. One-way — never auto-activate; the user
                // may have deliberately switched a local plan off, and can manually
                // re-activate a revealed foreign plan.
                entries.keys
                    .filter { it.isActive && isLocationMismatch(it.location, deviceCountry) }
                    .forEach { repository.updatePricePlanActiveStatus(it.pricePlanIndex.toInt(), false) }
            }
        }
    }

    /** Ticked pairings as (importPlanId → set of exportPlanIds). */
    val pairings: LiveData<Map<Long, Set<Long>>> =
        repository.planCombinations.map { list ->
            list.orEmpty().groupBy { it.importPlanID }
                .mapValues { (_, v) -> v.map { it.exportPlanID }.toSet() }
        }

    /**
     * Tick / untick one pairing. Nothing is computed here: costing already covers
     * every pair (the totals decompose), so a tick only changes what is shown —
     * and, per §3.3, whether the import plan's bundled row exists at all.
     */
    fun toggleCombination(importPlanId: Long, exportPlanId: Long, currentlyOn: Boolean) {
        if (currentlyOn) repository.deselectPlanCombination(importPlanId, exportPlanId)
        else repository.selectPlanCombination(
            importPlanId, exportPlanId, PlanCombination.SOURCE_MANUAL)
    }

    /** Replace an export plan's compatibility tags; empty means open market. */
    fun setCompatibilityTags(planId: Long, tags: List<String>) {
        val cleaned = tags.map { it.trim() }.filter { it.isNotEmpty() }
        repository.updateCompatibilityTags(
            planId, if (cleaned.isEmpty()) null else CompatibilityTags(cleaned))
    }

    /**
     * Tick the cheapest import × cheapest export pair, once, when nothing is
     * ticked yet — so a novice never lands on an empty Combinations section.
     *
     * Ranking is by [averageRateOf]; pending plans (no prices yet) are excluded
     * rather than treated as free, and incompatible pairs are skipped. Stamped
     * SOURCE_HEURISTIC, which is the same write path the future ranking
     * heuristic will use, so the mechanism is exercised from day one.
     */
    fun seedDefaultCombinationIfEmpty(rows: List<PricePlanListRow>) {
        if (!pairings.value.isNullOrEmpty()) return
        val imports = rows.filter { !it.isExport && it.active && it.averageRate != null }
        val exports = rows.filter { it.isExport && it.active && it.averageRate != null }
        if (imports.isEmpty() || exports.isEmpty()) return
        // Cheapest import = lowest average buy rate. Best export = HIGHEST average
        // sell rate — export income, not cost, so the comparison inverts.
        val bestImport = imports.minByOrNull { it.averageRate!! } ?: return
        val bestExport = exports
            .filter { it.pairsWith(bestImport) }
            .maxByOrNull { it.averageRate!! } ?: return
        repository.selectPlanCombination(
            bestImport.planId, bestExport.planId, PlanCombination.SOURCE_HEURISTIC)
    }

    fun toggleFavourite(planId: Long) {
        favouriteStore.setFavourite(if (favouriteStore.id.value == planId) null else planId)
    }

    fun setActive(planId: Long, active: Boolean) {
        repository.updatePricePlanActiveStatus(planId.toInt(), active)
    }

    fun delete(planId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            // Clear costings that reference this plan FIRST. Without this the
            // costings rows linger as orphans until CostingWorker or
            // ComparisonUIViewModel runs `pruneCostings()` — the Compare tab
            // can show ghost rows for a deleted plan in the meantime. Legacy
            // PricePlanNavFragment did the same explicit pre-delete.
            repository.deleteRelatedCostings(planId.toInt())
            repository.deletePricePlan(planId.toInt())
            // The reconcile in the rows-collect coroutine will clear the favourite
            // automatically next time the LiveData emits, but do it eagerly so the
            // UI doesn't show a star next to a row that's about to vanish.
            if (favouriteStore.id.value == planId) favouriteStore.setFavourite(null)
        }
    }

    /** Delete every supplier plan, clearing each plan's cached costings first
     *  (mirrors [delete] — without the pre-delete, the Compare tab can show
     *  ghost rows for a since-deleted plan until a later prune runs). */
    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _rows.value.forEach { row ->
                repository.deleteRelatedCostings(row.planId.toInt())
                repository.deletePricePlan(row.planId.toInt())
            }
            favouriteStore.setFavourite(null)
        }
    }

    /**
     * Add the parsed plans to the library via the same repository path the
     * Import / Export screen uses. `clobber` replaces a plan with a matching
     * name; otherwise the import is kept alongside the existing one.
     */
    suspend fun importPlansFromList(
        list: List<PricePlanJsonFile>,
        clobber: Boolean
    ): ImportOutcome = withContext(Dispatchers.IO) {
        var replaced = 0
        var added = 0
        // Name AND direction, matching the v17 unique key, so an incoming export
        // plan is not miscounted as replacing a same-named import plan.
        val existingNames: Set<Pair<String, Int>> =
            repository.allPricePlansNow?.map { it.planName to it.direction }?.toSet().orEmpty()
        // Pass 1 — insert every plan, synchronously, keeping each export plan's
        // new id so its pairings can be restored below.
        val exportPlanIds = mutableMapOf<PricePlanJsonFile, Long>()
        list.forEach { pp ->
            val plan = JsonTools.createPricePlan(pp)
            val drs = ArrayList<DayRate>()
            pp.rates?.forEach { drj -> drs.add(JsonTools.createDayRate(drj)) }
            val newId = repository.insertSync(plan, drs, clobber)
            if (plan.isExport && !pp.selectedWith.isNullOrEmpty()) {
                // A rejected duplicate returns 0 — fall back to the existing row,
                // which is the plan the pairings refer to anyway.
                exportPlanIds[pp] = if (newId != 0L) newId
                else repository.findPricePlanID(
                    plan.supplier, plan.planName, PricePlan.DIRECTION_EXPORT)
            }
            if ((plan.planName to plan.direction) in existingNames) replaced += 1 else added += 1
            // A terms-only dynamic plan lands pending; auto-materialise it
            // (self-heal poke — the badge offers tap-to-retry if this fails).
            DynamicTariffWorker.maybeEnqueuePendingImport(getApplication(), pp)
        }
        // Pass 2 — re-tick pairings. Separate from pass 1 because an export plan
        // may name an import plan that appears LATER in the same file. Names that
        // do not resolve here are dropped silently: a shared file naming a plan
        // the recipient does not have must not fail the import.
        exportPlanIds.forEach { (pp, id) ->
            if (id != 0L) repository.restorePairings(id, pp.selectedWith)
        }
        ImportOutcome(replaced, added)
    }

    /**
     * Build the JSON payload for a single plan, ready for sharing. The map is
     * built with one entry so it round-trips through the standard
     * `JsonTools.createPricePlanJson` path used by legacy imports — meaning a
     * shared file can be re-imported by either UI without special handling.
     */
    suspend fun buildPlanJson(planId: Long): String? = withContext(Dispatchers.IO) {
        val all = repository.allPricePlansForExport ?: return@withContext null
        val entry = all.entries.firstOrNull { it.key.pricePlanIndex == planId }
            ?: return@withContext null
        // Carry the plan's pairings so a shared export plan arrives already
        // paired with the import plans the recipient also has.
        JsonTools.createPricePlanJson(
            mapOf(entry.key to entry.value), repository.pairingsAsNames)
    }
}
