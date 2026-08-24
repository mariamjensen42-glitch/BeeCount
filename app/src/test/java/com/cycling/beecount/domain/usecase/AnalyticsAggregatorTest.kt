package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsAggregatorTest {

    private fun entry(id: Long, type: EntryType, amount: Double, category: String, date: LocalDate = LocalDate.of(2026, 8, 1)) = Entry(
        id = id,
        type = type,
        amount = amount,
        amountRaw = "$amount",
        categoryName = category,
        date = date,
        note = "",
    )

    @Test
    fun `categoryBreakdown returns slices sorted by amount descending`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 30.0, "餐饮"),
            entry(2, EntryType.EXPENSE, 70.0, "购物"),
            entry(3, EntryType.EXPENSE, 50.0, "交通"),
            entry(4, EntryType.INCOME, 500.0, "红包"),
        )
        val breakdown = AnalyticsAggregator.categoryBreakdown(entries)

        assertEquals(150.0, breakdown.total, 0.001)
        assertEquals(listOf("购物", "交通", "餐饮"), breakdown.slices.map { it.name })
        assertEquals(70.0, breakdown.slices[0].amount, 0.001)
        assertEquals(0.4667f, breakdown.slices[0].fraction, 0.001f)
        assertEquals(0.2f, breakdown.slices[2].fraction, 0.001f)
    }

    @Test
    fun `categoryBreakdown is empty when no expense`() {
        val entries = listOf(
            entry(1, EntryType.INCOME, 500.0, "红包"),
        )
        val breakdown = AnalyticsAggregator.categoryBreakdown(entries)

        assertEquals(0.0, breakdown.total, 0.001)
        assertTrue(breakdown.slices.isEmpty())
    }

    @Test
    fun `refund reduces expense in totals`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 100.0, "餐饮"),
            entry(2, EntryType.REFUND, 30.0, "餐饮"),
            entry(3, EntryType.INCOME, 500.0, "红包"),
        )
        val totals = AnalyticsAggregator.totals(entries)

        assertEquals(70.0, totals.expense, 0.001)
        assertEquals(500.0, totals.income, 0.001)
    }

    @Test
    fun `refund reduces category rank amount`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 100.0, "餐饮"),
            entry(2, EntryType.REFUND, 30.0, "餐饮"),
            entry(3, EntryType.EXPENSE, 50.0, "交通"),
        )
        val ranks = AnalyticsAggregator.expenseRanks(entries)

        assertEquals(listOf("餐饮", "交通"), ranks.map { it.name })
        assertEquals(70.0, ranks[0].amount, 0.001)
        assertEquals(50.0, ranks[1].amount, 0.001)
    }

    @Test
    fun `refund reduces daily expense to zero floor`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 30.0, "餐饮", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.REFUND, 30.0, "餐饮", LocalDate.of(2026, 8, 1)),
            entry(3, EntryType.REFUND, 10.0, "餐饮", LocalDate.of(2026, 8, 1)),
        )
        val daily = AnalyticsAggregator.dailyExpense(java.time.YearMonth.of(2026, 8), entries)

        assertEquals(0.0, daily[0].amount, 0.001)
    }
}
