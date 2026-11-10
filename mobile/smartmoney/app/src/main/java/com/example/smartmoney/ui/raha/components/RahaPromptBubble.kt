package com.example.smartmoney.ui.raha.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

/**
 * A sleek, proactive speech bubble that emerges from the Raha AI floating icon
 * to gently prompt the user with contextual questions and invitations to use the assistant.
 */
@Composable
fun RahaPromptBubble(
    text: String?,
    isVisible: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    if (text.isNullOrBlank()) return

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(260)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { it / 2 } +
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialScale = 0.82f
                ),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.82f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(bottom = 160.dp, start = 16.dp, end = 32.dp)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 290.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomEnd = 18.dp,
                            bottomStart = 4.dp
                        ),
                        spotColor = Color.Black.copy(alpha = 0.35f)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ),
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = 18.dp,
                    bottomStart = 4.dp
                ),
                color = if (isDark) SmartMoneyColors.DeepNavy else Color.White,
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        if (isDark) {
                            listOf(
                                SmartMoneyColors.AzurePrimary.copy(alpha = 0.85f),
                                SmartMoneyColors.PowderBlue.copy(alpha = 0.65f)
                            )
                        } else {
                            listOf(
                                SmartMoneyColors.AzurePrimary,
                                SmartMoneyColors.PowderBlue.copy(alpha = 0.75f)
                            )
                        }
                    )
                )
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                color = SmartMoneyColors.AzurePrimary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isDark) SmartMoneyColors.PowderBlue else SmartMoneyColors.AzurePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                            color = if (isDark) Color.White else SmartMoneyColors.TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss prompt",
                            tint = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
