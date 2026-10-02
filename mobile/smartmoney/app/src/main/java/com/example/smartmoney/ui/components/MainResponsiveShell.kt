package com.example.smartmoney.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartmoney.data.local.UserProfileManager
import com.example.smartmoney.ui.navigation.Screen
import com.example.smartmoney.ui.theme.LocalDarkTheme
import com.example.smartmoney.ui.theme.SmartMoneyColors
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.zIndex
import com.example.smartmoney.domain.model.Notification
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * CompositionLocal providing current visibility state of top app bar across screens.
 * Driven by nested scroll events to vanish the top bar when the user scrolls down through content.
 */
val LocalTopBarVisible = compositionLocalOf { true }

@Composable
fun MainResponsiveShell(
    navController: NavHostController = rememberNavController(),
    userName: String = "User",
    pagerState: PagerState? = null,
    unreadNotificationCount: Int = 0,
    activeBannerNotification: Notification? = null,
    onBannerDismiss: () -> Unit = {},
    onBannerClick: (Notification) -> Unit = {},
    onSignOut: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val navRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val previousRoute = navController.previousBackStackEntry?.destination?.route
    val profileBitmap by UserProfileManager.profileBitmap.collectAsState()

    val bottomBarScreens = remember { Screen.bottomBarScreens }
    val bottomBarRoutes = remember { bottomBarScreens.map { it.route }.toSet() }

    // When at the root pager destination, active route tracks the pager's current page
    val activeBottomScreen = if (pagerState != null && pagerState.currentPage in bottomBarScreens.indices) {
        bottomBarScreens[pagerState.currentPage]
    } else {
        Screen.Dashboard
    }

    val currentRoute = if (navRoute == Screen.Dashboard.route) {
        activeBottomScreen.route
    } else {
        navRoute
    }

    val isBottomBarScreen = currentRoute in bottomBarRoutes
    val isOverview = currentRoute == Screen.Dashboard.route
    val canNavigateBack = !isBottomBarScreen || previousRoute == Screen.Menu.route

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isDark = LocalDarkTheme.current
    val drawerBg = if (isDark) SmartMoneyColors.DeepNavy else SmartMoneyColors.AzurePrimary

    var isBottomBarCollapsed by rememberSaveable { mutableStateOf(false) }
    var isTopBarVisible by rememberSaveable { mutableStateOf(true) }
    var isScrolling by remember { mutableStateOf(false) }
    var scrollStopJob by remember { mutableStateOf<Job?>(null) }

    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 6.dp.toPx() } }

    val nestedScrollConnection = remember(scrollThresholdPx, scope) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (abs(delta) > 1f) {
                    isScrolling = true
                    scrollStopJob?.cancel()
                    scrollStopJob = scope.launch {
                        delay(250)
                        isScrolling = false
                    }
                }
                if (delta < -scrollThresholdPx) {
                    // Scrolling down (content moving up) -> hide top bar & collapse bottom bar
                    isBottomBarCollapsed = true
                    isTopBarVisible = false
                } else if (delta > scrollThresholdPx) {
                    // Scrolling up (content moving down) -> show top bar & expand bottom bar
                    isBottomBarCollapsed = false
                    isTopBarVisible = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (abs(consumed.y) > 1f) {
                    isScrolling = true
                    scrollStopJob?.cancel()
                    scrollStopJob = scope.launch {
                        delay(250)
                        isScrolling = false
                    }
                }
                if (available.y > 0) {
                    // Reached the top of the content and continuing to pull down
                    isTopBarVisible = true
                    isBottomBarCollapsed = false
                }
                return Offset.Zero
            }
        }
    }

    // Reset top bar and bottom bar to visible/expanded state whenever route or active page changes
    LaunchedEffect(currentRoute, pagerState?.currentPage) {
        isBottomBarCollapsed = false
        isTopBarVisible = true
        isScrolling = false
        scrollStopJob?.cancel()
    }

    fun navigateTo(targetRoute: String) {
        if (targetRoute in bottomBarRoutes) {
            val targetIndex = bottomBarScreens.indexOfFirst { it.route == targetRoute }
            if (targetIndex >= 0) {
                if (navRoute != Screen.Dashboard.route) {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                }
                if (pagerState != null) {
                    scope.launch {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                }
            }
        } else {
            if (currentRoute != targetRoute) {
                navController.navigate(targetRoute) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = drawerBg) {
                AppSidebar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        scope.launch { drawerState.close() }
                        navigateTo(screen.route)
                    },
                    onSignOut = onSignOut
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = if (isBottomBarScreen || currentRoute == Screen.Notifications.route) {
                    WindowInsets(0, 0, 0, 0)
                } else {
                    ScaffoldDefaults.contentWindowInsets
                },
                topBar = {
                    if (!isBottomBarScreen && currentRoute != Screen.Notifications.route) {
                        AppTopBar(
                            title = Screen.allScreens.find { it.route == currentRoute }?.title ?: "Overview",
                            userName = userName,
                            profileBitmap = profileBitmap,
                            isOverview = false,
                            canNavigateBack = canNavigateBack,
                            onBackClick = { navController.popBackStack() },
                            onProfileClick = { navigateTo(Screen.Settings.route) },
                            onNotificationsClick = { navigateTo(Screen.Notifications.route) },
                            onSettingsClick = { navigateTo(Screen.Settings.route) },
                            unreadNotificationCount = unreadNotificationCount
                        )
                    }
                }
            ) { innerPadding ->
                CompositionLocalProvider(LocalTopBarVisible provides isTopBarVisible) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        content(innerPadding)

                        if (isBottomBarScreen) {
                            AppBottomNavigationBar(
                                currentRoute = currentRoute,
                                onNavigate = { screen ->
                                    navigateTo(screen.route)
                                },
                                isCollapsed = isBottomBarCollapsed,
                                isScrolling = isScrolling,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }

            // Top Status Bar Tint / Protection Scrim for all post-auth screens
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight + 12.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.38f),
                                Color.Black.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // In-App Notification Dropdown Banner
            InAppNotificationBannerHost(
                bannerNotification = activeBannerNotification,
                onDismiss = onBannerDismiss,
                onClick = onBannerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .zIndex(100f)
            )
        }
    }
}
