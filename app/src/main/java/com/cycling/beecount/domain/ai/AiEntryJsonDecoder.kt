package com.cycling.beecount.domain.ai

import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    )

    /**
     * 解码并校验。返回 null 表示格式非法（调用方应重试或报错）。
     */
    fun decode(raw: String): AiParseResult? = runCatching {
        val dto = json.decodeFromString<AiEntryDto>(raw)
        if (!dto.recordable) {
            return@runCatching AiParseResult(recordable = false, message = dto.message)
        }
        val type = when (dto.type?.lowercase()) {
            "expense" -> EntryType.EXPENSE
            "income" -> EntryType.INCOME
            else -> return@runCatching null
        }
        val amount = dto.amount ?: return@runCatching null
        if (amount <= 0.0 || amount.isNaN() || amount.isInfinite()) return@runCatching null
        val amountRaw = dto.amount_raw?.trim().orEmpty()
        if (amountRaw.isEmpty()) return@runCatching null
        val category = dto.category?.trim().orEmpty()
        if (category.isEmpty()) return@runCatching null
        val date = dto.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return@runCatching null
        AiParseResult(
            recordable = true,
            type = type,
            amount = amount,
            amountRaw = amountRaw,
            categoryName = category,
            date = date,
        )
    }.getOrNull()
}
