package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildComparisonAnalyticsUseCaseTest {

    private val month = YearMonth.of(2026, 8)

    private fun entry(id: Long, type: EntryType, amount: Double, category: String, date: LocalDate) = Entry(
        id = id,
        type = type,
        amount = amount,
        amountRaw = "$amount",
        categoryName = category,
        date = date,
        note = "",
    )

    @Test
    fun `monthly comparison uses current month and previous month`() = runTest {
        val repo = FakeEntryRepository(emptyList())
        BuildComparisonAnalyticsUseCase(repo)(month = month).first()
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 1) to LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 7, 31),
            ),
            repo.observedRanges,
        )
    }

    @Test
    fun `aggregates current and previous period summaries`() = runTest {
        val currentEntries = listOf(
            entry(1, EntryType.EXPENSE, 30.0, "餐饮", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.INCOME, 500.0, "红包", LocalDate.of(2026, 8, 2)),
        )
        val prevEntries = listOf(
            entry(3, EntryType.EXPENSE, 45.0, "餐饮", LocalDate.of(2026, 7, 1)),
            entry(4, EntryType.INCOME, 300.0, "红包", LocalDate.of(2026, 7, 2)),
        )
        val repo = FakeEntryRepository(currentEntries + prevEntries)
        val result = BuildComparisonAnalyticsUseCase(repo)(month = month).first()

        assertEquals("2026年8月", result.currentLabel)
        assertEquals("2026年7月", result.previousLabel)
        assertEquals(30.0, result.current.expense, 0.001)
        assertEquals(500.0, result.current.income, 0.001)
        assertEquals(2, result.current.entryCount)
        assertEquals(45.0, result.previous.expense, 0.001)
        assertEquals(300.0, result.previous.income, 0.001)
        assertEquals(2, result.previous.entryCount)
    }

    @Test
    fun `annual comparison uses current year and previous year`() = runTest {
        val repo = FakeEntryRepository(emptyList())
        BuildComparisonAnalyticsUseCase(repo)(year = 2026).first()
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 12, 31),
                LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 12, 31),
            ),
            repo.observedRanges,
        )
    }

    @Test
    fun `annual comparison aggregates current and previous year`() = runTest {
        val currentEntries = listOf(
            entry(1, EntryType.EXPENSE, 100.0, "餐饮", LocalDate.of(2026, 3, 1)),
            entry(2, EntryType.INCOME, 1000.0, "红包", LocalDate.of(2026, 6, 1)),
        )
        val prevEntries = listOf(
            entry(3, EntryType.EXPENSE, 50.0, "餐饮", LocalDate.of(2025, 3, 1)),
            entry(4, EntryType.EXPENSE, 80.0, "购物", LocalDate.of(2025, 4, 1)),
        )
        val repo = FakeEntryRepository(currentEntries + prevEntries)
        val result = BuildComparisonAnalyticsUseCase(repo)(year = 2026).first()

        assertEquals("2026年", result.currentLabel)
        assertEquals("2025年", result.previousLabel)
        assertEquals(100.0, result.current.expense, 0.001)
        assertEquals(1000.0, result.current.income, 0.001)
        assertEquals(2, result.current.entryCount)
        assertEquals(130.0, result.previous.expense, 0.001)
        assertEquals(0.0, result.previous.income, 0.001)
        assertEquals(2, result.previous.entryCount)
    }
}
