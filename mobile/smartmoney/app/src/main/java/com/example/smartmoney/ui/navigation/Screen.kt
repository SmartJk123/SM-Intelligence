package com.example.smartmoney.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkOutline
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
        title = "Transactions",
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
        group = NavGroup.FINANCES,
        showInBottomBar = true
    )
    object CashFlow : Screen(
        route = "cashflow",
        title = "Cash Flow",
        icon = Icons.AutoMirrored.Outlined.CompareArrows,
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
        group = NavGroup.FINANCES
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
    object Menu : Screen(
        route = "menu",
        title = "More",
        icon = Icons.Outlined.Menu,
        group = NavGroup.NONE,
        showInBottomBar = true
    )
    object Invoice : Screen(
        route = "invoice",
        title = "Add Invoice",
        icon = Icons.AutoMirrored.Outlined.ReceiptLong
    )

    companion object {
        val allScreens: List<Screen> by lazy {
            listOf(
                Dashboard, Accounts, Transactions, CashFlow, Budgets,
                Investments, Analytics, Reports, Notifications, Settings, Menu, Invoice
            )
        }
        val bottomBarScreens: List<Screen> by lazy {
            allScreens.filter { it.showInBottomBar }
        }
    }
}
