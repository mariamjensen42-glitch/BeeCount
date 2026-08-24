package com.cycling.beecount.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.repository.TodayTotals
import com.cycling.beecount.domain.usecase.EntryIntake
import com.cycling.beecount.domain.usecase.ManageTagUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI 架构：账本页 ViewModel。
 * 筛选为多标签交集（ADR 0007）：[filteredEntries] 只包含同时带所有选中标签的账目，
 * [filteredTotals] 为筛选结果的支出/收入合计。
 */
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val entryQuery: EntryQuery,
    private val manageTagUseCase: ManageTagUseCase,
    private val entryIntake: EntryIntake,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    /** 多标签交集 + 综合筛选后的账目（时间倒序） */
    val filteredEntries: StateFlow<List<Entry>> = _uiState
        .map { state ->
            filterEntriesByTags(state.entries, state.selectedTagIds).filter { entry ->
                matchesFilters(entry, state.filters)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 筛选结果合计（无筛选时为全部账目合计；退款从支出中扣减） */
    val filteredTotals: StateFlow<TodayTotals> = filteredEntries
        .map { entries ->
            entries.fold(TodayTotals()) { acc, entry ->
                when (entry.type) {
                    com.cycling.beecount.domain.model.EntryType.EXPENSE ->
                        acc.copy(expense = acc.expense + entry.amount)

                    com.cycling.beecount.domain.model.EntryType.REFUND ->
                        acc.copy(expense = (acc.expense - entry.amount).coerceAtLeast(0.0))

                    com.cycling.beecount.domain.model.EntryType.INCOME ->
                        acc.copy(income = acc.income + entry.amount)

                    com.cycling.beecount.domain.model.EntryType.NEUTRAL -> acc
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayTotals())

    init {
        viewModelScope.launch {
            entryQuery.observeAllWithTags().collect { entries ->
                _uiState.update { it.copy(entries = entries) }
            }
        }
        viewModelScope.launch {
            entryQuery.observeTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
        viewModelScope.launch {
            entryQuery.observeCategories().collect { categories ->
                _uiState.update { it.copy(allCategories = categories) }
            }
        }
    }

    fun onEvent(event: LedgerEvent) {
        when (event) {
            is LedgerEvent.ToggleTag -> {
                val selected = _uiState.value.selectedTagIds
                _uiState.update {
                    it.copy(selectedTagIds = if (event.tagId in selected) selected - event.tagId else selected + event.tagId)
                }
            }

            LedgerEvent.ClearFilter ->
                _uiState.update { it.copy(selectedTagIds = emptySet()) }

            LedgerEvent.ToggleShowFilters ->
                _uiState.update { it.copy(showFilters = !it.showFilters) }

            LedgerEvent.ResetFilters ->
                _uiState.update { it.copy(filters = LedgerFilters()) }

            is LedgerEvent.SetKeyword ->
                _uiState.update { it.copy(filters = it.filters.copy(keyword = event.keyword)) }

            is LedgerEvent.SetDateRange ->
                _uiState.update { it.copy(filters = it.filters.copy(dateRange = event.dateRange)) }

            is LedgerEvent.SetCategory ->
                _uiState.update { it.copy(filters = it.filters.copy(categoryName = event.categoryName)) }

            is LedgerEvent.SetAmountRange ->
                _uiState.update {
                    it.copy(filters = it.filters.copy(minAmount = event.min, maxAmount = event.max))
                }

            is LedgerEvent.SetCounterparty ->
                _uiState.update { it.copy(filters = it.filters.copy(counterparty = event.counterparty)) }

            is LedgerEvent.SetEntryType ->
                _uiState.update { it.copy(filters = it.filters.copy(type = event.type)) }

            LedgerEvent.OpenTagManage ->
                _uiState.update { it.copy(showTagManage = true) }

            LedgerEvent.CloseTagManage ->
                _uiState.update { it.copy(showTagManage = false) }

            is LedgerEvent.RenameTag -> launchManage { manageTagUseCase.rename(event.id, event.name) }
            is LedgerEvent.DeleteTag -> launchManage { manageTagUseCase.delete(event.id) }
            LedgerEvent.DismissError -> _uiState.update { it.copy(transientError = null) }

            is LedgerEvent.UpdateTagColor ->
                launchManage { manageTagUseCase.updateColor(event.id, event.color) }

            is LedgerEvent.OpenEditEntry ->
                _uiState.update { it.copy(editingEntry = event.entry) }

            LedgerEvent.CloseEditEntry ->
                _uiState.update { it.copy(editingEntry = null) }

            is LedgerEvent.SaveEditEntry -> saveEdit(event)
        }
    }

    private fun saveEdit(event: LedgerEvent.SaveEditEntry) {
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

    private fun launchManage(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(transientError = "操作失败，请重试") }
            }
        }
    }
}

/** 按多标签交集筛选账目 */
internal fun filterEntriesByTags(entries: List<Entry>, selectedTagIds: Set<Long>): List<Entry> =
    entries.filter { entry ->
        selectedTagIds.all { id -> entry.tags.any { it.id == id } }
    }

/** 综合筛选：关键词（备注/对方/类别/金额原文）、日期范围、分类、金额区间、对方、收支类型，多条件「与」 */
internal fun matchesFilters(entry: Entry, filters: LedgerFilters): Boolean {
    if (filters.keyword.isNotBlank()) {
        val keyword = filters.keyword.trim()
        val haystack = listOf(entry.note, entry.counterparty.orEmpty(), entry.categoryName, entry.amountRaw)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (!haystack.contains(keyword, ignoreCase = true)) return false
    }
    filters.dateRange?.let { range ->
        val (start, end) = when (range) {
            LedgerDateRange.Today -> LocalDate.now() to LocalDate.now()
            LedgerDateRange.ThisWeek -> {
                val today = LocalDate.now()
                val startDs = today.minusDays((today.dayOfWeek.value - 1).toLong())
                startDs to today
            }
            LedgerDateRange.ThisMonth -> {
                val today = LocalDate.now()
                today.withDayOfMonth(1) to today
            }
            is LedgerDateRange.Custom -> range.start to range.end
        }
        if (entry.date.isBefore(start) || entry.date.isAfter(end)) return false
    }
    filters.categoryName?.let { cat ->
        if (entry.categoryName != cat) return false
    }
    filters.minAmount?.let { min -> if (entry.amount < min) return false }
    filters.maxAmount?.let { max -> if (entry.amount > max) return false }
    filters.counterparty?.let { cp ->
        if (entry.counterparty != cp) return false
    }
    filters.type?.let { type -> if (entry.type != type) return false }
    return true
}
