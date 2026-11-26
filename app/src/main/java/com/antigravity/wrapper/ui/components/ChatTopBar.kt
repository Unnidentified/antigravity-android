package com.antigravity.wrapper.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wrapper.data.BridgeStatus

/**
 * Thick horizontal progress bar with strict 90-degree edges (`drawRect`, 0dp radius) paired
 * with an angular monospace tabular percentage label directly to its right.
 */
@Composable
fun SharpQuotaProgressBar(
    leftPercent: Float?,
    modifier: Modifier = Modifier,
    barWidth: Dp? = null,
    barHeight: Dp = 9.dp,
    textSize: TextUnit = 13.sp,
    trackColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    fillColor: androidx.compose.ui.graphics.Color = if (leftPercent != null) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    },
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val targetFraction = if (leftPercent != null) {
        (leftPercent / 100f).coerceIn(0f, 1f)
    } else {
        0.25f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 420),
        label = "SharpQuotaProgress"
    )
    val pctLabel = if (leftPercent != null) "${leftPercent.toInt()}%" else "--%"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val barModifier = if (barWidth != null) {
            Modifier.width(barWidth).height(barHeight)
        } else {
            Modifier.weight(1f).height(barHeight)
        }
        Canvas(modifier = barModifier) {
            drawRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = size
            )
            val fillW = (size.width * animatedFraction).coerceIn(0f, size.width)
            if (fillW > 0f) {
                drawRect(
                    color = fillColor,
                    topLeft = Offset.Zero,
                    size = Size(fillW, size.height)
                )
            }
        }
        Text(
            text = pctLabel,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = textSize,
                fontFeatureSettings = "tnum",
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

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

    val pctText = if (quotaLeftPercent != null) "${quotaLeftPercent.toInt()}%" else "--%"

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Open conversations",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp)
            ) {
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

            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clickable(onClick = onOpenQuotaSheet)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Usage quota: $pctText"
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SharpQuotaProgressBar(
                    leftPercent = quotaLeftPercent,
                    barWidth = 60.dp,
                    barHeight = 15.dp,
                    textSize = 12.sp,
                    trackColor = trackColor,
                    fillColor = ringColor,
                    textColor = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onOpenWorkspaceSheet,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Workspace & actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
