package com.antigravity.wrapper.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val CODE_CARD_SHAPE = RoundedCornerShape(14.dp)
private val COPY_PILL_SHAPE = RoundedCornerShape(8.dp)
private val MATH_CARD_SHAPE = RoundedCornerShape(16.dp)
private val BLOCKQUOTE_SHAPE = RoundedCornerShape(12.dp)
private val TABLE_SHAPE = RoundedCornerShape(14.dp)

private data class HighlightCacheKey(
    val code: String,
    val isDark: Boolean
)

private val highlightCache = object : LinkedHashMap<HighlightCacheKey, AnnotatedString>(128, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HighlightCacheKey, AnnotatedString>?): Boolean {
        return size > 128
    }
}

private fun cachedHighlight(code: String, isDark: Boolean): AnnotatedString {
    val key = HighlightCacheKey(code, isDark)
    synchronized(highlightCache) {
        highlightCache[key]?.let { return it }
    }
    val highlighted = CodeSyntaxHighlighter.highlight(code, isDark)
    if (code.length <= 8_000) {
        synchronized(highlightCache) {
            highlightCache[key] = highlighted
        }
    }
    return highlighted
}

private val latexCache = object : LinkedHashMap<String, LatexNode>(128, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LatexNode>?): Boolean {
        return size > 128
    }
}

private fun cachedParseLatex(latex: String): LatexNode {
    synchronized(latexCache) {
        latexCache[latex]?.let { return it }
    }
    val parsed = LatexParser.parse(latex)
    if (latex.length <= 2_000) {
        synchronized(latexCache) {
            latexCache[latex] = parsed
        }
    }
    return parsed
}

private data class AnnotatedCacheKey(
    val inlines: List<MarkdownInline>,
    val baseSize: TextUnit,
    val primaryColor: Color,
    val codeBg: Color,
    val codeColor: Color,
    val mathColor: Color
)

private val annotatedCache = object : LinkedHashMap<AnnotatedCacheKey, AnnotatedString>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AnnotatedCacheKey, AnnotatedString>?): Boolean {
        return size > 256
    }
}

private class TextLayoutHolder {
    var result: TextLayoutResult? = null
}

@Immutable
data class MarkdownRenderStyles(
    val isDark: Boolean,
    val primaryColor: Color,
    val codeBg: Color,
    val codeColor: Color,
    val mathColor: Color,
    val outlineVariant: Color,
    val blockquoteBg: Color,
    val paragraphStyle: TextStyle,
    val listStyle: TextStyle,
    val listMarkerStyle: TextStyle,
    val orderedMarkerStyle: TextStyle,
    val h1Style: TextStyle,
    val h2Style: TextStyle,
    val h3Style: TextStyle,
    val h4Style: TextStyle,
    val codeTextStyle: TextStyle,
    val tableHeaderStyle: TextStyle,
    val tableCellStyle: TextStyle
)

@Immutable
data class MarkdownBlockChunk(
    val blocks: List<MarkdownBlock>
)

