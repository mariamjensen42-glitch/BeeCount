package com.cycling.beecount.ui.calendar

import com.cycling.beecount.domain.repository.EntrySnapshot
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val showDaySheet: Boolean = false,
    val pendingUndo: EntrySnapshot? = null,
    val openedInitialDay: Boolean = false,
)

sealed interface CalendarEvent {
    data class ShiftMonth(val delta: Int) : CalendarEvent
    data object GoToCurrentMonth : CalendarEvent
    data class SelectDate(val date: LocalDate) : CalendarEvent
    data class SelectMonth(val month: YearMonth) : CalendarEvent
    data object CloseDaySheet : CalendarEvent
    data class DeleteEntry(val entryId: Long) : CalendarEvent
    data object UndoDelete : CalendarEvent
    data object ClearUndo : CalendarEvent
    data object OpenInitialToday : CalendarEvent
}
