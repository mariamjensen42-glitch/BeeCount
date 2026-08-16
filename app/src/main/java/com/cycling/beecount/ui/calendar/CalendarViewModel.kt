package com.cycling.beecount.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.CalendarMonth
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.usecase.BuildCalendarMonthUseCase
import com.cycling.beecount.domain.usecase.DeleteEntryWithUndoUseCase
import com.cycling.beecount.domain.usecase.ObserveEntriesOnUseCase
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
    private val buildCalendarMonth: BuildCalendarMonthUseCase,
    private val observeEntriesOn: ObserveEntriesOnUseCase,
    private val deleteEntryWithUndo: DeleteEntryWithUndoUseCase,
    private val restoreEntry: RestoreEntryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    val calendarMonth: StateFlow<CalendarMonth?> = _uiState
        .map { it.selectedMonth }
        .distinctUntilChanged()
        .flatMapLatest(buildCalendarMonth::invoke)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dayEntries: StateFlow<List<Entry>> = _uiState
        .map { it.selectedDate }
        .distinctUntilChanged()
        .flatMapLatest { date -> if (date == null) flowOf(emptyList()) else observeEntriesOn(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onEvent(event: CalendarEvent) {
        when (event) {
            is CalendarEvent.ShiftMonth -> _uiState.update {
                it.copy(selectedMonth = it.selectedMonth.plusMonths(event.delta.toLong()), selectedDate = null, showDaySheet = false)
            }
            CalendarEvent.GoToCurrentMonth -> _uiState.update {
                it.copy(selectedMonth = YearMonth.now(), selectedDate = null, showDaySheet = false)
            }
            is CalendarEvent.SelectDate -> _uiState.update { it.copy(selectedDate = event.date, showDaySheet = true) }
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
        }
    }
}
