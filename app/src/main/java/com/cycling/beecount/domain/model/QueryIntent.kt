package com.cycling.beecount.domain.model

import java.time.LocalDate

/**
 * 自然语言查询的领域模型。
 *
 * 设计原则：AI 只负责「理解意图」，真实数字一律由 [EntryQuery]/[AnalyticsAggregator] 从数据库计算，
 * 再由 [AnswerQueryUseCase] 用确定性模板生成中文答案，杜绝模型编造金额。
 */

/** 查询指标：AI 从自然语言里识别出来，决定后续聚合方式。 */
enum class QueryMetric {
    /** 总支出（已扣除退款） */
    TOTAL_EXPENSE,

    /** 总收入 */
    TOTAL_INCOME,

    /** 净收支 = 收入 − 支出 */
    NET,

    /** 记账笔数 */
    COUNT,

    /** 分类支出排行（返回 breakdown） */
    CATEGORY_RANKS,

    /** 日均支出（按有记账的天数计算） */
    AVG_DAILY,
}

/** 时间范围类别；CUSTOM 时附带具体起止日期。 */
enum class QueryPeriodKind {
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    THIS_YEAR,
    LAST_YEAR,
    ALL,
    CUSTOM,
}

/** 已解析为绝对日期的查询区间。 */
data class QueryPeriod(
    val kind: QueryPeriodKind,
    val start: LocalDate,
    val end: LocalDate,
)

/**
 * AI 解析出的查询意图。
 *
 * [isQuery]=false 时表示输入并非统计查询（如记账描述、闲聊），调用方应回退到记账管线，
 * [message] 可承载简短回应。
 */
data class QueryIntent(
    val isQuery: Boolean,
    val metric: QueryMetric? = null,
    val periodKind: QueryPeriodKind? = null,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val category: String? = null,
    val tag: String? = null,
    val message: String? = null,
)

/** 单类别金额，用于分类排行展示。 */
data class CategoryAmount(
    val name: String,
    val amount: Double,
)

/**
 * 查询的最终答案（确定性计算，非模型生成）。
 *
 * [value] 为主指标数值；[breakdown] 仅在 [metric]=CATEGORY_RANKS 时有意义；
 * [periodLabel] 为中文区间描述，供 UI 直接展示。
 */
data class QueryAnswer(
    val metric: QueryMetric,
    val period: QueryPeriod,
    val categoryFilter: String?,
    val value: Double,
    val count: Int,
    val periodLabel: String,
    val breakdown: List<CategoryAmount> = emptyList(),
)
