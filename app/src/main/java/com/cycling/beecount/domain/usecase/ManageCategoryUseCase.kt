package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.CATEGORY_PATH_SEPARATOR
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * 用例：类别管理（设置页）。
 *
 * 支持一级/二级分类的创建、重命名、删除（历史归并）、图标/颜色、手动排序与隐藏。
 * 删除类别会把其历史账目（含子分类）归并到用户指定的目标分类（ADR 0008 的升级：账目仍是类别名快照，归并通过改写快照实现）。
 */
class ManageCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun create(name: String, type: EntryType): Long {
        val trimmed = requireName(name)
        return categoryRepository.create(name = trimmed, type = type)
    }

    suspend fun createChild(parentId: Long, name: String): Long =
        categoryRepository.createChild(parentId, requireName(name))

    suspend fun rename(id: Long, name: String) {
        val trimmed = requireName(name)
        categoryRepository.rename(id, trimmed)
    }

    /** 删除类别并把历史账目归并到 [targetId]。[targetId] 不能与 [id] 相同 */
    suspend fun deleteWithMerge(id: Long, targetId: Long) =
        categoryRepository.deleteWithMerge(id, targetId)

    suspend fun moveParent(id: Long, parentId: Long?) =
        categoryRepository.moveParent(id, parentId)

    suspend fun updateIcon(id: Long, icon: String) = categoryRepository.updateIcon(id, icon)

    suspend fun updateColor(id: Long, color: Long) = categoryRepository.updateColor(id, color)

    suspend fun updateSortOrder(id: Long, sortOrder: Int) =
        categoryRepository.updateSortOrder(id, sortOrder)

    suspend fun updateHidden(id: Long, isHidden: Boolean) =
        categoryRepository.updateHidden(id, isHidden)

    private fun requireName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "类别名不能为空" }
        require(!trimmed.contains(CATEGORY_PATH_SEPARATOR)) {
            "类别名不能包含「$CATEGORY_PATH_SEPARATOR」"
        }
        return trimmed
    }
}
