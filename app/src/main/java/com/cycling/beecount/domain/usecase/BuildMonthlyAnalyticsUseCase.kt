package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.MonthlyAnalytics
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用例：构建月度报表（ADR 0009）。
 * 查询整月区间，在内存中聚合出合计、分类排行与每日支出柱状。
 */
class BuildMonthlyAnalyticsUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    operator fun invoke(month: YearMonth): Flow<MonthlyAnalytics> =
        entryRepository.observeBetween(month.atDay(1), month.atEndOfMonth())
            .map { entries -> entries.toMonthlyAnalytics(month) }
}

private fun List<Entry>.toMonthlyAnalytics(month: YearMonth): MonthlyAnalytics {
    val daily = AnalyticsAggregator.dailyExpense(month, this)
    val totals = AnalyticsAggregator.totals(this)
    return MonthlyAnalytics(
        month = month,
        expense = totals.expense,
        income = totals.income,
        entryCount = totals.entryCount,
        categoryRanks = AnalyticsAggregator.expenseRanks(this),
        dailyExpense = daily,
        maxDaily = daily.filter { it.amount > 0.0 }.maxByOrNull { it.amount },
    )
}
