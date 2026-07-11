package com.tfcode.comparetout.ui2

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.IntHolder
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.DoubleHolder
import com.tfcode.comparetout.model.priceplan.DynamicTerms
import com.tfcode.comparetout.model.priceplan.MinuteRateRange
import com.tfcode.comparetout.model.priceplan.PricePlan
import com.tfcode.comparetout.model.priceplan.Restrictions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────
// UI2 supplier-plan wizard.
//
// Day-of-week convention follows the legacy MODEL: 0=Sunday, 1=Monday … 6=Saturday.
// Rate bands live at MINUTE granularity (0..1440) so half-hourly / dynamic
// tariffs round-trip exactly.
//
// The builder mirrors the legacy validatePlan() rules:
//   - Each day-rate must cover every minute of the day [0, 1440)
//   - Date ranges across day-rates must tile every day of the year [1..365]
//   - Days of week must tile every weekday within each date range
//   - No overlapping date ranges
// ──────────────────────────────────────────────────────────────────────────

/** One contiguous chunk of minutes within a day at a fixed c/kWh price. */
data class RateBand(
    val beginMinute: Int,
    val endMinute: Int,    // exclusive (1440 = midnight)
    val price: Double
) {
    val durationMinutes: Int get() = (endMinute - beginMinute).coerceAtLeast(0)
}

/** UI-only representation of a day-rate row. */
data class DayRateBuilder(
    val id: Long = nextLocalId(),
    val startDate: String = "01/01",
    val endDate: String = "12/31",
    val daysOfWeek: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6),  // 0=Sun … 6=Sat
    val bands: List<RateBand> = listOf(RateBand(0, 1440, 10.0))
) {
    companion object {
        private var counter = 0L
        fun nextLocalId(): Long = --counter   // negative — won't clash with DB ids
    }
}

/** Validation outcome for one day-rate. Gaps are uncovered (begin, end] minute ranges.
 *  [dateRangeInvalid] is a string resource id — the composable display site resolves it. */
data class DayRateIssues(
    val gaps: List<IntRange> = emptyList(),
    val overlaps: List<IntRange> = emptyList(),
    @StringRes val dateRangeInvalid: Int? = null
) {
    val isClean: Boolean get() = gaps.isEmpty() && overlaps.isEmpty() && dateRangeInvalid == null
}

/**
 * UI representation of one tiered-usage restriction: once [kwhLimit] kWh have
 * been bought at [rate] within the [period], further usage at that rate is
 * charged [revisedPrice] instead. NB the model keys restrictions by the rate
 * VALUE (c/kWh as a string) — editing a band's price breaks the link, so the
 * editor offers the plan's current rate values in a dropdown.
 */
data class RestrictionEntryBuilder(
    val id: Long = DayRateBuilder.nextLocalId(),
    val period: String = "Annual",      // Annual / Monthly / Bimonthly
    val rate: String = "",              // rate value the restriction attaches to
    val kwhLimit: Int = 0,
    val revisedPrice: Double = 0.0
)

/** Whole-plan validation. The day-rate map keys the [DayRateBuilder.id]. */
data class PlanIssues(
    val supplierBlank: Boolean = false,
    val planNameBlank: Boolean = false,
    val perDayRate: Map<Long, DayRateIssues> = emptyMap(),
    val dateRangeOverlap: Boolean = false,
    val datesMissing: List<IntRange> = emptyList(),    // uncovered day-of-year ranges
    val weekdaysMissing: Map<Long, Set<Int>> = emptyMap()
) {
    val isClean: Boolean get() = !supplierBlank && !planNameBlank &&
        perDayRate.values.all { it.isClean } && !dateRangeOverlap &&
        datesMissing.isEmpty() && weekdaysMissing.values.all { it.isEmpty() }
}

