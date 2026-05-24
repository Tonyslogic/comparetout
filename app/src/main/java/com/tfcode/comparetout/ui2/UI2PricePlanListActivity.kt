package com.tfcode.comparetout.ui2

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.DecimalFormat

// ──────────────────────────────────────────────────────────────────────────
// Supplier-plan list. Compact accordion per plan — collapsed header shows
// only the name plus a favourite star; expansion exposes details and the
// Edit / Delete / Favourite controls. The favourite ID persists across runs
// via FavouritePlanStore and highlights matching rows in every Tariff Plan
// accordion in the app.
// ──────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class UI2PricePlanListActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                PricePlanListScreen(onClose = { finish() })
            }
        }
    }
}

private val moneyFmt = DecimalFormat("#,##0.00")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PricePlanListScreen(
    viewModel: UI2PricePlanListViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val rows by viewModel.rows.collectAsState()
    val favouriteId by viewModel.favouriteId.observeAsState(null)
    val (showHints, toggleShowHints) = rememberShowHints()
    var pendingDelete by remember { mutableStateOf<PricePlanListRow?>(null) }
    var showDrawer by remember { mutableStateOf(false) }

    val openWizard: (Long?) -> Unit = { planId ->
        val intent = Intent(context, UI2PricePlanWizardActivity::class.java)
        if (planId != null) intent.putExtra("PricePlanID", planId)
        context.startActivity(intent)
    }

    val onSwitchLegacy: () -> Unit = {
        // The list is launched from the in-app drawer; switching to legacy
        // means finishing back to the host UI2 navigation, which then routes
        // through MainActivity.
        onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supplier Plans") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item("header") {
                    ListHeader(
                        count = rows.size,
                        showHints = showHints,
                        onCreate = { openWizard(null) }
                    )
                }
                if (showHints) {
                    item("hint") { ListHintCard() }
                }
                if (rows.isEmpty()) {
                    item("empty") { EmptyListMessage(showHints) }
                } else {
                    items(rows, key = { it.planId }) { row ->
                        PricePlanAccordion(
                            row = row,
                            isFavourite = favouriteId == row.planId,
                            showHints = showHints,
                            onEdit = { openWizard(row.planId) },
                            onDelete = { pendingDelete = row },
                            onToggleFavourite = { viewModel.toggleFavourite(row.planId) }
                        )
                    }
                }
            }

            // Scrim + right-side drawer — matches every other UI2 screen.
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
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxSize()) {
                    UI2DrawerContent(
                        showHints = showHints,
                        onShowHintsChange = { if (it != showHints) toggleShowHints() },
                        onSwitchLegacy = { showDrawer = false; onSwitchLegacy() },
                        onClose = { showDrawer = false }
                    )
                }
            }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete supplier plan?") },
            text = {
                Text(
                    "\"${row.supplier} · ${row.planName}\" will be permanently deleted. " +
                        "Any cached costing results for this plan will also be removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.delete(row.planId)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ListHeader(count: Int, showHints: Boolean, onCreate: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$count plan" + if (count == 1) "" else "s",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showHints) {
                    Text(
                        "Tap a row to see its details · Star marks your current contract",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create new", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ListHintCard() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("About supplier plans",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                "Every cost in the app — Compare tab totals, dashboard Tariff Plan " +
                    "tables, KPIs — is calculated against one of these plans. " +
                    "Tap the ★ on a plan to mark it as the tariff you're currently " +
                    "contracted to; it will be highlighted everywhere it appears.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PricePlanAccordion(
    row: PricePlanListRow,
    isFavourite: Boolean,
    showHints: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavourite: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = if (isFavourite)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column {
            // ── Collapsed header: just enough to scan the list at a glance.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isFavourite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isFavourite) "Current plan" else null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isFavourite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        row.supplier,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        row.planName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                  else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                if (borderColor != Color.Transparent) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (row.reference.isNotBlank() && row.reference != "<REFERENCE>") {
                        Text(
                            row.reference,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Spec strip — standing / feed / bonus / day-rate count
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpecCell("Standing", "€${moneyFmt.format(row.standingCharges)}/yr",
                            modifier = Modifier.weight(1f))
                        SpecCell("Feed-in", "${moneyFmt.format(row.feed)} c/kWh",
                            modifier = Modifier.weight(1f))
                        SpecCell("Bonus", "€${moneyFmt.format(row.signUpBonus)}",
                            modifier = Modifier.weight(1f))
                        SpecCell(
                            label = "Rates",
                            value = "${row.rateCount} day-rate" +
                                if (row.rateCount == 1) "" else "s",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (row.deemedExport) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            shape = CircleShape
                        ) {
                            Text("Deemed export",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }

                    if (showHints) {
                        Text(
                            if (isFavourite)
                                "★ This is your current plan — highlighted in every Tariff Plan view."
                            else
                                "Tap ★ to mark this as your current plan.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onToggleFavourite,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isFavourite) Icons.Default.Star
                                              else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "My plan",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (row.lastUpdate.isNotBlank()) {
                        Text(
                            "Last updated ${row.lastUpdate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(46.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyListMessage(showHints: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("No supplier plans yet", style = MaterialTheme.typography.titleSmall)
            if (showHints) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap “+ Create new” to enter your supplier's tariff. Plans drive every " +
                        "cost calculation in the app — at least one is needed before the Compare " +
                        "tab can show prices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
