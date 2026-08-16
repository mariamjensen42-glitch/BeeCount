package com.cycling.beecount.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.repository.TodayTotals
import com.cycling.beecount.domain.usecase.ManageTagUseCase
import com.cycling.beecount.domain.usecase.ObserveAllEntriesUseCase
import com.cycling.beecount.domain.usecase.ObserveTagsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    observeAllEntries: ObserveAllEntriesUseCase,
    observeTags: ObserveTagsUseCase,
    private val manageTagUseCase: ManageTagUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LedgerUiState())
    val uiState: StateFlow<LedgerUiState> = _uiState.asStateFlow()

    /** 多标签交集筛选后的账目（时间倒序） */
    val filteredEntries: StateFlow<List<Entry>> = _uiState
        .map { state ->
            state.entries.filter { entry ->
                state.selectedTagIds.all { id -> entry.tags.any { it.id == id } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 筛选结果合计（无筛选时为全部账目合计） */
    val filteredTotals: StateFlow<TodayTotals> = filteredEntries
        .map { entries ->
            entries.fold(TodayTotals()) { acc, entry ->
                when (entry.type) {
                    com.cycling.beecount.domain.model.EntryType.EXPENSE ->
                        acc.copy(expense = acc.expense + entry.amount)

                    com.cycling.beecount.domain.model.EntryType.INCOME ->
                        acc.copy(income = acc.income + entry.amount)

                    com.cycling.beecount.domain.model.EntryType.NEUTRAL -> acc
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayTotals())

    init {
        viewModelScope.launch {
            observeAllEntries().collect { entries ->
                _uiState.update { it.copy(entries = entries) }
            }
        }
        viewModelScope.launch {
            observeTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
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

            LedgerEvent.OpenTagManage ->
                _uiState.update { it.copy(showTagManage = true) }

            LedgerEvent.CloseTagManage ->
                _uiState.update { it.copy(showTagManage = false) }

            is LedgerEvent.RenameTag -> launchManage { manageTagUseCase.rename(event.id, event.name) }
            is LedgerEvent.DeleteTag -> launchManage { manageTagUseCase.delete(event.id) }
            LedgerEvent.DismissError -> _uiState.update { it.copy(transientError = null) }

            is LedgerEvent.UpdateTagColor ->
                launchManage { manageTagUseCase.updateColor(event.id, event.color) }
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
