package com.cycling.beecount.domain.model

import java.time.LocalDate

/**
 * 账目：一条已入库的收支记录。
 *
 * [amountRaw] 保留用户口语金额原文（如"1万"），[amount] 是其归一化后的元数值，
 * 确认卡片同时展示两者以便用户核对换算。
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
)
