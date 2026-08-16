package com.cycling.beecount.domain.model

import java.time.LocalDate

/**
 * 待确认草稿（ADR 0014）：通知记账解析成功但用户尚未确认的账目草稿。
 * 确认通知与 App 内待确认队列都展示它；确认后按确认卡片流程入库，不点确认通知不丢。
 *
 * [originalText] 为支付通知原文（闸门通过的标题+正文），确认入库时作为备注的兜底；
 * [note] 为 AI 提炼的简短备注（商户/商品），优先于原文展示。
 */
data class PendingDraft(
    val id: Long = 0L,
    val type: EntryType,
    val amount: Double,
    val amountRaw: String,
    val categoryName: String,
    val date: LocalDate,
    val tags: List<String> = emptyList(),
    val note: String? = null,
    val originalText: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 草稿转回确认卡片所需的解析结果（recordable 恒为 true） */
fun PendingDraft.toAiParseResult(): AiParseResult = AiParseResult(
    recordable = true,
    type = type,
    amount = amount,
    amountRaw = amountRaw,
    categoryName = categoryName,
    date = date,
    tags = tags,
    note = note,
)

/** 解析结果生成待确认草稿（仅 recordable 的结果可转） */
fun AiParseResult.toPendingDraft(originalText: String): PendingDraft = PendingDraft(
    type = requireNotNull(type) { "recordable 结果必须携带类型" },
    amount = requireNotNull(amount) { "recordable 结果必须携带金额" },
    amountRaw = requireNotNull(amountRaw) { "recordable 结果必须携带金额原文" },
    categoryName = requireNotNull(categoryName) { "recordable 结果必须携带类别" },
    date = requireNotNull(date) { "recordable 结果必须携带日期" },
    tags = tags,
    note = note,
    originalText = originalText,
)
