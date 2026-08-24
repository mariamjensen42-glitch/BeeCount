package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.AnnualReport
import com.cycling.beecount.domain.model.AnnualReportSection
import com.cycling.beecount.domain.model.CategoryCount
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.ExpenseRigidity
import com.cycling.beecount.domain.model.FinanceHealthScore
import com.cycling.beecount.domain.model.GrowthAnalytics
import com.cycling.beecount.domain.model.HealthMetric
import com.cycling.beecount.domain.model.NetAssetPoint
import com.cycling.beecount.domain.model.NetAssetTrend
import com.cycling.beecount.domain.model.SpendingStats
import com.cycling.beecount.domain.model.TagCloudItem
import com.cycling.beecount.domain.model.TimeSlot
import com.cycling.beecount.domain.model.TimeSlotAmount
import com.cycling.beecount.domain.model.WeekdayStats
import com.cycling.beecount.domain.model.WeekendVsWeekday
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

/**
 * 高级统计聚合（模块 G）的共享纯函数。
 * 与 [AnalyticsAggregator] 同构：只读、纯函数、便于单测。
 *
 * 口径约定（与 [AnalyticsAggregator] 既有图表口径一致并明确区分）：
 * - 逐笔维度（频次/客单价/中位数/方差/标准差）按每笔【支出】的原始金额计算，不冲减退款。
 * - 刚性/可选/冲动占比同样按支出原始金额，只在组内分类、不扣退款。
 * - 净资产趋势是累计口径（收入 − 支出），需要全量历史，不在此函数内做区间裁剪。
 */
internal object GrowthAggregator {

    /** 组装某区间的深度统计（模块 G）：由各纯函数聚合而成，periodDays 供健康覆盖评分使用 */
    fun buildAnalysis(entries: List<Entry>, periodDays: Int): GrowthAnalytics = GrowthAnalytics(
        spendingStats = spendingStats(entries),
        timeSlots = timeSlots(entries),
        weekdayStats = weekdayStats(entries),
        weekendVsWeekday = weekendVsWeekday(entries),
        rigidity = rigidity(entries),
        health = health(entries, periodDays),
        tagCloud = tagCloud(entries),
    )

    /** 支出/退款的组合净额（冲减退款、不低于 0） */
    private fun List<Entry>.netExpense(): Double =
        (filter { it.type == EntryType.EXPENSE }.sumOf { it.amount } -
            filter { it.type == EntryType.REFUND }.sumOf { it.amount }).coerceAtLeast(0.0)

    /**
     * 分类频次：只统计支出类别，按笔数降序。
     * 退款与中性记录不计入「吃了多少次饭」这类笔数统计。
     */
    fun categoryCounts(entries: List<Entry>): List<CategoryCount> =
        entries.filter { it.type == EntryType.EXPENSE }
            .groupBy { it.categoryName }
            .map { (name, list) -> CategoryCount(name, list.size) }
            .sortedByDescending { it.count }

    /** 支出分布统计（176-180） */
    fun spendingStats(entries: List<Entry>): SpendingStats {
        val expenses = entries.filter { it.type == EntryType.EXPENSE }.map { it.amount }.sorted()
        val count = expenses.size
        val avg = if (count == 0) 0.0 else expenses.sum() / count
        val median = when {
            count == 0 -> 0.0
            count % 2 == 1 -> expenses[count / 2]
            else -> (expenses[count / 2 - 1] + expenses[count / 2]) / 2.0
        }
        val variance = if (count == 0) 0.0 else expenses.sumOf { (it - avg) * (it - avg) } / count
        val stdDev = sqrt(variance)
        return SpendingStats(
            expenseCount = count,
            perCategoryCounts = categoryCounts(entries),
            avgTicket = avg,
            median = median,
            maxExpense = entries.filter { it.type == EntryType.EXPENSE }.maxByOrNull { it.amount },
            maxIncome = entries.filter { it.type == EntryType.INCOME }.maxByOrNull { it.amount },
            variance = variance,
            stdDev = stdDev,
            coefficientOfVariation = if (avg == 0.0) 0.0 else stdDev / avg,
        )
    }

