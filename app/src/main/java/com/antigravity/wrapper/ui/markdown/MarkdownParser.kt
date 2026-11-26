package com.antigravity.wrapper.ui.markdown

import androidx.compose.runtime.Immutable

@Immutable
sealed interface MarkdownBlock {
    @Immutable data class Heading(val level: Int, val inlines: List<MarkdownInline>) : MarkdownBlock
    @Immutable data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownBlock
    @Immutable data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    @Immutable data class MathBlock(val latex: String) : MarkdownBlock
    @Immutable data class BlockQuote(val blocks: List<MarkdownBlock>) : MarkdownBlock
    @Immutable data class BulletEntry(
        val depth: Int,
        val inlines: List<MarkdownInline>,
        val checked: Boolean? = null
    )
    @Immutable data class OrderedEntry(
        val depth: Int,
        val number: Int,
        val inlines: List<MarkdownInline>
    )
    @Immutable data class BulletList(val items: List<BulletEntry>) : MarkdownBlock
    @Immutable data class OrderedList(val items: List<OrderedEntry>) : MarkdownBlock
    @Immutable data class TableBlock(
        val headers: List<List<MarkdownInline>>,
        val rows: List<List<List<MarkdownInline>>>
    ) : MarkdownBlock
    @Immutable data object HorizontalRule : MarkdownBlock
}

@Immutable
sealed interface MarkdownInline {
    @Immutable data class Text(val value: String) : MarkdownInline
    @Immutable data class Bold(val children: List<MarkdownInline>) : MarkdownInline
    @Immutable data class Italic(val children: List<MarkdownInline>) : MarkdownInline
    @Immutable data class Strikethrough(val children: List<MarkdownInline>) : MarkdownInline
    @Immutable data class InlineCode(val code: String) : MarkdownInline
    @Immutable data class InlineMath(val latex: String) : MarkdownInline
    @Immutable data class Link(val label: String, val url: String) : MarkdownInline
}

/**
 * Pure-Kotlin Markdown + LaTeX AST parser handling headings, nested lists, task checkboxes,
 * blockquotes, math-safe pipe tables, fenced code blocks, display math ($$ / \[...\]),
 * inline math ($ / \(...\)), inline code, emphasis (* / _), strikethrough, and links.
 */
object MarkdownParser {

