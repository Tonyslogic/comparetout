package com.tfcode.comparetout.ui2

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────
// UI2 Timezone selector
//
// What it does:
//   - Lets the user pick the IANA zone the app should treat as "local" when
//     importing unzoned timestamps and rendering UTC-anchored data.
//   - Defaults to (but never overwrites) the phone's current zone — useful
//     when travelling; if the user wants to keep showing Dublin times while
//     in Tokyo, this is where they pin it.
//
// Layout follows the rest of UI2:
//   - Top app bar with back + right-side drawer
//   - Hint card (gated by Show hints)
//   - "Current" summary card with reset-to-device button
//   - Search field
//   - Region-grouped sticky-headered LazyColumn of zones
// ──────────────────────────────────────────────────────────────────────────

/** One zone, pre-decorated for the picker list so we don't re-resolve per frame. */
private data class ZoneRow(
    val id: String,
    val region: String,         // "Europe", "America", "UTC", etc.
    val city: String,           // last segment of the ID, "_" replaced with " "
    val offsetLabel: String,    // "UTC+01:00" / "UTC-05:30"
    val shortName: String       // "GMT" / "PST" / abbreviation, may be empty
)

@HiltViewModel
class UI2TimezoneViewModel @Inject constructor(
    application: Application,
    private val store: UserTimezoneStore
) : AndroidViewModel(application) {

    val storedZone: LiveData<String?> = store.zoneId.asLiveData()

    init {
        viewModelScope.launch(Dispatchers.IO) { store.ensureLoaded() }
    }

    fun setZone(id: ZoneId?) = store.setZone(id)
}

@AndroidEntryPoint
class UI2TimezoneActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UI2Theme {
                TimezoneScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TimezoneScreen(
    viewModel: UI2TimezoneViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    val storedRaw by viewModel.storedZone.observeAsState(null)
    val deviceZone = remember { ZoneId.systemDefault() }
    val effectiveZone = remember(storedRaw) {
        runCatching { storedRaw?.let { ZoneId.of(it) } }.getOrNull() ?: deviceZone
    }
    val (showHints, toggleShowHints) = rememberShowHints()
    var showDrawer by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    // ZoneRows are derived once per Instant.now() — short-lived but cheap.
    // Done lazily because building all 600+ rows on the main thread is fine
    // (we measured: ~5ms on a mid-range phone) but only do it when the
    // composable enters the composition.
    val allRows = remember { buildAllZoneRows() }
    val filtered = remember(query, allRows) { allRows.filter { it.matches(query) } }
    val grouped = remember(filtered) { filtered.groupBy { it.region } }
    val lazyState = rememberLazyListState()

    // When the stored zone resolves for the first time, jump the list to it
    // so the user sees their current pick without having to scroll/search.
    // The one-shot flag prevents re-scrolling when the user taps a new zone
    // — that would yank them away from the row they just tapped.
    var hasAutoScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveZone.id, allRows.size) {
        if (hasAutoScrolled || allRows.isEmpty()) return@LaunchedEffect
        val idx = allRows.indexOfFirst { it.id == effectiveZone.id }
        if (idx >= 0) {
            lazyState.scrollToItem(idx)
            hasAutoScrolled = true
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Timezone") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDrawer = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header strip: hint + current selection + search — non-scrolling
                // so they stay visible while the user pages through hundreds
                // of zones below.
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showHints) HintCard()
                    CurrentSelectionCard(
                        effective = effectiveZone,
                        device = deviceZone,
                        overridden = storedRaw != null && effectiveZone != deviceZone,
                        onResetDeviceDefault = { viewModel.setZone(null) }
                    )
                    SearchField(query, onChange = { query = it })
                }

