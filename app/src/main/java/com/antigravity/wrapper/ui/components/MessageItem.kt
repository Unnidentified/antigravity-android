package com.antigravity.wrapper.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.antigravity.wrapper.ui.markdown.MarkdownBlock
import com.antigravity.wrapper.ui.markdown.MarkdownBlockChunk
import com.antigravity.wrapper.ui.markdown.MarkdownParser
import com.antigravity.wrapper.ui.markdown.MarkdownRenderStyles
import com.antigravity.wrapper.ui.markdown.NativeBlockQuoteSliceView
import com.antigravity.wrapper.ui.markdown.NativeMarkdownChunkView
import com.antigravity.wrapper.ui.markdown.NativeMarkdownView
import com.antigravity.wrapper.ui.markdown.NativeStandaloneCodeBlock
import com.antigravity.wrapper.ui.markdown.NativeStandaloneMathBlock
import com.antigravity.wrapper.ui.markdown.NativeStandaloneTableBlock
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

@Immutable
sealed interface ChatFeedItem {
    val key: String
    val contentType: String
    val messageId: String
    val isTurnStart: Boolean
    val isTurnEnd: Boolean

    @Immutable
    data class UserBubble(
        override val key: String,
        override val messageId: String,
        val markdownContent: String,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "user_bubble"
    }

    @Immutable
    data class StreamingBadge(
        override val key: String,
        override val messageId: String,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "streaming_badge"
    }

    @Immutable
    data class ReasoningTree(
        override val key: String,
        override val messageId: String,
        val block: ReasoningBlock,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "reasoning_header"
    }

    @Immutable
    data class ProseChunk(
        override val key: String,
        override val messageId: String,
        val chunk: MarkdownBlockChunk,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "assistant_prose"
    }

    @Immutable
    data class BlockQuoteSlice(
        override val key: String,
        override val messageId: String,
        val chunk: MarkdownBlockChunk,
        val isQuoteStart: Boolean,
        val isQuoteEnd: Boolean,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "assistant_blockquote_slice"
    }

    @Immutable
    data class CodeBlockItem(
        override val key: String,
        override val messageId: String,
        val language: String,
        val code: String,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "assistant_code_block"
    }

    @Immutable
    data class MathBlockItem(
        override val key: String,
        override val messageId: String,
        val latex: String,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "assistant_math_block"
    }

    @Immutable
    data class TableBlockItem(
        override val key: String,
        override val messageId: String,
        val table: MarkdownBlock.TableBlock,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "assistant_table"
    }

    @Immutable
    data class ArtifactItem(
        override val key: String,
        override val messageId: String,
        val artifact: ArtifactCard,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "artifact_card"
    }

    @Immutable
    data class AssistantActions(
        override val key: String,
        override val messageId: String,
        val copyableMarkdown: String,
        override val isTurnStart: Boolean,
        override val isTurnEnd: Boolean
    ) : ChatFeedItem {
        override val contentType: String = "assistant_actions"
    }
}

