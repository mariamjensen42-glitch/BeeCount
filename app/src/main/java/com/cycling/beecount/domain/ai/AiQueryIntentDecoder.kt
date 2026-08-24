package com.cycling.beecount.domain.ai

import com.cycling.beecount.domain.model.QueryIntent
import com.cycling.beecount.domain.model.QueryMetric
import com.cycling.beecount.domain.model.QueryPeriodKind
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * 将模型返回的查询意图 JSON 解码为 [QueryIntent]。
 *
 * 与 [AiEntryJsonDecoder] 同构：模型只输出结构化意图（is_query/metric/period/...），
 * 数字由调用方从数据库计算。返回 null 表示格式非法（调用方应重试或报错）。
 */
class AiQueryIntentDecoder @Inject constructor(
    private val json: Json,
) {

    @Serializable
    private data class QueryIntentDto(
        val is_query: Boolean? = null,
        val metric: String? = null,
        val period: String? = null,
        val start: String? = null,
        val end: String? = null,
        val category: String? = null,
        val tag: String? = null,
        val message: String? = null,
    )

    fun decode(raw: String): QueryIntent? = runCatching {
        val dto = json.decodeFromString<QueryIntentDto>(raw)
        val isQuery = dto.is_query ?: false
        if (!isQuery) {
            return@runCatching QueryIntent(isQuery = false, message = dto.message)
        }

        val metric = when (dto.metric?.lowercase()) {
            "total_expense", "expense" -> QueryMetric.TOTAL_EXPENSE
            "total_income", "income" -> QueryMetric.TOTAL_INCOME
            "net" -> QueryMetric.NET
            "count" -> QueryMetric.COUNT
            "category_ranks", "category" -> QueryMetric.CATEGORY_RANKS
            "avg_daily", "avg" -> QueryMetric.AVG_DAILY
            else -> {
                Timber.w("AI 返回未知 metric：%s", dto.metric)
                return@runCatching null
            }
        }

        val periodKind = when (dto.period?.lowercase()) {
            "this_week" -> QueryPeriodKind.THIS_WEEK
            "last_week" -> QueryPeriodKind.LAST_WEEK
            "this_month" -> QueryPeriodKind.THIS_MONTH
            "last_month" -> QueryPeriodKind.LAST_MONTH
            "this_year" -> QueryPeriodKind.THIS_YEAR
            "last_year" -> QueryPeriodKind.LAST_YEAR
            "all" -> QueryPeriodKind.ALL
            "custom" -> QueryPeriodKind.CUSTOM
            else -> {
                Timber.w("AI 返回未知 period：%s", dto.period)
                return@runCatching null
            }
        }

        val (customStart, customEnd) = if (periodKind == QueryPeriodKind.CUSTOM) {
            val s = dto.start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val e = dto.end?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (s == null || e == null) {
                Timber.w("custom 周期缺少合法 start/end：%s / %s", dto.start, dto.end)
                return@runCatching null
            }
            s to e
        } else {
            null to null
        }

        QueryIntent(
            isQuery = true,
            metric = metric,
            periodKind = periodKind,
            customStart = customStart,
            customEnd = customEnd,
            category = dto.category?.trim()?.takeIf { it.isNotEmpty() },
            tag = dto.tag?.trim()?.takeIf { it.isNotEmpty() },
        )
    }.onFailure { e ->
        Timber.w(e, "查询意图 JSON 解码失败：%s", e.message)
    }.getOrNull()
}
