package com.cycling.beecount.domain.model

/**
 * 预算执行预测：基于当前周期已花进度，线性外推到周期末的预估支出，
 * 判断是否将超支。所有数字来自 [BudgetProgress] 真实统计，纯本地计算、零云依赖。
 */
data class BudgetForecast(
    val progress: BudgetProgress,
    /** 预计到周期末的总支出（按当前日均线性外推） */
    val projectedSpent: Double,
    /** 预计超支金额（>0 即超支） */
    val projectedOver: Double,
    /** 是否预测超支 */
    val willOver: Boolean,
    /** 本地中文叙述，直接展示 */
    val message: String,
)
