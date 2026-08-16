package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 测试用 TagRepository：内存标签库，记录创建/删除/改名/改色 */
class FakeTagRepository(
    initialTags: List<Tag> = emptyList(),
) : TagRepository {
    val tags = initialTags.toMutableList()
    var createCount = 0

    override fun observeAll(): Flow<List<Tag>> = flowOf(tags.toList())

    override suspend fun create(name: String, color: Long): Long {
        createCount++
        val id = (tags.maxOfOrNull { it.id } ?: 0L) + 1
        tags += Tag(id = id, name = name, color = color, isCustom = true)
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        val index = tags.indexOfFirst { it.id == id }
        if (index >= 0) tags[index] = tags[index].copy(name = name)
    }

    override suspend fun updateColor(id: Long, color: Long) {
        val index = tags.indexOfFirst { it.id == id }
        if (index >= 0) tags[index] = tags[index].copy(color = color)
    }

    override suspend fun delete(id: Long) {
        tags.removeAll { it.id == id }
    }
}
