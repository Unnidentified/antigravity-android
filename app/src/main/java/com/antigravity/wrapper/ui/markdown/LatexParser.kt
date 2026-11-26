package com.antigravity.wrapper.ui.markdown

import androidx.compose.runtime.Immutable

@Immutable
sealed interface LatexNode {
    @Immutable data class Row(val children: List<LatexNode>) : LatexNode
    @Immutable data class Text(val value: String, val italic: Boolean = false, val bold: Boolean = false) : LatexNode
    @Immutable data class Fraction(val numerator: LatexNode, val denominator: LatexNode) : LatexNode
    @Immutable data class Sqrt(val index: LatexNode?, val body: LatexNode) : LatexNode
    @Immutable data class Script(val base: LatexNode, val superscript: LatexNode?, val subscript: LatexNode?) : LatexNode
    @Immutable data class Matrix(
        val leftDelim: String,
        val rightDelim: String,
        val rows: List<List<LatexNode>>,
        val isAligned: Boolean = false
    ) : LatexNode
    @Immutable data class OverAccent(val accent: String, val body: LatexNode) : LatexNode
    @Immutable data class Stacked(
        val body: LatexNode,
        val above: LatexNode? = null,
        val below: LatexNode? = null
    ) : LatexNode
}

/**
 * Pure-Kotlin recursive-descent LaTeX math parser supporting fractions, roots,
 * super/subscripts, matrices/cases/aligned blocks, styled text, and Greek/math symbols.
 */
object LatexParser {

