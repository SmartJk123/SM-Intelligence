package com.example.smartmoney.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.Notification
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.text.DecimalFormat

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
        // Top Bar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Notifications",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen
                    )
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE53935).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$unreadCount new",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE53935),
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
                        tint = if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen
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
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        // Filter Tabs (All / Unread)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NotificationFilterChip(
                label = "All",
                isSelected = selectedFilter == "ALL",
                count = null,
                onClick = { viewModel.setFilter("ALL") }
            )
            NotificationFilterChip(
                label = "Unread",
                isSelected = selectedFilter == "UNREAD",
                count = if (unreadCount > 0) unreadCount else null,
                onClick = { viewModel.setFilter("UNREAD") }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content
        if (notifications.isEmpty()) {
            EmptyNotificationsView(selectedFilter = selectedFilter)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = notifications,
                    key = { it.id }
                ) { notif ->
                    NotificationCard(
                        notification = notif,
                        isDark = isDark,
                        containerColor = cardBgColor,
                        onClick = {
                            viewModel.markAsRead(notif.id)
                            selectedNotification = notif.copy(isRead = true)
                        },
                        onDelete = { viewModel.deleteNotification(notif.id) }
                    )
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
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    color = if (isSelected) activeText.copy(alpha = 0.2f) else Color(0xFFE53935).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) activeText else Color(0xFFE53935),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: Notification,
    isDark: Boolean,
    containerColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isCredit = notification.type.equals("CREDIT", ignoreCase = true)
    val accentColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFD32F2F)
    val accentBg = accentColor.copy(alpha = 0.12f)
    val icon = if (isCredit) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Direction Badge Icon
            Surface(
                shape = CircleShape,
                color = accentBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = notification.title,
                            fontSize = 14.sp,
                            fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.Bold,
                            color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!notification.isRead) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            )
                        }
                    }

                    if (notification.amount != null) {
                        val sign = if (isCredit) "+" else "-"
                        val formatted = DecimalFormat("#,##0.00").format(notification.amount)
                        Text(
                            text = "$sign KES $formatted",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notification.message,
                    fontSize = 12.sp,
                    color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!notification.reference.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f)
                        ) {
                            Text(
                                text = notification.reference,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTimestamp(notification.timestamp),
                            fontSize = 11.sp,
                            color = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.7f) else SmartMoneyColors.TextMuted.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete notification",
                                tint = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.5f) else SmartMoneyColors.TextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotificationsView(selectedFilter: String) {
    val isDark = LocalDarkTheme.current
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
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (selectedFilter == "UNREAD") "No Unread Notifications" else "No Notifications Yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (selectedFilter == "UNREAD")
                    "You're all caught up! Switch to 'All' to view your alert history."
                else
                    "Real-time alerts for deposits, payments, and account balances will appear here.",
                fontSize = 13.sp,
                color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        if (timestamp.contains("T")) {
            val parts = timestamp.split("T")
            val datePart = parts[0]
            val timePart = parts[1].take(5)
            "$datePart $timePart"
        } else {
            timestamp.take(16)
        }
    } catch (_: Exception) {
        timestamp
    }
}

private fun formatDetailedTimestamp(timestamp: String): String {
    return try {
        if (timestamp.contains("T")) {
            val parts = timestamp.split("T")
            val date = parts[0]
            val time = parts[1].take(8)
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
private fun NotificationDetailBottomSheet(
    notification: Notification,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    val isCredit = notification.type.equals("CREDIT", ignoreCase = true)
    val isDebit = notification.type.equals("DEBIT", ignoreCase = true)

    val accentColor = when {
        isCredit -> Color(0xFF2E7D32)
        isDebit -> Color(0xFFD32F2F)
        else -> if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen
    }
    val accentBg = accentColor.copy(alpha = 0.12f)
    val typeIcon = when {
        isCredit -> Icons.AutoMirrored.Filled.TrendingUp
        isDebit -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.Outlined.Notifications
    }

    val typeLabel = when {
        isCredit -> "Inflow (Deposit / Credit)"
        isDebit -> "Outflow (Payment / Debit)"
        else -> "System Alert"
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
                    color = accentBg
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

            // Amount Card if present
            if (notification.amount != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = accentBg
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isCredit) "Total Inflow" else "Total Outflow",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = accentColor.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sign = if (isCredit) "+" else "-"
                        val formatted = DecimalFormat("#,##0.00").format(notification.amount)
                        Text(
                            text = "$sign KES $formatted",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
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
                        text = "Notification Message",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
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

            // Reference / Details metadata
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
                                text = "Reference Code",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) SmartMoneyColors.DarkTextSecondary else SmartMoneyColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = notification.reference,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
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

            // Action Buttons (Delete & Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE53935)
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