private fun emitMarkdownFeedItems(
    msgId: String,
    segKey: String,
    markdown: String,
    seenKeys: HashSet<String>,
    pending: MutableList<ChatFeedItem>
) {
    val parsed = MarkdownParser.parse(markdown)
    if (parsed.isEmpty()) return

    fun uniqueKey(base: String): String {
        if (seenKeys.add(base)) return base
        var idx = 1
        while (!seenKeys.add("$base#$idx")) {
            idx++
        }
        return "$base#$idx"
    }

    val proseBuffer = mutableListOf<MarkdownBlock>()
    var chunkIndex = 0

    fun flushProse() {
        if (proseBuffer.isEmpty()) return
        val chunk = MarkdownBlockChunk(proseBuffer.toList())
        proseBuffer.clear()
        pending.add(
            ChatFeedItem.ProseChunk(
                key = uniqueKey("pr:$msgId:$segKey:${chunkIndex++}"),
                messageId = msgId,
                chunk = chunk,
                isTurnStart = false,
                isTurnEnd = false
            )
        )
    }

    for (bIdx in parsed.indices) {
        when (val block = parsed[bIdx]) {
            is MarkdownBlock.CodeBlock -> {
                flushProse()
                pending.add(
                    ChatFeedItem.CodeBlockItem(
                        key = uniqueKey("cb:$msgId:$segKey:$bIdx"),
                        messageId = msgId,
                        language = block.language,
                        code = block.code,
                        isTurnStart = false,
                        isTurnEnd = false
                    )
                )
            }
            is MarkdownBlock.MathBlock -> {
                flushProse()
                pending.add(
                    ChatFeedItem.MathBlockItem(
                        key = uniqueKey("mb:$msgId:$segKey:$bIdx"),
                        messageId = msgId,
                        latex = block.latex,
                        isTurnStart = false,
                        isTurnEnd = false
                    )
                )
            }
            is MarkdownBlock.TableBlock -> {
                flushProse()
                pending.add(
                    ChatFeedItem.TableBlockItem(
                        key = uniqueKey("tb:$msgId:$segKey:$bIdx"),
                        messageId = msgId,
                        table = block,
                        isTurnStart = false,
                        isTurnEnd = false
                    )
                )
            }
            is MarkdownBlock.BlockQuote -> {
                val shouldSplitQuote = block.blocks.size > 2 ||
                    block.blocks.count { it is MarkdownBlock.Paragraph } > 1 ||
                    block.blocks.any {
                        it is MarkdownBlock.BulletList ||
                            it is MarkdownBlock.OrderedList ||
                            it is MarkdownBlock.CodeBlock ||
                            it is MarkdownBlock.TableBlock ||
                            it is MarkdownBlock.BlockQuote
                    }
                if (shouldSplitQuote && block.blocks.isNotEmpty()) {
                    flushProse()
                    val quoteSlices = ArrayList<MarkdownBlock>(block.blocks.size)
                    for (inner in block.blocks) {
                        when {
                            inner is MarkdownBlock.BulletList && inner.items.size > 3 -> {
                                inner.items.chunked(3).forEach { part ->
                                    quoteSlices.add(MarkdownBlock.BulletList(part))
                                }
                            }
                            inner is MarkdownBlock.OrderedList && inner.items.size > 3 -> {
                                inner.items.chunked(3).forEach { part ->
                                    quoteSlices.add(MarkdownBlock.OrderedList(part))
                                }
                            }
                            else -> quoteSlices.add(inner)
                        }
                    }
                    for (qIdx in quoteSlices.indices) {
                        pending.add(
                            ChatFeedItem.BlockQuoteSlice(
                                key = uniqueKey("bq:$msgId:$segKey:$bIdx:$qIdx"),
                                messageId = msgId,
                                chunk = MarkdownBlockChunk(listOf(quoteSlices[qIdx])),
                                isQuoteStart = qIdx == 0,
                                isQuoteEnd = qIdx == quoteSlices.lastIndex,
                                isTurnStart = false,
                                isTurnEnd = false
                            )
                        )
                    }
                } else {
                    proseBuffer.add(block)
                    flushProse()
                }
            }
            else -> {
                proseBuffer.add(block)
                // Split long consecutive prose/list sections into compact chunks so no single
                // LazyColumn item spans multiple screen heights.
                if (proseBuffer.size >= 3 ||
                    block is MarkdownBlock.BulletList ||
                    block is MarkdownBlock.OrderedList
                ) {
                    flushProse()
                }
            }
        }
    }
    flushProse()
}

