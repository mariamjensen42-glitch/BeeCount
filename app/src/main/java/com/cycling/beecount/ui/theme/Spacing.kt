package com.cycling.beecount.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距规模：基于 4dp 网格的常用间距 token。
 *
 * 所有布局间距（padding、Spacer、Arrangement.spacedBy）优先从这里取值，
 * 避免在页面内散落魔法数字。8dp 基础步进，细分场景用 2dp 粒度补足。
 */
object Spacing {
    /** 4dp — 最紧凑的间隙，用于元素内部的微型间隔 */
    val xxs = 4.dp

    /** 6dp — 图标与文字间的间隙、紧凑行内间隔 */
    val xs = 6.dp

    /** 8dp — 行内元素间隔、列表项内 padding */
    val sm = 8.dp

    /** 10dp — 标签/筛选 Chip 之间的间隔 */
    val mdSm = 10.dp

    /** 12dp — 常用行内间隔、block 内 padding */
    val md = 12.dp

    /** 14dp — 列表行内部水平 padding */
    val lgSm = 14.dp

    /** 16dp — 页面/卡片标准内外边距 */
    val lg = 16.dp

    /** 20dp — 分组间距、列表与页面之间的间隔 */
    val xl = 20.dp

    /** 24dp — 大区块分隔、对话框内边距 */
    val xxl = 24.dp

    /** 28dp — 特殊宽场景（图表轴标签） */
    val xxlSm = 28.dp

    /** 32dp — 大区块留白、空态垂直留白 */
    val xxxl = 32.dp

    /** 48dp — 页面/区块间的大留白 */
    val huge = 48.dp
}
