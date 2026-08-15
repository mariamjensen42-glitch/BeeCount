package com.cycling.beecount.domain.model

import java.time.LocalDate

/**
 * AI 解析结果：用户原话经模型解析后的结构化产出。
 *
 * [recordable] 为 false 时表示输入不是一笔可记账的收支（闲聊、查询等），
 * 此时 [message] 携带模型给出的自然语言回应，其余字段为空。
 * [note] 仅 OCR 路径使用：模型从截图原文中提炼的简短备注（商家名/消费场景等），
 * 普通文字输入时为 null，由调用方决定是否用原话覆盖。
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
    val note: String? = null,
)
