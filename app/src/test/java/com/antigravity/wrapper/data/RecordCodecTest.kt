package com.antigravity.wrapper.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordCodecTest {

    @Test
    fun roundTripsBookmarksWithSpecialCharacters() {
        val input = listOf(
            BookmarkItem(
                id = "bm-1",
                title = "Fix \\ bug\nwith quotes",
                url = "https://antigravity.google.com/c/123?a=1&b=2",
                createdAtMillis = 1_700_000_000_000L,
                pinned = true
            ),
            BookmarkItem(
                id = "bm-2",
                title = "Second Item",
                url = "https://antigravity.google.com",
                createdAtMillis = 1_700_000_001_000L,
                pinned = false
            )
        )

        val encoded = RecordCodec.encodeBookmarks(input)
        val decoded = RecordCodec.decodeBookmarks(encoded)

        assertEquals(input, decoded)
    }

    @Test
    fun roundTripsDownloads() {
        val input = listOf(
            DownloadRecord(
                id = 42L,
                fileName = "antigravity-report.pdf",
                url = "https://antigravity.google.com/files/report.pdf",
                mimeType = "application/pdf",
                downloadedAtMillis = 1_700_000_123_456L
            )
        )

        val encoded = RecordCodec.encodeDownloads(input)
        val decoded = RecordCodec.decodeDownloads(encoded)

        assertEquals(input, decoded)
    }

    @Test
    fun roundTripsMultiLineQuickPrompts() {
        val input = listOf(
            QuickPrompt(
                id = "p-1",
                title = "PR Review",
                body = "Line 1\nLine 2 with \\ slash",
                category = "Engineering"
            )
        )

        val encoded = RecordCodec.encodePrompts(input)
        val decoded = RecordCodec.decodePrompts(encoded)

        assertEquals(input, decoded)
    }

    @Test
    fun decodesEmptyAndNullSafely() {
        assertTrue(RecordCodec.decodeBookmarks(null).isEmpty())
        assertTrue(RecordCodec.decodeBookmarks("").isEmpty())
        assertTrue(RecordCodec.decodeDownloads(null).isEmpty())
        assertTrue(RecordCodec.decodePrompts("").isEmpty())
    }
}
