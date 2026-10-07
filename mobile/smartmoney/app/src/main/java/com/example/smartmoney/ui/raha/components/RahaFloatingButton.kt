package com.example.smartmoney.ui.raha.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import kotlinx.coroutines.delay

/**
 * Floating action button for "Raha", the SmartMoney AI Assistant.
 *
 * Anchored on the left side of the screen with a fixed vertical position (stays put when scrolling).
 * Features a circular shape and circular border in all states.
 *
 * Collapses to the left edge of the screen when idle so it never blocks essential cards, lists,
 * or right-side buttons (such as the accounts '+' action button).
 *
 * Interactions:
 * - Tap or swipe right (inward from left edge) while collapsed: smoothly reveals the full circular button.
 * - Tap while expanded: opens the Raha Assistant window.
 * - Swipe left (outward toward left edge) or 4.5s idle timeout: smoothly docks back into the left edge.
 */
@Composable
fun RahaFloatingButton(
    onClick: () -> Unit,
    hasUnreadAlert: Boolean,
    isBottomBarVisible: Boolean = true,
    modifier: Modifier = Modifier,
    onPositioned: ((Rect) -> Unit)? = null
) {
    var isCollapsed by rememberSaveable { mutableStateOf(true) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = LocalDarkTheme.current

    // Auto-dock back to edge after 4.5 seconds of inactivity when expanded
    LaunchedEffect(isCollapsed) {
        if (!isCollapsed) {
            delay(4500L)
            isCollapsed = true
        }
    }

    // Fixed vertical position: stays completely put and does NOT jump down during scrolling
    val bottomOffset = 96.dp

    // Smooth horizontal translation: docked at left edge (-32.dp offset) vs expanded floating (18.dp)
    val horizontalOffset by animateDpAsState(
        targetValue = if (isCollapsed) (-32).dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "RahaFabHorizontalOffset"
    )

    // Center icon inside the visible peek crescent when collapsed
    val iconOffsetX by animateDpAsState(
        targetValue = if (isCollapsed) 13.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "RahaFabIconOffsetX"
    )

    val iconSize by animateDpAsState(
        targetValue = if (isCollapsed) 18.dp else 26.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "RahaFabIconSize"
    )

    val scale = if (isPressed) 0.94f else 1.0f

    val gradientColors = if (isDark) {
        listOf(
            Color(0xFF2E3D31),
            Color(0xFF657166),
            Color(0xFF8FA893)
        )
    } else {
        listOf(
            SmartMoneyColors.AzurePrimary,
            Color(0xFF556356),
            Color(0xFF7E9180)
        )
    }

    Box(
        modifier = modifier
            .padding(bottom = bottomOffset)
            .offset(x = horizontalOffset)
            .scale(scale)
            .size(56.dp)
            .onGloballyPositioned { coordinates ->
                onPositioned?.invoke(coordinates.boundsInWindow())
            }
            .shadow(
                elevation = if (isPressed) 4.dp else if (isCollapsed) 6.dp else 10.dp,
                shape = CircleShape,
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .background(
                brush = Brush.linearGradient(gradientColors),
                shape = CircleShape
            )
            .border(
                width = 1.5.dp,
                color = if (isDark) Color(0xFFDAEBE3).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.7f),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 12f) {
                        // Swiped inward from left edge -> Reveal full button
                        isCollapsed = false
                    } else if (dragAmount < -12f) {
                        // Swiped outward toward left edge -> Collapse to edge
                        isCollapsed = true
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (isCollapsed) {
                        isCollapsed = false // First tap reveals full logo
                    } else {
                        onClick() // Second tap opens Raha assistant
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = if (isCollapsed) "Expand Raha" else "Ask Raha",
            tint = Color.White,
            modifier = Modifier
                .offset(x = iconOffsetX)
                .size(iconSize)
        )

        // Notification / alert badge
        if (hasUnreadAlert) {
            Box(
                modifier = Modifier
                    .size(if (isCollapsed) 10.dp else 13.dp)
                    .align(Alignment.TopEnd)
                    .padding(
                        top = 2.dp,
                        end = if (isCollapsed) 5.dp else 2.dp
                    )
                    .background(Color(0xFFE57373), CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    }
}
