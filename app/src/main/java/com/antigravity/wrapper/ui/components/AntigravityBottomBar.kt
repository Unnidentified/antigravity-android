package com.antigravity.wrapper.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.antigravity.wrapper.data.AppDestination

@Composable
fun AntigravityBottomBar(
    activeDestination: AppDestination,
    savedCount: Int,
    downloadsCount: Int,
    onSelectDestination: (AppDestination) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == activeDestination
            val icon = when (destination) {
                AppDestination.WORKSPACE -> if (selected) Icons.Rounded.Terminal else Icons.Outlined.Terminal
                AppDestination.SAVED -> if (selected) Icons.Rounded.Bookmarks else Icons.Outlined.Bookmarks
                AppDestination.DOWNLOADS -> if (selected) Icons.Rounded.Download else Icons.Outlined.Download
                AppDestination.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
            }
            val badgeCount = when (destination) {
                AppDestination.SAVED -> savedCount
                AppDestination.DOWNLOADS -> downloadsCount
                else -> 0
            }

            NavigationBarItem(
                selected = selected,
                onClick = { onSelectDestination(destination) },
                icon = {
                    if (badgeCount > 0 && destination == AppDestination.DOWNLOADS) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                }
                            }
                        ) {
                            Icon(imageVector = icon, contentDescription = destination.label)
                        }
                    } else {
                        Icon(imageVector = icon, contentDescription = destination.label)
                    }
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
