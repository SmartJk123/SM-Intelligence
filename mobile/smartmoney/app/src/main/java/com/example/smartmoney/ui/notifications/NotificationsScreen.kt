package com.example.smartmoney.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Notification
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Functional classification for tailoring the visual design to each notification.
 */
enum class NotificationKind {
    INFLOW,         // Incoming credits, deposits, salary
    OUTFLOW,        // Outgoing payments, debits, paybills
    BUDGET_ALERT,   // Budget threshold, overspending warning
    SYSTEM_UPDATE,  // System, sync, account linking
    SECURITY        // Login, auth, security alert
}

private fun classifyNotification(notification: Notification): NotificationKind {
    val type = notification.type.uppercase()
    val title = notification.title.lowercase()
    val msg = notification.message.lowercase()

    return when {
        type == "CREDIT" || title.contains("inflow") || title.contains("received") || title.contains("deposit") || title.contains("credited") -> {
            NotificationKind.INFLOW
        }
        type == "DEBIT" || title.contains("outflow") || title.contains("paid") || title.contains("sent") || title.contains("debited") -> {
            NotificationKind.OUTFLOW
        }
        title.contains("budget") || msg.contains("budget") || title.contains("limit") || msg.contains("limit") || title.contains("threshold") -> {
            NotificationKind.BUDGET_ALERT
        }
        title.contains("security") || title.contains("password") || title.contains("login") || title.contains("device") || title.contains("pin") -> {
            NotificationKind.SECURITY
        }
        else -> {
            NotificationKind.SYSTEM_UPDATE
        }
    }
}

