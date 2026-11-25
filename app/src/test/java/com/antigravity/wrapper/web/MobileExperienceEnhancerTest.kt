package com.antigravity.wrapper.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileExperienceEnhancerTest {

    @Test
    fun escapesSpecialCharactersForJsStrings() {
        val raw = "Hello \"world\"\nLine 2\\path\tTab"
        val escaped = MobileExperienceEnhancer.escapeForJsString(raw)

        assertEquals("Hello \\\"world\\\"\\nLine 2\\\\path\\tTab", escaped)
    }

    @Test
    fun buildsRemoveScriptWhenPolishDisabled() {
        val script = MobileExperienceEnhancer.buildMobilePolishScript(
            enabled = false,
            darkTheme = true,
            amoledBlack = false
        )

        assertTrue(script.contains("removeChild"))
        assertFalse(script.contains("color-scheme"))
    }

    @Test
    fun buildsDarkAndAmoledPolishScriptWhenEnabled() {
        val script = MobileExperienceEnhancer.buildMobilePolishScript(
            enabled = true,
            darkTheme = true,
            amoledBlack = true
        )

        assertTrue(script.contains("color-scheme: dark"))
        assertTrue(script.contains("#000000"))
        assertTrue(script.contains("overflow-x: clip"))
    }

    @Test
    fun buildsInsertPromptScriptWithEscapedContent() {
        val script = MobileExperienceEnhancer.buildInsertPromptScript("Review \"PR\"\nNow")

        assertTrue(script.contains("Review \\\"PR\\\"\\nNow"))
        assertTrue(script.contains("dispatchEvent"))
    }
}
