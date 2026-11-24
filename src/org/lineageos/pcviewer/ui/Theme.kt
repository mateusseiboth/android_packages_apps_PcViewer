package org.lineageos.pcviewer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Material 3 with the system's dynamic colors, like the other desktop apps. */
@Composable
fun ViewerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = dynamicDarkColorScheme(context).takeIf { dark } ?: dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
