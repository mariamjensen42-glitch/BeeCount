package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 账目仓库接口：Domain 层定义，Data 层以 Room 实现。
 */
interface EntryRepository {
    /** 观察某一天的全部账目 */
    fun observeEntriesOn(date: LocalDate): Flow<List<Entry>>

    /** 观察某一天的支出/收入合计 */
    fun observeTotalsOn(date: LocalDate): Flow<TodayTotals>

    /** 账本页：观察全部账目（时间倒序，带各自标签） */
    fun observeAllWithTags(): Flow<List<Entry>>

    suspend fun add(entry: Entry): Long

    /** 入库账目并写入标签关联，同事务 */
    suspend fun addWithTags(entry: Entry, tagIds: List<Long>): Long

    suspend fun delete(id: Long)

    /** 清空全部账目（ADR 0008：只清账目，类别/标签保留） */
    suspend fun clearAll()
}

data class TodayTotals(
    val expense: Double = 0.0,
    val income: Double = 0.0,
)
