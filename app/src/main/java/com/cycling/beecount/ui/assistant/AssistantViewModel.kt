package com.cycling.beecount.ui.assistant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.usecase.ConfirmEntryUseCase
import com.cycling.beecount.domain.usecase.ObserveCategoriesUseCase
import com.cycling.beecount.domain.usecase.ObserveEntriesOnUseCase
import com.cycling.beecount.domain.usecase.ObserveTagsUseCase
import com.cycling.beecount.domain.usecase.ObserveTotalsOnUseCase
import com.cycling.beecount.domain.usecase.OcrEntryUseCase
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
    private val ocrEntryUseCase: OcrEntryUseCase,
    observeEntriesOn: ObserveEntriesOnUseCase,
    observeTotalsOn: ObserveTotalsOnUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeTags: ObserveTagsUseCase,
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
            observeTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
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

            is AssistantEvent.EditTags ->
                _uiState.update { s -> s.copy(pendingResult = s.pendingResult?.copy(tags = event.tags)) }

            AssistantEvent.Confirm -> confirm()
            AssistantEvent.DismissCard ->
                _uiState.update { it.copy(pendingResult = null, pendingOriginalText = "") }

            is AssistantEvent.Undo -> undo(event.entryId)
            AssistantEvent.DismissError -> _uiState.update { it.copy(transientError = null) }
            is AssistantEvent.OcrImageSelected -> processOcrImage(event.uri)
            AssistantEvent.ShowCamera -> _uiState.update { it.copy(showCameraSheet = true) }
            AssistantEvent.DismissCamera -> _uiState.update { it.copy(showCameraSheet = false) }
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
            handleParseOutcome(parseEntryUseCase(trimmed), originalText = trimmed)
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
                    tags = result.tags,
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

    private fun processOcrImage(uri: Uri) {
        if (_uiState.value.isParsing) return
        _uiState.update { it.copy(isParsing = true) }
        viewModelScope.launch {
            when (val outcome = ocrEntryUseCase(uri)) {
                is OcrEntryUseCase.Outcome.RecognitionFailed -> {
                    _uiState.update { s ->
                        s.copy(
                            messages = s.messages + AssistantMessage.Assistant(
                                newMessageId(),
                                "未能识别图片文字，请换张截图或手动输入"
                            )
                        )
                    }
                }

                is OcrEntryUseCase.Outcome.ImageError -> {
                    _uiState.update { s ->
                        s.copy(
                            messages = s.messages + AssistantMessage.Assistant(
                                newMessageId(),
                                "无法读取图片，请重试"
                            )
                        )
                    }
                }

                is OcrEntryUseCase.Outcome.Parsed ->
                    handleParseOutcome(
                        outcome.parseOutcome,
                        // 优先用 AI 整理的简短备注，否则回退到识别原文
                        originalText = (outcome.parseOutcome as? ParseEntryUseCase.Outcome.Success)
                            ?.result?.note?.takeIf { it.isNotEmpty() }
                            ?: outcome.rawText,
                    )
            }
            _uiState.update { it.copy(isParsing = false) }
        }
    }

    /** 处理 [ParseEntryUseCase.Outcome]，供文字输入和 OCR 两条路径共用。*/
    private fun handleParseOutcome(
        outcome: ParseEntryUseCase.Outcome,
        originalText: String,
        nonRecordableDefault: String = "我没听懂这笔账，换种说法试试？",
    ) {
        when (outcome) {
            is ParseEntryUseCase.Outcome.Success -> {
                val result = outcome.result
                _uiState.update { s ->
                    if (result.recordable) {
                        s.copy(pendingResult = result, pendingOriginalText = originalText)
                    } else {
                        s.copy(
                            messages = s.messages + AssistantMessage.Assistant(
                                newMessageId(),
                                result.message ?: nonRecordableDefault
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
                            "请先到底部「设置」里填写你的 DeepSeek API Key"
                        )
                    )
                }
            }

            is ParseEntryUseCase.Outcome.Error -> {
                _uiState.update { s ->
                    s.copy(
                        messages = s.messages + AssistantMessage.Assistant(
                            newMessageId(), outcome.message
                        )
                    )
                }
            }
        }
    }
}
