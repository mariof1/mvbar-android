package com.mvbar.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import kotlinx.coroutines.launch

private enum class SettingsTab(val label: String, val icon: ImageVector) {
    GENERAL("General", Icons.Filled.Settings),
    PLAYBACK("Playback", Icons.Filled.MusicNote),
    AUTO("Auto", Icons.Filled.DirectionsCar),
    DEBUG("Debug", Icons.Filled.BugReport)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }
    var debugEnabled by remember { mutableStateOf(DebugLog.enabled) }
    var showLogViewer by remember { mutableStateOf(false) }
    var logEntries by remember { mutableStateOf(DebugLog.getEntries()) }
    var uploadUrl by remember { mutableStateOf(DebugLog.uploadServerUrl.ifBlank { "http://10.10.100.5:9999" }) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // Cache settings
    var cacheSizeMb by remember { mutableLongStateOf(AudioCacheManager.getCacheSizeMb()) }
    var cachedTrackCount by remember { mutableIntStateOf(AudioCacheManager.getCachedTrackCount()) }
    val cacheLimitSteps = listOf(100, 250, 500, 1000, 2000, 5000)
    var cacheLimitIndex by remember {
        val current = AudioCacheManager.maxCacheMb
        mutableIntStateOf(cacheLimitSteps.indexOfFirst { it >= current }.coerceAtLeast(0))
    }
    var prefetchCount by remember { mutableIntStateOf(AudioCacheManager.prefetchCount) }
    var wifiOnly by remember { mutableStateOf(AudioCacheManager.wifiOnlyDownload) }
    var autoCacheFavorites by remember { mutableStateOf(AudioCacheManager.autoCacheFavorites) }
    var autoCachePodcasts by remember { mutableStateOf(AudioCacheManager.autoCachePodcasts) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = OnSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        // Tab row
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = Cyan500,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                if (selectedTab.ordinal < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = Cyan500
                    )
                }
            },
            divider = { HorizontalDivider(color = SurfaceDark) }
        ) {
            SettingsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label, fontSize = 13.sp) },
                    icon = { Icon(tab.icon, null, modifier = Modifier.size(20.dp)) },
                    selectedContentColor = Cyan500,
                    unselectedContentColor = OnSurfaceDim
                )
            }
        }

        // Tab content
        when (selectedTab) {
            SettingsTab.GENERAL -> GeneralTab(onLogout)
            SettingsTab.PLAYBACK -> PlaybackTab(
                cacheSizeMb = cacheSizeMb,
                cachedTrackCount = cachedTrackCount,
                cacheLimitSteps = cacheLimitSteps,
                cacheLimitIndex = cacheLimitIndex,
                prefetchCount = prefetchCount,
                wifiOnly = wifiOnly,
                autoCacheFavorites = autoCacheFavorites,
                autoCachePodcasts = autoCachePodcasts,
                onClearCache = {
                    AudioCacheManager.clearCache()
                    cacheSizeMb = 0
                    cachedTrackCount = 0
                },
                onCacheLimitChange = { cacheLimitIndex = it },
                onCacheLimitSet = { AudioCacheManager.setMaxCacheMb(cacheLimitSteps[cacheLimitIndex]) },
                onPrefetchChange = { prefetchCount = it },
                onPrefetchSet = { AudioCacheManager.setPrefetchCount(prefetchCount) },
                onWifiOnlyChange = { wifiOnly = it; AudioCacheManager.setWifiOnlyDownload(it) },
                onAutoCacheChange = { autoCacheFavorites = it; AudioCacheManager.setAutoCacheFavorites(it) },
                onAutoCachePodcastsChange = { autoCachePodcasts = it; AudioCacheManager.setAutoCachePodcasts(it) }
            )
            SettingsTab.AUTO -> AndroidAutoTab()
            SettingsTab.DEBUG -> DebugTab(
                context = context,
                scope = scope,
                debugEnabled = debugEnabled,
                uploadUrl = uploadUrl,
                uploadStatus = uploadStatus,
                isUploading = isUploading,
                onToggleDebug = {
                    debugEnabled = it
                    DebugLog.enabled = it
                    DebugLog.save(context)
                    ApiClient.rebuild()
                    if (it) DebugLog.i("Settings", "Debug logging enabled")
                },
                onViewLogs = {
                    logEntries = DebugLog.getEntries()
                    showLogViewer = true
                },
                onShareLogs = { DebugLog.shareLog(context) },
                onCopyLogs = { DebugLog.copyToClipboard(context) },
                onClearLogs = { DebugLog.clear(); logEntries = emptyList() },
                onUploadUrlChange = {
                    uploadUrl = it
                    DebugLog.uploadServerUrl = it
                    DebugLog.save(context)
                },
                onUpload = {
                    DebugLog.uploadServerUrl = uploadUrl
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
                }
            )
        }
    }
}

