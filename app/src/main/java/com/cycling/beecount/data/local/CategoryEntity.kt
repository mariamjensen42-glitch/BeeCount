package com.cycling.beecount.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.DEFAULT_CATEGORY_COLOR
import com.cycling.beecount.domain.model.EntryType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: EntryType,
    val isCustom: Boolean,
    /** 父分类 id，null 表示一级分类 */
    val parentId: Long? = null,
    /** Emoji 图标，空表示未设置（展示用） */
    val icon: String = "",
    /** ARGB 颜色 */
    val color: Long = DEFAULT_CATEGORY_COLOR,
    /** 手动拖拽排序序号，0 = 跟随使用频率自动排序 */
    val sortOrder: Int = 0,
    /** 是否从 AI 解析候选与常用选择中隐藏 */
    val isHidden: Boolean = false,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = type,
    isCustom = isCustom,
    parentId = parentId,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    isHidden = isHidden,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    isCustom = isCustom,
    parentId = parentId,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    isHidden = isHidden,
)
