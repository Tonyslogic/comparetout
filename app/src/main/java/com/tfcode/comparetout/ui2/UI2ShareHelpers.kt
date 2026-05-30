package com.tfcode.comparetout.ui2

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ──────────────────────────────────────────────────────────────────────────
// UI2 Share verb — context-driven export via Android's system share chooser.
//
// Phase A only handles text payloads (JSON / CSV) via Intent.EXTRA_TEXT, which
// matches the legacy MainActivity behaviour and needs no FileProvider. When
// later phases want to attach large files (Backup & Export's .db snapshot,
// per-source slice CSVs that exceed EXTRA_TEXT's silent truncation), this is
// where the byte-attachment path will live.
// ──────────────────────────────────────────────────────────────────────────

/** Distinct export formats. The label is shown in the picker; mime is used on
 *  the share intent so receiving apps (mail, drive, signal, sqlite viewer)
 *  can route the payload appropriately. */
enum class ShareFormat(val label: String, val mime: String, val extension: String) {
    JSON("JSON", "application/json", "json"),
    CSV ("CSV",  "text/csv",        "csv")
}

/** Fire-and-forget text share. Payload sits in `EXTRA_TEXT`; the system
 *  chooser handles destination selection. `subject` is used as the e-mail
 *  subject line / saved file name when the picked receiver supports it. */
fun Context.shareText(payload: String, format: ShareFormat, subject: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = format.mime
        putExtra(Intent.EXTRA_TEXT, payload)
        if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    startActivity(Intent.createChooser(send, null))
}

/** Small picker shown when a subject supports more than one format. Render it
 *  conditionally with `if (showDialog) ShareFormatDialog(...)` from the caller —
 *  the dialog manages its own selection state but closes via `onDismiss`. */
@Composable
fun ShareFormatDialog(
    title: String = "Share as…",
    formats: List<ShareFormat>,
    initial: ShareFormat = formats.first(),
    onPick: (ShareFormat) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Share, contentDescription = null) },
        title = { Text(title) },
        text = {
            Column {
                formats.forEach { fmt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == fmt,
                                onClick = { selected = fmt }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == fmt,
                            onClick = { selected = fmt }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(fmt.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(selected); onDismiss() }) { Text("Share") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Suggested invocation patterns (kept as documentation rather than helpers
// so each call-site retains control over IO threading + status feedback):
//
//   // Single-format (plan / scenario):
//   IconButton(onClick = {
//       scope.launch(Dispatchers.IO) {
//           val json = JsonTools.exportSomething(...)
//           withContext(Dispatchers.Main) {
//               context.shareText(json, ShareFormat.JSON, "Plan: ${plan.planName}")
//           }
//       }
//   }) { Icon(Icons.Default.Share, "Share") }
//
//   // Multi-format (comparison results — CSV default, JSON alternate):
//   var pick by remember { mutableStateOf(false) }
//   if (pick) ShareFormatDialog(
//       formats = listOf(ShareFormat.CSV, ShareFormat.JSON),
//       onPick = { fmt -> exportAndShare(fmt) },
//       onDismiss = { pick = false }
//   )
