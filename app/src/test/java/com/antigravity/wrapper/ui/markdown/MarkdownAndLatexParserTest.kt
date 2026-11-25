package com.antigravity.wrapper.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownAndLatexParserTest {

    @Test
    fun parsesHeadingsCodeBlocksTablesAndDisplayMath() {
        val md = """
            # Quadratic Formula

            Given $ a x^2 + b x + c = 0 $ with **bold** and `inline`.

            $$
            x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            $$

            ```kotlin
            fun solve(a: Double) = a * 2
            ```

            | Var | Meaning |
            | --- | --- |
            | a | Leading coeff |
        """.trimIndent()

        val blocks = MarkdownParser.parse(md)

        assertTrue(blocks[0] is MarkdownBlock.Heading)
        assertEquals(1, (blocks[0] as MarkdownBlock.Heading).level)
        assertTrue(blocks.any { it is MarkdownBlock.MathBlock })
        val codeBlock = blocks.first { it is MarkdownBlock.CodeBlock } as MarkdownBlock.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.code.contains("fun solve"))
        val table = blocks.first { it is MarkdownBlock.TableBlock } as MarkdownBlock.TableBlock
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun parsesInlineLatexAndLinks() {
        val inlines = MarkdownParser.parseInlines(
            "Use \$\\alpha + \\beta\$ or \\(x^2\\) and see [Docs](https://antigravity.google.com)."
        )

        val maths = inlines.filterIsInstance<MarkdownInline.InlineMath>()
        val links = inlines.filterIsInstance<MarkdownInline.Link>()

        assertEquals(2, maths.size)
        assertEquals("\\alpha + \\beta", maths[0].latex)
        assertEquals("x^2", maths[1].latex)
        assertEquals(1, links.size)
        assertEquals("Docs", links[0].label)
        assertEquals("https://antigravity.google.com", links[0].url)
    }

    @Test
    fun parsesLatexFractionsRootsScriptsAndMatrices() {
        val fracNode = LatexParser.parse("\\frac{\\alpha^2 + 1}{\\sqrt{x_i}}")
        assertTrue(fracNode is LatexNode.Fraction)
        val frac = fracNode as LatexNode.Fraction
        assertTrue(frac.denominator is LatexNode.Sqrt)

        val matrixNode = LatexParser.parse("\\begin{pmatrix} 1 & 0 \\\\ 0 & 1 \\end{pmatrix}")
        assertTrue(matrixNode is LatexNode.Matrix)
        val mat = matrixNode as LatexNode.Matrix
        assertEquals("(", mat.leftDelim)
        assertEquals(")", mat.rightDelim)
        assertEquals(2, mat.rows.size)
        assertEquals(2, mat.rows[0].size)
    }
}
