package com.example.smartmoney.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartmoney.core.util.CurrencyUtils
import com.example.smartmoney.data.local.UserProfileManager
import com.example.smartmoney.domain.model.BankAccount
import com.example.smartmoney.domain.model.Transaction
import com.example.smartmoney.domain.model.TrendPoint
import com.example.smartmoney.ui.components.BankLogo
import com.example.smartmoney.ui.components.UserProfileAvatar
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale


import com.example.smartmoney.ui.home.analytics.components.OverviewAnalyticsSection

private val MutedSageCard = SmartMoneyColors.PaleMintGreen      // #DAEBE3: Pale Mint Green
private val DarkContrastColor = SmartMoneyColors.DarkSlateGreen // #657166: Dark Slate Green
private val DarkGreenPillBadge = SmartMoneyColors.DarkSlateGreen // #657166: Dark Slate Green

/**
 * Pure, lightweight UI presentation composable for the Overview / Home screen.
 * All network calls, database flows, and mathematical aggregations are executed
 * upstream in [HomeViewModel] off the Main thread.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState = HomeUiState.DEFAULT,
    userName: String = "User",
    onLinkAccountClick: () -> Unit = {},
    unreadNotificationCount: Int = uiState.unreadNotificationCount,
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = onSettingsClick,
    onGlowFinished: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onOpenBudgetsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val scrollState = rememberScrollState()
    val profileBitmap by UserProfileManager.profileBitmap.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var isBalanceVisible by remember { mutableStateOf(true) }

    // Ambient glow animation controller for top card bottom contour
    val glowProgress = remember { Animatable(0f) }
    var activeGlowType by remember { mutableStateOf(TopCardGlowType.NONE) }

    LaunchedEffect(uiState.glowEvent?.eventId) {
        val event = uiState.glowEvent ?: return@LaunchedEffect
        if (event.type == TopCardGlowType.NONE) return@LaunchedEffect

        activeGlowType = event.type
        // 1. Snappy ignite / surge in (280ms)
        glowProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        )
        // 2. Hold peak illumination (1200ms)
        delay(1200L)
        // 3. Graceful smooth fade away (1800ms)
        glowProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 1800, easing = LinearOutSlowInEasing)
        )
        activeGlowType = TopCardGlowType.NONE
        onGlowFinished()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
        // =========================================================================
        // 1. THE FLUSH-TOP MAIN CARD WITH OVERLAPPING APPTOPBAR
        // Sits completely flush against the top edge without any top borders/margins.
        // Rounding applied ONLY to bottom corners (bottomStart = 32.dp, bottomEnd = 32.dp).
        // Transparent AppTopBar overlaps the card at the top, acting as its title bar.
        // =========================================================================
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
                color = if (isDark) SmartMoneyColors.DarkSlateGreen else MutedSageCard,
                contentColor = if (isDark) Color.White else DarkContrastColor,
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 22.dp)
                ) {
                    // Clearance for the overlapping transparent AppTopBar (standard TopAppBar height 64.dp + 8.dp)
                    Spacer(modifier = Modifier.height(72.dp))

                    // -----------------------------------------------------------------
                    // INNER BANNERS CAROUSEL:
                    // Smooth swipeable HorizontalPager between:
                    // Page 0 (Left): Total Balance banner card
                    // Page 1 (Right): Connected Apps banner card
                    // -----------------------------------------------------------------
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        pageSpacing = 16.dp,
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        when (page) {
                            0 -> TotalBalanceBannerCard(
                                uiState = uiState,
                                isBalanceVisible = isBalanceVisible,
                                onToggleBalanceVisibility = { isBalanceVisible = !isBalanceVisible }
                            )
                            1 -> ConnectedAppsBannerCard(
                                bankAccounts = uiState.bankAccounts,
                                isOnboardingActive = uiState.isOnboardingActive,
                                onLinkAccountClick = onLinkAccountClick
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // -----------------------------------------------------------------
                    // SCROLL DOT AND LINE INDICATOR:
                    // Animated indicator displaying an active line and inactive dot
                    // with smooth spring transitions and tap-to-scroll interaction.
                    // -----------------------------------------------------------------
                    CarouselPageIndicator(
                        pageCount = 2,
                        currentPage = pagerState.currentPage,
                        onDotClick = { targetPage ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetPage)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            // Glowing Bottom Accent Line (lights up light green on money received, red on money leaving)
            if (glowProgress.value > 0.001f && activeGlowType != TopCardGlowType.NONE) {
                TopCardBottomGlowLine(
                    glowType = activeGlowType,
                    glowAlpha = glowProgress.value,
                    isDark = isDark,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        // =========================================================================
        // 2. DETACHED CASH IN TREND & LINKED ACCOUNTS SUMMARY
        // Positioned cleanly below the main banner card.
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // ONBOARDING GET STARTED CARD (when onboarding is active off Main thread)
            // =========================================================================
            if (uiState.isOnboardingActive) {
                OnboardingGetStartedCard(
                    onLinkAccountClick = onLinkAccountClick
                )
                Spacer(modifier = Modifier.height(18.dp))
            }

            // 4-Dimension Financial Analytics Section (Cash Flow, Spending Donut, Heatmap, Budget Pacing)
            OverviewAnalyticsSection(
                analyticsData = uiState.analytics,
                selectedMonth = uiState.selectedMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onOpenBudgetsClick = onOpenBudgetsClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Linked Accounts Summary Section
            LinkedAccountsSummarySection(
                bankAccounts = uiState.bankAccounts,
                isOnboardingActive = uiState.isOnboardingActive,
                onLinkAccountClick = onLinkAccountClick
            )

            Spacer(modifier = Modifier.navigationBarsPadding().height(96.dp))
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 96.dp)
    )
    }
}

/**
 * Luminous multi-pass ambient glow line hugging the rounded bottom contour
 * (bottomStart = 32.dp, bottomEnd = 32.dp) of the Overview top card.
 *
 * Renders:
 * 1. Interior Card Backlight Reflection (soft vertical gradient wash clipped to bottom rounded corners).
 * 2. Outer Atmospheric Halo (wide diffuse bloom).
 * 3. Intermediate Bloom Layer (concentrated radiance).
 * 4. Crisp Neon Core Filament Line (bright pastel core with rounded stroke caps).
 */
