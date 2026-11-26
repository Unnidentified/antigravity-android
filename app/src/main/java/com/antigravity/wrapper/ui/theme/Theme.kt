package com.antigravity.wrapper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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

private fun buildDarkPalette(
    primary: Long,
    onPrimary: Long,
    primaryContainer: Long,
    onPrimaryContainer: Long,
    secondary: Long,
    tertiary: Long,
    bg: Long,
    fg: Long,
    fgMuted: Long,
    cLowest: Long,
    cLow: Long,
    cMid: Long,
    cHigh: Long,
    cHighest: Long,
    outline: Long,
    outlineVariant: Long,
    error: Long = 0xFFF2B8B5L
): ColorScheme = darkColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary),
    onSecondary = Color(onPrimary),
    secondaryContainer = Color(cHigh),
    onSecondaryContainer = Color(fg),
    tertiary = Color(tertiary),
    onTertiary = Color(onPrimary),
    tertiaryContainer = Color(cHigh),
    onTertiaryContainer = Color(fg),
    background = Color(bg),
    onBackground = Color(fg),
    surface = Color(bg),
    onSurface = Color(fg),
    surfaceVariant = Color(cMid),
    onSurfaceVariant = Color(fgMuted),
    surfaceContainerLowest = Color(cLowest),
    surfaceContainerLow = Color(cLow),
    surfaceContainer = Color(cMid),
    surfaceContainerHigh = Color(cHigh),
    surfaceContainerHighest = Color(cHighest),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    error = Color(error),
    onError = Color(0xFF3B0808L),
    errorContainer = Color(0xFF7F1D1DL),
    onErrorContainer = Color(0xFFFEE2E2L)
)

private fun buildLightPalette(
    primary: Long,
    onPrimary: Long,
    primaryContainer: Long,
    onPrimaryContainer: Long,
    secondary: Long,
    tertiary: Long,
    bg: Long,
    fg: Long,
    fgMuted: Long,
    cLowest: Long,
    cLow: Long,
    cMid: Long,
    cHigh: Long,
    cHighest: Long,
    outline: Long,
    outlineVariant: Long,
    error: Long = 0xFFC41E29L
): ColorScheme = lightColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    secondary = Color(secondary),
    onSecondary = Color(0xFFFFFFFFL),
    secondaryContainer = Color(cHigh),
    onSecondaryContainer = Color(fg),
    tertiary = Color(tertiary),
    onTertiary = Color(0xFFFFFFFFL),
    tertiaryContainer = Color(cHigh),
    onTertiaryContainer = Color(fg),
    background = Color(bg),
    onBackground = Color(fg),
    surface = Color(bg),
    onSurface = Color(fg),
    surfaceVariant = Color(cMid),
    onSurfaceVariant = Color(fgMuted),
    surfaceContainerLowest = Color(cLowest),
    surfaceContainerLow = Color(cLow),
    surfaceContainer = Color(cMid),
    surfaceContainerHigh = Color(cHigh),
    surfaceContainerHighest = Color(cHighest),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    error = Color(error),
    onError = Color(0xFFFFFFFFL),
    errorContainer = Color(0xFFFFDAD6L),
    onErrorContainer = Color(0xFF410002L)
)

private val AntigravityDarkColors = buildDarkPalette(
    primary = 0xFFA8C7FAL, onPrimary = 0xFF062E6FL, primaryContainer = 0xFF0842A0L, onPrimaryContainer = 0xFFD3E3FDL,
    secondary = 0xFFC2E7FFL, tertiary = 0xFFC4EED0L,
    bg = 0xFF111318L, fg = 0xFFE3E3E8L, fgMuted = 0xFFC4C6D0L,
    cLowest = 0xFF0C0E13L, cLow = 0xFF171A21L, cMid = 0xFF1B1F27L, cHigh = 0xFF252A34L, cHighest = 0xFF303542L,
    outline = 0xFF44474FL, outlineVariant = 0xFF2D313BL
)

private val PureOledBlackColors = AntigravityDarkColors.copy(
    primary = Color(0xFFE2E8F0L),
    onPrimary = Color(0xFF0B0D12L),
    primaryContainer = Color(0xFF1E222AL),
    onPrimaryContainer = Color(0xFFF8FAFC),
    background = Color(0xFF000000L),
    surface = Color(0xFF000000L),
    surfaceContainerLowest = Color(0xFF000000L),
    surfaceContainerLow = Color(0xFF090A0EL),
    surfaceContainer = Color(0xFF101217L),
    surfaceContainerHigh = Color(0xFF171A20L),
    surfaceContainerHighest = Color(0xFF21252EL)
)

