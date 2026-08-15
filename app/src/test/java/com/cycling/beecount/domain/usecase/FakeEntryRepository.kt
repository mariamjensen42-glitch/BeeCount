package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.TodayTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 测试用 EntryRepository：返回固定账目列表，并记录 observeBetween 的查询区间 */
class FakeEntryRepository(
    private val entries: List<Entry>,
) : EntryRepository {
    var observedStart: LocalDate? = null
    var observedEnd: LocalDate? = null

    override fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<Entry>> {
        observedStart = start
        observedEnd = end
        return flowOf(entries)
    }

    override fun observeEntriesOn(date: LocalDate): Flow<List<Entry>> = flowOf(entries)
    override fun observeTotalsOn(date: LocalDate): Flow<TodayTotals> = flowOf(TodayTotals())
    override fun observeAllWithTags(): Flow<List<Entry>> = flowOf(entries)
    override suspend fun add(entry: Entry): Long = entry.id
    override suspend fun addWithTags(entry: Entry, tagIds: List<Long>): Long = entry.id
    override suspend fun delete(id: Long) {}
    override suspend fun clearAll() {}
}
