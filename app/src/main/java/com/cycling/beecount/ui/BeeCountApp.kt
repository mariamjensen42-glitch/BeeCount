package com.cycling.beecount.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cycling.beecount.ui.analytics.AnalyticsRoute
import com.cycling.beecount.ui.assistant.AssistantRoute
import com.cycling.beecount.ui.ledger.LedgerRoute
import com.cycling.beecount.ui.settings.SettingsRoute
import com.cycling.beecount.ui.theme.HoneyAmber
import com.cycling.beecount.ui.theme.OnHoneyAmber
import com.woowla.compose.icon.collections.heroicons.*
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.Solid
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.BookOpen
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.CalendarDays
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChartBar
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Cog6Tooth
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.BookOpen
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.CalendarDays
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.ChartBar
import com.woowla.compose.icon.collections.heroicons.heroicons.solid.Cog6Tooth

/**
 * 应用根导航：悬浮胶囊底部栏，「今日 / 账本 / 设置 / 图表」四个 tab（ADR 0009）。
 * 胶囊以覆盖层悬浮在内容之上（非 M3 NavigationBar），内容可滚动穿过胶囊下方。
 * 常规宽度显示图标与文字；窄宽或字体放大时自动退化为四个等宽图标 tab，
 * 避免把正常手机上的导航整体缩小来迁就最窄屏幕。
 */
@Composable
fun BeeCountApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "assistant",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("assistant") { AssistantRoute() }
            composable("ledger") { LedgerRoute() }
            composable("settings") { SettingsRoute() }
            composable("analytics") { AnalyticsRoute() }
        }
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
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 360dp 手机在默认字体下仍展示文字；320dp、横屏/分屏或放大字体时使用图标模式。
        val showLabels = maxWidth >= 320.dp && fontScale <= 1.15f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(28.dp),
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(28.dp),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PillTab(
                modifier = Modifier.weight(1f),
                label = "今日",
                selected = currentRoute == "assistant",
                outlineIcon = Heroicons.Outline.CalendarDays,
                solidIcon = Heroicons.Solid.CalendarDays,
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
                label = "设置",
                selected = currentRoute == "settings",
                outlineIcon = Heroicons.Outline.Cog6Tooth,
                solidIcon = Heroicons.Solid.Cog6Tooth,
                showLabel = showLabels,
                onClick = { onNavigate("settings") },
            )
            PillTab(
                modifier = Modifier.weight(1f),
                label = "图表",
                selected = currentRoute == "analytics",
                outlineIcon = Heroicons.Outline.ChartBar,
                solidIcon = Heroicons.Solid.ChartBar,
                showLabel = showLabels,
                onClick = { onNavigate("analytics") },
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
        targetValue = if (selected) HoneyAmber else Color.Transparent,
        label = "pillContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) OnHoneyAmber else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillContent",
    )
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) 10.dp else 0.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) solidIcon else outlineIcon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = content,
        )
        if (showLabel) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}