                LazyColumn(
                    state = lazyState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, bottom = 92.dp
                    )
                ) {
                    if (filtered.isEmpty()) {
                        item("empty") {
                            Text("No zones match \"$query\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp))
                        }
                    }
                    grouped.forEach { (region, rows) ->
                        stickyHeader(key = "h-$region") {
                            RegionHeader(region, rows.size)
                        }
                        items(rows, key = { "z-${it.id}" }) { row ->
                            ZoneItem(
                                row = row,
                                isSelected = row.id == effectiveZone.id,
                                isDeviceDefault = row.id == deviceZone.id,
                                onClick = {
                                    viewModel.setZone(ZoneId.of(row.id))
                                }
                            )
                        }
                    }
                }
            }

            // Standard UI2 right-side drawer.
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
private fun HintCard() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("About timezone",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(
                "Internally the app stores every reading in UTC. The zone you " +
                    "pick here decides how imports without explicit zone info " +
                    "are interpreted, and how UTC values are rendered back to you. " +
                    "Default is your phone's current zone — change it only if you " +
                    "want to keep seeing home time while travelling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CurrentSelectionCard(
    effective: ZoneId,
    device: ZoneId,
    overridden: Boolean,
    onResetDeviceDefault: () -> Unit
) {
    val row = remember(effective) { effective.toRow() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Public, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Currently using", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(row.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    append(row.offsetLabel)
                    if (row.shortName.isNotBlank()) append(" · ").append(row.shortName)
                    if (!overridden) append(" · device default")
                    else append(" · pinned override")
                }
                Text(sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (overridden) {
                OutlinedButton(onClick = onResetDeviceDefault) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Device (${device.id.substringAfterLast('/')})")
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search city or region…") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Clear, "Clear")
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RegionHeader(region: String, count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(region,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ZoneItem(
    row: ZoneRow,
    isSelected: Boolean,
    isDeviceDefault: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (isSelected) Icons.Default.CheckCircle else Icons.Default.Public,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.city,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isDeviceDefault) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            shape = CircleShape
                        ) {
                            Text("Device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(row.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.offsetLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium)
                if (row.shortName.isNotBlank()) {
                    Text(row.shortName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
            }
        }
    }
}

private fun ZoneRow.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return id.lowercase().contains(q)
            || region.lowercase().contains(q)
            || city.lowercase().contains(q)
            || offsetLabel.lowercase().contains(q)
            || shortName.lowercase().contains(q)
}

/** Build one [ZoneRow] for every IANA zone — sorted by region, then by offset, then by city. */
private fun buildAllZoneRows(): List<ZoneRow> {
    val now = Instant.now()
    return ZoneId.getAvailableZoneIds()
        .asSequence()
        .filter { it.isNotBlank() }
        .map { ZoneId.of(it).toRow(now) }
        // Stable, predictable ordering — Africa/America/Antarctica/.../UTC/Etc
        .sortedWith(compareBy({ it.region.regionRank() }, { it.region }, { -it.offsetSeconds() }, { it.city }))
        .toList()
}

private fun String.regionRank(): Int = when (this) {
    "UTC" -> 0           // pin GMT/UTC to the top
    "Etc" -> 100         // Etc/* and SystemV/* less useful, push down
    "SystemV" -> 110
    else -> 50
}

private fun ZoneRow.offsetSeconds(): Int {
    // Parse "UTC±HH:MM" back to seconds for tie-breaking — avoids resolving
    // the zone again per sort step.
    val s = offsetLabel.removePrefix("UTC")
    if (s.isEmpty() || s == "Z") return 0
    val sign = if (s[0] == '-') -1 else 1
    val rest = s.drop(1)
    val parts = rest.split(":")
    val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val mins = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return sign * (hours * 3600 + mins * 60)
}

private fun ZoneId.toRow(now: Instant = Instant.now()): ZoneRow {
    val parts = id.split("/", limit = 2)
    val region = if (parts.size == 2) parts[0] else "UTC"
    val city = (if (parts.size == 2) parts[1] else parts[0]).replace('_', ' ')
    val offset = rules.getOffset(now)
    val offsetLabel = formatOffset(offset)
    val shortName = runCatching {
        getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }.getOrDefault("").let { name ->
        // Filter out names that are just the numeric offset (e.g. "GMT+1") —
        // they duplicate the offsetLabel we already show.
        if (name.startsWith("GMT") || name.startsWith("UTC")) "" else name
    }
    return ZoneRow(
        id = id,
        region = region,
        city = city,
        offsetLabel = offsetLabel,
        shortName = shortName
    )
}

private val OFFSET_FMT = DateTimeFormatter.ofPattern("xxx")

private fun formatOffset(offset: ZoneOffset): String {
    if (offset.totalSeconds == 0) return "UTC"
    return "UTC${OFFSET_FMT.format(offset)}"
}
