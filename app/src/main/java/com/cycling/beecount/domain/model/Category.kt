package com.cycling.beecount.domain.model

/**
 * 账目类别：由预定义类别与用户自定义类别共同构成，支持二级层级。
 *
 * [parentId] 非空时表示该类别是某父类别的子分类，其名称以「父·子」完整路径作为账目类别快照。
 * [icon] 为 Emoji 图标（预置与自定义均可设置）；[color] 为 ARGB 色值。
 * [sortOrder] 为手动拖拽排序序号，0 表示「跟随使用频率自动排序」；同类型内按「手动的先、未手动的按使用频率降序」展示。
 * [isHidden] 为 true 时该类别从 AI 解析候选与常用选择中隐藏（已入账历史不受影响）。
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val type: EntryType,
    val isCustom: Boolean = false,
    val parentId: Long? = null,
    val icon: String = "",
    val color: Long = DEFAULT_CATEGORY_COLOR,
    val sortOrder: Int = 0,
    val isHidden: Boolean = false,
) {
    /** 是否为子分类 */
    val hasParent: Boolean get() = parentId != null

    /** 展示名：子分类只显示叶子名，一级分类显示本名（账目快照用完整路径） */
    val displayName: String
        get() = if (hasParent) {
            name.substringAfterLast(CATEGORY_PATH_SEPARATOR)
        } else {
            name
        }
}

/** 账目类别快照的「父·子」路径分隔符 */
const val CATEGORY_PATH_SEPARATOR: String = "·"

/** 默认颜色：中性灰（未选择颜色时的兜底） */
const val DEFAULT_CATEGORY_COLOR: Long = 0xFF90A4AE

/**
 * 新建分类取色板（暗色主题下可辨识），参照标签取色逻辑。
 */
val CATEGORY_COLOR_PALETTE: List<Long> = listOf(
    0xFFFF8A80, // 红
    0xFFFFB74D, // 橙
    0xFFFFD54F, // 黄
    0xFFAED581, // 绿
    0xFF4DB6AC, // 青绿
    0xFF4FC3F7, // 蓝
    0xFF7986CB, // 靛蓝
    0xFFBA68C8, // 紫
    0xFFF06292, // 粉
    0xFFA1887F, // 棕
)

/** 取第一个未被 [usedColors] 占用的板色；全部占用时回到第一个 */
fun nextCategoryColor(usedColors: Set<Long>): Long =
    CATEGORY_COLOR_PALETTE.firstOrNull { it !in usedColors } ?: CATEGORY_COLOR_PALETTE.first()
