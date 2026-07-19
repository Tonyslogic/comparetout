package com.tfcode.comparetout.ui2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import java.time.LocalDate
import androidx.compose.ui.geometry.Size as CanvasSize

/*
 * Wizard load-profile distribution charts — extracted verbatim from WizardScheduleComposables.kt (mega-refactor B3).
 * Imports inherited; unused are cosmetic.
 */

/* ──────────────────────────────────────────────────────────────────
   Load profile distribution charts
   Mirrors DataSourceDistributionCharts in the dashboard.
   Uses a pure-Compose bar chart to avoid a second AndroidView reference.
────────────────────────────────────────────────────────────────── */

@Composable
fun WizardDistributionCharts(
    hourly: List<Double>,
    daily: List<Double>,
    monthly: List<Double>,
    modifier: Modifier = Modifier
) {
    val hourLabels  = (0..23).map { "%02d".format(it) }
    val dayLabels   = stringArrayResource(R.array.ui2_days_short_mon_first).toList()
    val monthLabels = stringArrayResource(R.array.ui2_months_short).toList()
    val hourlyTitle  = stringResource(R.string.ui2_wiz_hourly_dist)
    val dailyTitle   = stringResource(R.string.ui2_wiz_dow_dist)
    val monthlyTitle = stringResource(R.string.ui2_wiz_monthly_dist)

    var zoomedIdx by remember { mutableIntStateOf(-1) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (zoomedIdx >= 0) {
            WizardDistBarChart(
                title = when (zoomedIdx) {
                    0 -> hourlyTitle
                    1 -> dailyTitle
                    else -> monthlyTitle
                },
                values = when (zoomedIdx) { 0 -> hourly; 1 -> daily; else -> monthly },
                labels = when (zoomedIdx) { 0 -> hourLabels; 1 -> dayLabels; else -> monthLabels },
                showEveryNthLabel = if (zoomedIdx == 0) 6 else 1,
                barHeight = 160.dp,
                onClick = { zoomedIdx = -1 }
            )
            Text(stringResource(R.string.ui2_wiz_tap_zoom_out),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        } else {
            WizardDistBarChart(title = hourlyTitle, values = hourly,
                labels = hourLabels, showEveryNthLabel = 6, barHeight = 60.dp, onClick = { zoomedIdx = 0 })
            WizardDistBarChart(title = dailyTitle, values = daily,
                labels = dayLabels, showEveryNthLabel = 1, barHeight = 60.dp, onClick = { zoomedIdx = 1 })
            WizardDistBarChart(title = monthlyTitle, values = monthly,
                labels = monthLabels, showEveryNthLabel = 1, barHeight = 60.dp, onClick = { zoomedIdx = 2 })
            Text(stringResource(R.string.ui2_wiz_tap_zoom_in),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun WizardDistBarChart(
    title: String,
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    showEveryNthLabel: Int = 1,
    barHeight: Dp = 60.dp,
    onClick: (() -> Unit)? = null,
) {
    val labelColorArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val gridColorArgb  = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f).toArgb()
    val barColorArgb   = MaterialTheme.colorScheme.primary.toArgb()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp))
        AndroidView(
            factory = { ctx ->
                BarChart(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    styleWizardDistBarChart(this, labelColorArgb, gridColorArgb)
                }
            },
            update = { chart ->
                styleWizardDistBarChart(chart, labelColorArgb, gridColorArgb)
                val filteredLabels = labels.mapIndexed { i, l -> if (i % showEveryNthLabel == 0) l else "" }
                val entries = values.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
                val ds = BarDataSet(entries, "").apply {
                    color = barColorArgb
                    setDrawValues(false)
                }
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) =
                        filteredLabels.getOrElse(value.toInt()) { "" }
                }
                chart.data = BarData(ds)
                chart.invalidate()
            },
            modifier = Modifier.fillMaxWidth().height(barHeight)
        )
    }
}

internal fun styleWizardDistBarChart(chart: BarChart, labelColor: Int, gridColor: Int) {
    chart.description.isEnabled = false
    chart.setDrawGridBackground(false)
    chart.setDrawBarShadow(false)
    chart.setFitBars(true)
    chart.setNoDataTextColor(labelColor)
    chart.setTouchEnabled(false)
    chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
    chart.xAxis.granularity = 1f
    chart.xAxis.setDrawGridLines(false)
    chart.xAxis.textColor = labelColor
    chart.xAxis.textSize = 9f
    chart.axisLeft.isEnabled = true
    chart.axisLeft.setLabelCount(2, true)
    chart.axisLeft.setDrawGridLines(false)
    chart.axisLeft.textColor = labelColor
    chart.axisLeft.textSize = 8f
    chart.axisLeft.resetAxisMinimum()
    chart.axisLeft.valueFormatter = object : ValueFormatter() {
        override fun getFormattedValue(value: Float) = "%.1f%%".format(value)
    }
    chart.axisRight.isEnabled = false
    chart.legend.isEnabled = false
    chart.setScaleEnabled(false)
}
