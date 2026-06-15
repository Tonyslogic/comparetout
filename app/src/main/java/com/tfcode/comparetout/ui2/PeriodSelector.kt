package com.tfcode.comparetout.ui2

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─── The shared "D / M / Y / *  < >" date selector ──────────────────────────
//
// Extracted from UI2DashboardFragment so every accordion (and any future host)
// renders an identical control and shares the same transition behaviour. The
// body delegates layout to [AdaptivePeriodControl] so the public API here is
// unchanged — every call site automatically retiers under font scaling.

private val DAY_FMT         = DateTimeFormatter.ofPattern("dd/MM/yy")
private val MONTH_FMT       = DateTimeFormatter.ofPattern("MMM yy")
private val RANGE_FMT       = DateTimeFormatter.ofPattern("d MMM yy")
private val RANGE_FMT_SHORT = DateTimeFormatter.ofPattern("d MMM")

/**
 * Inclusive `[from, to]` date range covered by [period] anchored at [anchor].
 *
 * In **basic** mode `MONTH`/`YEAR` snap to the natural calendar boundary
 * (whole calendar month / whole calendar year).
 *
 * In **advanced** mode they are *trailing windows* that **end** on [anchor] and
 * reach back one month / one year — i.e. "the selected day is the last day of
 * the selection". A trailing window therefore routinely spans more than one
 * calendar month or year (see [PeriodSelector]'s range label).
 */
fun periodDateRange(
    period: DataSourcePeriod,
    anchor: LocalDate,
    advanced: Boolean,
    dataStart: LocalDate,
    dataEnd: LocalDate
): Pair<LocalDate, LocalDate> = when (period) {
    DataSourcePeriod.YESTERDAY -> anchor to anchor
    DataSourcePeriod.MONTH ->
        if (advanced) anchor.minusMonths(1).plusDays(1) to anchor
        else anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
    DataSourcePeriod.YEAR ->
        if (advanced) anchor.minusYears(1).plusDays(1) to anchor
        else LocalDate.of(anchor.year, 1, 1) to LocalDate.of(anchor.year, 12, 31)
    DataSourcePeriod.ALL -> dataStart to dataEnd
}

/**
 * The anchor to adopt when the user switches from [oldPeriod] to [newPeriod].
 *
 * The rule is uniform: the new anchor is the **end of the old period's range**,
 * clamped to the available data. Because [periodDateRange] already encodes the
 * basic/advanced distinction, this single rule yields every requested case:
 *
 *  - **Basic** — the anchor moves to a calendar boundary. `y -> m` lands on the
 *    last month of the year, `m -> d` on the last day of the month, …; `d -> m`
 *    / `d -> y` keep the day within the new month/year boundary.
 *  - **Advanced** — every period's range ends on the anchor, so the anchor is
 *    *preserved* across the switch and only the window width changes. Picking
 *    `Y` after landing on a day gives the trailing year ending on that day
 *    ("select a year backwards from the selected day").
 */
fun transitionAnchor(
    oldPeriod: DataSourcePeriod,
    anchor: LocalDate,
    newPeriod: DataSourcePeriod,
    advanced: Boolean,
    dataStart: LocalDate,
    dataEnd: LocalDate
): LocalDate {
    if (newPeriod == oldPeriod) return anchor.coerceIn(dataStart, dataEnd)
    val (_, oldEnd) = periodDateRange(oldPeriod, anchor, advanced, dataStart, dataEnd)
    return oldEnd.coerceIn(dataStart, dataEnd)
}

/**
 * Shift [anchor] by one [period] step (a day / month / year), clamped to the
 * available data. Backs the `< >` chevrons. `ALL` has nothing to step, so the
 * anchor is returned unchanged.
 */
