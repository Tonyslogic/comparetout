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
 * Wizard usage-data section content — extracted verbatim from UI2WizardActivity.kt (mega-refactor B2c).
 * Imports inherited; unused are cosmetic.
 */

/* ──────────────────────────────────────────────────────────────────
   Usage Data section content
────────────────────────────────────────────────────────────────── */

internal data class SourceOption(
    val src: LoadSource,
    val titleRes: Int,
    val descRes: Int,
    val disabled: Boolean = false
)

internal val USAGE_SOURCE_OPTIONS = listOf(
    SourceOption(LoadSource.SOURCE,
        R.string.ui2_wiz_src_derive, R.string.ui2_wiz_src_derive_desc),
    SourceOption(LoadSource.SLP,
        R.string.ui2_wiz_src_slp, R.string.ui2_wiz_src_slp_desc),
    SourceOption(LoadSource.COPY_PROFILE,
        R.string.ui2_wiz_src_copy, R.string.ui2_wiz_src_copy_desc),
    SourceOption(LoadSource.HAND,
        R.string.ui2_wiz_src_hand, R.string.ui2_wiz_src_hand_desc),
    SourceOption(LoadSource.LINKED,
        R.string.ui2_wiz_src_link, R.string.ui2_wiz_src_link_desc)
)

