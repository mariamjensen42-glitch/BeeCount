package com.cycling.beecount.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.usecase.ConfirmEntryUseCase
import com.cycling.beecount.domain.usecase.ObserveCategoriesUseCase
import com.cycling.beecount.domain.usecase.ObserveEntriesOnUseCase
import com.cycling.beecount.domain.usecase.ObserveTotalsOnUseCase
import com.cycling.beecount.domain.usecase.ParseEntryUseCase
import com.cycling.beecount.domain.usecase.UndoEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI 架构：接收 UI 事件 → 调用用例 → 更新 UI 状态。
 *
 * 状态以单一 [MutableStateFlow] 维护：今日账目/合计/类别/Key 由 repository 观察流
 * 合并写入；解析、确认、撤销等用户操作直接 update。
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val parseEntryUseCase: ParseEntryUseCase,
    private val confirmEntryUseCase: ConfirmEntryUseCase,
    private val undoEntryUseCase: UndoEntryUseCase,
    observeEntriesOn: ObserveEntriesOnUseCase,
    observeTotalsOn: ObserveTotalsOnUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val aiKeyRepository: AiKeyRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    /** 消息唯一 id 生成器（LazyColumn key 需稳定唯一） */
    private var nextMessageId = 0L

    private val _uiState = MutableStateFlow(AssistantUiState(today = today))
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private fun newMessageId(): Long = ++nextMessageId

    init {
        viewModelScope.launch {
            observeEntriesOn(today).collect { entries ->
                _uiState.update { it.copy(todayEntries = entries) }
            }
        }
        viewModelScope.launch {
            observeTotalsOn(today).collect { totals ->
                _uiState.update { it.copy(todayTotals = totals) }
            }
        }
        viewModelScope.launch {
            observeCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            aiKeyRepository.observeKey().collect { key ->
                _uiState.update { it.copy(hasApiKey = !key.isNullOrBlank()) }
            }
        }
    }

    fun onEvent(event: AssistantEvent) {
        when (event) {
            is AssistantEvent.SubmitInput -> submitInput(event.text)
            is AssistantEvent.EditAmount ->
                _uiState.update { s -> s.copy(pendingResult = s.pendingResult?.copy(amount = event.amount)) }

            is AssistantEvent.EditCategory ->
                _uiState.update { s -> s.copy(pendingResult = s.pendingResult?.copy(categoryName = event.name)) }

            AssistantEvent.Confirm -> confirm()
            AssistantEvent.DismissCard ->
                _uiState.update { it.copy(pendingResult = null, pendingOriginalText = "") }

            is AssistantEvent.Undo -> undo(event.entryId)
            is AssistantEvent.SaveApiKey -> saveApiKey(event.key)
            AssistantEvent.CloseKeySetup -> _uiState.update { it.copy(showKeySetup = false) }
            AssistantEvent.OpenKeySetup -> _uiState.update { it.copy(showKeySetup = true) }
            AssistantEvent.DismissError -> _uiState.update { it.copy(transientError = null) }
        }
    }

    private fun submitInput(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isParsing) return
        _uiState.update { s ->
            s.copy(
                messages = s.messages + AssistantMessage.User(newMessageId(), trimmed),
                isParsing = true,
            )
        }
        viewModelScope.launch {
            when (val outcome = parseEntryUseCase(trimmed)) {
                is ParseEntryUseCase.Outcome.Success -> {
                    val result = outcome.result
                    _uiState.update { s ->
                        if (result.recordable) {
                            s.copy(pendingResult = result, pendingOriginalText = trimmed)
                        } else {
                            s.copy(
                                messages = s.messages + AssistantMessage.Assistant(
                                    newMessageId(),
                                    result.message ?: "我没听懂这笔账，换种说法试试？"
                                )
                            )
                        }
                    }
                }

                ParseEntryUseCase.Outcome.KeyMissing -> {
                    _uiState.update { s ->
                        s.copy(
                            messages = s.messages + AssistantMessage.Assistant(
                                newMessageId(),
                                "请先在右上角设置里填写你的 DeepSeek API Key"
                            ),
                            showKeySetup = true,
                        )
                    }
                }

                is ParseEntryUseCase.Outcome.Error -> {
                    _uiState.update { s ->
                        s.copy(
                            messages = s.messages + AssistantMessage.Assistant(newMessageId(), outcome.message)
                        )
                    }
                }
            }
            _uiState.update { it.copy(isParsing = false) }
        }
    }

    private fun confirm() {
        val result = _uiState.value.pendingResult ?: return
        val text = _uiState.value.pendingOriginalText
        val amount = result.amount ?: return
        val categoryName = result.categoryName.orEmpty()
        if (amount <= 0 || categoryName.isBlank()) {
            _uiState.update { it.copy(transientError = "金额和类别不能为空") }
            return
        }
        viewModelScope.launch {
            try {
                val entry = confirmEntryUseCase(
                    result = result,
                    editedAmount = amount,
                    editedCategoryName = categoryName,
                    originalText = text,
                )
                _uiState.update { s ->
                    s.copy(
                        messages = s.messages + AssistantMessage.Saved(newMessageId(), entry),
                        pendingResult = null,
                        pendingOriginalText = "",
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(transientError = "保存失败，请重试") }
            }
        }
    }

    private fun undo(entryId: Long) {
        viewModelScope.launch {
            try {
                undoEntryUseCase(entryId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(transientError = "撤销失败，请重试") }
            }
        }
    }

    private fun saveApiKey(key: String) {
        if (key.isBlank()) {
            _uiState.update { it.copy(transientError = "Key 不能为空") }
            return
        }
        viewModelScope.launch {
            try {
                aiKeyRepository.saveKey(key)
                _uiState.update { s ->
                    s.copy(
                        showKeySetup = false,
                        messages = s.messages + AssistantMessage.Assistant(newMessageId(), "API Key 已保存，可以开始记账了"),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(transientError = "保存失败，请重试") }
            }
        }
    }
}
