package com.cycling.beecount.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 组件级设计 token：为常用组件集中提供默认样式。
 * 页面不直接写 `CardDefaults.cardColors(...)` 或散落的魔法数字，
 * 而是引用这里的语义化常量。
 */
object ComponentDefaults {

    // ===== 间距（与 Spacing 对齐的别名，方便组件语义引用） =====
    // 页面级
    val PagePadding = Spacing.lg // 16dp，页面左右边距
    val PageSpacing = Spacing.lg // 16dp，卡片间间隔
    val ContentSectionSpacing = Spacing.xxl // 24dp，区块间隔

    // 卡片级
    val CardPadding = Spacing.lg // 16dp，卡片内容 padding
    val CardInternalSpacing = Spacing.md // 12dp，卡片内容行间隔

    // ===== Card =====
    /** 标准内容卡片容器色 */
    val CardContainerColor @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    /** 超预算警示卡片容器色 */
    val BudgetWarningContainerColor @Composable get() =
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)

    /** 标准内容卡片 colors */
    @Composable
    fun cardColors(): CardColors = CardDefaults.cardColors(containerColor = CardContainerColor)

    /** 超预算警示卡片 colors */
    @Composable
    fun budgetWarningColors(): CardColors = CardDefaults.cardColors(
        containerColor = BudgetWarningContainerColor,
    )

    /** 标准内容卡片 shape */
    val cardShape = BeeCountShapes.small

    // ===== 底部导航胶囊 =====
    /** 悬浮底部胶囊整体圆角 */
    val pillShape = BeeCountShapes.extraLarge

    /** 悬浮底部胶囊内 tab 按钮圆角 */
    val pillTabShape = BeeCountShapes.medium

    /** 悬浮底部胶囊阴影高度 */
    val pillElevation: Dp = 12.dp
}