@Composable
fun rememberMarkdownRenderStyles(fontScale: Float = 1f): MarkdownRenderStyles {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val isDark = remember(colorScheme.background) { colorScheme.background.luminance() < 0.5f }
    return remember(colorScheme, typography, fontScale, isDark) {
        val codeBg = if (isDark) colorScheme.surfaceContainerHighest else colorScheme.surfaceContainerHigh
        val baseParagraph = typography.bodyLarge.copy(
            fontSize = 15.sp * fontScale,
            lineHeight = 23.sp * fontScale,
            color = colorScheme.onSurface
        )
        val baseList = typography.bodyLarge.copy(
            fontSize = 15.sp * fontScale,
            lineHeight = 22.5.sp * fontScale,
            color = colorScheme.onSurface
        )
        val headingBase = typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface
        )
        MarkdownRenderStyles(
            isDark = isDark,
            primaryColor = colorScheme.primary,
            codeBg = codeBg,
            codeColor = colorScheme.onSurface,
            mathColor = colorScheme.secondary,
            outlineVariant = colorScheme.outlineVariant,
            blockquoteBg = colorScheme.surfaceContainerLow,
            paragraphStyle = baseParagraph,
            listStyle = baseList,
            listMarkerStyle = baseList.copy(
                color = colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            orderedMarkerStyle = baseList.copy(
                color = colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            ),
            h1Style = headingBase.copy(fontSize = 22.sp * fontScale, lineHeight = (22.sp * 1.32f) * fontScale),
            h2Style = headingBase.copy(fontSize = 19.sp * fontScale, lineHeight = (19.sp * 1.32f) * fontScale),
            h3Style = headingBase.copy(fontSize = 17.sp * fontScale, lineHeight = (17.sp * 1.32f) * fontScale),
            h4Style = headingBase.copy(fontSize = 15.5.sp * fontScale, lineHeight = (15.5.sp * 1.32f) * fontScale),
            codeTextStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp * fontScale,
                lineHeight = 19.sp * fontScale,
                color = colorScheme.onSurface
            ),
            tableHeaderStyle = typography.labelLarge.copy(
                fontSize = 13.5.sp * fontScale,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            ),
            tableCellStyle = typography.bodyMedium.copy(
                fontSize = 13.5.sp * fontScale,
                color = colorScheme.onSurface
            )
        )
    }
}

@Composable
fun NativeMarkdownView(
    markdown: String,
    fontScale: Float = 1f,
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    enableSelection: Boolean = true
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val styles = rememberMarkdownRenderStyles(fontScale)
    NativeMarkdownChunkView(
        chunk = remember(blocks) { MarkdownBlockChunk(blocks) },
        fontScale = fontScale,
        styles = styles,
        onCopyCode = onCopyCode,
        onLinkClick = onLinkClick,
        modifier = modifier,
        enableSelection = enableSelection
    )
}

@Composable
fun NativeMarkdownChunkView(
    chunk: MarkdownBlockChunk,
    fontScale: Float = 1f,
    styles: MarkdownRenderStyles = rememberMarkdownRenderStyles(fontScale),
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    enableSelection: Boolean = true
) {
    val blocks = chunk.blocks
    val content: @Composable () -> Unit = {
        Column(
            modifier = if (enableSelection) Modifier.fillMaxWidth() else modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (i in blocks.indices) {
                RenderMarkdownBlock(
                    block = blocks[i],
                    fontScale = fontScale,
                    styles = styles,
                    onCopyCode = onCopyCode,
                    onLinkClick = onLinkClick
                )
            }
        }
    }

    if (enableSelection) {
        SelectionContainer(modifier = modifier.fillMaxWidth()) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun NativeBlockQuoteSliceView(
    chunk: MarkdownBlockChunk,
    isQuoteStart: Boolean,
    isQuoteEnd: Boolean,
    fontScale: Float = 1f,
    styles: MarkdownRenderStyles = rememberMarkdownRenderStyles(fontScale),
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    enableSelection: Boolean = true
) {
    val accentColor = styles.primaryColor
    val sliceShape = remember(isQuoteStart, isQuoteEnd) {
        when {
            isQuoteStart && isQuoteEnd -> BLOCKQUOTE_SHAPE
            isQuoteStart -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            isQuoteEnd -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
            else -> RectangleShape
        }
    }
    val innerTopPad = if (isQuoteStart) 10.dp else 3.dp
    val innerBottomPad = if (isQuoteEnd) 10.dp else 3.dp

    val sliceBody: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sliceShape)
                .background(styles.blockquoteBg)
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        topLeft = Offset.Zero,
                        size = Size(4.dp.toPx(), size.height)
                    )
                }
                .padding(start = 16.dp, end = 12.dp, top = innerTopPad, bottom = innerBottomPad),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val blocks = chunk.blocks
            for (i in blocks.indices) {
                RenderMarkdownBlock(
                    block = blocks[i],
                    fontScale = fontScale,
                    styles = styles,
                    onCopyCode = onCopyCode,
                    onLinkClick = onLinkClick
                )
            }
        }
    }

    if (enableSelection) {
        SelectionContainer(modifier = modifier.fillMaxWidth()) {
            sliceBody()
        }
    } else {
        Box(modifier = modifier.fillMaxWidth()) {
            sliceBody()
        }
    }
}

