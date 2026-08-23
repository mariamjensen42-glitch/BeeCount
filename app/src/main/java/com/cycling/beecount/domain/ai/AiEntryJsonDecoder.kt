package com.cycling.beecount.domain.ai

import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * 将模型返回的 JSON 文本解码为 [AiParseResult]。
 *
 * 模型输出约定（见 DeepSeek JSON Output）：
 * - recordable=true 时输出 type/amount_raw/amount/category/date
 * - recordable=false 时输出 message
 */
class AiEntryJsonDecoder @Inject constructor(
    private val json: Json,
) {

    @Serializable
    private data class AiEntryDto(
        val recordable: Boolean,
        val type: String? = null,
        val amount_raw: String? = null,
        val amount: Double? = null,
        val category: String? = null,
        val date: String? = null,
        val message: String? = null,
        val tags: List<String>? = null,
        val note: String? = null,
        val counterparty: String? = null,
    )

    /**
     * 解码并校验。返回 null 表示格式非法（调用方应重试或报错）。
     */
    fun decode(raw: String): AiParseResult? = runCatching {
        val dto = json.decodeFromString<AiEntryDto>(raw)
        if (!dto.recordable) {
            Timber.d("AI 返回 recordable=false，message=%s", dto.message)
            return@runCatching AiParseResult(recordable = false, message = dto.message)
        }
        val type = when (dto.type?.lowercase()) {
            "expense" -> EntryType.EXPENSE
            "income" -> EntryType.INCOME
            else -> {
                Timber.w("AI 返回未知 type：%s", dto.type)
                return@runCatching null
            }
        }
        val amount = dto.amount ?: run {
            Timber.w("AI 返回缺少 amount")
            return@runCatching null
        }
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            Timber.w("AI 返回非法 amount：%s", amount)
            return@runCatching null
        }
        val amountRaw = dto.amount_raw?.trim().orEmpty()
        if (amountRaw.isEmpty()) {
            Timber.w("AI 返回缺少 amount_raw")
            return@runCatching null
        }
        val category = dto.category?.trim().orEmpty()
        if (category.isEmpty()) {
            Timber.w("AI 返回缺少 category")
            return@runCatching null
        }
        val date = dto.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: run {
                Timber.w("AI 返回非法或缺失 date：%s", dto.date)
                return@runCatching null
            }
        val tags = dto.tags.orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(3)
        val note = dto.note?.trim()?.takeIf { it.isNotEmpty() }
        val counterparty = dto.counterparty?.trim()?.takeIf { it.isNotEmpty() }
        Timber.d("AI JSON 解码成功：type=%s，amount=%s，category=%s，date=%s", type, amount, category, date)
        AiParseResult(
            recordable = true,
            type = type,
            amount = amount,
            amountRaw = amountRaw,
            categoryName = category,
            date = date,
            tags = tags,
            note = note,
            counterparty = counterparty,
        )
    }.onFailure { e ->
        Timber.w(e, "AI JSON 解码失败：%s", e.message)
    }.getOrNull()
}
