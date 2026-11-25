package com.antigravity.wrapper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.wrapper.data.BridgeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    conversationTitle: String,
    activeProject: String,
    bridgeStatus: BridgeStatus,
    quotaUsedPercent: Float?,
    onOpenDrawer: () -> Unit,
    onOpenQuotaSheet: () -> Unit,
    onNewChat: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val displayTitle = conversationTitle
        .takeIf { it.isNotBlank() && !it.equals("Google Antigravity", ignoreCase = true) }
        ?: "Antigravity"

    val subtitle = when (bridgeStatus) {
        BridgeStatus.CONNECTING -> "Connecting…"
        BridgeStatus.OFFLINE -> "Offline"
        BridgeStatus.ERROR -> "Connection issue"
        BridgeStatus.AUTH_REQUIRED -> "Sign in required"
        BridgeStatus.CONNECTED -> activeProject.takeIf { it.isNotBlank() }
    }

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Open conversations"
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenQuotaSheet) {
                if (quotaUsedPercent != null) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = (quotaUsedPercent / 100f).coerceIn(0.05f, 1f) * 360f,
                                useCenter = false,
                                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = "Usage & quota",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Rounded.EditNote,
                    contentDescription = "New chat",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    )
}
