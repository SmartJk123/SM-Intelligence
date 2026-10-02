package com.example.smartmoney.ui.home

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
    onSimulateInflow: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> },
    onSimulateOutflow: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _ -> },
    unreadNotificationCount: Int = uiState.unreadNotificationCount,
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = onSettingsClick,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val scrollState = rememberScrollState()
    val profileBitmap by UserProfileManager.profileBitmap.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var isBalanceVisible by remember { mutableStateOf(true) }

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
                                bankAccounts = uiState.bankAccounts
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
            // SIMULATION ACTION BAR (Moved between main card and cash flow graph)
            // =========================================================================
            SimulationActionBar(
                isSimulatingInflow = uiState.isSimulatingInflow,
                isSimulatingOutflow = uiState.isSimulatingOutflow,
                onSimulateInflow = {
                    if (uiState.isSimulatingInflow) return@SimulationActionBar
                    onSimulateInflow(
                        {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("⚡ Simulated KCB inflow of KES 1,000 received!")
                            }
                        },
                        { error ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Simulation failed: $error")
                            }
                        }
                    )
                },
                onSimulateOutflow = {
                    if (uiState.isSimulatingOutflow) return@SimulationActionBar
                    onSimulateOutflow(
                        {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("⚡ Simulated KCB outflow of KES 500 debited!")
                            }
                        },
                        { error ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Simulation failed: $error")
                            }
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Cash In Trend (Income Trend Graph Card) - detached from banner
            CashFlowTrendGraphCard(trendPoints = uiState.trend)

            Spacer(modifier = Modifier.height(20.dp))

            // Linked Accounts Summary Section
            LinkedAccountsSummarySection(bankAccounts = uiState.bankAccounts)

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

                // Dark green pill-shaped badge: "↑ 24% Last week"
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
                            text = "24%",
                            color = if (isDark) SmartMoneyColors.PaleMintGreen else MutedSageCard,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Last week",
                            color = Color.White.copy(alpha = 0.92f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
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

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDark) Color(0xFF143823) else SmartMoneyColors.PaleMintGreen
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(themeGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Real-time",
                            style = MaterialTheme.typography.labelSmall,
                            color = themeGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (bankAccounts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No connected bank or payment apps yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
private fun LinkedAccountsSummarySection(bankAccounts: List<BankAccount>) {
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

        if (bankAccounts.isEmpty()) {
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
                    Text(
                        text = "No linked bank accounts yet. Link an account from the Accounts tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
 * Quick Simulation Action Bar placed prominently between the main card and the cash flow graph.
 * Allows instant triggering of KCB Inflow (+ KES 1,000) and Outflow (- KES 500) transactions.
 */
@Composable
private fun SimulationActionBar(
    isSimulatingInflow: Boolean,
    isSimulatingOutflow: Boolean,
    onSimulateInflow: () -> Unit,
    onSimulateOutflow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isDark) SmartMoneyColors.DarkSurfaceElevated else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Lightning Icon + Title + Live Sandbox Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDark) SmartMoneyColors.DarkSlateGreen.copy(alpha = 0.5f)
                                else SmartMoneyColors.PaleMintGreen
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (isDark) SmartMoneyColors.PaleMintGreen else SmartMoneyColors.DarkSlateGreen,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transaction Simulation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDark) Color(0xFF1B3828) else SmartMoneyColors.PaleMintGreen
                ) {
                    Text(
                        text = "KCB Sandbox",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color(0xFF4ADE80) else SmartMoneyColors.DarkSlateGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row: Inflow (+1,000) & Outflow (-500)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SimulationButton(
                    modifier = Modifier.weight(1f),
                    label = "+ Inflow",
                    amountSubtitle = "+ KES 1,000",
                    isCredit = true,
                    isLoading = isSimulatingInflow,
                    onClick = onSimulateInflow
                )

                SimulationButton(
                    modifier = Modifier.weight(1f),
                    label = "- Outflow",
                    amountSubtitle = "- KES 500",
                    isCredit = false,
                    isLoading = isSimulatingOutflow,
                    onClick = onSimulateOutflow
                )
            }
        }
    }
}

@Composable
private fun SimulationButton(
    modifier: Modifier = Modifier,
    label: String,
    amountSubtitle: String,
    isCredit: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val accentColor = if (isCredit) {
        if (isDark) Color(0xFF4ADE80) else Color(0xFF1B6A3E)
    } else {
        if (isDark) Color(0xFFFF6B6B) else Color(0xFFB3261E)
    }

    val containerColor = if (isCredit) {
        if (isDark) Color(0xFF143321) else SmartMoneyColors.PaleMintGreen.copy(alpha = 0.55f)
    } else {
        if (isDark) Color(0xFF381A1A) else SmartMoneyColors.LightSalmon.copy(alpha = 0.45f)
    }

    val borderColor = if (isCredit) {
        if (isDark) Color(0xFF225B36) else Color(0xFF4CAF50).copy(alpha = 0.4f)
    } else {
        if (isDark) Color(0xFF5E2727) else Color(0xFFF44336).copy(alpha = 0.4f)
    }

    Surface(
        onClick = onClick,
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = amountSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
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
