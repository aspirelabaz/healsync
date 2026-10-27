package com.aspirelab.healsync.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.cos
import kotlin.math.sin

object GlassColors {
    val Base = Color(0xFF070B1A)
    val TextPrimary = Color(0xF2FFFFFF)
    val TextSecondary = Color(0xA6FFFFFF)
    val TextMuted = Color(0x73FFFFFF)

    val Steps = Color(0xFF3DDC97)
    val Heart = Color(0xFFE66767)
    val Sleep = Color(0xFF9085E9)
    val Warning = Color(0xFFF2B33D)

    // Sleep stages, validated as an adjacent categorical set on the dark glass surface.
    val StageAwake = Color(0xFFD95926)
    val StageRem = Color(0xFF9085E9)
    val StageLight = Color(0xFF199E70)
    val StageDeep = Color(0xFF3987E5)
}

private val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Full-screen animated backdrop that every glass surface on top of it blurs. */
@Composable
fun GlassScaffold(content: @Composable BoxScope.() -> Unit) {
    val hazeState = rememberHazeState()
    Box(Modifier.fillMaxSize().background(GlassColors.Base)) {
        LiquidBackground(Modifier.fillMaxSize().hazeSource(hazeState))
        CompositionLocalProvider(LocalHazeState provides hazeState) { content() }
    }
}

/** Slowly drifting colour blobs; soft radial gradients so the glass on top has something to refract. */
@Composable
private fun LiquidBackground(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "liquid")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun blob(color: Color, cx: Float, cy: Float, radius: Float) = drawCircle(
            brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = Offset(cx, cy), radius = radius),
            radius = radius,
            center = Offset(cx, cy),
        )
        blob(Color(0xCC4B3FD8), w * (0.15f + 0.10f * cos(phase)), h * (0.12f + 0.05f * sin(phase)), w * 0.95f)
        blob(Color(0xAA00A3A3), w * (0.95f + 0.08f * sin(phase)), h * (0.38f + 0.06f * cos(phase)), w * 0.85f)
        blob(Color(0x99D0457A), w * (0.20f + 0.12f * sin(phase + 1f)), h * (0.72f + 0.05f * cos(phase)), w * 0.9f)
        blob(Color(0x882463EB), w * (0.85f + 0.06f * cos(phase + 2f)), h * (0.95f + 0.04f * sin(phase)), w * 0.8f)
    }
}

/**
 * Liquid-glass surface for an already clipped and blurred element: a light tint, a sheen fading
 * down from the top edge and a specular rim brighter at the top-left.
 */
private fun Modifier.glass(shape: Shape, tint: Color = Color.White): Modifier = this
    .background(
        Brush.verticalGradient(listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.04f), tint.copy(alpha = 0.07f))),
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.22f)),
        ),
        shape = shape,
    )

/** Blurs whatever [GlassScaffold] draws behind this element (Android 12+, translucent fallback below). */
@Composable
private fun Modifier.hazeGlass(): Modifier {
    val state = LocalHazeState.current ?: return this
    return hazeEffect(state) {
        backgroundColor = GlassColors.Base
        tints = listOf(HazeTint(Color.White.copy(alpha = 0.04f)))
        blurRadius = 28.dp
        noiseFactor = 0.06f
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "press")
    Column(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .hazeGlass()
            .glass(shape)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Round glass button with a springy press, for icons in headers. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "press")
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .hazeGlass()
            .glass(CircleShape)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Pill-shaped glass button; [tint] colours the glass for a primary action. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 450f), label = "press")
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .hazeGlass()
            .glass(shape, tint)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
