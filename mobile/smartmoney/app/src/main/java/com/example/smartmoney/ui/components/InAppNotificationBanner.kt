package com.example.smartmoney.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Notification
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

/**
 * Host composable for displaying an in-app notification banner that drops down from the top
 * of the screen with smooth spring animations, displays for a few seconds, and supports
 * tap-to-view and swipe-to-dismiss.
 */
@Composable
fun InAppNotificationBannerHost(
    bannerNotification: Notification?,
    onDismiss: () -> Unit,
    onClick: (Notification) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = bannerNotification != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(animationSpec = tween(durationMillis = 250)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ) + fadeOut(animationSpec = tween(durationMillis = 200)),
        modifier = modifier
    ) {
        if (bannerNotification != null) {
            InAppNotificationBannerCard(
                notification = bannerNotification,
                onDismiss = onDismiss,
                onClick = { onClick(bannerNotification) }
            )
        }
    }
}

/**
 * Visual card for the in-app notification banner.
 */
@Composable
fun InAppNotificationBannerCard(
    notification: Notification,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val isCredit = notification.type.equals("CREDIT", ignoreCase = true)
    val isDebit = notification.type.equals("DEBIT", ignoreCase = true)

    val containerColor = if (isDark) {
        SmartMoneyColors.DarkSurfaceElevated
    } else {
        Color.White
    }

    val borderColor = if (isDark) {
        if (isCredit) Color(0xFF225B36) else if (isDebit) Color(0xFF5E2727) else Color(0xFF2E3D32)
    } else {
        if (isCredit) SmartMoneyColors.PaleMintGreen else if (isDebit) SmartMoneyColors.LightSalmon else SmartMoneyColors.BorderLine
    }

    val iconBg = if (isDark) {
        if (isCredit) Color(0xFF143823) else if (isDebit) Color(0xFF4A1818) else SmartMoneyColors.DarkSlateGreen.copy(alpha = 0.4f)
    } else {
        if (isCredit) SmartMoneyColors.PaleMintGreen else if (isDebit) SmartMoneyColors.LightSalmon else MaterialTheme.colorScheme.primaryContainer
    }

    val iconTint = if (isDark) {
        if (isCredit) Color(0xFF4ADE80) else if (isDebit) Color(0xFFFF6B6B) else Color.White
    } else {
        if (isCredit) Color(0xFF1B6A3E) else if (isDebit) Color(0xFF992B1C) else MaterialTheme.colorScheme.onPrimaryContainer
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -12f) {
                        onDismiss()
                    }
                }
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction / Notification Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isCredit -> Icons.Default.ArrowUpward
                            isDebit -> Icons.Default.ArrowDownward
                            else -> Icons.Default.Notifications
                        },
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Notification Title, Message and Time Tag
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notification.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0xFF1E3225) else SmartMoneyColors.PaleMintGreen.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "Just now",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFF4ADE80) else SmartMoneyColors.DarkSlateGreen,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.5.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Dismiss (X) Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss banner",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
