package com.antigravity.wrapper.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.antigravity.wrapper.R

/**
 * Antigravity ("agy") spectrum arch logo rendered from [R.drawable.ic_agy_logo].
 */
@Composable
fun AgyLogo(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(id = R.drawable.ic_agy_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
