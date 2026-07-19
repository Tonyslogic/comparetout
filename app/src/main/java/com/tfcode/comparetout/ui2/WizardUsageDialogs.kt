package com.tfcode.comparetout.ui2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.tfcode.comparetout.ComparisonUIViewModel
import com.tfcode.comparetout.R
import com.tfcode.comparetout.SimulatorLauncher
import com.tfcode.comparetout.TOUTCApplication
import com.tfcode.comparetout.model.json.scenario.ScenarioJsonFile
import com.tfcode.comparetout.model.scenario.PanelPVSummary
import com.tfcode.comparetout.model.scenario.Scenario
import com.tfcode.comparetout.scenario.HeatPumpWeatherCache
import com.tfcode.comparetout.scenario.loadprofile.StandardLoadProfiles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/*
 * Wizard usage-data dialogs (derive-from-source, SLP picker, hand-craft) — extracted verbatim from UI2WizardActivity.kt (mega-refactor B2c).
 * Imports inherited; unused are cosmetic.
 */

/* ──────────────────────────────────────────────────────────────────
   Derive from a source dialog
────────────────────────────────────────────────────────────────── */

@Composable
internal fun DeriveFromSourceDialog(
    availableSources: List<SourceDateRange>,
    isDeriving: Boolean,
    // (sysSn, importer, from, to, fillGaps, absoluteYear)
    onDerive: (String, ComparisonUIViewModel.Importer, LocalDate, LocalDate, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember { mutableStateOf<SourceDateRange?>(null) }
    // Advanced PeriodSelector state: a D/M/Y trailing window ending on the anchor.
    var period         by remember { mutableStateOf(DataSourcePeriod.YEAR) }
    var anchor         by remember { mutableStateOf(LocalDate.now()) }
    var fillGaps       by remember { mutableStateOf(true) }
    // Averages = an average hourly/daily/monthly shape over the window; Absolute year = the source's real
    // measured series for the chosen year mapped onto the 2001 grid (plus that year's distribution).
    var absoluteYear   by remember { mutableStateOf(false) }

    fun onSourcePicked(src: SourceDateRange) {
        selectedSource = src
        period = DataSourcePeriod.YEAR
        anchor = LocalDate.parse(src.finishDate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui2_wiz_src_derive)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (availableSources.isEmpty()) {
                    item {
                        Text(stringResource(R.string.ui2_wiz_no_imported_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.ui2_wiz_extraction),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !absoluteYear,
                                    onClick  = { absoluteYear = false },
                                    label    = { Text(stringResource(R.string.ui2_wiz_averages)) }
                                )
                                FilterChip(
                                    selected = absoluteYear,
                                    onClick  = { absoluteYear = true; period = DataSourcePeriod.YEAR },
                                    label    = { Text(stringResource(R.string.ui2_wiz_absolute_year)) }
                                )
                            }
                            Text(
                                stringResource(if (absoluteYear) R.string.ui2_wiz_absolute_desc
                                               else R.string.ui2_wiz_averages_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Text(stringResource(R.string.ui2_wiz_select_data_source),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    items(availableSources) { src ->
                        val sel = selectedSource == src
                        val typeLabel = stringResource(when (src.importerType) {
                            ComparisonUIViewModel.Importer.ALPHAESS        -> R.string.brand_alphaess
                            ComparisonUIViewModel.Importer.ESBNHDF         -> R.string.ui2_wiz_esbn_word
                            ComparisonUIViewModel.Importer.HOME_ASSISTANT  -> R.string.home_assistant
                            ComparisonUIViewModel.Importer.OCTOPUS         -> R.string.ui2_wiz_octopus_word
                            ComparisonUIViewModel.Importer.SOLIS           -> R.string.ui2_wiz_solis
                            ComparisonUIViewModel.Importer.FUSION_SOLAR    -> R.string.ui2_wiz_fusionsolar
                            else -> R.string.ui2_wiz_unknown
                        })
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp))
                                .clickable { onSourcePicked(src) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("$typeLabel — ${src.sysSn}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (sel) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface)
                                Text("${src.startDate} → ${src.finishDate}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (sel) Icon(Icons.Default.CheckCircle, null,
                                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Meter-only (ESBN / Octopus) warning
                    if (selectedSource?.importerType == ComparisonUIViewModel.Importer.ESBNHDF ||
                        selectedSource?.importerType == ComparisonUIViewModel.Importer.OCTOPUS) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Warning, null, Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Text(stringResource(R.string.ui2_wiz_esbn_warning),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    val src = selectedSource
                    if (src != null) {
                        item {
                            PeriodSelector(
                                selectedPeriod = period,
                                anchorDate     = anchor,
                                dataStart      = src.startDate,
                                dataEnd        = src.finishDate,
                                advanced       = true,
                                onPeriodChange = { p, a, _ -> period = p; anchor = a },
                                onNavigate     = { fwd, _ ->
                                    anchor = stepAnchor(anchor, period, fwd,
                                        LocalDate.parse(src.startDate),
                                        LocalDate.parse(src.finishDate))
                                }
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui2_wiz_fill_gaps),
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.ui2_wiz_fill_gaps_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = fillGaps, onCheckedChange = { fillGaps = it })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val src = selectedSource
            Button(
                onClick = {
                    if (src != null) {
                        val startD  = LocalDate.parse(src.startDate)
                        val finishD = LocalDate.parse(src.finishDate)
                        val (rawFrom, rawTo) = periodDateRange(period, anchor, true, startD, finishD)
                        onDerive(src.sysSn, src.importerType,
                                 rawFrom.coerceIn(startD, finishD),
                                 rawTo.coerceIn(startD, finishD), fillGaps, absoluteYear)
                        onDismiss()
                    }
                },
                enabled = src != null && !isDeriving
            ) { Text(stringResource(if (isDeriving) R.string.ui2_wiz_deriving
                                    else R.string.ui2_wiz_derive)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

/* ──────────────────────────────────────────────────────────────────
   SLP picker dialog
────────────────────────────────────────────────────────────────── */

@Composable
internal fun SLPPickerDialog(
    currentProfile: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui2_wiz_src_slp)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(StandardLoadProfiles.standardLoadProfiles.toList()) { profileName ->
                    val sel = profileName == currentProfile
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp,
                                if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp))
                            .clickable { onSelect(profileName); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(profileName, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            color = if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                        if (sel) Icon(Icons.Default.CheckCircle, null,
                            Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

/* ──────────────────────────────────────────────────────────────────
   Hand-craft distribution dialog
────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HandCraftDialog(
    hourly: List<Double>,
    daily: List<Double>,
    monthly: List<Double>,
    onSave: (List<Double>, List<Double>, List<Double>) -> Unit,
    onDismiss: () -> Unit
) {
    var localHourly  by remember { mutableStateOf(hourly) }
    var localDaily   by remember { mutableStateOf(daily) }
    var localMonthly by remember { mutableStateOf(monthly) }
    var tab          by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.ui2_wiz_edit_distribution),
                            style = MaterialTheme.typography.titleMedium) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.dialog_cancel))
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                onSave(localHourly, localDaily, localMonthly)
                                onDismiss()
                            }) { Text(stringResource(R.string.ui2_save)) }
                        }
                    )
                }
            ) { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues)) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 },
                            text = { Text(stringResource(R.string.ui2_wiz_tab_hourly)) })
                        Tab(selected = tab == 1, onClick = { tab = 1 },
                            text = { Text(stringResource(R.string.ui2_wiz_tab_daily)) })
                        Tab(selected = tab == 2, onClick = { tab = 2 },
                            text = { Text(stringResource(R.string.ui2_wiz_tab_monthly)) })
                    }
                    when (tab) {
                        0 -> DistributionSliderList(
                            values = localHourly,
                            labels = (0..23).map { "%02d:00".format(it) },
                            uniformValue = 100.0 / 24.0,
                            onChange = { idx, v -> localHourly  = localHourly.toMutableList().also  { it[idx] = v } },
                            onReset  = { localHourly = List(24) { 100.0 / 24.0 } }
                        )
                        1 -> DistributionSliderList(
                            values = localDaily,
                            labels = stringArrayResource(R.array.ui2_days_short_mon_first).toList(),
                            uniformValue = 100.0 / 7.0,
                            onChange = { idx, v -> localDaily   = localDaily.toMutableList().also   { it[idx] = v } },
                            onReset  = { localDaily = List(7) { 100.0 / 7.0 } }
                        )
                        2 -> DistributionSliderList(
                            values = localMonthly,
                            labels = stringArrayResource(R.array.ui2_months_short).toList(),
                            uniformValue = 100.0 / 12.0,
                            onChange = { idx, v -> localMonthly = localMonthly.toMutableList().also { it[idx] = v } },
                            onReset  = { localMonthly = List(12) { 100.0 / 12.0 } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DistributionSliderList(
    values: List<Double>,
    labels: List<String>,
    uniformValue: Double,
    onChange: (Int, Double) -> Unit,
    onReset: () -> Unit
) {
    val maxSlider = (uniformValue * 4).toFloat().coerceAtLeast(20f)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.ui2_wiz_sum_pct, "%.1f".format(values.sum())),
                style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onReset) { Text(stringResource(R.string.ui2_wiz_reset_flat)) }
        }
        LazyColumn {
            itemsIndexed(values) { idx, value ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(labels.getOrElse(idx) { idx.toString() },
                        modifier = Modifier.width(56.dp),
                        style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { onChange(idx, it.toDouble()) },
                        valueRange = 0f..maxSlider,
                        modifier = Modifier.weight(1f)
                    )
                    Text("%.1f".format(value),
                        modifier = Modifier.width(36.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
