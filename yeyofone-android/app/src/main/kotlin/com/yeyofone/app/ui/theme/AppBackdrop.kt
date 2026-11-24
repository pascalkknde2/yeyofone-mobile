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

/**
 * Full-screen backdrop behind the app's pages. Light mode shows the blue wave artwork; dark
 * mode keeps the plain slate fill. Pages draw on top of it with transparent containers.
 */
@Composable
fun AppBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(BackgroundGray)) {
        if (!Palette.isDark) {
            Image(
                painter = painterResource(R.drawable.app_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        content()
    }
}
