package com.cycling.beecount.data.repository

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.EntrySnapshot
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.usecase.AnomalyDetector
import com.cycling.beecount.domain.usecase.AnomalyNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 账目变更后的通知钩子（ADR 0013）：桌面小组件刷新器实现它 */
fun interface WidgetRefresher {
    suspend fun refresh()
}

/**
 * 账目仓库装饰器（ADR 0013）：所有写操作完成后触发桌面小组件刷新（[refresher]），
 * 单一接入点保证任何入口改账目（记账/删除/撤销/导入/清空/演示数据）widget 都自动更新；
 * 读操作原样委托 [delegate]。能感知删除量的写（快照删除、批量删除）只在确实删了行时刷新；
 * 按 id 删除 [delete] 无法观察删除结果，无条件刷新。
 */
@Singleton
class WidgetAwareEntryRepository @Inject constructor(
    @Named("base") private val delegate: EntryRepository,
    private val refresher: WidgetRefresher,
    private val anomalyDetector: AnomalyDetector,
    private val anomalyNotifier: AnomalyNotifier,
) : EntryRepository by delegate {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 新支出落库后异步检测异常（导入走 addAll*，不触发以免刷屏） */
    private fun maybeDetect(entry: Entry) {
        if (entry.type != EntryType.EXPENSE) return
        scope.launch { anomalyDetector.detect(entry)?.let { anomalyNotifier.notify(it) } }
    }

    override suspend fun add(entry: Entry): Long =
        delegate.add(entry).also { refresher.refresh(); maybeDetect(entry) }

    override suspend fun addWithTags(entry: Entry, tagIds: List<Long>): Long =
        delegate.addWithTags(entry, tagIds).also { refresher.refresh(); maybeDetect(entry) }

    override suspend fun delete(id: Long) {
        delegate.delete(id)
        refresher.refresh()
    }

    override suspend fun deleteWithSnapshot(id: Long): EntrySnapshot? =
        delegate.deleteWithSnapshot(id)?.also { refresher.refresh() }

    override suspend fun restoreSnapshot(snapshot: EntrySnapshot) {
        delegate.restoreSnapshot(snapshot)
        refresher.refresh()
    }

    override suspend fun replaceAll(entries: List<Entry>) {
        delegate.replaceAll(entries)
        refresher.refresh()
    }

    override suspend fun replaceAllWithTagIds(entries: List<Entry>, tagIndex: Map<String, Long>) {
        delegate.replaceAllWithTagIds(entries, tagIndex)
        refresher.refresh()
    }

    override suspend fun clearAll() {
        delegate.clearAll()
        refresher.refresh()
    }

    override suspend fun addAll(entries: List<Entry>): Int =
        delegate.addAll(entries).also { if (it > 0) refresher.refresh() }

    override suspend fun addAllWithTag(entries: List<Entry>, tag: Tag): Int =
        delegate.addAllWithTag(entries, tag).also { if (it > 0) refresher.refresh() }

    override suspend fun deleteBySourceRefs(refs: Collection<String>): Int =
        delegate.deleteBySourceRefs(refs).also { if (it > 0) refresher.refresh() }
}
