package com.tfcode.comparetout.ui2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.PricePlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────
// UI2 supplier-plan list — read-only view + actions (delete, toggle active).
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
    val active: Boolean,
    val lastUpdate: String
)

@HiltViewModel
class UI2PricePlanListViewModel @Inject constructor(
    application: Application,
    private val repository: ToutcRepository
) : AndroidViewModel(application) {

    private val _rows = MutableStateFlow<List<PricePlanListRow>>(emptyList())
    val rows: StateFlow<List<PricePlanListRow>> = _rows.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    init {
        viewModelScope.launch(Dispatchers.Main) {
            repository.getAllPricePlans().asFlow().collect { map: Map<PricePlan, List<DayRate>>? ->
                _rows.value = (map ?: emptyMap()).entries.map { (plan, drs) ->
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
                        active = plan.isActive,
                        lastUpdate = plan.lastUpdate
                    )
                }.sortedWith(compareBy({ it.supplier.lowercase() }, { it.planName.lowercase() }))
            }
        }
    }

    fun delete(planId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePricePlan(planId.toInt())
        }
    }
}
