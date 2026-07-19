@file:Suppress("AssignedValueIsNeverRead")

package com.tfcode.comparetout.ui2

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tfcode.comparetout.ComparisonUIViewModel.Importer
import com.tfcode.comparetout.R
import com.tfcode.comparetout.importers.alphaess.BarcodeLabelReader
import com.tfcode.comparetout.model.importers.alphaess.AlphaESSTransformMeta
import com.tfcode.comparetout.region.RegionProfiles
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

internal enum class DataSourceIoAction { IMPORT, EXPORT }

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
    val fusionsolarRaw by viewModel.fusionsolar.observeAsState()
    val fusionSolarCaptcha by viewModel.fusionSolarCaptcha.observeAsState()
    val fetchMap by viewModel.fetchStatus.observeAsState(emptyMap())
    val haSensors by viewModel.haSensors.observeAsState()
    val pvgis by viewModel.pvgis.observeAsState()
    val cds by viewModel.cds.observeAsState()
    val prices by viewModel.prices.observeAsState()
    val busy by viewModel.busy.observeAsState(false)
    val toast by viewModel.toast.observeAsState()
    val alphaBind by viewModel.alphaBind.observeAsState(AlphaBindUiState())
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
    val fusionsolar = fusionsolarRaw?.withLiveFetch(fetchMap)
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
                                onRemoveSource = { viewModel.deleteEntireSource(Importer.ALPHAESS) },
                                bindState = alphaBind,
                                onBindRequestCode = viewModel::alphaRequestVerification,
                                onBindWithCode = viewModel::alphaBindWithCode,
                                onBindReset = viewModel::alphaBindReset
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
                                onSetCredentials = viewModel::setEsbnCredentials,
                                onSelect = viewModel::selectEsbnSystem,
                                onFetch = viewModel::fetchEsbn,
                                onCancel = viewModel::cancelFetch,
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
                if (uiVis.fusionsolar) item("fusionsolar") {
                    SourceAccordion(
                        title = stringResource(R.string.brand_fusionsolar),
                        subtitle = stringResource(R.string.ui2_dsm_fusionsolar_sub),
                        state = fusionsolar,
                        showHints = showHints,
                        body = {
                            FusionSolarSection(
                                state = fusionsolar,
                                showHints = showHints,
                                captchaImage = fusionSolarCaptcha,
                                onSetCredentials = viewModel::setFusionSolarCredentials,
                                onClearCaptcha = viewModel::clearFusionSolarCaptcha,
                                onSelect = viewModel::selectFusionSolarStation,
                                onFetch = viewModel::fetchFusionSolar,
                                onCancel = viewModel::cancelFetch,
                                onDeleteAll = viewModel::deleteAllData,
                                onDeleteRange = viewModel::deleteRange,
                                onRemoveSource = { viewModel.deleteEntireSource(Importer.FUSION_SOLAR) }
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
                // Wholesale price cache behind dynamic tariff plans. Shown where the
                // edition supports dynamic tariffs (IE wholesale market / GB Octopus
                // Agile) AND the user hasn't hidden it in App settings — consistent
                // with the other cache sources (PVGIS/CDS).
                if ((RegionProfiles.current.dynamicMarkets.isNotEmpty() ||
                        RegionProfiles.current.hasOctopus) && uiVis.wholesale) item("prices") {
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