private val AntigravitySlateColors = buildDarkPalette(
    primary = 0xFFE2C290L, onPrimary = 0xFF281D0DL, primaryContainer = 0xFF3E301EL, onPrimaryContainer = 0xFFF5E3C6L,
    secondary = 0xFF93B8E8L, tertiary = 0xFF81C784L,
    bg = 0xFF141518L, fg = 0xFFECEEF2L, fgMuted = 0xFFA0A4AEL,
    cLowest = 0xFF101114L, cLow = 0xFF18191DL, cMid = 0xFF1E2025L, cHigh = 0xFF26282EL, cHighest = 0xFF31343CL,
    outline = 0xFF4A4E58L, outlineVariant = 0xFF2E3138L
)

private val AntigravityLightColors = buildLightPalette(
    primary = 0xFF1A56DBL, onPrimary = 0xFFFFFFFFL, primaryContainer = 0xFFD8E2FFL, onPrimaryContainer = 0xFF001A41L,
    secondary = 0xFF00639AL, tertiary = 0xFF146C2EL,
    bg = 0xFFF8F9FCL, fg = 0xFF191C20L, fgMuted = 0xFF44474EL,
    cLowest = 0xFFFFFFFFL, cLow = 0xFFF2F4F8L, cMid = 0xFFECEEF4L, cHigh = 0xFFE6E8EFL, cHighest = 0xFFE0E2E9L,
    outline = 0xFF74777FL, outlineVariant = 0xFFC4C6D0L
)

private val CatppuccinMochaColors = buildDarkPalette(
    primary = 0xFFCBA6F7L, onPrimary = 0xFF1E1E2EL, primaryContainer = 0xFF453759L, onPrimaryContainer = 0xFFF5E0DCL,
    secondary = 0xFF89B4FAL, tertiary = 0xFFA6E3A1L,
    bg = 0xFF1E1E2EL, fg = 0xFFCDD6F4L, fgMuted = 0xFFBAC2DEL,
    cLowest = 0xFF11111BL, cLow = 0xFF181825L, cMid = 0xFF242436L, cHigh = 0xFF313244L, cHighest = 0xFF45475AL,
    outline = 0xFF585B70L, outlineVariant = 0xFF313244L, error = 0xFFF38BA8L
)

private val CatppuccinLatteColors = buildLightPalette(
    primary = 0xFF7224D6L, onPrimary = 0xFFFFFFFFL, primaryContainer = 0xFFEAD9FFL, onPrimaryContainer = 0xFF2F005CL,
    secondary = 0xFF1252CCL, tertiary = 0xFF256918L,
    bg = 0xFFEFF1F5L, fg = 0xFF4C4F69L, fgMuted = 0xFF515469L,
    cLowest = 0xFFFFFFFFL, cLow = 0xFFE6E9EFL, cMid = 0xFFDCE0E8L, cHigh = 0xFFD4D8E2L, cHighest = 0xFFC8CCD6L,
    outline = 0xFF7B7E91L, outlineVariant = 0xFFD4D8E2L, error = 0xFFB80D32L
)

private val DraculaColors = buildDarkPalette(
    primary = 0xFFBD93F9L, onPrimary = 0xFF211A30L, primaryContainer = 0xFF44375EL, onPrimaryContainer = 0xFFF8F8F2L,
    secondary = 0xFF8BE9FDL, tertiary = 0xFF50FA7BL,
    bg = 0xFF282A36L, fg = 0xFFF8F8F2L, fgMuted = 0xFFB0B8D1L,
    cLowest = 0xFF1E2029L, cLow = 0xFF21222CL, cMid = 0xFF2E3140L, cHigh = 0xFF383C4EL, cHighest = 0xFF44475AL,
    outline = 0xFF6272A4L, outlineVariant = 0xFF3B3F51L, error = 0xFFFF8484L
)

private val NordColors = buildDarkPalette(
    primary = 0xFF8EC7D8L, onPrimary = 0xFF1E2430L, primaryContainer = 0xFF3B4D61L, onPrimaryContainer = 0xFFECEFF4L,
    secondary = 0xFF9FC0DFL, tertiary = 0xFFAEC997L,
    bg = 0xFF2E3440L, fg = 0xFFECEFF4L, fgMuted = 0xFFD8DEE9L,
    cLowest = 0xFF242933L, cLow = 0xFF2A303CL, cMid = 0xFF3B4252L, cHigh = 0xFF434C5EL, cHighest = 0xFF4C566AL,
    outline = 0xFF616E88L, outlineVariant = 0xFF3B4252L, error = 0xFFF9A8B0L
)

