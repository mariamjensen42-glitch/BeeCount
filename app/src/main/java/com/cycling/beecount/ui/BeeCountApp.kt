package com.cycling.beecount.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.cycling.beecount.ui.analytics.AnalyticsRoute
import com.cycling.beecount.ui.assistant.AssistantRoute
import com.cycling.beecount.ui.budget.BudgetCreateRoute
import com.cycling.beecount.ui.budget.BudgetManageRoute
import com.cycling.beecount.ui.calendar.CalendarRoute
import com.cycling.beecount.ui.ledger.LedgerRoute
import com.cycling.beecount.ui.settings.CategoryManageRoute
import com.cycling.beecount.ui.settings.SettingsRoute
import com.cycling.beecount.ui.settings.TagManageRoute
import com.cycling.beecount.ui.theme.ComponentDefaults
import com.cycling.beecount.ui.theme.Dimens
import com.cycling.beecount.ui.theme.TerminalCyan
import com.cycling.beecount.ui.theme.OnTerminalCyan
import com.cycling.beecount.ui.theme.Spacing
import com.woowla.compose.icon.collections.heroicons.*
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.Solid
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.BookOpen
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.CalendarDays
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChartBar
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChatBubbleLeftRight
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Cog6Tooth
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.BookOpen
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.CalendarDays
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.ChartBar
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.ChatBubbleLeftRight
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.Cog6Tooth

/**
 * 应用根导航：悬浮胶囊底部栏，「今日 / 账本 / 设置 / 图表」四个 tab（ADR 0009）。
 * 胶囊以覆盖层悬浮在内容之上（非 M3 NavigationBar），内容可滚动穿过胶囊下方。
 * 常规宽度显示图标与文字；窄宽或字体放大时自动退化为四个等宽图标 tab，
 * 避免把正常手机上的导航整体缩小来迁就最窄屏幕。
 */
/**
 * 悬浮胶囊底部栏占用的屏幕高度（含底部 12dp 留白与胶囊自身高度）。
 * 页面内 snackbar 等底部浮层需垫高到它上方，否则会被胶囊遮住；账本页内容 padding 同口径。
 */
internal val FLOATING_PILL_CLEARANCE = 84.dp

/** 顶层 tab 路由：抽屉式详情页（如 budget-manage）与顶层 tab 的过渡方向以此区分。 */
private val topLevelRoutes = setOf("assistant", "ledger", "settings", "calendar")

