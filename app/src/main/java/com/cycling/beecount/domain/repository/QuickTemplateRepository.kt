package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.QuickTemplate
import kotlinx.coroutines.flow.Flow

/**
 * 快捷模板仓库接口：Domain 层定义，Data 层以 DataStore 实现。
 */
interface QuickTemplateRepository {
    fun observeAll(): Flow<List<QuickTemplate>>

    suspend fun add(template: QuickTemplate): Long

    suspend fun update(template: QuickTemplate)

    suspend fun delete(id: Long)
}
