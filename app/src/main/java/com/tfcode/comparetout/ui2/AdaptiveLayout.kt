package com.tfcode.comparetout.ui2

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import kotlin.math.ceil

// ──────────────────────────────────────────────────────────────────────────
// Shared substrate for zoom- and width-adaptive Compose layouts in UI2.
//
// One source of truth for every adjustable number that drives retiering —
// font-scale tiers for period selectors / cell rows / chip rows, pinned
// column sizing for data tables, width tiers for landscape / tablet
// layouts. Tunables live in [AdaptiveLayout]; helpers (`adaptiveFontScale`,
// `pinnedColumnWidth`, `widthTier`, etc.) below it. Reusable composables
// (`AdaptivePeriodControl`, `AdaptiveCellRow`, `AdaptiveChipRow`,
// `PinnedScrollTable`, `AdaptiveTwoColumn`) sit underneath.
// ──────────────────────────────────────────────────────────────────────────

internal object AdaptiveLayout {
    // Control-row tier breakpoints (driven by LocalDensity.current.fontScale).
    const val CONTROL_TIER_B_AT = 1.25f   // single-row → two-row
    const val CONTROL_TIER_C_AT = 1.60f   // segmented chips → dropdown

    // Pinned-column sizing. ~120 dp at fontScale=1.0, growing with fontScale,
    // capped so very-large fonts can't eat the scroll area entirely.
    val PINNED_BASE_WIDTH = 120.dp
    val PINNED_MAX_WIDTH  = 210.dp

    // Comfortable width of a value-column cell at normal font. Used two ways:
    //   • summed (with the pinned column) to decide whether the whole table
    //     fits the available width → weighted fill, else pin + scroll;
    //   • as the per-column floor in scroll mode, there scaled by fontScale.
    // Numeric columns stay narrow; best/worst-style mixed text needs wider.
    val SCROLL_COL_MIN_NUMERIC = 48.dp
    val SCROLL_COL_MIN_MIXED   = 72.dp

    // Pinned/label column's comfortable width at normal font, used in the
    // fit-vs-scroll test. The scroll-mode pinned width uses pinnedColumnWidth().
    val PINNED_FIT_MIN_WIDTH = 88.dp

    // In scroll mode the pinned column may never eat more than this fraction
    // of the table's available width, so the scrollable values stay readable.
    const val PINNED_MAX_FRACTION = 0.5f

    // Comfortable per-chip width (label + padding + inter-chip gap) at normal
    // font, used by the central [stripLayout] fit test to decide how many
    // chips share a row before wrapping. SHORT = single-letter ("J"),
    // LONG = multi-letter ("Jan"). Both grow with fontScale at the call site.
    // SHORT is tuned so a 13-chip month strip still fits ONE line at the
    // default Display-size (step 1 of 5) and font, yet wraps to two rows from
    // the next Display-size step up. Raise it to make wrapping start sooner.
    val CHIP_MIN_SHORT = 25.dp
    val CHIP_MIN_LONG  = 48.dp

    // Most balanced rows a wrapping chip strip may use before it gives up and
    // collapses to a dropdown. Lower it to make the dropdown appear sooner.
    val CHIP_MAX_WRAP_ROWS = 2

    // Below this measured width (× fontScale) the D/M/Y/* period control drops
    // its date slot onto a second row instead of keeping everything inline.
    // Sized so a normal phone at the default Display-size keeps one row, and a
    // larger Display-size step (or larger font) pushes it to two.
    val PERIOD_ONE_ROW_MIN_WIDTH = 320.dp

    // Minimum 48 dp touch target for tap surfaces in every tier (a11y).
    val MIN_TOUCH = 48.dp

    // Width tiers — driven by BoxWithConstraints.maxWidth.
    val WIDTH_COMPACT_AT = 0.dp
    val WIDTH_MEDIUM_AT  = 480.dp
    val WIDTH_WIDE_AT    = 720.dp
    val WIDTH_ULTRA_AT   = 1000.dp

    // Centred max width for action button rows so they don't span tablets.
    val ACTION_ROW_MAX_WIDTH = 480.dp

