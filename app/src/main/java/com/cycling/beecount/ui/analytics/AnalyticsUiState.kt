package com.cycling.beecount.ui.analytics

import java.time.Year
import java.time.YearMonth

/** 图表页时间粒度（ADR 0009）：月度报表 / 年度报告 */
enum class AnalyticsGranularity { MONTH, YEAR }

enum class HeatmapMetric(val label: String) {
    EXPENSE("支出金额"),
    ENTRY_COUNT("记账笔数"),
    ACTIVE_DAY("记账天数"),
}

/**
 * MVI 架构：图表页 UI 状态。
 * [granularity] 决定展示月度报表还是年度报告，
 * [selectedMonth]/[selectedYear] 为当前查看的周期，默认定位本月/本年。
 */
/** AI 月度报告状态（P0）：随「生成月报」动作流转 */
sealed interface MonthlyReportState {
    data object Idle : MonthlyReportState
    data object Loading : MonthlyReportState
    data class Content(val text: String, val isLocal: Boolean = false) : MonthlyReportState
    data object KeyMissing : MonthlyReportState
    data class Error(val message: String) : MonthlyReportState
}

data class AnalyticsUiState(
    val granularity: AnalyticsGranularity = AnalyticsGranularity.MONTH,
    val monthlyReport: MonthlyReportState = MonthlyReportState.Idle,
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedYear: Int = Year.now().value,
    val heatmapMetric: HeatmapMetric = HeatmapMetric.EXPENSE,
    val selectedHeatmapDate: java.time.LocalDate? = null,
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

    data class SetMonth(val month: YearMonth) : AnalyticsEvent

    data class SelectHeatmapMetric(val metric: HeatmapMetric) : AnalyticsEvent

    data class SelectHeatmapDate(val date: java.time.LocalDate) : AnalyticsEvent

    /** 生成 AI 月度财务报告（P0） */
    data object GenerateMonthlyReport : AnalyticsEvent
}
