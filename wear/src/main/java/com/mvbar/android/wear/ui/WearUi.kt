package com.mvbar.android.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text

@Composable
fun WearList(
    modifier: Modifier = Modifier,
    content: ScalingLazyListScope.() -> Unit
) {
    val listState = rememberScalingLazyListState()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearTheme.Background)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
        PositionIndicator(scalingLazyListState = listState)
    }
}

@Composable
fun WearHeaderChip(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    accent: Color = WearTheme.Cyan
) {
    Chip(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = accent) },
        label = {
            Text(
                title,
                color = WearTheme.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        },
        secondaryLabel = subtitle?.let {
            {
                Text(
                    it,
                    color = WearTheme.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.caption2
                )
            }
        }
    )
}

@Composable
fun WearStatusChip(
    title: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Default.Inbox,
    accent: Color = WearTheme.Cyan,
    onClick: (() -> Unit)? = null
) {
    if (onClick == null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(WearTheme.Surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = WearTheme.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
                subtitle?.let {
                    Text(
                        it,
                        color = WearTheme.OnSurfaceDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.caption2
                    )
                }
            }
        }
        return
    }

    Chip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ChipDefaults.secondaryChipColors(backgroundColor = WearTheme.Surface),
        icon = { Icon(icon, contentDescription = null, tint = accent) },
        label = {
            Text(
                title,
                color = WearTheme.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
        },
        secondaryLabel = subtitle?.let {
            {
                Text(
                    it,
                    color = WearTheme.OnSurfaceDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.caption2
                )
            }
        }
    )
}

@Composable
fun LoadingChip(label: String = "Loading") {
    WearStatusChip(label, "One moment", Icons.Default.HourglassEmpty, WearTheme.Cyan)
}

@Composable
fun EmptyChip(title: String, subtitle: String? = null) {
    WearStatusChip(title, subtitle, Icons.Default.Inbox, WearTheme.OnSurfaceDim)
}

@Composable
fun OfflineChip(onRetry: (() -> Unit)? = null) {
    WearStatusChip(
        title = "Can't reach mvbar",
        subtitle = "Check Wi-Fi or phone connection",
        icon = Icons.Default.CloudOff,
        accent = WearTheme.Orange,
        onClick = onRetry
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = WearTheme.OnSurfaceSubtle,
        style = MaterialTheme.typography.caption2,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
fun WearInfoPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(WearTheme.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        content()
    }
}

@Composable
fun WearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: Color = WearTheme.Cyan
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(WearTheme.SurfaceRaised)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
}

@Composable
fun WearInfoText(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: Color = WearTheme.OnSurface
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            title,
            color = titleColor,
            style = MaterialTheme.typography.caption1,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            color = WearTheme.OnSurfaceDim,
            style = MaterialTheme.typography.caption2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RoundIconAction(
    label: String,
    icon: ImageVector,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = WearTheme.OnSurface
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.primaryButtonColors(backgroundColor = background),
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun CenteredActions(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
fun FullScreenMessage(title: String, subtitle: String? = null, icon: ImageVector = Icons.Default.Inbox) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearTheme.Background)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        WearStatusChip(title, subtitle, icon)
    }
}
