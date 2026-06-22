package com.mvbar.android.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mvbar.android.data.AaPreferences
import com.mvbar.android.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun AndroidAutoTab() {
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

