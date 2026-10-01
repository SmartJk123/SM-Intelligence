package com.example.smartmoney.ui.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.data.local.UserProfileManager
import com.example.smartmoney.ui.components.UserProfileAvatar
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun MoreScreen(
    userName: String? = null,
    userEmail: String? = null,
    userId: String? = null,
    isDarkMode: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val profileBitmap by UserProfileManager.profileBitmap.collectAsState()

    val cardBg = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surface

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                UserProfileManager.updateProfilePicture(context, uri, userId = userId)
            }
        }
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                UserProfileManager.updateProfilePicture(context, uri, userId = userId)
            }
        }
    }

    fun launchPhotoPicker() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            getContentLauncher.launch("image/*")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Text(
            text = "Settings & Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Account Profile Card with Avatar & Photo Upload
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar with Camera badge button
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserProfileAvatar(
                        bitmap = profileBitmap,
                        userName = userName,
                        modifier = Modifier.size(80.dp),
                        fontSize = 26.sp
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { launchPhotoPicker() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = "Change profile picture",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = userName ?: "Personal Workspace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!userEmail.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Change Photo & Remove Photo
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { launchPhotoPicker() },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (profileBitmap != null) "Change Photo" else "Upload Photo")
                    }

                    if (profileBitmap != null) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    UserProfileManager.removeProfilePicture(context, userId = userId)
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Preferences",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Dark Mode Settings Row with Moon Vector and Switch
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MoonVectorIcon,
                        contentDescription = "Dark Mode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dark Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isDarkMode) "Dark theme enabled" else "Switch between light and dark themes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isDarkMode,
                    onCheckedChange = onToggleDarkMode
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Log Out", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Clean Moon vector icon for Dark Mode toggle.
 */
val MoonVectorIcon: ImageVector
    get() {
        if (_moonVectorIcon != null) return _moonVectorIcon!!
        _moonVectorIcon = ImageVector.Builder(
            name = "MoonVector",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f
            ) {
                moveTo(12.3f, 2.0f)
                curveTo(12.0f, 2.0f, 11.7f, 2.0f, 11.4f, 2.1f)
                curveTo(11.1f, 3.1f, 11.0f, 4.0f, 11.0f, 5.0f)
                curveTo(11.0f, 9.4f, 14.6f, 13.0f, 19.0f, 13.0f)
                curveTo(20.0f, 13.0f, 20.9f, 12.9f, 21.9f, 12.6f)
                curveTo(21.4f, 17.3f, 17.5f, 21.0f, 12.5f, 21.0f)
                curveTo(7.3f, 21.0f, 3.0f, 16.7f, 3.0f, 11.5f)
                curveTo(3.0f, 6.7f, 6.6f, 2.7f, 11.3f, 2.1f)
                curveTo(11.6f, 2.0f, 12.0f, 2.0f, 12.3f, 2.0f)
                close()
            }
        }.build()
        return _moonVectorIcon!!
    }

private var _moonVectorIcon: ImageVector? = null
