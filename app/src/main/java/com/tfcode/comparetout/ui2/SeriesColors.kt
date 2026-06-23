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

import androidx.compose.ui.graphics.Color
import com.tfcode.comparetout.ui2.UI2GraphsViewModel.FilterSeries

/**
 * The **single source of truth** for every data-series colour in the UI2 charts. Bar, line, Sankey
 * ([SankeyView]), the Comparison merged charts ([compareSeriesColor]) and the Dashboard pies ([SeriesColors])
 * all read from here, so a concept (solar / import / export / battery / EV / hot water / heat pump) looks the
 * same on every screen. Add a series here rather than hard-coding a colour.
 *
 * Hues are grouped by domain and kept mutually distinguishable — which matters most in the Sankey, where the
 * flows sit edge-to-edge:
 *  - **Core**: house blue, import **red** (cost), export yellow (income), solar **amber** (sun).
 *  - **Battery / PV routing**: greens, teal, violet/indigo, grey.
 *  - **EV**: orange / lime.
 *  - **Hot water**: purple / brown.
 *  - **Heat pump**: pinks for the energy series, cool hues (purple/blue/teal) for the line metrics.
 */
internal val SERIES_COLORS: Map<FilterSeries, Color> = mapOf(
    // Core — house, grid, solar
    FilterSeries.LOAD          to Color(0xFF42A5F5), // blue   — house load
    FilterSeries.BUY           to Color(0xFFE53935), // red    — import (cost)
    FilterSeries.FEED          to Color(0xFFFFEE58), // yellow — export (income)
    FilterSeries.PV            to Color(0xFFFFB300), // amber  — solar
    // Battery & PV routing — greens / teal / violet / indigo / grey
    FilterSeries.PV2BAT        to Color(0xFF66BB6A), // green
    FilterSeries.PV2LOAD       to Color(0xFF9E9E9E), // grey
    FilterSeries.BAT2LOAD      to Color(0xFF00897B), // teal
    FilterSeries.GRID2BAT      to Color(0xFF7E57C2), // violet
    FilterSeries.BAT2GRID      to Color(0xFF3949AB), // indigo
    FilterSeries.BAT_CHARGE    to Color(0xFF43A047), // green
    FilterSeries.BAT_DISCHARGE to Color(0xFF6D4C41), // brown (off-orange to free the warm region)
    // EV — orange / lime
    FilterSeries.EV_SCHEDULE   to Color(0xFFEF6C00), // deep orange
    FilterSeries.EV_DIVERT     to Color(0xFFC0CA33), // lime
    FilterSeries.EV_ACTUAL     to Color(0xFFFF7043), // coral (importer-only)
    // Hot water — purple / brown
    FilterSeries.HW_SCHEDULE   to Color(0xFF9C27B0), // purple
    FilterSeries.HW_DIVERT     to Color(0xFF795548), // brown
    // Heat pump — pinks (energy) + cool hues (line metrics)
    FilterSeries.HEAT_PUMP        to Color(0xFFD81B60), // magenta-pink — HP electrical load
    FilterSeries.HEAT_PUMP_BACKUP to Color(0xFFAD1457), // dark pink
    FilterSeries.HEAT_PUMP_HEAT   to Color(0xFFF06292), // light pink
    FilterSeries.HEAT_PUMP_COP    to Color(0xFF6A1B9A), // deep purple — line
    FilterSeries.HEAT_PUMP_TEMP   to Color(0xFF1565C0), // blue        — line
    FilterSeries.HEAT_PUMP_WIND   to Color(0xFF00838F)  // teal        — line
)

/** Registry colour for a series, with a safe fallback. The one accessor every chart should use. */
internal fun seriesColor(series: FilterSeries): Color = SERIES_COLORS[series] ?: Color.Gray

/**
 * Bridge for the Comparison screen, which keys series by String id. The screen distinguishes its two metric
 * groups by context ([isEnergy]) — `buy`/`feed` appear in BOTH the cost columns and the energy flows:
 *  - **Energy** series delegate to the central registry, so load / solar / import / export / battery / EV / HW
 *    match the graphs, Sankey and Dashboard exactly.
 *  - **Cost** columns keep their own money palette (cyan buy / amber sell / grey fixed) — these match the cost
 *    stack's band colours ([CompareScreen]'s `BUY_BAND_COLOR` etc.) and have no [FilterSeries] equivalent.
 */
internal fun compareSeriesColor(id: String, primary: Color, isEnergy: Boolean): Color {
    val key = id.removePrefix("c_")
    if (!isEnergy) {
        // Cost columns — kept byte-identical to the previous cost palette / the cost-stack band colours.
        return when (key) {
            "buy"          -> Color(0xFF22B8CE) // cyan  — matches BUY_BAND_COLOR
            "sell", "feed" -> Color(0xFFF5A623) // amber — matches SELL_BAND_COLOR
            "bonus"        -> Color(0xFF4CAF50) // green
            "fixed"        -> Color(0xFF9E9E9E) // grey  — matches FIXED_BAND_COLOR
            else           -> primary           // "net" and anything unmapped
        }
    }
    // Energy flows — delegate to the central registry.
    return compareEnergySeries[key]?.let(::seriesColor) ?: primary
}

/** Comparison energy-series id → [FilterSeries]. `heatPump` is included for when Compare-HP lands (deferred). */
private val compareEnergySeries: Map<String, FilterSeries> = mapOf(
    "load" to FilterSeries.LOAD,
    "buy" to FilterSeries.BUY,
    "feed" to FilterSeries.FEED,
    "pv" to FilterSeries.PV,
    "pv2load" to FilterSeries.PV2LOAD,
    "bat2load" to FilterSeries.BAT2LOAD,
    "grid2bat" to FilterSeries.GRID2BAT,
    "charge" to FilterSeries.BAT_CHARGE,
    "discharge" to FilterSeries.BAT_DISCHARGE,
    "evSchedule" to FilterSeries.EV_SCHEDULE,
    "evDivert" to FilterSeries.EV_DIVERT,
    "hwSchedule" to FilterSeries.HW_SCHEDULE,
    "hwDivert" to FilterSeries.HW_DIVERT,
    "heatPump" to FilterSeries.HEAT_PUMP,
    "heatPumpBackup" to FilterSeries.HEAT_PUMP_BACKUP,
    "heatPumpHeat" to FilterSeries.HEAT_PUMP_HEAT
)

/**
 * Semantic aliases for the Dashboard pies, whose slice labels ("Solar", "Import", "Hot Water", …) name a
 * concept rather than a [FilterSeries]. Each maps to the registry so the pies match the graphs.
 */
internal object SeriesColors {
    val house      = seriesColor(FilterSeries.LOAD)
    val solar      = seriesColor(FilterSeries.PV)
    val gridImport = seriesColor(FilterSeries.BUY)
    val export     = seriesColor(FilterSeries.FEED)
    val battery    = seriesColor(FilterSeries.BAT2LOAD)
    val pvToBattery = seriesColor(FilterSeries.PV2BAT)
    val gridToBattery = seriesColor(FilterSeries.GRID2BAT)
    val batteryToGrid = seriesColor(FilterSeries.BAT2GRID)
    val batteryCharge = seriesColor(FilterSeries.BAT_CHARGE)
    val batteryDischarge = seriesColor(FilterSeries.BAT_DISCHARGE)
    val hotWater   = seriesColor(FilterSeries.HW_SCHEDULE)
    val ev         = seriesColor(FilterSeries.EV_SCHEDULE)
}
