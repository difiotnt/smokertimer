@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.difiotnt.smokertimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class AppUiState(
    val entries: List<SmokingEntry> = emptyList(),
    val settings: SmokingSettings = SmokingSettings(),
    val noteDraft: String = "",
    val selectedRange: HistoryRange = HistoryRange.DAY,
    val isLoading: Boolean = true,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SmokingNotifications.ensureChannel(this)

        val repository = SmokingRepository(this)
        var uiState by mutableStateOf(AppUiState())

        Thread {
            val entries = repository.loadEntries()
            val settings = repository.loadSettings()
            runOnUiThread {
                uiState = uiState.copy(
                    entries = entries,
                    settings = settings,
                    isLoading = false,
                )
                SmokingNotifications.scheduleReminder(this, entries.firstOrNull(), settings)
            }
        }.start()

        setContent {
            SmokerTimerTheme {
                SmokingTimerApp(
                    uiState = uiState,
                    onStateChange = { nextState ->
                        uiState = nextState
                    },
                    repository = repository,
                    activity = this,
                )
            }
        }
    }
}

@Composable
private fun SmokingTimerApp(
    uiState: AppUiState,
    onStateChange: (AppUiState) -> Unit,
    repository: SmokingRepository,
    activity: ComponentActivity,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val notificationPermissionGranted = derivedNotificationPermissionState(activity)

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val nextSettings = uiState.settings.copy(notificationsEnabled = granted)
        val nextState = uiState.copy(settings = nextSettings)
        onStateChange(nextState)
        persistSnapshot(context, repository, nextState)
        if (granted) {
            scope.launch { snackbarHostState.showSnackbar("Notifikasi pengingat aktif") }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Notifikasi tidak diizinkan") }
        }
    }

    val zoneId = remember { ZoneId.systemDefault() }
    val latestEntry = uiState.entries.firstOrNull()
    val allowedAtMillis = latestEntry?.let { it.timestampMillis + uiState.settings.intervalMinutes * 60_000L }
    val visibleEntries = remember(uiState.entries, uiState.selectedRange, nowMillis) {
        filterEntries(uiState.entries, uiState.selectedRange, zoneId, nowMillis)
    }
    val daySections = remember(visibleEntries) {
        buildDaySections(visibleEntries, zoneId)
    }
    val todayCount = remember(uiState.entries, nowMillis) {
        val today = LocalDate.now(zoneId)
        uiState.entries.count {
            Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId).toLocalDate() == today
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Smoker Timer", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Log rokok + pengingat interval",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Status disimpan lokal di perangkat")
                        }
                    }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                .padding(paddingValues),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    HeroCard(
                        latestEntry = latestEntry,
                        allowedAtMillis = allowedAtMillis,
                        nowMillis = nowMillis,
                        intervalMinutes = uiState.settings.intervalMinutes,
                        todayCount = todayCount,
                    )
                }

                item {
                    IntervalCard(
                        intervalMinutes = uiState.settings.intervalMinutes,
                        notificationsEnabled = uiState.settings.notificationsEnabled,
                        notificationPermissionGranted = notificationPermissionGranted,
                        onIntervalSelected = { minutes ->
                            val nextSettings = uiState.settings.copy(intervalMinutes = minutes)
                            val nextState = uiState.copy(settings = nextSettings)
                            onStateChange(nextState)
                            persistSnapshot(context, repository, nextState)
                            scope.launch { snackbarHostState.showSnackbar("Interval diubah ke ${minutes} menit") }
                        },
                        onNotificationToggle = { enabled ->
                            if (!enabled) {
                                val nextSettings = uiState.settings.copy(notificationsEnabled = false)
                                val nextState = uiState.copy(settings = nextSettings)
                                onStateChange(nextState)
                                persistSnapshot(context, repository, nextState)
                                return@IntervalCard
                            }

                            if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val nextSettings = uiState.settings.copy(notificationsEnabled = true)
                                val nextState = uiState.copy(settings = nextSettings)
                                onStateChange(nextState)
                                persistSnapshot(context, repository, nextState)
                            }
                        },
                    )
                }

                item {
                    AddEntryCard(
                        noteDraft = uiState.noteDraft,
                        onNoteChange = { nextText ->
                            onStateChange(uiState.copy(noteDraft = nextText))
                        },
                        onSave = {
                            val entry = SmokingEntry(
                                timestampMillis = nowMillis,
                                note = uiState.noteDraft.trim(),
                            )
                            val nextEntries = (uiState.entries + entry)
                                .sortedByDescending { it.timestampMillis }
                            val nextState = uiState.copy(entries = nextEntries, noteDraft = "")
                            onStateChange(nextState)
                            persistSnapshot(context, repository, nextState)
                            scope.launch { snackbarHostState.showSnackbar("Riwayat merokok disimpan") }
                        },
                    )
                }

                item {
                    HistoryHeader(
                        selectedRange = uiState.selectedRange,
                        visibleCount = visibleEntries.size,
                        onRangeChange = { range ->
                            onStateChange(uiState.copy(selectedRange = range))
                        },
                    )
                }

                if (uiState.isLoading) {
                    item {
                        LoadingCard()
                    }
                } else if (visibleEntries.isEmpty()) {
                    item {
                        EmptyStateCard(range = uiState.selectedRange)
                    }
                } else {
                    items(daySections) { section ->
                        DaySectionCard(section = section)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    latestEntry: SmokingEntry?,
    allowedAtMillis: Long?,
    nowMillis: Long,
    intervalMinutes: Int,
    todayCount: Int,
) {
    val zoneId = ZoneId.systemDefault()
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale("id", "ID")) }
    val lastSmokingLabel = latestEntry?.let {
        val dt = Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId)
        "${dt.toLocalDate().format(formatter)} • ${dt.toLocalTime().format(timeFormatter)}"
    } ?: "Belum ada catatan"

    val statusText = when {
        allowedAtMillis == null -> "Belum ada timer aktif"
        nowMillis >= allowedAtMillis -> "Smoking allowed sekarang"
        else -> "Boleh lagi dalam ${formatRemaining(allowedAtMillis - nowMillis)}"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Timer & status",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (latestEntry == null) {
                    "Timer akan muncul setelah catatan pertama."
                } else {
                    "Rokok terakhir: $lastSmokingLabel"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricPill(
                    label = "Hari ini",
                    value = "$todayCount kali",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                MetricPill(
                    label = "Interval",
                    value = "${intervalMinutes} menit",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    tint: Color,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun IntervalCard(
    intervalMinutes: Int,
    notificationsEnabled: Boolean,
    notificationPermissionGranted: Boolean,
    onIntervalSelected: (Int) -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Atur interval", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90, 120).forEach { minutes ->
                    FilterChip(
                        selected = intervalMinutes == minutes,
                        onClick = { onIntervalSelected(minutes) },
                        label = { Text(formatIntervalLabel(minutes)) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notifikasi allowed smoking", fontWeight = FontWeight.Medium)
                    Text(
                        text = if (notificationsEnabled) {
                            if (notificationPermissionGranted) "Aktif dan siap mengirim pengingat" else "Aktif, tapi izin notifikasi belum diberikan"
                        } else {
                            "Nonaktif"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = onNotificationToggle,
                )
            }
        }
    }
}

@Composable
private fun AddEntryCard(
    noteDraft: String,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Catat smoking sekarang", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = noteDraft,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Keterangan opsional") },
                placeholder = { Text("Contoh: habis makan malam, lagi stres, dll") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = KeyboardType.Text,
                ),
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Simpan smoking sekarang")
            }
        }
    }
}

