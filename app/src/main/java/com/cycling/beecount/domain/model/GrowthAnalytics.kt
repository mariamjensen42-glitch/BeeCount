package com.cycling.beecount.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 高级统计分析（模块 G）的领域模型。
 *
 * 口径说明（与 ADR 0009 的既有图表口径一致，且明确区分两种统计）：
 * - **逐笔维度**（频次/客单价/中位数/方差/标准差）只统计每笔【支出】的原始金额（不冲减退款），
 *   因为「每次消费多少钱」看的是单笔交易本身；退款另算。
 * - **占比维度**（刚性/可选/冲动占比）同样按支出分类的原始金额计算，退款不在此扣减。
 * - **净资产趋势**采用累计口径：净资产 = 累计收入 − 累计支出（不含中性/退款，退款在支出中已冲抵），
 *   是「到某天为止的净现金流」，可从任意时间点回溯。
 */

/** 分类频次：一段时间内一个支出类别的记账笔数 */
data class CategoryCount(
    val name: String,
    val count: Int,
)

/** 标签云项：一段时间内一个标签的消费聚合。[amount] 越大字体越大（消费越多），[count] 为笔数 */
data class TagCloudItem(
    val name: String,
    val count: Int,
    val amount: Double,
    val color: Long,
)

/**
 * 支出分布统计（模块 G 176-180）。
 * [expenseCount] 是支出笔数（「本月总共吃了多少次饭」的聚合口径），
 * [perCategoryCounts] 为按支出类别分组的笔数降序；
 * [avgTicket] 客单价 = 支出总额 ÷ 支出笔数；[median] 中位数；[variance]/[stdDev] 支出波动。
 */
data class SpendingStats(
    val expenseCount: Int,
    val perCategoryCounts: List<CategoryCount>,
    val avgTicket: Double,
    val median: Double,
    val maxExpense: Entry?,
    val maxIncome: Entry?,
    val variance: Double,
    val stdDev: Double,
    /** 变异系数 = 标准差 ÷ 均值，衡量支出波动相对大小；均值为 0 时为 0 */
    val coefficientOfVariation: Double,
)

/** 一天中的时间段（模块 G 182），[fraction] 为该段占全部支出比例（0..1） */
data class TimeSlotAmount(
    val slot: TimeSlot,
    val amount: Double,
    val count: Int,
    val fraction: Float,
)

enum class TimeSlot(val label: String) {
    MORNING("上午"),
    AFTERNOON("下午"),
    EVENING("晚上"),
}

/** 星期几消费摘要（模块 G 181）：某天支出合计与笔数 */
data class WeekdayStats(
    val dayOfWeek: DayOfWeek,
    val expense: Double,
    val count: Int,
)

/**
 * 周末 vs 工作日对比（模块 G 181）。
 * [weekendPerDay]/[weekdayPerDay] 为各自「日均」（合计 ÷ 该组天数），
 * [extraPercent] 为周末日均超出工作日日均的百分比（周末更省时为负）。
 */
data class WeekendVsWeekday(
    val weekendExpense: Double,
    val weekendPerDay: Double,
    val weekdayExpense: Double,
    val weekdayPerDay: Double,
    val extraPercent: Double,
)

/**
 * 支出刚性结构（模块 G 183-185）。
 * [rigid] 刚性/固定支出（房租水电等维持基本生活的必要支出），[variable] 可选支出，
 * [impulse] 冲动/非必需支出（可选支出中偏「想要」的部分）；三者占比以总支出为分母。
 */
data class ExpenseRigidity(
    val totalExpense: Double,
    val rigidExpense: Double,
    val rigidRatio: Float,
    val variableExpense: Double,
    val variableRatio: Float,
    val impulseExpense: Double,
    val impulseRatio: Float,
)

/**
 * 净资产趋势的一个数据点（模块 G 186-187）。
 * [netAsset] = 截止该点的累计收入 − 累计支出；
 * [deficitRatio] 资产负债率代理（未覆盖支出占比）：累计支出超出累计收入的部分 ÷ 累计支出，
 * 反映财务杠杆/超支压力，介于 0..1，超过即超支。
 */
data class NetAssetPoint(
    val point: LocalDate,
    val netAsset: Double,
    val deficitRatio: Float,
)

/** 净资产趋势（模块 G 186-187）：按所选粒度逐点铺满 */
data class NetAssetTrend(
    val points: List<NetAssetPoint>,
)

/** 财务健康评分的一个子维度（模块 G 188） */
data class HealthMetric(
    val name: String,
    val score: Int,
    val weight: Int,
    val detail: String,
)

/** 财务健康评分（模块 G 188）：加权综合 0..100，附评级与分维度明细 */
data class FinanceHealthScore(
    val total: Int,
    val grade: String,
    val metrics: List<HealthMetric>,
)

/** 年度收支报告的一个章节（模块 G 189） */
data class AnnualReportSection(
    val title: String,
    val lines: List<String>,
)

/** 年度收支报告书（模块 G 189）：自动生成的文本摘要 */
data class AnnualReport(
    val year: Int,
    val sections: List<AnnualReportSection>,
)

/** 某周期（月/年）的深度统计数据聚合（模块 G） */
data class GrowthAnalytics(
    val spendingStats: SpendingStats,
    val timeSlots: List<TimeSlotAmount>,
    val weekdayStats: List<WeekdayStats>,
    val weekendVsWeekday: WeekendVsWeekday,
    val rigidity: ExpenseRigidity,
    val health: FinanceHealthScore,
    val tagCloud: List<TagCloudItem> = emptyList(),
)
