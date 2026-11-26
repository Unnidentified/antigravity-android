package com.antigravity.wrapper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun effortLevelCount(effort: String): Int = when (effort.trim().lowercase()) {
    "minimal" -> 1
    "low", "fast" -> 1
    "medium" -> 2
    "high" -> 3
    "extra", "max" -> 4
    else -> 3
}

/**
 * Four ascending vertical effort signal bars that fill from 1..4 based on reasoning level.
 */
@Composable
fun EffortBarsIcon(
    activeBars: Int,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
    modifier: Modifier = Modifier.size(16.dp)
) {
    val clamped = activeBars.coerceIn(0, 4)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barCount = 4
        val gap = w * 0.12f
        val barW = (w - gap * (barCount - 1)) / barCount
        val radius = CornerRadius(barW * 0.35f, barW * 0.35f)
        val heightFractions = floatArrayOf(0.42f, 0.60f, 0.78f, 0.96f)
        for (i in 0 until barCount) {
            val barH = h * heightFractions[i]
            val x = i * (barW + gap)
            val y = h - barH
            drawRoundRect(
                color = if (i < clamped) activeColor else inactiveColor,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = radius
            )
        }
    }
}
