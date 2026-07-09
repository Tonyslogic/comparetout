package com.tfcode.comparetout.ui2

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tfcode.comparetout.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
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

private enum class ImportSource { FILE, PASTE, COMMUNITY, LLM, EXTRA }

private val IMPORT_MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
    "*/*"            // fallback for senders that drop the MIME entirely
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun <T> UI2ImportSheet(
    title: String,
    hint: String? = null,
    applyLabel: String = stringResource(R.string.ui2_import_apply),
    /** When non-null, offers a third "Community" source that downloads JSON from
     * this URL into the same parse/preview/apply pipeline. */
    communityUrl: String? = null,
    communityNote: String = stringResource(R.string.ui2_import_community_note_default),
    /** When non-null, offers a "Prompt an LLM" source: the user copies this
     * prompt, runs it through their own AI assistant, then pastes the JSON it
     * returns back via the "Paste JSON" source. */
    llmPrompt: String? = null,
    /** When non-null, offers one extra self-contained source chip. Unlike the
     * other sources it does not feed the parse/preview pipeline — the content
     * carries its own action button (e.g. the Octopus tariff fetch, which
     * inserts plans directly). */
    extraSourceLabel: String? = null,
    extraSourceContent: (@Composable () -> Unit)? = null,
    parse: (String) -> ParsedPreview<T>,
    onApply: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf(ImportSource.PASTE) }
    var pickedFileName by remember { mutableStateOf<String?>(null) }
    var buffer by remember { mutableStateOf("") }          // raw text fed to parse()
    var pasteText by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var promptCopied by remember { mutableStateOf(false) }

    // Fetch the community JSON over the network into `buffer`, so it flows
    // through the same parse → preview → Apply path as file/paste.
    fun startCommunityDownload() {
        val url = communityUrl ?: return
        source = ImportSource.COMMUNITY
        scope.launch {
            downloading = true
            downloadError = null
            buffer = ""
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    URL(url).openStream().use { ins ->
                        BufferedReader(InputStreamReader(ins, StandardCharsets.UTF_8))
                            .use { it.readText() }
                    }
                }.getOrNull()
            }
            downloading = false
            if (text.isNullOrBlank()) {
                // Non-composable context — resolve via context.getString.
                downloadError = context.getString(R.string.ui2_import_download_failed)
            } else {
                buffer = text
            }
        }
    }

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

            // Source picker. The selection only changes which input control is
            // visible; file/paste/community share the same `buffer` so the
            // preview reflects whichever source last wrote to it. With the LLM
            // source there can be up to four chips — a FlowRow wraps them to a
            // second line when the width can't hold them (narrow phone / large
            // Display-size / large font), rather than clipping.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = source == ImportSource.FILE,
                    onClick = {
                        source = ImportSource.FILE
                        // Re-trigger the file picker on every tap so users
                        // can swap files without dismissing the sheet.
                        picker.launch(IMPORT_MIME_TYPES)
                    },
                    label = { Text(stringResource(R.string.ui2_import_from_file)) },
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
                    label = { Text(stringResource(R.string.ui2_import_paste_json)) },
                    leadingIcon = {
                        Icon(Icons.Default.ContentPaste, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                    }
                )
                if (communityUrl != null) {
                    FilterChip(
                        selected = source == ImportSource.COMMUNITY,
                        onClick = { startCommunityDownload() },
                        label = { Text(stringResource(R.string.ui2_import_community)) },
                        leadingIcon = {
                            Icon(Icons.Default.CloudDownload, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                        }
                    )
                }
                if (llmPrompt != null) {
                    FilterChip(
                        selected = source == ImportSource.LLM,
                        onClick = { source = ImportSource.LLM },
                        label = { Text(stringResource(R.string.ui2_import_prompt_llm)) },
                        leadingIcon = {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                        }
                    )
                }
                if (extraSourceLabel != null && extraSourceContent != null) {
                    FilterChip(
                        selected = source == ImportSource.EXTRA,
                        onClick = { source = ImportSource.EXTRA },
                        label = { Text(extraSourceLabel) },
                        leadingIcon = {
                            Icon(Icons.Default.CloudDownload, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                        }
                    )
                }
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
                        Text(stringResource(R.string.ui2_import_paste_clipboard))
                    }
                }
                ImportSource.COMMUNITY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            communityNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (downloading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.ui2_import_downloading),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { startCommunityDownload() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ui2_import_download_latest))
                            }
                        }
                        downloadError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                ImportSource.LLM -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.ui2_import_llm_intro),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            stringResource(R.string.ui2_import_llm_steps),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                llmPrompt?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    promptCopied = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(
                                if (promptCopied) R.string.ui2_import_prompt_copied
                                else R.string.ui2_import_copy_prompt))
                        }
                        TextButton(onClick = {
                            source = ImportSource.PASTE
                            buffer = pasteText
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ui2_import_have_json))
                        }
                        Text(
                            stringResource(R.string.ui2_import_llm_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                ImportSource.EXTRA -> {
                    extraSourceContent?.invoke()
                }
            }

            HorizontalDivider()
            PreviewRow(preview)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
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
        Text(fileName ?: stringResource(R.string.ui2_import_pick_file))
    }
}

@Composable
private fun PreviewRow(preview: ParsedPreview<*>?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (preview) {
            null -> {
                Text(
                    stringResource(R.string.ui2_import_preview_placeholder),
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
