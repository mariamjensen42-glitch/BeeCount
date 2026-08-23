package com.cycling.beecount.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 统一尺寸 token：图标、控件高度、圆角补充、通用尺寸等。
 *
 * 主要覆盖 Shape.kt / Spacing.kt 未涵盖的“组件自带尺寸”，
 * 例如图标大小、最小触控区域、图表元素尺寸等。
 */
object Dimens {
    // ===== 图标 =====
    /** 18dp — 小图标（提示、次要操作） */
    val IconSmall = 18.dp

    /** 20dp — 标准图标（导航、行内操作） */
    val IconDefault = 20.dp

    /** 24dp — 较大图标（标题区、强调图标） */
    val IconLarge = 24.dp

    // ===== 控件高度 =====
    /** 34dp — 紧凑 IconButton */
    val MinIconButton = 34.dp

    /** 36dp — 紧凑 Chip/选择点 */
    val MinChip = 36.dp

    /** 38dp — 小圆点选择器 */
    val MinDot = 38.dp

    /** 48dp — M3 最小触控高度（导航、按钮） */
    val MinTouchTarget = 48.dp

    /** 52dp — 较大行内元素（标签行头像） */
    val RowAvatar = 52.dp

    // ===== 卡牌内元素 =====
    /** 28dp — 行内小标签/标签点 */
    val RowElement = 28.dp

    // ===== 底部导航胶囊 =====
    /** 12.dp — 胶囊阴影/标高 */
    val PillShadowElevation = 12.dp

    // ===== 图表辅助 =====
    /** 112dp — 图表卡内标签宽度 */
    val ChartLabelWidth = 112.dp

    /** 76dp — 排行标签宽度 */
    val RankLabelWidth = 76.dp

    /** 120dp — 每日支出柱图高度 */
    val DailyTrendHeight = 120.dp

    /** 140dp — 月度支出折线图高度 */
    val YearTrendHeight = 140.dp

    /** 34dp — 进度条最小高度 */
    val ProgressBarHeight = 34.dp

    /** 10dp — 进度条高度 */
    val BarHeight = 10.dp

    /** 3dp — 图表小圆角 */
    val ChartCorner = 3.dp

    /** 5dp — 图表条/热力图小圆角 */
    val ChartBarCorner = 5.dp

    /** 6dp — 柱状条顶部圆角 */
    val BarTopCorner = 6.dp

    /** 2dp — 极小的圆角（分类色块、热力图单元格） */
    val DotCorner = 2.dp

    /** 14dp — 热力图单元格尺寸 */
    val HeatmapCell = 14.dp

    /** 17dp — 热力图轴标签高度/宽度 */
    val HeatmapAxis = 17.dp

    /** 14.dp — 标签行圆角 */
    val TagRowCorner = 14.dp
}
