package com.cycling.beecount.ui.calendar

import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.repository.EntrySnapshot
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val showDaySheet: Boolean = false,
    val pendingUndo: EntrySnapshot? = null,
    val editingEntry: Entry? = null,
    val allCategories: List<Category> = emptyList(),
    val allTags: List<com.cycling.beecount.domain.model.Tag> = emptyList(),
    val openedInitialDay: Boolean = false,
    val transientError: String? = null,
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
    data class OpenEditEntry(val entry: Entry) : CalendarEvent
    data object CloseEditEntry : CalendarEvent
    data class SaveEditEntry(
        val entryId: Long,
        val editedType: com.cycling.beecount.domain.model.EntryType,
        val editedAmount: Double,
        val editedCategoryName: String,
        val editedDate: java.time.LocalDate,
        val editedNote: String,
        val tagNames: List<String>,
        val editedCounterparty: String? = null,
        val editedIsReimbursed: Boolean = false,
    ) : CalendarEvent
}
