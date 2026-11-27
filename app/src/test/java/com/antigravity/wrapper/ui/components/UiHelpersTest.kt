package com.antigravity.wrapper.ui.components

import com.antigravity.wrapper.data.ArtifactCard
import com.antigravity.wrapper.data.AssistantSegment
import com.antigravity.wrapper.data.AssistantSegmentKind
import com.antigravity.wrapper.data.ChatMessage
import com.antigravity.wrapper.data.MessageRole
import com.antigravity.wrapper.data.ModelOption
import com.antigravity.wrapper.data.ReasoningBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiHelpersTest {

    @Test
    fun formatReasoningFileDisplayNameExtractsBasenameFromDeepPaths() {
        assertEquals(
            "MessageItem.kt",
            formatReasoningFileDisplayName("app/src/main/java/com/antigravity/wrapper/ui/components/MessageItem.kt", "")
        )
        assertEquals(
            "MainActivity.kt",
            formatReasoningFileDisplayName("", "app\\src\\main\\MainActivity.kt")
        )
        assertEquals(
            "README.md",
            formatReasoningFileDisplayName("README.md", "fallback.md")
        )
        assertEquals(
            "components",
            formatReasoningFileDisplayName("ui/components/", "")
        )
    }

    @Test
    fun matchesActiveModelLabelAvoidsPrefixCollisions() {
        assertFalse(matchesActiveModelLabel("Gemini 4 Flash", "Gemini 4 Flash Thinking High"))
        assertTrue(matchesActiveModelLabel("Gemini 4 Flash Thinking", "Gemini 4 Flash Thinking High"))

        assertFalse(matchesActiveModelLabel("GPT-OSS 120B", "GPT-OSS 120B Pro"))
        assertTrue(matchesActiveModelLabel("GPT-OSS 120B", "GPT-OSS 120B (Medium)"))
        assertTrue(matchesActiveModelLabel("GPT-OSS 120B (Medium)", "GPT-OSS 120B (Medium)"))

        assertTrue(matchesActiveModelLabel("Gemini 4 Neon", "Gemini 4 Neon Minimal"))
        assertTrue(matchesActiveModelLabel("Gemini Fast", "Gemini Fast"))
        assertFalse(matchesActiveModelLabel("Gemini", "Gemini Fast"))
    }

    @Test
    fun resolveSelectedModelPrefersExplicitFlagThenExactLabelMatch() {
        val models = listOf(
            ModelOption(id = "1", name = "Gemini 4 Flash", isSelected = false),
            ModelOption(id = "2", name = "Gemini 4 Flash Thinking", isSelected = false),
            ModelOption(id = "3", name = "Claude 4.5 Sonnet", isSelected = true)
        )
        assertEquals("3", resolveSelectedModel(models, "Gemini 4 Flash High")?.id)

        val fallbackModels = listOf(
            ModelOption(id = "1", name = "Gemini 4 Flash", isSelected = false),
            ModelOption(id = "2", name = "Gemini 4 Flash Thinking", isSelected = false)
        )
        assertEquals("2", resolveSelectedModel(fallbackModels, "Gemini 4 Flash Thinking High")?.id)
        assertNull(resolveSelectedModel(fallbackModels, "Unknown Model"))
    }

    @Test
    fun flattenChatMessagesSplitsLargeAssistantTurnsWithDeterministicKeys() {
        val userMsg = ChatMessage(
            id = "u-1",
            role = MessageRole.USER,
            markdownContent = "it has access to the device screen itself via the sdk"
        )
        val assistantMsg = ChatMessage(
            id = "a-1",
            role = MessageRole.ASSISTANT,
            markdownContent = "Intro\n\n```kotlin\nprintln(1)\n```\n\nOutro",
            segments = listOf(
                AssistantSegment(
                    id = "seg-r",
                    kind = AssistantSegmentKind.REASONING,
                    reasoningBlock = ReasoningBlock(id = "rb-1", title = "Worked for 12s")
                ),
                AssistantSegment(
                    id = "seg-m",
                    kind = AssistantSegmentKind.MARKDOWN,
                    markdown = "Intro\n\n```kotlin\nprintln(1)\n```\n\nOutro"
                )
            ),
            artifacts = listOf(ArtifactCard(id = "art-1", title = "2 files changed"))
        )

        val feed1 = flattenChatMessages(listOf(userMsg, assistantMsg))
        val feed2 = flattenChatMessages(listOf(userMsg, assistantMsg))

        assertEquals(feed1.map { it.key }, feed2.map { it.key })
        assertEquals(
            listOf(
                "user_bubble",
                "reasoning_header",
                "assistant_prose",
                "assistant_code_block",
                "assistant_prose",
                "artifact_card",
                "assistant_actions"
            ),
            feed1.map { it.contentType }
        )
        assertEquals(feed1.size, feed1.map { it.key }.toSet().size)
        assertTrue(feed1[1].isTurnStart)
        assertFalse(feed1[1].isTurnEnd)
        assertTrue(feed1.last().isTurnEnd)
    }

    @Test
    fun flattenChatMessagesSplitsTallBlockQuotesIntoGranularSlices() {
        val quoteMd = buildString {
            appendLine("Here is a prompt:")
            appendLine()
            appendLine("> Paragraph one inside quote.")
            appendLine(">")
            appendLine("> Paragraph two inside quote.")
            appendLine(">")
            appendLine("> Paragraph three inside quote.")
            appendLine(">")
            appendLine("> - Item 1")
            appendLine("> - Item 2")
            appendLine("> - Item 3")
            appendLine("> - Item 4")
            appendLine("> - Item 5")
        }
        val msg = ChatMessage(
            id = "a-bq",
            role = MessageRole.ASSISTANT,
            markdownContent = quoteMd
        )

        val feed1 = flattenChatMessages(listOf(msg))
        val feed2 = flattenChatMessages(listOf(msg))
        assertEquals(feed1.map { it.key }, feed2.map { it.key })

        val quoteSlices = feed1.filterIsInstance<ChatFeedItem.BlockQuoteSlice>()
        assertEquals(5, quoteSlices.size)
        assertTrue(quoteSlices.first().isQuoteStart)
        assertFalse(quoteSlices.first().isQuoteEnd)
        assertFalse(quoteSlices[1].isQuoteStart)
        assertFalse(quoteSlices[1].isQuoteEnd)
        assertFalse(quoteSlices.last().isQuoteStart)
        assertTrue(quoteSlices.last().isQuoteEnd)
        assertEquals("assistant_blockquote_slice", quoteSlices.first().contentType)
    }
}