    /** 一天中的时间段（182）：按记账时间 createdAt 的小时划分上午/下午/晚上 */
    fun timeSlots(entries: List<Entry>): List<TimeSlotAmount> {
        val total = entries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount }
        val bySlot = entries.filter { it.type == EntryType.EXPENSE }
            .groupBy { slotOf(it) }
        return TimeSlot.entries.map { slot ->
            val group = bySlot[slot].orEmpty()
            val amount = group.sumOf { it.amount }
            TimeSlotAmount(
                slot = slot,
                amount = amount,
                count = group.size,
                fraction = if (total == 0.0) 0f else (amount / total).toFloat(),
            )
        }
    }

    private fun slotOf(entry: Entry): TimeSlot {
        val hour = Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).hour
        return when {
            hour < 12 -> TimeSlot.MORNING
            hour < 18 -> TimeSlot.AFTERNOON
            else -> TimeSlot.EVENING
        }
    }

    /** 星期几消费摘要（181）：按支出净额与笔数聚合到星期 */
    fun weekdayStats(entries: List<Entry>): List<WeekdayStats> =
        DayOfWeek.entries.map { dow ->
            val onDow = entries.filter { it.date.dayOfWeek == dow }
            WeekdayStats(
                dayOfWeek = dow,
                expense = onDow.netExpense(),
                count = onDow.count { it.type == EntryType.EXPENSE },
            )
        }

    /** 周末 vs 工作日（181）：各自「有支出的日均」对比 */
    fun weekendVsWeekday(entries: List<Entry>): WeekendVsWeekday {
        val weekend = entries.filter { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val weekday = entries.filter { it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY }
        val weekendDays = weekend.filter { it.type == EntryType.EXPENSE || it.type == EntryType.REFUND }
            .map { it.date }.distinct().size
        val weekdayDays = weekday.filter { it.type == EntryType.EXPENSE || it.type == EntryType.REFUND }
            .map { it.date }.distinct().size
        val weekendExpense = weekend.netExpense()
        val weekdayExpense = weekday.netExpense()
        val weekendPerDay = if (weekendDays == 0) 0.0 else weekendExpense / weekendDays
        val weekdayPerDay = if (weekdayDays == 0) 0.0 else weekdayExpense / weekdayDays
        val extra = if (weekdayPerDay == 0.0) 0.0 else (weekendPerDay - weekdayPerDay) / weekdayPerDay * 100.0
        return WeekendVsWeekday(
            weekendExpense = weekendExpense,
            weekendPerDay = weekendPerDay,
            weekdayExpense = weekdayExpense,
            weekdayPerDay = weekdayPerDay,
            extraPercent = extra,
        )
    }

    /**
     * 支出刚性结构（183-185）。
     * 按「类别路径的任一分段」匹配关键词归类：一级分类（如「居住」「购物」）语义即父分段，
     * 子分类（如「居住·房租」）沿父分段的必要性归类。
     * - 刚性：维持基本生活的必要支出（居住、医疗、教育、交通）。
     * - 可变：可选/弹性支出（其余类别）。
     * - 冲动：可变支出中偏「想要」的非必需部分（购物、娱乐、人情）。
     */
    fun rigidity(entries: List<Entry>): ExpenseRigidity {
        val expenses = entries.filter { it.type == EntryType.EXPENSE }
        val total = expenses.sumOf { it.amount }
        var rigid = 0.0
        var variable = 0.0
        var impulse = 0.0
        expenses.forEach { e ->
            val segments = e.categoryName.split(CATEGORY_SEPARATOR)
            when {
                segments.any { it in RIGID_CATEGORIES } -> rigid += e.amount
                else -> {
                    variable += e.amount
                    if (segments.any { it in IMPULSE_CATEGORIES }) impulse += e.amount
                }
            }
        }
        fun ratio(amount: Double) = if (total == 0.0) 0f else (amount / total).toFloat()
        return ExpenseRigidity(
            totalExpense = total,
            rigidExpense = rigid,
            rigidRatio = ratio(rigid),
            variableExpense = variable,
            variableRatio = ratio(variable),
            impulseExpense = impulse,
            impulseRatio = ratio(impulse),
        )
    }

    private const val CATEGORY_SEPARATOR = "·"

    private val RIGID_CATEGORIES = setOf("居住", "医疗", "教育", "交通")
    private val IMPULSE_CATEGORIES = setOf("购物", "娱乐", "人情")

    /**
     * 标签云（模块 G）：按支出金额聚合的标签，消费越多字体越大。
     * 只统计支出、按标签原始金额求和（口径与逐笔维度一致，不冲减退款）；
     * 取前 [TAG_CLOUD_LIMIT] 个高消费标签，金额降序。
     */
    fun tagCloud(entries: List<Entry>): List<TagCloudItem> =
        entries.filter { it.type == EntryType.EXPENSE }
            .flatMap { entry -> entry.tags.map { tag -> tag to entry.amount } }
            .groupBy { (tag, _) -> tag }
            .map { (tag, pairs) ->
                TagCloudItem(
                    name = tag.name,
                    count = pairs.size,
                    amount = pairs.sumOf { it.second },
                    color = tag.color,
                )
            }
            .sortedByDescending { it.amount }
            .take(TAG_CLOUD_LIMIT)

    private const val TAG_CLOUD_LIMIT = 40

    /**
     * 净资产趋势（186）：对全量历史按日累计 [收入 − 支出]，返回每日一个点。
     * 从任意时间点可回溯——某天的净资产即截至该天的全部净现金流。
     */
    fun netAssetTrend(entries: List<Entry>): NetAssetTrend {
        val byDate = entries.groupBy { it.date }
        if (byDate.isEmpty()) return NetAssetTrend(emptyList())
        val start = byDate.keys.min()
        val end = byDate.keys.max()
        var net = 0.0
        val points = generateSequence(start) { it.plusDays(1).takeIf { d -> d <= end } }
            .map { date ->
                val day = byDate[date].orEmpty()
                net += day.filter { it.type == EntryType.INCOME }.sumOf { it.amount }
                net -= day.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount }
                net += day.filter { it.type == EntryType.REFUND }.sumOf { it.amount }
                NetAssetPoint(date, net, deficitRatio(entries, date, net))
            }
            .toList()
        return NetAssetTrend(points)
    }

    /** 资产负债率代理：到 [date] 止累计支出超收入的比重（0..1），未超支为 0 */
    private fun deficitRatio(entries: List<Entry>, date: LocalDate, net: Double): Float {
        if (net >= 0) return 0f
        // 累计支出（不含退款冲减）作为分母口径，衡量「超支部分占累计支出」的压力
        val cumulativeExpense = entries.filter { it.date <= date && it.type == EntryType.EXPENSE }
            .sumOf { it.amount }
        if (cumulativeExpense == 0.0) return 0f
        return ((-net) / cumulativeExpense).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 财务健康评分（188）：加权 0..100。
     * 储蓄率 30 / 支出稳定 25 / 冲动控制 25 / 刚性健康度 10 / 记账覆盖 10。
     * 记分单调：储蓄率越高越好、波动越小越好、冲动占比越低越好、覆盖越全越好；
     * 刚性占比以约 35% 为健康目标，过高（必要的固定开销吃掉太多结余）或过低（基本生活无保障）
     * 都导致扣分——区别于「冲动」，刚性高并不等于财务差，而是反映结构平衡。
     */
    fun health(entries: List<Entry>, periodDays: Int): FinanceHealthScore {
        val totals = AnalyticsAggregator.totals(entries)
        val stats = spendingStats(entries)
        val rigidInfo = rigidity(entries)
        val rigid = rigidInfo.rigidRatio.toDouble()
        val impulse = rigidInfo.impulseRatio.toDouble()

        val savingsRate = if (totals.income == 0.0) 0.0 else (totals.income - totals.expense) / totals.income
        val savingsScore = clamp(savingsRate * 100.0)

        val cv = stats.coefficientOfVariation
        val stableScore = clamp((1.0 - cv.coerceIn(0.0, 1.0)) * 100.0)

        val impulseScore = clamp((1.0 - impulse.coerceIn(0.0, 1.0)) * 100.0)

        val rigidScore = clamp(100.0 - kotlin.math.abs(rigid - RIGID_TARGET) * 100.0)

        val activeDays = entries.filter { it.type == EntryType.EXPENSE || it.type == EntryType.INCOME }
            .map { it.date }.distinct().size
        val coverageScore = if (periodDays == 0) 0.0 else clamp(activeDays.toDouble() / periodDays * 100.0)

        val metrics = listOf(
            HealthMetric("储蓄率", savingsScore.toInt(), 30, savingDetail(savingsScore, totals.income, totals.expense)),
            HealthMetric("支出稳定", stableScore.toInt(), 25, "支出波动（变异系数）${"%.2f".format(cv)}"),
            HealthMetric("冲动控制", impulseScore.toInt(), 25, "冲动支出占比 ${"%.0f%%".format(impulse * 100)}"),
            HealthMetric("刚性健康度", rigidScore.toInt(), 10, "刚性支出占比 ${"%.0f%%".format(rigid * 100)}（健康目标 ≈ ${"%.0f%%".format(RIGID_TARGET * 100)}）"),
            HealthMetric("记账覆盖", coverageScore.toInt(), 10, "有账天数 $activeDays / $periodDays"),
        )
        val total = metrics.sumOf { it.score * it.weight } / metrics.sumOf { it.weight }
        return FinanceHealthScore(total = total, grade = gradeOf(total), metrics = metrics)
    }

    private const val RIGID_TARGET = 0.35

    private fun savingDetail(score: Double, income: Double, expense: Double): String {
        val rate = if (income == 0.0) 0.0 else (income - expense) / income
        return "储蓄率 ${"%.0f%%".format(rate * 100)}（收入 ¥${"%.2f".format(income)}）"
    }

    private fun clamp(value: Double): Double = value.coerceIn(0.0, 100.0)

    private fun gradeOf(score: Int): String = when {
        score >= 80 -> "优秀"
        score >= 65 -> "良好"
        score >= 50 -> "及格"
        else -> "待改善"
    }

    /** 年度收支报告书（189）：从聚合结果生成文本章节 */
    fun annualReport(year: Int, analytics: GrowthAnalytics, income: Double, expense: Double): AnnualReport {
        val stats = analytics.spendingStats
        val sections = mutableListOf<AnnualReportSection>()

        val balance = income - expense
        sections += AnnualReportSection(
            "收支概览",
            listOf(
                "全年收入 ¥${"%.2f".format(income)}，支出 ¥${"%.2f".format(expense)}",
                "结余 ¥${"%.2f".format(balance)}（${if (balance >= 0) "盈余" else "超支"}），共记 ${stats.expenseCount} 笔支出",
            ),
        )

        stats.perCategoryCounts.take(3).takeIf { it.isNotEmpty() }?.let { top ->
            sections += AnnualReportSection(
                "支出分类 TOP 3",
                top.mapIndexed { i, c -> "${i + 1}. ${c.name} · ${c.count} 笔" },
            )
        }

        val peakWeekday = analytics.weekdayStats.maxByOrNull { it.expense }?.dayOfWeek
        val peakSlot = analytics.timeSlots.maxByOrNull { it.amount }?.slot
        sections += AnnualReportSection(
            "消费习惯",
            listOfNotNull(
                peakWeekday?.let { "支出最多的一天是周${chineseWeekday(it)}" },
                peakSlot?.let { "支出高峰时段是${it.label}" },
                "周末日均 ¥${"%.2f".format(analytics.weekendVsWeekday.weekendPerDay)}，" +
                    "比工作日${if (analytics.weekendVsWeekday.extraPercent >= 0) "多" else "少"} " +
                    "${"%.1f%%".format(kotlin.math.abs(analytics.weekendVsWeekday.extraPercent))}",
            ),
        )

        sections += AnnualReportSection(
            "年度健康评分",
            listOf(
                "综合得分 ${analytics.health.total}（${analytics.health.grade}）",
                "储蓄率、支出稳定与刚性占比是影响评分的主要维度，详见图表页「财务健康」。",
            ),
        )

        sections += AnnualReportSection(
            "复盘建议",
            listOfNotNull(
                savingsAdvice(analytics, income, expense),
                if (balance < 0) "年度入不敷出，建议优先压缩冲动与可变支出。" else "年度有结余，注意保持支出稳定，避免大额波动。",
            ),
        )

        return AnnualReport(year, sections)
    }

    private fun savingsAdvice(analytics: GrowthAnalytics, income: Double, expense: Double): String? =
        if (income == 0.0) null else {
            val rate = (income - expense) / income
            when {
                rate >= 0.3 -> "储蓄率优秀（${"%.0f%%".format(rate * 100)}），可继续优化投资配置。"
                rate >= 0.1 -> "储蓄率中等（${"%.0f%%".format(rate * 100)}），目标提升到 30%。"
                rate >= 0.0 -> "储蓄偏低（${"%.0f%%".format(rate * 100)}），从冲动支出入手。"
                else -> "储蓄率为负（入不敷出），优先审视刚性支出。"
            }
        }

    private fun chineseWeekday(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }
}
