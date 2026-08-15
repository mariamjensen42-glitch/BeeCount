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

    suspend fun add(entry: Entry): Long

    suspend fun delete(id: Long)
}

data class TodayTotals(
    val expense: Double = 0.0,
    val income: Double = 0.0,
)
