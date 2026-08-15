package com.cycling.beecount.domain.model

import java.time.LocalDate

/**
 * AI 解析结果：用户原话经模型解析后的结构化产出。
 *
 * [recordable] 为 false 时表示输入不是一笔可记账的收支（闲聊、查询等），
 * 此时 [message] 携带模型给出的自然语言回应，其余字段为空。
 */
data class AiParseResult(
    val recordable: Boolean,
    val type: EntryType? = null,
    val amount: Double? = null,
    val amountRaw: String? = null,
    val categoryName: String? = null,
    val date: LocalDate? = null,
    val message: String? = null,
    val tags: List<String> = emptyList(),
)
