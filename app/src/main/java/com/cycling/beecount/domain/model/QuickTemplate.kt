package com.cycling.beecount.domain.model

import kotlinx.serialization.Serializable

/**
 * 快捷模板：用户自定义的一键记账模板。
 *
 * 点击即构建一条 [AiParseResult] 并弹出确认卡（复用 [EntryIntake.confirm] 入库），
 * 免去每次输入「早餐=豆浆油条+5元」这类高频记账。
 * [amountRaw] 为确认卡「金额原文」展示的原文，[note] 作为该笔账目的备注。
 */
@Serializable
data class QuickTemplate(
    val id: Long = 0L,
    val title: String,
    val categoryName: String,
    val amount: Double,
    val type: EntryType = EntryType.EXPENSE,
    val amountRaw: String = "",
    val note: String = "",
    val tags: List<String> = emptyList(),
)
