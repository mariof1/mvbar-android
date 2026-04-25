package com.mvbar.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.AaPreferences
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.sync.SyncManager
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.ui.theme.*
import com.mvbar.android.wearbridge.WearNode
import com.mvbar.android.wearbridge.WearPairingStatus
import com.mvbar.android.wearbridge.WearStatePublisher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onLogout: () -> Unit, onBrowseCache: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugEnabled by remember { mutableStateOf(DebugLog.enabled) }
    var showLogViewer by remember { mutableStateOf(false) }
    var logEntries by remember { mutableStateOf(DebugLog.getEntries()) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // Cache settings
    var cacheSizeMb by remember { mutableLongStateOf(AudioCacheManager.getCacheSizeMb()) }
    var cachedTrackCount by remember { mutableIntStateOf(AudioCacheManager.getCachedTrackCount()) }
    val cacheLimitSteps = listOf(100, 250, 500, 1000, 2000, 5000, 10000, 20000, 30000)
    var cacheLimitIndex by remember {
        val current = AudioCacheManager.maxCacheMb
        mutableIntStateOf(cacheLimitSteps.indexOfFirst { it >= current }.coerceAtLeast(0))
    }
    var prefetchCount by remember { mutableIntStateOf(AudioCacheManager.prefetchCount) }
    var wifiOnly by remember { mutableStateOf(AudioCacheManager.wifiOnlyDownload) }
    var autoCacheFavorites by remember { mutableStateOf(AudioCacheManager.autoCacheFavorites) }
    var autoCachePodcasts by remember { mutableStateOf(AudioCacheManager.autoCachePodcasts) }

    // Sync settings
    val lastSync by SyncManager.lastSyncTime.collectAsState()
    val isSyncing by SyncManager.isSyncing.collectAsState()
    val syncStatus by SyncManager.syncStatus.collectAsState()
    val syncIntervalOptions = listOf(1, 6, 12, 24)
    var syncIntervalIndex by remember {
        val current = SyncManager.getSyncIntervalHours()
        mutableIntStateOf(syncIntervalOptions.indexOf(current).coerceAtLeast(0))
    }
    var dbTrackCount by remember { mutableIntStateOf(0) }

    // Auto-resume
    var autoResume by remember { mutableStateOf(false) }

    // Android Auto categories
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        autoResume = AaPreferences.getAutoResume(context)
        categories = AaPreferences.getCategoryOrder(context)
        try {
            dbTrackCount = MvbarDatabase.getInstance(context).trackDao().count()
        } catch (_: Exception) {}
    }

    fun moveCategory(index: Int, direction: Int) {
        val target = index + direction
        if (target < 0 || target >= categories.size) return
        val mutable = categories.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        categories = mutable
        scope.launch { AaPreferences.saveCategoryOrder(context, mutable) }
    }

    if (showLogViewer) {
        LogViewerScreen(
            entries = logEntries,
            onBack = { showLogViewer = false },
            onRefresh = { logEntries = DebugLog.getEntries() },
            onCopy = { DebugLog.copyToClipboard(context) },
            onShare = { DebugLog.shareLog(context) },
            onClear = { DebugLog.clear(); logEntries = emptyList() }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // ── ACCOUNT ──────────────────────────────────────────────
        item {
            SectionHeader("ACCOUNT")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = Cyan500)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("mvbar Android", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Dns, null, tint = Cyan500)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Server", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text(
                                ApiClient.getBaseUrl().removeSuffix("/"),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }

        // ── PLAYBACK ─────────────────────────────────────────────
        item {
            SectionHeader("PLAYBACK")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsToggle(
                        title = "Auto-Resume",
                        subtitle = "Restore last queue and open player when app launches",
                        checked = autoResume,
                        onCheckedChange = {
                            autoResume = it
                            scope.launch { AaPreferences.saveAutoResume(context, it) }
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Prefetch Next Tracks", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                        Text(
                            "$prefetchCount",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Cyan500,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Auto-download upcoming tracks for seamless playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    Slider(
                        value = prefetchCount.toFloat(),
                        onValueChange = { prefetchCount = it.toInt() },
                        onValueChangeFinished = { AudioCacheManager.setPrefetchCount(prefetchCount) },
                        valueRange = 0f..5f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = Cyan500,
                            activeTrackColor = Cyan500,
                            inactiveTrackColor = SurfaceDark
                        )
                    )
                }
            }
        }

        // ── STORAGE & CACHE ──────────────────────────────────────
        item {
            SectionHeader("STORAGE & CACHE")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Audio Cache", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Text(
                        "${cacheSizeMb} MB used · $cachedTrackCount tracks cached",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBrowseCache,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Browse", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                AudioCacheManager.clearCache()
                                cacheSizeMb = 0
                                cachedTrackCount = 0
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear", fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))

                    val limitMb = cacheLimitSteps[cacheLimitIndex]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Max Cache Size", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                        Text(
                            if (limitMb >= 1000) "${limitMb / 1000} GB" else "$limitMb MB",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Cyan500,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = cacheLimitIndex.toFloat(),
                        onValueChange = { cacheLimitIndex = it.toInt() },
                        onValueChangeFinished = { AudioCacheManager.setMaxCacheMb(cacheLimitSteps[cacheLimitIndex]) },
                        valueRange = 0f..(cacheLimitSteps.size - 1).toFloat(),
                        steps = cacheLimitSteps.size - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = Cyan500,
                            activeTrackColor = Cyan500,
                            inactiveTrackColor = SurfaceDark
                        )
                    )

                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggle(
                        title = "WiFi Only Downloads",
                        subtitle = "Only prefetch and cache on WiFi",
                        checked = wifiOnly,
                        onCheckedChange = { wifiOnly = it; AudioCacheManager.setWifiOnlyDownload(it) }
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))

                    SettingsToggle(
                        title = "Auto-Cache Favorites",
                        subtitle = "Keep favorited tracks available offline",
                        checked = autoCacheFavorites,
                        onCheckedChange = { autoCacheFavorites = it; AudioCacheManager.setAutoCacheFavorites(it) }
                    )
                }
            }
        }

        // ── SYNC ─────────────────────────────────────────────────
        item {
            SectionHeader("SYNC")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val lastSyncText = if (lastSync > 0) {
                        val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                        "Last synced: ${sdf.format(java.util.Date(lastSync))}"
                    } else {
                        "Never synced"
                    }
                    Text(lastSyncText, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Text(
                        "$dbTrackCount tracks cached locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sync Interval", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                        Text(
                            "Every ${syncIntervalOptions[syncIntervalIndex]} hour${if (syncIntervalOptions[syncIntervalIndex] != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Cyan500,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = syncIntervalIndex.toFloat(),
                        onValueChange = { syncIntervalIndex = it.toInt() },
                        onValueChangeFinished = {
                            SyncManager.setSyncIntervalHours(context, syncIntervalOptions[syncIntervalIndex])
                        },
                        valueRange = 0f..(syncIntervalOptions.size - 1).toFloat(),
                        steps = syncIntervalOptions.size - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = Cyan500,
                            activeTrackColor = Cyan500,
                            inactiveTrackColor = SurfaceDark
                        )
                    )

                    Button(
                        onClick = {
                            SyncManager.syncNow(context)
                            scope.launch {
                                kotlinx.coroutines.delay(1000)
                                try {
                                    dbTrackCount = MvbarDatabase.getInstance(context).trackDao().count()
                                } catch (_: Exception) {}
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan500.copy(alpha = 0.15f),
                            contentColor = Cyan500
                        )
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Cyan500,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(syncStatus.ifEmpty { "Syncing…" }, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Filled.Sync, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }
                }
            }
        }

        // ── ANDROID AUTO ─────────────────────────────────────────
        item {
            SectionHeader("ANDROID AUTO")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Category Order",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface
                    )
                    Text(
                        "Reorder categories shown in Android Auto. Top items appear on the main screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    Spacer(Modifier.height(12.dp))

                    categories.forEachIndexed { index, key ->
                        if (index > 0) {
                            HorizontalDivider(color = SurfaceDark)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Cyan500,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp)
                            )
                            Text(
                                AaPreferences.displayName(key),
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { moveCategory(index, -1) },
                                enabled = index > 0
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Move up",
                                    tint = if (index > 0) Cyan500 else OnSurfaceDim
                                )
                            }
                            IconButton(
                                onClick = { moveCategory(index, 1) },
                                enabled = index < categories.size - 1
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Move down",
                                    tint = if (index < categories.size - 1) Cyan500 else OnSurfaceDim
                                )
                            }
                        }
                    }

                    if (categories.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Changes take effect on next Android Auto connection",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                    }
                }
            }
        }

        // ── WEAR OS ──────────────────────────────────────────────
        item {
            SectionHeader("WEAR OS")
        }
        item {
            WearOsCard()
        }

        // ── DEVELOPER ────────────────────────────────────────────
        item {
            SectionHeader("DEVELOPER")
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsToggle(
                        title = "Debug Logging",
                        subtitle = "Log API calls, errors, and crashes",
                        checked = debugEnabled,
                        onCheckedChange = {
                            debugEnabled = it
                            DebugLog.enabled = it
                            DebugLog.save(context)
                            ApiClient.rebuild()
                            if (it) DebugLog.i("Settings", "Debug logging enabled")
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))

                    Text("Log Actions", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                logEntries = DebugLog.getEntries()
                                showLogViewer = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500)
                        ) {
                            Icon(Icons.Filled.List, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("View (${DebugLog.getEntries().size})")
                        }
                        OutlinedButton(
                            onClick = { DebugLog.shareLog(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500)
                        ) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { DebugLog.copyToClipboard(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500)
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        OutlinedButton(
                            onClick = { DebugLog.clear(); logEntries = emptyList() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(12.dp))

                    Text("Upload to Server", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Text(
                        "Send logs to your mvbar server",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            isUploading = true
                            uploadStatus = null
                            scope.launch {
                                try {
                                    val result = DebugLog.uploadLog()
                                    uploadStatus = "✓ $result"
                                } catch (e: Exception) {
                                    uploadStatus = "✗ ${e.message}"
                                } finally {
                                    isUploading = false
                                }
                            }
                        },
                        enabled = !isUploading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Upload, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isUploading) "Uploading..." else "Upload Logs",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (uploadStatus != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            uploadStatus ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uploadStatus?.startsWith("✓") == true) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        ),
        color = Cyan500,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Cyan500,
                uncheckedThumbColor = OnSurfaceDim,
                uncheckedTrackColor = SurfaceDark
            )
        )
    }
}

