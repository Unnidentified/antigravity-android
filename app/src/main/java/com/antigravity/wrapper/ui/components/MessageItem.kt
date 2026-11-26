package com.antigravity.wrapper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wrapper.data.ArtifactCard
import com.antigravity.wrapper.data.AssistantSegmentKind
import com.antigravity.wrapper.data.ChatMessage
import com.antigravity.wrapper.data.MessageRole
import com.antigravity.wrapper.data.ReasoningBlock
import com.antigravity.wrapper.data.ReasoningSection
import com.antigravity.wrapper.data.ReasoningStepItem
import com.antigravity.wrapper.data.ReasoningStepKind
import com.antigravity.wrapper.ui.markdown.NativeMarkdownView
import com.antigravity.wrapper.ui.theme.diffColors
import kotlinx.coroutines.delay

private val THINKING_STUB_REGEX = Regex("^Thinking(?:\\s+for\\s+\\d+\\s*[sm])?$", RegexOption.IGNORE_CASE)
private val USER_BUBBLE_SHAPE = RoundedCornerShape(
    topStart = 22.dp,
    topEnd = 22.dp,
    bottomStart = 22.dp,
    bottomEnd = 6.dp
)
private val REASONING_ROW_SHAPE = RoundedCornerShape(8.dp)
private val STEP_ROW_SHAPE = RoundedCornerShape(6.dp)
private val BADGE_SHAPE = RoundedCornerShape(4.dp)
private val ARTIFACT_CARD_SHAPE = RoundedCornerShape(18.dp)
private val PILL_SHAPE = RoundedCornerShape(999.dp)

private val REASONING_ENTER_TRANSITION = expandVertically(
    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
    expandFrom = Alignment.Top
) + fadeIn(animationSpec = tween(durationMillis = 120))

private val REASONING_EXIT_TRANSITION = shrinkVertically(
    animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing),
    shrinkTowards = Alignment.Top
)

internal fun formatReasoningFileDisplayName(fileName: String, title: String): String {
    val raw = fileName.ifBlank { title }.trim()
    if (raw.isEmpty()) return raw
    val normalized = raw.trimEnd('/', '\\')
    if (normalized.isEmpty()) return raw
    val lastSlash = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
    return if (lastSlash >= 0) normalized.substring(lastSlash + 1) else normalized
}

