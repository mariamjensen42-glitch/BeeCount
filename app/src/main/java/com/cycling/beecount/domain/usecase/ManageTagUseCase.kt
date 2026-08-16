package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.nextTagColor
import com.cycling.beecount.domain.repository.TagRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 用例：标签管理（改名/改色/删除/新建），来自账本页「管理标签」入口。
 * 新建标签从 8 色板顺序取第一个未被占用的颜色（ADR 0007）。
 */
class ManageTagUseCase @Inject constructor(
    private val tagRepository: TagRepository,
) {

    suspend fun create(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        val usedColors = tagRepository.observeAll().first().map { it.color }.toSet()
        return tagRepository.create(name = trimmed, color = nextTagColor(usedColors))
    }

    suspend fun rename(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        tagRepository.rename(id, trimmed)
    }

    suspend fun updateColor(id: Long, color: Long) = tagRepository.updateColor(id, color)

    /** 删除标签：账目保留，关联随外键级联清除 */
    suspend fun delete(id: Long) = tagRepository.delete(id)
}
