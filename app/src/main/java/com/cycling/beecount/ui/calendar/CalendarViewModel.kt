package com.cycling.beecount.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.CalendarMonth
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.usecase.DeleteEntryWithUndoUseCase
import com.cycling.beecount.domain.usecase.EntryIntake
import com.cycling.beecount.domain.usecase.RestoreEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val entryQuery: EntryQuery,
    private val deleteEntryWithUndo: DeleteEntryWithUndoUseCase,
    private val restoreEntry: RestoreEntryUseCase,
    private val entryIntake: EntryIntake,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    val calendarMonth: StateFlow<CalendarMonth?> = _uiState
        .map { it.selectedMonth }
        .distinctUntilChanged()
        .flatMapLatest(entryQuery::buildCalendar)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dayEntries: StateFlow<List<Entry>> = _uiState
        .map { it.selectedDate }
        .distinctUntilChanged()
        .flatMapLatest { date -> if (date == null) flowOf(emptyList()) else entryQuery.observeDay(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            entryQuery.observeCategories().collect { categories ->
                _uiState.update { it.copy(allCategories = categories) }
            }
        }
        viewModelScope.launch {
            entryQuery.observeTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
    }

    fun onEvent(event: CalendarEvent) {
        when (event) {
            is CalendarEvent.ShiftMonth -> _uiState.update {
                it.copy(selectedMonth = it.selectedMonth.plusMonths(event.delta.toLong()), selectedDate = null, showDaySheet = false)
            }
            CalendarEvent.GoToCurrentMonth -> _uiState.update {
                it.copy(selectedMonth = YearMonth.now(), selectedDate = null, showDaySheet = false)
            }
            is CalendarEvent.SelectDate -> _uiState.update { it.copy(selectedDate = event.date, showDaySheet = true) }
            is CalendarEvent.SelectMonth -> _uiState.update {
                it.copy(selectedMonth = event.month, selectedDate = null, showDaySheet = false)
            }
            CalendarEvent.CloseDaySheet -> _uiState.update { it.copy(showDaySheet = false) }
            is CalendarEvent.DeleteEntry -> if (_uiState.value.pendingUndo == null) viewModelScope.launch {
                deleteEntryWithUndo(event.entryId)?.let { snapshot ->
                    _uiState.update { it.copy(pendingUndo = snapshot) }
                    kotlinx.coroutines.delay(5_000)
                    _uiState.update { state -> if (state.pendingUndo == snapshot) state.copy(pendingUndo = null) else state }
                }
            }
            CalendarEvent.UndoDelete -> viewModelScope.launch {
                _uiState.value.pendingUndo?.let { snapshot ->
                    restoreEntry(snapshot)
                    _uiState.update { it.copy(pendingUndo = null) }
                }
            }
            CalendarEvent.ClearUndo -> _uiState.update { it.copy(pendingUndo = null) }
            CalendarEvent.OpenInitialToday -> if (!_uiState.value.openedInitialDay) {
                val today = LocalDate.now()
                _uiState.update {
                    it.copy(selectedMonth = YearMonth.from(today), selectedDate = today, showDaySheet = false, openedInitialDay = true)
                }
            }
            is CalendarEvent.OpenEditEntry ->
                _uiState.update { it.copy(editingEntry = event.entry) }
            CalendarEvent.CloseEditEntry ->
                _uiState.update { it.copy(editingEntry = null) }
            is CalendarEvent.SaveEditEntry -> saveEdit(event)
        }
    }

    private fun saveEdit(event: CalendarEvent.SaveEditEntry) {
        val entry = _uiState.value.editingEntry ?: return
        viewModelScope.launch {
            try {
                entryIntake.update(
                    entry = entry,
                    editedType = event.editedType,
                    editedAmount = event.editedAmount,
                    editedCategoryName = event.editedCategoryName,
                    editedDate = event.editedDate,
                    editedNote = event.editedNote,
                    tagNames = event.tagNames,
                    editedCounterparty = event.editedCounterparty,
                    editedIsReimbursed = event.editedIsReimbursed,
                )
                _uiState.update { it.copy(editingEntry = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(transientError = "保存失败，请重试") }
            }
        }
    }
}