    private val parseCache = object : LinkedHashMap<String, LatexNode>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LatexNode>?): Boolean {
            return size > 128
        }
    }

    fun parse(input: String): LatexNode {
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return LatexNode.Text("")
        synchronized(parseCache) {
            parseCache[cleaned]?.let { return it }
        }
        val parsed = Parser(cleaned).parseSequence(stopTokens = emptySet())
        if (cleaned.length <= 2_000) {
            synchronized(parseCache) {
                parseCache[cleaned] = parsed
            }
        }
        return parsed
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseSequence(stopTokens: Set<String>): LatexNode {
            val items = mutableListOf<LatexNode>()
            while (pos < src.length) {
                if (stopTokens.any { src.startsWith(it, pos) }) {
                    break
                }
                val ch = src[pos]
                if (ch == '}') break
                if (ch.isWhitespace()) {
                    pos++
                    continue
                }
                val atom = parseAtom() ?: continue
                val attached = parseScripts(atom)
                items.add(attached)
            }
            return when (items.size) {
                0 -> LatexNode.Text("")
                1 -> items[0]
                else -> LatexNode.Row(items)
            }
        }

        private fun parseAtom(): LatexNode? {
            skipWhitespace()
            if (pos >= src.length) return null
            return when (val ch = src[pos]) {
                '{' -> parseBracedGroup()
                '\\' -> parseCommand()
                '~' -> {
                    pos++
                    LatexNode.Text(" ")
                }
                '+', '-', '=', '<', '>', ',' -> {
                    pos++
                    val spacing = if (ch == ',') ", " else " $ch "
                    LatexNode.Text(spacing)
                }
                '(', ')', '[', ']', '|', '/', ':', ';', '!', '?' -> {
                    pos++
                    LatexNode.Text(ch.toString())
                }
                else -> {
                    if (ch.isDigit() || ch == '.') {
                        val start = pos
                        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
                        LatexNode.Text(src.substring(start, pos))
                    } else if (ch.isLetter()) {
                        pos++
                        LatexNode.Text(ch.toString(), italic = true)
                    } else {
                        pos++
                        LatexNode.Text(ch.toString())
                    }
                }
            }
        }

        private fun parseScripts(base: LatexNode): LatexNode {
            var sup: LatexNode? = null
            var sub: LatexNode? = null
            while (pos < src.length) {
                skipWhitespace()
                if (pos >= src.length) break
                when (src[pos]) {
                    '^' -> {
                        pos++
                        sup = parseArgument()
                    }
                    '_' -> {
                        pos++
                        sub = parseArgument()
                    }
                    else -> break
                }
            }
            return if (sup != null || sub != null) {
                LatexNode.Script(base = base, superscript = sup, subscript = sub)
            } else {
                base
            }
        }

        private fun parseArgument(): LatexNode {
            skipWhitespace()
            if (pos >= src.length) return LatexNode.Text("")
            return if (src[pos] == '{') {
                parseBracedGroup()
            } else {
                parseAtom() ?: LatexNode.Text("")
            }
        }

        private fun parseBracedGroup(): LatexNode {
            if (pos < src.length && src[pos] == '{') pos++
            val node = parseSequence(stopTokens = emptySet())
            if (pos < src.length && src[pos] == '}') pos++
            return node
        }

        private fun parseOptionalBracket(): LatexNode? {
            skipWhitespace()
            if (pos >= src.length || src[pos] != '[') return null
            pos++ // '['
            val node = parseSequence(stopTokens = setOf("]"))
            if (pos < src.length && src[pos] == ']') pos++
            return node
        }

        private fun parseCommand(): LatexNode? {
            pos++ // '\'
            if (pos >= src.length) return LatexNode.Text("\\")

            // Single non-letter control symbols: \, \; \: \! \{ \} \% \$ \_ \\
            if (!src[pos].isLetter()) {
                val sym = src[pos++]
                return when (sym) {
                    ',', ';', ':' -> LatexNode.Text(" ")
                    '!' -> null
                    '\\' -> LatexNode.Text("\n")
                    '{', '}', '%', '$', '_', '#', '&' -> LatexNode.Text(sym.toString())
                    ' ' -> LatexNode.Text(" ")
                    else -> LatexNode.Text(sym.toString())
                }
            }

            val start = pos
            while (pos < src.length && src[pos].isLetter()) pos++
            val cmd = src.substring(start, pos)

            return when (cmd) {
                "frac", "dfrac", "tfrac", "cfrac" -> {
                    val num = parseArgument()
                    val den = parseArgument()
                    LatexNode.Fraction(num, den)
                }
                "binom", "tbinom", "dbinom" -> {
                    val top = parseArgument()
                    val bot = parseArgument()
                    LatexNode.Matrix(
                        leftDelim = "(",
                        rightDelim = ")",
                        rows = listOf(listOf(top), listOf(bot)),
                        isAligned = false
                    )
                }
                "sqrt" -> {
                    val idx = parseOptionalBracket()
                    val body = parseArgument()
                    LatexNode.Sqrt(idx, body)
                }
                "text", "textrm", "mathrm", "operatorname", "mbox", "mathsf", "mathtt", "mathcal", "mathfrak" -> {
                    val rawText = parseRawBracedText()
                    LatexNode.Text(rawText, italic = false)
                }
                "mathbf", "textbf", "boldsymbol", "bm" -> {
                    val rawText = parseRawBracedText()
                    LatexNode.Text(rawText, italic = false, bold = true)
                }
                "mathbb" -> {
                    val rawText = parseRawBracedText()
                    val mapped = rawText.map { BLACKBOARD_MAP[it] ?: it.toString() }.joinToString("")
                    LatexNode.Text(mapped, italic = false, bold = true)
                }
                "overset" -> {
                    val above = parseArgument()
                    val body = parseArgument()
                    LatexNode.Stacked(body = body, above = above)
                }
                "underset" -> {
                    val below = parseArgument()
                    val body = parseArgument()
                    LatexNode.Stacked(body = body, below = below)
                }
                "hat", "widehat" -> LatexNode.OverAccent("^", parseArgument())
                "bar", "overline" -> LatexNode.OverAccent("¯", parseArgument())
                "vec" -> LatexNode.OverAccent("→", parseArgument())
                "tilde", "widetilde" -> LatexNode.OverAccent("~", parseArgument())
                "dot" -> LatexNode.OverAccent("·", parseArgument())
                "left", "right", "big", "Big", "bigg", "Bigg" -> {
                    skipWhitespace()
                    if (pos < src.length) {
                        val delim = when {
                            src[pos] == '.' -> {
                                pos++
                                ""
                            }
                            src[pos] == '\\' -> {
                                val nextNode = parseCommand()
                                if (nextNode is LatexNode.Text) nextNode.value else ""
                            }
                            else -> src[pos++].toString()
                        }
                        if (delim.isEmpty()) null else LatexNode.Text(delim)
                    } else {
                        null
                    }
                }
                "begin" -> parseEnvironment()
                "quad" -> LatexNode.Text("   ")
                "qquad" -> LatexNode.Text("     ")
                "displaystyle", "textstyle", "scriptstyle", "limits", "nolimits" -> null
                else -> {
                    SYMBOLS[cmd]?.let { LatexNode.Text(it) }
                        ?: FUNCTIONS.firstOrNull { it == cmd }?.let { LatexNode.Text(it) }
                        ?: LatexNode.Text(cmd)
                }
            }
        }

        private fun parseRawBracedText(): String {
            skipWhitespace()
            if (pos >= src.length) return ""
            if (src[pos] != '{') {
                return src[pos++].toString()
            }
            pos++ // '{'
            var depth = 1
            val start = pos
            while (pos < src.length && depth > 0) {
                when (src[pos]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                if (depth > 0) pos++
            }
            val text = src.substring(start, pos)
            if (pos < src.length && src[pos] == '}') pos++
            return text
        }

        private fun parseEnvironment(): LatexNode {
            val envName = parseRawBracedText().trim()
            val endMarker = "\\end{$envName}"
            val endIndex = src.indexOf(endMarker, pos)
            val bodyRaw = if (endIndex >= 0) {
                val content = src.substring(pos, endIndex)
                pos = endIndex + endMarker.length
                content
            } else {
                val content = src.substring(pos)
                pos = src.length
                content
            }

            val rowStrings = bodyRaw.split("\\\\").map { it.trim() }.filter { it.isNotEmpty() }
            val parsedRows = rowStrings.map { row ->
                row.split("&").map { cell -> parse(cell.trim()) }
            }

            val isAlignedEnv = envName in setOf("aligned", "align", "align*", "gather", "gather*", "split", "eqnarray")
            val (left, right) = when (envName) {
                "pmatrix" -> "(" to ")"
                "bmatrix" -> "[" to "]"
                "Bmatrix" -> "{" to "}"
                "vmatrix" -> "|" to "|"
                "Vmatrix" -> "‖" to "‖"
                "cases" -> "{" to ""
                else -> "" to ""
            }

            return LatexNode.Matrix(left, right, parsedRows, isAligned = isAlignedEnv || envName == "cases")
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }

    private val BLACKBOARD_MAP = mapOf(
        'R' to "ℝ", 'N' to "ℕ", 'Z' to "ℤ", 'Q' to "ℚ", 'C' to "ℂ", 'E' to "𝔼", 'P' to "ℙ"
    )

    private val FUNCTIONS = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan",
        "sinh", "cosh", "tanh", "log", "ln", "exp", "lim", "sup", "inf", "max", "min",
        "arg", "det", "dim", "gcd", "ker", "Pr", "deg", "mod"
    )

    private val SYMBOLS = mapOf(
        // Greek lowercase
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
        "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
        "phi" to "φ", "varphi" to "ϕ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        // Greek uppercase
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        // Operators & calculus
        "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "infty" to "∞", "partial" to "∂", "nabla" to "∇",
        "pm" to " ± ", "mp" to " ∓ ", "times" to " × ", "div" to " ÷ ",
        "cdot" to " · ", "cdots" to "⋯", "dotsb" to "⋯", "ldots" to "…", "dots" to "…", "vdots" to "⋮", "ddots" to "⋱",
        "star" to "⋆", "circ" to "∘", "bullet" to "•", "oplus" to " ⊕ ", "otimes" to " ⊗ ",
        // Relations
        "leq" to " ≤ ", "le" to " ≤ ", "geq" to " ≥ ", "ge" to " ≥ ",
        "neq" to " ≠ ", "ne" to " ≠ ", "approx" to " ≈ ", "equiv" to " ≡ ",
        "sim" to " ∼ ", "simeq" to " ≃ ", "cong" to " ≅ ", "propto" to " ∝ ",
        "ll" to " ≪ ", "gg" to " ≫ ", "perp" to " ⊥ ", "parallel" to " ∥ ",
        // Set & logic
        "in" to " ∈ ", "notin" to " ∉ ", "ni" to " ∋ ",
        "subset" to " ⊂ ", "subseteq" to " ⊆ ", "supset" to " ⊃ ", "supseteq" to " ⊇ ",
        "cup" to " ∪ ", "cap" to " ∩ ", "emptyset" to "∅", "varnothing" to "∅",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄", "neg" to "¬", "lnot" to "¬",
        "land" to " ∧ ", "wedge" to " ∧ ", "lor" to " ∨ ", "vee" to " ∨ ",
        "therefore" to "∴", "because" to "∵", "top" to "⊤", "bot" to "⊥",
        // Arrows
        "to" to " → ", "rightarrow" to " → ", "leftarrow" to " ← ", "leftrightarrow" to " ↔ ",
        "Rightarrow" to " ⇒ ", "Leftarrow" to " ⇐ ", "Leftrightarrow" to " ⇔ ", "iff" to " ⟺ ",
        "implies" to " ⟹ ", "mapsto" to " ↦ ", "uparrow" to "↑", "downarrow" to "↓",
        // Misc
        "angle" to "∠", "degree" to "°", "prime" to "′", "ell" to "ℓ", "hbar" to "ℏ",
        "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ", "lfloor" to "⌊", "rfloor" to "⌋",
        "lceil" to "⌈", "rceil" to "⌉", "langle" to "⟨", "rangle" to "⟩", "mid" to " | "
    )
}
