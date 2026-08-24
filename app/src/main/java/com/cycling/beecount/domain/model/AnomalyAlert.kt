package com.cycling.beecount.domain.model

import java.time.LocalDate

/** 异常类型：单笔金额过大 / 单日累计过高 */
enum class AnomalyKind { SINGLE_LARGE, DAY_TOTAL_HIGH }

/**
 * 异常消费预警：本地统计发现某笔支出或某日累计显著偏离历史基线时产出。
 * 所有数字来自数据库真实计算，绝不编造。
 */
data class AnomalyAlert(
    val kind: AnomalyKind,
    /** 一级类别（如「餐饮·外卖」归并为「餐饮」） */
    val category: String,
    /** 触发金额：单笔金额或当日累计 */
    val amount: Double,
    /** 对比基线：单笔历史均值或历史日均 */
    val baseline: Double,
    /** 历史标准差 */
    val stdDev: Double,
    val date: LocalDate,
    /** 本地中文描述，直接用于通知文案 */
    val message: String,
)