    // Cap on full-bleed forms / dialogs so paragraphs don't run to 16in.
    val CONTENT_MAX_WIDTH = 720.dp
}

enum class ControlTier { A, B, C }

enum class WidthTier { COMPACT, MEDIUM, WIDE, ULTRA }

/** Wraps [LocalDensity.current.fontScale] so tests / previews can override. */
@Composable
fun adaptiveFontScale(): Float = LocalDensity.current.fontScale

@Composable
fun controlTier(fontScale: Float = adaptiveFontScale()): ControlTier = when {
    fontScale < AdaptiveLayout.CONTROL_TIER_B_AT -> ControlTier.A
    fontScale < AdaptiveLayout.CONTROL_TIER_C_AT -> ControlTier.B
    else -> ControlTier.C
}

/** (BASE * fs).coerceAtMost(MAX). */
fun pinnedColumnWidth(fontScale: Float): Dp =
    (AdaptiveLayout.PINNED_BASE_WIDTH * fontScale).coerceAtMost(AdaptiveLayout.PINNED_MAX_WIDTH)

/** Resolve the current width tier from a BoxWithConstraints' available width. */
@Composable
fun BoxWithConstraintsScope.widthTier(): WidthTier {
    val w = this.maxWidth
    return when {
        w >= AdaptiveLayout.WIDTH_ULTRA_AT  -> WidthTier.ULTRA
        w >= AdaptiveLayout.WIDTH_WIDE_AT   -> WidthTier.WIDE
        w >= AdaptiveLayout.WIDTH_MEDIUM_AT -> WidthTier.MEDIUM
        else -> WidthTier.COMPACT
    }
}

// ─── Central width-fit detection ──────────────────────────────────────────
//
// THE one place every adaptive strip control (chip rows, cell rows) and the
// data table consult to decide how to lay themselves out. All retiering in
// UI2 is driven from a single question:
//
//     does the content's comfortable width fit the measured available width?
//
// The available width comes from a BoxWithConstraints; the comfortable width
// is the caller's per-item floor grown by fontScale. Screen size, orientation,
// Android Display-size (density) and font scale each move one side of that
// comparison, so this single primitive covers every combination — there is no
// separate "is it landscape" or "is the font big" branch anywhere else.

/** How many fixed-width items of [itemWidth] fit across [available] (min 1). */
fun itemsPerRow(available: Dp, itemWidth: Dp): Int =
    if (itemWidth.value <= 0f) 1
    else (available.value / itemWidth.value).toInt().coerceAtLeast(1)

/** True when [needed] (already fontScale-scaled) fits within [available]. */
fun fitsWidth(available: Dp, needed: Dp): Boolean = available >= needed

/**
 * Result of the central strip fit calculation: either lay the items out in
 * [rows] balanced rows (using long labels when [useLong]), or collapse to a
 * dropdown. The chip strip's four observable states map onto this directly:
 *   • Rows(1, useLong = true)  — extra-wide / landscape: one row, long labels.
 *   • Rows(1, useLong = false) — portrait normal: one row, short labels.
 *   • Rows(2 / 3, ...)         — portrait crowded: wrap to balanced rows.
 *   • Dropdown                 — too crowded for [maxRows]: collapse.
 */
sealed class StripLayout {
    data class Rows(val rows: Int, val useLong: Boolean) : StripLayout()
    object Dropdown : StripLayout()
}

/**
 * Central strip layout decision shared by every dense single-select strip.
 *
 * Prefers long labels when they ALSO fit one row (the room only exists in
 * landscape / on tablets / on small Display-size). Otherwise falls back to
 * short labels and wraps to as few balanced rows as the width allows, finally
 * collapsing to a dropdown past [maxRows].
 *
 * @param available    measured available width (from BoxWithConstraints).
 * @param itemCount    number of items in the strip.
 * @param shortItemWidth comfortable per-item width with the short label.
 * @param longItemWidth  comfortable per-item width with the long label.
 * @param maxRows      most rows allowed before collapsing to a dropdown.
 * @param fontScale    current font scale; both item widths are grown by it.
 */