internal fun flattenChatMessages(messages: List<ChatMessage>): List<ChatFeedItem> {
    if (messages.isEmpty()) return emptyList()
    val seenKeys = HashSet<String>(messages.size * 4)
    val result = ArrayList<ChatFeedItem>(messages.size * 4)

    fun uniqueKey(base: String): String {
        if (seenKeys.add(base)) return base
        var idx = 1
        while (!seenKeys.add("$base#$idx")) {
            idx++
        }
        return "$base#$idx"
    }

    for (mIdx in messages.indices) {
        val msg = messages[mIdx]
        if (msg.role == MessageRole.USER) {
            result.add(
                ChatFeedItem.UserBubble(
                    key = uniqueKey("u:${msg.id}"),
                    messageId = msg.id,
                    markdownContent = msg.markdownContent,
                    isTurnStart = true,
                    isTurnEnd = true
                )
            )
            continue
        }

        val turnItems = ArrayList<ChatFeedItem>(8)
        if (msg.isStreaming) {
            turnItems.add(
                ChatFeedItem.StreamingBadge(
                    key = uniqueKey("st:${msg.id}"),
                    messageId = msg.id,
                    isTurnStart = false,
                    isTurnEnd = false
                )
            )
        }

        if (msg.segments.isNotEmpty()) {
            for (sIdx in msg.segments.indices) {
                val segment = msg.segments[sIdx]
                when (segment.kind) {
                    AssistantSegmentKind.REASONING -> {
                        val block = segment.reasoningBlock ?: continue
                        turnItems.add(
                            ChatFeedItem.ReasoningTree(
                                key = uniqueKey("rs:${msg.id}:${segment.id}"),
                                messageId = msg.id,
                                block = block,
                                isTurnStart = false,
                                isTurnEnd = false
                            )
                        )
                    }
                    AssistantSegmentKind.MARKDOWN -> {
                        if (segment.markdown.isNotBlank()) {
                            emitMarkdownFeedItems(
                                msgId = msg.id,
                                segKey = segment.id,
                                markdown = segment.markdown,
                                seenKeys = seenKeys,
                                pending = turnItems
                            )
                        }
                    }
                }
            }
        } else {
            for (rIdx in msg.reasoningBlocks.indices) {
                val block = msg.reasoningBlocks[rIdx]
                turnItems.add(
                    ChatFeedItem.ReasoningTree(
                        key = uniqueKey("rb:${msg.id}:${block.id}"),
                        messageId = msg.id,
                        block = block,
                        isTurnStart = false,
                        isTurnEnd = false
                    )
                )
            }
            if (msg.markdownContent.isNotBlank()) {
                emitMarkdownFeedItems(
                    msgId = msg.id,
                    segKey = "body",
                    markdown = msg.markdownContent,
                    seenKeys = seenKeys,
                    pending = turnItems
                )
            }
        }

        for (aIdx in msg.artifacts.indices) {
            val artifact = msg.artifacts[aIdx]
            turnItems.add(
                ChatFeedItem.ArtifactItem(
                    key = uniqueKey("art:${msg.id}:${artifact.id}:$aIdx"),
                    messageId = msg.id,
                    artifact = artifact,
                    isTurnStart = false,
                    isTurnEnd = false
                )
            )
        }

        val copyableMarkdown = if (msg.markdownContent.isNotBlank()) {
            msg.markdownContent
        } else {
            msg.segments
                .asSequence()
                .filter { it.kind == AssistantSegmentKind.MARKDOWN && it.markdown.isNotBlank() }
                .joinToString("\n\n") { it.markdown }
        }
        val isThinkingStub = copyableMarkdown.trim().matches(THINKING_STUB_REGEX)
        if (!msg.isStreaming && copyableMarkdown.isNotBlank() && !isThinkingStub) {
            turnItems.add(
                ChatFeedItem.AssistantActions(
                    key = uniqueKey("act:${msg.id}"),
                    messageId = msg.id,
                    copyableMarkdown = copyableMarkdown,
                    isTurnStart = false,
                    isTurnEnd = false
                )
            )
        }

        if (turnItems.isNotEmpty()) {
            val lastIdx = turnItems.lastIndex
            for (tIdx in turnItems.indices) {
                val start = tIdx == 0
                val end = tIdx == lastIdx
                val finalized = when (val item = turnItems[tIdx]) {
                    is ChatFeedItem.UserBubble -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.StreamingBadge -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.ReasoningTree -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.ProseChunk -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.BlockQuoteSlice -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.CodeBlockItem -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.MathBlockItem -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.TableBlockItem -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.ArtifactItem -> item.copy(isTurnStart = start, isTurnEnd = end)
                    is ChatFeedItem.AssistantActions -> item.copy(isTurnStart = start, isTurnEnd = end)
                }
                result.add(finalized)
            }
        }
    }
    return result
}

