package com.tfcode.comparetout.ui2.dashboard

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.MainActivity
import com.tfcode.comparetout.R
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.profile.AppProfiles
import com.tfcode.comparetout.region.RegionProfiles
import com.tfcode.comparetout.model.costings.Costings
import com.tfcode.comparetout.model.scenario.LoadProfile
import com.tfcode.comparetout.model.scenario.Panel
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.ScenarioComponents
import com.tfcode.comparetout.model.scenario.SimKPIs
import com.tfcode.comparetout.ui2.charts.ExpandableCard
import com.tfcode.comparetout.ui2.charts.LoadDistributionCharts
import com.tfcode.comparetout.ui2.charts.PVSummaryBarChart
import com.tfcode.comparetout.ui2.charts.PieChart
import com.tfcode.comparetout.ui2.charts.PieLegend
import com.tfcode.comparetout.ui2.charts.PieSlice
import com.tfcode.comparetout.ui2.charts.SimulationPieCharts
import com.tfcode.comparetout.ui2.charts.SimpleDistBarChart
import com.tfcode.comparetout.ui2.charts.dashPieLabels
import com.tfcode.comparetout.ui2.charts.stylePVBarChart
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.time.LocalDate

/*
 * Dashboard topology diagram — extracted verbatim from UI2DashboardFragment.kt
 * (mega-refactor B1d). Import list is inherited from the source file; unused
 * entries are cosmetic only.
 */

/* ──────────────────────────────────────────────────────────────────
   Topology diagram — inverter centre, PV strings on top per MPPT,
   consumers (house / hot water / EV) on the left, grid on the right,
   and batteries along the bottom with a shared bus when there are
   multiple. Decorated with schedule / discharge / divert icons.
────────────────────────────────────────────────────────────────── */

