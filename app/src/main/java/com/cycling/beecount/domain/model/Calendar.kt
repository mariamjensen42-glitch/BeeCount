package com.cycling.beecount.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** 月历的一天摘要；仅包含当前月日期。 */
data class CalendarDaySummary(
    val date: LocalDate,
    val expense: Double,
    val income: Double,
    val entryCount: Int,
)

/** 月历按月展示的数据快照。 */
data class CalendarMonth(
    val month: YearMonth,
    val expense: Double,
    val income: Double,
    val days: List<CalendarDaySummary>,
)
