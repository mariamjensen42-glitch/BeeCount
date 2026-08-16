package com.cycling.beecount.widget

/**
 * 收支速览小组件的四个数字（ADR 0013）：今日/本月 × 支出/收入。
 * 口径与 App 内完全一致——今日 = date==今天（含补记），本月 = 当月自然月，
 * 中性记录（退款）不计（SQL 层已排除）。
 */
data class OverviewStats(
    val todayExpense: Double,
    val todayIncome: Double,
    val monthExpense: Double,
    val monthIncome: Double,
)
