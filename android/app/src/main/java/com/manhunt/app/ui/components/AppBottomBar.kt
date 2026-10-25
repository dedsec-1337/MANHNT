package com.manhunt.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppTab(val label: String, val icon: ImageVector) {
    HUNT("Hunt", Icons.Default.Explore),
    CREW("Crew", Icons.Default.Groups),
    YOU("You", Icons.Default.Person),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AppBottomBar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = colors.surface,
        contentColor = colors.onSurface
    ) {
        AppTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.primary.copy(alpha = 0.12f),
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant
                )
            )
        }
    }
}
