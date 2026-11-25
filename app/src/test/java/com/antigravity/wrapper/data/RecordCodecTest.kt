package com.antigravity.wrapper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCodecTest {

    @Test
    fun roundTripsMultiLineQuickPrompts() {
        val input = listOf(
            QuickPrompt(
                id = "p-1",
                title = "PR Review",
                body = "Line 1\nLine 2 with \\ slash",
                category = "Engineering"
            ),
            QuickPrompt(
                id = "p-2",
                title = "Math Explain",
                body = "Derive E = mc^2 cleanly.",
                category = "Learn"
            )
        )

        val encoded = RecordCodec.encodePrompts(input)
        val decoded = RecordCodec.decodePrompts(encoded)

        assertEquals(input, decoded)
    }

    @Test
    fun decodesEmptyAndNullSafely() {
        assertTrue(RecordCodec.decodePrompts(null).isEmpty())
        assertTrue(RecordCodec.decodePrompts("").isEmpty())
    }
}
