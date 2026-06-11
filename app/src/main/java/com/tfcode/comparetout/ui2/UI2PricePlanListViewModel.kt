package com.tfcode.comparetout.ui2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
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
    val active: Boolean
)

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
                        active = plan.isActive
                    )
                }.sortedWith(compareBy({ it.supplier.lowercase() }, { it.planName.lowercase() }))
                // Drop the favourite if the plan it points to has been deleted.
                favouriteStore.reconcile(entries.keys.map { it.pricePlanIndex })
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