@Composable
private fun GeneralTab(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lastSync by SyncManager.lastSyncTime.collectAsState()
    val isSyncing by SyncManager.isSyncing.collectAsState()
    val syncStatus by SyncManager.syncStatus.collectAsState()
    val syncIntervalOptions = listOf(1, 6, 12, 24)
    var syncIntervalIndex by remember {
        val current = SyncManager.getSyncIntervalHours()
        mutableIntStateOf(syncIntervalOptions.indexOf(current).coerceAtLeast(0))
    }
    var cachedTrackCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            cachedTrackCount = MvbarDatabase.getInstance(context).trackDao().count()
        } catch (_: Exception) {}
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
    ) {
        // App info
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                }
            }
        }

        // Server info
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                }
            }
        }

        // Data Sync section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sync, null, tint = Cyan500)
                        Spacer(Modifier.width(12.dp))
                        Text("Data Sync", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Last sync time
                    val lastSyncText = if (lastSync > 0) {
                        val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                        "Last synced: ${sdf.format(java.util.Date(lastSync))}"
                    } else {
                        "Never synced"
                    }
                    Text(lastSyncText, style = MaterialTheme.typography.bodySmall, color = OnSurfaceDim)

                    // Cached tracks count
                    Text(
                        "$cachedTrackCount tracks cached locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )

                    Spacer(Modifier.height(12.dp))

                    // Sync interval
                    Text("Sync Interval", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Text(
                        "Every ${syncIntervalOptions[syncIntervalIndex]} hour${if (syncIntervalOptions[syncIntervalIndex] != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cyan500
                    )
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

                    Spacer(Modifier.height(8.dp))

                    // Sync Now button
                    Button(
                        onClick = {
                            SyncManager.syncNow(context)
                            scope.launch {
                                kotlinx.coroutines.delay(1000)
                                try {
                                    cachedTrackCount = MvbarDatabase.getInstance(context).trackDao().count()
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

        // Sign out
        item {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
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

@Composable
private fun PlaybackTab(
    cacheSizeMb: Long,
    cachedTrackCount: Int,
    cacheLimitSteps: List<Int>,
    cacheLimitIndex: Int,
    prefetchCount: Int,
    wifiOnly: Boolean,
    autoCacheFavorites: Boolean,
    autoCachePodcasts: Boolean,
    onClearCache: () -> Unit,
    onCacheLimitChange: (Int) -> Unit,
    onCacheLimitSet: () -> Unit,
    onPrefetchChange: (Int) -> Unit,
    onPrefetchSet: () -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAutoCacheChange: (Boolean) -> Unit,
    onAutoCachePodcastsChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
    ) {
        // Cache status
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Audio Cache", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                            Text(
                                "${cacheSizeMb} MB used · $cachedTrackCount tracks cached",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                        }
                        OutlinedButton(
                            onClick = onClearCache,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Cache limit
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Max Cache Size", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Text(
                        "${cacheLimitSteps[cacheLimitIndex]} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cyan500
                    )
                    Slider(
                        value = cacheLimitIndex.toFloat(),
                        onValueChange = { onCacheLimitChange(it.toInt()) },
                        onValueChangeFinished = onCacheLimitSet,
                        valueRange = 0f..(cacheLimitSteps.size - 1).toFloat(),
                        steps = cacheLimitSteps.size - 2,
                        colors = SliderDefaults.colors(
                            thumbColor = Cyan500,
                            activeTrackColor = Cyan500,
                            inactiveTrackColor = SurfaceDark
                        )
                    )
                }
            }
        }

        // Prefetch
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Prefetch Next Tracks", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Text(
                        "Auto-download $prefetchCount upcoming track${if (prefetchCount != 1) "s" else ""} for seamless playback",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    Slider(
                        value = prefetchCount.toFloat(),
                        onValueChange = { onPrefetchChange(it.toInt()) },
                        onValueChangeFinished = onPrefetchSet,
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

        // Toggles
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsToggle(
                        title = "WiFi Only Downloads",
                        subtitle = "Only prefetch and cache on WiFi",
                        checked = wifiOnly,
                        onCheckedChange = onWifiOnlyChange
                    )

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(8.dp))

                    SettingsToggle(
                        title = "Auto-Cache Favorites",
                        subtitle = "Keep favorited tracks available offline",
                        checked = autoCacheFavorites,
                        onCheckedChange = onAutoCacheChange
                    )

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = SurfaceDark)
                    Spacer(Modifier.height(8.dp))

                    SettingsToggle(
                        title = "Auto-Cache Podcasts",
                        subtitle = "Keep unplayed episodes available offline",
                        checked = autoCachePodcasts,
                        onCheckedChange = onAutoCachePodcastsChange
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugTab(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    debugEnabled: Boolean,
    uploadUrl: String,
    uploadStatus: String?,
    isUploading: Boolean,
    onToggleDebug: (Boolean) -> Unit,
    onViewLogs: () -> Unit,
    onShareLogs: () -> Unit,
    onCopyLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onUploadUrlChange: (String) -> Unit,
    onUpload: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
    ) {
        // Debug toggle
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsToggle(
                        title = "Debug Logging",
                        subtitle = "Log API calls, errors, and crashes",
                        checked = debugEnabled,
                        onCheckedChange = onToggleDebug
                    )
                }
            }
        }

        // Log actions
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Log Actions", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewLogs,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500)
                        ) {
                            Icon(Icons.Filled.List, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("View (${DebugLog.getEntries().size})")
                        }

                        OutlinedButton(
                            onClick = onShareLogs,
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
                            onClick = onCopyLogs,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan500)
                        ) {
                            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }

                        OutlinedButton(
                            onClick = onClearLogs,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear")
                        }
                    }
                }
            }
        }

        // Upload to server
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Upload to Server", style = MaterialTheme.typography.bodyLarge, color = OnSurface)
                    Text(
                        "Send logs directly to your dev server",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uploadUrl,
                        onValueChange = onUploadUrlChange,
                        label = { Text("Server URL") },
                        placeholder = { Text("http://10.10.100.5:9999") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan500,
                            unfocusedBorderColor = OnSurfaceSubtle,
                            focusedLabelColor = Cyan500,
                            unfocusedLabelColor = OnSurfaceDim,
                            cursorColor = Cyan500,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = onUpload,
                        enabled = !isUploading && uploadUrl.isNotBlank(),
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
                            uploadStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uploadStatus.startsWith("✓")) Color(0xFF22C55E) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
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

