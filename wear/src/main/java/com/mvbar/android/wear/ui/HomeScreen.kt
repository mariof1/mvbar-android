package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun HomeScreen(
    backend: Backend,
    onOpenNowPlaying: () -> Unit,
    onOpenCacheSettings: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().background(WearTheme.Background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabPill(
                selected = tab == 0,
                accent = WearTheme.Orange,
                icon = Icons.Default.Podcasts,
                label = "Pods",
                onClick = { tab = 0 }
            )
            TabPill(
                selected = tab == 1,
                accent = WearTheme.Cyan,
                icon = Icons.Default.MusicNote,
                label = "Music",
                onClick = { tab = 1 }
            )
            Button(
                onClick = onOpenCacheSettings,
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = WearTheme.OnSurfaceDim)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> PodcastsTab(backend, onOpenNowPlaying)
                1 -> MusicTab(backend, onOpenNowPlaying)
            }
        }
    }
}

@Composable
private fun TabPill(
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = if (selected)
            ButtonDefaults.primaryButtonColors(backgroundColor = accent)
        else
            ButtonDefaults.secondaryButtonColors(backgroundColor = WearTheme.Surface),
        modifier = Modifier.height(28.dp).widthIn(min = 60.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = WearTheme.OnSurface, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                color = WearTheme.OnSurface,
                style = MaterialTheme.typography.caption2,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