@Composable
fun NativeStandaloneCodeBlock(
    language: String,
    code: String,
    fontScale: Float = 1f,
    styles: MarkdownRenderStyles = rememberMarkdownRenderStyles(fontScale),
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        NativeCodeBlockCard(
            language = language,
            code = code,
            styles = styles,
            onCopy = { onCopyCode(code) }
        )
    }
}

@Composable
fun NativeStandaloneMathBlock(
    latex: String,
    fontScale: Float = 1f,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        NativeLatexDisplayCard(
            latex = latex,
            fontScale = fontScale,
            onCopy = { onCopyCode(latex) }
        )
    }
}

@Composable
fun NativeStandaloneTableBlock(
    table: MarkdownBlock.TableBlock,
    fontScale: Float = 1f,
    styles: MarkdownRenderStyles = rememberMarkdownRenderStyles(fontScale),
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        NativeMarkdownTable(
            headers = table.headers,
            rows = table.rows,
            styles = styles,
            onLinkClick = onLinkClick
        )
    }
}

@Composable
private fun RenderMarkdownBlock(
    block: MarkdownBlock,
    fontScale: Float,
    styles: MarkdownRenderStyles,
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit
) {
    when (block) {
        is MarkdownBlock.Heading -> {
            val style = when (block.level) {
                1 -> styles.h1Style
                2 -> styles.h2Style
                3 -> styles.h3Style
                else -> styles.h4Style
            }
            InlineRichText(
                inlines = block.inlines,
                style = style,
                styles = styles,
                onLinkClick = onLinkClick,
                modifier = if (block.level <= 2) Modifier.padding(top = 6.dp) else Modifier.padding(top = 2.dp)
            )
        }

        is MarkdownBlock.Paragraph -> {
            InlineRichText(
                inlines = block.inlines,
                style = styles.paragraphStyle,
                styles = styles,
                onLinkClick = onLinkClick
            )
        }

        is MarkdownBlock.CodeBlock -> {
            NativeCodeBlockCard(
                language = block.language,
                code = block.code,
                styles = styles,
                onCopy = { onCopyCode(block.code) }
            )
        }

        is MarkdownBlock.MathBlock -> {
            NativeLatexDisplayCard(
                latex = block.latex,
                fontScale = fontScale,
                onCopy = { onCopyCode(block.latex) }
            )
        }

        is MarkdownBlock.BlockQuote -> {
            val accentColor = styles.primaryColor
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BLOCKQUOTE_SHAPE)
                    .background(styles.blockquoteBg)
                    .drawBehind {
                        drawRect(
                            color = accentColor,
                            topLeft = Offset.Zero,
                            size = Size(4.dp.toPx(), size.height)
                        )
                    }
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in block.blocks.indices) {
                    RenderMarkdownBlock(
                        block = block.blocks[i],
                        fontScale = fontScale,
                        styles = styles,
                        onCopyCode = onCopyCode,
                        onLinkClick = onLinkClick
                    )
                }
            }
        }

        is MarkdownBlock.BulletList -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in block.items.indices) {
                    val entry = block.items[i]
                    val bulletGlyph = when {
                        entry.checked == true -> "☑"
                        entry.checked == false -> "☐"
                        entry.depth % 3 == 1 -> "◦"
                        entry.depth % 3 == 2 -> "▪"
                        else -> "•"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (entry.depth * 18).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = bulletGlyph,
                            style = styles.listMarkerStyle,
                            modifier = Modifier.padding(start = 6.dp, end = 10.dp)
                        )
                        InlineRichText(
                            inlines = entry.inlines,
                            style = styles.listStyle,
                            styles = styles,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MarkdownBlock.OrderedList -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in block.items.indices) {
                    val entry = block.items[i]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (entry.depth * 18).dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${entry.number}.",
                            style = styles.orderedMarkerStyle,
                            modifier = Modifier.padding(start = 4.dp, end = 10.dp)
                        )
                        InlineRichText(
                            inlines = entry.inlines,
                            style = styles.listStyle,
                            styles = styles,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MarkdownBlock.TableBlock -> {
            NativeMarkdownTable(
                headers = block.headers,
                rows = block.rows,
                styles = styles,
                onLinkClick = onLinkClick
            )
        }

        MarkdownBlock.HorizontalRule -> {
            HorizontalDivider(
                color = styles.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

private fun List<MarkdownInline>.containsInlineCode(): Boolean {
    for (i in indices) {
        when (val item = this[i]) {
            is MarkdownInline.InlineCode -> return true
            is MarkdownInline.Bold -> if (item.children.containsInlineCode()) return true
            is MarkdownInline.Italic -> if (item.children.containsInlineCode()) return true
            is MarkdownInline.Strikethrough -> if (item.children.containsInlineCode()) return true
            else -> Unit
        }
    }
    return false
}

private fun List<MarkdownInline>.containsLink(): Boolean {
    for (i in indices) {
        when (val item = this[i]) {
            is MarkdownInline.Link -> return true
            is MarkdownInline.Bold -> if (item.children.containsLink()) return true
            is MarkdownInline.Italic -> if (item.children.containsLink()) return true
            is MarkdownInline.Strikethrough -> if (item.children.containsLink()) return true
            else -> Unit
        }
    }
    return false
}

@Composable
private fun InlineRichText(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    styles: MarkdownRenderStyles,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (inlines.isEmpty()) return

    if (inlines.size == 1 && inlines[0] is MarkdownInline.Text) {
        Text(
            text = (inlines[0] as MarkdownInline.Text).value,
            style = style,
            modifier = modifier
        )
        return
    }

    val primaryColor = styles.primaryColor
    val codeBg = styles.codeBg
    val codeColor = styles.codeColor
    val mathColor = styles.mathColor
    val baseSize = if (style.fontSize.isSpecified) style.fontSize else 15.sp
    val hasLink = remember(inlines) { inlines.containsLink() }
    val hasInlineCode = remember(inlines) { inlines.containsInlineCode() }
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)

    val annotated = remember(
        inlines,
        primaryColor,
        codeBg,
        codeColor,
        mathColor,
        baseSize,
        hasLink
    ) {
        if (!hasLink) {
            val cacheKey = AnnotatedCacheKey(
                inlines = inlines,
                baseSize = baseSize,
                primaryColor = primaryColor,
                codeBg = codeBg,
                codeColor = codeColor,
                mathColor = mathColor
            )
            synchronized(annotatedCache) {
                annotatedCache[cacheKey]?.let { return@remember it }
            }
            val built = buildAnnotatedString {
                appendInlines(
                    inlines = inlines,
                    baseSize = baseSize,
                    primaryColor = primaryColor,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    mathColor = mathColor,
                    onLinkClick = { currentOnLinkClick(it) }
                )
            }
            if (built.length <= 8_000) {
                synchronized(annotatedCache) {
                    annotatedCache[cacheKey] = built
                }
            }
            built
        } else {
            buildAnnotatedString {
                appendInlines(
                    inlines = inlines,
                    baseSize = baseSize,
                    primaryColor = primaryColor,
                    codeBg = codeBg,
                    codeColor = codeColor,
                    mathColor = mathColor,
                    onLinkClick = { currentOnLinkClick(it) }
                )
            }
        }
    }

    if (!hasInlineCode) {
        Text(
            text = annotated,
            style = style,
            modifier = modifier
        )
        return
    }

    val layoutHolder = remember(annotated) { TextLayoutHolder() }
    val codeRanges = remember(annotated) {
        annotated.getStringAnnotations("inline_code", 0, annotated.length)
    }

    Text(
        text = annotated,
        style = style,
        onTextLayout = { layoutHolder.result = it },
        modifier = modifier.drawBehind {
            val layout = layoutHolder.result ?: return@drawBehind
            if (codeRanges.isEmpty()) return@drawBehind

            val radiusPx = 4.dp.toPx()
            val hPadPx = 2.5.dp.toPx()
            val baseSp = if (style.fontSize != TextUnit.Unspecified) style.fontSize else 15.sp
            val codeFontPx = (baseSp * 0.88f).toPx()

            for (r in codeRanges.indices) {
                val range = codeRanges[r]
                val startOffset = range.start.coerceIn(0, annotated.length)
                val endOffset = range.end.coerceIn(startOffset, annotated.length)
                if (startOffset >= endOffset) continue

                val firstLine = layout.getLineForOffset(startOffset)
                val lastLine = layout.getLineForOffset((endOffset - 1).coerceAtLeast(startOffset))
                for (line in firstLine..lastLine) {
                    val segStart = if (line == firstLine) startOffset else layout.getLineStart(line)
                    val segEnd = if (line == lastLine) endOffset else layout.getLineEnd(line, visibleEnd = true)
                    if (segStart >= segEnd) continue

                    var minLeft = Float.POSITIVE_INFINITY
                    var maxRight = Float.NEGATIVE_INFINITY
                    for (charIdx in segStart until segEnd) {
                        val charBox = layout.getBoundingBox(charIdx)
                        if (charBox.left < minLeft) minLeft = charBox.left
                        if (charBox.right > maxRight) maxRight = charBox.right
                    }
                    if (!minLeft.isFinite() || !maxRight.isFinite()) continue
                    val left = (minLeft - hPadPx).coerceAtLeast(0f)
                    val right = (maxRight + hPadPx).coerceAtMost(size.width)

                    // Derive pill top/bottom strictly from line baseline + code font size so every
                    // inline code box keeps uniform height regardless of glyph ascenders or wraps.
                    val baseline = layout.getLineBaseline(line)
                    val lineTop = layout.getLineTop(line)
                    val lineBottom = layout.getLineBottom(line)
                    val top = (baseline - codeFontPx * 0.94f).coerceAtLeast(lineTop + 0.5f).coerceAtLeast(0f)
                    val bottom = (baseline + codeFontPx * 0.28f).coerceAtMost(lineBottom - 0.5f).coerceAtMost(size.height)
                    if (right <= left || bottom <= top) continue

                    drawRoundRect(
                        color = codeBg,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                    )
                }
            }
        }
    )
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<MarkdownInline>,
    baseSize: TextUnit,
    primaryColor: Color,
    codeBg: Color,
    codeColor: Color,
    mathColor: Color,
    onLinkClick: (String) -> Unit
) {
    inlines.forEach { inline ->
        when (inline) {
            is MarkdownInline.Text -> append(inline.value)
            is MarkdownInline.Bold -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                appendInlines(inline.children, baseSize, primaryColor, codeBg, codeColor, mathColor, onLinkClick)
                pop()
            }
            is MarkdownInline.Italic -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlines(inline.children, baseSize, primaryColor, codeBg, codeColor, mathColor, onLinkClick)
                pop()
            }
            is MarkdownInline.Strikethrough -> {
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                appendInlines(inline.children, baseSize, primaryColor, codeBg, codeColor, mathColor, onLinkClick)
                pop()
            }
            is MarkdownInline.InlineCode -> {
                pushStringAnnotation(tag = "inline_code", annotation = inline.code)
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = baseSize * 0.88f,
                        fontWeight = FontWeight.Medium,
                        color = codeColor
                    )
                )
                append(inline.code)
                pop()
                pop()
            }
            is MarkdownInline.InlineMath -> {
                val parsed = cachedParseLatex(inline.latex)
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Serif,
                        color = mathColor
                    )
                )
                appendLatexInlineNode(parsed, baseSize)
                pop()
            }
            is MarkdownInline.Link -> {
                val linkStyle = TextLinkStyles(
                    style = SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                )
                pushLink(
                    LinkAnnotation.Clickable(
                        tag = "url",
                        styles = linkStyle,
                        linkInteractionListener = { onLinkClick(inline.url) }
                    )
                )
                append(inline.label)
                pop()
            }
        }
    }
}

