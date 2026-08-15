package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildAnnualAnalyticsUseCaseTest {

    private val year = 2026

    private fun entry(id: Long, type: EntryType, amount: Double, category: String, month: Int, day: Int) = Entry(
        id = id,
        type = type,
        amount = amount,
        amountRaw = "$amount",
        categoryName = category,
        date = LocalDate.of(year, month, day),
        note = "",
    )

    private val entries = listOf(
        entry(1, EntryType.EXPENSE, 100.0, "餐饮", 1, 5),
        entry(2, EntryType.EXPENSE, 50.0, "交通", 1, 20),
        entry(3, EntryType.EXPENSE, 300.0, "购物", 3, 10),
        entry(4, EntryType.INCOME, 1000.0, "红包", 3, 10),
        entry(5, EntryType.EXPENSE, 20.0, "餐饮", 12, 1),
    )

    @Test
    fun `queries the whole year range`() = runTest {
        val repo = FakeEntryRepository(entries)
        BuildAnnualAnalyticsUseCase(repo)(year).first()
        assertEquals(LocalDate.of(2026, 1, 1), repo.observedStart)
        assertEquals(LocalDate.of(2026, 12, 31), repo.observedEnd)
    }

    @Test
    fun `aggregates year totals and entry count`() = runTest {
        val repo = FakeEntryRepository(entries)
        val result = BuildAnnualAnalyticsUseCase(repo)(year).first()
        assertEquals(470.0, result.expense, 0.001)
        assertEquals(1000.0, result.income, 0.001)
        assertEquals(5, result.entryCount)
    }

    @Test
    fun `produces twelve monthly points zero filled`() = runTest {
        val repo = FakeEntryRepository(entries)
        val result = BuildAnnualAnalyticsUseCase(repo)(year).first()
        assertEquals(12, result.monthlyExpense.size)
        assertEquals(150.0, result.monthlyExpense[0].amount, 0.001)
        assertEquals(0.0, result.monthlyExpense[1].amount, 0.001)
        assertEquals(300.0, result.monthlyExpense[2].amount, 0.001)
        assertEquals(20.0, result.monthlyExpense[11].amount, 0.001)
    }

    @Test
    fun `ranks categories across the year by expense descending`() = runTest {
        val repo = FakeEntryRepository(entries)
        val result = BuildAnnualAnalyticsUseCase(repo)(year).first()
        assertEquals(listOf("购物", "餐饮", "交通"), result.categoryRanks.map { it.name })
    }

    @Test
    fun `highlights busiest month biggest entry and avg daily spending`() = runTest {
        val repo = FakeEntryRepository(entries)
        val result = BuildAnnualAnalyticsUseCase(repo)(year).first()
        assertEquals(2026, result.highlights.busiestMonth?.year)
        assertEquals(3, result.highlights.busiestMonth?.monthValue)
        assertEquals(300.0, result.highlights.busiestAmount, 0.001)
        assertEquals("购物", result.highlights.biggestEntry?.categoryName)
        assertEquals(300.0, result.highlights.biggestEntry?.amount ?: 0.0, 0.001)
        // 支出 470 ÷ 有支出的 4 天（1/5、1/20、3/10、12/1）
        assertEquals(117.5, result.highlights.avgDailyExpense, 0.001)
    }

    @Test
    fun `highlights are empty when the year has no expense`() = runTest {
        val repo = FakeEntryRepository(
            listOf(entry(1, EntryType.INCOME, 1000.0, "红包", 6, 1))
        )
        val result = BuildAnnualAnalyticsUseCase(repo)(year).first()
        assertNull(result.highlights.busiestMonth)
        assertNull(result.highlights.biggestEntry)
        assertEquals(0.0, result.highlights.avgDailyExpense, 0.001)
        assertEquals(1000.0, result.income, 0.001)
    }
}