/** Top-level wizard state. Persisted on Save. */
data class PricePlanBuilder(
    val pricePlanId: Long = 0L,
    val supplier: String = "",
    val planName: String = "",
    val reference: String = "",
    val feed: Double = 0.0,            // c/kWh paid for export
    val standingCharges: Double = 0.0, // € / year
    val signUpBonus: Double = 0.0,     // € one-off
    val deemedExport: Boolean = false,
    val lastUpdate: String = "",
    // ISO 3166-1 alpha-2 country the supplier operates in; "" = everywhere.
    // Drives the phone-location filter in the plan list.
    val location: String = "",
    val dayRates: List<DayRateBuilder> = listOf(DayRateBuilder()),
    // Tiered-usage restrictions (e.g. Octopus Zero: 4000 kWh/year at the base
    // rate, then a different unit price). Costing applies them via RateLookup.
    val restrictionsActive: Boolean = false,
    val restrictionEntries: List<RestrictionEntryBuilder> = emptyList(),
    // Supplier terms of a dynamic (wholesale-tracking) plan. Non-null gates the
    // whole rate editor: the 365 generated day-rates are never loaded into
    // builders or hand-edited — the terms card regenerates them instead.
    val dynamicTerms: DynamicTerms? = null
) {
    val isDynamic: Boolean get() = dynamicTerms != null
    /** Distinct rate values (c/kWh) across all day-rate bands — the values a restriction can attach to. */
    val uniqueRates: List<Double>
        get() = dayRates.flatMap { dr -> dr.bands.map { it.price } }.distinct().sorted()
}

enum class PricePlanSaveResult { Idle, Saving, Saved, Failed }

