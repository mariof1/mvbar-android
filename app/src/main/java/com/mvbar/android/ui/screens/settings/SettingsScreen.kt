package com.mvbar.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mvbar.android.BuildConfig
import com.mvbar.android.data.api.ApiClient
import com.mvbar.android.data.AaPreferences
import com.mvbar.android.data.local.MvbarDatabase
import com.mvbar.android.data.model.User
import com.mvbar.android.data.repository.AuthRepository
import com.mvbar.android.data.sync.SyncManager
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.player.AudioCacheManager
import com.mvbar.android.ui.LocalIsOnline
import com.mvbar.android.ui.theme.*
import com.mvbar.android.update.AppUpdateInfo
import com.mvbar.android.update.AppUpdateManager
import com.mvbar.android.update.UpdateInstallResult
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class UpdateUiState(
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val hasChecked: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null,
    val downloadedFile: File? = null,
    val lastCheckedAt: Long? = null,
    val message: String? = null,
    val error: String? = null,
    val installPermissionNeeded: Boolean = false
)

private const val UPDATE_AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onBrowseCache: () -> Unit = {},
    recommendationTuningCount: Int = 0,
    recommendationFeedbackBusy: Boolean = false,
    onLoadRecommendationTuning: () -> Unit = {},
    onResetRecommendationTuning: () -> Unit = {}
) {
    val context = LocalContext.current
    val isOnline = LocalIsOnline.current
    val scope = rememberCoroutineScope()
    val authRepository = remember(context) { AuthRepository(context.applicationContext) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var currentUserLoading by remember { mutableStateOf(true) }
    var debugEnabled by remember { mutableStateOf(DebugLog.enabled) }
    var showLogViewer by remember { mutableStateOf(false) }
    var logEntries by remember { mutableStateOf(DebugLog.getEntries()) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf(UpdateUiState()) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showResetRecommendationDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(authRepository) {
        currentUser = authRepository.getSavedUser()
        currentUserLoading = currentUser == null
        authRepository.refreshCurrentUser()
            .onSuccess { currentUser = it }
        currentUserLoading = false
    }

    LaunchedEffect(Unit) {
        onLoadRecommendationTuning()
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

    fun startDownloadedUpdate(file: File) {
        try {
            when (AppUpdateManager.startInstall(context, file)) {
                UpdateInstallResult.Started -> {
                    updateState = updateState.copy(
                        message = "Installer opened.",
                        error = null,
                        installPermissionNeeded = false
                    )
                }
                UpdateInstallResult.NeedsInstallPermission -> {
                    updateState = updateState.copy(
                        message = "Allow mvbar to install downloaded APKs, then tap Install.",
                        error = null,
                        installPermissionNeeded = true
                    )
                    AppUpdateManager.openInstallPermissionSettings(context)
                }
            }
        } catch (e: Exception) {
            updateState = updateState.copy(
                error = e.message ?: "Could not open the APK installer.",
                message = null
            )
        }
    }

    fun checkForAppUpdate(force: Boolean = false) {
        if (updateState.isChecking || updateState.isDownloading) return
        val now = System.currentTimeMillis()
        if (!force && updateState.hasChecked && updateState.lastCheckedAt?.let { now - it < UPDATE_AUTO_CHECK_INTERVAL_MS } == true) {
            return
        }
        val existingDownloadedFile = updateState.downloadedFile?.takeIf { it.exists() }
        val existingUpdateVersion = updateState.availableUpdate?.version
        scope.launch {
            updateState = updateState.copy(
                isChecking = true,
                message = null,
                error = null,
                installPermissionNeeded = false
            )
            try {
                val check = AppUpdateManager.checkForUpdates()
                val latest = if (check.updateAvailable) check.latest else null
                val keepDownloadedFile = latest != null &&
                    existingDownloadedFile != null &&
                    latest.version == existingUpdateVersion
                updateState = updateState.copy(
                    isChecking = false,
                    hasChecked = true,
                    availableUpdate = latest,
                    downloadedFile = if (keepDownloadedFile) existingDownloadedFile else null,
                    lastCheckedAt = System.currentTimeMillis(),
                    message = when {
                        check.updateAvailable -> null
                        else -> null
                    },
                    error = null
                )
            } catch (e: Exception) {
                updateState = updateState.copy(
                    isChecking = false,
                    hasChecked = true,
                    lastCheckedAt = System.currentTimeMillis(),
                    error = e.message ?: "Update check failed.",
                    message = null
                )
            }
        }
    }

    fun downloadAppUpdate(update: AppUpdateInfo) {
        if (updateState.isChecking || updateState.isDownloading) return
        scope.launch {
            updateState = updateState.copy(
                isDownloading = true,
                message = null,
                error = null,
                installPermissionNeeded = false
            )
            try {
                val file = AppUpdateManager.downloadUpdate(context, update)
                updateState = updateState.copy(
                    isDownloading = false,
                    downloadedFile = file,
                    message = "Downloaded ${update.assetName}.",
                    error = null
                )
                startDownloadedUpdate(file)
            } catch (e: Exception) {
                updateState = updateState.copy(
                    isDownloading = false,
                    error = e.message ?: "APK download failed.",
                    message = null
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        checkForAppUpdate(force = true)
        while (true) {
            kotlinx.coroutines.delay(UPDATE_AUTO_CHECK_INTERVAL_MS)
            checkForAppUpdate()
        }
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

    if (showUpdateDialog) {
        UpdateDialog(
            state = updateState,
            onDismiss = { showUpdateDialog = false },
            onCheck = { checkForAppUpdate(force = true) },
            onDownload = { updateState.availableUpdate?.let { downloadAppUpdate(it) } },
            onInstall = { updateState.downloadedFile?.let { startDownloadedUpdate(it) } },
            onOpenInstallSettings = { AppUpdateManager.openInstallPermissionSettings(context) }
        )
    }

    if (showClearCacheDialog) {
        SettingsConfirmationDialog(
            title = "Clear offline audio?",
            message = "This removes downloaded and cached audio from this device. Your favorites, playlists, and listening history stay on the server.",
            confirmLabel = "Clear audio",
            destructive = true,
            onConfirm = {
                showClearCacheDialog = false
                AudioCacheManager.clearCache()
                cacheSizeMb = 0
                cachedTrackCount = 0
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }

    if (showSignOutDialog) {
        SettingsConfirmationDialog(
            title = "Sign out of mvbar?",
            message = "You will need your server address and account details to sign in again. Offline audio remains on this device.",
            confirmLabel = "Sign out",
            destructive = true,
            onConfirm = {
                showSignOutDialog = false
                onLogout()
            },
            onDismiss = { showSignOutDialog = false }
        )
    }

    if (showResetRecommendationDialog) {
        SettingsConfirmationDialog(
            title = "Reset recommendation tuning?",
            message = "This removes your More like this, Less from this artist, hidden mix, and Don’t recommend choices. Listening history and favorites are not changed.",
            confirmLabel = "Reset tuning",
            destructive = true,
            onConfirm = {
                showResetRecommendationDialog = false
                onResetRecommendationTuning()
            },
            onDismiss = { showResetRecommendationDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSectionCard(
                    title = "App & account",
                    subtitle = "Connection, version, and account access",
                    icon = Icons.Filled.Person
                ) {
                    CurrentUserProfile(
                        user = currentUser,
                        loading = currentUserLoading
                    )
                    SettingsDivider(indented = false)
                    SettingsInfoRow(
                        icon = Icons.Filled.MusicNote,
                        title = "mvbar Android",
                        subtitle = "Version ${BuildConfig.VERSION_NAME}",
                        trailing = {
                            CompactUpdateButton(
                                state = updateState,
                                onClick = {
                                    showUpdateDialog = true
                                    if (!updateState.hasChecked && updateState.downloadedFile == null) {
                                        checkForAppUpdate(force = true)
                                    }
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        icon = Icons.Filled.Dns,
                        title = "Connected server",
                        subtitle = ApiClient.getBaseUrl().removeSuffix("/")
                    )
                    SettingsDivider(indented = false)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                SettingsSectionCard(
                    title = "Playback",
                    subtitle = "Startup and seamless listening",
                    icon = Icons.Filled.PlayCircle
                ) {
                    SettingsToggle(
                        icon = Icons.Filled.Restore,
                        title = "Resume where I left off",
                        subtitle = "Restore the last queue and reopen the player at launch",
                        checked = autoResume,
                        onCheckedChange = {
                            autoResume = it
                            scope.launch { AaPreferences.saveAutoResume(context, it) }
                        }
                    )
                    SettingsDivider()
                    SettingsSlider(
                        icon = Icons.Filled.SkipNext,
                        title = "Preload upcoming tracks",
                        subtitle = "Keep the next songs ready for gap-free playback",
                        valueLabel = if (prefetchCount == 0) "Off" else "$prefetchCount track${if (prefetchCount == 1) "" else "s"}",
                        value = prefetchCount.toFloat(),
                        onValueChange = { prefetchCount = it.roundToInt() },
                        onValueChangeFinished = { AudioCacheManager.setPrefetchCount(prefetchCount) },
                        valueRange = 0f..5f,
                        steps = 4
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        icon = Icons.Filled.Tune,
                        title = "Recommendation tuning",
                        subtitle = if (recommendationTuningCount == 0) {
                            "No manual tuning saved"
                        } else {
                            "$recommendationTuningCount saved ${if (recommendationTuningCount == 1) "choice" else "choices"}"
                        },
                        trailing = {
                            OutlinedButton(
                                onClick = { showResetRecommendationDialog = true },
                                enabled = isOnline && recommendationTuningCount > 0 && !recommendationFeedbackBusy,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (recommendationFeedbackBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Reset")
                                }
                            }
                        }
                    )
                }
            }

            item {
                val limitMb = cacheLimitSteps[cacheLimitIndex]
                val limitLabel = if (limitMb >= 1000) {
                    val gigabytes = limitMb / 1000.0
                    if (gigabytes % 1.0 == 0.0) "${gigabytes.toInt()} GB" else "${gigabytes} GB"
                } else {
                    "$limitMb MB"
                }
                val usageFraction = (cacheSizeMb.toFloat() / limitMb.toFloat()).coerceIn(0f, 1f)

                SettingsSectionCard(
                    title = "Downloads & storage",
                    subtitle = "Control offline audio and data usage",
                    icon = Icons.Filled.DownloadForOffline
                ) {
                    SettingsInfoRow(
                        icon = Icons.Filled.OfflinePin,
                        title = "Offline audio",
                        subtitle = "$cachedTrackCount audio item${if (cachedTrackCount == 1) "" else "s"} available offline",
                        trailing = { SettingsValueBadge("$cacheSizeMb MB") }
                    )
                    LinearProgressIndicator(
                        progress = { usageFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Cyan500,
                        trackColor = WhiteOverlay10
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBrowseCache,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400)
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Manage")
                        }
                        OutlinedButton(
                            onClick = { showClearCacheDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingsDivider(indented = false)
                    SettingsSlider(
                        icon = Icons.Filled.Storage,
                        title = "Storage limit",
                        subtitle = "mvbar removes the least recently used audio when full",
                        valueLabel = limitLabel,
                        value = cacheLimitIndex.toFloat(),
                        onValueChange = { cacheLimitIndex = it.roundToInt() },
                        onValueChangeFinished = {
                            AudioCacheManager.setMaxCacheMb(cacheLimitSteps[cacheLimitIndex])
                        },
                        valueRange = 0f..(cacheLimitSteps.size - 1).toFloat(),
                        steps = cacheLimitSteps.size - 2
                    )
                    SettingsDivider()
                    SettingsToggle(
                        icon = Icons.Filled.Wifi,
                        title = "Download on Wi-Fi only",
                        subtitle = "Automatic downloads wait for a Wi-Fi connection",
                        checked = wifiOnly,
                        onCheckedChange = {
                            wifiOnly = it
                            AudioCacheManager.setWifiOnlyDownload(it)
                        }
                    )
                    SettingsDivider()
                    SettingsToggle(
                        icon = Icons.Filled.Favorite,
                        title = "Keep favorites offline",
                        subtitle = "Automatically download favorited songs",
                        checked = autoCacheFavorites,
                        onCheckedChange = {
                            autoCacheFavorites = it
                            AudioCacheManager.setAutoCacheFavorites(it)
                        }
                    )
                }
            }

            item {
                val lastSyncText = if (lastSync > 0) {
                    val formatter = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                    "Last synced ${formatter.format(java.util.Date(lastSync))}"
                } else {
                    "Not synced yet"
                }
                val intervalHours = syncIntervalOptions[syncIntervalIndex]

                SettingsSectionCard(
                    title = "Library sync",
                    subtitle = "Keep browsing data ready when you are offline",
                    icon = Icons.Filled.Sync
                ) {
                    SettingsInfoRow(
                        icon = if (isSyncing) Icons.Filled.Sync else Icons.Filled.CloudDone,
                        title = if (isSyncing) syncStatus.ifEmpty { "Syncing library…" } else lastSyncText,
                        subtitle = "$dbTrackCount tracks stored in the local library index",
                        trailing = {
                            SettingsValueBadge(
                                text = if (isSyncing) "Working" else "Ready",
                                color = if (isSyncing) Orange400 else Cyan400
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsSlider(
                        icon = Icons.Filled.Schedule,
                        title = "Background refresh",
                        subtitle = "How often mvbar refreshes its local library data",
                        valueLabel = if (intervalHours == 1) "Hourly" else "Every $intervalHours h",
                        value = syncIntervalIndex.toFloat(),
                        onValueChange = { syncIntervalIndex = it.roundToInt() },
                        onValueChangeFinished = {
                            SyncManager.setSyncIntervalHours(context, syncIntervalOptions[syncIntervalIndex])
                        },
                        valueRange = 0f..(syncIntervalOptions.size - 1).toFloat(),
                        steps = syncIntervalOptions.size - 2
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan500.copy(alpha = 0.16f),
                            contentColor = Cyan400
                        )
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Cyan400,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSyncing) "Syncing…" else "Sync now", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                SettingsSectionCard(
                    title = "Android Auto",
                    subtitle = "Choose the order of categories shown in your car",
                    icon = Icons.Filled.DirectionsCar
                ) {
                    if (categories.isEmpty()) {
                        SettingsInfoRow(
                            icon = Icons.Filled.HourglassEmpty,
                            title = "Loading categories",
                            subtitle = "Your Android Auto layout will appear here"
                        )
                    } else {
                        categories.forEachIndexed { index, key ->
                            if (index > 0) SettingsDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(34.dp),
                                    shape = RoundedCornerShape(11.dp),
                                    color = Cyan500.copy(alpha = 0.14f),
                                    contentColor = Cyan400
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    AaPreferences.displayName(key),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = OnSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { moveCategory(index, -1) },
                                    enabled = index > 0,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(WhiteOverlay5, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowUp,
                                        contentDescription = "Move ${AaPreferences.displayName(key)} up",
                                        tint = if (index > 0) Cyan400 else OnSurfaceSubtle
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = { moveCategory(index, 1) },
                                    enabled = index < categories.lastIndex,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(WhiteOverlay5, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Move ${AaPreferences.displayName(key)} down",
                                        tint = if (index < categories.lastIndex) Cyan400 else OnSurfaceSubtle
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Changes apply the next time Android Auto connects.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim,
                            modifier = Modifier.padding(start = 48.dp)
                        )
                    }
                }
            }

            item { WearOsCard() }

            item {
                SettingsSectionCard(
                    title = "Help & diagnostics",
                    subtitle = "Troubleshooting tools and diagnostic logs",
                    icon = Icons.Filled.Build
                ) {
                    SettingsToggle(
                        icon = Icons.Filled.BugReport,
                        title = "Debug logging",
                        subtitle = "Record API calls, playback errors, and crashes",
                        checked = debugEnabled,
                        onCheckedChange = {
                            debugEnabled = it
                            DebugLog.enabled = it
                            DebugLog.save(context)
                            ApiClient.rebuild()
                            if (it) DebugLog.i("Settings", "Debug logging enabled")
                        }
                    )
                    SettingsDivider()
                    SettingsInfoRow(
                        icon = Icons.Filled.Description,
                        title = "Diagnostic log",
                        subtitle = "${DebugLog.getEntries().size} entries currently stored on this device"
                    )
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("View", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { DebugLog.shareLog(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Share", fontSize = 13.sp)
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Copy", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                DebugLog.clear()
                                logEntries = emptyList()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Clear log", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Cyan500)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isUploading) "Uploading…" else "Upload log to server",
                            color = Color.Black,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    uploadStatus?.let { status ->
                        val success = status.startsWith("✓")
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = (if (success) Color(0xFF22C55E) else MaterialTheme.colorScheme.error).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                status,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (success) Color(0xFF4ADE80) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentUserProfile(user: User?, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Cyan500.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                user != null -> Text(
                    text = user.email.firstOrNull()?.uppercase() ?: "?",
                    color = Cyan400,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Cyan400,
                    strokeWidth = 2.dp
                )
                else -> Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Cyan400,
                    modifier = Modifier.size(26.dp)
                )
            }

            if (user != null && !user.avatarPath.isNullOrBlank()) {
                AsyncImage(
                    model = ApiClient.avatarUrl(user.avatarPath),
                    contentDescription = "Avatar for ${user.email}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Signed in as",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceDim
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    user != null -> user.email
                    loading -> "Loading account…"
                    else -> "Account details unavailable"
                },
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (user != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (user.role.equals("admin", ignoreCase = true)) "Administrator" else "Member",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            }
        }
    }
}