@Composable
private fun TopCardBottomGlowLine(
    glowType: TopCardGlowType,
    glowAlpha: Float,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val cornerRadius = 32.dp

    val (glowColor, coreColor) = when (glowType) {
        TopCardGlowType.INFLOW_GREEN -> {
            if (isDark) {
                Color(0xFF34C759) to Color(0xFFD1FAE5) // Apple Mint / Emerald with bright neon core
            } else {
                Color(0xFF22C55E) to Color(0xFF86EFAC) // Fresh bright green with light core
            }
        }
        TopCardGlowType.OUTFLOW_RED -> {
            if (isDark) {
                Color(0xFFFF453A) to Color(0xFFFFD1D1) // Apple Coral Red with luminous core
            } else {
                Color(0xFFEF4444) to Color(0xFFFECACA) // Crisp bright red with light core
            }
        }
        TopCardGlowType.NONE -> return
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = cornerRadius.toPx()

        // Construct the bottom contour path:
        // Starts at (0, h - r), sweeps 90 deg around bottom-left arc to (r, h),
        // runs horizontally to (w - r, h), sweeps 90 deg around bottom-right arc to (w, h - r).
        val path = Path().apply {
            moveTo(0f, h - r)
            arcTo(
                rect = Rect(left = 0f, top = h - 2 * r, right = 2 * r, bottom = h),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
            lineTo(w - r, h)
            arcTo(
                rect = Rect(left = w - 2 * r, top = h - 2 * r, right = w, bottom = h),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )
        }

        // 1. Subtle interior card bottom backlight reflection
        val washHeight = 36.dp.toPx()
        val washBrush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                glowColor.copy(alpha = glowAlpha * 0.16f)
            ),
            startY = h - washHeight,
            endY = h
        )
        val clipPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, w, h),
                    bottomLeft = CornerRadius(r, r),
                    bottomRight = CornerRadius(r, r)
                )
            )
        }
        clipPath(clipPath) {
            drawRect(
                brush = washBrush,
                topLeft = Offset(0f, h - washHeight),
                size = Size(w, washHeight)
            )
        }

        // 2. Wide atmospheric halo / ambient bloom
        drawPath(
            path = path,
            color = glowColor.copy(alpha = glowAlpha * 0.25f),
            style = Stroke(
                width = 14.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 3. Concentrated inner bloom layer
        drawPath(
            path = path,
            color = glowColor.copy(alpha = glowAlpha * 0.55f),
            style = Stroke(
                width = 6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 4. Razor-sharp luminous core filament line
        drawPath(
            path = path,
            color = coreColor.copy(alpha = glowAlpha * 0.95f),
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * Total Balance Banner Card (Page 0 of the top container carousel):
 * Houses Total Balance, Privacy Toggle, Performance Pill Badge,
 * and the Total Cash In & Total Cash Out metric row.
 */
@Composable
private fun TotalBalanceBannerCard(
    uiState: HomeUiState,
    isBalanceVisible: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Row: Title + Performance Badge (top right, like a card chip/contactless badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Balance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiState.isOnboardingActive) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "No cards linked",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else if (!uiState.hasTransactions) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isDark) SmartMoneyColors.DarkSurface else SmartMoneyColors.PaleMintGreen.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Card active",
                                color = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isDark) SmartMoneyColors.DarkSurface else DarkGreenPillBadge
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (isDark) SmartMoneyColors.PaleMintGreen else MutedSageCard,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Live",
                                color = if (isDark) SmartMoneyColors.PaleMintGreen else MutedSageCard,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "This week",
                                color = Color.White.copy(alpha = 0.92f),
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 2. Middle Row: Big Balance amount + visibility toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                val displayBalance = remember(isBalanceVisible, uiState.totalBalance) {
                    if (isBalanceVisible) {
                        formatKesCurrency(uiState.totalBalance)
                    } else {
                        "••••••••"
                    }
                }

                Text(
                    text = displayBalance,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    onClick = onToggleBalanceVisibility,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = if (isBalanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // 3. Bottom Row: Total Cash In & Total Cash Out (inside the credit card)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricItem(
                    modifier = Modifier.weight(1f),
                    label = "Total Cash In",
                    amount = uiState.totalCashIn,
                    isCredit = true
                )

                MetricItem(
                    modifier = Modifier.weight(1f),
                    label = "Total Cash Out",
                    amount = uiState.totalCashOut,
                    isCredit = false
                )
            }
        }
    }
}

/**
 * Connected Apps Banner Card (Page 1 of the top container carousel):
 * Proportioned strictly to the dimensions of a physical credit card (aspect ratio 1.586 : 1).
 * Features an internal scrollable list allowing the user to scroll through and view all connected
 * banks and accounts beyond the initial field of view (FOV).
 */
@Composable
private fun ConnectedAppsBannerCard(
    bankAccounts: List<BankAccount>,
    isOnboardingActive: Boolean = false,
    onLinkAccountClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val themeGreen = if (isDark) Color(0xFF4ADE80) else SmartMoneyColors.DarkSlateGreen
    val themeRed = MaterialTheme.colorScheme.error
    val bankScrollState = rememberScrollState()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Header Row: "Connected Apps" title and real-time status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Connected Apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${bankAccounts.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val statusBg = if (isOnboardingActive) {
                    if (isDark) SmartMoneyColors.DarkSurface else MaterialTheme.colorScheme.surfaceVariant
                } else {
                    if (isDark) Color(0xFF143823) else SmartMoneyColors.PaleMintGreen
                }
                val statusDotColor = if (isOnboardingActive) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else themeGreen
                val statusTextColor = if (isOnboardingActive) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else themeGreen
                val statusText = if (isOnboardingActive) "0 Active" else "Real-time"

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusTextColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isOnboardingActive) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No Connected Accounts",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Link a bank or card to see live balances",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        onClick = onLinkAccountClick,
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                        contentColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Link Account",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Scrollable container: allows the user to scroll through to view all other banks not initially in FOV
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(bankScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bankAccounts.forEach { bank ->
                        BankFlowMiniCard(bank = bank, themeGreen = themeGreen, themeRed = themeRed)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Subtle footer hint showing sync status and scroll affordance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (bankAccounts.size > 2) "↕ Scroll to view all ${bankAccounts.size} accounts" else "All accounts connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.5.sp
                    )
                    Text(
                        text = "Auto-sync",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = themeGreen,
                        fontSize = 10.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Animated Dot and Line Carousel Page Indicator:
 * Active page transforms into a sleek elongated line/pill, while inactive pages are dots.
 * Supports smooth spring animation and tap-to-scroll interactivity.
 */
@Composable
private fun CarouselPageIndicator(
    pageCount: Int,
    currentPage: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val activeColor = if (isDark) Color.White else DarkContrastColor
    val inactiveColor = if (isDark) Color.White.copy(alpha = 0.35f) else DarkContrastColor.copy(alpha = 0.28f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            val width by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 7.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "indicator_width_$index"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) activeColor else inactiveColor,
                animationSpec = tween(durationMillis = 250),
                label = "indicator_color_$index"
            )

            Box(
                modifier = Modifier
                    .size(width = width, height = 7.dp)
                    .clip(RoundedCornerShape(3.5.dp))
                    .background(color)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDotClick(index)
                    }
            )
        }
    }
}