@Composable
fun ChatFeedRowItem(
    item: ChatFeedItem,
    fontScale: Float,
    markdownStyles: MarkdownRenderStyles,
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
    if (item is ChatFeedItem.UserBubble) {
        UserMessageBubbleContent(
            markdownContent = item.markdownContent,
            fontScale = fontScale,
            onCopyText = onCopyText,
            modifier = modifier.padding(top = 6.dp, bottom = 6.dp)
        )
        return
    }

    val topPad = if (item.isTurnStart) 10.dp else 4.dp
    val bottomPad = if (item.isTurnEnd) 8.dp else 4.dp
    val blockModifier = modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = topPad, bottom = bottomPad)

    when (item) {
        is ChatFeedItem.StreamingBadge -> {
            Row(modifier = blockModifier) {
                StreamingPulseBadge()
            }
        }
        is ChatFeedItem.ReasoningTree -> {
            androidx.compose.foundation.layout.Box(modifier = blockModifier) {
                ReasoningBlockRow(
                    block = item.block,
                    fontScale = fontScale,
                    expandedReasoningIds = expandedReasoningState.ids,
                    autoExpandReasoning = autoExpandReasoning,
                    onToggleReasoning = onToggleReasoning,
                    onExpandReasoningContent = onExpandReasoningContent,
                    onExpandReasoningStep = onExpandReasoningStep,
                    onCopyText = onCopyText,
                    onLinkClick = onLinkClick
                )
            }
        }
        is ChatFeedItem.ProseChunk -> {
            NativeMarkdownChunkView(
                chunk = item.chunk,
                fontScale = fontScale,
                styles = markdownStyles,
                onCopyCode = onCopyText,
                onLinkClick = onLinkClick,
                modifier = blockModifier
            )
        }
        is ChatFeedItem.BlockQuoteSlice -> {
            val quoteTop = if (item.isQuoteStart) topPad else 0.dp
            val quoteBottom = if (item.isQuoteEnd) bottomPad else 0.dp
            NativeBlockQuoteSliceView(
                chunk = item.chunk,
                isQuoteStart = item.isQuoteStart,
                isQuoteEnd = item.isQuoteEnd,
                fontScale = fontScale,
                styles = markdownStyles,
                onCopyCode = onCopyText,
                onLinkClick = onLinkClick,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = quoteTop, bottom = quoteBottom)
            )
        }
        is ChatFeedItem.CodeBlockItem -> {
            NativeStandaloneCodeBlock(
                language = item.language,
                code = item.code,
                fontScale = fontScale,
                styles = markdownStyles,
                onCopyCode = onCopyText,
                modifier = blockModifier
            )
        }
        is ChatFeedItem.MathBlockItem -> {
            NativeStandaloneMathBlock(
                latex = item.latex,
                fontScale = fontScale,
                onCopyCode = onCopyText,
                modifier = blockModifier
            )
        }
        is ChatFeedItem.TableBlockItem -> {
            NativeStandaloneTableBlock(
                table = item.table,
                fontScale = fontScale,
                styles = markdownStyles,
                onLinkClick = onLinkClick,
                modifier = blockModifier
            )
        }
        is ChatFeedItem.ArtifactItem -> {
            val handleArtifactOpen = remember(item.artifact, onArtifactClick) {
                { onArtifactClick(item.artifact) }
            }
            androidx.compose.foundation.layout.Box(modifier = blockModifier) {
                ArtifactSummaryCard(
                    artifact = item.artifact,
                    onOpen = handleArtifactOpen
                )
            }
        }
        is ChatFeedItem.AssistantActions -> {
            AssistantMessageActionRow(
                messageId = item.messageId,
                copyableMarkdown = item.copyableMarkdown,
                onCopyText = onCopyText,
                onShareText = onShareText,
                modifier = blockModifier
            )
        }
        is ChatFeedItem.UserBubble -> Unit
    }
}

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
    UserMessageBubbleContent(
        markdownContent = message.markdownContent,
        fontScale = fontScale,
        onCopyText = onCopyText,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun UserMessageBubbleContent(
    markdownContent: String,
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
    val handleClick = remember(markdownContent, onCopyText) {
        { onCopyText(markdownContent) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            onClick = handleClick,
            shape = USER_BUBBLE_SHAPE,
            color = colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(
                text = markdownContent,
                style = textStyle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageActionRow(
    messageId: String,
    copyableMarkdown: String,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var copied by remember(messageId) { mutableStateOf(false) }
    if (copied) {
        LaunchedEffect(messageId) {
            delay(1500L)
            copied = false
        }
    }
    Row(
        modifier = modifier,
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

        if (expanded) {
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

        if (secExpanded) {
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

        if (canExpand && stepExpanded) {
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
