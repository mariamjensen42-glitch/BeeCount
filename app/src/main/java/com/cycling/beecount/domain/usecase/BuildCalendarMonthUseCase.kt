package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.CalendarDaySummary
import com.cycling.beecount.domain.model.CalendarMonth
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 构建月历所需的逐日收支摘要。 */
class BuildCalendarMonthUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    operator fun invoke(month: YearMonth): Flow<CalendarMonth> =
        entryRepository.observeBetween(month.atDay(1), month.atEndOfMonth())
            .map { entries -> entries.toCalendarMonth(month) }
}

private fun List<Entry>.toCalendarMonth(month: YearMonth): CalendarMonth {
    val byDate = groupBy { it.date }
    val days = (1..month.lengthOfMonth()).map { day ->
        val entries = byDate[month.atDay(day)].orEmpty()
        CalendarDaySummary(
            date = month.atDay(day),
            expense = entries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount },
            income = entries.filter { it.type == EntryType.INCOME }.sumOf { it.amount },
            entryCount = entries.size,
        )
    }
    return CalendarMonth(
        month = month,
        expense = days.sumOf { it.expense },
        income = days.sumOf { it.income },
        days = days,
    )
}
