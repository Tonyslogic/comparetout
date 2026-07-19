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
 * AlphaESS source section — extracted verbatim from UI2DataSourceManagementActivity.kt
 * (mega-refactor B5). Imports inherited; unused are cosmetic.
 */

// ── AlphaESS section ──────────────────────────────────────────────────

@Composable
internal fun AlphaSection(
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
    onRemoveSource: () -> Unit,
    bindState: AlphaBindUiState,
    onBindRequestCode: (String, String) -> Unit,
    onBindWithCode: (String, String) -> Unit,
    onBindReset: () -> Unit
) {
    var showCreds by remember { mutableStateOf(false) }
    var showAddInverter by remember { mutableStateOf(false) }
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
    // In-app inverter registration (plans/source/alpha.md): the portal's own
    // add-SN flow is broken by its email confirmation, so the sheet drives
    // the bind API. Needs a working appId/secret — the bind POSTs are signed
    // with them.
    OutlinedButton(
        onClick = { onBindReset(); showAddInverter = true },
        enabled = state?.credentialsKnownGood == true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.ui2_dsm_add_inverter))
    }
    if (state?.credentialsKnownGood != true) {
        HintLine(stringResource(R.string.ui2_dsm_add_inverter_needs_creds))
    }
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
    if (showAddInverter) {
        AddInverterSheet(
            bind = bindState,
            onRequestCode = onBindRequestCode,
            onBindWithCode = onBindWithCode,
            onDismiss = { showAddInverter = false; onBindReset() }
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

/**
 * The add-inverter (bind SN) sheet — plans/source/alpha.md §3.
 *
 * Step 1 (identify): SN + check code, typed or filled from a barcode photo.
 * Step 2 (request code): "Continue" fires getVerificationCode; when the
 * verification code comes back in-band the VM binds immediately (no email
 * needed — the email leg is the broken part of the portal flow). Otherwise
 * the emailed-code entry appears with a rate-limit-aware Resend.
 * Step 3 (done): success pane; the new SN is already in the system list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddInverterSheet(
    bind: AlphaBindUiState,
    onRequestCode: (String, String) -> Unit,
    onBindWithCode: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var serial by remember { mutableStateOf("") }
    var checkCode by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    // Reveal the code entry without first requesting one. Codes are valid for
    // 30 minutes and survive the app closing, so a user who already has one
    // (from an earlier request or the email) can bind straight away rather
    // than being forced back through "Continue".
    var revealCode by remember { mutableStateOf(false) }
    val showCodeEntry = bind.codeRequested || revealCode
    // Resend guard (§3 step 2b): the button re-enables after 60 s — never
    // auto-loop a code-issuing endpoint (6053 exists for a reason).
    var resendWait by remember { mutableStateOf(0) }
    LaunchedEffect(bind.codeRequested) { if (bind.codeRequested) resendWait = 60 }
    LaunchedEffect(resendWait) {
        if (resendWait > 0) {
            delay(1000)
            resendWait--
        }
    }
    // The camera contract returns only a boolean; remember where it wrote.
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    fun handleScan(uri: Uri) {
        scope.launch {
            val scan = withContext(Dispatchers.Default) { decodeLabelImage(context, uri) }
            if (scan == null || scan.isEmpty) {
                scanMessage = context.getString(R.string.ui2_dsm_scan_none)
            } else {
                // Fields stay editable — scanning only fills them.
                scan.serial?.let { serial = it }
                scan.checkCode?.let { checkCode = it }
                scanMessage = context.getString(R.string.ui2_dsm_scan_filled)
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = captureUri
        if (saved && uri != null) handleScan(uri)
    }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) handleScan(uri)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.ui2_dsm_add_inverter),
                style = MaterialTheme.typography.titleMedium)

            if (bind.boundSn != null) {
                Text(
                    stringResource(
                        if (bind.alreadyBound) R.string.ui2_dsm_bind_already
                        else R.string.ui2_dsm_bind_done,
                        bind.boundSn),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ui2_dsm_bind_finish))
                }
            } else {
                Text(stringResource(R.string.ui2_dsm_add_inverter_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = serial, onValueChange = { serial = it },
                    label = { Text(stringResource(R.string.ui2_dsm_serial_number)) },
                    singleLine = true,
                    enabled = !bind.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = checkCode, onValueChange = { checkCode = it },
                    label = { Text(stringResource(R.string.ui2_dsm_check_code)) },
                    singleLine = true,
                    enabled = !bind.busy,
                    isError = bind.checkCodeError,
                    supportingText = if (bind.checkCodeError) {
                        { Text(stringResource(R.string.ui2_dsm_bind_check_code_error)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            scanMessage = null
                            val uri = newCaptureUri(context)
                            captureUri = uri
                            takePicture.launch(uri)
                        },
                        enabled = !bind.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_dsm_scan_camera))
                    }
                    OutlinedButton(
                        onClick = {
                            scanMessage = null
                            pickImage.launch(PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        enabled = !bind.busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_dsm_scan_image))
                    }
                }
                scanMessage?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (showCodeEntry) {
                    // The emailed-code path (no code came back in-band), or the
                    // user already holds a code and revealed this directly.
                    Text(stringResource(
                            if (bind.codeRequested) R.string.ui2_dsm_bind_code_sent
                            else R.string.ui2_dsm_bind_have_code_hint),
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = emailCode, onValueChange = { emailCode = it },
                        label = { Text(stringResource(R.string.ui2_dsm_verification_code)) },
                        singleLine = true,
                        enabled = !bind.busy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                resendWait = 60
                                onRequestCode(serial, checkCode)
                            },
                            // A fresh request needs the SN + check code; the
                            // "I have a code" shortcut can be opened before
                            // they're filled, so guard on them here too.
                            enabled = !bind.busy && resendWait == 0 &&
                                    serial.isNotBlank() && checkCode.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when {
                                    resendWait > 0 -> stringResource(
                                        R.string.ui2_dsm_bind_resend_wait, resendWait)
                                    bind.codeRequested -> stringResource(
                                        R.string.ui2_dsm_bind_resend)
                                    else -> stringResource(R.string.ui2_dsm_bind_request_code)
                                }
                            )
                        }
                        Button(
                            onClick = { onBindWithCode(serial, emailCode) },
                            enabled = !bind.busy && emailCode.isNotBlank() &&
                                    serial.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.ui2_dsm_bind_add))
                        }
                    }
                } else {
                    Button(
                        onClick = { onRequestCode(serial, checkCode) },
                        enabled = !bind.busy && serial.isNotBlank() && checkCode.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (bind.busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ui2_dsm_bind_continue))
                    }
                    // Resume path: a code already in hand (valid 30 minutes,
                    // survives closing the app) skips straight to entry.
                    TextButton(
                        onClick = { revealCode = true },
                        enabled = !bind.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.ui2_dsm_bind_have_code))
                    }
                }
                if (bind.rateLimited) {
                    Text(stringResource(R.string.ui2_dsm_bind_rate_limited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
                bind.error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Load a label photo, downscale to ≤2048 px on the long edge, and hand the
 * pixels to [BarcodeLabelReader] (plans/source/alpha.md §2 — the reader is
 * pure JVM, so the Bitmap handling lives here). Null on unreadable input;
 * an empty scan means "no barcode found".
 */
internal fun decodeLabelImage(context: Context, uri: Uri): BarcodeLabelReader.LabelScan? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull() ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val scan = BarcodeLabelReader.scan(pixels, bitmap.width, bitmap.height)
    bitmap.recycle()
    return scan
}

/**
 * A fresh cache target for the system-camera capture, shared through the
 * existing manifest FileProvider (file_paths.xml cache-path "captures") —
 * TakePicture grants the camera app write access to the URI, so no CAMERA
 * permission is needed.
 */
internal fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "label-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

