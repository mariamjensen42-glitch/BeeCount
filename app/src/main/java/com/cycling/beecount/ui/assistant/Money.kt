package com.cycling.beecount.ui.assistant

/**
 * 金额格式化工具：统一 "%.2f" 展示，避免各处重复
 */
internal fun formatMoney(amount: Double): String = "%.2f".format(amount)