private fun AnnotatedString.Builder.appendLatexInlineNode(
    node: LatexNode,
    baseSize: TextUnit
) {
    when (node) {
        is LatexNode.Row -> node.children.forEach { appendLatexInlineNode(it, baseSize) }
        is LatexNode.Text -> {
            if (node.italic || node.bold) {
                pushStyle(
                    SpanStyle(
                        fontStyle = if (node.italic) FontStyle.Italic else FontStyle.Normal,
                        fontWeight = if (node.bold) FontWeight.Bold else FontWeight.Normal
                    )
                )
                append(node.value)
                pop()
            } else {
                append(node.value)
            }
        }
        is LatexNode.Fraction -> {
            append("(")
            appendLatexInlineNode(node.numerator, baseSize * 0.92f)
            append(")/(")
            appendLatexInlineNode(node.denominator, baseSize * 0.92f)
            append(")")
        }
        is LatexNode.Sqrt -> {
            if (node.index != null) {
                pushStyle(SpanStyle(fontSize = baseSize * 0.68f, baselineShift = BaselineShift.Superscript))
                appendLatexInlineNode(node.index, baseSize * 0.68f)
                pop()
            }
            append("√(")
            appendLatexInlineNode(node.body, baseSize)
            append(")")
        }
        is LatexNode.Script -> {
            appendLatexInlineNode(node.base, baseSize)
            node.subscript?.let { sub ->
                pushStyle(SpanStyle(fontSize = baseSize * 0.72f, baselineShift = BaselineShift.Subscript))
                appendLatexInlineNode(sub, baseSize * 0.72f)
                pop()
            }
            node.superscript?.let { sup ->
                pushStyle(SpanStyle(fontSize = baseSize * 0.72f, baselineShift = BaselineShift.Superscript))
                appendLatexInlineNode(sup, baseSize * 0.72f)
                pop()
            }
        }
        is LatexNode.OverAccent -> {
            appendLatexInlineNode(node.body, baseSize)
            append(
                when (node.accent) {
                    "^" -> "\u0302"
                    "¯" -> "\u0304"
                    "→" -> "\u20D7"
                    "~" -> "\u0303"
                    "·" -> "\u0307"
                    else -> ""
                }
            )
        }
        is LatexNode.Stacked -> {
            appendLatexInlineNode(node.body, baseSize)
            node.above?.let { ab ->
                pushStyle(SpanStyle(fontSize = baseSize * 0.68f, baselineShift = BaselineShift.Superscript))
                appendLatexInlineNode(ab, baseSize * 0.68f)
                pop()
            }
            node.below?.let { bl ->
                pushStyle(SpanStyle(fontSize = baseSize * 0.68f, baselineShift = BaselineShift.Subscript))
                appendLatexInlineNode(bl, baseSize * 0.68f)
                pop()
            }
        }
        is LatexNode.Matrix -> {
            append(node.leftDelim)
            node.rows.forEachIndexed { rIdx, row ->
                if (rIdx > 0) append("; ")
                row.forEachIndexed { cIdx, cell ->
                    if (cIdx > 0) append(if (node.isAligned) " " else ", ")
                    appendLatexInlineNode(cell, baseSize)
                }
            }
            append(node.rightDelim)
        }
    }
}