private val TokyoNightColors = buildDarkPalette(
    primary = 0xFF7AA2F7L, onPrimary = 0xFF15161EL, primaryContainer = 0xFF29355AL, onPrimaryContainer = 0xFFC0CAF5L,
    secondary = 0xFF7DCFFFL, tertiary = 0xFF9ECE6AL,
    bg = 0xFF1A1B26L, fg = 0xFFC0CAF5L, fgMuted = 0xFF9AA5CEL,
    cLowest = 0xFF13141DL, cLow = 0xFF16161EL, cMid = 0xFF1F2335L, cHigh = 0xFF292E42L, cHighest = 0xFF343B58L,
    outline = 0xFF565F89L, outlineVariant = 0xFF292E42L, error = 0xFFF7768EL
)

private val GruvboxDarkColors = buildDarkPalette(
    primary = 0xFFFABD2FL, onPrimary = 0xFF282828L, primaryContainer = 0xFF504020L, onPrimaryContainer = 0xFFFBF1C7L,
    secondary = 0xFF8DB0A3L, tertiary = 0xFFB8BB26L,
    bg = 0xFF282828L, fg = 0xFFEBDBB2L, fgMuted = 0xFFC8B99EL,
    cLowest = 0xFF1D2021L, cLow = 0xFF242424L, cMid = 0xFF32302FL, cHigh = 0xFF3C3836L, cHighest = 0xFF504945L,
    outline = 0xFF665C54L, outlineVariant = 0xFF3C3836L, error = 0xFFFC7E6EL
)

private val GruvboxLightColors = buildLightPalette(
    primary = 0xFF804C02L, onPrimary = 0xFFFFFFFFL, primaryContainer = 0xFFF3E2B3L, onPrimaryContainer = 0xFF3C2800L,
    secondary = 0xFF065E6FL, tertiary = 0xFF5D5907L,
    bg = 0xFFFBF1C7L, fg = 0xFF3C3836L, fgMuted = 0xFF574E47L,
    cLowest = 0xFFFFF8DDL, cLow = 0xFFF2E5BCL, cMid = 0xFFEBDBB2L, cHigh = 0xFFE2D1A8L, cHighest = 0xFFD5C4A1L,
    outline = 0xFF928374L, outlineVariant = 0xFFD5C4A1L, error = 0xFF9D0006L
)

private val OneDarkProColors = buildDarkPalette(
    primary = 0xFF61AFEFL, onPrimary = 0xFF181A1FL, primaryContainer = 0xFF2C4259L, onPrimaryContainer = 0xFFABB2BFL,
    secondary = 0xFFD18AE6L, tertiary = 0xFF98C379L,
    bg = 0xFF282C34L, fg = 0xFFABB2BFL, fgMuted = 0xFFAAB2C0L,
    cLowest = 0xFF1E222AL, cLow = 0xFF21252BL, cMid = 0xFF2C313CL, cHigh = 0xFF353B45L, cHighest = 0xFF3E4451L,
    outline = 0xFF5C6370L, outlineVariant = 0xFF353B45L, error = 0xFFEF8890L
)

private val SolarizedDarkColors = buildDarkPalette(
    primary = 0xFF6BB4EBL, onPrimary = 0xFF00232CL, primaryContainer = 0xFF0B4666L, onPrimaryContainer = 0xFFEEE8D5L,
    secondary = 0xFF42C7BDL, tertiary = 0xFFA2BA1CL,
    bg = 0xFF002B36L, fg = 0xFFC5D0CDL, fgMuted = 0xFFACBBC0L,
    cLowest = 0xFF001F27L, cLow = 0xFF073642L, cMid = 0xFF093B48L, cHigh = 0xFF0E4453L, cHighest = 0xFF144E5EL,
    outline = 0xFF586E75L, outlineVariant = 0xFF0F4452L, error = 0xFFF58A87L
)