@HiltViewModel
class UI2PricePlanViewModel @Inject constructor(
    application: Application,
    private val repository: ToutcRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val pricePlanId: Long = savedStateHandle["PricePlanID"] ?: -1L
    val isEditMode: Boolean get() = pricePlanId != -1L

    private val _builder = MutableStateFlow(PricePlanBuilder())
    val builder = _builder.asStateFlow()

    private val _isLoading = MutableStateFlow(isEditMode)
    val isLoading: LiveData<Boolean> = _isLoading.asLiveData()

    private val _expandedSections = MutableStateFlow(setOf("details"))
    val expandedSections: LiveData<Set<String>> = _expandedSections.asLiveData()

    /** Day-rate ids currently expanded inside the Day-rates accordion. */
    private val _expandedDayRates = MutableStateFlow(setOf<Long>())
    val expandedDayRates: LiveData<Set<Long>> = _expandedDayRates.asLiveData()

    private val _saveResult = MutableStateFlow(PricePlanSaveResult.Idle)
    val saveResult: LiveData<PricePlanSaveResult> = _saveResult.asLiveData()

    init {
        if (isEditMode) loadExisting()
    }

    private fun loadExisting() {
        viewModelScope.launch(Dispatchers.IO) {
            val plans = repository.allPricePlansNow.orEmpty()
            val plan = plans.firstOrNull { it.pricePlanIndex == pricePlanId }
            val rates = if (plan != null) repository.getAllDayRatesForPricePlanID(pricePlanId) else emptyList()
            if (plan != null) {
                _builder.value = plan.toBuilder(rates)
                // Expand the first day-rate on load so the user sees the redesigned editor immediately.
                _expandedDayRates.value = setOfNotNull(_builder.value.dayRates.firstOrNull()?.id)
            }
            _isLoading.value = false
        }
    }

    fun updateBuilder(transform: (PricePlanBuilder) -> PricePlanBuilder) {
        _builder.update(transform)
    }

    fun toggleSection(id: String) {
        _expandedSections.update { if (it.contains(id)) it - id else it + id }
    }

    fun toggleDayRate(id: Long) {
        _expandedDayRates.update { if (it.contains(id)) it - id else it + id }
    }

    fun addDayRate() {
        val newRate = DayRateBuilder()
        _builder.update { b -> b.copy(dayRates = b.dayRates + newRate) }
        _expandedDayRates.update { it + newRate.id }
    }

    fun removeDayRate(id: Long) {
        _builder.update { b ->
            b.copy(dayRates = b.dayRates.filterNot { it.id == id }.ifEmpty { listOf(DayRateBuilder()) })
        }
        _expandedDayRates.update { it - id }
    }

    fun updateDayRate(id: Long, transform: (DayRateBuilder) -> DayRateBuilder) {
        _builder.update { b ->
            b.copy(dayRates = b.dayRates.map { if (it.id == id) transform(it) else it })
        }
    }

    fun setRestrictionsActive(active: Boolean) {
        _builder.update { it.copy(restrictionsActive = active) }
    }

    fun addRestriction() {
        _builder.update { b ->
            val defaultRate = b.uniqueRates.firstOrNull()?.let { formatRateValue(it) } ?: ""
            b.copy(
                restrictionsActive = true,
                restrictionEntries = b.restrictionEntries + RestrictionEntryBuilder(rate = defaultRate)
            )
        }
    }

    fun removeRestriction(id: Long) {
        _builder.update { b ->
            b.copy(restrictionEntries = b.restrictionEntries.filterNot { it.id == id })
        }
    }

    fun updateRestriction(id: Long, transform: (RestrictionEntryBuilder) -> RestrictionEntryBuilder) {
        _builder.update { b ->
            b.copy(restrictionEntries = b.restrictionEntries.map {
                if (it.id == id) transform(it) else it
            })
        }
    }

    fun save(runCosting: Boolean) {
        val issues = validate(_builder.value)
        if (!issues.isClean) {
            _saveResult.value = PricePlanSaveResult.Failed
            return
        }
        _saveResult.value = PricePlanSaveResult.Saving
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (plan, rates) = _builder.value.toEntities()
                if (isEditMode) {
                    // Dynamic plans carry no rate builders. A materialised plan
                    // (terms.year set) passes its stored rows through unchanged
                    // (preserving their BUY/SELL tags) so a scalar edit
                    // (name/standing/feed) can't strip the generated rates —
                    // terms changes go through regenerate() instead. A plan
                    // whose prices are still pending — including one just
                    // converted from fixed rates — saves terms-only, dropping
                    // any leftover fixed-rate builders.
                    val effectiveRates = when {
                        !_builder.value.isDynamic -> rates
                        _builder.value.dynamicTerms?.year != null ->
                            repository.getAllDayRatesForPricePlanID(pricePlanId)
                        else -> emptyList()
                    }
                    repository.updatePricePlan(plan, ArrayList(effectiveRates))
                    // Editing a plan invalidates every costing computed against it
                    // (across all scenarios) — otherwise the dashboard / Compare
                    // tab would show stale figures until something else forced a
                    // recompute. CostingWorker skips combinations where a costing
                    // already exists, so the stale rows must be deleted here.
                    // (insert(clobber=false) needs no cleanup — a new plan has none.)
                    repository.removeCostingsForPricePlan(plan.pricePlanIndex)
                } else {
                    // A new dynamic plan has no fetched prices yet — insert
                    // terms-only (a pending plan); rate builders left over from
                    // before a dynamic conversion are ignored.
                    val insertRates = if (_builder.value.isDynamic) emptyList() else rates
                    repository.insert(plan, insertRates, /* clobber = */ false)
                }
                // Recompute immediately on every save, not just on "Run": editing
                // a plan just invalidated its costings (and a new plan has none
                // yet), so kick the worker chain now rather than deferring to the
                // next navigation. Less aggressive than the legacy per-component
                // delete-and-recompute, but explicit.
                withContext(Dispatchers.Main) {
                    com.tfcode.comparetout.SimulatorLauncher
                        .simulateIfNeeded(getApplication())
                }
                _saveResult.value = PricePlanSaveResult.Saved
            } catch (t: Throwable) {
                _saveResult.value = PricePlanSaveResult.Failed
            }
        }
    }

    /**
     * Replace the wizard's plan/charges/day-rates with the contents of a
     * parsed PricePlanJsonFile. The DB ids (pricePlanId, dayRate ids) are
     * intentionally NOT touched in edit mode — so importing into an existing
     * plan overwrites its rates but keeps it pointing at the same row, while
     * importing into a new plan inherits whatever JSON named it.
     */
    fun loadFromJson(json: PricePlanJsonFile) {
        val parsedPlan = JsonTools.createPricePlan(json)
        // Build day-rate builders directly from the JSON so that the source's
        // precedence between minuteRange and hours is preserved verbatim:
        //   - If the JSON specifies a minuteRange (non-null), use it as the
        //     single source of truth — ignore the hours field entirely.
        //   - Only fall back to MinuteRateRange.fromHours(hours) when the JSON
        //     has no minuteRange at all (legacy exports pre-dating it).
        // Day-rate ids are reset to local negatives so saving never collides
        // with the source plan's DB rows.
        val dayRateBuilders = json.rates.orEmpty().map { drj ->
            val bands: List<RateBand> = when {
                drj.minuteRange != null -> drj.minuteRange.map {
                    RateBand(it.startMinute, it.endMinute, it.cost)
                }
                drj.hours != null -> {
                    val dh = DoubleHolder().apply { doubles = drj.hours }
                    MinuteRateRange.fromHours(dh).rates.map {
                        RateBand(it.begin, it.end, it.price)
                    }
                }
                else -> emptyList()
            }.ifEmpty { listOf(RateBand(0, 1440, 10.0)) }
            DayRateBuilder(
                id = DayRateBuilder.nextLocalId(),
                startDate = drj.startDate ?: "01/01",
                endDate = drj.endDate ?: "12/31",
                daysOfWeek = drj.days?.toSet() ?: setOf(0, 1, 2, 3, 4, 5, 6),
                bands = bands
            )
        }.ifEmpty { listOf(DayRateBuilder()) }
        _builder.update { current ->
            PricePlanBuilder(
                // Keep the existing DB binding so re-importing into an Edit
                // session updates the same row instead of creating a new one.
                pricePlanId = current.pricePlanId,
                supplier = parsedPlan.supplier ?: "",
                planName = parsedPlan.planName ?: "",
                reference = parsedPlan.reference ?: "<REFERENCE>",
                feed = parsedPlan.feed,
                standingCharges = parsedPlan.standingCharges,
                signUpBonus = parsedPlan.signUpBonus,
                deemedExport = parsedPlan.isDeemedExport,
                lastUpdate = parsedPlan.lastUpdate ?: "",
                location = parsedPlan.location,
                dayRates = if (parsedPlan.isDynamic) emptyList() else dayRateBuilders,
                restrictionsActive = parsedPlan.restrictions?.isActive == true,
                restrictionEntries = parsedPlan.restrictions.toEntryBuilders(),
                dynamicTerms = parsedPlan.dynamicTerms
            )
        }
        // Expand the day-rates accordion so the user immediately sees what
        // was loaded; default-collapse it otherwise feels like the import
        // silently did nothing.
        _expandedSections.value += "rates"
        _expandedDayRates.value =
            setOfNotNull(_builder.value.dayRates.firstOrNull()?.id)
    }

    /**
     * The dynamic plan's only rate-mutation path: apply edited terms + year by
     * regenerating. A terms-only plan JSON goes to [DynamicTariffWorker], whose
     * clobbering insert replaces this plan's row and its generated rates; the
     * screen closes like a save and the notification tracks the fetch.
     */
    fun regenerate(newTerms: DynamicTerms, year: Int) {
        val b = _builder.value
        val ppj = PricePlanJsonFile()
        ppj.supplier = b.supplier
        ppj.plan = b.planName
        ppj.standingCharges = b.standingCharges
        ppj.feed = b.feed
        ppj.bonus = b.signUpBonus
        ppj.active = true
        ppj.location = b.location
        val terms = com.tfcode.comparetout.model.json.priceplan.DynamicTermsJson()
        terms.market = newTerms.market
        terms.year = year
        terms.multiplier = newTerms.multiplier
        terms.adder = newTerms.adder
        terms.cap = newTerms.cap
        terms.floor = newTerms.floor
        terms.feedMultiplier = newTerms.feedMultiplier
        terms.feedAdder = newTerms.feedAdder
        ppj.dynamic = terms
        DynamicTariffWorker.enqueue(
            getApplication(), com.google.gson.Gson().toJson(ppj), b.planName, year)
        _saveResult.value = PricePlanSaveResult.Saved
    }

    /**
     * Convert the plan being edited into a dynamic (wholesale-tracking) one:
     * attach terms for [marketId] so the wizard swaps the Rates/Restrictions
     * sections for the Dynamic terms card. The fixed-rate builders are kept on
     * the builder (they are ignored while dynamic and restored by
     * [clearDynamic]) but are never persisted — save() strips them until the
     * prices materialise.
     */
    fun makeDynamic(marketId: String) {
        _builder.update { b ->
            b.copy(dynamicTerms = DynamicTerms().apply {
                market = marketId
                multiplier = 1.0
                adder = 0.0
            })
        }
        _expandedSections.update { it - "make-dynamic" + "dynamic" }
    }

    /**
     * Undo [makeDynamic] while the prices are still pending (terms.year unset):
     * drop the terms and return to the fixed-rate editor. Not offered once a
     * plan has materialised — its generated rates are not convertible back.
     */
    fun clearDynamic() {
        _builder.update { b ->
            b.copy(
                dynamicTerms = null,
                dayRates = b.dayRates.ifEmpty { listOf(DayRateBuilder()) }
            )
        }
        _expandedSections.update { it - "dynamic" + "rates" }
    }
}

