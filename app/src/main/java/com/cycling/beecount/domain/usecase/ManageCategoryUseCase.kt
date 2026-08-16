package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * 用例：类别管理（设置页）。
 * 删除类别不影响已有账目——账目存的是类别名快照（ADR 0008）。
 */
class ManageCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {

    suspend fun create(name: String, type: EntryType): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "类别名不能为空" }
        return categoryRepository.create(name = trimmed, type = type)
    }

    suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "类别名不能为空" }
        categoryRepository.rename(id, trimmed)
    }

    suspend fun delete(id: Long) = categoryRepository.delete(id)
}
