package com.example.smartmoney.ui.raha.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.pow

// Premium Apple-style fluid easing curves
private val AppleOpenEasing = CubicBezierEasing(0.16f, 1.0f, 0.30f, 1.0f)
private val AppleCloseEasing = CubicBezierEasing(0.40f, 0.0f, 0.20f, 1.0f)

/**
 * Apple/macOS Genie-inspired transition container for the Raha AI Chat interface.
 *
 * Animates the chat window as an asymmetric fluid funnel expanding out of and
 * collapsing back into the exact recorded screen coordinates of the AI icon.
 *
 * Characteristics:
 * - Single normalized Animatable progress: 0f (collapsed) <-> 1f (expanded).
 * - Asymmetric Bezier funnel deformation during opening and closing.
 * - Hardware-accelerated draw-phase execution via graphicsLayer for 60/120 FPS.
 * - Progressive content fade-in and dissolve to avoid warped text.
 * - Instant reversible interruption handling with zero state jumps.
 */
@Composable
fun AiChatTransition(
    isOpen: Boolean,
    originRect: Rect,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (contentAlpha: Float) -> Unit
) {
    val progress = remember { Animatable(if (isOpen) 1f else 0f) }
    var containerBoundsInWindow by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 480, easing = AppleOpenEasing)
            )
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 360, easing = AppleCloseEasing)
            )
        }
    }

    // Skip drawing & composition when fully collapsed and closed
    if (progress.value <= 0.001f && !isOpen) {
        return
    }

    val currentProgress = progress.value
    val scrimAlpha = (currentProgress * 0.48f).coerceIn(0f, 0.48f)
    val contentAlpha = ((currentProgress - 0.22f) / 0.78f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerBoundsInWindow = coordinates.boundsInWindow()
            }
    ) {
        // 1. Ambient Background Scrim (Fades progressively with progress)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        // 2. Asymmetric Fluid Genie Window Envelope
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fullW = size.width
                    val fullH = size.height

                    // Compute relative icon bounds inside this container
                    val hasMeasured = originRect != Rect.Zero && containerBoundsInWindow != Rect.Zero
                    val relLeft = if (hasMeasured) {
                        (originRect.left - containerBoundsInWindow.left).coerceIn(0f, fullW)
                    } else {
                        18.dp.toPx()
                    }
                    val relTop = if (hasMeasured) {
                        (originRect.top - containerBoundsInWindow.top).coerceIn(0f, fullH)
                    } else {
                        fullH - 152.dp.toPx()
                    }
                    val relW = if (hasMeasured && originRect.width > 0f) originRect.width else 56.dp.toPx()
                    val relH = if (hasMeasured && originRect.height > 0f) originRect.height else 56.dp.toPx()

                    shape = GenieShape(
                        progress = currentProgress,
                        iconRect = Rect(
                            left = relLeft,
                            top = relTop,
                            right = relLeft + relW,
                            bottom = relTop + relH
                        )
                    )
                    clip = true
                }
        ) {
            content(contentAlpha)
        }
    }
}

/**
 * Custom hardware-accelerated Shape that constructs the asymmetric Genie Bezier funnel path.
 */
private class GenieShape(
    private val progress: Float,
    private val iconRect: Rect
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val fullWidth = size.width
        val fullHeight = size.height

        if (progress >= 0.999f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }

        // Non-linear lead/lag for fluid siphon mechanics
        // The bottom leads downward into the icon; the top expands upwards first
        val pBottom = progress.pow(1.35f).coerceIn(0f, 1f)
        val pTop = progress.pow(0.70f).coerceIn(0f, 1f)

        val iconCenterX = iconRect.center.x.coerceIn(0f, fullWidth)
        val iconCenterY = iconRect.center.y.coerceIn(0f, fullHeight)
        val iconW = iconRect.width.coerceAtLeast(36f)
        val iconH = iconRect.height.coerceAtLeast(36f)

        val fullCenterX = fullWidth / 2f

        // Dynamic boundaries
        val curTop = lerp(iconCenterY - iconH / 2f, 0f, pTop)
        val curBottom = lerp(iconCenterY + iconH / 2f, fullHeight, pBottom)

        val topW = lerp(iconW, fullWidth, pTop)
        val bottomW = lerp(iconW, fullWidth, pBottom)

        val topCenterX = lerp(iconCenterX, fullCenterX, pTop)
        val bottomCenterX = lerp(iconCenterX, fullCenterX, pBottom)

        val tlX = (topCenterX - topW / 2f).coerceAtLeast(0f)
        val trX = (topCenterX + topW / 2f).coerceAtMost(fullWidth)
        val blX = (bottomCenterX - bottomW / 2f).coerceAtLeast(0f)
        val brX = (bottomCenterX + bottomW / 2f).coerceAtMost(fullWidth)

        // Waist pinch factor creates the iconic curved Genie siphon neck during mid-flight
        val stretch = 4f * progress * (1f - progress)
        val waistFactor = stretch * 0.22f
        val heightSpan = (curBottom - curTop).coerceAtLeast(1f)
        val avgWidth = ((trX - tlX) + (brX - blX)) * 0.5f
        val pinch = avgWidth * waistFactor

        val path = Path().apply {
            moveTo(tlX, curTop)
            lineTo(trX, curTop)

            // Right curved edge (Genie siphon curve bows inward towards center)
            cubicTo(
                x1 = (trX + (brX - trX) * 0.38f - pinch).coerceIn(0f, fullWidth),
                y1 = curTop + heightSpan * 0.38f,
                x2 = (trX + (brX - trX) * 0.78f - pinch * 0.65f).coerceIn(0f, fullWidth),
                y2 = curTop + heightSpan * 0.78f,
                x3 = brX,
                y3 = curBottom
            )

            lineTo(blX, curBottom)

            // Left curved edge (Genie siphon curve bows inward towards center)
            cubicTo(
                x1 = (blX + (tlX - blX) * 0.22f + pinch * 0.65f).coerceIn(0f, fullWidth),
                y1 = curTop + heightSpan * 0.78f,
                x2 = (blX + (tlX - blX) * 0.62f + pinch).coerceIn(0f, fullWidth),
                y2 = curTop + heightSpan * 0.38f,
                x3 = tlX,
                y3 = curTop
            )
            close()
        }

        return Outline.Generic(path)
    }
}
