package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import kotlinx.coroutines.flow.Flow

/**
 * 类别仓库接口：预定义类别 + 用户自定义类别，支持二级层级、图标/颜色、手动排序与隐藏。
 */
interface CategoryRepository {
    /** 观察全部类别（含预定义与自定义），已按「手动的先、未手动的按使用频率降序」排好序 */
    fun observeAll(): Flow<List<Category>>

    /** 创建自定义一级分类，返回新类别 id */
    suspend fun create(name: String, type: EntryType): Long

    /** 在 [parentId] 分类下创建二级子分类，返回新类别 id；[parentId] 必须是一级分类 */
    suspend fun createChild(parentId: Long, name: String): Long

    /** 重命名类别：[name] 为叶子新名；若是一级分类会级联更新其子分类路径与相应账目快照 */
    suspend fun rename(id: Long, name: String)

    /** 删除类别并把其历史账目（含子分类）归并到 [targetId] 名下 */
    suspend fun deleteWithMerge(id: Long, targetId: Long)

    /** 调整分类的父级（把子分类移动到另一一级分类下；向一级分类则传 null） */
    suspend fun moveParent(id: Long, parentId: Long?)

    suspend fun updateIcon(id: Long, icon: String)

    suspend fun updateColor(id: Long, color: Long)

    /** 设置手动拖拽顺序；[sortOrder] = 0 表示回到「按使用频率自动排序」 */
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /** 隐藏/显示分类（隐藏后从 AI 解析候选与常用选择中剔除） */
    suspend fun updateHidden(id: Long, isHidden: Boolean)
}
