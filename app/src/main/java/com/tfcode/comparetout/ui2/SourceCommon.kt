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
 * Data-source management shared widgets (intro, accordion shell, chips, credential/delete/fetch dialogs, system list) — extracted verbatim from UI2DataSourceManagementActivity.kt
 * (mega-refactor B5). Imports inherited; unused are cosmetic.
 */

@Composable
internal fun IntroHint() {
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
internal fun SourceAccordion(
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
internal fun StatusChip(state: SourceState?) {
    val (icon, tint, label) = when {
        state == null -> Triple(Icons.Outlined.Warning,
            MaterialTheme.colorScheme.outline, stringResource(R.string.ui2_dsm_loading))
        // Without credentials ESBN is a healthy file-import source, not an
        // unconfigured one; with credentials it reports like any cloud source
        // (experimental sync can go invalid when ESB changes the flow).
        state.importer == Importer.ESBNHDF && !state.credentialsConfigured ->
            Triple(Icons.Default.UploadFile,
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

// ── Shared widgets ─────────────────────────────────────────────────────

@Composable
internal fun CredentialStrip(
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
internal fun DeleteSourceDialog(
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
internal fun SystemList(
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
internal fun SystemRow(
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
internal fun HintLine(text: String) {
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
internal fun CredentialDialog(
    title: String,
    userLabel: String,
    passLabel: String,
    initialUser: String,
    // Non-null → a warning banner above the fields; the ESBN experimental
    // notice must be visible at the exact moment of credential entry.
    warning: String? = null,
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
                if (warning != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
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
internal fun DeleteDialog(
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

internal sealed class DeleteMode {
    object Choose : DeleteMode()
    object ConfirmAll : DeleteMode()
    object PickRange : DeleteMode()
    data class ConfirmRange(val startMs: Long, val endMs: Long) : DeleteMode()
}

internal fun LocalDate.toUtcMillis(): Long =
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
internal fun FetchStartDialog(
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
internal fun StickyButtonPickerDialog(
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
internal fun SourceState.withLiveFetch(
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
