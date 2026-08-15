package com.cycling.beecount.ui.analytics

import java.time.Year
import java.time.YearMonth

/** 图表页时间粒度（ADR 0009）：月度报表 / 年度报告 */
enum class AnalyticsGranularity { MONTH, YEAR }

/**
 * MVI 架构：图表页 UI 状态。
 * [granularity] 决定展示月度报表还是年度报告，
 * [selectedMonth]/[selectedYear] 为当前查看的周期，默认定位本月/本年。
 */
data class AnalyticsUiState(
    val granularity: AnalyticsGranularity = AnalyticsGranularity.MONTH,
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedYear: Int = Year.now().value,
)

/** MVI 架构：图表页事件 */
sealed interface AnalyticsEvent {
    /** 切换「月度 / 年度」粒度 */
    data object ToggleGranularity : AnalyticsEvent

    /** 前后翻月（delta 为 ±1） */
    data class ShiftMonth(val delta: Int) : AnalyticsEvent

    data object GoToCurrentMonth : AnalyticsEvent

    /** 前后翻年（delta 为 ±1） */
    data class ShiftYear(val delta: Int) : AnalyticsEvent

    data object GoToCurrentYear : AnalyticsEvent
}