private val SolarizedLightColors = buildLightPalette(
    primary = 0xFF0E6C64L, onPrimary = 0xFFFFFFFFL, primaryContainer = 0xFFD0ECE8L, onPrimaryContainer = 0xFF003733L,
    secondary = 0xFF1368A6L, tertiary = 0xFF566600L,
    bg = 0xFFFDF6E3L, fg = 0xFF3B4D54L, fgMuted = 0xFF4C5E65L,
    cLowest = 0xFFFFFBF0L, cLow = 0xFFF5EEDCL, cMid = 0xFFEEE8D5L, cHigh = 0xFFE8E1CDL, cHighest = 0xFFDFD8C2L,
    outline = 0xFF93A1A1L, outlineVariant = 0xFFE0D8C3L, error = 0xFFB8211EL
)

private val MonokaiProColors = buildDarkPalette(
    primary = 0xFFFFD866L, onPrimary = 0xFF221F22L, primaryContainer = 0xFF4D432AL, onPrimaryContainer = 0xFFFCFCFAL,
    secondary = 0xFF78DCE8L, tertiary = 0xFFA9DC76L,
    bg = 0xFF2D2A2EL, fg = 0xFFFCFCFAL, fgMuted = 0xFFC8C7C7L,
    cLowest = 0xFF1F1D20L, cLow = 0xFF262327L, cMid = 0xFF363338L, cHigh = 0xFF403E41L, cHighest = 0xFF524F53L,
    outline = 0xFF727072L, outlineVariant = 0xFF403E41L, error = 0xFFFF84A3L
)

private val RosePineColors = buildDarkPalette(
    primary = 0xFFEBBCBAL, onPrimary = 0xFF191724L, primaryContainer = 0xFF49333CL, onPrimaryContainer = 0xFFE0DEF4L,
    secondary = 0xFF9CCFD8L, tertiary = 0xFF53A0BFL,
    bg = 0xFF191724L, fg = 0xFFE0DEF4L, fgMuted = 0xFFACA8C8L,
    cLowest = 0xFF12101BL, cLow = 0xFF1F1D2EL, cMid = 0xFF26233AL, cHigh = 0xFF302C47L, cHighest = 0xFF403D52L,
    outline = 0xFF6E6A86L, outlineVariant = 0xFF2D2944L, error = 0xFFEB6F92L
)

private val RosePineDawnColors = buildLightPalette(
    primary = 0xFFA4403EL, onPrimary = 0xFFFFFFFFL, primaryContainer = 0xFFF7E0DEL, onPrimaryContainer = 0xFF4C201FL,
    secondary = 0xFF346973L, tertiary = 0xFF286983L,
    bg = 0xFFFAF4EDL, fg = 0xFF575279L, fgMuted = 0xFF5D5977L,
    cLowest = 0xFFFFFAF3L, cLow = 0xFFF4EDE8L, cMid = 0xFFF2E9E1L, cHigh = 0xFFE8DED5L, cHighest = 0xFFDFDAD9L,
    outline = 0xFF9893A5L, outlineVariant = 0xFFDFDAD9L, error = 0xFFA13B58L
)

private val KanagawaWaveColors = buildDarkPalette(
    primary = 0xFF8AA8E0L, onPrimary = 0xFF16161DL, primaryContainer = 0xFF2D4F67L, onPrimaryContainer = 0xFFDCD7BAL,
    secondary = 0xFFAC96D0L, tertiary = 0xFF98BB6CL,
    bg = 0xFF1F1F28L, fg = 0xFFDCD7BAL, fgMuted = 0xFFB9B9AFL,
    cLowest = 0xFF16161DL, cLow = 0xFF1A1A22L, cMid = 0xFF2A2A37L, cHigh = 0xFF363646L, cHighest = 0xFF46465AL,
    outline = 0xFF54546DL, outlineVariant = 0xFF363646L, error = 0xFFF47E7EL
)

private val EverforestDarkColors = buildDarkPalette(
    primary = 0xFFA7C080L, onPrimary = 0xFF1E2326L, primaryContainer = 0xFF3C4841L, onPrimaryContainer = 0xFFD3C6AAL,
    secondary = 0xFF89C4BCL, tertiary = 0xFF8CC99BL,
    bg = 0xFF2D353BL, fg = 0xFFD3C6AAL, fgMuted = 0xFFBBC6BEL,
    cLowest = 0xFF232A2EL, cLow = 0xFF293136L, cMid = 0xFF343F44L, cHigh = 0xFF3D484DL, cHighest = 0xFF475258L,
    outline = 0xFF7A8478L, outlineVariant = 0xFF3D484DL, error = 0xFFF59FA1L
)