@Composable
private fun HistoryHeader(
    selectedRange: HistoryRange,
    visibleCount: Int,
    onRangeChange: (HistoryRange) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$visibleCount catatan", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            androidx.compose.material3.TabRow(
                selectedTabIndex = selectedRange.ordinal,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { _ -> },
                divider = {},
            ) {
                HistoryRange.entries.forEachIndexed { index, range ->
                    androidx.compose.material3.Tab(
                        selected = selectedRange == range,
                        onClick = { onRangeChange(range) },
                        text = { Text(range.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySectionCard(section: DaySection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${section.entries.size} kali", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            section.entries.forEachIndexed { index, entry ->
                EntryRow(entry = entry)
                if (index != section.entries.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: SmokingEntry) {
    val zoneId = ZoneId.systemDefault()
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale("id", "ID")) }
    val time = Instant.ofEpochMilli(entry.timestampMillis).atZone(zoneId).toLocalTime().format(timeFormatter)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(time, fontWeight = FontWeight.SemiBold)
                Text(
                    "waktu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (entry.note.isBlank()) "Tanpa keterangan" else entry.note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyStateCard(range: HistoryRange) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Belum ada history di tab ${range.label.lowercase(Locale("id", "ID"))}")
            Text(
                "Setelah kamu menekan tombol simpan, catatan akan muncul di sini.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Memuat data...", fontWeight = FontWeight.SemiBold)
            Text("Membaca log tersimpan dari penyimpanan lokal.")
        }
    }
}

private fun filterEntries(
    entries: List<SmokingEntry>,
    range: HistoryRange,
    zoneId: ZoneId,
    nowMillis: Long,
): List<SmokingEntry> {
    if (range == HistoryRange.ALL) return entries.sortedByDescending { it.timestampMillis }

    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val startDate = when (range) {
        HistoryRange.DAY -> today
        HistoryRange.WEEK -> today.minusDays(6)
        HistoryRange.MONTH -> today.withDayOfMonth(1)
        HistoryRange.ALL -> today
    }

    return entries
        .filter { entry ->
            val entryDate = Instant.ofEpochMilli(entry.timestampMillis).atZone(zoneId).toLocalDate()
            !entryDate.isBefore(startDate)
        }
        .sortedByDescending { it.timestampMillis }
}

private fun buildDaySections(
    entries: List<SmokingEntry>,
    zoneId: ZoneId,
): List<DaySection> {
    return entries
        .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zoneId).toLocalDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (date, dayEntries) ->
            val title = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID")))
            DaySection(
                dateKey = date.toEpochDay(),
                title = title,
                entries = dayEntries.sortedByDescending { it.timestampMillis },
            )
        }
}

private fun formatRemaining(millis: Long): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}j ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "beberapa detik"
    }
}

private fun formatIntervalLabel(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

private fun persistSnapshot(
    context: android.content.Context,
    repository: SmokingRepository,
    state: AppUiState,
) {
    Thread {
        repository.saveEntries(state.entries.sortedByDescending { it.timestampMillis })
        repository.saveSettings(state.settings)
        SmokingNotifications.scheduleReminder(context, state.entries.firstOrNull(), state.settings)
    }.start()
}

private fun derivedNotificationPermissionState(activity: ComponentActivity): Boolean {
    return if (Build.VERSION.SDK_INT < 33) {
        true
    } else {
        activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