// ── validation ──────────────────────────────────────────────────────────────

/**
 * Run the same checks as PricePlan.validatePlan, but produce structured
 * messages so the UI can highlight every offending field rather than show one
 * line at the bottom.
 */
fun validate(b: PricePlanBuilder): PlanIssues {
    val supplierBlank = b.supplier.isBlank()
    val planNameBlank = b.planName.isBlank()

    // Dynamic plans carry no editable day-rates — the generated set already
    // passed the model's checks at materialisation (or is pending download).
    if (b.isDynamic) {
        return PlanIssues(supplierBlank = supplierBlank, planNameBlank = planNameBlank)
    }

    val drIssues = mutableMapOf<Long, DayRateIssues>()
    for (dr in b.dayRates) {
        drIssues[dr.id] = validateDayRate(dr)
    }

    // ── date-range tiling: do the [startDate..endDate] ranges cover 1..365 with no overlap?
    val daysCovered = IntArray(366) // 1..365
    var overlap = false
    for (dr in b.dayRates) {
        val (start, end) = parseDateRange(dr.startDate, dr.endDate) ?: continue
        for (d in start..end) {
            if (daysCovered[d] > 0) overlap = true
            daysCovered[d] = daysCovered[d] + 1
        }
    }
    val gaps = mutableListOf<IntRange>()
    var i = 1
    while (i <= 365) {
        if (daysCovered[i] == 0) {
            val from = i
            // We already know daysCovered[from] == 0; scan forward from from+1
            // to find the first covered day. This makes the resulting range
            // from..<to provably non-empty (to >= from+1).
            var to = from + 1
            while (to <= 365 && daysCovered[to] == 0) to++
            gaps += from..<to
            i = to
        } else i++
    }

    // ── weekday tiling within each date range: collect the days of week that each
    //    date range's day-rates together cover; missing → user is told.
    val weekdaysMissing = mutableMapOf<Long, Set<Int>>()
    val rangeKey: (DayRateBuilder) -> String = { "${it.startDate}->${it.endDate}" }
    val byRange = b.dayRates.groupBy(rangeKey)
    for ((_, group) in byRange) {
        val union = group.flatMap { it.daysOfWeek }.toSet()
        if (union.size < 7) {
            val missing = (0..6).toSet() - union
            for (dr in group) weekdaysMissing[dr.id] = missing
        }
    }

    return PlanIssues(
        supplierBlank = supplierBlank,
        planNameBlank = planNameBlank,
        perDayRate = drIssues,
        dateRangeOverlap = overlap,
        datesMissing = gaps,
        weekdaysMissing = weekdaysMissing
    )
}

