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
 * Cloud source sections: ESBN, Octopus, Solis, FusionSolar — extracted verbatim from UI2DataSourceManagementActivity.kt
 * (mega-refactor B5). Imports inherited; unused are cosmetic.
 */

// ── ESBN section ──────────────────────────────────────────────────────

@Composable
internal fun EsbnSection(
    state: SourceState?,
    showHints: Boolean,
    onSetCredentials: (String, String) -> Unit,
    onSelect: (String) -> Unit,
    onFetch: (String) -> Unit,
    onCancel: (String) -> Unit,
    onImportFile: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onDeleteRange: (String, LocalDateTime, LocalDateTime) -> Unit,
    onExportFolder: (String, String) -> Unit,
    onRemoveSource: () -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
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
    // Experimental notice — always visible when the section is expanded (NOT
    // gated by "show hints"): cloud sync is a scraped browser flow ESB has
    // broken before, and the user must see that before typing a password.
    // The same text repeats inside the credential dialog, above the fields.
    val experimentalNotice = stringResource(R.string.ui2_dsm_esbn_experimental)
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
                experimentalNotice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
    // File import stays first-class, listed before the cloud controls — it is
    // the always-works path when the experimental flow breaks.
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
        // No start-date dialog: the HDF download is always the full history
        // (one login + one POST per run), so there is nothing to pick.
        onFetch = onFetch,
        onCancel = onCancel,
        onDelete = { sys -> pendingDelete = sys },
        // ESBN import stays section-level (the "Import HDF file" button
        // above) — the file's MPRN is read from its contents, so there's
        // no natural per-row import here. Export *is* per-MPRN.
        onExport = { mprn ->
            exportTargetMprn = mprn
            pickExportFolder.launch(null)
        }
    )
    if (state?.credentialsConfigured == true) {
        // Rate-limit hint under the fetch controls (plans/source/esbn.md §3).
        Text(stringResource(R.string.ui2_dsm_esbn_rate_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state?.systems.isNullOrEmpty() && showHints) {
        Text(stringResource(R.string.ui2_dsm_esbn_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showCreds) {
        CredentialDialog(
            title = stringResource(R.string.ui2_dsm_esbn_creds_title),
            userLabel = stringResource(R.string.ui2_dsm_esbn_user_label),
            passLabel = stringResource(R.string.ui2_dsm_esbn_pass_label),
            initialUser = "",
            // Unmissable at the moment of credential entry — directly above
            // the Username/Password fields.
            warning = experimentalNotice,
            onDismiss = { showCreds = false },
            onSubmit = { u, p ->
                onSetCredentials(u, p)
                showCreds = false
            }
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
    if (showDeleteSource) {
        DeleteSourceDialog(
            sourceName = stringResource(R.string.ui2_dsm_esbn_data),
            mentionsCredentials = state?.credentialsConfigured == true,
            onDismiss = { showDeleteSource = false },
            onConfirm = { onRemoveSource(); showDeleteSource = false }
        )
    }
}

// ── Octopus Energy section ────────────────────────────────────────────

@Composable
internal fun OctopusSection(
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
internal fun SolisSection(
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

/**
 * FusionSolar (Huawei) — clone of [SolisSection] plus the captcha
 * round-trip: when the portal demands one, [captchaImage] is non-null, the
 * credential dialog (re)opens showing the image and a code field, and the
 * submit re-runs the probe with the typed code.
 */
@Composable
internal fun FusionSolarSection(
    state: SourceState?,
    showHints: Boolean,
    captchaImage: ByteArray?,
    onSetCredentials: (String, String, String, String?) -> Unit,
    onClearCaptcha: () -> Unit,
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
    // A captcha demand re-opens the sheet so the user can answer it.
    LaunchedEffect(captchaImage) {
        if (captchaImage != null) showCreds = true
    }
    if (showHints) {
        HintLine(stringResource(R.string.ui2_dsm_fusionsolar_hint))
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
        // No per-row file import — FusionSolar is cloud-sync only (UI2-only source).
    )
    if (state?.systems.isNullOrEmpty() && showHints) {
        Text(stringResource(R.string.ui2_dsm_fusionsolar_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showCreds) {
        FusionSolarCredentialDialog(
            captchaImage = captchaImage,
            onDismiss = {
                showCreds = false
                onClearCaptcha()
            },
            onSubmit = { u, p, h, code ->
                onSetCredentials(u, p, h, code)
                showCreds = false
            }
        )
    }
    if (showDeleteSource) {
        DeleteSourceDialog(
            sourceName = stringResource(R.string.brand_fusionsolar),
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

/**
 * Username / Password / Server, plus a captcha row that appears only when
 * the portal demanded one. The password is the user's full portal password —
 * the in-dialog notice says it is stored encrypted on this device and sent
 * only to Huawei's servers.
 */
@Composable
internal fun FusionSolarCredentialDialog(
    captchaImage: ByteArray?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String?) -> Unit
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val captchaBitmap = remember(captchaImage) {
        captchaImage?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui2_dsm_fusionsolar_creds_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.ui2_dsm_fusionsolar_password_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(stringResource(R.string.ui2_dsm_fusionsolar_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.ui2_dsm_fusionsolar_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = host, onValueChange = { host = it },
                    label = { Text(stringResource(R.string.ui2_dsm_fusionsolar_server)) },
                    placeholder = { Text("region01eu5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (captchaBitmap != null) {
                    Text(stringResource(R.string.ui2_dsm_fusionsolar_captcha_prompt),
                        style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.Image(
                        bitmap = captchaBitmap.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.ui2_dsm_fusionsolar_captcha_label),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp)
                    )
                    OutlinedTextField(
                        value = code, onValueChange = { code = it },
                        label = { Text(stringResource(
                            R.string.ui2_dsm_fusionsolar_captcha_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(user.trim(), pass, host.trim(), code.trim().ifEmpty { null })
            }) {
                Text(stringResource(R.string.ui2_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}