    private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.+)$")
    private val HR_REGEX = Regex("^(\\s*([-*_])\\s*){3,}$")
    private val BULLET_REGEX = Regex("^(\\s*)[-*+]\\s+(.+)$")
    private val ORDERED_REGEX = Regex("^(\\s*)(\\d+)[.)]\\s+(.+)$")
    private val TASK_PREFIX_REGEX = Regex("^\\[([ xX])]\\s+(.*)$")
    private val TABLE_SEP_REGEX = Regex("^\\|?(\\s*:?-+:?\\s*\\|)+(\\s*:?-+:?\\s*)?\\|?$")

    private val parseCache = object : LinkedHashMap<String, List<MarkdownBlock>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MarkdownBlock>>?): Boolean {
            return size > 64
        }
    }

    fun parse(markdown: String): List<MarkdownBlock> {
        synchronized(parseCache) {
            parseCache[markdown]?.let { return it }
        }
        val parsed = parseInternal(markdown)
        if (markdown.length <= 24_000) {
            synchronized(parseCache) {
                parseCache[markdown] = parsed
            }
        }
        return parsed
    }

    private fun leadingIndentDepth(spacesRaw: String): Int {
        val spaces = spacesRaw.fold(0) { acc, ch -> acc + if (ch == '\t') 2 else 1 }
        return (spaces / 2).coerceIn(0, 4)
    }

    private fun parseInternal(markdown: String): List<MarkdownBlock> {
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 1. Fenced code block ```lang
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                i++
                val codeLines = mutableListOf<String>()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
                blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
                continue
            }

            // 2. Display math block $$ ... $$ or \[ ... \]
            if (trimmed.startsWith("$$")) {
                val rest = trimmed.removePrefix("$$")
                if (rest.endsWith("$$") && rest.length >= 2) {
                    blocks.add(MarkdownBlock.MathBlock(rest.removeSuffix("$$").trim()))
                    i++
                } else {
                    val mathLines = mutableListOf<String>()
                    if (rest.isNotBlank()) mathLines.add(rest)
                    i++
                    while (i < lines.size) {
                        val l = lines[i]
                        val endIdx = l.indexOf("$$")
                        if (endIdx >= 0) {
                            val before = l.substring(0, endIdx).trim()
                            if (before.isNotEmpty()) mathLines.add(before)
                            i++
                            break
                        }
                        mathLines.add(l)
                        i++
                    }
                    blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n").trim()))
                }
                continue
            }

            if (trimmed.startsWith("\\[")) {
                val rest = trimmed.removePrefix("\\[")
                if (rest.endsWith("\\]")) {
                    blocks.add(MarkdownBlock.MathBlock(rest.removeSuffix("\\]").trim()))
                    i++
                } else {
                    val mathLines = mutableListOf<String>()
                    if (rest.isNotBlank()) mathLines.add(rest)
                    i++
                    while (i < lines.size) {
                        val l = lines[i]
                        val endIdx = l.indexOf("\\]")
                        if (endIdx >= 0) {
                            val before = l.substring(0, endIdx).trim()
                            if (before.isNotEmpty()) mathLines.add(before)
                            i++
                            break
                        }
                        mathLines.add(l)
                        i++
                    }
                    blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n").trim()))
                }
                continue
            }

            // 3. Heading
            val headingMatch = HEADING_REGEX.matchEntire(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2].trim()
                blocks.add(MarkdownBlock.Heading(level, parseInlines(text)))
                i++
                continue
            }

            // 4. Horizontal rule
            if (HR_REGEX.matches(trimmed)) {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // 5. Pipe table (header + separator row)
            if (trimmed.contains("|") && i + 1 < lines.size && TABLE_SEP_REGEX.matches(lines[i + 1].trim())) {
                val headers = splitTableRow(trimmed).map { parseInlines(it) }
                i += 2
                val rows = mutableListOf<List<List<MarkdownInline>>>()
                while (i < lines.size && lines[i].trim().contains("|") && lines[i].trim().isNotEmpty()) {
                    rows.add(splitTableRow(lines[i].trim()).map { parseInlines(it) })
                    i++
                }
                blocks.add(MarkdownBlock.TableBlock(headers, rows))
                continue
            }

            // 6. Blockquote
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").removePrefix(" "))
                    i++
                }
                blocks.add(MarkdownBlock.BlockQuote(parse(quoteLines.joinToString("\n"))))
                continue
            }

            // 7. Bullet list (preserving indentation depth & task checkboxes)
            if (BULLET_REGEX.matches(line)) {
                val items = mutableListOf<MarkdownBlock.BulletEntry>()
                while (i < lines.size) {
                    val m = BULLET_REGEX.matchEntire(lines[i]) ?: break
                    val depth = leadingIndentDepth(m.groupValues[1])
                    var rawBody = m.groupValues[2].trim()
                    val taskMatch = TASK_PREFIX_REGEX.matchEntire(rawBody)
                    val checked = if (taskMatch != null) {
                        rawBody = taskMatch.groupValues[2].trim()
                        taskMatch.groupValues[1].equals("x", ignoreCase = true)
                    } else {
                        null
                    }
                    i++
                    while (i < lines.size &&
                        lines[i].trim().isNotEmpty() &&
                        lines[i].startsWith("  ") &&
                        !BULLET_REGEX.matches(lines[i]) &&
                        !ORDERED_REGEX.matches(lines[i]) &&
                        !lines[i].trim().startsWith("```") &&
                        !lines[i].trim().startsWith("$$")
                    ) {
                        rawBody += " " + lines[i].trim()
                        i++
                    }
                    items.add(MarkdownBlock.BulletEntry(depth, parseInlines(rawBody), checked))
                }
                blocks.add(MarkdownBlock.BulletList(items))
                continue
            }

            // 8. Ordered list (preserving indentation depth)
            if (ORDERED_REGEX.matches(line)) {
                val items = mutableListOf<MarkdownBlock.OrderedEntry>()
                while (i < lines.size) {
                    val m = ORDERED_REGEX.matchEntire(lines[i]) ?: break
                    val depth = leadingIndentDepth(m.groupValues[1])
                    val number = m.groupValues[2].toIntOrNull() ?: (items.size + 1)
                    var rawBody = m.groupValues[3].trim()
                    i++
                    while (i < lines.size &&
                        lines[i].trim().isNotEmpty() &&
                        lines[i].startsWith("  ") &&
                        !BULLET_REGEX.matches(lines[i]) &&
                        !ORDERED_REGEX.matches(lines[i]) &&
                        !lines[i].trim().startsWith("```") &&
                        !lines[i].trim().startsWith("$$")
                    ) {
                        rawBody += " " + lines[i].trim()
                        i++
                    }
                    items.add(MarkdownBlock.OrderedEntry(depth, number, parseInlines(rawBody)))
                }
                blocks.add(MarkdownBlock.OrderedList(items))
                continue
            }

            // 9. Paragraph (consume until next blank or structural block)
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i]
                val curTrimmed = cur.trim()
                if (curTrimmed.isEmpty() ||
                    curTrimmed.startsWith("```") ||
                    curTrimmed.startsWith("$$") ||
                    curTrimmed.startsWith("\\[") ||
                    curTrimmed.startsWith(">") ||
                    HEADING_REGEX.matches(curTrimmed) ||
                    HR_REGEX.matches(curTrimmed) ||
                    BULLET_REGEX.matches(cur) ||
                    ORDERED_REGEX.matches(cur)
                ) {
                    break
                }
                if (curTrimmed.contains("|") && i + 1 < lines.size && TABLE_SEP_REGEX.matches(lines[i + 1].trim())) {
                    break
                }
                paraLines.add(curTrimmed)
                i++
            }
            if (paraLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(parseInlines(paraLines.joinToString(" "))))
            } else {
                i++
            }
        }

        return blocks
    }

    /**
     * Splits a pipe table row while keeping escaped `\|` and pipes inside inline math `$...$`
     * or inline code `` `...` `` intact (e.g. `$P(A|B)$`).
     */
    private fun splitTableRow(row: String): List<String> {
        var working = row.trim()
        if (working.startsWith("|")) working = working.substring(1)
        if (working.endsWith("|") && !working.endsWith("\\|")) {
            working = working.substring(0, working.length - 1)
        }
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var inCode = false
        var inMath = false
        var idx = 0
        while (idx < working.length) {
            val ch = working[idx]
            if (ch == '\\' && idx + 1 < working.length) {
                if (working[idx + 1] == '|') {
                    cur.append('|')
                    idx += 2
                    continue
                }
                cur.append(ch).append(working[idx + 1])
                idx += 2
                continue
            }
            if (ch == '`') {
                inCode = !inCode
                cur.append(ch)
                idx++
                continue
            }
            if (!inCode && ch == '$') {
                inMath = !inMath
                cur.append(ch)
                idx++
                continue
            }
            if (ch == '|' && !inCode && !inMath) {
                cells.add(cur.toString().trim())
                cur.setLength(0)
            } else {
                cur.append(ch)
            }
            idx++
        }
        cells.add(cur.toString().trim())
        return cells
    }

    fun parseInlines(text: String): List<MarkdownInline> {
        val result = mutableListOf<MarkdownInline>()
        val buffer = StringBuilder()
        var i = 0

        fun flushText() {
            if (buffer.isNotEmpty()) {
                result.add(MarkdownInline.Text(buffer.toString()))
                buffer.setLength(0)
            }
        }

        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length) {
                val next = text[i + 1]
                if (next == '(') {
                    val end = text.indexOf("\\)", i + 2)
                    if (end > i + 2) {
                        flushText()
                        result.add(MarkdownInline.InlineMath(text.substring(i + 2, end).trim()))
                        i = end + 2
                        continue
                    }
                }
                if (next in charArrayOf('$', '*', '_', '`', '[', ']', '\\')) {
                    buffer.append(next)
                    i += 2
                    continue
                }
            }

            // Inline code `...`
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    flushText()
                    result.add(MarkdownInline.InlineCode(text.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }

            // Inline $$...$$ inside a line
            if (text.startsWith("$$", i)) {
                val end = text.indexOf("$$", i + 2)
                if (end > i + 2) {
                    flushText()
                    result.add(MarkdownInline.InlineMath(text.substring(i + 2, end).trim()))
                    i = end + 2
                    continue
                }
            }

            // Inline LaTeX $...$
            if (text[i] == '$' && (i + 1 < text.length && text[i + 1] != '$' && !text[i + 1].isWhitespace())) {
                val end = findClosingDollar(text, i + 1)
                if (end > i + 1) {
                    flushText()
                    result.add(MarkdownInline.InlineMath(text.substring(i + 1, end).trim()))
                    i = end + 1
                    continue
                }
            }

            // Link [label](url)
            if (text[i] == '[') {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket > i + 1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        flushText()
                        val label = text.substring(i + 1, closeBracket).replace("`", "")
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        result.add(MarkdownInline.Link(label, url))
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // Bold **...** or __...__
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    flushText()
                    result.add(MarkdownInline.Bold(parseInlines(text.substring(i + 2, end))))
                    i = end + 2
                    continue
                }
            }
            if (text.startsWith("__", i)) {
                val end = text.indexOf("__", i + 2)
                if (end > i + 2) {
                    flushText()
                    result.add(MarkdownInline.Bold(parseInlines(text.substring(i + 2, end))))
                    i = end + 2
                    continue
                }
            }

            // Strikethrough ~~...~~
            if (text.startsWith("~~", i)) {
                val end = text.indexOf("~~", i + 2)
                if (end > i + 2) {
                    flushText()
                    result.add(MarkdownInline.Strikethrough(parseInlines(text.substring(i + 2, end))))
                    i = end + 2
                    continue
                }
            }

            // Italic *...* or word-boundary _..._ (does not split snake_case identifiers)
            if (text[i] == '*' && (i + 1 < text.length && !text[i + 1].isWhitespace())) {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    flushText()
                    result.add(MarkdownInline.Italic(parseInlines(text.substring(i + 1, end))))
                    i = end + 1
                    continue
                }
            }
            if (text[i] == '_' &&
                (i == 0 || !text[i - 1].isLetterOrDigit()) &&
                (i + 1 < text.length && !text[i + 1].isWhitespace())
            ) {
                val end = findClosingWordUnderscore(text, i + 1)
                if (end > i + 1) {
                    flushText()
                    result.add(MarkdownInline.Italic(parseInlines(text.substring(i + 1, end))))
                    i = end + 1
                    continue
                }
            }

            buffer.append(text[i])
            i++
        }

        flushText()
        return result
    }

    private fun findClosingWordUnderscore(text: String, start: Int): Int {
        var idx = start
        while (idx < text.length) {
            if (text[idx] == '_' &&
                !text[idx - 1].isWhitespace() &&
                (idx + 1 >= text.length || !text[idx + 1].isLetterOrDigit())
            ) {
                return idx
            }
            idx++
        }
        return -1
    }

    private fun findClosingDollar(text: String, start: Int): Int {
        var idx = start
        while (idx < text.length) {
            if (text[idx] == '\\' && idx + 1 < text.length) {
                idx += 2
                continue
            }
            if (text[idx] == '$' && (idx == start || !text[idx - 1].isWhitespace())) {
                return idx
            }
            idx++
        }
        return -1
    }
}
