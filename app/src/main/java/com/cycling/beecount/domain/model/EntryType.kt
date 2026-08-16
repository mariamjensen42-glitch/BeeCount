package com.cycling.beecount.domain.model

/**
 * 账目类型
 *
 * [NEUTRAL] 是第三类中性记录（ADR 0012）：真实入库、出现在账本页，但不计入任何统计。
 * 目前仅微信账单导入的退款交易会产生中性记录，手动/AI/OCR 记账无法创建。
 */
enum class EntryType {
    EXPENSE,
    INCOME,
    NEUTRAL,
}