fun validateDayRate(dr: DayRateBuilder): DayRateIssues {
    val dateInvalid = when {
        !dr.startDate.matches(Regex("\\d{1,2}/\\d{1,2}")) -> R.string.ui2_ppw_err_start_mmdd
        !dr.endDate.matches(Regex("\\d{1,2}/\\d{1,2}"))   -> R.string.ui2_ppw_err_end_mmdd
        else -> {
            val parsed = parseDateRange(dr.startDate, dr.endDate)
            if (parsed == null) R.string.ui2_ppw_err_invalid_date else if (parsed.second < parsed.first)
                R.string.ui2_ppw_err_end_before_start else null
        }
    }
    // Coverage check over [0, 1440) — overlaps and gaps both matter.
    val gaps = mutableListOf<IntRange>()
    val overlaps = mutableListOf<IntRange>()
    val sorted = dr.bands
        .filter { it.beginMinute in 0..1440 && it.endMinute in 0..1440 && it.beginMinute < it.endMinute }
        .sortedBy { it.beginMinute }
    var cursor = 0
    for (b in sorted) {
        when {
            b.beginMinute > cursor -> gaps += cursor until b.beginMinute
            b.beginMinute < cursor -> overlaps += b.beginMinute until cursor
        }
        cursor = maxOf(cursor, b.endMinute)
    }
    if (cursor < 1440) gaps += cursor until 1440
    return DayRateIssues(gaps = gaps, overlaps = overlaps, dateRangeInvalid = dateInvalid)
}

