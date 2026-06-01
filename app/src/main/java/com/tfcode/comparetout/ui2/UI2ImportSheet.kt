package com.tfcode.comparetout.ui2

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

// ──────────────────────────────────────────────────────────────────────────
// UI2 Import verb — one ModalBottomSheet used everywhere a user can ingest
// a JSON payload. Generic over the target type; callers supply a `parse`
// lambda that turns the raw text into either a previewable success or a
// human-readable error.
//
// The sheet always offers BOTH a file pick and a paste textarea — file
// pickers are unreliable across the wider Android ecosystem (Drive / Signal /
// Gboard frequently strip MIME types or hide untagged JSON files), so paste
// is the bulletproof fallback. Clipboard paste covers the common case of
// sharing a snippet between devices via a chat app.
// ──────────────────────────────────────────────────────────────────────────

/** What a [UI2ImportSheet] parser returns. The description is rendered as a
 *  one-line preview above the Apply button. */
sealed class ParsedPreview<out T> {
    data class Ok<T>(val value: T, val description: String) : ParsedPreview<T>()
    data class Err(val message: String) : ParsedPreview<Nothing>()
}

private enum class ImportSource { FILE, PASTE }

private val IMPORT_MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
    "*/*"            // fallback for senders that drop the MIME entirely
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> UI2ImportSheet(
    title: String,
    hint: String? = null,
    applyLabel: String = "Apply",
    parse: (String) -> ParsedPreview<T>,
    onApply: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf(ImportSource.PASTE) }
    var pickedFileName by remember { mutableStateOf<String?>(null) }
    var buffer by remember { mutableStateOf("") }          // raw text fed to parse()
    var pasteText by remember { mutableStateOf("") }

    // Preview is recomputed whenever the buffer changes. Empty buffer = no
    // preview yet (Apply disabled); non-empty buffer = either Ok or Err.
    val preview: ParsedPreview<T>? = remember(buffer) {
        if (buffer.isBlank()) null else parse(buffer)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Same stream + UTF-8 reader the legacy importers use; we just hand
        // the resulting string to `parse` instead of straight into Gson.
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        BufferedReader(InputStreamReader(ins, StandardCharsets.UTF_8))
                            .use { it.readText() }
                    } ?: ""
                }.getOrElse { "" }
            }
            pickedFileName = uri.lastPathSegment?.substringAfterLast('/')
            buffer = text
            source = ImportSource.FILE
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Source picker — two FilterChips. The selection only changes
            // which input control is visible; both share the same `buffer`
            // so the preview reflects whichever source last wrote to it.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = source == ImportSource.FILE,
                    onClick = {
                        source = ImportSource.FILE
                        // Re-trigger the file picker on every tap so users
                        // can swap files without dismissing the sheet.
                        picker.launch(IMPORT_MIME_TYPES)
                    },
                    label = { Text("From file") },
                    leadingIcon = {
                        Icon(Icons.Default.UploadFile, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = source == ImportSource.PASTE,
                    onClick = {
                        source = ImportSource.PASTE
                        // Switching back to paste shows whatever the user
                        // had typed before — buffer is kept in sync below.
                        buffer = pasteText
                    },
                    label = { Text("Paste JSON") },
                    leadingIcon = {
                        Icon(Icons.Default.ContentPaste, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                    }
                )
            }

            when (source) {
                ImportSource.FILE -> {
                    FilePickerSummary(
                        fileName = pickedFileName,
                        onPick = { picker.launch(IMPORT_MIME_TYPES) }
                    )
                }
                ImportSource.PASTE -> {
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = {
                            pasteText = it
                            buffer = it
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp),
                        placeholder = { Text("{ … }") },
                        minLines = 4,
                        maxLines = 12,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        scope.launch {
                            val clip = clipboard.getClipEntry()
                                ?.clipData?.getItemAt(0)?.text?.toString().orEmpty()
                            if (clip.isNotBlank()) {
                                pasteText = clip
                                buffer = clip
                            }
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste from clipboard")
                    }
                }
            }

            HorizontalDivider()
            PreviewRow(preview)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = preview is ParsedPreview.Ok,
                    onClick = {
                        (preview as? ParsedPreview.Ok<T>)?.let {
                            onApply(it.value)
                            // Caller is expected to close the sheet on success;
                            // we don't unilaterally onDismiss() because some
                            // flows might want to keep it open (e.g. apply +
                            // tweak). Most current callers will simply call
                            // onDismiss() inside their onApply lambda.
                        }
                    }
                ) { Text(applyLabel) }
            }
        }
    }

    // Auto-trigger the file picker the first time someone selects "From file"
    // — saves a tap that would otherwise be "select chip, then tap empty
    // summary to actually open the picker".
    LaunchedEffect(Unit) { /* no-op — the chip onClick already triggers it */ }
}

@Composable
private fun FilePickerSummary(fileName: String?, onPick: () -> Unit) {
    OutlinedButton(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.UploadFile, contentDescription = null,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(fileName ?: "Pick a JSON file…")
    }
}

@Composable
private fun PreviewRow(preview: ParsedPreview<*>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (preview) {
            null -> {
                Text(
                    "Pick a file or paste JSON to preview.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is ParsedPreview.Ok -> {
                Icon(
                    Icons.Default.Check, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        preview.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            is ParsedPreview.Err -> {
                Icon(
                    Icons.Default.Error, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                // Gson exceptions can be very long; trim to a single readable
                // line — the user just needs to know the parse failed, not
                // the full stack trace.
                Text(
                    preview.message.lineSequence().firstOrNull().orEmpty()
                        .take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
