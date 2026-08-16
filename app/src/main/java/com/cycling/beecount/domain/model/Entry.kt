package com.cycling.beecount.domain.model

import java.time.LocalDate

/**
 * 账目：一条已入库的收支记录。
 *
 * [amountRaw] 保留用户口语金额原文（如"1万"），[amount] 是其归一化后的元数值，
 * 确认卡片同时展示两者以便用户核对换算。
 *
 * [sourceRef] 是来源引用（ADR 0012）：微信导入的账目记录其交易单号，可空、全库唯一，
 * 用于去重与"撤销本次导入"按集合定位；手动/AI/OCR 记账为 null。
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
)
