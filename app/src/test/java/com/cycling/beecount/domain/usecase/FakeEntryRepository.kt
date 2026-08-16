package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.EntrySnapshot
import com.cycling.beecount.domain.repository.TodayTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 测试用 EntryRepository：返回固定账目列表，并记录 observeBetween 的查询区间 */
class FakeEntryRepository(
    entries: List<Entry>,
) : EntryRepository {
    private val entries = entries.toMutableList()
    var observedStart: LocalDate? = null
    var observedEnd: LocalDate? = null
    var replaceAllCalls = 0
    var lastRestoredSnapshot: EntrySnapshot? = null
    val storedEntries: List<Entry> get() = entries

    override fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<Entry>> {
        observedStart = start
        observedEnd = end
        return flowOf(entries)
    }

    override fun observeEntriesOn(date: LocalDate): Flow<List<Entry>> =
        flowOf(entries.filter { it.date == date })
    override fun observeTotalsOn(date: LocalDate): Flow<TodayTotals> = flowOf(TodayTotals())
    override fun observeAllWithTags(): Flow<List<Entry>> = flowOf(entries)
    override suspend fun add(entry: Entry): Long {
        entries += entry
        return entry.id
    }

    override suspend fun addWithTags(entry: Entry, tagIds: List<Long>): Long {
        entries += entry
        return entry.id
    }

    override suspend fun delete(id: Long) {
        entries.removeAll { it.id == id }
    }

    override suspend fun deleteWithSnapshot(id: Long): EntrySnapshot? {
        val entry = entries.firstOrNull { it.id == id } ?: return null
        entries.removeAll { it.id == id }
        return EntrySnapshot(entry = entry, tagIds = entry.tags.map { it.id })
    }

    override suspend fun restoreSnapshot(snapshot: EntrySnapshot) {
        lastRestoredSnapshot = snapshot
        entries.removeAll { it.id == snapshot.entry.id }
        entries += snapshot.entry.copy(tags = snapshot.tagIds.map { id ->
            snapshot.entry.tags.firstOrNull { it.id == id }
                ?: error("Missing tag $id in snapshot entry")
        })
    }

    override suspend fun replaceAll(entries: List<Entry>) {
        replaceAllCalls++
        this.entries.clear()
        this.entries += entries
    }

    override suspend fun clearAll() {
        entries.clear()
    }

    override suspend fun findExistingSourceRefs(refs: Collection<String>): Set<String> =
        entries.mapNotNull { it.sourceRef }.toSet().intersect(refs.toSet())

    override suspend fun addAll(entries: List<Entry>): Int {
        val existing = findExistingSourceRefs(entries.mapNotNull { it.sourceRef })
        val fresh = entries.filter { it.sourceRef == null || it.sourceRef !in existing }
        this.entries += fresh
        return fresh.size
    }

    override suspend fun addAllWithTag(entries: List<Entry>, tag: Tag): Int {
        val existing = findExistingSourceRefs(entries.mapNotNull { it.sourceRef })
        val fresh = entries.filter { it.sourceRef == null || it.sourceRef !in existing }
        this.entries += fresh.map { it.copy(tags = it.tags + tag) }
        return fresh.size
    }

    override suspend fun deleteBySourceRefs(refs: Collection<String>): Int {
        val set = refs.toSet()
        val before = entries.size
        entries.removeAll { it.sourceRef in set }
        return before - entries.size
    }
}