@Composable
fun BeeCountApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "ledger",
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                val targetRoute = targetState.destination.route
                val initialRoute = initialState.destination.route
                when {
                    // 从顶层 tab 深入详情页：从右向左滑入（覆盖式 push）
                    initialRoute in topLevelRoutes && targetRoute !in topLevelRoutes ->
                        slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(240)) + fadeIn(tween(240))
                    // tab 之间：轻微滑入
                    else ->
                        slideInHorizontally(initialOffsetX = { it / 8 }, animationSpec = tween(200)) + fadeIn(tween(200))
                }
            },
            exitTransition = {
                val targetRoute = targetState.destination.route
                val initialRoute = initialState.destination.route
                when {
                    // 从顶层 tab 深入详情页：顶层向左侧轻微退场
                    initialRoute in topLevelRoutes && targetRoute !in topLevelRoutes ->
                        slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = tween(240)) + fadeOut(tween(240))
                    // tab 之间：轻微退场
                    else ->
                        slideOutHorizontally(targetOffsetX = { -it / 8 }, animationSpec = tween(200)) + fadeOut(tween(200))
                }
            },
            popEnterTransition = {
                val targetRoute = targetState.destination.route
                val initialRoute = initialState.destination.route
                when {
                    // 从详情页返回顶层 tab：顶层从左侧滑入
                    targetRoute in topLevelRoutes && initialRoute !in topLevelRoutes ->
                        slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = tween(240)) + fadeIn(tween(240))
                    else ->
                        fadeIn(tween(200))
                }
            },
            popExitTransition = {
                val targetRoute = targetState.destination.route
                val initialRoute = initialState.destination.route
                when {
                    // 从详情页返回顶层 tab：被移除的详情页向右滑出
                    targetRoute in topLevelRoutes && initialRoute !in topLevelRoutes ->
                        slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(240)) + fadeOut(tween(240))
                    else ->
                        fadeOut(tween(200))
                }
            },
        ) {
            composable("assistant") { AssistantRoute() }
            composable(
                route = "assistant/{prefillDate}",
                arguments = listOf(navArgument("prefillDate") { type = NavType.StringType }),
            ) { entry ->
                AssistantRoute(
                    prefillDate = java.time.LocalDate.parse(requireNotNull(entry.arguments?.getString("prefillDate"))),
                    onEntrySaved = { navController.popBackStack("calendar", inclusive = false) },
                )
            }
            composable("ledger") {
                LedgerRoute(onOpenTagManage = { navController.navigate("tag-manage") })
            }
            composable("settings") {
                SettingsRoute(
                    onOpenCategoryManage = { navController.navigate("category-manage") },
                    onOpenTagManage = { navController.navigate("tag-manage") },
                    onOpenBudgetManage = { navController.navigate("budget-manage") },
                )
            }
            composable("category-manage") {
                CategoryManageRoute(onBack = { navController.popBackStack() })
            }
            composable("tag-manage") {
                TagManageRoute(onBack = { navController.popBackStack() })
            }
            composable("budget-manage") {
                BudgetManageRoute(
                    onBack = { navController.popBackStack() },
                    onCreateBudget = { navController.navigate("budget-create") },
                )
            }
            composable("budget-create") {
                BudgetCreateRoute(onBack = { navController.popBackStack() })
            }
            composable("calendar") {
                CalendarRoute(
                    onOpenAnalytics = { month -> navController.navigate("analytics/$month") },
                    onAddEntry = { date -> navController.navigate("assistant/$date") },
                )
            }
            composable(
                route = "analytics/{month}",
                arguments = listOf(navArgument("month") { type = NavType.StringType }),
            ) {
                AnalyticsRoute(
                    initialMonth = java.time.YearMonth.parse(requireNotNull(it.arguments?.getString("month"))),
                    onBack = { navController.popBackStack() },
                )
            }
        }
        if (currentRoute in setOf("assistant", "ledger", "settings", "calendar")) {
            FloatingPillBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun FloatingPillBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.md)
            .padding(bottom = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        // 360dp 手机在默认字体下仍展示文字；320dp、横屏/分屏或放大字体时使用图标模式。
        val showLabels = maxWidth >= 320.dp && fontScale <= 1.15f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .shadow(
                    elevation = ComponentDefaults.pillElevation,
                    shape = ComponentDefaults.pillShape,
                )
                .clip(ComponentDefaults.pillShape)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = ComponentDefaults.pillShape,
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = ComponentDefaults.pillShape,
                )
                .padding(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PillTab(
                modifier = Modifier.weight(1f),
                label = "今日",
                selected = currentRoute == "assistant",
                outlineIcon = Heroicons.Outline.ChatBubbleLeftRight,
                solidIcon = Heroicons.Solid.ChatBubbleLeftRight,
                showLabel = showLabels,
                onClick = { onNavigate("assistant") },
            )
            PillTab(
                modifier = Modifier.weight(1f),
                label = "账本",
                selected = currentRoute == "ledger",
                outlineIcon = Heroicons.Outline.BookOpen,
                solidIcon = Heroicons.Solid.BookOpen,
                showLabel = showLabels,
                onClick = { onNavigate("ledger") },
            )
            PillTab(
                modifier = Modifier.weight(1f),
                label = "日历",
                selected = currentRoute == "calendar",
                outlineIcon = Heroicons.Outline.CalendarDays,
                solidIcon = Heroicons.Solid.CalendarDays,
                showLabel = showLabels,
                onClick = { onNavigate("calendar") },
            )
            PillTab(
                modifier = Modifier.weight(1f),
                label = "设置",
                selected = currentRoute == "settings",
                outlineIcon = Heroicons.Outline.Cog6Tooth,
                solidIcon = Heroicons.Solid.Cog6Tooth,
                showLabel = showLabels,
                onClick = { onNavigate("settings") },
            )
        }
    }
}

@Composable
private fun PillTab(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    outlineIcon: ImageVector,
    solidIcon: ImageVector,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) TerminalCyan else Color.Transparent,
        label = "pillContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) OnTerminalCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillContent",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillIconScale",
    )
    Row(
        modifier = modifier
            .heightIn(min = Dimens.MinTouchTarget)
            .clip(ComponentDefaults.pillTabShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) Spacing.mdSm else 0.dp, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) solidIcon else outlineIcon,
            contentDescription = label,
            modifier = Modifier.size(Dimens.IconDefault).scale(iconScale),
            tint = content,
        )
        if (showLabel) {
            Text(
                text = label,
                modifier = Modifier.padding(start = Spacing.xs),
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}