@Composable
private fun NativeCodeBlockCard(
    language: String,
    code: String,
    styles: MarkdownRenderStyles,
    onCopy: () -> Unit
) {
    var copied by remember(language, code) { mutableStateOf(false) }
    if (copied) {
        LaunchedEffect(code) {
            delay(1500L)
            copied = false
        }
    }

    val highlighted = remember(code, styles.isDark) {
        cachedHighlight(code, styles.isDark)
    }

    Surface(
        shape = CODE_CARD_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = styles.outlineVariant.copy(alpha = 0.7f),
                shape = CODE_CARD_SHAPE
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Compact top-left Copy button header row (offsetting code body below so button never overlaps code)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = {
                        onCopy()
                        copied = true
                    },
                    shape = COPY_PILL_SHAPE,
                    color = if (copied) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = if (copied) "Copied code" else "Copy code",
                            tint = if (copied) styles.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (copied) styles.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (language.isNotBlank()) {
                    Text(
                        text = language.lowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = highlighted,
                    style = styles.codeTextStyle
                )
            }
        }
    }
}

@Composable
private fun NativeLatexDisplayCard(
    latex: String,
    fontScale: Float,
    onCopy: () -> Unit
) {
    val rootNode = remember(latex) { cachedParseLatex(latex) }
    var copied by remember(latex) { mutableStateOf(false) }
    if (copied) {
        LaunchedEffect(latex) {
            delay(1500L)
            copied = false
        }
    }

    Surface(
        shape = MATH_CARD_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = MATH_CARD_SHAPE
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 42.dp, top = 16.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                RenderLatexNode(
                    node = rootNode,
                    fontSize = 18.sp * fontScale,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = {
                    onCopy()
                    copied = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(30.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = "Copy LaTeX",
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun RenderLatexNode(
    node: LatexNode,
    fontSize: TextUnit,
    color: Color
) {
    when (node) {
        is LatexNode.Row -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                node.children.forEach { child ->
                    RenderLatexNode(child, fontSize, color)
                }
            }
        }

        is LatexNode.Text -> {
            Text(
                text = node.value,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = fontSize,
                    fontStyle = if (node.italic) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (node.bold) FontWeight.Bold else FontWeight.Normal,
                    color = color
                )
            )
        }

        is LatexNode.Fraction -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(IntrinsicSize.Max)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    RenderLatexNode(node.numerator, fontSize * 0.85f, color)
                }
                HorizontalDivider(
                    thickness = 1.2.dp,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    RenderLatexNode(node.denominator, fontSize * 0.85f, color)
                }
            }
        }

        is LatexNode.Sqrt -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                node.index?.let { idx ->
                    Box(modifier = Modifier.padding(bottom = 10.dp)) {
                        RenderLatexNode(idx, fontSize * 0.62f, color)
                    }
                }
                Text(
                    text = "√",
                    style = TextStyle(
                        fontFamily = FontFamily.Serif,
                        fontSize = fontSize * 1.15f,
                        color = color
                    )
                )
                Column(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .width(IntrinsicSize.Max)
                ) {
                    HorizontalDivider(
                        thickness = 1.2.dp,
                        color = color,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)) {
                        RenderLatexNode(node.body, fontSize * 0.95f, color)
                    }
                }
            }
        }

        is LatexNode.Script -> {
            val isLargeOperator = node.base is LatexNode.Text &&
                node.base.value.trim() in setOf("∑", "∏", "∫", "∬", "∮", "lim")
            if (isLargeOperator) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 3.dp)
                ) {
                    node.superscript?.let {
                        RenderLatexNode(it, fontSize * 0.65f, color)
                    }
                    RenderLatexNode(node.base, fontSize * 1.3f, color)
                    node.subscript?.let {
                        RenderLatexNode(it, fontSize * 0.65f, color)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RenderLatexNode(node.base, fontSize, color)
                    val scriptGap = (fontSize.value * 0.38f).dp
                    when {
                        node.superscript != null && node.subscript != null -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(start = 1.dp)
                            ) {
                                Box(modifier = Modifier.offset(y = (-1).dp)) {
                                    RenderLatexNode(node.superscript, fontSize * 0.66f, color)
                                }
                                Box(modifier = Modifier.offset(y = 1.dp)) {
                                    RenderLatexNode(node.subscript, fontSize * 0.66f, color)
                                }
                            }
                        }
                        node.superscript != null -> {
                            Box(
                                modifier = Modifier
                                    .padding(start = 1.dp, bottom = scriptGap)
                            ) {
                                RenderLatexNode(node.superscript, fontSize * 0.68f, color)
                            }
                        }
                        node.subscript != null -> {
                            Box(
                                modifier = Modifier
                                    .padding(start = 1.dp, top = scriptGap)
                            ) {
                                RenderLatexNode(node.subscript, fontSize * 0.68f, color)
                            }
                        }
                    }
                }
            }
        }

        is LatexNode.OverAccent -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = node.accent,
                    style = TextStyle(fontSize = fontSize * 0.65f, color = color, lineHeight = fontSize * 0.65f)
                )
                RenderLatexNode(node.body, fontSize, color)
            }
        }

        is LatexNode.Stacked -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                node.above?.let {
                    RenderLatexNode(it, fontSize * 0.68f, color)
                }
                RenderLatexNode(node.body, fontSize, color)
                node.below?.let {
                    RenderLatexNode(it, fontSize * 0.68f, color)
                }
            }
        }

        is LatexNode.Matrix -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.leftDelim.isNotEmpty()) {
                    Text(
                        text = node.leftDelim,
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = fontSize * (1.25f + (node.rows.size - 1) * 0.35f),
                            fontWeight = FontWeight.Light,
                            color = color
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Column(
                    horizontalAlignment = if (node.isAligned) Alignment.Start else Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (node.isAligned) 8.dp else 6.dp)
                ) {
                    node.rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (node.isAligned) 4.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { cell ->
                                RenderLatexNode(cell, fontSize * 0.92f, color)
                            }
                        }
                    }
                }
                if (node.rightDelim.isNotEmpty()) {
                    Text(
                        text = node.rightDelim,
                        style = TextStyle(
                            fontFamily = FontFamily.Serif,
                            fontSize = fontSize * (1.25f + (node.rows.size - 1) * 0.35f),
                            fontWeight = FontWeight.Light,
                            color = color
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeMarkdownTable(
    headers: List<List<MarkdownInline>>,
    rows: List<List<List<MarkdownInline>>>,
    styles: MarkdownRenderStyles,
    onLinkClick: (String) -> Unit
) {
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)

    Surface(
        shape = TABLE_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = styles.outlineVariant,
                shape = TABLE_SHAPE
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minTableWidth = maxWidth
            val needsHorizontalScroll = (colCount * 128 + (colCount - 1) * 16 + 24).dp > minTableWidth
            val colModifier = if (needsHorizontalScroll) {
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .width(IntrinsicSize.Max)
                    .widthIn(min = minTableWidth)
            } else {
                Modifier.fillMaxWidth()
            }
            Column(modifier = colModifier) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (c in 0 until colCount) {
                        Box(
                            modifier = if (needsHorizontalScroll) Modifier.width(128.dp) else Modifier.weight(1f)
                        ) {
                            InlineRichText(
                                inlines = headers.getOrElse(c) { emptyList() },
                                style = styles.tableHeaderStyle,
                                styles = styles,
                                onLinkClick = onLinkClick
                            )
                        }
                    }
                }

                for (r in rows.indices) {
                    val row = rows[r]
                    HorizontalDivider(
                        color = styles.outlineVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (c in 0 until colCount) {
                            Box(
                                modifier = if (needsHorizontalScroll) Modifier.width(128.dp) else Modifier.weight(1f)
                            ) {
                                InlineRichText(
                                    inlines = row.getOrElse(c) { emptyList() },
                                    style = styles.tableCellStyle,
                                    styles = styles,
                                    onLinkClick = onLinkClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
