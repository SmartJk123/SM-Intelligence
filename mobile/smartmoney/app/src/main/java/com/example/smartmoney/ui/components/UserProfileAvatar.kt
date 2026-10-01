package com.example.smartmoney.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.remember

private val WhitespaceRegex = "\\s+".toRegex()

/**
 * Extracts initials from a user's full name.
 * e.g., "Frank Mwangi" -> "FM", "Frank" -> "FR", blank -> "SM".
 */
fun extractInitials(name: String?): String {
    if (name.isNullOrBlank()) return "SM"
    val parts = name.trim().split(WhitespaceRegex).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts.first().first().uppercase()}${parts.last().first().uppercase()}"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "SM"
    }
}

/**
 * Circular user avatar displaying either the user's custom profile picture
 * or a placeholder with the user's name initials.
 */
@Composable
fun UserProfileAvatar(
    bitmap: ImageBitmap?,
    userName: String?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    fontSize: TextUnit = 12.sp
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "User profile picture",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        val initials = remember(userName) { extractInitials(userName) }
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = textColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
