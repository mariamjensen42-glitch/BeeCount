package com.cycling.beecount.domain.model

import java.time.LocalDate

/**
 * 账目：一条已入库的收支记录。
 *
 * [amountRaw] 保留用户口语金额原文（如"1万"），[amount] 是其归一化后的元数值，
 * 确认卡片同时展示两者以便用户核对换算。
 *
 * [counterparty] 是交易对方（商家/收到的转账方等），可为空——微信账单导入时来自账单行，
 * 手动/AI/OCR 记账在解析可识别时写入，无法识别时为 null。
 *
 * [sourceRef] 是来源引用（ADR 0012）：微信导入的账目记录其交易单号，可空、全库唯一，
 * 用于去重与"撤销本次导入"按集合定位；手动/AI/OCR 记账为 null。
 *
 * [isReimbursed] 仅对支出有效：标记该笔支出是否已报销（如因公垫付后被公司打款）。
 * 其余类型（收入/退款/中性）恒为 false 且 UI 不展示该标记。
 */
data class Entry(
    val id: Long = 0L,
    val type: EntryType,
    val amount: Double,
    val amountRaw: String,
    val categoryName: String,
    val date: LocalDate,
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: List<Tag> = emptyList(),
    val sourceRef: String? = null,
    val counterparty: String? = null,
    val isReimbursed: Boolean = false,
)
