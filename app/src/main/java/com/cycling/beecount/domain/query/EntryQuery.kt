package com.cycling.beecount.domain.query

import com.cycling.beecount.domain.model.AnnualAnalytics
import com.cycling.beecount.domain.model.AnnualHighlights
import com.cycling.beecount.domain.model.Budget
import com.cycling.beecount.domain.model.BudgetProgress
import com.cycling.beecount.domain.model.CalendarDaySummary
import com.cycling.beecount.domain.model.CalendarMonth
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.MonthlyAnalytics
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.BudgetRepository
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.TagRepository
import com.cycling.beecount.domain.repository.TodayTotals
import com.cycling.beecount.domain.usecase.AnalyticsAggregator
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 查询模块（深 module）：把「给定日期/区间/粒度 → 聚合结果」的查询语义集中在一个接口后。
 *
 * 与 `domain/usecase` 的动作类（解析、确认、删除）形成对照：查询（observe/build/aggregate）
 * 与动作（command）是深 module 的自然分界。实现吸收原先分散的转发与 map 逻辑。
 * `BuildComparisonAnalyticsUseCase`（真实周期边界数学 + combine）保留为独立深模块，不在此。
 */
@Singleton
class EntryQuery @Inject constructor(
    private val entryRepository: EntryRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val budgetRepository: BudgetRepository,
) {

    fun observeDay(date: LocalDate): Flow<List<Entry>> = entryRepository.observeEntriesOn(date)

    fun observeTotals(date: LocalDate): Flow<TodayTotals> = entryRepository.observeTotalsOn(date)

    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<Entry>> =
        entryRepository.observeBetween(start, end)

    fun observeAllWithTags(): Flow<List<Entry>> = entryRepository.observeAllWithTags()

    fun observeCategories(): Flow<List<Category>> = categoryRepository.observeAll()

    fun observeTags(): Flow<List<Tag>> = tagRepository.observeAll()

    fun observeBudgets(): Flow<List<Budget>> = budgetRepository.observeBudgets()

    fun observeBudgetProgress(today: LocalDate): Flow<List<BudgetProgress>> =
        budgetRepository.observeProgress(today)

    fun buildCalendar(month: YearMonth): Flow<CalendarMonth> =
        entryRepository.observeBetween(month.atDay(1), month.atEndOfMonth())
            .map { entries -> entries.toCalendarMonth(month) }

    fun buildMonth(month: YearMonth): Flow<MonthlyAnalytics> =
        entryRepository.observeBetween(month.atDay(1), month.atEndOfMonth())
            .map { entries -> entries.toMonthlyAnalytics(month) }

    fun buildAnnual(year: Int): Flow<AnnualAnalytics> {
        val start = YearMonth.of(year, 1).atDay(1)
        val end = YearMonth.of(year, 12).atEndOfMonth()
        return entryRepository.observeBetween(start, end)
            .map { entries -> entries.toAnnualAnalytics(year) }
    }
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
