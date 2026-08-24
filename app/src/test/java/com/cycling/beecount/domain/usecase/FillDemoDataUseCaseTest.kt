package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FillDemoDataUseCaseTest {

    private val today = LocalDate.of(2026, 8, 16)

    @Test
    fun `replaces existing entries with current-year sample`() = runTest {
        val existing = Entry(
            id = 1,
            type = EntryType.EXPENSE,
            amount = 1.0,
            amountRaw = "1",
            categoryName = "其他",
            date = today,
            note = "旧账目",
        )
        val repository = FakeEntryRepository(listOf(existing))

        FillDemoDataUseCase(repository, FakeTagRepository())(today)

        assertEquals(1, repository.replaceAllCalls)
        assertFalse(repository.storedEntries.any { it.note == "旧账目" })
        assertTrue(repository.storedEntries.isNotEmpty())
    }

    @Test
    fun `creates rich five-year entries without future dates`() {
        val useCase = FillDemoDataUseCase(FakeEntryRepository(emptyList()), FakeTagRepository())

        val entries = useCase.buildEntries(today)

        assertTrue(entries.all { it.date.year in (today.year - 4)..today.year })
        assertTrue(entries.all { it.date <= today })
        assertEquals(FillDemoDataUseCase.DEMO_ENTRY_COUNT, entries.size)
        assertTrue(entries.map { it.date.year }.toSet().size == 5)
        assertTrue(entries.any { it.type == EntryType.EXPENSE })
        assertTrue(entries.any { it.type == EntryType.INCOME })
        assertTrue(entries.map { it.categoryName }.containsAll(listOf(
            "餐饮", "交通", "购物", "居住", "娱乐", "医疗", "教育", "人情", "工资", "奖金", "红包", "报销", "理财",
        )))
        assertTrue(entries.groupBy { it.date }.any { (_, sameDay) -> sameDay.size > 1 })
    }

    @Test
    fun `current partial month only contains elapsed sample dates while past years are complete`() {
        val useCase = FillDemoDataUseCase(FakeEntryRepository(emptyList()), FakeTagRepository())
        val partialMonthToday = LocalDate.of(2026, 8, 9)

        val entries = useCase.buildEntries(partialMonthToday)

        assertTrue(entries.filter { it.date.year == partialMonthToday.year && it.date.monthValue == 8 }.all { it.date <= partialMonthToday })
        assertTrue(entries.none { it.date.year == partialMonthToday.year && it.date.monthValue > 8 })
        assertTrue(entries.any { it.date == LocalDate.of(2025, 12, 20) })
    }

    @Test
    fun `demo entries derive deterministic tags and write associations`() = runTest {
        val repository = FakeEntryRepository(emptyList())
        val tagRepository = FakeTagRepository()

        FillDemoDataUseCase(repository, tagRepository)(today)

        assertEquals(1, repository.replaceAllWithTagIdsCalls)
        // 所有演示条目都带标签；标签库据此建立了去重后的标签集
        assertTrue(repository.storedEntries.all { it.tags.isNotEmpty() })
        assertTrue(tagRepository.tags.size > 0)
    }

    @Test
    fun `demo tags cover expected labels`() {
        val useCase = FillDemoDataUseCase(FakeEntryRepository(emptyList()), FakeTagRepository())
        val entries = useCase.buildEntries(today)
        val tagNames = entries.flatMap { it.tags }.map { it.name }.toSet()
        assertTrue(tagNames.contains("早餐"))
        assertTrue(tagNames.contains("通勤"))
        assertTrue(tagNames.contains("房租"))
    }
}
