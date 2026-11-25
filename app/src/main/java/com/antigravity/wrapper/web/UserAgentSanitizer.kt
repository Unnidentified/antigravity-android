package com.antigravity.wrapper.web

/**
 * Sanitizes Android WebView's default User-Agent string by stripping WebView-specific markers
 * (`; wv` and `Version/x.y`) so Google OAuth (`accounts.google.com`) treats the session as a
 * standard modern Chrome Mobile client instead of returning `403 disallowed_useragent`.
 */
object UserAgentSanitizer {

    private const val FALLBACK_CHROME_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private const val DESKTOP_CHROME_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val WV_SEMICOLON_REGEX = Regex(";\\s*wv(?=\\s*[;)])", RegexOption.IGNORE_CASE)
    private val WV_LEADING_REGEX = Regex("\\(\\s*wv\\s*;\\s*", RegexOption.IGNORE_CASE)
    private val VERSION_TOKEN_REGEX = Regex("\\bVersion/\\d+(?:\\.\\d+)*\\s*", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE_REGEX = Regex("\\s{2,}")

    fun sanitize(defaultUserAgent: String?): String {
        if (defaultUserAgent.isNullOrBlank()) {
            return FALLBACK_CHROME_MOBILE_UA
        }

        val cleaned = defaultUserAgent
            .replace(WV_SEMICOLON_REGEX, "")
            .replace(WV_LEADING_REGEX, "(")
            .replace(VERSION_TOKEN_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

        return cleaned.ifBlank { FALLBACK_CHROME_MOBILE_UA }
    }

    fun forViewportMode(defaultUserAgent: String?, desktopMode: Boolean): String {
        return if (desktopMode) {
            DESKTOP_CHROME_UA
        } else {
            sanitize(defaultUserAgent)
        }
    }
}
