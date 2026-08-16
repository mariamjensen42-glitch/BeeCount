package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildMonthlyAnalyticsUseCaseTest {

    private val month = YearMonth.of(2026, 8)

    private fun entry(id: Long, type: EntryType, amount: Double, category: String, day: Int) = Entry(
        id = id,
        type = type,
        amount = amount,
        amountRaw = "$amount",
        categoryName = category,
        date = month.atDay(day),
        note = "",
    )

    @Test
    fun `queries the whole month range`() = runTest {
        val repo = FakeEntryRepository(emptyList())
        BuildMonthlyAnalyticsUseCase(repo)(month).first()
        assertEquals(LocalDate.of(2026, 8, 1), repo.observedStart)
        assertEquals(LocalDate.of(2026, 8, 31), repo.observedEnd)
    }

    @Test
    fun `aggregates expense income and entry count`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                entry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                entry(2, EntryType.EXPENSE, 45.0, "餐饮", 1),
                entry(3, EntryType.INCOME, 500.0, "红包", 2),
            )
        )
        val result = BuildMonthlyAnalyticsUseCase(repo)(month).first()
        assertEquals(75.0, result.expense, 0.001)
        assertEquals(500.0, result.income, 0.001)
        assertEquals(3, result.entryCount)
    }

    @Test
    fun `ranks categories by expense descending and excludes income`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                entry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                entry(2, EntryType.EXPENSE, 45.0, "餐饮", 1),
                entry(3, EntryType.EXPENSE, 20.0, "交通", 2),
                entry(4, EntryType.INCOME, 500.0, "红包", 2),
            )
        )
        val result = BuildMonthlyAnalyticsUseCase(repo)(month).first()
        assertEquals(listOf("餐饮", "交通"), result.categoryRanks.map { it.name })
        assertEquals(75.0, result.categoryRanks[0].amount, 0.001)
        assertEquals(20.0, result.categoryRanks[1].amount, 0.001)
    }

    @Test
    fun `fills every day of month with zero for days without spending`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                entry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                entry(2, EntryType.EXPENSE, 20.0, "交通", 15),
            )
        )
        val result = BuildMonthlyAnalyticsUseCase(repo)(month).first()
        assertEquals(31, result.dailyExpense.size)
        assertEquals(1, result.dailyExpense[0].day)
        assertEquals(30.0, result.dailyExpense[0].amount, 0.001)
        assertEquals(0.0, result.dailyExpense[1].amount, 0.001)
        assertEquals(20.0, result.dailyExpense[14].amount, 0.001)
        assertEquals(0.0, result.dailyExpense[30].amount, 0.001)
    }

    @Test
    fun `max daily is the day with highest spending and null when no expense`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                entry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                entry(2, EntryType.EXPENSE, 20.0, "交通", 15),
            )
        )
        val result = BuildMonthlyAnalyticsUseCase(repo)(month).first()
        assertEquals(1, result.maxDaily?.day)
        assertEquals(30.0, result.maxDaily?.amount ?: 0.0, 0.001)

        val incomeOnly = FakeEntryRepository(
            listOf(entry(3, EntryType.INCOME, 500.0, "红包", 2))
        )
        val empty = BuildMonthlyAnalyticsUseCase(incomeOnly)(month).first()
        assertNull(empty.maxDaily)
        assertTrue(empty.dailyExpense.all { it.amount == 0.0 })
    }
}