private val AyuMirageColors = buildDarkPalette(
    primary = 0xFFFFCC66L, onPrimary = 0xFF1F2430L, primaryContainer = 0xFF453D29L, onPrimaryContainer = 0xFFCBCCC6L,
    secondary = 0xFF73D0FFL, tertiary = 0xFFBAE67EL,
    bg = 0xFF1F2430L, fg = 0xFFCBCCC6L, fgMuted = 0xFFA6ADB5L,
    cLowest = 0xFF171B24L, cLow = 0xFF1C212BL, cMid = 0xFF242936L, cHigh = 0xFF2D3343L, cHighest = 0xFF384052L,
    outline = 0xFF5C6773L, outlineVariant = 0xFF2D3343L, error = 0xFFFF7A7AL
)

private val GitHubDarkDefaultColors = buildDarkPalette(
    primary = 0xFF58A6FFL, onPrimary = 0xFF0D1117L, primaryContainer = 0xFF1F3B61L, onPrimaryContainer = 0xFFC9D1D9L,
    secondary = 0xFF79C0FFL, tertiary = 0xFF3FB950L,
    bg = 0xFF0D1117L, fg = 0xFFE6EDF3L, fgMuted = 0xFF9CA5B0L,
    cLowest = 0xFF090C10L, cLow = 0xFF161B22L, cMid = 0xFF1C2128L, cHigh = 0xFF21262DL, cHighest = 0xFF30363DL,
    outline = 0xFF484F58L, outlineVariant = 0xFF30363DL, error = 0xFFFA6860L
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

data class DiffColors(
    val addedRowBackground: Color,
    val removedRowBackground: Color,
    val addedAccent: Color,
    val removedAccent: Color
)

fun diffColors(isDark: Boolean): DiffColors = if (isDark) {
    DiffColors(
        addedRowBackground = Color(0xFF153823L),
        removedRowBackground = Color(0xFF3E181CL),
        addedAccent = Color(0xFF4ADE80L),
        removedAccent = Color(0xFFFF9494L)
    )
} else {
    DiffColors(
        addedRowBackground = Color(0xFFE6F4EAL),
        removedRowBackground = Color(0xFFFCE8E6L),
        addedAccent = Color(0xFF0E682BL),
        removedAccent = Color(0xFFB31919L)
    )
}

fun staticColorSchemeFor(
    themeMode: ThemeMode,
    systemDark: Boolean = true
): ColorScheme = when (themeMode) {
    ThemeMode.SYSTEM -> if (systemDark) AntigravityDarkColors else AntigravityLightColors
    ThemeMode.ANTIGRAVITY_DARK -> AntigravityDarkColors
    ThemeMode.PURE_OLED_BLACK -> PureOledBlackColors
    ThemeMode.ANTIGRAVITY_LIGHT -> AntigravityLightColors
    ThemeMode.SLATE -> AntigravitySlateColors
    ThemeMode.CATPPUCCIN_MOCHA -> CatppuccinMochaColors
    ThemeMode.CATPPUCCIN_LATTE -> CatppuccinLatteColors
    ThemeMode.DRACULA -> DraculaColors
    ThemeMode.NORD -> NordColors
    ThemeMode.TOKYO_NIGHT -> TokyoNightColors
    ThemeMode.GRUVBOX_DARK -> GruvboxDarkColors
    ThemeMode.GRUVBOX_LIGHT -> GruvboxLightColors
    ThemeMode.ONE_DARK_PRO -> OneDarkProColors
    ThemeMode.SOLARIZED_DARK -> SolarizedDarkColors
    ThemeMode.SOLARIZED_LIGHT -> SolarizedLightColors
    ThemeMode.MONOKAI_PRO -> MonokaiProColors
    ThemeMode.ROSE_PINE -> RosePineColors
    ThemeMode.ROSE_PINE_DAWN -> RosePineDawnColors
    ThemeMode.KANAGAWA_WAVE -> KanagawaWaveColors
    ThemeMode.EVERFOREST_DARK -> EverforestDarkColors
    ThemeMode.AYU_MIRAGE -> AyuMirageColors
    ThemeMode.GITHUB_DARK_DEFAULT -> GitHubDarkDefaultColors
}

@Composable
fun isEffectiveDarkTheme(themeMode: ThemeMode): Boolean {
    return if (themeMode == ThemeMode.SYSTEM) {
        isSystemInDarkTheme()
    } else {
        themeMode.isDark
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

    val colorScheme = if (
        themeMode == ThemeMode.SYSTEM &&
        dynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        staticColorSchemeFor(themeMode, systemDark = darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AntigravityTypography,
        shapes = AntigravityShapes,
        content = content
    )
}
