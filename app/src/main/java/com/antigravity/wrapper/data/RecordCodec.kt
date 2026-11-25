package com.antigravity.wrapper.data

/**
 * Lightweight, dependency-free record codec safe for pure JVM unit tests and R8 minification.
 * Escapes control characters so arbitrary titles, URLs, and multi-line prompts round-trip cleanly.
 */
object RecordCodec {

    private const val FIELD_SEP = '\u001F'
    private const val RECORD_SEP = '\u001E'

    fun encodeBookmarks(items: List<BookmarkItem>): String {
        return items.joinToString(RECORD_SEP.toString()) { item ->
            listOf(
                escape(item.id),
                escape(item.title),
                escape(item.url),
                item.createdAtMillis.toString(),
                item.pinned.toString()
            ).joinToString(FIELD_SEP.toString())
        }
    }

    fun decodeBookmarks(raw: String?): List<BookmarkItem> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { record ->
            val parts = record.split(FIELD_SEP)
            if (parts.size < 4) return@mapNotNull null
            BookmarkItem(
                id = unescape(parts[0]),
                title = unescape(parts[1]),
                url = unescape(parts[2]),
                createdAtMillis = parts[3].toLongOrNull() ?: 0L,
                pinned = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false
            )
        }
    }

    fun encodeDownloads(items: List<DownloadRecord>): String {
        return items.joinToString(RECORD_SEP.toString()) { item ->
            listOf(
                item.id.toString(),
                escape(item.fileName),
                escape(item.url),
                escape(item.mimeType),
                item.downloadedAtMillis.toString()
            ).joinToString(FIELD_SEP.toString())
        }
    }

    fun decodeDownloads(raw: String?): List<DownloadRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { record ->
            val parts = record.split(FIELD_SEP)
            if (parts.size < 5) return@mapNotNull null
            DownloadRecord(
                id = parts[0].toLongOrNull() ?: return@mapNotNull null,
                fileName = unescape(parts[1]),
                url = unescape(parts[2]),
                mimeType = unescape(parts[3]),
                downloadedAtMillis = parts[4].toLongOrNull() ?: 0L
            )
        }
    }

    fun encodePrompts(items: List<QuickPrompt>): String {
        return items.joinToString(RECORD_SEP.toString()) { item ->
            listOf(
                escape(item.id),
                escape(item.title),
                escape(item.body),
                escape(item.category)
            ).joinToString(FIELD_SEP.toString())
        }
    }

    fun decodePrompts(raw: String?): List<QuickPrompt> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { record ->
            val parts = record.split(FIELD_SEP)
            if (parts.size < 4) return@mapNotNull null
            QuickPrompt(
                id = unescape(parts[0]),
                title = unescape(parts[1]),
                body = unescape(parts[2]),
                category = unescape(parts[3])
            )
        }
    }

    private fun escape(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                FIELD_SEP -> sb.append("\\f")
                RECORD_SEP -> sb.append("\\r")
                '\n' -> sb.append("\\n")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> sb.append('\\')
                    'f' -> sb.append(FIELD_SEP)
                    'r' -> sb.append(RECORD_SEP)
                    'n' -> sb.append('\n')
                    else -> {
                        sb.append('\\')
                        sb.append(value[i + 1])
                    }
                }
                i += 2
            } else {
                sb.append(ch)
                i += 1
            }
        }
        return sb.toString()
    }
}