@Immutable
data class ExpandedReasoningState(
    val ids: Map<String, Boolean> = emptyMap()
)

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    fontScale: Float,
    expandedReasoningState: ExpandedReasoningState,
    autoExpandReasoning: Boolean,
    onToggleReasoning: (blockId: String, defaultExpanded: Boolean) -> Unit,
    onExpandReasoningContent: (blockId: String, blockTitle: String) -> Unit = { _, _ -> },
    onExpandReasoningStep: (blockId: String, sectionId: String, stepId: String, stepTitle: String) -> Unit = { _, _, _, _ -> },
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
            expandedReasoningIds = expandedReasoningState.ids,
            autoExpandReasoning = autoExpandReasoning,
            onToggleReasoning = onToggleReasoning,
            onExpandReasoningContent = onExpandReasoningContent,
            onExpandReasoningStep = onExpandReasoningStep,
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
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val textStyle = remember(colorScheme.onSurface, fontScale) {
        typography.bodyLarge.copy(
            fontSize = 15.sp * fontScale,
            lineHeight = 22.sp * fontScale,
            color = colorScheme.onSurface
        )
    }
    val handleClick = remember(message.markdownContent, onCopyText) {
        { onCopyText(message.markdownContent) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            onClick = handleClick,
            shape = USER_BUBBLE_SHAPE,
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(
                text = message.markdownContent,
                style = textStyle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ReasoningBlockRow(
    block: ReasoningBlock,
    fontScale: Float,
    expandedReasoningIds: Map<String, Boolean>,
    autoExpandReasoning: Boolean,
    onToggleReasoning: (blockId: String, defaultExpanded: Boolean) -> Unit,
    onExpandReasoningContent: (blockId: String, blockTitle: String) -> Unit,
    onExpandReasoningStep: (blockId: String, sectionId: String, stepId: String, stepTitle: String) -> Unit,
    onCopyText: (String) -> Unit,
    onLinkClick: (String) -> Unit
) {
    val defaultExpanded = autoExpandReasoning || block.isStreaming
    val isExpanded = expandedReasoningIds[block.id] ?: defaultExpanded
    CollapsibleReasoningTree(
        block = block,
        headerText = block.title,
        expanded = isExpanded,
        expandedIds = expandedReasoningIds,
        autoExpand = autoExpandReasoning,
        fontScale = fontScale,
        onToggleBlock = {
            onToggleReasoning(block.id, defaultExpanded)
        },
        onToggleNode = { nodeId, nodeDefault ->
            onToggleReasoning(nodeId, nodeDefault)
        },
        onRequestBlockContent = { onExpandReasoningContent(block.id, block.title) },
        onRequestStepExpand = { sectionId, stepId, stepTitle ->
            onExpandReasoningStep(block.id, sectionId, stepId, stepTitle)
        },
        onCopyCode = onCopyText,
        onLinkClick = onLinkClick
    )
}

@Composable
private fun AssistantMessageSection(
    message: ChatMessage,
    fontScale: Float,
    expandedReasoningIds: Map<String, Boolean>,
    autoExpandReasoning: Boolean,
    onToggleReasoning: (blockId: String, defaultExpanded: Boolean) -> Unit,
    onExpandReasoningContent: (blockId: String, blockTitle: String) -> Unit,
    onExpandReasoningStep: (blockId: String, sectionId: String, stepId: String, stepTitle: String) -> Unit,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onArtifactClick: (ArtifactCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var copied by remember(message.id) { mutableStateOf(false) }
    if (copied) {
        LaunchedEffect(message.id) {
            delay(1500L)
            copied = false
        }
    }

    val copyableMarkdown = remember(message.markdownContent, message.segments) {
        if (message.markdownContent.isNotBlank()) {
            message.markdownContent
        } else {
            message.segments
                .asSequence()
                .filter { it.kind == AssistantSegmentKind.MARKDOWN && it.markdown.isNotBlank() }
                .joinToString("\n\n") { it.markdown }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (message.isStreaming) {
            StreamingPulseBadge()
        }

        if (message.segments.isNotEmpty()) {
            // Interleaved chronological segments (reasoning blocks <-> markdown prose)
            for (i in message.segments.indices) {
                val segment = message.segments[i]
                when (segment.kind) {
                    AssistantSegmentKind.REASONING -> {
                        val block = segment.reasoningBlock
                        if (block != null) {
                            key(segment.id) {
                                ReasoningBlockRow(
                                    block = block,
                                    fontScale = fontScale,
                                    expandedReasoningIds = expandedReasoningIds,
                                    autoExpandReasoning = autoExpandReasoning,
                                    onToggleReasoning = onToggleReasoning,
                                    onExpandReasoningContent = onExpandReasoningContent,
                                    onExpandReasoningStep = onExpandReasoningStep,
                                    onCopyText = onCopyText,
                                    onLinkClick = onLinkClick
                                )
                            }
                        }
                    }
                    AssistantSegmentKind.MARKDOWN -> {
                        if (segment.markdown.isNotBlank()) {
                            key(segment.id) {
                                NativeMarkdownView(
                                    markdown = segment.markdown,
                                    fontScale = fontScale,
                                    onCopyCode = onCopyText,
                                    onLinkClick = onLinkClick
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Fallback for messages without interleaved segments
            for (i in message.reasoningBlocks.indices) {
                val block = message.reasoningBlocks[i]
                key(block.id) {
                    ReasoningBlockRow(
                        block = block,
                        fontScale = fontScale,
                        expandedReasoningIds = expandedReasoningIds,
                        autoExpandReasoning = autoExpandReasoning,
                        onToggleReasoning = onToggleReasoning,
                        onExpandReasoningContent = onExpandReasoningContent,
                        onExpandReasoningStep = onExpandReasoningStep,
                        onCopyText = onCopyText,
                        onLinkClick = onLinkClick
                    )
                }
            }

            if (message.markdownContent.isNotBlank()) {
                NativeMarkdownView(
                    markdown = message.markdownContent,
                    fontScale = fontScale,
                    onCopyCode = onCopyText,
                    onLinkClick = onLinkClick
                )
            }
        }

        // Artifact / Diff Summary Cards
        for (i in message.artifacts.indices) {
            val artifact = message.artifacts[i]
            key(artifact.id) {
                ArtifactSummaryCard(
                    artifact = artifact,
                    onOpen = { onArtifactClick(artifact) }
                )
            }
        }

        // Native Message Action Row
        val isThinkingStub = remember(copyableMarkdown) {
            copyableMarkdown.trim().matches(THINKING_STUB_REGEX)
        }
        if (!message.isStreaming && copyableMarkdown.isNotBlank() && !isThinkingStub) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        onCopyText(copyableMarkdown)
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
                    onClick = { onShareText(copyableMarkdown) },
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
fun CollapsibleReasoningTree(
    block: ReasoningBlock,
    headerText: String,
    expanded: Boolean,
    expandedIds: Map<String, Boolean>,
    autoExpand: Boolean,
    fontScale: Float,
    onToggleBlock: () -> Unit,
    onToggleNode: (nodeId: String, defaultExpanded: Boolean) -> Unit,
    onRequestBlockContent: () -> Unit = {},
    onRequestStepExpand: (sectionId: String, stepId: String, stepTitle: String) -> Unit = { _, _, _ -> },
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit
) {
    val chevronRotationState = animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "ReasoningTreeChevron"
    )
    val hasStructuredPayload = block.sections.isNotEmpty() || block.content.isNotBlank()
    var awaitingContent by remember(block.id) { mutableStateOf(false) }
    var requestedRemote by remember(block.id) { mutableStateOf(false) }

    if (expanded && !hasStructuredPayload) {
        LaunchedEffect(block.id, hasStructuredPayload) {
            awaitingContent = true
            if (!requestedRemote) {
                requestedRemote = true
                onRequestBlockContent()
            }
            delay(1800L)
            awaitingContent = false
        }
    } else {
        if (awaitingContent) awaitingContent = false
        if (!expanded && requestedRemote) requestedRemote = false
    }

    val colorScheme = MaterialTheme.colorScheme
    val guideColor = colorScheme.outlineVariant.copy(alpha = 0.45f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Level 1: Borderless muted text toggle + chevron (› collapsed, ⌄ expanded)
        Row(
            modifier = Modifier
                .clip(REASONING_ROW_SHAPE)
                .clickable(onClick = onToggleBlock)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp * fontScale
                ),
                color = if (expanded) {
                    colorScheme.onSurface
                } else {
                    colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse work summary" else "Expand work summary",
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotationState.value }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = REASONING_ENTER_TRANSITION,
            exit = REASONING_EXIT_TRANSITION
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val x = 6.dp.toPx()
                        val topY = 2.dp.toPx()
                        val bottomY = (size.height - 4.dp.toPx()).coerceAtLeast(topY)
                        drawLine(
                            color = guideColor,
                            start = Offset(x, topY),
                            end = Offset(x, bottomY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(start = 16.dp, top = 2.dp, bottom = 4.dp)
            ) {
                when {
                    block.sections.isNotEmpty() -> {
                        for (index in block.sections.indices) {
                            val section = block.sections[index]
                            key(section.id) {
                                ReasoningSectionNode(
                                    section = section,
                                    expandedIds = expandedIds,
                                    autoExpand = autoExpand,
                                    fontScale = fontScale,
                                    onToggleNode = onToggleNode,
                                    onRequestStepExpand = onRequestStepExpand,
                                    onCopyCode = onCopyCode,
                                    onLinkClick = onLinkClick,
                                    modifier = if (index > 0) Modifier.padding(top = 2.dp) else Modifier
                                )
                            }
                        }
                    }
                    block.content.isNotBlank() -> {
                        NativeMarkdownView(
                            markdown = block.content,
                            fontScale = fontScale * 0.90f,
                            onCopyCode = onCopyCode,
                            onLinkClick = onLinkClick,
                            enableSelection = false
                        )
                    }
                    awaitingContent -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 1.75.dp,
                                color = colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Loading steps…",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = if (block.durationLabel.isNotBlank()) {
                                "Worked for ${block.durationLabel}."
                            } else {
                                "No step trace available for this section."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningSectionNode(
    section: ReasoningSection,
    expandedIds: Map<String, Boolean>,
    autoExpand: Boolean,
    fontScale: Float,
    onToggleNode: (nodeId: String, defaultExpanded: Boolean) -> Unit,
    onRequestStepExpand: (sectionId: String, stepId: String, stepTitle: String) -> Unit,
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (section.title.isBlank()) {
        Column(modifier = modifier.fillMaxWidth()) {
            for (index in section.steps.indices) {
                val step = section.steps[index]
                key(step.id) {
                    ReasoningStepNode(
                        sectionId = section.id,
                        step = step,
                        expandedIds = expandedIds,
                        fontScale = fontScale,
                        onToggleNode = onToggleNode,
                        onRequestStepExpand = onRequestStepExpand,
                        onCopyCode = onCopyCode,
                        onLinkClick = onLinkClick,
                        modifier = if (index > 0) Modifier.padding(top = 2.dp) else Modifier
                    )
                }
            }
        }
        return
    }

    val defaultOpen = autoExpand || section.defaultExpanded
    val secExpanded = expandedIds[section.id] ?: defaultOpen
    val secChevronState = animateFloatAsState(
        targetValue = if (secExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "SectionChevron"
    )
    var awaitingSteps by remember(section.id) { mutableStateOf(false) }
    var requestedSteps by remember(section.id) { mutableStateOf(false) }

    if (secExpanded && section.steps.isEmpty()) {
        LaunchedEffect(section.id) {
            awaitingSteps = true
            if (!requestedSteps) {
                requestedSteps = true
                onRequestStepExpand(section.id, "", "")
            }
            delay(1500L)
            awaitingSteps = false
        }
    } else {
        if (awaitingSteps) awaitingSteps = false
        if (!secExpanded && requestedSteps) requestedSteps = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Level 2: Borderless progress group header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(REASONING_ROW_SHAPE)
                .clickable {
                    if (!secExpanded && section.steps.isEmpty()) {
                        requestedSteps = true
                        onRequestStepExpand(section.id, "", "")
                    }
                    onToggleNode(section.id, defaultOpen)
                }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp * fontScale
                ),
                color = if (secExpanded) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = if (secExpanded) "Collapse section" else "Expand section",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer { rotationZ = secChevronState.value }
            )
        }

        AnimatedVisibility(
            visible = secExpanded,
            enter = REASONING_ENTER_TRANSITION,
            exit = REASONING_EXIT_TRANSITION
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
            ) {
                if (section.steps.isEmpty()) {
                    Text(
                        text = if (awaitingSteps) "Loading steps…" else "No nested steps logged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                } else {
                    for (index in section.steps.indices) {
                        val step = section.steps[index]
                        key(step.id) {
                            ReasoningStepNode(
                                sectionId = section.id,
                                step = step,
                                expandedIds = expandedIds,
                                fontScale = fontScale,
                                onToggleNode = onToggleNode,
                                onRequestStepExpand = onRequestStepExpand,
                                onCopyCode = onCopyCode,
                                onLinkClick = onLinkClick,
                                modifier = if (index > 0) Modifier.padding(top = 2.dp) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningStepNode(
    sectionId: String,
    step: ReasoningStepItem,
    expandedIds: Map<String, Boolean>,
    fontScale: Float,
    onToggleNode: (nodeId: String, defaultExpanded: Boolean) -> Unit,
    onRequestStepExpand: (sectionId: String, stepId: String, stepTitle: String) -> Unit,
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val canExpand = step.isExpandable || step.detail.isNotBlank()
    val stepExpanded = expandedIds[step.id] ?: false
    val stepChevronState = animateFloatAsState(
        targetValue = if (stepExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "StepChevron"
    )
    var awaitingStepDetail by remember(step.id) { mutableStateOf(false) }

    if (stepExpanded && step.detail.isBlank() && step.isExpandable) {
        LaunchedEffect(step.id) {
            awaitingStepDetail = true
            delay(1500L)
            awaitingStepDetail = false
        }
    } else if (awaitingStepDetail) {
        awaitingStepDetail = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val rowModifier = Modifier
            .fillMaxWidth()
            .clip(STEP_ROW_SHAPE)
            .let { mod ->
                if (canExpand) {
                    mod.clickable {
                        if (!stepExpanded && step.detail.isBlank()) {
                            onRequestStepExpand(sectionId, step.id, step.title)
                        }
                        onToggleNode(step.id, false)
                    }
                } else {
                    mod
                }
            }
            .padding(vertical = 4.dp, horizontal = 2.dp)

        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step.kind == ReasoningStepKind.FILE) {
                if (step.actionVerb.isNotBlank()) {
                    Text(
                        text = step.actionVerb,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp * fontScale
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (step.badgeText.isNotBlank()) {
                    Surface(
                        shape = BADGE_SHAPE,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = step.badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp * fontScale,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = formatReasoningFileDisplayName(step.fileName, step.title),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp * fontScale
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (step.lineRange.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = step.lineRange,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp * fontScale
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            } else {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp * fontScale
                    ),
                    color = if (stepExpanded) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            if (canExpand) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = if (stepExpanded) "Collapse step" else "Expand step",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = stepChevronState.value }
                )
            }
        }

        if (canExpand) {
            AnimatedVisibility(
                visible = stepExpanded,
                enter = REASONING_ENTER_TRANSITION,
                exit = REASONING_EXIT_TRANSITION
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 2.dp, bottom = 6.dp)
                        .clip(REASONING_ROW_SHAPE)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(10.dp)
                ) {
                    when {
                        step.detail.isNotBlank() -> {
                            NativeMarkdownView(
                                markdown = step.detail,
                                fontScale = fontScale * 0.88f,
                                onCopyCode = onCopyCode,
                                onLinkClick = onLinkClick,
                                enableSelection = false
                            )
                        }
                        awaitingStepDetail -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 1.75.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Fetching details…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "No additional step output.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val diffPalette = remember(isDark) { diffColors(isDark) }

    Surface(
        shape = ARTIFACT_CARD_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ARTIFACT_CARD_SHAPE)
            .clickable(onClick = onOpen)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = ARTIFACT_CARD_SHAPE
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
                            color = diffPalette.addedAccent
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "-${artifact.removedLines}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = diffPalette.removedAccent
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
                shape = PILL_SHAPE,
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
    val alphaState = transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StreamingAlpha"
    )
    Surface(
        shape = PILL_SHAPE,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.graphicsLayer { alpha = alphaState.value }
    ) {
        Text(
            text = "Generating…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
