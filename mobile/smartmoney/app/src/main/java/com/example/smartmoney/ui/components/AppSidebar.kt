
package com.example.smartmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.ui.navigation.NavGroup
import com.example.smartmoney.ui.navigation.Screen
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun AppSidebar(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val sidebarBg = if (isDark) SmartMoneyColors.DeepNavy else SmartMoneyColors.AzurePrimary
    val brandBg = if (isDark) SmartMoneyColors.DarkSurface else Color.White
    val brandLogoColor = if (isDark) SmartMoneyColors.DarkActiveCyan else SmartMoneyColors.AzurePrimary
    val brandTitleColor = if (isDark) SmartMoneyColors.DarkTextPrimary else Color.White
    val brandSubColor = if (isDark) SmartMoneyColors.DarkInactive else Color.White.copy(alpha = 0.7f)
    val groupHeadingColor = if (isDark) SmartMoneyColors.DarkInactive else Color.White.copy(alpha = 0.6f)
    val dividerColor = if (isDark) SmartMoneyColors.DarkBorderLine else Color.White.copy(alpha = 0.2f)
    val profileAvatarBg = if (isDark) SmartMoneyColors.DarkSurface else Color.White.copy(alpha = 0.2f)
    val profileAvatarColor = if (isDark) SmartMoneyColors.DarkActiveCyan else Color.White
    val profileTitleColor = if (isDark) SmartMoneyColors.DarkTextPrimary else Color.White
    val profileSubColor = if (isDark) SmartMoneyColors.DarkInactive else Color.White.copy(alpha = 0.7f)
    val logoutIconColor = if (isDark) SmartMoneyColors.DarkTextPrimary else Color.White

    Column(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(sidebarBg)
            .padding(16.dp)
    ) {
        // Brand Header (clicking navigates to Overview)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNavigate(Screen.Dashboard) }
                .padding(bottom = 20.dp, top = 8.dp)
        ) {
            SystemLogo(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "SM-Intelligence",
                    color = brandTitleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "SMARTMONEY",
                    color = brandSubColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }

        // Navigation Items (Grouped)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            var lastGroup = NavGroup.NONE
            val sidebarScreens = remember { Screen.allScreens.filter { it != Screen.Menu } }

            sidebarScreens.forEach { screen ->
                if (screen.group != NavGroup.NONE && screen.group != lastGroup) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = screen.group.name,
                        color = groupHeadingColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    lastGroup = screen.group
                }

                val isSelected = currentRoute == screen.route

                SidebarItem(
                    screen = screen,
                    isSelected = isSelected,
                    isDark = isDark,
                    onClick = { onNavigate(screen) }
                )
            }
        }

        // User Profile Foot & Sign out
        HorizontalDivider(color = dividerColor, modifier = Modifier.padding(vertical = 12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigate(Screen.Settings) }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(profileAvatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SM",
                        color = profileAvatarColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Personal Workspace",
                        color = profileTitleColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Settings & Preferences",
                        color = profileSubColor,
                        fontSize = 10.sp
                    )
                }
            }
            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = "Sign Out",
                    tint = logoutIconColor
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    screen: Screen,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) {
        if (isDark) SmartMoneyColors.DarkSurface else Color.White
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        if (isDark) Color.White else SmartMoneyColors.AzureDark
    } else {
        if (isDark) SmartMoneyColors.DarkInactive.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = screen.title,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
