package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import kotlinx.coroutines.flow.Flow

/**
 * 类别仓库接口：预定义类别 + 用户自定义类别。
 */
interface CategoryRepository {
    /** 观察全部类别（含预定义与自定义） */
    fun observeAll(): Flow<List<Category>>

    /** 创建自定义类别，返回新类别 id */
    suspend fun create(name: String, type: EntryType): Long

    suspend fun rename(id: Long, name: String)

    /** 删除类别：账目存的是类别名快照，已有账目不受影响 */
    suspend fun delete(id: Long)
}
