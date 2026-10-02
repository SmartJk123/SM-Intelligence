package com.example.smartmoney.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.smartmoney.ui.theme.EnergyTheme
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    userName: String = "User",
    profileBitmap: ImageBitmap? = null,
    isOverview: Boolean = false,
    canNavigateBack: Boolean = false,
    onBackClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    unreadNotificationCount: Int = 0,
    containerColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val titleColor = if (isOverview) (if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen) else (if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary)
    val borderCol = if (isOverview) (if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen) else (if (isDark) SmartMoneyColors.DarkBorderLine else SmartMoneyColors.BorderLine)
    val textMutedCol = if (isOverview) (if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.DarkSlateGreen) else (if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted)
    val actionIconColor = if (isDark) Color.White else (if (isOverview) SmartMoneyColors.DarkSlateGreen else SmartMoneyColors.TextPrimary)

    TopAppBar(
        title = {
            AnimatedContent(
                targetState = isOverview to title,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "top_bar_title_anim"
            ) { (overview, pageTitle) ->
                if (overview) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onProfileClick
                        )
                    ) {
                        UserProfileAvatar(
                            bitmap = profileBitmap,
                            userName = userName,
                            modifier = Modifier.size(38.dp),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Welcome back,",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = textMutedCol,
                                lineHeight = 13.sp
                            )
                            Text(
                                text = userName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = pageTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = titleColor
                    )
                }
            }
        },
        actions = {
            // Currency Indicator Badge with solid, 100% opaque background
            Box(
                modifier = Modifier
                    .background(
                        color = if (isDark) SmartMoneyColors.DarkSurfaceElevated else Color.White,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "KES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else (if (isOverview) SmartMoneyColors.DarkSlateGreen else SmartMoneyColors.TextPrimary)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Notifications Icon
            IconButton(onClick = onNotificationsClick) {
                Box {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = actionIconColor
                    )
                    if (unreadNotificationCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFFE53935), CircleShape)
                                .align(Alignment.TopEnd)
                        )
                    }
                }
            }

            // Settings Icon
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = actionIconColor
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = titleColor,
            titleContentColor = titleColor,
            actionIconContentColor = actionIconColor
        ),
        modifier = modifier
    )
}