private fun getNotificationDateSection(timestamp: String): String {
    return try {
        val date = if (timestamp.length >= 10 && timestamp[4] == '-' && timestamp[7] == '-') {
            LocalDate.parse(timestamp.substring(0, 10))
        } else {
            Instant.parse(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val today = LocalDate.now()
        when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.isAfter(today.minusDays(7)) -> "This Week"
            else -> "Earlier"
        }
    } catch (_: Exception) {
        "Earlier"
    }
}

private fun formatRelativeTime(timestamp: String): String {
    return try {
        val instant = if (timestamp.contains("T")) {
            try { Instant.parse(timestamp) } catch (_: Exception) { null }
        } else null

        if (instant != null) {
            val now = Instant.now()
            val diffSec = java.time.Duration.between(instant, now).seconds
            when {
                diffSec < 60 -> "Just now"
                diffSec < 3600 -> "${diffSec / 60}m ago"
                diffSec < 86400 -> "${diffSec / 3600}h ago"
                diffSec < 172800 -> "Yesterday"
                else -> {
                    val dt = instant.atZone(ZoneId.systemDefault())
                    val month = dt.month.getDisplayName(TextStyle.SHORT, Locale.US)
                    "$month ${dt.dayOfMonth}"
                }
            }
        } else {
            val parts = timestamp.split("T", " ")
            if (parts.size >= 2) "${parts[0]} ${parts[1].take(5)}" else timestamp.take(16)
        }
    } catch (_: Exception) {
        timestamp.take(16)
    }
}

private fun formatDetailedTimestamp(timestamp: String): String {
    return try {
        if (timestamp.contains("T")) {
            val parts = timestamp.split("T")
            val date = parts[0]
            val time = parts[1].take(8).replace("Z", "")
            "$date at $time"
        } else {
            timestamp
        }
    } catch (_: Exception) {
        timestamp
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    var selectedNotification by remember { mutableStateOf<Notification?>(null) }

    val bgColor = if (isDark) SmartMoneyColors.DarkBackground else SmartMoneyColors.SlateBackground
    val cardBgColor = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Notifications",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.DarkSlateGreen
                    )
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$unreadCount new",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.DarkSlateGreen
                    )
                }
            },
            actions = {
                if (unreadCount > 0) {
                    IconButton(
                        onClick = { viewModel.markAllAsRead() }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = "Mark all as read",
                            tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        // Filter Bar (All, Unread, Money In, Money Out, System)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                NotificationFilterChip(
                    label = "All",
                    isSelected = selectedFilter == "ALL",
                    count = null,
                    onClick = { viewModel.setFilter("ALL") }
                )
            }
            item {
                NotificationFilterChip(
                    label = "Unread",
                    isSelected = selectedFilter == "UNREAD",
                    count = if (unreadCount > 0) unreadCount else null,
                    onClick = { viewModel.setFilter("UNREAD") }
                )
            }
            item {
                NotificationFilterChip(
                    label = "Money In",
                    isSelected = selectedFilter == "INFLOW",
                    count = null,
                    onClick = { viewModel.setFilter("INFLOW") }
                )
            }
            item {
                NotificationFilterChip(
                    label = "Money Out",
                    isSelected = selectedFilter == "OUTFLOW",
                    count = null,
                    onClick = { viewModel.setFilter("OUTFLOW") }
                )
            }
            item {
                NotificationFilterChip(
                    label = "System",
                    isSelected = selectedFilter == "SYSTEM",
                    count = null,
                    onClick = { viewModel.setFilter("SYSTEM") }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content
        if (notifications.isEmpty()) {
            EmptyNotificationsView(selectedFilter = selectedFilter)
        } else {
            // Group notifications chronologically to eliminate disorientation
            val groupedNotifications = remember(notifications) {
                notifications.groupBy { getNotificationDateSection(it.timestamp) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedNotifications.forEach { (sectionHeader, sectionItems) ->
                    item(key = "header_$sectionHeader") {
                        SectionHeader(title = sectionHeader, count = sectionItems.size)
                    }

                    items(
                        items = sectionItems,
                        key = { it.id }
                    ) { notif ->
                        TailoredNotificationCard(
                            notification = notif,
                            isDark = isDark,
                            cardBgColor = cardBgColor,
                            onClick = {
                                viewModel.markAsRead(notif.id)
                                selectedNotification = notif.copy(isRead = true)
                            }
                        )
                    }
                }
            }
        }
    }

    selectedNotification?.let { notif ->
        NotificationDetailBottomSheet(
            notification = notif,
            isDark = isDark,
            onDismiss = { selectedNotification = null },
            onDelete = {
                viewModel.deleteNotification(notif.id)
                selectedNotification = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    val isDark = LocalDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
        )
        Text(
            text = "$count ${if (count == 1) "alert" else "alerts"}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.6f) else SmartMoneyColors.TextMuted.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun NotificationFilterChip(
    label: String,
    isSelected: Boolean,
    count: Int?,
    onClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val activeBg = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
    val activeText = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
    val inactiveBg = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surface
    val inactiveText = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) activeBg else inactiveBg,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) activeText else inactiveText
            )
            if (count != null && count > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) activeText.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) activeText else Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dedicated, tailored card component engineered specifically for each notification kind.
 */
@Composable
private fun TailoredNotificationCard(
    notification: Notification,
    isDark: Boolean,
    cardBgColor: Color,
    onClick: () -> Unit
) {
    val kind = classifyNotification(notification)
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    // Tailored palette and visual identity
    val (accentColor, pillBg, icon, kindLabel) = when (kind) {
        NotificationKind.INFLOW -> {
            val col = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
            Quad(col, col.copy(alpha = if (isDark) 0.18f else 0.10f), Icons.Outlined.ArrowDownward, "MONEY IN")
        }
        NotificationKind.OUTFLOW -> {
            val col = if (isDark) Color(0xFFFF6B6B) else Color(0xFFEF4444)
            Quad(col, col.copy(alpha = if (isDark) 0.18f else 0.10f), Icons.Outlined.ArrowUpward, "MONEY OUT")
        }
        NotificationKind.BUDGET_ALERT -> {
            val col = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
            Quad(col, col.copy(alpha = if (isDark) 0.20f else 0.12f), Icons.Outlined.Savings, "BUDGET ALERT")
        }
        NotificationKind.SECURITY -> {
            val col = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
            Quad(col, col.copy(alpha = if (isDark) 0.20f else 0.12f), Icons.Outlined.Shield, "SECURITY")
        }
        NotificationKind.SYSTEM_UPDATE -> {
            val col = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
            Quad(col, col.copy(alpha = if (isDark) 0.18f else 0.10f), Icons.Outlined.Info, "SYSTEM")
        }
    }

    val containerBg = if (notification.isRead) {
        cardBgColor
    } else {
        if (isDark) SmartMoneyColors.DarkSurfaceElevated else cardBgColor
    }

    val cardBorder = if (!notification.isRead) {
        BorderStroke(1.dp, accentColor.copy(alpha = if (isDark) 0.40f else 0.28f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 1.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 1. Header Row: Kind Pill + "NEW" badge + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Category Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = pillBg
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = kindLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    // Prominent Unread Indicator
                    if (!notification.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "NEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }

                // Relative Timestamp
                Text(
                    text = formatRelativeTime(notification.timestamp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.65f) else SmartMoneyColors.TextMuted.copy(alpha = 0.75f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Middle Row: Leading Icon Avatar + Body + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Leading Icon Container
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pillBg,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title + Message
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.title,
                        fontSize = 14.sp,
                        fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.Bold,
                        color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = notification.message,
                        fontSize = 12.sp,
                        color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Dedicated Amount Badge for Financial Transactions
                if (notification.amount != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val sign = if (kind == NotificationKind.INFLOW) "+" else "-"
                    val formatted = DecimalFormat("#,##0.00").format(notification.amount)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = pillBg
                    ) {
                        Text(
                            text = "$sign KES $formatted",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // 3. Optional Reference Chip (Clean & unobtrusive)
            if (!notification.reference.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f),
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(notification.reference))
                        isCopied = true
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Ref: ${notification.reference}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy reference",
                            tint = if (isCopied) accentColor else (if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.6f) else SmartMoneyColors.TextMuted.copy(alpha = 0.6f)),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun EmptyNotificationsView(selectedFilter: String) {
    val isDark = LocalDarkTheme.current
    val (emptyTitle, emptyDesc, emptyIcon) = when (selectedFilter) {
        "UNREAD" -> Triple(
            "No Unread Alerts",
            "You're all caught up! Switch to 'All' to review your full alert history.",
            Icons.Outlined.DoneAll
        )
        "INFLOW" -> Triple(
            "No Inflow Alerts",
            "Incoming deposits and M-PESA receipts will appear here as they arrive.",
            Icons.Outlined.ArrowDownward
        )
        "OUTFLOW" -> Triple(
            "No Outflow Alerts",
            "Payments, merchant paybills, and withdrawals will appear here.",
            Icons.Outlined.ArrowUpward
        )
        "SYSTEM" -> Triple(
            "No System Notices",
            "Account status, automated syncs, and security notices will be logged here.",
            Icons.Outlined.Info
        )
        else -> Triple(
            "No Notifications Yet",
            "Real-time alerts for deposits, payments, and account balances will appear here.",
            Icons.Outlined.Notifications
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = (if (isDark) SmartMoneyColors.DarkSurface else SmartMoneyColors.PaleMintGreen).copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = emptyIcon,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = emptyTitle,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = emptyDesc,
                fontSize = 13.sp,
                color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationDetailBottomSheet(
    notification: Notification,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    val kind = classifyNotification(notification)

    val (accentColor, pillBg, typeIcon, typeLabel) = when (kind) {
        NotificationKind.INFLOW -> {
            val col = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A)
            Quad(col, col.copy(alpha = if (isDark) 0.20f else 0.12f), Icons.Outlined.ArrowDownward, "Inflow (Deposit / Credit)")
        }
        NotificationKind.OUTFLOW -> {
            val col = if (isDark) Color(0xFFFF6B6B) else Color(0xFFEF4444)
            Quad(col, col.copy(alpha = if (isDark) 0.20f else 0.12f), Icons.Outlined.ArrowUpward, "Outflow (Payment / Debit)")
        }
        NotificationKind.BUDGET_ALERT -> {
            val col = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
            Quad(col, col.copy(alpha = if (isDark) 0.20f else 0.12f), Icons.Outlined.Savings, "Budget Alert")
        }
        NotificationKind.SECURITY -> {
            val col = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
            Quad(col, col.copy(alpha = if (isDark) 0.20f else 0.12f), Icons.Outlined.Shield, "Security Notice")
        }
        NotificationKind.SYSTEM_UPDATE -> {
            val col = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
            Quad(col, col.copy(alpha = if (isDark) 0.20f else 0.12f), Icons.Outlined.Info, "System Notification")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header Row: Type pill + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pillBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = typeLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notification Title
            Text(
                text = notification.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatDetailedTimestamp(notification.timestamp),
                fontSize = 12.sp,
                color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted
            )

            // Tailored Amount Hero Card if present
            if (notification.amount != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = pillBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (kind == NotificationKind.INFLOW) "Total Cleared Inflow" else "Total Disbursed Outflow",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sign = if (kind == NotificationKind.INFLOW) "+" else "-"
                        val formatted = DecimalFormat("#,##0.00").format(notification.amount)
                        Text(
                            text = "$sign KES $formatted",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Message Body Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.04f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Alert Details",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = notification.message,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
                    )
                }
            }

            // Reference metadata
            if (!notification.reference.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.04f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Transaction Reference",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = notification.reference,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(notification.reference))
                                isCopied = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                contentDescription = "Copy reference",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCopied) "Copied" else "Copy",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons (Delete & Done)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFEF4444)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Delete", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                        contentColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Done", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
