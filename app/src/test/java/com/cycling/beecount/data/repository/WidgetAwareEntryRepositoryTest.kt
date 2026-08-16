package com.cycling.beecount.data.repository

import com.cycling.beecount.data.repository.WidgetAwareEntryRepository
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.usecase.FakeEntryRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 装饰器测试（ADR 0013）：账目写操作触发 widget 刷新、未实际改数据不触发、只读不触发。
 */
class WidgetAwareEntryRepositoryTest {

    private fun entry(sourceRef: String? = null) = Entry(
        type = EntryType.EXPENSE,
        amount = 10.0,
        amountRaw = "10",
        categoryName = "餐饮",
        date = LocalDate.of(2026, 8, 16),
        note = "测试",
        sourceRef = sourceRef,
    )

    private class Harness {
        val delegate = FakeEntryRepository(emptyList())
        var refreshes = 0
        val repo = WidgetAwareEntryRepository(delegate) { refreshes++ }
    }

    @Test
    fun `add 触发刷新`() = runTest {
        val h = Harness()
        h.repo.add(entry())
        assertEquals(1, h.refreshes)
    }

    @Test
    fun `addWithTags 触发刷新`() = runTest {
        val h = Harness()
        h.repo.addWithTags(entry(), emptyList())
        assertEquals(1, h.refreshes)
    }

    @Test
    fun `delete 触发刷新`() = runTest {
        val h = Harness()
        val id = h.repo.add(entry())
        h.refreshes = 0
        h.repo.delete(id)
        assertEquals(1, h.refreshes)
    }

    @Test
    fun `deleteWithSnapshot 命中触发、未命中不触发`() = runTest {
        val h = Harness()
        val id = h.repo.add(entry())
        h.refreshes = 0
        h.repo.deleteWithSnapshot(id)
        assertEquals(1, h.refreshes)
        h.repo.deleteWithSnapshot(999L)
        assertEquals(1, h.refreshes) // 不存在：未改数据，不刷新
    }

    @Test
    fun `restoreSnapshot 触发刷新`() = runTest {
        val h = Harness()
        val snapshot = h.repo.deleteWithSnapshot(h.repo.add(entry()))
        h.refreshes = 0
        h.repo.restoreSnapshot(snapshot!!)
        assertEquals(1, h.refreshes)
    }

    @Test
    fun `replaceAll 与 clearAll 触发刷新`() = runTest {
        val h = Harness()
        h.repo.replaceAll(listOf(entry()))
        assertEquals(1, h.refreshes)
        h.repo.clearAll()
        assertEquals(2, h.refreshes)
    }

    @Test
    fun `addAll 有新增触发、全重复不触发`() = runTest {
        val h = Harness()
        h.repo.addAll(listOf(entry("WX-1")))
        assertEquals(1, h.refreshes)
        h.repo.addAll(listOf(entry("WX-1"))) // 重复：未实际插入
        assertEquals(1, h.refreshes)
    }

    @Test
    fun `addAllWithTag 有新增触发、全重复不触发`() = runTest {
        val h = Harness()
        val tag = Tag(id = 1L, name = "微信", color = 0xFF07C160L)
        h.repo.addAllWithTag(listOf(entry("WX-2")), tag)
        assertEquals(1, h.refreshes)
        h.repo.addAllWithTag(listOf(entry("WX-2")), tag)
        assertEquals(1, h.refreshes)
    }

    @Test
    fun `deleteBySourceRefs 命中触发、未命中不触发`() = runTest {
        val h = Harness()
        h.repo.addAll(listOf(entry("WX-3")))
        h.refreshes = 0
        h.repo.deleteBySourceRefs(listOf("WX-3"))
        assertEquals(1, h.refreshes)
        h.repo.deleteBySourceRefs(listOf("不存在"))
        assertEquals(1, h.refreshes)
    }

    @Test
    fun `只读操作不触发刷新`() = runTest {
        val h = Harness()
        h.repo.observeAllWithTags()
        h.repo.observeBetween(LocalDate.now(), LocalDate.now())
        assertEquals(0, h.refreshes)
    }
}
