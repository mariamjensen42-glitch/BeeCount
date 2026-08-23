package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.ComparisonAnalytics
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.PeriodSummary
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * 用例：时间段对比（ADR 0009 扩展）。
 * 月度粒度为「本月 vs 上月」，年度粒度为「本年 vs 去年」，聚合当前与上一期间的支出/收入/笔数。
 */
class BuildComparisonAnalyticsUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {

    operator fun invoke(month: YearMonth? = null, year: Int? = null): Flow<ComparisonAnalytics> {
        require(month != null || year != null) { "month 或 year 必须提供一个" }
        val currentStart: YearMonth
        val currentEnd: YearMonth
        val previousStart: YearMonth
        val previousEnd: YearMonth
        val currentLabel: String
        val previousLabel: String
        if (month != null) {
            currentStart = month
            currentEnd = month
            previousStart = month.minusMonths(1)
            previousEnd = month.minusMonths(1)
            currentLabel = "${month.year}年${month.monthValue}月"
            previousLabel = "${previousStart.year}年${previousStart.monthValue}月"
        } else {
            val y = year!!
            currentStart = YearMonth.of(y, 1)
            currentEnd = YearMonth.of(y, 12)
            previousStart = YearMonth.of(y - 1, 1)
            previousEnd = YearMonth.of(y - 1, 12)
            currentLabel = "${y}年"
            previousLabel = "${y - 1}年"
        }
        return entryRepository.observeBetween(currentStart.atDay(1), currentEnd.atEndOfMonth())
            .combine(
                entryRepository.observeBetween(previousStart.atDay(1), previousEnd.atEndOfMonth())
            ) { current, previous ->
                ComparisonAnalytics(
                    currentLabel = currentLabel,
                    previousLabel = previousLabel,
                    current = toSummary(current),
                    previous = toSummary(previous),
                )
            }
    }

    private fun toSummary(entries: List<Entry>): PeriodSummary {
        val totals = AnalyticsAggregator.totals(entries)
        return PeriodSummary(
            expense = totals.expense,
            income = totals.income,
            entryCount = totals.entryCount,
        )
    }
}