@Composable
internal fun TopologyDiagram(sc: ScenarioComponents) {
    val inverters = sc.inverters.orEmpty()
    val panels = sc.panels.orEmpty()
    val batteries = sc.batteries.orEmpty()
    val loadShifts = sc.loadShifts.orEmpty()
    val discharges = sc.discharges.orEmpty()
    val evCharges = sc.evCharges.orEmpty()
    val evDiverts = sc.evDiverts.orEmpty()
    val hwSchedules = sc.hwSchedules.orEmpty()
    val hwDivertActive = sc.hwDivert?.isActive == true
    val heatPumps = sc.heatPumps.orEmpty()

    val hasLoad = sc.loadProfile != null
    val hasHw = sc.hwSystem != null
    val hasEv = evCharges.isNotEmpty() || evDiverts.isNotEmpty()
    val hasHeatPump = heatPumps.isNotEmpty()
    val hasInverter = inverters.isNotEmpty()

    val lineColor = MaterialTheme.colorScheme.outline
    val busColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(380.dp)) {
        val totalW = maxWidth
        val totalH = maxHeight
        val cardW = 80.dp
        val cardH = 56.dp
        val invW = 116.dp
        val invH = 68.dp
        val pvTop = 6.dp
        val battTop = totalH - cardH - 6.dp
        val leftX = 4.dp
        val rightX = totalW - cardW - 4.dp
        val cx = totalW / 2
        val cy = totalH / 2
        val invLeft = cx - invW / 2
        val invTop = cy - invH / 2

        // ── Connection lines ─────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val strokeBus = 2.5.dp.toPx()
            val invLeftPx = invLeft.toPx()
            val invTopPx = invTop.toPx()
            val invWPx = invW.toPx()
            val invHPx = invH.toPx()
            val invRightPx = invLeftPx + invWPx
            val invBottomPx = invTopPx + invHPx
            val invMidYPx = invTopPx + invHPx / 2
            val cardWPx = cardW.toPx()
            val cardHPx = cardH.toPx()
            val cxPx = cx.toPx()
            val totalWPx = totalW.toPx()
            val totalHPx = totalH.toPx()
            val leftXPx = leftX.toPx()
            val rightXPx = rightX.toPx()
            val pvTopPx = pvTop.toPx()
            val battTopPx = battTop.toPx()
            val padPx = 4.dp.toPx()

            // PV strings → top of inverter at MPPT entry points
            if (panels.isNotEmpty() && hasInverter) {
                val inv = inverters.first()
                val mpptCount = inv.mpptCount.coerceAtLeast(1)
                val n = panels.size
                val gridW = totalWPx - 2 * padPx
                panels.forEachIndexed { i, p ->
                    val pvCenterX = padPx + gridW * (i + 0.5f) / n
                    val pvBottomY = pvTopPx + cardHPx
                    val mpptIdx = (p.mppt - 1).coerceIn(0, mpptCount - 1)
                    val invTopX = invLeftPx + invWPx * (mpptIdx + 0.5f) / mpptCount
                    drawLine(
                        color = lineColor,
                        start = Offset(pvCenterX, pvBottomY),
                        end = Offset(invTopX, invTopPx),
                        strokeWidth = stroke
                    )
                }
            }

            // Batteries → bottom of inverter (shared bus when >1)
            if (batteries.isNotEmpty() && hasInverter) {
                val n = batteries.size
                val gridW = totalWPx - 2 * padPx
                val busY = battTopPx - 12.dp.toPx()
                val xs = (0 until n).map { i -> padPx + gridW * (i + 0.5f) / n }
                if (n > 1) {
                    drawLine(busColor, Offset(xs.first(), busY), Offset(xs.last(), busY), strokeBus)
                    xs.forEach { x ->
                        drawLine(busColor, Offset(x, busY), Offset(x, battTopPx), stroke)
                    }
                    drawLine(busColor, Offset(cxPx, busY), Offset(cxPx, invBottomPx), strokeBus)
                } else {
                    drawLine(lineColor, Offset(xs[0], battTopPx), Offset(cxPx, invBottomPx), stroke)
                }
            }

            // Consumers (left) → inverter left edge
            val consumers = listOfNotNull(
                if (hasLoad) Unit else null,
                if (hasHw)   Unit else null,
                if (hasEv)   Unit else null,
                if (hasHeatPump) Unit else null
            )
            if (consumers.isNotEmpty() && hasInverter) {
                val cardRightX = leftXPx + cardWPx
                val total = consumers.size
                val topPad = 70.dp.toPx()
                val bottomPad = 90.dp.toPx()
                val span = totalHPx - topPad - bottomPad
                consumers.forEachIndexed { i, _ ->
                    val yCenter = topPad + span * (i + 0.5f) / total
                    drawLine(
                        color = lineColor,
                        start = Offset(cardRightX, yCenter),
                        end = Offset(invLeftPx, invMidYPx),
                        strokeWidth = stroke
                    )
                }
            }

            // Grid (right) → inverter right edge
            if (hasInverter) {
                drawLine(
                    color = busColor,
                    start = Offset(invRightPx, invMidYPx),
                    end = Offset(rightXPx, invMidYPx),
                    strokeWidth = strokeBus
                )
            }
        }

        // ── Card layer ────────────────────────────────────────────
        // Inverter (centre)
        TopologyNode(
            label = inverters.firstOrNull()?.inverterName
                ?: stringResource(R.string.ui2_dash_no_inverter_node),
            subline = if (hasInverter)
                stringResource(R.string.ui2_dash_inv_subline,
                    "%.1f".format(inverters.first().maxInverterLoad),
                    inverters.first().mpptCount)
            else null,
            iconRes = R.drawable.inverter,
            highlight = true,
            modifier = Modifier
                .width(invW).height(invH)
                .offset(x = invLeft, y = invTop)
        )
        if (inverters.size > 1) {
            Text(stringResource(R.string.ui2_dash_n_more, inverters.size - 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.offset(x = invLeft + 4.dp, y = invTop + invH + 2.dp))
        }

        // PV strings (top)
        if (panels.isNotEmpty()) {
            val n = panels.size
            panels.forEachIndexed { i, p ->
                val perCol = (totalW - 8.dp) / n
                val xPos = 4.dp + perCol * i + (perCol - cardW) / 2
                TopologyNode(
                    label = p.panelName.takeIf { !it.isNullOrBlank() } ?: "S${i + 1}",
                    subline = "${p.panelCount}×${p.panelkWp}W",
                    iconRes = R.drawable.solarpanel,
                    badge = stringResource(R.string.ui2_dash_mppt_n, p.mppt),
                    modifier = Modifier
                        .width(cardW).height(cardH)
                        .offset(x = xPos, y = pvTop)
                )
            }
        }

        // Batteries (bottom)
        if (batteries.isNotEmpty()) {
            val n = batteries.size
            batteries.forEachIndexed { i, b ->
                val perCol = (totalW - 8.dp) / n
                val xPos = 4.dp + perCol * i + (perCol - cardW) / 2
                val invName = b.inverter ?: ""
                val hasCharge = loadShifts.any { it.inverter == invName }
                val hasDisch = discharges.any { it.inverter == invName }
                TopologyNode(
                    label = "B${i + 1}",
                    subline = "${"%.1f".format(b.batterySize)} kWh",
                    iconRes = R.drawable.battery1,
                    decorationIcons = listOfNotNull(
                        if (hasCharge) R.drawable.ic_baseline_access_time_24 else null,
                        if (hasDisch) R.drawable.baseline_file_upload_24 else null
                    ),
                    modifier = Modifier
                        .width(cardW).height(cardH)
                        .offset(x = xPos, y = battTop)
                )
            }
        }

        // Consumers (left, stacked top-to-bottom)
        run {
            data class C(val label: String, val sub: String?, val icon: Int, val deco: List<Int>,
                         val emoji: String? = null)
            val consumers = mutableListOf<C>()
            if (hasLoad) consumers += C(
                stringResource(R.string.ui2_dash_house),
                sc.loadProfile?.annualUsage?.let { "${"%.0f".format(it)} kWh/y" },
                R.drawable.house,
                emptyList()
            )
            if (hasHw) consumers += C(
                stringResource(R.string.ui2_component_hot_water),
                sc.hwSystem?.let { "${it.hwCapacity} L · ${it.hwRate} kW" },
                R.drawable.waterwarm,
                listOfNotNull(
                    if (hwSchedules.isNotEmpty()) R.drawable.ic_baseline_access_time_24 else null,
                    if (hwDivertActive) R.drawable.ic_baseline_call_split_24 else null
                )
            )
            if (hasEv) consumers += C(
                stringResource(R.string.ui2_component_ev),
                if (evCharges.isNotEmpty())
                    stringResource(R.string.ui2_dash_n_sched, evCharges.size) else null,
                R.drawable.ev_on,
                listOfNotNull(
                    if (evCharges.isNotEmpty()) R.drawable.ic_baseline_access_time_24 else null,
                    if (evDiverts.isNotEmpty()) R.drawable.ic_baseline_call_split_24 else null
                )
            )
            if (hasHeatPump) consumers += C(
                stringResource(R.string.ui2_component_heat_pump),
                heatPumps.first().let {
                    "${"%.1f".format(it.capacityKw)} kW · " + (if (it.weatherSource == "cds") "CDS"
                        else stringResource(R.string.ui2_dash_weather_sample_word))
                },
                0,                       // no drawable — rendered via the emoji below
                emptyList(),
                emoji = "♨️"        // ♨️, matching the heat-pump glyph used elsewhere in the dashboard
            )
            val total = consumers.size
            val topPad = 70.dp
            val bottomPad = 90.dp
            val span = totalH - topPad - bottomPad
            consumers.forEachIndexed { i, c ->
                val yCenter = topPad + span * (i + 0.5f) / total
                TopologyNode(
                    label = c.label,
                    subline = c.sub,
                    iconRes = c.icon,
                    decorationIcons = c.deco,
                    emojiIcon = c.emoji,
                    modifier = Modifier
                        .width(cardW).height(cardH)
                        .offset(x = leftX, y = yCenter - cardH / 2)
                )
            }
        }

        // Grid (right)
        TopologyNode(
            label = stringResource(R.string.ui2_graphs_grid),
            subline = stringResource(R.string.ui2_dash_import_export_sub),
            iconRes = R.drawable.baseline_file_upload_24,
            iconTinted = true,
            modifier = Modifier
                .width(cardW).height(cardH)
                .offset(x = rightX, y = cy - cardH / 2)
        )

        // Empty state
        if (!hasInverter && panels.isEmpty() && batteries.isEmpty() && !hasLoad && !hasHw && !hasEv && !hasHeatPump) {
            Text(
                stringResource(R.string.ui2_dash_nothing_to_draw),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = leftX, y = cy - 8.dp)
            )
        }
    }
}

