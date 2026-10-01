# Navigation Bar Architecture, Styling & Kotlin Conversion Blueprint

This document provides a comprehensive specification, layout blueprint, styling tokens, and Jetpack Compose (Kotlin / Compose Multiplatform) implementation guide inspired by the navigation system in [`src/app/workspace-shell.ts`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-shell.ts).

---

## 1. High-Level Architecture & Layout Overview

The navigation architecture is responsive across three device form factors:
1. **Desktop / Wide Screen (>= 1024px)**: Fixed Left Sidebar navigation drawer ([`workspace-shell.ts:26-57`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-shell.ts#L26-L57)) + Top Workspace Bar with search, currency indicator, notifications, and user avatar ([`workspace-shell.ts:59-83`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-shell.ts#L59-L83)).
2. **Tablet / Medium Screen (768px - 1023px)**: Collapsed Navigation Rail with icons, expandable slide-out modal drawer.
3. **Mobile Screen (< 768px)**: Fixed Bottom Navigation Bar ([`workspace-shell.ts:89-106`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-shell.ts#L89-L106)) + Minimal Top App Bar with hamburger drawer trigger.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ DESKTOP LAYOUT                                                                         │
│ ┌──────────────────────┬─────────────────────────────────────────────────────────────┐ │
│ │ SIDEBAR (240px)      │ TOPBAR (Height: 58-76px)                                    │ │
│ │ ┌──────────────────┐ │ ☰  Overview  Accounts  Transactions  Reports | KES [🔔] [SM]│ │
│ │ │ SM-Intelligence  │ ├─────────────────────────────────────────────────────────────┤ │
│ │ └──────────────────┘ │                                                             │ │
│ │                      │                                                             │ │
│ │ FINANCES             │                      MAIN CONTENT                           │ │
│ │ [🌌] Overview        │                   (NavHost / Screen)                        │ │
│ │ [💳] Accounts        │                                                             │ │
│ │ [🧾] Transactions    │                                                             │ │
│ │ [💸] Cash Flow       │                                                             │ │
│ │ [🛡️] Budgets         │                                                             │ │
│ │ [💼] Investments     │                                                             │ │
│ │ [📐] Analytics       │                                                             │ │
│ │ [📄] Reports         │                                                             │ │
│ │                      │                                                             │ │
│ │ ACCOUNT              │                                                             │ │
│ │ [🔔] Notifications   │                                                             │ │
│ │ [👤] Settings        │                                                             │ │
│ │                      │                                                             │ │
│ │ [SM] Personal User   │                                                             │ │
│ │ [↩] Sign Out         │                                                             │ │
│ └──────────────────────┴─────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────┐
│ MOBILE LAYOUT                        │
│ ┌──────────────────────────────────┐ │
│ │ TOPBAR: ☰ SM-Intelligence KES [SM│ │
│ ├──────────────────────────────────┤ │
│ │                                  │ │
│ │                                  │ │
│ │           MAIN CONTENT           │ │
│ │                                  │ │
│ │                                  │ │
│ ├──────────────────────────────────┤ │
│ │ BOTTOM NAV:                      │ │
│ │ [🌌 Overview] [💳 Accounts]      │ │
│ │ [🧾 Activity] [📄 Reports] [☰]   │ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

---

## 2. Navigation Item Data Model

In the web app, navigation destinations are defined in [`src/app/workspace-shell.ts:5-16`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-shell.ts#L5-L16):

```typescript
// Original Angular TypeScript Definition
export const workspaceLinks = [
  { path: 'dashboard', label: 'Financial Overview', group: '', icon: '🌌' },
  { path: 'accounts', label: 'Accounts', group: 'FINANCES', icon: '💳' },
  { path: 'transactions', label: 'Transactions', group: '', icon: '🧾' },
  { path: 'cashflow', label: 'Cash Flow', group: '', icon: '💸' },
  { path: 'budgets', label: 'Budgets', group: '', icon: '🛡️' },
  { path: 'investments', label: 'Investments', group: '', icon: '💼' },
  { path: 'analysis', label: 'Analytics', group: '', icon: '📐' },
  { path: 'reports', label: 'Reports', group: '', icon: '📄' },
  { path: 'notifications', label: 'Notifications', group: 'ACCOUNT', icon: '🔔' },
  { path: 'settings', label: 'Profile and Settings', group: '', icon: '👤' },
];
```

---

## 3. Styling & Design Tokens

These values originate from [`src/workspace.css`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/workspace.css) and [`src/styles.css`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/styles.css).

### Color Palette

| Token | Light Theme Value | Dark Theme Value | Purpose |
| :--- | :--- | :--- | :--- |
| `sidebar-bg` | `#1e75e9` (Azure Blue) | `#0d1b2a` (Deep Navy) | Sidebar background |
| `sidebar-fg` | `#ffffff` | `#e2e8f0` | Sidebar text & icons |
| `sidebar-item-hover` | `rgba(255, 255, 255, 0.11)` | `rgba(255, 255, 255, 0.08)` | Hover/pressed background |
| `sidebar-item-active-bg`| `#ffffff` | `#1e293b` | Active tab container |
| `sidebar-item-active-fg`| `#1765c8` | `#38bdf8` | Active tab text & icon |
| `topbar-bg` | `#ffffff` | `#0f172a` | Topbar background |
| `topbar-border` | `#dce6f1` | `#1e293b` | Topbar bottom divider |
| `bottom-nav-bg` | `#ffffff` | `#0f172a` | Mobile bottom bar background |
| `bottom-nav-active-fg`| `#2869e8` | `#38bdf8` | Mobile active tab color |
| `bottom-nav-inactive-fg`| `#5c7085` | `#94a3b8` | Mobile inactive tab color |
| `currency-pill-border` | `#dce6f1` | `#334155` | `KES` badge outline |

### Dimensions & Sizing

- **Sidebar Width**: `240px` (Expanded) / `72px` (Collapsed Rail).
- **Topbar Height**: `58px` (Standard Desktop) / `76px` (Wide Screens).
- **Bottom Navigation Height**: `64px` + device safe-area inset.
- **Navigation Item Radius**: `8px` - `10px`.
- **Icon Size**: `22px` - `24px`.
- **Typography**:
  - Topbar Brand: `14px`, Semi-bold (weight 600).
  - Sidebar Labels: `13px`, Medium (weight 500).
  - Navigation Groups (`FINANCES`, `ACCOUNT`): `10px`, Bold (weight 700), `letter-spacing: 1.5px`, uppercase.
  - Mobile Tab Labels: `10px`, Medium.

---

## 4. Jetpack Compose (Kotlin) Implementation

Below is a complete, modular, idiomatic Kotlin implementation using Jetpack Compose (Material 3) and Navigation Component.

### 4.1. Navigation Route Definitions

```kotlin
package com.smartmoney.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavGroup {
    NONE,
    FINANCES,
    ACCOUNT
}

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val group: NavGroup = NavGroup.NONE,
    val showInBottomBar: Boolean = false
) {
    object Dashboard : Screen(
        route = "dashboard",
        title = "Overview",
        icon = Icons.Outlined.Dashboard,
        group = NavGroup.NONE,
        showInBottomBar = true
    )
    object Accounts : Screen(
        route = "accounts",
        title = "Accounts",
        icon = Icons.Outlined.AccountBalance,
        group = NavGroup.FINANCES,
        showInBottomBar = true
    )
    object Transactions : Screen(
        route = "transactions",
        title = "Activity",
        icon = Icons.Outlined.ReceiptLong,
        group = NavGroup.FINANCES,
        showInBottomBar = true
    )
    object CashFlow : Screen(
        route = "cashflow",
        title = "Cash Flow",
        icon = Icons.Outlined.CompareArrows,
        group = NavGroup.FINANCES
    )
    object Budgets : Screen(
        route = "budgets",
        title = "Budgets",
        icon = Icons.Outlined.Savings,
        group = NavGroup.FINANCES
    )
    object Investments : Screen(
        route = "investments",
        title = "Investments",
        icon = Icons.Outlined.WorkOutline,
        group = NavGroup.FINANCES
    )
    object Analytics : Screen(
        route = "analysis",
        title = "Analytics",
        icon = Icons.Outlined.PieChart,
        group = NavGroup.FINANCES
    )
    object Reports : Screen(
        route = "reports",
        title = "Reports",
        icon = Icons.Outlined.Description,
        group = NavGroup.FINANCES,
        showInBottomBar = true
    )
    object Notifications : Screen(
        route = "notifications",
        title = "Notifications",
        icon = Icons.Outlined.Notifications,
        group = NavGroup.ACCOUNT
    )
    object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Outlined.Settings,
        group = NavGroup.ACCOUNT
    )

    companion object {
        val allScreens = listOf(
            Dashboard, Accounts, Transactions, CashFlow, Budgets,
            Investments, Analytics, Reports, Notifications, Settings
        )
        val bottomBarScreens = allScreens.filter { it.showInBottomBar }
    }
}
```

---

### 4.2. Color Tokens (Compose Theme)

```kotlin
package com.smartmoney.ui.theme

import androidx.compose.ui.graphics.Color

object SmartMoneyColors {
    val AzurePrimary = Color(0xFF1E75E9)
    val AzureDark = Color(0xFF1765C8)
    val AzureLight = Color(0xFFEBF3FE)
    val SlateBackground = Color(0xFFF8FBFF)
    val DarkBackground = Color(0xFF0F172A)
    val DarkSurface = Color(0xFF1E293B)
    val BorderLine = Color(0xFFDCE6F1)
    val DarkBorderLine = Color(0xFF334155)
    val TextPrimary = Color(0xFF18334F)
    val TextMuted = Color(0xFF5C7085)
}
```

---

### 4.3. Desktop Sidebar Component

Translates [`src/app/workspace-shell.ts:26-57`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-shell.ts#L26-L57):

```kotlin
package com.smartmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmoney.ui.navigation.NavGroup
import com.smartmoney.ui.navigation.Screen
import com.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun AppSidebar(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(SmartMoneyColors.AzurePrimary)
            .padding(16.dp)
    ) {
        // Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SM",
                    color = SmartMoneyColors.AzurePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "SM-Intelligence",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "SMARTMONEY",
                    color = Color.White.copy(alpha = 0.7f),
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

            Screen.allScreens.forEach { screen ->
                if (screen.group != NavGroup.NONE && screen.group != lastGroup) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = screen.group.name,
                        color = Color.White.copy(alpha = 0.6f),
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
                    onClick = { onNavigate(screen) }
                )
            }
        }

        // User Profile Foot & Sign out
        Divider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("SM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Personal Workspace",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Every shilling in view",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = Icons.Outlined.Logout,
                    contentDescription = "Sign Out",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color.White else Color.Transparent
    val contentColor = if (isSelected) SmartMoneyColors.AzureDark else Color.White

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
```

---

### 4.4. Top App Bar Component

Translates [`src/app/workspace-shell.ts:59-83`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-shell.ts#L59-L83):

```kotlin
package com.smartmoney.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmoney.ui.theme.SmartMoneyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    isMobile: Boolean = false,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = SmartMoneyColors.TextPrimary
            )
        },
        navigationIcon = {
            if (isMobile) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Open navigation menu",
                        tint = SmartMoneyColors.TextPrimary
                    )
                }
            }
        },
        actions = {
            // Currency Indicator Badge
            Box(
                modifier = Modifier
                    .border(1.dp, SmartMoneyColors.BorderLine, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "KES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SmartMoneyColors.TextMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Notifications Icon
            IconButton(onClick = onNotificationsClick) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = SmartMoneyColors.TextMuted
                )
            }

            // User Avatar
            IconButton(onClick = onProfileClick) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(1.dp, SmartMoneyColors.BorderLine, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SmartMoneyColors.AzureDark
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White
        ),
        modifier = modifier
    )
}
```

---

### 4.5. Mobile Bottom Navigation Bar

Translates [`src/app/workspace-shell.ts:89-106`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-shell.ts#L89-L106):

```kotlin
package com.smartmoney.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmoney.ui.navigation.Screen
import com.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun AppBottomNavigationBar(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 6.dp,
        modifier = modifier
    ) {
        Screen.bottomBarScreens.forEach { screen ->
            val isSelected = currentRoute == screen.route

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title
                    )
                },
                label = {
                    Text(text = screen.title, fontSize = 10.sp)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SmartMoneyColors.AzureDark,
                    selectedTextColor = SmartMoneyColors.AzureDark,
                    indicatorColor = SmartMoneyColors.AzureLight,
                    unselectedIconColor = SmartMoneyColors.TextMuted,
                    unselectedTextColor = SmartMoneyColors.TextMuted
                )
            )
        }

        // 5th Menu Button: Opens Drawer
        NavigationBarItem(
            selected = false,
            onClick = onMenuClick,
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "More Menu"
                )
            },
            label = {
                Text(text = "Menu", fontSize = 10.sp)
            },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = SmartMoneyColors.TextMuted,
                unselectedTextColor = SmartMoneyColors.TextMuted
            )
        )
    }
}
```

---

### 4.6. Responsive Scaffold Orchestrator

Putting it all together with Compose `BoxWithConstraints`:

```kotlin
package com.smartmoney.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch
import com.smartmoney.ui.components.*
import com.smartmoney.ui.navigation.Screen

@Composable
fun MainResponsiveShell(
    content: @Composable (PaddingValues) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isDesktop = maxWidth >= 840.dp

        if (isDesktop) {
            // Desktop Layout: Permanent Left Sidebar + Topbar + Content
            Row(modifier = Modifier.fillMaxSize()) {
                AppSidebar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onSignOut = { /* Handle Sign Out */ }
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    AppTopBar(
                        title = Screen.allScreens.find { it.route == currentRoute }?.title ?: "Overview",
                        onMenuClick = {},
                        onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                        onProfileClick = { navController.navigate(Screen.Settings.route) },
                        isMobile = false
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        content(PaddingValues(0.dp))
                    }
                }
            }
        } else {
            // Mobile / Tablet Layout: Modal Navigation Drawer + Bottom Bar
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        AppSidebar(
                            currentRoute = currentRoute,
                            onNavigate = { screen ->
                                scope.launch { drawerState.close() }
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onSignOut = { /* Handle Sign Out */ }
                        )
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        AppTopBar(
                            title = Screen.allScreens.find { it.route == currentRoute }?.title ?: "Overview",
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                            onProfileClick = { navController.navigate(Screen.Settings.route) },
                            isMobile = true
                        )
                    },
                    bottomBar = {
                        AppBottomNavigationBar(
                            currentRoute = currentRoute,
                            onNavigate = { screen ->
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onMenuClick = { scope.launch { drawerState.open() } }
                        )
                    }
                ) { innerPadding ->
                    content(innerPadding)
                }
            }
        }
    }
}
```

---

## 5. Summary of Key Translation Decisions

1. **Angular Router to Jetpack Navigation**: Angular's `routerLinkActive="active"` maps directly to comparing `navBackStackEntry?.destination?.route == screen.route`.
2. **CSS Media Queries to `BoxWithConstraints`**: Mobile vs. desktop transitions (CSS `@media (max-width: 840px)`) map to Compose's `maxWidth >= 840.dp`.
3. **Angular Signals to Compose State**: The `menu()` signal in Angular maps to Compose's `DrawerState` manipulated via coroutines (`scope.launch { drawerState.open() }`).
4. **Strict Color Parity**: Azure blue `#1E75E9` and active contrast `#1765C8` on white cards preserve the exact look and feel across platforms.
