package com.antigravity.wrapper.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun NativeMarkdownView(
    markdown: String,
    fontScale: Float = 1f,
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    SelectionContainer(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            blocks.forEach { block ->
                RenderMarkdownBlock(
                    block = block,
                    fontScale = fontScale,
                    isDark = isDark,
                    onCopyCode = onCopyCode,
                    onLinkClick = onLinkClick
                )
            }
        }
    }
}

@Composable
private fun RenderMarkdownBlock(
    block: MarkdownBlock,
    fontScale: Float,
    isDark: Boolean,
    onCopyCode: (String) -> Unit,
    onLinkClick: (String) -> Unit
) {
    when (block) {
        is MarkdownBlock.Heading -> {
            val baseSize = when (block.level) {
                1 -> 22.sp
                2 -> 19.sp
                3 -> 17.sp
                else -> 15.5.sp
            }
            val style = MaterialTheme.typography.titleMedium.copy(
                fontSize = baseSize * fontScale,
                lineHeight = (baseSize * 1.32f) * fontScale,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            InlineRichText(
                inlines = block.inlines,
                style = style,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 2.dp)
            )
        }

        is MarkdownBlock.Paragraph -> {
            val style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp * fontScale,
                lineHeight = 23.sp * fontScale,
                color = MaterialTheme.colorScheme.onSurface
            )
            InlineRichText(
                inlines = block.inlines,
                style = style,
                onLinkClick = onLinkClick
            )
        }

        is MarkdownBlock.CodeBlock -> {
            NativeCodeBlockCard(
                language = block.language,
                code = block.code,
                fontScale = fontScale,
                isDark = isDark,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(end = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    block.blocks.forEach { inner ->
                        RenderMarkdownBlock(
                            block = inner,
                            fontScale = fontScale,
                            isDark = isDark,
                            onCopyCode = onCopyCode,
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }
        }

        is MarkdownBlock.BulletList -> {
            val style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp * fontScale,
                lineHeight = 22.5.sp * fontScale,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEach { itemInlines ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = style.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(start = 6.dp, end = 10.dp)
                        )
                        InlineRichText(
                            inlines = itemInlines,
                            style = style,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MarkdownBlock.OrderedList -> {
            val style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp * fontScale,
                lineHeight = 22.5.sp * fontScale,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEach { (number, itemInlines) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "$number.",
                            style = style.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(start = 4.dp, end = 10.dp)
                        )
                        InlineRichText(
                            inlines = itemInlines,
                            style = style,
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
                fontScale = fontScale,
                onLinkClick = onLinkClick
            )
        }

        MarkdownBlock.HorizontalRule -> {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun InlineRichText(
    inlines: List<MarkdownInline>,
    style: TextStyle,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeColor = MaterialTheme.colorScheme.onSurface
    val mathColor = MaterialTheme.colorScheme.secondary

    val annotated = remember(inlines, primaryColor, codeBg, codeColor, mathColor, style.fontSize) {
        buildAnnotatedString {
            appendInlines(
                inlines = inlines,
                baseSize = style.fontSize,
                primaryColor = primaryColor,
                codeBg = codeBg,
                codeColor = codeColor,
                mathColor = mathColor,
                onLinkClick = onLinkClick
            )
        }
    }

    Text(
        text = annotated,
        style = style,
        modifier = modifier
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
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = baseSize * 0.9f,
                        background = codeBg,
                        color = codeColor
                    )
                )
                append(" ${inline.code} ")
                pop()
            }
            is MarkdownInline.InlineMath -> {
                val parsed = LatexParser.parse(inline.latex)
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
        is LatexNode.Matrix -> {
            append(node.leftDelim)
            node.rows.forEachIndexed { rIdx, row ->
                if (rIdx > 0) append("; ")
                row.forEachIndexed { cIdx, cell ->
                    if (cIdx > 0) append(", ")
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
    fontScale: Float,
    isDark: Boolean,
    onCopy: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600L)
            copied = false
        }
    }

    val highlighted = remember(code, isDark) {
        CodeSyntaxHighlighter.highlight(code, isDark)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = {
                        onCopy()
                        copied = true
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = highlighted,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp * fontScale,
                        lineHeight = 19.sp * fontScale,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
    val rootNode = remember(latex) { LatexParser.parse(latex) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500L)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
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
                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                    RenderLatexNode(node.numerator, fontSize * 0.85f, color)
                }
                HorizontalDivider(
                    thickness = 1.2.dp,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                )
                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
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
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(start = 1.dp)
                    ) {
                        if (node.superscript != null) {
                            RenderLatexNode(node.superscript, fontSize * 0.68f, color)
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (node.subscript != null) {
                            RenderLatexNode(node.subscript, fontSize * 0.68f, color)
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    node.rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
    fontScale: Float,
    onLinkClick: (String) -> Unit
) {
    val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val headerStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = 13.5.sp * fontScale,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cellStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 13.5.sp * fontScale,
        color = MaterialTheme.colorScheme.onSurface
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (c in 0 until colCount) {
                    Box(modifier = Modifier.width(128.dp)) {
                        InlineRichText(
                            inlines = headers.getOrElse(c) { emptyList() },
                            style = headerStyle,
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }

            rows.forEach { row ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (c in 0 until colCount) {
                        Box(modifier = Modifier.width(128.dp)) {
                            InlineRichText(
                                inlines = row.getOrElse(c) { emptyList() },
                                style = cellStyle,
                                onLinkClick = onLinkClick
                            )
                        }
                    }
                }
            }
        }
    }
}
