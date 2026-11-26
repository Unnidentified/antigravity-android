package com.antigravity.wrapper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wrapper.data.ThemeMode

private val AntigravityDarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFC2E7FF),
    onSecondary = Color(0xFF003355),
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFC2E7FF),
    tertiary = Color(0xFFC4EED0),
    onTertiary = Color(0xFF0A3818),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = Color(0xFFC4EED0),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E3E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E3E8),
    surfaceVariant = Color(0xFF1E222B),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1B1F27),
    surfaceContainerHigh = Color(0xFF252A34),
    surfaceContainerHighest = Color(0xFF303542),
    outline = Color(0xFF44474F),
    outlineVariant = Color(0xFF2D313B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val AntigravityAmoledColors = AntigravityDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0B0E),
    surfaceContainer = Color(0xFF101218),
    surfaceContainerHigh = Color(0xFF181B22),
    surfaceContainerHighest = Color(0xFF222630)
)

// Cool charcoal slate + warm sand-gold palette inspired by T3 Code dark theme
private val AntigravitySlateColors = darkColorScheme(
    primary = Color(0xFFE2C290),
    onPrimary = Color(0xFF281D0D),
    primaryContainer = Color(0xFF3E301E),
    onPrimaryContainer = Color(0xFFF5E3C6),
    secondary = Color(0xFF93B8E8),
    onSecondary = Color(0xFF0E2540),
    secondaryContainer = Color(0xFF233245),
    onSecondaryContainer = Color(0xFFD0E3FC),
    tertiary = Color(0xFF81C784),
    onTertiary = Color(0xFF0D2E12),
    tertiaryContainer = Color(0xFF1B3B20),
    onTertiaryContainer = Color(0xFFC8E6C9),
    background = Color(0xFF141518),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF141518),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF23252A),
    onSurfaceVariant = Color(0xFFA0A4AE),
    surfaceContainerLowest = Color(0xFF101114),
    surfaceContainerLow = Color(0xFF18191D),
    surfaceContainer = Color(0xFF1E2025),
    surfaceContainerHigh = Color(0xFF26282E),
    surfaceContainerHighest = Color(0xFF31343C),
    outline = Color(0xFF4A4E58),
    outlineVariant = Color(0xFF2E3138),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

private val AntigravityLightColors = lightColorScheme(
    primary = Color(0xFF1A56DB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF00639A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE5FF),
    onSecondaryContainer = Color(0xFF001D32),
    tertiary = Color(0xFF146C2E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4EED0),
    onTertiaryContainer = Color(0xFF072711),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE1E4EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F8),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EF),
    surfaceContainerHighest = Color(0xFFE0E2E9),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0)
)

private val AntigravityTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val AntigravityShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun isEffectiveDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK, ThemeMode.SLATE, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
    }
}

@Composable
fun AntigravityTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val darkTheme = isEffectiveDarkTheme(themeMode)
    val context = LocalContext.current

    val colorScheme = when {
        themeMode == ThemeMode.SLATE -> AntigravitySlateColors
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamicScheme = if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
            if (themeMode == ThemeMode.AMOLED) {
                dynamicScheme.copy(
                    background = Color(0xFF000000),
                    surface = Color(0xFF000000),
                    surfaceContainerLowest = Color(0xFF000000),
                    surfaceContainerLow = Color(0xFF0A0B0E),
                    surfaceContainer = Color(0xFF101218)
                )
            } else {
                dynamicScheme
            }
        }
        themeMode == ThemeMode.AMOLED -> AntigravityAmoledColors
        darkTheme -> AntigravityDarkColors
        else -> AntigravityLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AntigravityTypography,
        shapes = AntigravityShapes,
        content = content
    )
}
