package com.tfcode.comparetout.ui2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.tfcode.comparetout.model.IntHolder
import com.tfcode.comparetout.model.ToutcRepository
import com.tfcode.comparetout.model.json.JsonTools
import com.tfcode.comparetout.model.json.priceplan.PricePlanJsonFile
import com.tfcode.comparetout.model.priceplan.DayRate
import com.tfcode.comparetout.model.priceplan.DoubleHolder
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

/** Validation outcome for one day-rate. Gaps are uncovered (begin, end] minute ranges. */
data class DayRateIssues(
    val gaps: List<IntRange> = emptyList(),
    val overlaps: List<IntRange> = emptyList(),
    val dateRangeInvalid: String? = null
) {
    val isClean: Boolean get() = gaps.isEmpty() && overlaps.isEmpty() && dateRangeInvalid == null
}

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
    val dayRates: List<DayRateBuilder> = listOf(DayRateBuilder()),
    // Restrictions are not yet editable in UI2 — round-trip the legacy blob so
    // plans created in the legacy editor preserve their tiered-usage caps.
    val restrictions: Restrictions? = null
) {
    val isComplete: Boolean
        get() = supplier.isNotBlank() && planName.isNotBlank() && dayRates.isNotEmpty()
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
                    repository.updatePricePlan(plan, ArrayList(rates))
                } else {
                    repository.insert(plan, rates, /* clobber = */ false)
                }
                if (runCosting) {
                    withContext(Dispatchers.Main) {
                        com.tfcode.comparetout.SimulatorLauncher
                            .simulateIfNeeded(getApplication())
                    }
                }
                _saveResult.value = PricePlanSaveResult.Saved
            } catch (t: Throwable) {
                _saveResult.value = PricePlanSaveResult.Failed
            }
        }
    }

    fun acknowledgeSaveResult() {
        _saveResult.value = PricePlanSaveResult.Idle
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
                dayRates = dayRateBuilders,
                restrictions = parsedPlan.restrictions
            )
        }
        // Expand the day-rates accordion so the user immediately sees what
        // was loaded; default-collapse it otherwise feels like the import
        // silently did nothing.
        _expandedSections.value += "rates"
        _expandedDayRates.value =
            setOfNotNull(_builder.value.dayRates.firstOrNull()?.id)
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
        !dr.startDate.matches(Regex("\\d{1,2}/\\d{1,2}")) -> "Start date must be MM/DD"
        !dr.endDate.matches(Regex("\\d{1,2}/\\d{1,2}"))   -> "End date must be MM/DD"
        else -> {
            val parsed = parseDateRange(dr.startDate, dr.endDate)
            if (parsed == null) "Invalid date" else if (parsed.second < parsed.first)
                "End date must come after start date" else null
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
    dayRates = if (rates.isEmpty()) listOf(DayRateBuilder()) else rates.map { it.toBuilder() },
    restrictions = restrictions
)

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
        isActive = true   // UI2 does not surface the "inactive" flag; always active.
        this@toEntities.restrictions?.let { restrictions = it }
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
