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

    @Test
    fun handlesNestedListsTasksMathPipesInTablesAndAdvancedLatex() {
        val md = """
            > Quoted note with `snake_case_id` and _emphasis_ plus __strong__.

            - Top bullet
              - Nested bullet with [ ]? No:
              - [x] Done task
              - [ ] Open task

            | Formula | Result |
            | --- | --- |
            | ${'$'}P(A|B)${'$'} | Conditional |

            $$
            \begin{aligned}
            f(n) &= \binom{n}{k} + \mathcal{O}(n \log n) \\
            g(x) &= \overset{a}{\rightarrow} \underset{i}{x}
            \end{aligned}
            $$
        """.trimIndent()

        val blocks = MarkdownParser.parse(md)

        assertTrue(blocks[0] is MarkdownBlock.BlockQuote)
        val quotePara = (blocks[0] as MarkdownBlock.BlockQuote).blocks.first() as MarkdownBlock.Paragraph
        assertTrue(quotePara.inlines.any { it is MarkdownInline.InlineCode && it.code == "snake_case_id" })
        assertTrue(quotePara.inlines.any { it is MarkdownInline.Italic })
        assertTrue(quotePara.inlines.any { it is MarkdownInline.Bold })

        val bullets = blocks.first { it is MarkdownBlock.BulletList } as MarkdownBlock.BulletList
        assertEquals(0, bullets.items[0].depth)
        assertEquals(1, bullets.items[1].depth)
        assertEquals(true, bullets.items[2].checked)
        assertEquals(false, bullets.items[3].checked)

        val table = blocks.first { it is MarkdownBlock.TableBlock } as MarkdownBlock.TableBlock
        assertEquals(2, table.rows[0].size)
        assertTrue(table.rows[0][0].any { it is MarkdownInline.InlineMath && it.latex == "P(A|B)" })

        val mathBlock = blocks.first { it is MarkdownBlock.MathBlock } as MarkdownBlock.MathBlock
        val aligned = LatexParser.parse(mathBlock.latex)
        assertTrue(aligned is LatexNode.Matrix && aligned.isAligned)
        assertEquals(2, (aligned as LatexNode.Matrix).rows.size)
    }

    @Test
    fun parsesIndentedCodeBlocksSeparatelyFromBlockquotes() {
        val md = """
            > Quote without copy button

                val x = 42
                println(x)
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.BlockQuote)
        val code = blocks[1] as MarkdownBlock.CodeBlock
        assertEquals("", code.language)
        assertTrue(code.code.contains("val x = 42"))
        assertTrue(code.code.contains("println(x)"))
    }
}
