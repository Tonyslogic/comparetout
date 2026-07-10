package com.tfcode.comparetout.ui2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
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
    /** The backtest year recorded in the terms, when set. */
    val dynamicYear: Int? = null
) {
    /** True when [location] is set and differs from the device's country. */
    fun locationMismatch(deviceCountry: String): Boolean =
        isLocationMismatch(location, deviceCountry)
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
                        dynamicYear = plan.dynamicTerms?.year
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
        val existingNames: Set<String> =
            repository.allPricePlansNow?.map { it.planName }?.toSet().orEmpty()
        list.forEach { pp ->
            val plan = JsonTools.createPricePlan(pp)
            val drs = ArrayList<DayRate>()
            pp.rates?.forEach { drj -> drs.add(JsonTools.createDayRate(drj)) }
            repository.insert(plan, drs, clobber)
            if (plan.planName in existingNames) replaced += 1 else added += 1
            // A terms-only dynamic plan lands pending; auto-materialise it
            // (self-heal poke — the badge offers tap-to-retry if this fails).
            DynamicTariffWorker.maybeEnqueuePendingImport(getApplication(), pp)
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
        JsonTools.createPricePlanJson(mapOf(entry.key to entry.value))
    }
}