fun stepAnchor(
    anchor: LocalDate,
    period: DataSourcePeriod,
    forward: Boolean,
    dataStart: LocalDate,
    dataEnd: LocalDate
): LocalDate {
    val step = if (forward) 1L else -1L
    val moved = when (period) {
        DataSourcePeriod.YESTERDAY -> anchor.plusDays(step)
        DataSourcePeriod.MONTH     -> anchor.plusMonths(step)
        DataSourcePeriod.YEAR      -> anchor.plusYears(step)
        DataSourcePeriod.ALL       -> anchor
    }
    return moved.coerceIn(dataStart, dataEnd)
}

/** A "15 Feb 22 – 14 Feb 23" style label for a selection that spans calendars. */
private fun spanLabel(from: LocalDate, to: LocalDate): String {
    val start = if (from.year == to.year) from.format(RANGE_FMT_SHORT) else from.format(RANGE_FMT)
    return "$start – ${to.format(RANGE_FMT)}"
}

/** Long label used by the Tier-C dropdown trigger / menu items. */
private fun periodLongLabel(p: DataSourcePeriod): String = when (p) {
    DataSourcePeriod.YESTERDAY -> "Day"
    DataSourcePeriod.MONTH     -> "Month"
    DataSourcePeriod.YEAR      -> "Year"
    DataSourcePeriod.ALL       -> "All time"
}

/**
 * The "D / M / Y / *  < >" period selector.
 *
 * The label between the chevrons shows a single token (day / month / year) for
 * a selection that sits inside one calendar unit, and the full **from – to
 * range** whenever the selection spans more than one — i.e. advanced trailing
 * windows and `*` (All).
 *
 * @param advanced supplied by the enclosing tab / panel / accordion. When the
 *        host has no Basic/Advanced control it simply omits this and the
 *        default (`false`, i.e. Basic) applies. The flag is forwarded verbatim
 *        through both callbacks so the host need not track it separately.
 * @param onPeriodChange invoked with the new period and the context-preserving
 *        anchor computed by [transitionAnchor].
 */
@Composable
fun PeriodSelector(
    selectedPeriod: DataSourcePeriod,
    anchorDate: LocalDate,
    dataStart: String,
    dataEnd: String,
    advanced: Boolean = false,
    onPeriodChange: (period: DataSourcePeriod, anchor: LocalDate, advanced: Boolean) -> Unit,
    onNavigate: (forward: Boolean, advanced: Boolean) -> Unit
) {
    val startDate = remember(dataStart) { LocalDate.parse(dataStart) }
    val endDate   = remember(dataEnd)   { LocalDate.parse(dataEnd) }
    val showNav = selectedPeriod != DataSourcePeriod.ALL
    val (rangeStart, rangeEnd) = periodDateRange(selectedPeriod, anchorDate, advanced, startDate, endDate)
    val dateLabel = when (selectedPeriod) {
        DataSourcePeriod.YESTERDAY -> anchorDate.format(DAY_FMT)
        DataSourcePeriod.MONTH ->
            if (advanced) spanLabel(rangeStart, rangeEnd) else anchorDate.format(MONTH_FMT)
        DataSourcePeriod.YEAR ->
            if (advanced) spanLabel(rangeStart, rangeEnd) else anchorDate.year.toString()
        DataSourcePeriod.ALL -> spanLabel(rangeStart, rangeEnd)
    }
    val atStart = showNav && anchorDate <= startDate
    val atEnd   = showNav && anchorDate >= endDate

    AdaptivePeriodControl(
        segments = DataSourcePeriod.values().toList(),
        selected = selectedPeriod,
        labelFor = { it.label },
        longLabelFor = ::periodLongLabel,
        onSelect = { period ->
            if (period != selectedPeriod) {
                val newAnchor = transitionAnchor(
                    selectedPeriod, anchorDate, period, advanced, startDate, endDate
                )
                onPeriodChange(period, newAnchor, advanced)
            }
        },
        dateSlot = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showNav) {
                    IconButton(
                        onClick = { onNavigate(false, advanced) },
                        enabled = !atStart,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(18.dp))
                    }
                }
                Text(
                    dateLabel, Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall
                )
                if (showNav) {
                    IconButton(
                        onClick = { onNavigate(true, advanced) },
                        enabled = !atEnd,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    )
}
