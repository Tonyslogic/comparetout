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

/*
 * Weather/PV source sections (PVGIS, CDS) — extracted verbatim from UI2DataSourceManagementActivity.kt
 * (mega-refactor B5). Imports inherited; unused are cosmetic.
 */

// ── Weather/PV sources (PVGIS, CDS) — Phase 5.5 ────────────────────────
//
// File-cache-shaped, not credential-probe-shaped like the importers above.
// One accordion lists the matching cached responses in the EPO folder and
// offers per-file + clear-all deletion; CDS additionally carries an encrypted
// API key (no live probe — validity is unknown until the first real fetch).

@Composable
internal fun WeatherSourceAccordion(
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
internal fun WeatherSourceBody(
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
internal fun WeatherCacheRow(entry: WeatherCacheEntry, onDelete: () -> Unit) {
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
internal fun WeatherStatusChip(state: WeatherSourceState?, showCredentials: Boolean) {
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
internal fun WeatherCredentialStrip(
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

