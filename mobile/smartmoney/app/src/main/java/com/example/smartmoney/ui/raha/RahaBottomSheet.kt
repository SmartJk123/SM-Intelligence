package com.example.smartmoney.ui.raha

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.domain.model.RahaAction
import com.example.smartmoney.domain.model.RahaMessage
import com.example.smartmoney.domain.model.RahaSender
import com.example.smartmoney.ui.raha.components.RahaActionCard
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

/**
 * Floating bordered chatbox for Raha, positioned just shy of all screen edges
 * (top, bottom, left, right) with an elegant subtle accent border, soft backdrop scrim,
 * and comprehensive message controls.
 */
@Composable
fun RahaBottomSheet(
    uiState: RahaUiState,
    onDismiss: () -> Unit,
    onSendMessage: (String) -> Unit,
    onActionClick: (RahaAction) -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f
) {
    val isDark = LocalDarkTheme.current
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    // Intercept back button to close assistant window
    BackHandler(enabled = true, onBack = onDismiss)

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size, uiState.isTyping) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Outer full-screen layout container (scrim handled by AiChatTransition envelope)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 18.dp), // Just shy of top, bottom, and side edges
        contentAlignment = Alignment.Center
    ) {
        // Bordered Chatbox Container Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume click inside the card to prevent dismiss
                ),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) SmartMoneyColors.DeepNavy else Color(0xFFFAF9F6),
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = if (isDark) {
                        listOf(
                            SmartMoneyColors.AzurePrimary.copy(alpha = 0.85f),
                            SmartMoneyColors.PowderBlue.copy(alpha = 0.45f),
                            SmartMoneyColors.DarkBorder
                        )
                    } else {
                        listOf(
                            SmartMoneyColors.AzurePrimary.copy(alpha = 0.75f),
                            SmartMoneyColors.PowderBlue.copy(alpha = 0.45f),
                            SmartMoneyColors.BorderLine
                        )
                    }
                )
            ),
            shadowElevation = 18.dp,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha }
                    .clip(RoundedCornerShape(24.dp))
            ) {
                // Header
                RahaHeader(
                    isDark = isDark,
                    onClearChat = onClearChat,
                    onClose = onDismiss
                )

                HorizontalDivider(
                    color = if (isDark) SmartMoneyColors.DarkBorderLine else SmartMoneyColors.BorderLine,
                    thickness = 1.dp
                )

                // Chat Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        RahaMessageBubble(
                            message = message,
                            isDark = isDark,
                            onActionClick = onActionClick
                        )
                    }

                    if (uiState.isTyping) {
                        item {
                            RahaTypingIndicator(isDark = isDark)
                        }
                    }
                }

                // Quick Reply Chips
                AnimatedVisibility(
                    visible = uiState.quickReplies.isNotEmpty() && !uiState.isTyping,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.quickReplies) { chip ->
                            QuickReplyChip(
                                text = chip,
                                isDark = isDark,
                                onClick = { onSendMessage(chip) }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = if (isDark) SmartMoneyColors.DarkBorderLine else SmartMoneyColors.BorderLine,
                    thickness = 1.dp
                )

                // Input Bar
                RahaInputBar(
                    inputText = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    isDark = isDark
                )
            }
        }
    }
}

@Composable
private fun RahaHeader(
    isDark: Boolean,
    onClearChat: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Raha Avatar with subtle gradient ring
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(SmartMoneyColors.AzurePrimary, SmartMoneyColors.PowderBlue)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Raha",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary,
                        fontSize = 17.sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Online pulse dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
            }
            Text(
                text = "Financial Assistant",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.7f) else SmartMoneyColors.TextMuted,
                    fontSize = 12.sp
                )
            )
        }

        IconButton(onClick = onClearChat) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = "Clear Chat",
                tint = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.6f) else SmartMoneyColors.TextMuted
            )
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary
            )
        }
    }
}

@Composable
private fun RahaMessageBubble(
    message: RahaMessage,
    isDark: Boolean,
    onActionClick: (RahaAction) -> Unit
) {
    val isUser = message.sender == RahaSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        SmartMoneyColors.AzurePrimary
                    } else {
                        if (isDark) Color(0xFF263228) else Color(0xFFE8EFEA)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isUser) Color.White else (if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary),
                    lineHeight = 20.sp
                )
            )
        }

        // Action card if provided by Raha
        if (!isUser && message.action != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.widthIn(max = 310.dp)) {
                RahaActionCard(
                    action = message.action,
                    onActionClick = onActionClick
                )
            }
        }
    }
}

@Composable
private fun RahaTypingIndicator(isDark: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF263228) else Color(0xFFE8EFEA))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = SmartMoneyColors.AzurePrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Raha is thinking...",
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.7f) else SmartMoneyColors.TextMuted,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun QuickReplyChip(
    text: String,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val chipBg = if (isDark) Color(0xFF263228) else Color.White
    val borderColor = if (isDark) Color(0xFF3B483D) else SmartMoneyColors.BorderLine

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(chipBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isDark) SmartMoneyColors.DarkTextPrimary else SmartMoneyColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun RahaInputBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    text = "Ask Raha anything...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isDark) SmartMoneyColors.DarkTextSecondary.copy(alpha = 0.5f) else SmartMoneyColors.TextMuted.copy(alpha = 0.6f)
                    )
                )
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isDark) Color(0xFF1E2620) else Color.White,
                unfocusedContainerColor = if (isDark) Color(0xFF1E2620) else Color.White,
                focusedBorderColor = SmartMoneyColors.AzurePrimary,
                unfocusedBorderColor = if (isDark) Color(0xFF3B483D) else SmartMoneyColors.BorderLine
            ),
            singleLine = true
        )

        val canSend = inputText.isNotBlank()
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (canSend) SmartMoneyColors.AzurePrimary else (if (isDark) Color(0xFF2E3D31) else Color(0xFFD5DFD8)))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) Color.White else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