@Composable
internal fun UsageDataSectionContent(
    builder: WizardBuilder,
    noviceMode: Boolean,
    allScenarios: List<Scenario>,
    availableSources: List<SourceDateRange>,
    isDeriving: Boolean,
    onUpdate: ((WizardBuilder) -> WizardBuilder) -> Unit,
    onLoadSLP: (String) -> Unit,
    onLoadProfileForCopy: (Long) -> Unit,
    onLoadProfileForLink: (Long) -> Unit,
    onInitHandCraft: () -> Unit,
    onDeriveFromSource: (String, ComparisonUIViewModel.Importer, LocalDate, LocalDate, Boolean, Boolean) -> Unit,
    onImport: () -> Unit
) {
    var advancedTab          by remember { mutableIntStateOf(0) }
    var showCopyProfilePicker by remember { mutableStateOf(false) }
    var showLinkProfilePicker by remember { mutableStateOf(false) }
    var showHandCraftDialog   by remember { mutableStateOf(false) }
    var showDeriveDialog      by remember { mutableStateOf(false) }
    var showSLPDialog         by remember { mutableStateOf(false) }

    if (showCopyProfilePicker) {
        ScenarioPickerDialog(
            title = stringResource(R.string.ui2_wiz_copy_profile_from),
            scenarios = allScenarios,
            onSelect = { id -> onLoadProfileForCopy(id) },
            onDismiss = { showCopyProfilePicker = false }
        )
    }
    if (showLinkProfilePicker) {
        ScenarioPickerDialog(
            title = stringResource(R.string.ui2_wiz_link_profile_from),
            scenarios = allScenarios,
            onSelect = { id -> onLoadProfileForLink(id) },
            onDismiss = { showLinkProfilePicker = false }
        )
    }
    if (showHandCraftDialog) {
        HandCraftDialog(
            hourly  = builder.loadProfileHourly  ?: List(24) { 100.0 / 24.0 },
            daily   = builder.loadProfileDaily   ?: List(7)  { 100.0 / 7.0 },
            monthly = builder.loadProfileMonthly ?: List(12) { 100.0 / 12.0 },
            onSave  = { h, d, m ->
                onUpdate { b -> b.copy(loadProfileHourly = h, loadProfileDaily = d, loadProfileMonthly = m) }
            },
            onDismiss = { showHandCraftDialog = false }
        )
    }
    if (showDeriveDialog) {
        DeriveFromSourceDialog(
            availableSources = availableSources,
            isDeriving = isDeriving,
            onDerive = onDeriveFromSource,
            onDismiss = { showDeriveDialog = false }
        )
    }
    if (showSLPDialog) {
        SLPPickerDialog(
            currentProfile = builder.slpProfile,
            onSelect = { onLoadSLP(it) },
            onDismiss = { showSLPDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryTabRow(selectedTabIndex = advancedTab) {
            Tab(selected = advancedTab == 0, onClick = { advancedTab = 0 },
                text = { Text(stringResource(R.string.ui2_cmp_basic)) })
            Tab(selected = advancedTab == 1, onClick = { advancedTab = 1 },
                text = { Text(stringResource(R.string.ui2_cmp_advanced)) })
        }

        if (noviceMode) {
            Text(stringResource(R.string.ui2_wiz_load_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(stringResource(R.string.ui2_wiz_source_header),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

            val noviceVisible = USAGE_SOURCE_OPTIONS.filter {
                // "Derive from a source" is now novice-visible (it's how you turn your own imported smart-meter /
                // inverter data into a load profile). Only Hand-craft stays advanced-only.
                it.src != LoadSource.HAND
            }
            noviceVisible.forEach { opt ->
                val selected = builder.loadSource == opt.src
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(10.dp))
                        .clickable {
                            when (opt.src) {
                                LoadSource.SOURCE       -> { onUpdate { it.copy(loadSource = LoadSource.SOURCE, isLoadLinked = false) }; showDeriveDialog = true }
                                LoadSource.SLP          -> { onUpdate { it.copy(loadSource = LoadSource.SLP,    isLoadLinked = false) }; showSLPDialog    = true }
                                LoadSource.COPY_PROFILE -> showCopyProfilePicker = true
                                LoadSource.HAND         -> { onInitHandCraft(); showHandCraftDialog = true }
                                LoadSource.LINKED       -> showLinkProfilePicker = true
                            }
                        }
                        .padding(12.dp)
                ) {
                    Text(stringResource(opt.titleRes),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(opt.descRes), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = builder.loadSource == LoadSource.SOURCE,
                    onClick  = { onUpdate { it.copy(loadSource = LoadSource.SOURCE, isLoadLinked = false) }; showDeriveDialog = true },
                    label    = { Text(stringResource(R.string.ui2_habf_source)) },
                    leadingIcon = { Icon(painterResource(R.drawable.house), null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.loadSource == LoadSource.SLP,
                    onClick  = { onUpdate { it.copy(loadSource = LoadSource.SLP, isLoadLinked = false) }; showSLPDialog = true },
                    label    = { Text(stringResource(R.string.ui2_wiz_chip_slp)) },
                    leadingIcon = { Icon(Icons.Default.Build, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.loadSource == LoadSource.COPY_PROFILE,
                    onClick  = { showCopyProfilePicker = true },
                    label    = { Text(stringResource(R.string.ui2_wiz_chip_copy)) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.loadSource == LoadSource.HAND,
                    onClick  = { onInitHandCraft(); showHandCraftDialog = true },
                    label    = { Text(stringResource(R.string.ui2_wiz_chip_craft)) },
                    leadingIcon = { Icon(Icons.Default.Build, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = builder.loadSource == LoadSource.LINKED,
                    onClick  = { showLinkProfilePicker = true },
                    label    = { Text(stringResource(R.string.ui2_wiz_chip_link)) },
                    leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) }
                )
            }
        }

        // Distribution source — read-only, shows what created the current profile
        val sourceLabel = when (builder.loadSource) {
            LoadSource.SOURCE       -> builder.distributionSource.ifBlank { "" }
            LoadSource.SLP          -> builder.slpProfile.ifBlank { "" }
            LoadSource.COPY_PROFILE, LoadSource.HAND, LoadSource.LINKED ->
                builder.distributionSource.ifBlank { "" }
        }
        if (sourceLabel.isNotBlank()) {
            OutlinedTextField(
                value = sourceLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.ui2_wiz_distribution_source)) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = if (noviceMode) ({
                    Text(stringResource(R.string.ui2_wiz_distribution_source_hint))
                }) else null
            )
        }

        // COPY_PROFILE / HAND: edit distribution button
        if (builder.loadSource == LoadSource.COPY_PROFILE || builder.loadSource == LoadSource.HAND) {
            FilledTonalButton(
                onClick = {
                    if (builder.loadProfileHourly == null) onInitHandCraft()
                    showHandCraftDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (builder.hasDistributionData)
                    R.string.ui2_wiz_edit_distribution else R.string.ui2_wiz_set_distribution))
            }
            if (!builder.hasDistributionData && noviceMode) {
                Text(stringResource(R.string.ui2_wiz_set_distribution_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Annual usage — always show except when SOURCE selected but no data derived yet
        if (builder.loadSource != LoadSource.SOURCE || builder.hasDistributionData) {
                OutlinedTextField(
                    value = builder.annualUsage,
                    onValueChange = { onUpdate { b -> b.copy(annualUsage = it) } },
                    label = { Text(stringResource(R.string.ui2_wiz_annual_usage)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    readOnly = builder.loadSource == LoadSource.LINKED,
                    supportingText = if (noviceMode)
                        ({ Text(stringResource(R.string.ui2_wiz_annual_usage_hint)) }) else null
                )
            }

            // Distribution charts
            if (builder.hasDistributionData && builder.loadProfileDaily != null && builder.loadProfileMonthly != null) {
                WizardDistributionCharts(
                    hourly  = builder.loadProfileHourly!!,
                    daily   = builder.loadProfileDaily!!,
                    monthly = builder.loadProfileMonthly!!
                )
            }

        // ── Advanced fields ─────────────────────────────────────────
        if (advancedTab == 1) {
            Text(stringResource(R.string.ui2_wiz_advanced_header),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline)
            OutlinedTextField(
                value = builder.hourlyBaseLoad,
                onValueChange = { onUpdate { b -> b.copy(hourlyBaseLoad = it) } },
                label = { Text(stringResource(R.string.ui2_wiz_hourly_base)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                supportingText = if (noviceMode)
                    ({ Text(stringResource(R.string.ui2_wiz_hourly_base_hint)) }) else null
            )
            OutlinedTextField(
                value = builder.gridImportMax,
                onValueChange = { onUpdate { b -> b.copy(gridImportMax = it) } },
                label = { Text(stringResource(R.string.ui2_wiz_grid_import_max)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                supportingText = if (noviceMode)
                    ({ Text(stringResource(R.string.ui2_wiz_grid_import_max_hint)) }) else null
            )
            OutlinedTextField(
                value = builder.gridExportMax,
                onValueChange = { onUpdate { b -> b.copy(gridExportMax = it) } },
                label = { Text(stringResource(R.string.ui2_wiz_grid_export_max)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                supportingText = if (noviceMode)
                    ({ Text(stringResource(R.string.ui2_wiz_grid_export_max_hint)) }) else null
            )
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui2_wiz_import_load_json))
            }
        }
    }
}