@Composable
private fun LogViewerScreen(
    entries: List<DebugLog.LogEntry>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = OnSurface)
            }
            Text(
                "Debug Log",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = Cyan500)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, "Copy", tint = Cyan500)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, "Share", tint = Cyan500)
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Delete, "Clear", tint = MaterialTheme.colorScheme.error)
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.List,
                        null,
                        tint = OnSurfaceSubtle,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No log entries", color = OnSurfaceDim)
                    Text(
                        "Enable debug logging and use the app to capture logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceSubtle
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp, start = 8.dp, end = 8.dp)
            ) {
                items(entries) { entry ->
                    val color = when (entry.level) {
                        "E" -> Color(0xFFEF4444)
                        "W" -> Color(0xFFF59E0B)
                        "I" -> Cyan500
                        else -> OnSurfaceDim
                    }
                    Text(
                        text = entry.format(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = color,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

@Composable
private fun AndroidAutoTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        categories = AaPreferences.getCategoryOrder(context)
    }

    fun move(index: Int, direction: Int) {
        val target = index + direction
        if (target < 0 || target >= categories.size) return
        val mutable = categories.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        categories = mutable
        scope.launch { AaPreferences.saveCategoryOrder(context, mutable) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Category Order",
                style = MaterialTheme.typography.titleMedium,
                color = Cyan500,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Reorder categories shown in Android Auto. Top items appear on the main screen.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(categories.size) { index ->
            val key = categories[index]
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Cyan500,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        AaPreferences.displayName(key),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { move(index, -1) },
                        enabled = index > 0
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = if (index > 0) Cyan500 else OnSurfaceDim
                        )
                    }
                    IconButton(
                        onClick = { move(index, 1) },
                        enabled = index < categories.size - 1
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = if (index < categories.size - 1) Cyan500 else OnSurfaceDim
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Changes take effect on next Android Auto connection",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}


@Composable
private fun WearOsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var watches by remember { mutableStateOf<List<WearNode>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var pushStatus by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        watches = WearPairingStatus.reachableWatches(context)
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Pairing",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface
            )
            Spacer(Modifier.height(4.dp))
            when {
                loading -> Text(
                    "Looking for paired watches…",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
                watches.isEmpty() -> Text(
                    "No watches detected. Install mvbar on your watch and ensure it is paired via Wear OS / Galaxy Wearable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
                else -> {
                    watches.forEach { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Watch,
                                contentDescription = null,
                                tint = if (node.isNearby) Cyan500 else OnSurfaceDim,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    node.displayName.ifBlank { "Watch" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface
                                )
                                Text(
                                    if (node.isNearby) "Connected" else "Cloud-relayed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDim
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Push the current server URL and login token to the watch so it can stream and download independently.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        scope.launch {
                            WearStatePublisher.publishAuth(context)
                            pushStatus = "Pushed credentials to watch"
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Push to watch")
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { scope.launch { refresh() } }) {
                    Text("Refresh", color = Cyan500)
                }
            }
            pushStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Cyan500)
            }
        }
    }
}
