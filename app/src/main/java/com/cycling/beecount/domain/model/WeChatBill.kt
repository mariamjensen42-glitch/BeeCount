package com.cycling.beecount.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 微信账单中的一行原始流水（ADR 0012），字段对应官方 xlsx 的 11 列。
 * [amountRaw] 保留金额单元格原文（"14.23"/"40"），[amount] 是其数值。
 * [sourceRef] 是交易单号，全库唯一，是去重与"撤销本次导入"的依据。
 */
data class WeChatBillRow(
    val time: LocalDateTime,
    val type: String,
    val counterparty: String,
    val goods: String,
    val incomeExpense: String,
    val amountRaw: String,
    val amount: Double,
    val status: String,
    val sourceRef: String,
)

/** 一次微信账单导入解析出的全部原始流水（ADR 0012）。 */
data class WeChatBill(
    val rows: List<WeChatBillRow>,
)

/**
 * 微信导入中一条可入库的账目草稿（ADR 0012）：类型、类别、备注等已按规则定好，
 * 用户确认导入后一次性入库。金额/备注/日期/来源引用直接来自账单行。
 */
data class WeChatImportDraftEntry(
    val type: EntryType,
    val amount: Double,
    val amountRaw: String,
    val categoryName: String,
    val date: LocalDate,
    val note: String,
    val sourceRef: String,
)

/**
 * 微信账单解析并分类后的导入草稿（ADR 0012）。
 * [entries] 是全部可入库账目（支出/收入/中性退款）；[skippedCount] 是跳过的中性交易
 * （充值/提现/零钱通存取/信用卡还款）与无法识别的行数，仅用于导入确认时报告。
 */
data class WeChatImportDraft(
    val entries: List<WeChatImportDraftEntry>,
    val skippedCount: Int,
)

fun WeChatImportDraftEntry.toEntry(): Entry = Entry(
    type = type,
    amount = amount,
    amountRaw = amountRaw,
    categoryName = categoryName,
    date = date,
    note = note,
    sourceRef = sourceRef,
)
