package com.antigravity.wrapper.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentSanitizerTest {

    @Test
    fun stripsWvAndVersionFromStandardAndroidWebViewUa() {
        val raw =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240805.005; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.127 Mobile Safari/537.36"

        val sanitized = UserAgentSanitizer.sanitize(raw)

        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A.240805.005) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.127 Mobile Safari/537.36",
            sanitized
        )
        assertFalse(sanitized.contains("; wv"))
        assertFalse(sanitized.contains("Version/4.0"))
        assertTrue(sanitized.contains("Chrome/128.0.6613.127"))
    }

    @Test
    fun returnsFallbackWhenNullOrBlank() {
        val fromNull = UserAgentSanitizer.sanitize(null)
        val fromBlank = UserAgentSanitizer.sanitize("   ")

        assertTrue(fromNull.contains("Chrome/"))
        assertFalse(fromNull.contains("; wv"))
        assertEquals(fromNull, fromBlank)
    }

    @Test
    fun preservesAlreadyCleanChromeMobileUa() {
        val chromeUa =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        assertEquals(chromeUa, UserAgentSanitizer.sanitize(chromeUa))
    }

    @Test
    fun returnsDesktopUaWhenDesktopModeEnabled() {
        val mobileUa =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36"

        val desktop = UserAgentSanitizer.forViewportMode(mobileUa, desktopMode = true)
        val mobile = UserAgentSanitizer.forViewportMode(mobileUa, desktopMode = false)

        assertFalse(desktop.contains("Mobile"))
        assertFalse(desktop.contains("; wv"))
        assertTrue(desktop.contains("Chrome/"))
        assertTrue(mobile.contains("Mobile"))
        assertFalse(mobile.contains("; wv"))
    }
}