/**
 * Compact mini-card for a connected bank within the dropdown overview.
 */
@Composable
private fun BankFlowMiniCard(
    bank: BankAccount,
    themeGreen: Color,
    themeRed: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                BankLogo(
                    bankName = bank.bankName,
                    modifier = Modifier.size(26.dp),
                    shape = RoundedCornerShape(6.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = bank.bankName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = bank.maskedAccountNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyUtils.formatKes(bank.balance),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = bank.cardType,
                    style = MaterialTheme.typography.labelSmall,
                    color = themeGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Metric pill displaying Cash In / Cash Out with directional badge and color coding.
 */
@Composable
private fun MetricItem(
    modifier: Modifier = Modifier,
    label: String,
    amount: BigDecimal,
    isCredit: Boolean
) {
    val isDark = LocalDarkTheme.current
    val themeGreen = if (isDark) Color(0xFF4ADE80) else SmartMoneyColors.DarkSlateGreen
    val themeRed = if (isDark) Color(0xFFFF6B6B) else Color(0xFF992B1C)

    val badgeBg = if (isDark) {
        if (isCredit) Color(0xFF143823) else Color(0xFF4A1818)
    } else {
        if (isCredit) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.LightSalmon
    }
    val textColor = if (isCredit) themeGreen else themeRed
    val icon = if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
    val formattedAmount = remember(amount) { formatKesCurrency(amount) }

    val containerBg = if (isDark) {
        if (isCredit) Color(0xFF142418) else Color(0xFF281717)
    } else {
        if (isCredit) SmartMoneyColors.PaleMintGreen.copy(alpha = 0.4f) else SmartMoneyColors.LightPeach.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerBg
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = formattedAmount,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Income Trend Graph Card built strictly using Jetpack Compose's native Canvas API.
 * No external charting libraries required.
 */
@Composable
private fun CashFlowTrendGraphCard(trendPoints: List<TrendPoint>) {
    val isDark = LocalDarkTheme.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cash Flow Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "7-Day Activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) SmartMoneyColors.DarkSurfaceElevated else SmartMoneyColors.PaleMintGreen
                ) {
                    Text(
                        text = "Weekly",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color.White else SmartMoneyColors.DarkSlateGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            if (trendPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Cash Flow Activity Yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect an account to automatically generate your 7-day cash flow analysis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cash In", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.width(16.dp))

                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF44336)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cash Out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Canvas Line Chart
                CashFlowLineGraphCanvas(
                    points = trendPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // X-Axis Day Labels
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    trendPoints.forEach { point ->
                        Text(
                            text = point.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Native Jetpack Compose Canvas rendering a smooth cubic-bezier line graph
 * for both Cash In and Cash Out with a Y-axis showing 1k increments.
 */
@Composable
private fun CashFlowLineGraphCanvas(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val cashInPath = remember { Path() }
    val cashOutPath = remember { Path() }
    val cashInFillPath = remember { Path() }
    val cashOutFillPath = remember { Path() }
    
    val textMeasurer = rememberTextMeasurer()
    val isDark = LocalDarkTheme.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = Color.LightGray.copy(alpha = 0.3f)

    val cashInColor = Color(0xFF4CAF50)
    val cashOutColor = Color(0xFFF44336)

    val floatIn = remember(points) { points.map { it.cashIn.toFloat() } }
    val floatOut = remember(points) { points.map { it.cashOut.toFloat() } }
    
    val (maxVal, increment) = remember(floatIn, floatOut) {
        val maxI = floatIn.maxOrNull() ?: 0f
        val maxO = floatOut.maxOrNull() ?: 0f
        val mx = maxOf(maxI, maxO)
        val step = when {
            mx <= 2000f -> 1000f
            mx <= 6000f -> 2000f
            mx <= 15000f -> 3000f
            mx <= 30000f -> 5000f
            mx <= 60000f -> 10000f
            else -> 20000f
        }
        val remainder = mx % step
        val adjustedMax = if (remainder > 0) mx + (step - remainder) else mx
        Pair(if (adjustedMax == 0f) 1000f else adjustedMax, step)
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val topPadding = 8.dp.toPx()
        val bottomPadding = 16.dp.toPx()
        val leftPadding = 32.dp.toPx() // Space for Y axis labels
        
        val usableHeight = height - topPadding - bottomPadding
        val usableWidth = width - leftPadding

        val stepX = usableWidth / (points.size - 1)

        // 1. Draw horizontal grid lines and Y-axis labels in dynamic increments
        val numLines = (maxVal / increment).toInt().coerceIn(1, 6)
        
        for (i in 0..numLines) {
            val currentValue = i * increment
            val normalizedY = (currentValue / maxVal).coerceIn(0f, 1f)
            val gridY = height - bottomPadding - (normalizedY * usableHeight)
            
            // Draw grid line
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, gridY),
                end = Offset(width, gridY),
                strokeWidth = 1.dp.toPx()
            )
            
            // Draw text label
            val labelText = if (currentValue >= 1000f) {
                "${(currentValue / 1000).toInt()}k"
            } else {
                "${currentValue.toInt()}"
            }
            val textLayoutResult = textMeasurer.measure(
                text = labelText,
                style = TextStyle(
                    color = labelColor,
                    fontSize = 10.sp
                )
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(0f, gridY - (textLayoutResult.size.height / 2f))
            )
        }

        // Helper to draw a line
        fun drawTrend(
            amounts: List<Float>, 
            path: Path, 
            fillPath: Path, 
            lineColor: Color
        ) {
            path.reset()
            fillPath.reset()

            var prevX = leftPadding
            var prevY = 0f

            amounts.forEachIndexed { index, amount ->
                val x = leftPadding + (index * stepX)
                val normalizedY = amount / maxVal
                val y = height - bottomPadding - (normalizedY * usableHeight)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    val controlX = prevX + (x - prevX) / 2f
                    path.cubicTo(
                        x1 = controlX,
                        y1 = prevY,
                        x2 = controlX,
                        y2 = y,
                        x3 = x,
                        y3 = y
                    )
                }
                prevX = x
                prevY = y
            }

            fillPath.addPath(path)
            fillPath.lineTo(prevX, height - bottomPadding)
            fillPath.lineTo(leftPadding, height - bottomPadding)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.25f),
                        lineColor.copy(alpha = 0.0f)
                    ),
                    startY = topPadding,
                    endY = height - bottomPadding
                )
            )

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw anchor points
            val outerRadius = 4.dp.toPx()
            val innerRadius = 2.dp.toPx()
            amounts.forEachIndexed { index, amount ->
                val x = leftPadding + (index * stepX)
                val normalizedY = amount / maxVal
                val y = height - bottomPadding - (normalizedY * usableHeight)
                val center = Offset(x, y)

                drawCircle(color = Color.White, radius = outerRadius, center = center)
                drawCircle(color = lineColor, radius = innerRadius, center = center)
            }
        }
        
        drawTrend(floatOut, cashOutPath, cashOutFillPath, cashOutColor)
        drawTrend(floatIn, cashInPath, cashInFillPath, cashInColor)
    }
}

/**
 * Summary section at the bottom of the Home screen displaying linked bank accounts.
 * Shows a compact horizontal scroll row (LazyRow) of bank names, card types, and masked numbers.
 */
@Composable
private fun LinkedAccountsSummarySection(
    bankAccounts: List<BankAccount>,
    isOnboardingActive: Boolean = false,
    onLinkAccountClick: () -> Unit = {}
) {
    val isDark = LocalDarkTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Linked Accounts Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (bankAccounts.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${bankAccounts.size} Linked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isOnboardingActive) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "No Accounts Linked",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Connect a bank or card to begin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        onClick = onLinkAccountClick,
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                        contentColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Link",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = bankAccounts,
                    key = { it.id }
                ) { bankAccount ->
                    LinkedAccountMiniCard(bankAccount = bankAccount)
                }
            }
        }
    }
}

