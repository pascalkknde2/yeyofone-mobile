package com.yeyofone.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yeyofone.app.R

/** Opacity of the wave artwork over the dark fill; full strength in light mode. */
private const val DARK_BACKDROP_ALPHA = 0.25f

/**
 * Full-screen backdrop behind the app's pages: the blue wave artwork over the plain fill, dimmed
 * in dark mode. Pages draw on top of it with transparent containers.
 */
@Composable
fun AppBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(BackgroundGray)) {
        Image(
            painter = painterResource(R.drawable.app_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = if (Palette.isDark) DARK_BACKDROP_ALPHA else 1f,
        )
        content()
    }
}
