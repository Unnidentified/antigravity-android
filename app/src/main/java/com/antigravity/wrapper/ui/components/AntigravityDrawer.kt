package com.antigravity.wrapper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.wrapper.BuildConfig
import com.antigravity.wrapper.data.AppDestination
import com.antigravity.wrapper.data.BookmarkItem

@Composable
fun AntigravityDrawerSheet(
    activeDestination: AppDestination,
    currentTitle: String,
    currentUrl: String,
    pinnedBookmarks: List<BookmarkItem>,
    isDesktopMode: Boolean,
    isImmersiveMode: Boolean,
    onSelectDestination: (AppDestination) -> Unit,
    onGoHome: () -> Unit,
    onOpenBookmark: (BookmarkItem) -> Unit,
    onOpenQuickPrompts: () -> Unit,
    onOpenFindInPage: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onToggleImmersiveMode: () -> Unit,
    onSharePage: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.width(316.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Branded Material 3 Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Antigravity",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUrl.removePrefix("https://"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = onGoHome,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Home,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Home", style = MaterialTheme.typography.labelMedium)
                            }
                            FilledTonalButton(
                                onClick = onOpenQuickPrompts,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Prompts", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            DrawerSectionLabel("Navigate")

            NavigationDrawerItem(
                label = { Text("Workspace") },
                selected = activeDestination == AppDestination.WORKSPACE,
                onClick = { onSelectDestination(AppDestination.WORKSPACE) },
                icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Saved & Bookmarks") },
                selected = activeDestination == AppDestination.SAVED,
                onClick = { onSelectDestination(AppDestination.SAVED) },
                icon = { Icon(Icons.Rounded.Bookmarks, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Downloads") },
                selected = activeDestination == AppDestination.DOWNLOADS,
                onClick = { onSelectDestination(AppDestination.DOWNLOADS) },
                icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Settings") },
                selected = activeDestination == AppDestination.SETTINGS,
                onClick = { onSelectDestination(AppDestination.SETTINGS) },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            if (pinnedBookmarks.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                DrawerSectionLabel("Pinned")
                pinnedBookmarks.take(6).forEach { bookmark ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = bookmark.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        selected = currentUrl == bookmark.url &&
                            activeDestination == AppDestination.WORKSPACE,
                        onClick = { onOpenBookmark(bookmark) },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            DrawerSectionLabel("Page Tools")

            NavigationDrawerItem(
                label = { Text("Find in page") },
                selected = false,
                onClick = onOpenFindInPage,
                icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = {
                    Text(if (isDesktopMode) "Switch to mobile view" else "Switch to desktop view")
                },
                selected = isDesktopMode,
                onClick = onToggleDesktopMode,
                icon = {
                    Icon(
                        imageVector = if (isDesktopMode) {
                            Icons.Rounded.Smartphone
                        } else {
                            Icons.Rounded.DesktopWindows
                        },
                        contentDescription = null
                    )
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = {
                    Text(if (isImmersiveMode) "Exit focus mode" else "Focus mode (hide bars)")
                },
                selected = isImmersiveMode,
                onClick = onToggleImmersiveMode,
                icon = { Icon(Icons.Rounded.Fullscreen, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Share current page") },
                selected = false,
                onClick = onSharePage,
                icon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                label = { Text("Open in Chrome tab") },
                selected = false,
                onClick = onOpenInBrowser,
                icon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
    )
}
