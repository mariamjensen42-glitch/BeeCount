package com.cycling.beecount.domain.model

/**
 * 标签：账目的附加分类标记（见 CONTEXT.md「标签」）。
 * 一笔记账可带多个标签；颜色存于标签库、由账目-标签关联引用，改色全局生效。
 */
data class Tag(
    val id: Long = 0L,
    val name: String,
    val color: Long,
    val isCustom: Boolean = true,
)

/**
 * 新建标签取色板（8 色，暗色主题下可辨识）。
 * 新标签按此顺序取第一个未被占用的颜色；全占用则回到第一个（循环）。
 */
val TAG_COLOR_PALETTE: List<Long> = listOf(
    0xFF81C784, // 绿
    0xFF64B5F6, // 蓝
    0xFFFFB74D, // 橙
    0xFF4DB6AC, // 青绿
    0xFFBA68C8, // 紫
    0xFFF06292, // 粉
    0xFFD4A35A, // 金棕
    0xFF90A4AE, // 灰蓝
)

/** 取第一个未被 [usedColors] 占用的板色；全部占用时回到第一个 */
fun nextTagColor(usedColors: Set<Long>): Long =
    TAG_COLOR_PALETTE.firstOrNull { it !in usedColors } ?: TAG_COLOR_PALETTE.first()
