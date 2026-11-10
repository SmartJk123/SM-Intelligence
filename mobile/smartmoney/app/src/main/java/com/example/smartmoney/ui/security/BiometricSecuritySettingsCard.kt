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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.core.security.AppLockManager
import com.example.smartmoney.core.security.BiometricStatus
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors

@Composable
fun BiometricSecuritySettingsCard(
    appLockManager: AppLockManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalDarkTheme.current

    val isPinConfigured by appLockManager.isPinConfigured.collectAsState()
    val isBiometricEnabled by appLockManager.isBiometricEnabled.collectAsState()
    val timeoutMinutes by appLockManager.lockTimeoutMinutes.collectAsState()

    val bioStatus = remember { appLockManager.checkBiometricStatus(context) }
    var showPinSetup by remember { mutableStateOf(false) }

    val cardBg = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surface
    val activeColor = SmartMoneyColors.PaleMintGreen
    val inactiveColor = MaterialTheme.colorScheme.error

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Security",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "App Lock & Biometrics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Protect your financial data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPinConfigured) activeColor.copy(alpha = 0.15f) else inactiveColor.copy(alpha = 0.12f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Secures the app with a local 4-digit PIN and optional fingerprint/face recognition. Automatically locks when returning from the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Action: Setup or Change PIN
            if (!isPinConfigured) {
                Button(
                    onClick = { showPinSetup = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set Up App PIN", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPinSetup = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Change PIN")
                    }
                    OutlinedButton(
                        onClick = { appLockManager.removePin() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disable")
                    }
                }

                // Biometrics toggle (if hardware supported)
                if (bioStatus == BiometricStatus.AVAILABLE) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Biometric Unlock",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { appLockManager.setBiometricEnabled(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-lock timeout selector
                Text(
                    text = "Auto-Lock Timeout",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        0 to "Immediately",
                        1 to "1 Minute",
                        5 to "5 Minutes"
                    )

                    for ((mins, label) in options) {
                        val isSelected = timeoutMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { appLockManager.setLockTimeoutMinutes(mins) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinSetup) {
        SetupPinBottomSheet(
            appLockManager = appLockManager,
            onDismiss = { showPinSetup = false }
        )
    }
}
