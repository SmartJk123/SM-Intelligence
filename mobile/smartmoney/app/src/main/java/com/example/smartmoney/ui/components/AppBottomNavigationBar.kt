package com.example.smartmoney.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.ui.navigation.Screen
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun AppBottomNavigationBar(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    isCollapsed: Boolean = false,
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val navContainerColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
    val selectedCol = if (isDark) Color.White else SmartMoneyColors.AzureDark
    val indicatorCol = if (isDark) SmartMoneyColors.DarkSurfaceElevated else SmartMoneyColors.AzureLight
    val unselectedCol = if (isDark) SmartMoneyColors.PaleMintGreen.copy(alpha = 0.85f) else SmartMoneyColors.TextMuted

    val targetAlpha = when {
        isScrolling -> 0.55f
        isCollapsed -> 0.85f
        else -> 1.0f
    }

    val barAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = if (isScrolling) 150 else 300,
            easing = FastOutSlowInEasing
        ),
        label = "barAlpha"
    )

    val transition = updateTransition(
        targetState = isCollapsed,
        label = "BottomNavMorphTransition"
    )

    val horizontalMargin by transition.animateDp(
        label = "horizontalMargin",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        }
    ) { collapsed ->
        if (collapsed) 28.dp else 0.dp
    }

    val bottomMargin by transition.animateDp(
        label = "bottomMargin",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        }
    ) { collapsed ->
        if (collapsed) 16.dp else 0.dp
    }

    val cornerRadius by transition.animateDp(
        label = "cornerRadius",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        }
    ) { collapsed ->
        if (collapsed) 32.dp else 0.dp
    }

    val barHeight by transition.animateDp(
        label = "barHeight",
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        }
    ) { collapsed ->
        if (collapsed) 60.dp else 76.dp
    }

    val elevation by transition.animateDp(
        label = "elevation",
        transitionSpec = {
            tween(durationMillis = 250)
        }
    ) { collapsed ->
        if (collapsed) 6.dp else 4.dp
    }

    val labelAlpha by transition.animateFloat(
        label = "labelAlpha",
        transitionSpec = {
            tween(durationMillis = 150)
        }
    ) { collapsed ->
        if (collapsed) 0f else 1f
    }

    val shape = RoundedCornerShape(cornerRadius)

    // Outer layout envelope maintains a stable height (76.dp + navigationBarsPadding)
    // to keep Scaffold innerPadding constant and avoid full-screen relayouts.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(76.dp)
            .padding(horizontal = horizontalMargin),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(bottom = bottomMargin)
                .height(barHeight)
                .graphicsLayer { alpha = barAlpha },
            shape = shape,
            color = navContainerColor,
            shadowElevation = elevation,
            tonalElevation = elevation,
            border = if (isDark) {
                BorderStroke(1.dp, SmartMoneyColors.DarkBorderLine)
            } else if (isCollapsed) {
                BorderStroke(1.dp, SmartMoneyColors.PaleSageGreen.copy(alpha = 0.85f))
            } else null
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxSize()
            ) {
                Screen.bottomBarScreens.forEach { screen ->
                    val isSelected = currentRoute == screen.route

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onNavigate(screen) },
                        alwaysShowLabel = !isCollapsed,
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = if (labelAlpha > 0.05f) {
                            {
                                Text(
                                    text = screen.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.graphicsLayer { alpha = labelAlpha }
                                )
                            }
                        } else null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = selectedCol,
                            selectedTextColor = selectedCol,
                            indicatorColor = indicatorCol,
                            unselectedIconColor = unselectedCol,
                            unselectedTextColor = unselectedCol
                        )
                    )
                }
            }
        }
    }
}
