package com.antigravity.wrapper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wrapper.data.ArtifactCard
import com.antigravity.wrapper.data.ChatMessage
import com.antigravity.wrapper.data.MessageRole
import com.antigravity.wrapper.data.ReasoningBlock
import com.antigravity.wrapper.ui.markdown.NativeMarkdownView
import kotlinx.coroutines.delay

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    fontScale: Float,
    expandedReasoningIds: Map<String, Boolean>,
    autoExpandReasoning: Boolean,
    onToggleReasoning: (blockId: String, defaultExpanded: Boolean) -> Unit,
    onExpandReasoningContent: (blockId: String, blockTitle: String) -> Unit = { _, _ -> },
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onArtifactClick: (ArtifactCard) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (message.role == MessageRole.USER) {
        UserMessageBubble(
            message = message,
            fontScale = fontScale,
            onCopyText = onCopyText,
            modifier = modifier
        )
    } else {
        AssistantMessageSection(
            message = message,
            fontScale = fontScale,
            expandedReasoningIds = expandedReasoningIds,
            autoExpandReasoning = autoExpandReasoning,
            onToggleReasoning = onToggleReasoning,
            onExpandReasoningContent = onExpandReasoningContent,
            onCopyText = onCopyText,
            onShareText = onShareText,
            onLinkClick = onLinkClick,
            onArtifactClick = onArtifactClick,
            modifier = modifier
        )
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    fontScale: Float,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = 22.dp,
                bottomEnd = 6.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clickable { onCopyText(message.markdownContent) }
        ) {
            Text(
                text = message.markdownContent,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp * fontScale,
                    lineHeight = 22.sp * fontScale,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageSection(
    message: ChatMessage,
    fontScale: Float,
    expandedReasoningIds: Map<String, Boolean>,
    autoExpandReasoning: Boolean,
    onToggleReasoning: (blockId: String, defaultExpanded: Boolean) -> Unit,
    onExpandReasoningContent: (blockId: String, blockTitle: String) -> Unit,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onArtifactClick: (ArtifactCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Branded assistant label row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                AgyLogo(modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Antigravity",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (message.timestampLabel.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${message.timestampLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (message.isStreaming) {
                Spacer(modifier = Modifier.width(8.dp))
                StreamingPulseBadge()
            }
        }

        // Collapsible & Expandable Reasoning / Thought Blocks
        message.reasoningBlocks.forEach { block ->
            val defaultExpanded = autoExpandReasoning || block.isStreaming
            val isExpanded = expandedReasoningIds[block.id] ?: defaultExpanded
            CollapsibleReasoningCard(
                block = block,
                expanded = isExpanded,
                fontScale = fontScale,
                onToggle = {
                    if (!isExpanded && block.content.isBlank()) {
                        onExpandReasoningContent(block.id, block.title)
                    }
                    onToggleReasoning(block.id, defaultExpanded)
                },
                onRequestContent = { onExpandReasoningContent(block.id, block.title) },
                onCopyCode = onCopyText,
                onLinkClick = onLinkClick
            )
        }

        // Native Markdown + LaTeX + Code Response Body
        if (message.markdownContent.isNotBlank()) {
            NativeMarkdownView(
                markdown = message.markdownContent,
                fontScale = fontScale,
                onCopyCode = onCopyText,
                onLinkClick = onLinkClick
            )
        }

        // Artifact / Diff Summary Cards
        message.artifacts.forEach { artifact ->
            ArtifactSummaryCard(
                artifact = artifact,
                onOpen = { onArtifactClick(artifact) }
            )
        }

        // Native Message Action Row
        val isThinkingStub = message.markdownContent.trim()
            .matches(Regex("^Thinking(?:\\s+for\\s+\\d+\\s*[sm])?$", RegexOption.IGNORE_CASE))
        if (!message.isStreaming && message.markdownContent.isNotBlank() && !isThinkingStub) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        onCopyText(message.markdownContent)
                        copied = true
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy response",
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = { onShareText(message.markdownContent) },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share response",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CollapsibleReasoningCard(
    block: ReasoningBlock,
    expanded: Boolean,
    fontScale: Float,
    onToggle: () -> Unit,
    onRequestContent: () -> Unit = {},
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "ReasoningChevron"
    )
    var awaitingContent by remember(block.id) { mutableStateOf(false) }
    var requestedRemote by remember(block.id) { mutableStateOf(false) }

    LaunchedEffect(expanded, block.content) {
        if (expanded && block.content.isBlank()) {
            awaitingContent = true
            if (!requestedRemote) {
                requestedRemote = true
                onRequestContent()
            }
            delay(1800L)
            awaitingContent = false
        } else {
            awaitingContent = false
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (block.durationLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = block.durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse reasoning" else "Expand reasoning",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (block.content.isNotBlank()) {
                        NativeMarkdownView(
                            markdown = block.content,
                            fontScale = fontScale * 0.92f,
                            onCopyCode = onCopyCode,
                            onLinkClick = onLinkClick
                        )
                    } else if (awaitingContent) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Loading reasoning steps…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = if (block.durationLabel.isNotBlank()) {
                                "Agent worked for ${block.durationLabel}; detailed step trace is not expanded on the desktop session."
                            } else {
                                "No detailed thought steps logged for this section."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactSummaryCard(
    artifact: ArtifactCard,
    onOpen: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Difference,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (artifact.addedLines != null && artifact.removedLines != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "+${artifact.addedLines}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF4ADE80)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "-${artifact.removedLines}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFF87171)
                        )
                    }
                }
                if (artifact.subtitle.isNotBlank()) {
                    Text(
                        text = artifact.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            val pillLabel = when {
                artifact.actionLabel.isNotBlank() -> artifact.actionLabel
                artifact.addedLines != null -> "Review"
                else -> "Open"
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = pillLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (artifact.url.startsWith("http", ignoreCase = true)) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingPulseBadge() {
    val transition = rememberInfiniteTransition(label = "StreamingPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StreamingAlpha"
    )
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)
    ) {
        Text(
            text = "Generating…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
