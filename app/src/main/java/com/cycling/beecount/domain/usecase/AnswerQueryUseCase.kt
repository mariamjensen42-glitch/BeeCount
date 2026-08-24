package com.cycling.beecount.domain.usecase

import com.cycling.beecount.data.datasource.AiChatDataSource
import com.cycling.beecount.data.datasource.AiChatResult
import com.cycling.beecount.data.datasource.FailureReason
import com.cycling.beecount.domain.ai.AiQueryIntentDecoder
import com.cycling.beecount.domain.model.CategoryAmount
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.QueryAnswer
import com.cycling.beecount.domain.model.QueryIntent
import com.cycling.beecount.domain.model.QueryMetric
import com.cycling.beecount.domain.model.QueryPeriod
import com.cycling.beecount.domain.model.QueryPeriodKind
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.TagRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 自然语言查询用例：把「用户问句 → 真实数字 → 中文答案」的查询语义收敛在此。
 *
 * 与 [EntryIntake]（写入口）对照：[AnswerQueryUseCase] 是只读查询入口。架构铁律：
 * AI 只识别意图（metric/period/category），真实数字由 [EntryQuery]/[AnalyticsAggregator]
 * 从数据库确定性计算，再由本地模板生成答案——模型绝不编造金额。
 */
@Singleton
class AnswerQueryUseCase @Inject constructor(
    private val entryQuery: EntryQuery,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val decoder: AiQueryIntentDecoder,
    private val aiChatDataSource: AiChatDataSource,
    private val aiKeyRepository: AiKeyRepository,
    private val currentDate: () -> LocalDate,
) {

    sealed interface Outcome {
        data class Answered(val answer: String, val data: QueryAnswer) : Outcome
        data object NotAQuery : Outcome
        data object KeyMissing : Outcome
        data class Error(val message: String) : Outcome
    }

    suspend fun answer(question: String): Outcome {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return Outcome.Error("请输入要查询的内容")

        val key = aiKeyRepository.getKey()
        if (key.isNullOrBlank()) return Outcome.KeyMissing

        val categories = categoryRepository.observeAll().first().map { it.name }
        val tags = tagRepository.observeAll().first().map { it.name }
        val today = currentDate()
        val systemPrompt = buildIntentPrompt(categories, tags, today)

        repeat(2) { attempt ->
            when (val result = aiChatDataSource.complete(key, systemPrompt, trimmed)) {
                is AiChatResult.Content -> {
                    val intent = decoder.decode(result.text)
                    if (intent == null) {
                        if (attempt == 0) return@repeat
                        return Outcome.Error("没看懂这个问题，换种说法试试？")
                    }
                    if (!intent.isQuery) return Outcome.NotAQuery
                    return resolve(intent, today, trimmed)
                }

                is AiChatResult.Failure -> when (result.reason) {
                    FailureReason.KEY_INVALID -> return Outcome.Error("API Key 无效，请到设置里检查")
                    FailureReason.NETWORK -> {
                        if (attempt == 0) return@repeat
                        return Outcome.Error("网络开小差了，请稍后再试")
                    }
                }
            }
        }
        return Outcome.Error("网络开小差了，请稍后再试")
    }

    private suspend fun resolve(intent: QueryIntent, today: LocalDate, question: String): Outcome {
        val metric = intent.metric ?: return Outcome.Error("查询意图缺少指标")
        val period = resolvePeriod(intent, today)
        val entries = entryQuery.observeRange(period.start, period.end).first()
        val filtered = applyFilters(entries, intent)

        val breakdown = if (metric == QueryMetric.CATEGORY_RANKS) {
            AnalyticsAggregator.expenseRanks(filtered)
                .map { CategoryAmount(it.name, it.amount) }
        } else {
            emptyList()
        }
        val value = computeValue(metric, filtered)
        val count = filtered.count { it.type == EntryType.EXPENSE || it.type == EntryType.INCOME }
        val answer = QueryAnswer(
            metric = metric,
            period = period,
            categoryFilter = intent.category,
            value = value,
            count = count,
            periodLabel = periodLabel(period),
            breakdown = breakdown,
        )
        return Outcome.Answered(formatAnswer(answer), answer)
    }

    private fun applyFilters(entries: List<Entry>, intent: QueryIntent): List<Entry> {
        var list = entries
        intent.category?.let { cat -> list = list.filter { matches(it.categoryName, cat) } }
        intent.tag?.let { tag ->
            list = list.filter { e -> e.tags.any { t -> matches(t.name, tag) } }
        }
        return list
    }

    /** 类别/标签容忍匹配：精确、前缀（二级「餐饮·外卖」）、子串都算命中。 */
    private fun matches(name: String, query: String): Boolean =
        name == query || name.startsWith("$query·") || name.contains(query)

    private fun computeValue(metric: QueryMetric, entries: List<Entry>): Double {
        val expenseNet = entries
            .filter { it.type == EntryType.EXPENSE }.sumOf { it.amount } -
            entries.filter { it.type == EntryType.REFUND }.sumOf { it.amount }
        return when (metric) {
            QueryMetric.TOTAL_EXPENSE -> expenseNet
            QueryMetric.TOTAL_INCOME -> entries.filter { it.type == EntryType.INCOME }.sumOf { it.amount }
            QueryMetric.NET -> entries.filter { it.type == EntryType.INCOME }.sumOf { it.amount } - expenseNet
            QueryMetric.COUNT -> entries.count { it.type == EntryType.EXPENSE || it.type == EntryType.INCOME }.toDouble()
            QueryMetric.CATEGORY_RANKS -> expenseNet
            QueryMetric.AVG_DAILY -> AnalyticsAggregator.avgDailyExpense(entries)
        }
    }

    private fun resolvePeriod(intent: QueryIntent, today: LocalDate): QueryPeriod {
        val kind = intent.periodKind ?: QueryPeriodKind.THIS_MONTH
        val (start, end) = when (kind) {
            QueryPeriodKind.THIS_MONTH -> {
                val m = YearMonth.from(today); m.atDay(1) to m.atEndOfMonth()
            }
            QueryPeriodKind.LAST_MONTH -> {
                val m = YearMonth.from(today).minusMonths(1); m.atDay(1) to m.atEndOfMonth()
            }
            QueryPeriodKind.THIS_YEAR -> {
                val y = today.year; YearMonth.of(y, 1).atDay(1) to YearMonth.of(y, 12).atEndOfMonth()
            }
            QueryPeriodKind.LAST_YEAR -> {
                val y = today.year - 1; YearMonth.of(y, 1).atDay(1) to YearMonth.of(y, 12).atEndOfMonth()
            }
            QueryPeriodKind.THIS_WEEK -> {
                val s = today.with(WeekFields.of(Locale.CHINA).dayOfWeek(), 1); s to today
            }
            QueryPeriodKind.LAST_WEEK -> {
                val s = today.with(WeekFields.of(Locale.CHINA).dayOfWeek(), 1).minusWeeks(1)
                s to s.plusDays(6)
            }
            QueryPeriodKind.ALL -> LocalDate.of(1970, 1, 1) to today
            QueryPeriodKind.CUSTOM -> (intent.customStart ?: today) to (intent.customEnd ?: today)
        }
        return QueryPeriod(kind, start, end)
    }

    private fun periodLabel(period: QueryPeriod): String {
        val f = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val ym = DateTimeFormatter.ofPattern("yyyy-MM")
        return when (period.kind) {
            QueryPeriodKind.THIS_MONTH -> "本月（${YearMonth.from(period.start).format(ym)}）"
            QueryPeriodKind.LAST_MONTH -> "上月（${YearMonth.from(period.start).format(ym)}）"
            QueryPeriodKind.THIS_YEAR -> "本年（${period.start.year}）"
            QueryPeriodKind.LAST_YEAR -> "去年（${period.start.year}）"
            QueryPeriodKind.THIS_WEEK -> "本周（${period.start.format(f)}~${period.end.format(f)}）"
            QueryPeriodKind.LAST_WEEK -> "上周（${period.start.format(f)}~${period.end.format(f)}）"
            QueryPeriodKind.ALL -> "全部历史"
            QueryPeriodKind.CUSTOM -> "（${period.start.format(f)}~${period.end.format(f)}）"
        }
    }

    private fun formatAnswer(a: QueryAnswer): String {
        val p = a.periodLabel
        val cat = a.categoryFilter?.let { "「$it」" } ?: ""
        val noData = if (a.count == 0 && a.value == 0.0) "（该区间暂无相关记录）" else ""
        return when (a.metric) {
            QueryMetric.TOTAL_EXPENSE ->
                "$p${cat}共支出 ${money(a.value)}（已扣除退款），共 ${a.count} 笔$noData"
            QueryMetric.TOTAL_INCOME ->
                "$p${cat}总收入 ${money(a.value)}，共 ${a.count} 笔$noData"
            QueryMetric.NET ->
                "$p 净收支 ${money(a.value)}（收入 − 支出）$noData"
            QueryMetric.COUNT ->
                "$p 共记账 ${a.count} 笔$noData"
            QueryMetric.AVG_DAILY ->
                "$p 日均支出 ${money(a.value)}（按有记账的天数计算）$noData"
            QueryMetric.CATEGORY_RANKS -> {
                val total = a.breakdown.sumOf { it.amount }
                val top = a.breakdown.take(5)
                    .joinToString("，") { "${it.name} ${money(it.amount)}" }
                val more = if (a.breakdown.size > 5) "…" else ""
                "$p 支出分类排行（合计 ${money(total)}）：$top$more"
            }
        }
    }

    private fun money(amount: Double): String =
        "¥" + String.format(Locale.CHINA, "%,.2f", amount)

    private fun buildIntentPrompt(categories: List<String>, tags: List<String>, today: LocalDate): String {
        val todayText = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val cats = categories.joinToString("、").ifEmpty { "（无）" }
        val tgs = tags.joinToString("、").ifEmpty { "（无）" }
        return """
            |你是一个记账助手的「查询理解」模块。用户会用一句自然语言提问，你需要把它解析为 JSON 并输出。
            |你只负责理解意图，不负责计算和回答——数字会由系统从数据库精确计算。
            |
            |输出必须严格符合以下 JSON（不要输出任何其他文字，包括 Markdown 代码块）：
            |{
            |  "is_query": true 或 false,
            |  "metric": "total_expense" | "total_income" | "net" | "count" | "category_ranks" | "avg_daily"（仅当 is_query 为 true）,
            |  "period": "this_month" | "last_month" | "this_year" | "last_year" | "this_week" | "last_week" | "all" | "custom"（仅当 is_query 为 true）,
            |  "start": "YYYY-MM-DD"（仅当 period 为 custom）,
            |  "end": "YYYY-MM-DD"（仅当 period 为 custom）,
            |  "category": "类别名"（可选，仅当问题限定了某类别；优先从下方类别列表选最贴合的，可写一级或二级如「餐饮」或「餐饮·外卖」）,
            |  "tag": "标签名"（可选，仅当问题限定了某标签；从下方标签列表选）,
            |  "message": "简短回应"（仅当 is_query 为 false，例如闲聊或无法理解为查询时）
            |}
            |
            |判断规则：
            |- 用户在问「花了多少/收入多少/净收支/花了几笔/各类别排行/日均」等统计类问题，is_query 为 true。
            |- 用户在描述一笔要记的收支、或闲聊、或无法理解为统计查询时，is_query 为 false，并用 message 简短回应。
            |- 相对时间换算：今天=$todayText；「本月」=this_month，「上月」=last_month，「本周/上周」按周一为一周起点，「今年/去年」对应 this_year/last_year，「全部/历史」=all。
            |- 用户没说时间范围时默认 this_month。
            |- 用户没限定类别/标签时省略对应字段。
            |
            |可用类别：$cats
            |可用标签：$tgs
            |
            |示例输入：上月餐饮花多少
            |示例输出：{"is_query": true, "metric": "total_expense", "period": "last_month", "category": "餐饮"}
            |
            |示例输入：这个月一共记了多少笔
            |示例输出：{"is_query": true, "metric": "count", "period": "this_month"}
            |
            |示例输入：去年各类别花钱排行
            |示例输出：{"is_query": true, "metric": "category_ranks", "period": "last_year"}
            |
            |示例输入：你好
            |示例输出：{"is_query": false, "message": "你好呀！你可以问我「上月餐饮花多少」之类的问题～"}
        """.trimMargin()
    }
}