@Composable
private fun TopologyNode(
    label: String,
    subline: String?,
    iconRes: Int,
    modifier: Modifier = Modifier,
    badge: String? = null,
    highlight: Boolean = false,
    iconTinted: Boolean = false,
    decorationIcons: List<Int> = emptyList(),
    emojiIcon: String? = null   // when set, drawn instead of [iconRes] (e.g. ♨️ for the heat pump, which has no drawable)
) {
    val container = if (highlight) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (highlight) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val textColor = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (emojiIcon != null) {
                Text(emojiIcon, style = MaterialTheme.typography.labelMedium)
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (iconTinted) textColor else Color.Unspecified
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (subline != null) {
            Text(
                text = subline,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        if (decorationIcons.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                decorationIcons.forEach { res ->
                    Icon(
                        painter = painterResource(res),
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = StatusGreen
                    )
                }
            }
        }
    }
}

@Composable
internal fun TopologyLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        LegendItem(R.drawable.ic_baseline_access_time_24,
            stringResource(R.string.ui2_dash_legend_schedule))
        LegendItem(R.drawable.baseline_file_upload_24,
            stringResource(R.string.ui2_dash_discharge))
        LegendItem(R.drawable.ic_baseline_call_split_24,
            stringResource(R.string.ui2_dash_legend_divert))
    }
}

@Composable
private fun LegendItem(iconRes: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = StatusGreen
        )
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