/** Parse "MM/DD" → day-of-year [1..365]. Feb 29 collapses onto Feb 28. */
private fun parseDateRange(s: String, e: String): Pair<Int, Int>? {
    val sd = parseMmDd(s) ?: return null
    val ed = parseMmDd(e) ?: return null
    return sd to ed
}

private fun parseMmDd(text: String): Int? {
    val parts = text.split("/")
    if (parts.size != 2) return null
    val m = parts[0].toIntOrNull() ?: return null
    val d = parts[1].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return try {
        var ld = java.time.LocalDate.of(2001, m, d)
        if (m == 2 && d == 29) ld = java.time.LocalDate.of(2001, 2, 28)
        ld.dayOfYear
    } catch (_: Throwable) { null }
}

// ── conversions ─────────────────────────────────────────────────────────────

private fun PricePlan.toBuilder(rates: List<DayRate>): PricePlanBuilder = PricePlanBuilder(
    pricePlanId = pricePlanIndex,
    supplier = supplier,
    planName = planName,
    reference = reference,
    feed = feed,
    standingCharges = standingCharges,
    signUpBonus = signUpBonus,
    deemedExport = isDeemedExport,
    lastUpdate = lastUpdate,
    location = location,
    // A dynamic plan's 365 generated rates never become editor cards — the
    // terms card is the only mutation path; save passes the rates through.
    dayRates = when {
        isDynamic -> emptyList()
        rates.isEmpty() -> listOf(DayRateBuilder())
        else -> rates.map { it.toBuilder() }
    },
    restrictionsActive = restrictions?.isActive == true,
    restrictionEntries = restrictions.toEntryBuilders(),
    dynamicTerms = dynamicTerms
)

/**
 * Render a rate value the way restriction keys store it. RateLookup parses the
 * key back with Double.parseDouble, so any canonical decimal form works —
 * trim a trailing ".0" for readability.
 */
