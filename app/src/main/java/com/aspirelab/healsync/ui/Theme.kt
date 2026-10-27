package com.aspirelab.healsync.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aspirelab.healsync.ui.glass.GlassColors

/** The liquid-glass design is built for a dark backdrop, so the app always uses this dark scheme. */
@Composable
fun HealSyncTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF8FB8FF),
        background = GlassColors.Base,
        surface = GlassColors.Base,
        onBackground = GlassColors.TextPrimary,
        onSurface = GlassColors.TextPrimary,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
