package com.antigravity.wrapper.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.antigravity.wrapper.R

/**
 * Monochrome Antigravity ("agy") arch logo rendered from [R.drawable.ic_agy_logo].
 * Uses pure monochrome white with tonal shading on dark surfaces, and modulates
 * onto [MaterialTheme.colorScheme.onSurface] on light surfaces for WCAG legibility.
 */
@Composable
fun AgyLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val onSurface = MaterialTheme.colorScheme.onSurface
    val colorFilter = if (isDarkSurface) {
        null
    } else {
        ColorFilter.tint(color = onSurface, blendMode = BlendMode.Modulate)
    }

    Image(
        painter = painterResource(id = R.drawable.ic_agy_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        colorFilter = colorFilter,
        modifier = modifier
    )
}
