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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.tfcode.comparetout.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Historical backtest window picker ───────────────────────────────────────
//
// A dynamic tariff is backtested over 12 consecutive months. A fixed calendar
// year (Jan–Dec) always straddles the SEMOpx (Ireland) publication gap, whereas
// a rolling 12-month window can sit entirely inside the market's freshest
// ~12-month data — and 12 consecutive months still cover each calendar month
// exactly once, so the materialiser tiles the sim's 2001 calendar the same way.
// The window is month-granular (the sim needs whole months) and moves one month
// at a time with the ‹ › steppers.

/** SEM go-live — the earliest month with public wholesale data. */
val EARLIEST_WINDOW_START: YearMonth = YearMonth.of(2018, 10)

/**
 * Most recent window whose 12th (last) month is already complete: end = the
 * previous whole month, so start = 12 months ago. Also the newest the window
 * can be — the › stepper stops here.
 */
fun latestWindowStart(): YearMonth = YearMonth.now().minusMonths(12)

/** Default backtest window = the most recent 12 complete months (gap-free). */
fun defaultWindowStart(): YearMonth = latestWindowStart()

/**
 * Resolve a stored (year, periodStartMonth) into a window start, clamped to the
 * valid range: legacy null month = January; a null year = the default window.
 */
fun windowStartOf(year: Int?, periodStartMonth: Int?): YearMonth {
    if (year == null) return defaultWindowStart()
    val ym = YearMonth.of(year, (periodStartMonth ?: 1).coerceIn(1, 12))
    val latest = latestWindowStart()
    return when {
        ym.isBefore(EARLIEST_WINDOW_START) -> EARLIEST_WINDOW_START
        ym.isAfter(latest) -> latest
        else -> ym
    }
}

private val WINDOW_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

/** "Jul 2025 — Jun 2026" for a rolling window; "2024" for a legacy Jan–Dec plan. */
fun windowLabel(year: Int?, periodStartMonth: Int?): String {
    if (year == null) return ""
    if ((periodStartMonth ?: 1) == 1) return year.toString()
    val start = YearMonth.of(year, periodStartMonth!!.coerceIn(1, 12))
    return "${start.format(WINDOW_FMT)} — ${start.plusMonths(11).format(WINDOW_FMT)}"
}

@Composable
fun HistoricalWindowStepper(
    start: YearMonth,
    onStartChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    earliest: YearMonth = EARLIEST_WINDOW_START,
    latest: YearMonth = latestWindowStart()
) {
    val end = start.plusMonths(11)
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.ui2_dyn_window_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onStartChange(start.minusMonths(1)) },
                enabled = start > earliest
            ) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.ui2_dyn_window_prev)
                )
            }
            Text(
                "${start.format(WINDOW_FMT)}  —  ${end.format(WINDOW_FMT)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onStartChange(start.plusMonths(1)) },
                enabled = start < latest
            ) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.ui2_dyn_window_next)
                )
            }
        }
    }
}
