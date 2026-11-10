package com.example.smartmoney.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.core.security.AppLockManager
import com.example.smartmoney.core.security.BiometricStatus
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupPinBottomSheet(
    appLockManager: AppLockManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val bioStatus = remember { appLockManager.checkBiometricStatus(context) }
    var enableBiometric by remember { mutableStateOf(bioStatus == BiometricStatus.AVAILABLE) }

    var step by remember { mutableStateOf(1) } // 1: Enter, 2: Confirm, 3: Success
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentPin = if (step == 1) firstPin else confirmPin

    fun onDigitPress(digit: String) {
        if (currentPin.length < 4) {
            val updated = currentPin + digit
            errorMessage = null
            if (step == 1) {
                firstPin = updated
                if (updated.length == 4) {
                    step = 2
                }
            } else {
                confirmPin = updated
                if (updated.length == 4) {
                    if (updated == firstPin) {
                        appLockManager.saveNewPin(updated)
                        if (bioStatus == BiometricStatus.AVAILABLE) {
                            appLockManager.setBiometricEnabled(enableBiometric)
                        }
                        step = 3
                    } else {
                        errorMessage = "PINs do not match. Please start over."
                        firstPin = ""
                        confirmPin = ""
                        step = 1
                    }
                }
            }
        }
    }

    fun onBackspace() {
        if (step == 1 && firstPin.isNotEmpty()) {
            firstPin = firstPin.dropLast(1)
            errorMessage = null
        } else if (step == 2 && confirmPin.isNotEmpty()) {
            confirmPin = confirmPin.dropLast(1)
            errorMessage = null
        }
    }

    val cardBg = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surface
    val textColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cardBg,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            if (step < 3) {
                Text(
                    text = if (step == 1) "Set up App PIN" else "Confirm your PIN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (step == 1) "Enter a 4-digit PIN to secure your app" else "Re-enter the same 4-digit PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 4 Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < currentPin.length
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else textColor.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Keypad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("", "0", "del")
                    )

                    for (row in rows) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (key in row) {
                                when (key) {
                                    "" -> Spacer(modifier = Modifier.size(56.dp))
                                    "del" -> {
                                        IconButton(
                                            onClick = { onBackspace() },
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                                contentDescription = "Delete",
                                                tint = textColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shadowElevation = 1.dp,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clickable { onDigitPress(key) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = key,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Step 3: Success & Optional Biometric Confirmation
                Text(
                    text = "App PIN Configured! ✓",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Your app is now protected with a secure PIN whenever you reopen it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    textAlign = TextAlign.Center
                )

                if (bioStatus == BiometricStatus.AVAILABLE) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Biometric Unlock",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                            Text(
                                text = "Use fingerprint or face for instant access",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableBiometric,
                            onCheckedChange = {
                                enableBiometric = it
                                appLockManager.setBiometricEnabled(it)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
