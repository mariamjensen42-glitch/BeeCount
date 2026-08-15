package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * 标签仓库接口：Domain 层定义，Data 层以 Room 实现（ADR 0007）。
 */
interface TagRepository {
    /** 观察全部标签（含颜色） */
    fun observeAll(): Flow<List<Tag>>

    /** 创建自定义标签，返回新标签 id */
    suspend fun create(name: String, color: Long): Long

    suspend fun rename(id: Long, name: String)

    suspend fun updateColor(id: Long, color: Long)

    /** 删除标签；账目与标签的关联随之清除，账目本身保留 */
    suspend fun delete(id: Long)
}
