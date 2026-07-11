@file:Suppress("AssignedValueIsNeverRead")

package com.tfcode.comparetout.ui2

import android.os.Bundle
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tfcode.comparetout.ComparisonUIViewModel.Importer
import com.tfcode.comparetout.R
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformMeta
import com.tfcode.comparetout.region.RegionProfiles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ──────────────────────────────────────────────────────────────────────────
// UI2 Data Source Management
//
// Three accordions, one per importer type — each collapses to a one-line
// summary (name + status chip) and expands to surface the bits the user
// needs to operate the source: credentials, system list, fetch + schedule,
// data deletion. Plus, for Home Assistant only, an "Energy sensors" panel
// that reflects what HA's Energy dashboard reports.
//
// Everything else (key stats, graphs, costing, generation) is already
// surfaced on the dashboard or in the scenario wizard — this screen is
// deliberately limited to "things you do to a source", not "things you
// see from a source".
// ──────────────────────────────────────────────────────────────────────────

private enum class DataSourceIoAction { IMPORT, EXPORT }

@AndroidEntryPoint
class UI2DataSourceManagementActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                DataSourceManagementScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataSourceManagementScreen(
    viewModel: UI2DataSourceManagementViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val uiVis = rememberUiVisibility()
    val alphaRaw by viewModel.alpha.observeAsState()
    val haRaw by viewModel.ha.observeAsState()
    val esbnRaw by viewModel.esbn.observeAsState()
    val octopusRaw by viewModel.octopus.observeAsState()
    val solisRaw by viewModel.solis.observeAsState()
    val fetchMap by viewModel.fetchStatus.observeAsState(emptyMap())
    val haSensors by viewModel.haSensors.observeAsState()
    val pvgis by viewModel.pvgis.observeAsState()
    val cds by viewModel.cds.observeAsState()
    val prices by viewModel.prices.observeAsState()
    val busy by viewModel.busy.observeAsState(false)
    val toast by viewModel.toast.observeAsState()
    // v2 enrichment status — used to decide which AlphaESS rows surface the
    // Migrate button. Missing-meta or transformVersion < CURRENT → stale.
    val alphaMetas by viewModel.alphaTransformMeta.observeAsState(emptyList())
    val staleByAlphaSn: Map<String, Boolean> = remember(alphaMetas, alphaRaw) {
        val byVersion = alphaMetas.associate { it.sysSn to it.transformVersion }
        alphaRaw?.systems.orEmpty().associate { sys ->
            sys.sysSn to ((byVersion[sys.sysSn] ?: AlphaESSTransformMeta.TRANSFORM_VERSION_V1)
                    < AlphaESSTransformMeta.TRANSFORM_VERSION_CURRENT)
        }
    }

    // Merge the persistent SourceState (DataStore + DB) with the live
    // WorkManager status (per-SN fetching/scheduled). Done in the activity so
    // the VM doesn't have to re-emit a full SourceState every time a worker
    // ticks.
    val alpha = alphaRaw?.withLiveFetch(fetchMap)
    val ha = haRaw?.withLiveFetch(fetchMap)
    val esbn = esbnRaw?.withLiveFetch(fetchMap)
    val octopus = octopusRaw?.withLiveFetch(fetchMap)
    val solis = solisRaw?.withLiveFetch(fetchMap)
    val (showHints, toggleShowHints) = rememberShowHints()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDrawer by remember { mutableStateOf(false) }

    LaunchedEffect(toast?.tag) {
        val t = toast ?: return@LaunchedEffect
        scope.launch {
            snackbarHostState.showSnackbar(t.message)
            viewModel.acknowledgeToast()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ui2_drawer_data_sources)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ui2_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.ui2_menu))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showHints) {
                    item("intro") { IntroHint() }
                }
                // Source-visibility gating (App settings): a hidden source's card is
                // not rendered — its data and any scheduled fetches are untouched.
                if (uiVis.alphaess) item("alpha") {
                    SourceAccordion(
                        title = stringResource(R.string.ui2_dsm_alpha_title),
                        subtitle = stringResource(R.string.ui2_dsm_alpha_sub),
                        state = alpha,
                        showHints = showHints,
                        body = {
                            AlphaSection(
                                state = alpha,
                                showHints = showHints,
                                onSetCredentials = viewModel::setAlphaCredentials,
                                onSelect = viewModel::selectAlphaSystem,
                                onFetch = viewModel::fetchAlpha,
                                onCancel = viewModel::cancelFetch,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange,
                                onImportFile = viewModel::importAlphaFile,
                                onExportFolder = viewModel::exportAlpha,
                                staleByAlphaSn = staleByAlphaSn,
                                onMigrate = viewModel::runMigration,
                                onRemoveSource = { viewModel.deleteEntireSource(Importer.ALPHAESS) }
                            )
                        }
                    )
                }
                if (uiVis.homeassistant) item("ha") {
                    SourceAccordion(
                        title = stringResource(R.string.home_assistant),
                        subtitle = stringResource(R.string.ui2_dsm_ha_sub),
                        state = ha,
                        showHints = showHints,
                        body = {
                            HASection(
                                state = ha,
                                sensors = haSensors,
                                showHints = showHints,
                                onRediscover = viewModel::discoverHA,
                                onRediscoverStored = viewModel::rediscoverHA,
                                onFetch = viewModel::fetchHA,
                                onCancel = viewModel::cancelFetch,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange,
                                onRemoveSource = { viewModel.deleteEntireSource(Importer.HOME_ASSISTANT) },
                                onDeviceChange = viewModel::setHaDeviceClassification
                            )
                        }
                    )
                }
                if (uiVis.esbn) item("esbn") {
                    SourceAccordion(
                        title = stringResource(R.string.brand_esbn),
                        subtitle = stringResource(R.string.ui2_dsm_esbn_sub),
                        state = esbn,
                        showHints = showHints,
                        body = {
                            EsbnSection(
                                state = esbn,
                                showHints = showHints,
                                onImportFile = viewModel::importEsbnFile,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange,
                                onExportFolder = viewModel::exportEsbn,
                                onRemoveSource = { viewModel.deleteEntireSource(Importer.ESBNHDF) }
                            )
                        }
                    )
                }
                if (uiVis.octopus) item("octopus") {
                    SourceAccordion(
                        title = stringResource(R.string.octopus_energy),
                        subtitle = stringResource(R.string.ui2_dsm_octopus_sub),
                        state = octopus,
                        showHints = showHints,
                        body = {
                            OctopusSection(
                                state = octopus,
                                showHints = showHints,
                                onSetCredentials = viewModel::setOctopusCredentials,
                                onSelect = viewModel::selectOctopusSystem,
                                onFetch = viewModel::fetchOctopus,
                                onCancel = viewModel::cancelFetch,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange,
                                onImportFile = viewModel::importOctopusFile,
                                onRemoveSource = { viewModel.deleteEntireSource(Importer.OCTOPUS) }
                            )
                        }
                    )
                }
                if (uiVis.solis) item("solis") {
                    SourceAccordion(
                        title = stringResource(R.string.brand_solis),
                        subtitle = stringResource(R.string.ui2_dsm_solis_sub),
                        state = solis,
                        showHints = showHints,
                        body = {
                            SolisSection(
                                state = solis,
                                showHints = showHints,
                                onSetCredentials = viewModel::setSolisCredentials,
                                onSelect = viewModel::selectSolisStation,
                                onFetch = viewModel::fetchSolis,
                                onCancel = viewModel::cancelFetch,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange,
                                onRemoveSource = { viewModel.deleteEntireSource(Importer.SOLIS) }
                            )
                        }
                    )
                }
                if (uiVis.pvgis) item("pvgis") {
                    WeatherSourceAccordion(
                        title = stringResource(R.string.ui2_dsm_pvgis_title),
                        subtitle = stringResource(R.string.ui2_dsm_pvgis_sub),
                        state = pvgis,
                        showHints = showHints,
                        showCredentials = false,
                        emptyHint = stringResource(R.string.ui2_dsm_pvgis_empty_hint),
                        entryNoun = stringResource(R.string.ui2_dsm_noun_location),
                        onSetCredentials = null,
                        onClearAll = viewModel::deleteAllPvgisCache,
                        onDeleteEntry = viewModel::deletePvCacheEntry,
                        onRemoveSource = null
                    )
                }
                if (uiVis.cds) item("cds") {
                    WeatherSourceAccordion(
                        title = stringResource(R.string.ui2_dsm_cds_title),
                        subtitle = stringResource(R.string.ui2_dsm_cds_sub),
                        state = cds,
                        showHints = showHints,
                        showCredentials = true,
                        emptyHint = stringResource(R.string.ui2_dsm_cds_empty_hint),
                        entryNoun = stringResource(R.string.ui2_dsm_noun_dataset),
                        onSetCredentials = viewModel::setCdsCredentials,
                        onClearAll = viewModel::deleteAllCdsCache,
                        onDeleteEntry = viewModel::deleteCdsCacheEntry,
                        onRemoveSource = viewModel::removeCdsSource
                    )
                }
                // Wholesale price cache behind dynamic tariff plans. Gated by the
                // edition's capability (IE wholesale market / GB Octopus Agile),
                // not by a ui_visibility flag — it's a cache view, and hiding it
                // while cached files exist would leave them unmanageable.
                if (RegionProfiles.current.dynamicMarkets.isNotEmpty() ||
                    RegionProfiles.current.hasOctopus) item("prices") {
                    WeatherSourceAccordion(
                        title = stringResource(R.string.ui2_dsm_prices_title),
                        subtitle = stringResource(R.string.ui2_dsm_prices_sub),
                        state = prices,
                        showHints = showHints,
                        showCredentials = false,
                        emptyHint = stringResource(R.string.ui2_dsm_prices_empty_hint),
                        entryNoun = stringResource(R.string.ui2_dsm_noun_market_year),
                        onSetCredentials = null,
                        onClearAll = viewModel::deleteAllPriceCache,
                        onDeleteEntry = viewModel::deletePriceCacheEntry,
                        onRemoveSource = null
                    )
                }
            }

            if (busy) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            // Right-side menu, identical pattern to every other UI2 screen.
            AnimatedVisibility(visible = showDrawer, enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showDrawer = false })
            }
            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInHorizontally(tween(220)) { it },
                exit = slideOutHorizontally(tween(220)) { it },
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(280.dp)
            ) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize()) {
                    UI2DrawerContent(
                        showHints = showHints,
                        onShowHintsChange = { if (it != showHints) toggleShowHints() },
                        onSwitchLegacy = { showDrawer = false; onClose() },
                        onClose = { showDrawer = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroHint() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.ui2_dsm_about_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.ui2_dsm_about_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceAccordion(
    title: String,
    subtitle: String,
    state: SourceState?,
    showHints: Boolean,
    body: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusChip(state)
                Column(Modifier.weight(1f)) {
                    Text(title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val systemCount = state?.systems?.size ?: 0
                if (systemCount > 0 && !expanded) {
                    Text(pluralStringResource(R.plurals.ui2_dsm_n_systems, systemCount, systemCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    body()
                }
            }
        }
    }
}

/** Compact "connected / not configured / invalid" indicator. */
@Composable
private fun StatusChip(state: SourceState?) {
    val (icon, tint, label) = when {
        state == null -> Triple(Icons.Outlined.Warning,
            MaterialTheme.colorScheme.outline, stringResource(R.string.ui2_dsm_loading))
        state.importer == Importer.ESBNHDF -> Triple(Icons.Default.UploadFile,
            MaterialTheme.colorScheme.primary, stringResource(R.string.ui2_dsm_chip_file))
        // Systems present but no credentials (e.g. imported via a snapshot) —
        // flag clearly as needing attention rather than a neutral "Not set".
        !state.credentialsConfigured && state.systems.isNotEmpty() ->
            Triple(Icons.Outlined.Warning, MaterialTheme.colorScheme.error,
                stringResource(R.string.ui2_dsm_chip_set_creds))
        !state.credentialsConfigured -> Triple(Icons.Default.CloudOff,
            MaterialTheme.colorScheme.outline, stringResource(R.string.ui2_dsm_chip_not_set))
        !state.credentialsKnownGood -> Triple(Icons.Outlined.Warning,
            MaterialTheme.colorScheme.error, stringResource(R.string.ui2_dsm_chip_invalid))
        else -> Triple(Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary, stringResource(R.string.ui2_dsm_chip_ready))
    }
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = CircleShape,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

// ── AlphaESS section ──────────────────────────────────────────────────

@Composable
private fun AlphaSection(
    state: SourceState?,
    showHints: Boolean,
    onSetCredentials: (String, String) -> Unit,
    onSelect: (String) -> Unit,
    onFetch: (String, LocalDateTime) -> Unit,
    onCancel: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit,
    onImportFile: (String, String) -> Unit,
    onExportFolder: (String, String) -> Unit,
    staleByAlphaSn: Map<String, Boolean>,
    onMigrate: (String) -> Unit,
    onRemoveSource: () -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
    var showDeleteSource by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    var pendingFetch by remember { mutableStateOf<ManagedSystem?>(null) }
    // The pickers fire asynchronously; remember which row's button kicked
    // them off so the resulting URI lands against the right SN.
    var importTargetSn by remember { mutableStateOf<String?>(null) }
    var exportTargetSn by remember { mutableStateOf<String?>(null) }
    val pickImportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val sn = importTargetSn
        importTargetSn = null
        if (uri != null && sn != null) onImportFile(sn, uri.toString())
    }
    val pickExportFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val sn = exportTargetSn
        exportTargetSn = null
        if (uri != null && sn != null) onExportFolder(sn, uri.toString())
    }
    if (showHints) {
        HintLine(stringResource(R.string.ui2_dsm_alpha_hint))
    }
    CredentialStrip(
        configured = state?.credentialsConfigured == true,
        good = state?.credentialsKnownGood == true,
        onEdit = { showCreds = true },
        onDeleteSource = if (state?.credentialsConfigured == true || !state?.systems.isNullOrEmpty())
            ({ showDeleteSource = true }) else null
    )
    SystemList(
        systems = state?.systems.orEmpty(),
        selected = state?.selectedSn,
        canFetch = state?.credentialsKnownGood == true,
        onSelect = onSelect,
        onFetch = { sn ->
            val sys = state?.systems?.firstOrNull { it.sysSn == sn } ?: return@SystemList
            pendingFetch = sys
        },
        onCancel = onCancel,
        onDelete = { sys -> pendingDelete = sys },
        onImport = { sn ->
            importTargetSn = sn
            pickImportFile.launch("*/*")
        },
        onExport = { sn ->
            exportTargetSn = sn
            pickExportFolder.launch(null)
        },
        onMigrate = onMigrate,
        isStale = { sn -> staleByAlphaSn[sn] == true }
    )
    if (showCreds) {
        CredentialDialog(
            title = stringResource(R.string.ui2_dsm_alpha_creds_title),
            userLabel = stringResource(R.string.ui2_dsm_app_id),
            passLabel = stringResource(R.string.ui2_dsm_app_secret),
            initialUser = "",
            onDismiss = { showCreds = false },
            onSubmit = { u, p ->
                onSetCredentials(u, p)
                showCreds = false
            }
        )
    }
    if (showDeleteSource) {
        DeleteSourceDialog(
            sourceName = stringResource(R.string.ui2_dsm_alpha_title),
            mentionsCredentials = true,
            onDismiss = { showDeleteSource = false },
            onConfirm = { onRemoveSource(); showDeleteSource = false }
        )
    }
    pendingDelete?.let { sys ->
        DeleteDialog(
            sysSn = sys.sysSn,
            availableStart = sys.startDate,
            availableEnd = sys.endDate,
            onDismiss = { pendingDelete = null },
            onDeleteAll = { onDeleteAll(sys.sysSn); pendingDelete = null },
            onDeleteRange = { f, t -> onDeleteRange(sys.sysSn, f, t); pendingDelete = null }
        )
    }
    pendingFetch?.let { sys ->
        FetchStartDialog(
            sysSn = sys.sysSn,
            lastDataDate = sys.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { pendingFetch = null },
            onConfirm = { start ->
                onFetch(sys.sysSn, start)
                pendingFetch = null
            }
        )
    }
}

// ── Home Assistant section ─────────────────────────────────────────────

@Composable
private fun HASection(
    state: SourceState?,
    sensors: HASensorSnapshot?,
    showHints: Boolean,
    onRediscover: (String, String) -> Unit,
    onRediscoverStored: () -> Unit,
    onFetch: (LocalDateTime) -> Unit,
    onCancel: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit,
    onRemoveSource: () -> Unit,
    onDeviceChange: (String, String, Boolean) -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
    var showDeleteSource by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    var pendingFetch by remember { mutableStateOf<ManagedSystem?>(null) }
    if (showHints) {
        HintLine(stringResource(R.string.ui2_dsm_ha_hint))
    }
    CredentialStrip(
        configured = state?.credentialsConfigured == true,
        good = state?.credentialsKnownGood == true,
        onEdit = { showCreds = true },
        onDeleteSource = if (state?.credentialsConfigured == true || !state?.systems.isNullOrEmpty())
            ({ showDeleteSource = true }) else null
    )

    SystemList(
        systems = state?.systems.orEmpty(),
        selected = state?.selectedSn ?: "HomeAssistant",
        // Match AlphaESS: visible whenever credentials are usable. The VM
        // toasts "Discover sensors first" if the underlying worker needs
        // them — keeping the UI's affordances symmetrical across sources
        // is more valuable than gating the button here.
        canFetch = state?.credentialsKnownGood == true,
        onSelect = { /* HA has a single canonical system */ },
        onFetch = { sn ->
            val sys = state?.systems?.firstOrNull { it.sysSn == sn } ?: return@SystemList
            pendingFetch = sys
        },
        onCancel = onCancel,
        onDelete = { sys -> pendingDelete = sys }
    )

    // Sensors are a configuration detail most users only revisit when
    // something's wrong. Hide behind a nested accordion so the section's
    // primary actions (fetch / delete) stay above the fold.
    HASensorsAccordion(
        sensors = sensors,
        showHints = showHints,
        canRediscover = state?.credentialsConfigured == true,
        // Re-discovery reuses the stored credentials — retyping a long-lived
        // token to refresh a sensor list was a dead end (users cancelled the
        // dialog and nothing happened). Changing credentials is the strip's
        // Edit action, which still opens the dialog.
        onRediscover = onRediscoverStored,
        onDeviceChange = onDeviceChange
    )

    // Backfill is a multi-step flow (source → timeframe → series → preview →
    // commit) — too much for a nested accordion, so it opens its own wizard.
    run {
        val context = LocalContext.current
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().clickable(
                enabled = state?.credentialsKnownGood == true
            ) {
                context.startActivity(
                    android.content.Intent(context, UI2HaBackfillActivity::class.java))
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui2_habf_title),
                        style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (state?.credentialsKnownGood == true)
                            R.string.ui2_dsm_backfill_sub
                        else R.string.ui2_dsm_connect_ha_first),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showCreds) {
        CredentialDialog(
            title = stringResource(R.string.home_assistant),
            userLabel = stringResource(R.string.ui2_dsm_host_url),
            passLabel = stringResource(R.string.ui2_dsm_lla_token),
            initialUser = "ws://homeassistant.local:8123/api/websocket",
            onDismiss = { showCreds = false },
            onSubmit = { h, t ->
                onRediscover(h, t)
                showCreds = false
            }
        )
    }
    if (showDeleteSource) {
        DeleteSourceDialog(
            sourceName = stringResource(R.string.home_assistant),
            mentionsCredentials = true,
            onDismiss = { showDeleteSource = false },
            onConfirm = { onRemoveSource(); showDeleteSource = false }
        )
    }
    pendingDelete?.let { sys ->
        DeleteDialog(
            sysSn = sys.sysSn,
            availableStart = sys.startDate,
            availableEnd = sys.endDate,
            onDismiss = { pendingDelete = null },
            onDeleteAll = { onDeleteAll(sys.sysSn); pendingDelete = null },
            onDeleteRange = { f, t -> onDeleteRange(sys.sysSn, f, t); pendingDelete = null }
        )
    }
    pendingFetch?.let { sys ->
        FetchStartDialog(
            sysSn = sys.sysSn,
            lastDataDate = sys.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { pendingFetch = null },
            onConfirm = { start ->
                onFetch(start)
                pendingFetch = null
            }
        )
    }
}

/**
 * Collapsible "Energy sensors" panel — collapsed by default so it doesn't
 * push the primary actions (fetch / delete) off-screen. Header shows a
 * one-line summary of what's been discovered (or "Not yet discovered").
 */
@Composable
private fun HASensorsAccordion(
    sensors: HASensorSnapshot?,
    showHints: Boolean,
    canRediscover: Boolean,
    onRediscover: () -> Unit,
    onDeviceChange: (String, String, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = when {
        sensors == null -> stringResource(R.string.ui2_dsm_not_discovered)
        else -> {
            val d = sensors.devices.size
            stringResource(R.string.ui2_dsm_sensor_summary,
                sensors.grid.size, sensors.gridExports.size,
                sensors.solar.size, sensors.batteries.size) +
                if (d > 0) " " + pluralStringResource(R.plurals.ui2_dsm_n_devices, d, d) else ""
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui2_dsm_energy_sensors),
                        style = MaterialTheme.typography.titleSmall)
                    Text(summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showHints && sensors == null) {
                            Text(stringResource(R.string.ui2_dsm_rediscover_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                        } else Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = onRediscover, enabled = canRediscover) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(if (sensors == null) R.string.ui2_dsm_discover
                                                else R.string.ui2_dsm_rediscover))
                        }
                    }
                    if (sensors != null) {
                        if (sensors.serverRange != null) {
                            Text(stringResource(R.string.ui2_dsm_stats_on_server,
                                    sensors.serverRange),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HASensorTable(sensors)
                        if (sensors.devices.isNotEmpty()) {
                            Text(stringResource(R.string.ui2_dsm_individual_devices),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (showHints) {
                                Text(stringResource(R.string.ui2_dsm_devices_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HADeviceTable(sensors.devices, onDeviceChange)
                        }
                    } else if (!canRediscover) {
                        Text(stringResource(R.string.ui2_dsm_set_creds_first),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// Fixed column widths so the row layout never reflows when a different role
// is selected (a variable-width role button made everything after it jump).
private val DEVICE_ROLE_COLUMN = 116.dp
private val DEVICE_REMOVE_COLUMN = 68.dp

/**
 * "Individual devices" as a fixed-column Device | Role | Remove-from-load
 * table. Each row keeps its editable controls: role dropdown (Ignore / EV /
 * Hot water / Heat pump) and the "Remove from load" opt-in. Persists per
 * change via [UI2DataSourceManagementViewModel.setHaDeviceClassification] —
 * the sensor mapping is handled here; no legacy screen involved.
 */
@Composable
private fun HADeviceTable(
    devices: List<HADeviceRow>,
    onDeviceChange: (String, String, Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ui2_dsm_device),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.ui2_dsm_role),
                modifier = Modifier.width(DEVICE_ROLE_COLUMN),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.ui2_dsm_remove_from_load),
                modifier = Modifier.width(DEVICE_REMOVE_COLUMN),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        devices.forEach { device ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            HADeviceTableRow(device, onDeviceChange)
        }
    }
}

@Composable
private fun HADeviceTableRow(
    device: HADeviceRow,
    onDeviceChange: (String, String, Boolean) -> Unit
) {
    // roleNames are persisted classification values — never translated.
    val roleNames = listOf("OTHER", "EV", "HOT_WATER", "HEAT_PUMP")
    val roleLabels = listOf(
        stringResource(R.string.ui2_dsm_role_ignore),
        stringResource(R.string.ui2_component_ev),
        stringResource(R.string.ui2_component_hot_water),
        stringResource(R.string.ui2_component_heat_pump)
    )
    val ignoreLabel = roleLabels[0]
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(device.label,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace)
        Box(Modifier.width(DEVICE_ROLE_COLUMN)) {
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(roleLabels.getOrElse(roleNames.indexOf(device.role)) { ignoreLabel },
                    maxLines = 1)
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                roleLabels.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuOpen = false
                            onDeviceChange(device.statId, roleNames[i], device.adjust)
                        }
                    )
                }
            }
        }
        Box(Modifier.width(DEVICE_REMOVE_COLUMN), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = device.adjust,
                onCheckedChange = { onDeviceChange(device.statId, device.role, it) }
            )
        }
    }
}

/**
 * Two-column Role | Sensor table of the discovered energy sensors. Read-only —
 * the "Individual devices" table below keeps the editable controls.
 * Sensor ids wrap rather than ellipsize: the distinguishing suffix of an
 * entity id is exactly what gets cut off by single-line truncation.
 */
@Composable
private fun HASensorTable(sensors: HASensorSnapshot) {
    val gridImport = stringResource(R.string.ui2_habf_series_buy)
    val gridExport = stringResource(R.string.ui2_habf_series_feed)
    val solarWord = stringResource(R.string.ui2_graphs_solar)
    val batteryWord = stringResource(R.string.ui2_graphs_battery)
    val rows = buildList {
        sensors.grid.forEach { add(gridImport to it) }
        sensors.gridExports.forEach { add(gridExport to it) }
        sensors.solar.forEach { add(solarWord to it) }
        sensors.batteries.forEachIndexed { index, (charging, discharging) ->
            val battery = if (sensors.batteries.size > 1)
                stringResource(R.string.ui2_dsm_battery_n, index + 1) else batteryWord
            charging?.let { add(stringResource(R.string.ui2_dsm_charge_suffix, battery) to it) }
            discharging?.let { add(stringResource(R.string.ui2_dsm_discharge_suffix, battery) to it) }
        }
    }
    if (rows.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row {
            Text(stringResource(R.string.ui2_dsm_role),
                modifier = Modifier.width(104.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.ui2_dsm_sensor),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEach { (role, statId) ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(role,
                    modifier = Modifier.width(104.dp),
                    style = MaterialTheme.typography.bodySmall)
                Text(statId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// ── ESBN section ──────────────────────────────────────────────────────

@Composable
private fun EsbnSection(
    state: SourceState?,
    showHints: Boolean,
    onImportFile: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit,
    onExportFolder: (String, String) -> Unit,
    onRemoveSource: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    var showDeleteSource by remember { mutableStateOf(false) }
    var exportTargetMprn by remember { mutableStateOf<String?>(null) }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onImportFile(uri.toString())
    }
    val pickExportFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val mprn = exportTargetMprn
        exportTargetMprn = null
        if (uri != null && mprn != null) onExportFolder(mprn, uri.toString())
    }
    // Always visible — even if hints off — because it explains why credentials
    // are absent. Calling it a "hint" understates the user-blocking nature.
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Warning, null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.ui2_dsm_esbn_deprecated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { pickFile.launch("*/*") },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Download, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ui2_dsm_import_hdf))
        }
        if (!state?.systems.isNullOrEmpty()) {
            IconButton(onClick = { showDeleteSource = true }) {
                Icon(Icons.Default.Delete,
                    contentDescription = stringResource(R.string.ui2_dsm_remove_source_cd),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    SystemList(
        systems = state?.systems.orEmpty(),
        selected = state?.selectedSn,
        canFetch = false,
        onSelect = { /* read-only */ },
        onFetch = { /* no cloud fetch */ },
        onCancel = { /* no cloud fetch */ },
        onDelete = { sys -> pendingDelete = sys },
        // ESBN import stays section-level (the "Import HDF file" button
        // above) — the file's MPRN is read from its contents, so there's
        // no natural per-row import here. Export *is* per-MPRN.
        onExport = { mprn ->
            exportTargetMprn = mprn
            pickExportFolder.launch(null)
        }
    )
    if (state?.systems.isNullOrEmpty() && showHints) {
        Text(stringResource(R.string.ui2_dsm_esbn_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    pendingDelete?.let { sys ->
        DeleteDialog(
            sysSn = sys.sysSn,
            availableStart = sys.startDate,
            availableEnd = sys.endDate,
            onDismiss = { pendingDelete = null },
            onDeleteAll = { onDeleteAll(sys.sysSn); pendingDelete = null },
            onDeleteRange = { f, t -> onDeleteRange(sys.sysSn, f, t); pendingDelete = null }
        )
    }
    if (showDeleteSource) {
        DeleteSourceDialog(
            sourceName = stringResource(R.string.ui2_dsm_esbn_data),
            mentionsCredentials = false,
            onDismiss = { showDeleteSource = false },
            onConfirm = { onRemoveSource(); showDeleteSource = false }
        )
    }
}

// ── Octopus Energy section ────────────────────────────────────────────

@Composable
private fun OctopusSection(
    state: SourceState?,
    showHints: Boolean,
    onSetCredentials: (String, String) -> Unit,
    onSelect: (String) -> Unit,
    onFetch: (String, LocalDateTime) -> Unit,
    onCancel: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit,
    onImportFile: (String, String) -> Unit,
    onRemoveSource: () -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
    var showDeleteSource by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    var pendingFetch by remember { mutableStateOf<ManagedSystem?>(null) }
    var importTargetSn by remember { mutableStateOf<String?>(null) }
    val pickImportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val sn = importTargetSn
        importTargetSn = null
        if (uri != null && sn != null) onImportFile(sn, uri.toString())
    }
    if (showHints) {
        HintLine(stringResource(R.string.ui2_dsm_octopus_hint))
    }
    CredentialStrip(
        configured = state?.credentialsConfigured == true,
        good = state?.credentialsKnownGood == true,
        onEdit = { showCreds = true },
        onDeleteSource = if (state?.credentialsConfigured == true || !state?.systems.isNullOrEmpty())
            ({ showDeleteSource = true }) else null
    )
    SystemList(
        systems = state?.systems.orEmpty(),
        selected = state?.selectedSn,
        canFetch = state?.credentialsKnownGood == true,
        onSelect = onSelect,
        onFetch = { sn ->
            val sys = state?.systems?.firstOrNull { it.sysSn == sn } ?: return@SystemList
            pendingFetch = sys
        },
        onCancel = onCancel,
        onDelete = { sys -> pendingDelete = sys },
        // Per-row CSV import: the dashboard-download fallback for keyless use.
        onImport = { sn ->
            importTargetSn = sn
            pickImportFile.launch("*/*")
        }
    )
    if (state?.systems.isNullOrEmpty() && showHints) {
        Text(stringResource(R.string.ui2_dsm_octopus_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showCreds) {
        CredentialDialog(
            title = stringResource(R.string.ui2_dsm_octopus_creds_title),
            userLabel = stringResource(R.string.ui2_dsm_account_number),
            passLabel = stringResource(R.string.ui2_dsm_api_key),
            initialUser = "",
            onDismiss = { showCreds = false },
            onSubmit = { u, p ->
                onSetCredentials(u, p)
                showCreds = false
            }
        )
    }
    if (showDeleteSource) {
        DeleteSourceDialog(
            sourceName = stringResource(R.string.octopus_energy),
            mentionsCredentials = true,
            onDismiss = { showDeleteSource = false },
            onConfirm = { onRemoveSource(); showDeleteSource = false }
        )
    }
    pendingDelete?.let { sys ->
        DeleteDialog(
            sysSn = sys.sysSn,
            availableStart = sys.startDate,
            availableEnd = sys.endDate,
            onDismiss = { pendingDelete = null },
            onDeleteAll = { onDeleteAll(sys.sysSn); pendingDelete = null },
            onDeleteRange = { f, t -> onDeleteRange(sys.sysSn, f, t); pendingDelete = null }
        )
    }
    pendingFetch?.let { sys ->
        FetchStartDialog(
            sysSn = sys.sysSn,
            lastDataDate = sys.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { pendingFetch = null },
            onConfirm = { start ->
                onFetch(sys.sysSn, start)
                pendingFetch = null
            }
        )
    }
}

@Composable
private fun SolisSection(
    state: SourceState?,
    showHints: Boolean,
    onSetCredentials: (String, String) -> Unit,
    onSelect: (String) -> Unit,
    onFetch: (String, LocalDateTime) -> Unit,
    onCancel: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit,
    onRemoveSource: () -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
    var showDeleteSource by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ManagedSystem?>(null) }
    var pendingFetch by remember { mutableStateOf<ManagedSystem?>(null) }
    if (showHints) {
        HintLine(stringResource(R.string.ui2_dsm_solis_hint))
    }
    CredentialStrip(
        configured = state?.credentialsConfigured == true,
        good = state?.credentialsKnownGood == true,
        onEdit = { showCreds = true },
        onDeleteSource = if (state?.credentialsConfigured == true || !state?.systems.isNullOrEmpty())
            ({ showDeleteSource = true }) else null
    )
    SystemList(
        systems = state?.systems.orEmpty(),
        selected = state?.selectedSn,
        canFetch = state?.credentialsKnownGood == true,
        onSelect = onSelect,
        onFetch = { sn ->
            val sys = state?.systems?.firstOrNull { it.sysSn == sn } ?: return@SystemList
            pendingFetch = sys
        },
        onCancel = onCancel,
        onDelete = { sys -> pendingDelete = sys }
        // No per-row file import — Solis is cloud-sync only (UI2-only source).
    )
    if (state?.systems.isNullOrEmpty() && showHints) {
        Text(stringResource(R.string.ui2_dsm_solis_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showCreds) {
        CredentialDialog(
            title = stringResource(R.string.ui2_dsm_solis_creds_title),
            userLabel = stringResource(R.string.ui2_dsm_api_key_id),
            passLabel = stringResource(R.string.ui2_dsm_api_secret),
            initialUser = "",
            onDismiss = { showCreds = false },
            onSubmit = { u, p ->
                onSetCredentials(u, p)
                showCreds = false
            }
        )
    }
    if (showDeleteSource) {
        DeleteSourceDialog(
            sourceName = stringResource(R.string.brand_solis),
            mentionsCredentials = true,
            onDismiss = { showDeleteSource = false },
            onConfirm = { onRemoveSource(); showDeleteSource = false }
        )
    }
    pendingDelete?.let { sys ->
        DeleteDialog(
            sysSn = sys.sysSn,
            availableStart = sys.startDate,
            availableEnd = sys.endDate,
            onDismiss = { pendingDelete = null },
            onDeleteAll = { onDeleteAll(sys.sysSn); pendingDelete = null },
            onDeleteRange = { f, t -> onDeleteRange(sys.sysSn, f, t); pendingDelete = null }
        )
    }
    pendingFetch?.let { sys ->
        FetchStartDialog(
            sysSn = sys.sysSn,
            lastDataDate = sys.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { pendingFetch = null },
            onConfirm = { start ->
                onFetch(sys.sysSn, start)
                pendingFetch = null
            }
        )
    }
}

// ── Weather/PV sources (PVGIS, CDS) — Phase 5.5 ────────────────────────
//
// File-cache-shaped, not credential-probe-shaped like the importers above.
// One accordion lists the matching cached responses in the EPO folder and
// offers per-file + clear-all deletion; CDS additionally carries an encrypted
// API key (no live probe — validity is unknown until the first real fetch).

@Composable
private fun WeatherSourceAccordion(
    title: String,
    subtitle: String,
    state: WeatherSourceState?,
    showHints: Boolean,
    showCredentials: Boolean,
    emptyHint: String,
    entryNoun: String,
    onSetCredentials: ((String, String) -> Unit)?,
    onClearAll: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onRemoveSource: (() -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherStatusChip(state, showCredentials)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val n = state?.entries?.size ?: 0
                if (n > 0 && !expanded) {
                    Text(stringResource(R.string.ui2_dsm_n_cached, n),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeatherSourceBody(state, showHints, emptyHint, entryNoun,
                        onSetCredentials, onClearAll, onDeleteEntry, onRemoveSource)
                }
            }
        }
    }
}

@Composable
private fun WeatherSourceBody(
    state: WeatherSourceState?,
    showHints: Boolean,
    emptyHint: String,
    entryNoun: String,
    onSetCredentials: ((String, String) -> Unit)?,
    onClearAll: () -> Unit,
    onDeleteEntry: (String) -> Unit,
    onRemoveSource: (() -> Unit)?
) {
    var showCreds by remember { mutableStateOf(false) }
    var showClearAll by remember { mutableStateOf(false) }

    if (onSetCredentials != null) {
        // CDS-specific strip: we don't probe in Phase 5.5, so we never claim
        // "last check OK/failed" — only Not configured / Configured.
        WeatherCredentialStrip(
            configured = state?.credentialsConfigured == true,
            onEdit = { showCreds = true },
            onDeleteSource = if (state?.credentialsConfigured == true) onRemoveSource else null
        )
    }

    if (state == null) {
        Text(stringResource(R.string.ui2_dsm_loading_ellipsis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    if (state.entries.isEmpty()) {
        if (showHints) HintLine(emptyHint)
        Text(stringResource(R.string.ui2_dsm_nothing_cached),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.entries.forEach { e -> WeatherCacheRow(e, onDelete = { onDeleteEntry(e.id) }) }
        }
        OutlinedButton(
            onClick = { showClearAll = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ui2_dsm_clear_all_n, state.entries.size),
                color = MaterialTheme.colorScheme.error)
        }
    }

    if (showCreds && onSetCredentials != null) {
        CredentialDialog(
            title = stringResource(R.string.brand_cds),
            userLabel = stringResource(R.string.ui2_dsm_cds_url),
            passLabel = stringResource(R.string.ui2_dsm_pat),
            initialUser = CDS_DEFAULT_URL,
            onDismiss = { showCreds = false },
            onSubmit = { u, p -> onSetCredentials(u, p); showCreds = false }
        )
    }
    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text(stringResource(R.string.ui2_dsm_clear_all_title)) },
            text = {
                Text(stringResource(R.string.ui2_dsm_clear_all_body, entryNoun),
                    style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = { onClearAll(); showClearAll = false },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui2_dsm_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun WeatherCacheRow(entry: WeatherCacheEntry, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.detail?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete,
                    contentDescription = stringResource(R.string.ui2_dsm_delete_cached_cd),
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Status chip for a weather source: cache count for anonymous (PVGIS), credential state for CDS. */
@Composable
private fun WeatherStatusChip(state: WeatherSourceState?, showCredentials: Boolean) {
    val (icon, tint) = when {
        state == null -> Icons.Outlined.Warning to MaterialTheme.colorScheme.outline
        showCredentials && !state.credentialsConfigured ->
            Icons.Default.CloudOff to MaterialTheme.colorScheme.outline
        else -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
    }
    Surface(color = tint.copy(alpha = 0.12f), shape = CircleShape, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** Honest credential strip for weather sources: Not configured / Configured (no probe). */
@Composable
private fun WeatherCredentialStrip(
    configured: Boolean,
    onEdit: () -> Unit,
    onDeleteSource: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui2_dsm_credentials),
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(if (configured) R.string.ui2_dsm_configured_first_fetch
                                   else R.string.ui2_dsm_not_configured),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (configured) R.string.ui2_dsm_update else R.string.ui2_dsm_set))
            }
            if (onDeleteSource != null) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDeleteSource) {
                    Icon(Icons.Default.Delete,
                        contentDescription = stringResource(R.string.ui2_dsm_remove_source_cd),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── Shared widgets ─────────────────────────────────────────────────────

@Composable
private fun CredentialStrip(
    configured: Boolean,
    good: Boolean,
    onEdit: () -> Unit,
    // When non-null, a trashcan appears beside the credentials action that
    // removes the whole source — readings AND credentials. Omitted (null)
    // when there is nothing configured to remove.
    onDeleteSource: (() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ui2_dsm_credentials),
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(when {
                        !configured -> R.string.ui2_dsm_not_configured
                        !good -> R.string.ui2_dsm_configured_failed
                        else -> R.string.ui2_dsm_configured_ok
                    }),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (configured && !good)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (configured) R.string.ui2_dsm_update else R.string.ui2_dsm_set))
            }
            if (onDeleteSource != null) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDeleteSource) {
                    Icon(Icons.Default.Delete,
                        contentDescription = stringResource(R.string.ui2_dsm_remove_source_cd),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Confirmation for removing an entire source. Spells out that this wipes both
 * the stored readings and (where applicable) the credentials — the per-system
 * Delete dialog keeps credentials, this one does not.
 */
@Composable
private fun DeleteSourceDialog(
    sourceName: String,
    mentionsCredentials: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui2_dsm_remove_source_title, sourceName)) },
        text = {
            Text(
                stringResource(if (mentionsCredentials) R.string.ui2_dsm_remove_source_body_creds
                               else R.string.ui2_dsm_remove_source_body),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ui2_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

@Composable
private fun SystemList(
    systems: List<ManagedSystem>,
    selected: String?,
    canFetch: Boolean,
    onSelect: (String) -> Unit,
    onFetch: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (ManagedSystem) -> Unit,
    // Optional secondary actions rendered as a second button row below
    // Fetch/Delete. Null → row omitted. Used for AlphaESS (import+export)
    // and ESBN (export only). HA passes neither.
    onImport: ((String) -> Unit)? = null,
    onExport: ((String) -> Unit)? = null,
    // AlphaESS only: Migrate button appears on a third row when isStale(sn)
    // is true and onMigrate is wired. Null callbacks → row omitted entirely.
    onMigrate: ((String) -> Unit)? = null,
    isStale: ((String) -> Boolean)? = null
) {
    if (systems.isEmpty()) {
        Text(stringResource(R.string.ui2_dsm_no_systems),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        systems.forEach { sys ->
            SystemRow(sys, selected, canFetch, onSelect, onFetch, onCancel, onDelete,
                onImport, onExport, onMigrate, isStale)
        }
    }
}

@Composable
private fun SystemRow(
    sys: ManagedSystem,
    selected: String?,
    canFetch: Boolean,
    onSelect: (String) -> Unit,
    onFetch: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (ManagedSystem) -> Unit,
    onImport: ((String) -> Unit)? = null,
    onExport: ((String) -> Unit)? = null,
    onMigrate: ((String) -> Unit)? = null,
    isStale: ((String) -> Boolean)? = null
) {
    val isSelected = sys.sysSn == selected
    val active = sys.fetching || sys.scheduled
    Surface(
        color = when {
            sys.fetching -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                sys.fetching -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                isSelected   -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else         -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            }
        ),
        modifier = Modifier.fillMaxWidth().clickable { onSelect(sys.sysSn) }
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (sys.fetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(sys.sysSn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sys.scheduled) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape
                    ) {
                        Text(stringResource(R.string.ui2_dsm_auto_sync),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            // Status line — tells the user exactly what's happening (data range
            // when idle, "Fetching X" when a catch-up is mid-flight).
            val statusText = when {
                sys.fetching && sys.progress != null ->
                    stringResource(R.string.ui2_dsm_fetching_progress, sys.progress.take(40))
                sys.fetching -> stringResource(R.string.ui2_dsm_fetching)
                sys.dateRange != null -> sys.dateRange
                else -> stringResource(R.string.ui2_dsm_no_data_yet)
            }
            Text(statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (sys.fetching) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canFetch) {
                    OutlinedButton(
                        onClick = { if (active) onCancel(sys.sysSn) else onFetch(sys.sysSn) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (active) Icons.Default.Stop else Icons.Default.PlayArrow,
                            null, Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(if (active) R.string.ui2_dsm_stop
                                            else R.string.ui2_dsm_fetch))
                    }
                }
                OutlinedButton(
                    onClick = { onDelete(sys) },
                    enabled = !sys.fetching,   // don't let the user delete mid-fetch
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp),
                        tint = if (sys.fetching) MaterialTheme.colorScheme.outline
                               else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui2_delete),
                        color = if (sys.fetching) MaterialTheme.colorScheme.outline
                                else MaterialTheme.colorScheme.error)
                }
            }
            // Secondary actions — file import (re-ingest a previously
            // exported source) and export (write the source to a file
            // for backup / sharing). Each is disabled mid-fetch to avoid
            // racing the underlying writes.
            if (onImport != null || onExport != null) {
                // Import / Export side-by-side at fs<1.6, stacked at fs>=1.6
                // so each label has room to render. AdaptiveCellRow keeps
                // both at 50/50 width through tier B and goes full-width per
                // button at tier C.
                val ioItems = buildList {
                    if (onImport != null) add(DataSourceIoAction.IMPORT)
                    if (onExport != null) add(DataSourceIoAction.EXPORT)
                }
                AdaptiveCellRow(
                    items = ioItems,
                    perRowAtA = ioItems.size, perRowAtB = ioItems.size, perRowAtC = 1,
                    spacing = 6.dp
                ) { action ->
                    when (action) {
                        DataSourceIoAction.IMPORT -> OutlinedButton(
                            onClick = { onImport!!(sys.sysSn) },
                            enabled = !sys.fetching,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.ui2_ie_import))
                        }
                        DataSourceIoAction.EXPORT -> OutlinedButton(
                            onClick = { onExport!!(sys.sysSn) },
                            enabled = !sys.fetching,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.ui2_ie_export))
                        }
                    }
                }
            }
            // Third row: Migrate — surfaced only when the SN's processed rows
            // are pre-v2. Disappears once AlphaESSMigrationWorker stamps the
            // transform meta to TRANSFORM_VERSION_CURRENT on completion.
            if (onMigrate != null && isStale != null && isStale(sys.sysSn)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onMigrate(sys.sysSn) },
                        enabled = !sys.fetching,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_dsm_migrate))
                    }
                }
            }
        }
    }
}

@Composable
private fun HintLine(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Outlined.Warning, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Text(text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CredentialDialog(
    title: String,
    userLabel: String,
    passLabel: String,
    initialUser: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(userLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text(passLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(user.trim(), pass.trim()) }) {
                Text(stringResource(R.string.ui2_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

/**
 * Two-step delete flow.
 *
 *  Step 1: ask "All readings" vs "A date range…" — both options need explicit
 *          confirmation, so this dialog never deletes on first tap.
 *  Step 2 (range only): show an M3 DateRangePicker bounded to the system's
 *          known data range, then "Delete N readings between FROM and TO?".
 *          A second confirmation prevents fat-finger range deletion.
 *
 * If the system has no data range yet (start/end null), only "All readings"
 * is offered — there's nothing to range-pick against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteDialog(
    sysSn: String,
    availableStart: String?,
    availableEnd: String?,
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteRange: (LocalDateTime, LocalDateTime) -> Unit
) {
    val isoFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    val start = remember(availableStart) {
        runCatching { availableStart?.let { LocalDate.parse(it, isoFmt) } }.getOrNull()
    }
    val end = remember(availableEnd) {
        runCatching { availableEnd?.let { LocalDate.parse(it, isoFmt) } }.getOrNull()
    }
    val hasRange = start != null && end != null

    var mode by remember { mutableStateOf<DeleteMode>(DeleteMode.Choose) }

    when (val m = mode) {
        DeleteMode.Choose -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.ui2_dsm_delete_data_title, sysSn)) },
            // Both choices live in the body as full-width OutlinedButtons so
            // neither looks secondary. The AlertDialog "confirmButton" is
            // demoted to Cancel — confirmation always happens on the next
            // step, never on this initial chooser.
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ui2_dsm_delete_choose_body),
                        style = MaterialTheme.typography.bodyMedium)
                    if (hasRange) {
                        Text(stringResource(R.string.ui2_dsm_available_data,
                                start.toString(), end.toString()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(stringResource(R.string.ui2_dsm_no_data_recorded),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = { mode = DeleteMode.ConfirmAll },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ui2_dsm_all_readings),
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (hasRange) {
                        OutlinedButton(
                            onClick = { mode = DeleteMode.PickRange },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui2_dsm_pick_range))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.Cancel, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )

        DeleteMode.ConfirmAll -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.ui2_dsm_delete_all_title)) },
            text = {
                Text(stringResource(R.string.ui2_dsm_delete_all_body, sysSn),
                    style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(onClick = onDeleteAll,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui2_scenarios_delete_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { mode = DeleteMode.Choose }) {
                    Text(stringResource(R.string.ui2_back))
                }
            }
        )

        is DeleteMode.PickRange -> {
            val pickerState = rememberDateRangePickerState(
                initialSelectedStartDateMillis = start?.toUtcMillis(),
                initialSelectedEndDateMillis = end?.toUtcMillis(),
                yearRange = (start!!.year)..(end!!.year)
            )
            // Re-derive the selection as the user picks. M3's own
            // DatePickerDialog has a long-running layout bug where a
            // DateRangePicker's lazy grid demands more height than the
            // dialog allocates and pushes the confirm row off-screen, so
            // we drop down to a plain Dialog and pin the buttons ourselves.
            val pickedStart = pickerState.selectedStartDateMillis?.let {
                LocalDate.ofEpochDay(it / 86_400_000L)
            }
            val pickedEnd = pickerState.selectedEndDateMillis?.let {
                LocalDate.ofEpochDay(it / 86_400_000L)
            }
            val days = if (pickedStart != null && pickedEnd != null)
                (pickedEnd.toEpochDay() - pickedStart.toEpochDay()).toInt() + 1 else 0
            StickyButtonPickerDialog(
                onDismiss = onDismiss,
                confirmEnabled = pickedStart != null && pickedEnd != null,
                confirmLabel = if (pickedStart != null && pickedEnd != null)
                    pluralStringResource(R.plurals.ui2_dsm_ok_delete_days, days, days)
                else stringResource(R.string.ui2_dsm_pick_start_end),
                confirmIcon = Icons.Default.Delete,
                onBack = { mode = DeleteMode.Choose },
                onConfirm = {
                    mode = DeleteMode.ConfirmRange(
                        pickerState.selectedStartDateMillis!!,
                        pickerState.selectedEndDateMillis!!
                    )
                }
            ) {
                DateRangePicker(
                    state = pickerState,
                    title = {
                        Text(stringResource(R.string.ui2_dsm_range_title, sysSn),
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp))
                    },
                    headline = {
                        Text(
                            if (pickedStart != null && pickedEnd != null)
                                "$pickedStart  →  $pickedEnd   ·   " +
                                    pluralStringResource(R.plurals.ui2_dsm_n_days, days, days)
                            else stringResource(R.string.ui2_dsm_tap_start_end),
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                        )
                    },
                    showModeToggle = false
                )
            }
        }

        is DeleteMode.ConfirmRange -> {
            val from = remember(m.startMs) { LocalDate.ofEpochDay(m.startMs / 86_400_000L) }
            val to   = remember(m.endMs)   { LocalDate.ofEpochDay(m.endMs   / 86_400_000L) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.ui2_dsm_delete_range_title)) },
                text = {
                    Text(
                        stringResource(R.string.ui2_dsm_delete_range_body,
                            from.toString(), to.toString(), sysSn),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteRange(from.atStartOfDay(), to.atStartOfDay())
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_dsm_delete_range))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mode = DeleteMode.Choose }) {
                        Text(stringResource(R.string.ui2_back))
                    }
                }
            )
        }
    }
}

private sealed class DeleteMode {
    object Choose : DeleteMode()
    object ConfirmAll : DeleteMode()
    object PickRange : DeleteMode()
    data class ConfirmRange(val startMs: Long, val endMs: Long) : DeleteMode()
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * Asks the user where the catch-up should begin. The default is one day after
 * the last date we already have (so a re-fetch picks up where it left off),
 * falling back to one year ago for a fresh source. The actual fetch then runs
 * from the chosen date through "yesterday" (the last complete day); the daily
 * worker scheduled by the VM tops up automatically each morning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FetchStartDialog(
    sysSn: String,
    lastDataDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val defaultStart = remember(lastDataDate) {
        lastDataDate?.plusDays(1) ?: LocalDate.now().minusYears(1)
    }
    val yesterday = remember { LocalDate.now().minusDays(1) }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = defaultStart.toUtcMillis(),
        yearRange = (defaultStart.year.coerceAtMost(yesterday.year - 5))..yesterday.year
    )
    val picked = pickerState.selectedDateMillis?.let {
        LocalDate.ofEpochDay(it / 86_400_000L)
    }
    val days = if (picked != null)
        (yesterday.toEpochDay() - picked.toEpochDay()).toInt() + 1 else 0
    val confirmLabel = when {
        picked == null -> stringResource(R.string.ui2_dsm_pick_start_date)
        picked.isAfter(yesterday) -> stringResource(R.string.ui2_dsm_pick_earlier)
        else -> pluralStringResource(R.plurals.ui2_dsm_ok_fetch_days, days, days)
    }
    StickyButtonPickerDialog(
        onDismiss = onDismiss,
        confirmEnabled = picked != null && !picked.isAfter(yesterday),
        confirmLabel = confirmLabel,
        confirmIcon = Icons.Default.PlayArrow,
        onBack = onDismiss,
        backLabel = stringResource(R.string.dialog_cancel),
        onConfirm = { onConfirm(picked!!.atStartOfDay()) }
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text(stringResource(R.string.ui2_dsm_fetch_title, sysSn),
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp))
            },
            headline = {
                Text(
                    when {
                        picked == null ->
                            stringResource(R.string.ui2_dsm_catch_up_hint, yesterday.toString())
                        picked.isAfter(yesterday) ->
                            stringResource(R.string.ui2_dsm_no_later_than, yesterday.toString())
                        else -> "$picked  →  $yesterday   ·   " +
                            pluralStringResource(R.plurals.ui2_dsm_n_days, days, days)
                    },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                )
            },
            showModeToggle = false
        )
    }
}

/**
 * A small dialog wrapper that places a picker (DatePicker / DateRangePicker)
 * in a height-weighted slot, then pins a Back/Cancel + Confirm button row
 * at the bottom of the dialog. Solves the M3 layout problem where the
 * picker's lazy grid demands more height than DatePickerDialog allocates
 * and the action row scrolls off the bottom of the screen.
 */
@Composable
private fun StickyButtonPickerDialog(
    onDismiss: () -> Unit,
    confirmEnabled: Boolean,
    confirmLabel: String,
    confirmIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onBack: () -> Unit,
    backLabel: String = stringResource(R.string.ui2_back),
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .heightIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Picker fills the available vertical space first; whatever
                // height remains is given to the bottom action row, which
                // is therefore guaranteed to stay on-screen.
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    content()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) { Text(backLabel) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = confirmEnabled,
                        onClick = onConfirm
                    ) {
                        Icon(confirmIcon, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

/**
 * Layer the live WorkManager state ([FetchStatus] per SN) on top of a
 * persisted [SourceState]. The merged value is what the row composables
 * consume so they can show "Fetching… 2024-08-01" without the VM having
 * to keep two parallel copies of the system list.
 */
private fun SourceState.withLiveFetch(
    fetchMap: Map<String, FetchStatus>
): SourceState = copy(
    systems = systems.map { sys ->
        val live = fetchMap[sys.sysSn] ?: return@map sys
        sys.copy(
            scheduled = live.scheduled || sys.scheduled,
            fetching = live.running,
            progress = live.progress
        )
    }
)
