package com.cycling.beecount.domain.model

/**
 * 账目类型
 *
 * [REFUND] 是退款/退货记录（红字冲销）：真实入库、出现在账本页，统计时从同类别支出中扣减，
 * 而不是作为收入入账。退款可从手动/AI/OCR 记账与微信账单导入产生。
 * [NEUTRAL] 是中性记录（ADR 0012）：真实入库、出现在账本页，但不计入任何统计。
 * 目前仅微信账单导入的特定退款场景可能保留中性记录；手动/AI/OCR 记账创建退款会用 [REFUND]。
 */
enum class EntryType {
    EXPENSE,
    INCOME,
    REFUND,
    NEUTRAL,
}
