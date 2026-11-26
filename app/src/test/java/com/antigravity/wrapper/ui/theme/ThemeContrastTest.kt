package com.antigravity.wrapper.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.antigravity.wrapper.data.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ThemeContrastTest {

    private fun srgbChannelToLinear(channel: Float): Double {
        return if (channel <= 0.03928f) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun wcagLuminance(color: Color): Double {
        val r = srgbChannelToLinear(color.red)
        val g = srgbChannelToLinear(color.green)
        val b = srgbChannelToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = wcagLuminance(a)
        val lb = wcagLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertContrastAA(
        themeLabel: String,
        pairName: String,
        fg: Color,
        bg: Color,
        minRatio: Double = 4.5
    ) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$themeLabel [$pairName] contrast ${"%.2f".format(ratio)}:1 is below $minRatio:1",
            ratio >= minRatio
        )
    }

    private fun verifySchemeAndDiffs(label: String, scheme: ColorScheme, isDark: Boolean) {
        val surfaces = listOf(
            "background" to scheme.background,
            "surface" to scheme.surface,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest
        )

        surfaces.forEach { (surfaceName, surfaceColor) ->
            assertContrastAA(label, "onSurface/$surfaceName", scheme.onSurface, surfaceColor)
            assertContrastAA(label, "onSurfaceVariant/$surfaceName", scheme.onSurfaceVariant, surfaceColor)
        }
        assertContrastAA(label, "onBackground/background", scheme.onBackground, scheme.background)

        val accentSurfaces = surfaces.filterNot { it.first == "surfaceContainerHighest" }
        accentSurfaces.forEach { (surfaceName, surfaceColor) ->
            assertContrastAA(label, "primary/$surfaceName", scheme.primary, surfaceColor)
            assertContrastAA(label, "secondary/$surfaceName", scheme.secondary, surfaceColor)
            assertContrastAA(label, "tertiary/$surfaceName", scheme.tertiary, surfaceColor)
            assertContrastAA(label, "error/$surfaceName", scheme.error, surfaceColor)
        }

        assertContrastAA(label, "onPrimary/primary", scheme.onPrimary, scheme.primary)
        assertContrastAA(label, "onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
        assertContrastAA(label, "onSecondary/secondary", scheme.onSecondary, scheme.secondary)
        assertContrastAA(label, "onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer)
        assertContrastAA(label, "onTertiary/tertiary", scheme.onTertiary, scheme.tertiary)
        assertContrastAA(label, "onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer)
        assertContrastAA(label, "onError/error", scheme.onError, scheme.error)
        assertContrastAA(label, "onErrorContainer/errorContainer", scheme.onErrorContainer, scheme.errorContainer)

        val diffPalette = diffColors(isDark)
        assertContrastAA(label, "onSurface/diffAddedRow", scheme.onSurface, diffPalette.addedRowBackground)
        assertContrastAA(label, "onSurface/diffRemovedRow", scheme.onSurface, diffPalette.removedRowBackground)
        assertContrastAA(label, "diffAddedAccent/surfaceContainerLow", diffPalette.addedAccent, scheme.surfaceContainerLow)
        assertContrastAA(label, "diffAddedAccent/surfaceContainer", diffPalette.addedAccent, scheme.surfaceContainer)
        assertContrastAA(label, "diffAddedAccent/diffAddedRow", diffPalette.addedAccent, diffPalette.addedRowBackground)
        assertContrastAA(label, "diffRemovedAccent/surfaceContainerLow", diffPalette.removedAccent, scheme.surfaceContainerLow)
        assertContrastAA(label, "diffRemovedAccent/surfaceContainer", diffPalette.removedAccent, scheme.surfaceContainer)
        assertContrastAA(label, "diffRemovedAccent/diffRemovedRow", diffPalette.removedAccent, diffPalette.removedRowBackground)
    }

    @Test
    fun allStaticThemesAndDiffPalettesMeetWcagAAContrast() {
        ThemeMode.entries.forEach { mode ->
            if (mode == ThemeMode.SYSTEM) {
                verifySchemeAndDiffs("System (Dark)", staticColorSchemeFor(mode, systemDark = true), isDark = true)
                verifySchemeAndDiffs("System (Light)", staticColorSchemeFor(mode, systemDark = false), isDark = false)
            } else {
                verifySchemeAndDiffs(mode.label, staticColorSchemeFor(mode, systemDark = mode.isDark), isDark = mode.isDark)
            }
        }
    }
}