/**
 * Persistent "Get Started" setup card displayed on the Overview screen
 * whenever a user has no linked bank accounts or cards.
 */
@Composable
fun OnboardingGetStartedCard(
    onLinkAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) SmartMoneyColors.DarkSlateGreen.copy(alpha = 0.5f) else SmartMoneyColors.PaleMintGreen
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GET STARTED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Text(
                    text = "🔒 256-bit Encrypted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Connect Your First Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your balance is currently KES 0.00 because no accounts are connected. Link your bank or card to activate live balance tracking, automated expense categorization, and budgets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLinkAccountClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                    contentColor = if (isDark) SmartMoneyColors.DarkSlateGreen else Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Connect Your First Account",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Compact horizontal preview card for each linked bank account.
 */
@Composable
private fun LinkedAccountMiniCard(bankAccount: BankAccount) {
    ElevatedCard(
        modifier = Modifier.width(170.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BankLogo(
                    bankName = bankAccount.bankName,
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = bankAccount.cardType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = bankAccount.bankName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = bankAccount.maskedAccountNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Formats monetary amounts in Kenyan Shillings (KES):
 * e.g., BigDecimal("23590.73") -> "KES 23,590.73"
 */
private fun formatKesCurrency(amount: BigDecimal): String {
    return CurrencyUtils.formatKes(amount)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    com.example.smartmoney.ui.theme.EnergyTheme {
        HomeScreen(
            userName = "User"
        )
    }
}