fun formatRateValue(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** Flatten the model's grouped-by-period restrictions into editable rows. */
private fun Restrictions?.toEntryBuilders(): List<RestrictionEntryBuilder> =
    this?.restrictions.orEmpty().flatMap { r ->
        r.restrictionEntries.entries.map { (rate, pair) ->
            RestrictionEntryBuilder(
                period = r.periodicity?.value ?: "Annual",
                rate = rate,
                kwhLimit = pair.first ?: 0,
                revisedPrice = pair.second ?: 0.0
            )
        }
    }

/** Regroup the editor rows by period into the model shape RateLookup consumes. */
private fun buildRestrictions(active: Boolean, entries: List<RestrictionEntryBuilder>): Restrictions {
    val out = Restrictions()
    val valid = entries.filter { it.rate.isNotBlank() && it.kwhLimit > 0 }
    out.isActive = active && valid.isNotEmpty()
    val list = ArrayList<com.tfcode.comparetout.model.priceplan.Restriction>()
    valid.groupBy { it.period }.forEach { (period, group) ->
        val type = runCatching {
            com.tfcode.comparetout.model.priceplan.Restriction.RestrictionType.fromValue(period)
        }.getOrNull() ?: return@forEach
        val r = com.tfcode.comparetout.model.priceplan.Restriction()
        group.forEach { e -> r.addEntry(type, e.rate, e.kwhLimit, e.revisedPrice) }
        list.add(r)
    }
    out.restrictions = list
    return out
}

private fun DayRate.toBuilder(): DayRateBuilder {
    // Prefer the minute-precision rate range. Legacy plans (and plans saved by
    // the broken pre-fix UI2 wizard) may have an empty MinuteRateRange but a
    // populated hourly snapshot — fall back to it the same way RateLookup does
    // so the wizard reflects the real rates instead of a default 24h 10c band.
    val effective = if (minuteRateRange?.rates?.isNotEmpty() == true) minuteRateRange
                    else MinuteRateRange.fromHours(hours)
    val bands = effective.rates
        .sortedBy { it.begin }
        .map { RateBand(it.begin, it.end, it.price) }
        .ifEmpty { listOf(RateBand(0, 1440, 10.0)) }
    return DayRateBuilder(
        id = if (dayRateIndex > 0L) dayRateIndex else DayRateBuilder.nextLocalId(),
        startDate = startDate,
        endDate = endDate,
        daysOfWeek = days.ints.toSet(),
        bands = bands
    )
}

private fun PricePlanBuilder.toEntities(): Pair<PricePlan, List<DayRate>> {
    val plan = PricePlan().apply {
        pricePlanIndex = this@toEntities.pricePlanId
        supplier = this@toEntities.supplier
        planName = this@toEntities.planName
        feed = this@toEntities.feed
        standingCharges = this@toEntities.standingCharges
        signUpBonus = this@toEntities.signUpBonus
        reference = this@toEntities.reference
        isDeemedExport = this@toEntities.deemedExport
        location = this@toEntities.location.trim().uppercase()
        isActive = true   // UI2 does not surface the "inactive" flag; always active.
        restrictions = buildRestrictions(
            this@toEntities.restrictionsActive, this@toEntities.restrictionEntries)
        dynamicTerms = this@toEntities.dynamicTerms
    }
    val rates = dayRates.map { dr ->
        DayRate().apply {
            // Preserve the DB-assigned id (positive) so the DAO's @Upsert performs
            // an UPDATE rather than DELETE+INSERT; locally-allocated ids (negative)
            // are reset to 0 to let SQLite auto-generate a new id on insert.
            if (dr.id > 0L) dayRateIndex = dr.id
            pricePlanId = this@toEntities.pricePlanId
            startDate = dr.startDate
            endDate = dr.endDate
            days = IntHolder().also { h -> h.ints = dr.daysOfWeek.sorted().toMutableList() }
            // Build MinuteRateRange directly from the band list — minute precision
            // round-trips here, so a 06:00–06:30 dynamic-pricing slot survives Save.
            // NB: use add(), not insert(). insert()'s for-loop body never executes
            // when mRates is empty, so the very first band would be dropped on the
            // floor and the saved plan would end up with zero rates.
            val mrr = MinuteRateRange()
            for (b in dr.bands.sortedBy { it.beginMinute }) {
                mrr.add(b.beginMinute, b.endMinute, b.price)
            }
            minuteRateRange = mrr
            // `hours` is now legacy book-keeping only. Derive 25 hourly snapshots so
            // any path still using DoubleHolder sees a plausible value.
            val dh = DoubleHolder()
            for (h in 0..24) dh.doubles[h] = mrr.lookup(h * 60)
            hours = dh
        }
    }
    return plan to rates
}
