package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildCalendarMonthUseCaseTest {
    private val month = YearMonth.of(2026, 8)

    private fun entry(id: Long, type: EntryType, amount: Double, day: Int) = Entry(
        id = id,
        type = type,
        amount = amount,
        amountRaw = amount.toString(),
        categoryName = "餐饮",
        date = month.atDay(day),
        note = "",
    )

    @Test
    fun `zero fills every day and aggregates daily cash flow`() = runTest {
        val result = BuildCalendarMonthUseCase(FakeEntryRepository(listOf(
            entry(1, EntryType.EXPENSE, 20.0, 1),
            entry(2, EntryType.EXPENSE, 35.0, 1),
            entry(3, EntryType.INCOME, 500.0, 2),
        )))(month).first()

        assertEquals(31, result.days.size)
        assertEquals(55.0, result.days[0].expense, 0.001)
        assertEquals(0.0, result.days[0].income, 0.001)
        assertEquals(2, result.days[0].entryCount)
        assertEquals(0.0, result.days[1].expense, 0.001)
        assertEquals(500.0, result.days[1].income, 0.001)
        assertEquals(0, result.days[2].entryCount)
        assertEquals(55.0, result.expense, 0.001)
        assertEquals(500.0, result.income, 0.001)
    }

    @Test
    fun `queries the selected calendar month`() = runTest {
        val repository = FakeEntryRepository(emptyList())
        BuildCalendarMonthUseCase(repository)(month).first()
        assertEquals(LocalDate.of(2026, 8, 1), repository.observedStart)
        assertEquals(LocalDate.of(2026, 8, 31), repository.observedEnd)
    }
}
