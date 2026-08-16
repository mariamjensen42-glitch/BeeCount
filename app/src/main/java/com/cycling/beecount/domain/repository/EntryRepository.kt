package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 账目仓库接口：Domain 层定义，Data 层以 Room 实现。
 */
interface EntryRepository {
    /** 观察某一天的全部账目（带各自标签） */
    fun observeEntriesOn(date: LocalDate): Flow<List<Entry>>

    /** 观察某一天的支出/收入合计 */
    fun observeTotalsOn(date: LocalDate): Flow<TodayTotals>

    /** 账本页：观察全部账目（时间倒序，带各自标签） */
    fun observeAllWithTags(): Flow<List<Entry>>

    /** 图表页：观察 [start, end] 区间内的账目（时间正序，带各自标签，ADR 0009） */
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<Entry>>

    suspend fun add(entry: Entry): Long

    /** 入库账目并写入标签关联，同事务 */
    suspend fun addWithTags(entry: Entry, tagIds: List<Long>): Long

    suspend fun delete(id: Long)

    /** 删除账目并返回可恢复的完整快照。实现必须保证删除与快照读取在同一事务内完成。 */
    suspend fun deleteWithSnapshot(id: Long): EntrySnapshot? =
        throw UnsupportedOperationException("deleteWithSnapshot is not implemented")

    /** 按快照中的原始账目 id 和标签关联恢复账目。实现必须在同一事务内完成。 */
    suspend fun restoreSnapshot(snapshot: EntrySnapshot) {
        throw UnsupportedOperationException("restoreSnapshot is not implemented")
    }

    /** 原子替换全部账目，类别和标签等元数据保留。 */
    suspend fun replaceAll(entries: List<Entry>)

    /** 清空全部账目（ADR 0008：只清账目，类别/标签保留） */
    suspend fun clearAll()
}

/** 删除账目的可恢复快照，保留 Entry 全字段及删除时的原始标签 id。 */
data class EntrySnapshot(
    val entry: Entry,
    val tagIds: List<Long>,
)

data class TodayTotals(
    val expense: Double = 0.0,
    val income: Double = 0.0,
)