fun stripLayout(
    available: Dp,
    itemCount: Int,
    shortItemWidth: Dp,
    longItemWidth: Dp,
    maxRows: Int = AdaptiveLayout.CHIP_MAX_WRAP_ROWS,
    fontScale: Float,
): StripLayout {
    if (itemCount <= 0) return StripLayout.Rows(1, useLong = false)
    // Extra-wide / landscape: long labels fit on a single line.
    if (itemsPerRow(available, longItemWidth * fontScale) >= itemCount) {
        return StripLayout.Rows(1, useLong = true)
    }
    // Portrait: short labels, wrapping as the effective width drops.
    val perRow = itemsPerRow(available, shortItemWidth * fontScale)
    if (perRow >= itemCount) return StripLayout.Rows(1, useLong = false)
    val rows = ceil(itemCount.toDouble() / perRow).toInt()
    return if (rows > maxRows) StripLayout.Dropdown
           else StripLayout.Rows(rows, useLong = false)
}

// ─── AdaptivePeriodControl ────────────────────────────────────────────────
//
// Segmented "D / M / Y / *" picker. Public API is generic so the same
// composable serves [PeriodSelector] (where T is DataSourcePeriod) and
// [CompareScreen.RangePicker] (where T is a Compare granularity enum, and
// selection can be toggled off → null).
//
// Retiers through the same central width-fit principle as the chip strip,
// giving the "3 portrait + extra-wide/landscape" states:
//   • landscape / extra-wide → one row, full segment names ("Day" / "Month").
//   • portrait normal        → one row, single-letter codes + inline date slot.
//   • portrait crowded       → two rows: segments share the width, date below.
//   • largest font (Tier C)  → dropdown (accessibility), date slot below.
// The one-vs-two-row split is driven by the MEASURED width (so a larger
// Display-size pushes it to two rows), not fontScale alone.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AdaptivePeriodControl(
    segments: List<T>,
    selected: T?,
    labelFor: (T) -> String,
    longLabelFor: (T) -> String,
    onSelect: (T) -> Unit,
    dateSlot: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val fontScale = adaptiveFontScale()
    // Largest font tier collapses to a dropdown regardless of width (a11y).
    if (controlTier(fontScale) == ControlTier.C) {
        Column(modifier = modifier.fillMaxWidth()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selected?.let { longLabelFor(it) } ?: "—",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    segments.forEach { seg ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    longLabelFor(seg),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = { onSelect(seg); expanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth()) { dateSlot() }
        }
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // One row only while the segments + date slot comfortably fit. The
        // needed width grows with fontScale, so a larger Display-size (less
        // effective width) AND a larger font both push it to two rows.
        val oneRow = fitsWidth(maxWidth, AdaptiveLayout.PERIOD_ONE_ROW_MIN_WIDTH * fontScale)
        if (oneRow) {
            // In landscape (MEDIUM+) there's room for full segment names.
            val useLong = maxWidth >= AdaptiveLayout.WIDTH_MEDIUM_AT
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                segments.forEach { seg ->
                    FilterChip(
                        selected = seg == selected,
                        onClick = { onSelect(seg) },
                        label = {
                            Text(
                                if (useLong) longLabelFor(seg) else labelFor(seg),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Spacer(Modifier.width(4.dp))
                Box(Modifier.weight(1f)) { dateSlot() }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    segments.forEach { seg ->
                        FilterChip(
                            selected = seg == selected,
                            onClick = { onSelect(seg) },
                            label = {
                                Text(
                                    labelFor(seg),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = AdaptiveLayout.MIN_TOUCH)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) { dateSlot() }
            }
        }
    }
}

// ─── AdaptiveCellRow ──────────────────────────────────────────────────────
//
// One-row-of-N-cells/buttons that collapses to wrapped rows of M when font
// scaling crowds the layout. Used by spec strips, action button rows, action
// legends, and any other weight(1f)-per-cell pattern.

@Composable
fun <T> AdaptiveCellRow(
    items: List<T>,
    perRowAtA: Int = items.size,
    perRowAtB: Int = (items.size + 1) / 2,
    perRowAtC: Int = 1,
    spacing: Dp = 6.dp,
    modifier: Modifier = Modifier,
    cell: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    val perRow = when (controlTier()) {
        ControlTier.A -> perRowAtA
        ControlTier.B -> perRowAtB
        ControlTier.C -> perRowAtC
    }.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        items.chunked(perRow).forEachIndexed { idx, chunk ->
            if (idx > 0) Spacer(Modifier.height(spacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                chunk.forEach { item ->
                    Box(Modifier.weight(1f)) { cell(item) }
                }
                // Pad partial last row so cell widths stay aligned with the rows above.
                repeat(perRow - chunk.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── AdaptiveChipRow ──────────────────────────────────────────────────────
//
// Single-select segmented filter that retiers off the AVAILABLE WIDTH via the
// central [stripLayout] decision — which folds together screen width,
// orientation, display (density) size and font scale (the per-chip floor grows
// with fontScale). Its four observable states are exactly stripLayout's:
//
//  • extra-wide / landscape → one weighted row of LONG labels ("Jan").
//  • portrait normal        → one weighted row of SHORT labels ("J").
//  • portrait crowded       → wrap to 2 then 3 BALANCED weighted rows
//                             (e.g. 13 chips → two rows of 7/6).
//  • too crowded            → collapse to an ExposedDropdownMenu.
//
// Used by the KPI MonthFilterRow and any other dense single-select chip strip.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AdaptiveChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    labelLong: ((T) -> String)? = null,
    modifier: Modifier = Modifier,
    maxWrapRows: Int = AdaptiveLayout.CHIP_MAX_WRAP_ROWS,
) {
    if (items.isEmpty()) return
    val scale = adaptiveFontScale()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val layout = stripLayout(
            available = maxWidth,
            itemCount = items.size,
            shortItemWidth = AdaptiveLayout.CHIP_MIN_SHORT,
            longItemWidth = if (labelLong != null) AdaptiveLayout.CHIP_MIN_LONG
                            else AdaptiveLayout.CHIP_MIN_SHORT,
            maxRows = maxWrapRows,
            fontScale = scale,
        )
        when (layout) {
            is StripLayout.Rows -> {
                val useLong = layout.useLong && labelLong != null
                val text: (T) -> String = { if (useLong) labelLong!!(it) else label(it) }
                // Balanced rows: ceil(n / rows) chips per row so a wrap is even
                // (13 over 2 rows → 7 + 6, not 12 + 1).
                val perRow = ceil(items.size.toDouble() / layout.rows)
                    .toInt().coerceAtLeast(1)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items.chunked(perRow).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chunk.forEach { item ->
                                CompactFilterChip(
                                    selected = isSelected(item),
                                    label = text(item),
                                    onClick = { onSelect(item) },
                                    modifier = Modifier.weight(1f).height(32.dp)
                                )
                            }
                            // Pad the partial last row so chip widths stay
                            // aligned with the full rows above.
                            repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            StripLayout.Dropdown -> {
                var expanded by remember { mutableStateOf(false) }
                val sel = items.firstOrNull { isSelected(it) }
                val longText: (T) -> String = { labelLong?.invoke(it) ?: label(it) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = sel?.let { longText(it) } ?: "—",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        items.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(longText(item),
                                        style = MaterialTheme.typography.bodyMedium)
                                },
                                onClick = { onSelect(item); expanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Dense, weight-friendly single-select chip used by [AdaptiveChipRow]. */
@Composable
private fun CompactFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.heightIn(min = 32.dp).clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── PinnedScrollTable ────────────────────────────────────────────────────
//
// Table with a frozen first ("pinned") column and a horizontally scrollable
// area for the remaining columns. Header and body share a single
// `rememberScrollState()`, so dragging either drags the other in lock-step.

data class PinnedScrollColumn<R>(
    val header: String,
    val align: TextAlign = TextAlign.End,
    val minWidth: Dp = AdaptiveLayout.SCROLL_COL_MIN_NUMERIC,
    // Relative weight used in the normal-size (fit-to-width) layout — mirrors
    // the Modifier.weight(...) the original hand-built tables used.
    val weight: Float = 1f,
    val accent: (R) -> Boolean = { false },
    val headerSlot: (@Composable () -> Unit)? = null,
    val cell: @Composable (R) -> Unit,
)

/**
 * Adaptive data table.
 *
 * When the table's *comfortable* width (the pinned column plus every value
 * column, grown by the current fontScale) fits the available width, the
 * columns are laid out with weights so the table fills the width with no
 * horizontal scroll — identical in spirit to the hand-built weighted tables
 * this replaced, and it distributes evenly across the extra width in
 * landscape.
 *
 * When it does not fit — a narrow screen, an increased display (density)
 * size, or enlarged fonts, in any combination — the first column is *pinned*
 * and the remaining columns move into a horizontally scrollable area (header +
 * body share one scroll state) so the values never clip.
 *
 * @param pinnedWeight relative weight of the label column in the fit layout.
 * @param minComfortableWidth overrides the auto-computed fit threshold (the
 *        pinned floor + Σ column min widths). Scaled by fontScale internally.
 */
@Composable
fun <R> PinnedScrollTable(
    rows: List<R>,
    pinnedHeader: String,
    pinnedCell: @Composable (R) -> Unit,
    columns: List<PinnedScrollColumn<R>>,
    modifier: Modifier = Modifier,
    pinnedWeight: Float = 2f,
    minComfortableWidth: Dp? = null,
    rowBackground: @Composable (R, Int) -> Color = { _, _ -> Color.Transparent },
    onRowClick: ((R) -> Unit)? = null,
    pinnedHeaderSlot: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val scale = adaptiveFontScale()
    val needed = (minComfortableWidth
        ?: (AdaptiveLayout.PINNED_FIT_MIN_WIDTH +
            columns.fold(0.dp) { acc, c -> acc + c.minWidth })) * scale
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Same central question as every other adaptive control: does the
        // comfortable (fontScale-scaled) width fit the measured width?
        if (fitsWidth(available = maxWidth, needed = needed)) {
            WeightedTable(
                rows, pinnedHeader, pinnedCell, columns, Modifier, pinnedWeight,
                rowBackground, onRowClick, pinnedHeaderSlot, footer
            )
        } else {
            PinnedScrollTableImpl(
                rows, pinnedHeader, pinnedCell, columns, Modifier, maxWidth,
                rowBackground, onRowClick, pinnedHeaderSlot, footer
            )
        }
    }
}

private fun cellAlignment(align: TextAlign): Alignment = when (align) {
    TextAlign.End -> Alignment.CenterEnd
    TextAlign.Start -> Alignment.CenterStart
    else -> Alignment.Center
}

// ── Fit-to-width (normal size) ─────────────────────────────────────────────
@Composable
private fun <R> WeightedTable(
    rows: List<R>,
    pinnedHeader: String,
    pinnedCell: @Composable (R) -> Unit,
    columns: List<PinnedScrollColumn<R>>,
    modifier: Modifier,
    pinnedWeight: Float,
    rowBackground: @Composable (R, Int) -> Color,
    onRowClick: ((R) -> Unit)?,
    pinnedHeaderSlot: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
) {
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.weight(pinnedWeight).padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (pinnedHeaderSlot != null) pinnedHeaderSlot()
                else Text(pinnedHeader, style = MaterialTheme.typography.labelMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            columns.forEach { col ->
                Box(
                    Modifier.weight(col.weight).padding(horizontal = 4.dp),
                    contentAlignment = cellAlignment(col.align)
                ) {
                    if (col.headerSlot != null) col.headerSlot.invoke()
                    else Text(col.header, style = MaterialTheme.typography.labelMedium,
                        textAlign = col.align, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(divider))

        // Body
        rows.forEachIndexed { index, row ->
            val bg = rowBackground(row, index)
            val rowModifier = Modifier
                .fillMaxWidth()
                .then(if (bg != Color.Transparent) Modifier.background(bg) else Modifier)
                .then(if (onRowClick != null) Modifier.clickable { onRowClick(row) } else Modifier)
            Row(
                modifier = rowModifier.heightIn(min = 32.dp).padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.weight(pinnedWeight).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) { pinnedCell(row) }
                columns.forEach { col ->
                    Box(
                        Modifier.weight(col.weight).padding(horizontal = 4.dp),
                        contentAlignment = cellAlignment(col.align)
                    ) { col.cell(row) }
                }
            }
            if (index < rows.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp)
                    .background(divider.copy(alpha = 0.3f)))
            }
        }

        if (footer != null) {
            Spacer(Modifier.height(4.dp))
            footer()
        }
    }
}

// ── Pinned first column + horizontal scroll (large fonts) ──────────────────
@Composable
private fun <R> PinnedScrollTableImpl(
    rows: List<R>,
    pinnedHeader: String,
    pinnedCell: @Composable (R) -> Unit,
    columns: List<PinnedScrollColumn<R>>,
    modifier: Modifier,
    availableWidth: Dp,
    rowBackground: @Composable (R, Int) -> Color,
    onRowClick: ((R) -> Unit)?,
    pinnedHeaderSlot: (@Composable () -> Unit)?,
    footer: (@Composable () -> Unit)?,
) {
    val hScroll = rememberScrollState()
    val scale = adaptiveFontScale()
    // Cap the pinned column so it can never eat more than half the row — past
    // that the scrollable value area becomes unreadable (see TODO #2).
    val pinnedWidth = pinnedColumnWidth(scale)
        .coerceAtMost(availableWidth * AdaptiveLayout.PINNED_MAX_FRACTION)
    val surface = MaterialTheme.colorScheme.surface
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    // Per-column floor grows with fontScale so larger text never clips.
    val colMin: (PinnedScrollColumn<R>) -> Dp = { it.minWidth * scale }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Header row ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(pinnedWidth)
                    .shadow(elevation = 2.dp)
                    .background(surface)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                if (pinnedHeaderSlot != null) {
                    pinnedHeaderSlot()
                } else {
                    Text(
                        pinnedHeader,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(hScroll),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEach { col ->
                    Box(
                        Modifier
                            .widthIn(min = colMin(col))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = cellAlignment(col.align)
                    ) {
                        if (col.headerSlot != null) {
                            col.headerSlot.invoke()
                        } else {
                            Text(
                                col.header,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = col.align,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        // Thin divider under the header.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(divider)
        )

        // ── Body rows ─────────────────────────────────────────────────────
        rows.forEachIndexed { index, row ->
            val bg = rowBackground(row, index)
            val rowModifier = Modifier
                .fillMaxWidth()
                .then(if (bg != Color.Transparent) Modifier.background(bg) else Modifier)
                .then(if (onRowClick != null) Modifier.clickable { onRowClick(row) } else Modifier)
            Row(
                modifier = rowModifier.heightIn(min = 36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pinned cell — opaque so scrolled cells pass underneath.
                Box(
                    Modifier
                        .width(pinnedWidth)
                        .shadow(elevation = 2.dp)
                        .background(if (bg != Color.Transparent) bg else surface)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    pinnedCell(row)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    columns.forEach { col ->
                        Box(
                            Modifier
                                .widthIn(min = colMin(col))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = cellAlignment(col.align)
                        ) {
                            col.cell(row)
                        }
                    }
                }
            }
            if (index < rows.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(divider.copy(alpha = 0.3f))
                )
            }
        }

        if (footer != null) {
            Spacer(Modifier.height(4.dp))
            footer()
        }
    }
}

// ─── AdaptiveTwoColumn ────────────────────────────────────────────────────
//
// Side-by-side container that becomes a single Column on COMPACT and a Row
// with weight(1f) columns on MEDIUM/WIDE/ULTRA.

@Composable
fun AdaptiveTwoColumn(
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    breakAt: Dp = AdaptiveLayout.WIDTH_MEDIUM_AT,
    spacing: Dp = 12.dp,
    primaryWeight: Float = 1f,
    secondaryWeight: Float = 1f,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth >= breakAt) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.Top
            ) {
                Box(Modifier.weight(primaryWeight)) { primary() }
                Box(Modifier.weight(secondaryWeight)) { secondary() }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                primary()
                secondary()
            }
        }
    }
}

/** Centred max-width wrapper for action-button rows so they don't span tablets. */
@Composable
fun ActionRowCenter(
    modifier: Modifier = Modifier,
    maxWidth: Dp = AdaptiveLayout.ACTION_ROW_MAX_WIDTH,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxWidth()) { content() }
    }
}
