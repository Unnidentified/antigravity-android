package com.antigravity.wrapper.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antigravity.wrapper.data.BridgeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    conversationTitle: String,
    activeProject: String,
    isNewChat: Boolean,
    bridgeStatus: BridgeStatus,
    quotaLeftPercent: Float?,
    onOpenDrawer: () -> Unit,
    onOpenQuotaSheet: () -> Unit,
    onOpenWorkspaceSheet: () -> Unit,
    onNewChat: () -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val ringColor = if (quotaLeftPercent != null) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    }

    val displayTitle = if (isNewChat) {
        "New chat"
    } else {
        conversationTitle
            .takeIf { it.isNotBlank() && !it.equals("Google Antigravity", ignoreCase = true) }
            ?: "Antigravity"
    }

    val subtitle = when (bridgeStatus) {
        BridgeStatus.CONNECTING -> "Connecting…"
        BridgeStatus.OFFLINE -> "Offline"
        BridgeStatus.ERROR -> "Connection issue"
        BridgeStatus.AUTH_REQUIRED -> "Sign in required"
        BridgeStatus.CONNECTED -> if (isNewChat) null else activeProject.takeIf { it.isNotBlank() }
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
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
            Column {
                Text(
                    text = displayTitle,
                    style = if (isNewChat) {
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    },
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
            val targetSweep = if (quotaLeftPercent != null) {
                (quotaLeftPercent / 100f).coerceIn(0.05f, 1f) * 360f
            } else {
                90f
            }
            val animatedSweep by animateFloatAsState(
                targetValue = targetSweep,
                animationSpec = tween(durationMillis = 420),
                label = "QuotaRingSweep"
            )
            IconButton(onClick = onOpenQuotaSheet) {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val stroke = Stroke(width = 3.3.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = stroke
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = animatedSweep,
                            useCenter = false,
                            style = stroke
                        )
                    }
                }
            }
            if (!isNewChat) {
                IconButton(
                    onClick = onOpenWorkspaceSheet,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Workspace & actions",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    )
}
