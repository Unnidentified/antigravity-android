package com.antigravity.wrapper.ui.components

import com.antigravity.wrapper.data.ModelOption
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
}
