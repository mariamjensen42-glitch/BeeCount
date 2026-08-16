package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.AnnualHeatmapDay
import com.cycling.beecount.domain.model.CategoryRank
import com.cycling.beecount.domain.model.DailyExpense
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.MonthlyExpensePoint
import java.time.LocalDate
import java.time.YearMonth

/** 区间合计（支出/收入/笔数），供月度与年度用例共用 */
data class AnalyticsTotals(
    val expense: Double,
    val income: Double,
    val entryCount: Int,
)

/**
 * 图表聚合的共享纯函数（ADR 0009）：合计、分类排行、月内日柱、年度月折线、日均支出。
 * 全部只统计支出；排行/柱状零填充由调用方（用例）按整月/整年铺满。
 */
internal object AnalyticsAggregator {

    /** 区间合计：支出/收入/笔数（笔数只算支出+收入，中性记录不计入，ADR 0012） */
    fun totals(entries: List<Entry>): AnalyticsTotals = AnalyticsTotals(
        expense = entries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount },
        income = entries.filter { it.type == EntryType.INCOME }.sumOf { it.amount },
        entryCount = entries.count { it.type == EntryType.EXPENSE || it.type == EntryType.INCOME },
    )

    /** 支出类别排行：按合计金额降序，收入类别不参与 */
    fun expenseRanks(entries: List<Entry>): List<CategoryRank> =
        entries.filter { it.type == EntryType.EXPENSE }
            .groupBy { it.categoryName }
            .map { (name, list) -> CategoryRank(name, list.sumOf { it.amount }) }
            .sortedByDescending { it.amount }

    /** 月内每日支出：1..月末 每一天一个点，无支出日为 0.0 */
    fun dailyExpense(month: YearMonth, entries: List<Entry>): List<DailyExpense> {
        val byDay = entries
            .filter { it.type == EntryType.EXPENSE }
            .groupBy { it.date.dayOfMonth }
        return (1..month.lengthOfMonth()).map { day ->
            DailyExpense(day, byDay[day]?.sumOf { it.amount } ?: 0.0)
        }
    }

    /** 年度 12 个月支出：1..12 每月一个点，无支出月为 0.0 */
    fun monthlyExpense(year: Int, entries: List<Entry>): List<MonthlyExpensePoint> {
        val byMonth = entries
            .filter { it.type == EntryType.EXPENSE }
            .groupBy { it.date.monthValue }
        return (1..12).map { month ->
            MonthlyExpensePoint(YearMonth.of(year, month), byMonth[month]?.sumOf { it.amount } ?: 0.0)
        }
    }

    /**
     * 年度热力图逐日摘要：全年每个日期均有一个点。
     * 支出金额只汇总支出，笔数与是否记账只算支出+收入（中性记录不计入，ADR 0012）。
     */
    fun annualHeatmap(year: Int, entries: List<Entry>): List<AnnualHeatmapDay> {
        val entriesByDate = entries.groupBy { it.date }
        val start = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        return generateSequence(start) { date ->
            date.takeIf { it < end }?.plusDays(1)
        }.map { date ->
            val entriesOnDate = entriesByDate[date].orEmpty()
            AnnualHeatmapDay(
                date = date,
                expense = entriesOnDate
                    .filter { it.type == EntryType.EXPENSE }
                    .sumOf { it.amount },
                entryCount = entriesOnDate.count {
                    it.type == EntryType.EXPENSE || it.type == EntryType.INCOME
                },
            )
        }.toList()
    }

    /** 日均支出：支出合计 ÷ 有支出的天数（不 ÷365，避免未记账的日子拉低均值） */
    fun avgDailyExpense(entries: List<Entry>): Double {
        val expenseEntries = entries.filter { it.type == EntryType.EXPENSE }
        val daysWithExpense = expenseEntries.map { it.date }.distinct().size
        if (daysWithExpense == 0) return 0.0
        return expenseEntries.sumOf { it.amount } / daysWithExpense
    }
}
