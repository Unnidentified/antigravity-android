package com.antigravity.wrapper.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Lightweight native syntax highlighter for fenced code blocks in Compose.
 */
object CodeSyntaxHighlighter {

    private val KEYWORDS = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data",
        "override", "private", "public", "protected", "internal", "suspend", "inline",
        "return", "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
        "throw", "import", "package", "companion", "init", "this", "super", "null",
        "true", "false", "is", "in", "as", "by", "lateinit", "const", "typealias",
        "def", "async", "await", "from", "with", "yield", "lambda", "pass", "None",
        "True", "False", "and", "or", "not", "elif", "except", "raise",
        "function", "let", "export", "default", "extends", "implements", "new",
        "typeof", "instanceof", "switch", "case", "break", "continue", "struct",
        "fn", "pub", "mut", "match", "impl", "trait", "use", "mod", "crate",
        "SELECT", "FROM", "WHERE", "INSERT", "UPDATE", "DELETE", "JOIN", "CREATE", "TABLE"
    )

    private const val MAX_HIGHLIGHT_LENGTH = 4000

    fun highlight(code: String, darkTheme: Boolean): AnnotatedString {
        if (code.length > MAX_HIGHLIGHT_LENGTH) {
            return AnnotatedString(code)
        }
        val keywordColor = if (darkTheme) Color(0xFFC792EA) else Color(0xFF7C3AED)
        val stringColor = if (darkTheme) Color(0xFFA5D6A7) else Color(0xFF15803D)
        val commentColor = if (darkTheme) Color(0xFF7F8694) else Color(0xFF6B7280)
        val numberColor = if (darkTheme) Color(0xFFF78C6C) else Color(0xFFC2410C)
        val typeColor = if (darkTheme) Color(0xFF82AAFF) else Color(0xFF1D4ED8)
        val annotationColor = if (darkTheme) Color(0xFFFFCB6B) else Color(0xFFB45309)

        return buildAnnotatedString {
            append(code)
            var i = 0
            while (i < code.length) {
                // Line comment // or #
                if (code.startsWith("//", i) || (code[i] == '#' && (i == 0 || code[i - 1] == '\n' || code[i - 1] == ' '))) {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), i, end)
                    i = end
                    continue
                }
                // Block comment /* ... */
                if (code.startsWith("/*", i)) {
                    val end = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                    addStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic), i, end)
                    i = end
                    continue
                }
                // Strings "..." or '...'
                if (code[i] == '"' || code[i] == '\'') {
                    val quote = code[i]
                    var j = i + 1
                    while (j < code.length) {
                        if (code[j] == '\\' && j + 1 < code.length) {
                            j += 2
                            continue
                        }
                        if (code[j] == quote) {
                            j++
                            break
                        }
                        if (code[j] == '\n') break
                        j++
                    }
                    addStyle(SpanStyle(color = stringColor), i, j)
                    i = j
                    continue
                }
                // Annotations @Composable
                if (code[i] == '@' && i + 1 < code.length && code[i + 1].isLetter()) {
                    var j = i + 1
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    addStyle(SpanStyle(color = annotationColor), i, j)
                    i = j
                    continue
                }
                // Numbers
                if (code[i].isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit())) {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                    addStyle(SpanStyle(color = numberColor), i, j)
                    i = j
                    continue
                }
                // Identifiers / keywords / PascalCase types
                if (code[i].isLetter() || code[i] == '_') {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val token = code.substring(i, j)
                    when {
                        token in KEYWORDS -> addStyle(
                            SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium),
                            i,
                            j
                        )
                        token.first().isUpperCase() && token.any { it.isLowerCase() } -> addStyle(
                            SpanStyle(color = typeColor),
                            i,
                            j
                        )
                    }
                    i = j
                    continue
                }
                i++
            }
        }
    }
}
