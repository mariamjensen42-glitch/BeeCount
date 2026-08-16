package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.AnnualAnalytics
import com.cycling.beecount.domain.model.AnnualHighlights
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用例：构建年度报告（ADR 0009）。
 * 查询整年区间，聚合出 12 个月趋势折线与年度高亮（最忙月/最大单笔/日均）。
 */
class BuildAnnualAnalyticsUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    operator fun invoke(year: Int): Flow<AnnualAnalytics> {
        val start = YearMonth.of(year, 1).atDay(1)
        val end = YearMonth.of(year, 12).atEndOfMonth()
        return entryRepository.observeBetween(start, end)
            .map { entries -> entries.toAnnualAnalytics(year) }
    }
}

private fun List<Entry>.toAnnualAnalytics(year: Int): AnnualAnalytics {
    val monthly = AnalyticsAggregator.monthlyExpense(year, this)
    val expenses = filter { it.type == EntryType.EXPENSE }
    val totals = AnalyticsAggregator.totals(this)
    val busiest = monthly.filter { it.amount > 0.0 }.maxByOrNull { it.amount }
    return AnnualAnalytics(
        year = year,
        expense = totals.expense,
        income = totals.income,
        entryCount = totals.entryCount,
        categoryRanks = AnalyticsAggregator.expenseRanks(this),
        monthlyExpense = monthly,
        dailyHeatmap = AnalyticsAggregator.annualHeatmap(year, this),
        highlights = AnnualHighlights(
            busiestMonth = busiest?.month,
            busiestAmount = busiest?.amount ?: 0.0,
            biggestEntry = expenses.maxByOrNull { it.amount },
            avgDailyExpense = AnalyticsAggregator.avgDailyExpense(this),
        ),
    )
}
