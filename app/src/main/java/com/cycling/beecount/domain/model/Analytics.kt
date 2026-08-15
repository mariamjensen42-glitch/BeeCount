package com.cycling.beecount.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * 图表聚合的领域模型（ADR 0009）。
 * 排行/趋势只统计支出——收入类别稀少，对它们无意义（见 CONTEXT.md「分类排行」）。
 */

/** 分类排行条目：某段时间内一个支出类别的合计金额 */
data class CategoryRank(
    val name: String,
    val amount: Double,
)

/** 某日支出：月内每日柱状图的一个数据点，无支出的天 amount 为 0.0 */
data class DailyExpense(
    val day: Int,
    val amount: Double,
)

/** 月度报表：图表页「月度」粒度的聚合结果 */
data class MonthlyAnalytics(
    val month: YearMonth,
    val expense: Double,
    val income: Double,
    val entryCount: Int,
    val categoryRanks: List<CategoryRank>,
    /** 1..月末 每一天一个点，全月铺满以保留趋势间隔感 */
    val dailyExpense: List<DailyExpense>,
    /** 单日支出最高的那天；本月无支出时为 null */
    val maxDaily: DailyExpense?,
)

/** 年度趋势的一个月数据点 */
data class MonthlyExpensePoint(
    val month: YearMonth,
    val amount: Double,
)

/**
 * 年度热力图的单日摘要。
 *
 * 每个年度内日期恰好对应一个摘要；[expense] 只累计支出，[entryCount] 统计当天全部已入库账目。
 */
data class AnnualHeatmapDay(
    val date: LocalDate,
    val expense: Double,
    val entryCount: Int,
) {
    val hasEntries: Boolean
        get() = entryCount > 0
}

/** 年度高亮：图表页「年度」粒度的 4 格数字卡 */
data class AnnualHighlights(
    val busiestMonth: YearMonth?,
    val busiestAmount: Double,
    val biggestEntry: Entry?,
    /** 全年支出 ÷ 有支出的天数（不 ÷365，见 ADR 0009） */
    val avgDailyExpense: Double,
)

/** 年度报告：图表页「年度」粒度的聚合结果 */
data class AnnualAnalytics(
    val year: Int,
    val expense: Double,
    val income: Double,
    val entryCount: Int,
    val categoryRanks: List<CategoryRank>,
    /** 1..12 月每月一个点，全月铺满 */
    val monthlyExpense: List<MonthlyExpensePoint>,
    /** 1 月 1 日至 12 月 31 日每天一个摘要，用于年度热力图 */
    val dailyHeatmap: List<AnnualHeatmapDay>,
    val highlights: AnnualHighlights,
)
