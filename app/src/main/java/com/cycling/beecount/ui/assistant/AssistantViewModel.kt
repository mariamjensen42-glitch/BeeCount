package com.cycling.beecount.ui.assistant

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.QuickTemplate
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.usecase.EntryIntake
import com.cycling.beecount.domain.usecase.OcrImageImportUseCase
import com.cycling.beecount.domain.usecase.SpeechToText
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
    private val entryIntake: EntryIntake,
    private val undoEntryUseCase: UndoEntryUseCase,
    private val ocrImageImportUseCase: OcrImageImportUseCase,
    private val speechRecognizer: SpeechToText,
    private val entryQuery: EntryQuery,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    /** 消息唯一 id 生成器（LazyColumn key 需稳定唯一） */
    private var nextMessageId = 0L

    private val _uiState = MutableStateFlow(AssistantUiState(today = today))
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private fun newMessageId(): Long = ++nextMessageId

    init {
        viewModelScope.launch {
            entryQuery.observeDay(today).collect { entries ->
                _uiState.update { it.copy(todayEntries = entries) }
            }
        }
        viewModelScope.launch {
            entryQuery.observeTotals(today).collect { totals ->
                _uiState.update { it.copy(todayTotals = totals) }
            }
        }
        viewModelScope.launch {
            entryQuery.observeCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            entryQuery.observeTags().collect { tags ->
                _uiState.update { it.copy(allTags = tags) }
            }
        }
        viewModelScope.launch {
            entryQuery.observeBudgetProgress(today).collect { progress ->
                _uiState.update { it.copy(budgetProgress = progress) }
            }
        }
        viewModelScope.launch {
            entryQuery.observeQuickTemplates().collect { templates ->
                _uiState.update { it.copy(quickTemplates = templates) }
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

            is AssistantEvent.SetTargetDate ->
                _uiState.update { s -> s.copy(targetDate = event.date) }

            is AssistantEvent.EditDate ->
                _uiState.update { s -> s.copy(pendingResult = s.pendingResult?.copy(date = event.date)) }

            AssistantEvent.ConsumeSavedEntryDate ->
                _uiState.update { s -> s.copy(savedEntryDate = null) }

            AssistantEvent.Confirm -> confirm()
            AssistantEvent.DismissCard -> dismissCard()
            is AssistantEvent.Undo -> undo(event.entryId)
            AssistantEvent.DismissError -> _uiState.update { it.copy(transientError = null) }
            is AssistantEvent.OcrImageSelected -> processOcrImage(event.uri)
            AssistantEvent.ShowCamera -> _uiState.update { it.copy(showCameraSheet = true) }
            AssistantEvent.DismissCamera -> _uiState.update { it.copy(showCameraSheet = false) }
            is AssistantEvent.ApplyTemplate -> applyTemplate(event.template)
            AssistantEvent.StartVoice -> startVoice()
            AssistantEvent.VoicePermissionDenied -> addAssistantMessage("需要录音权限才能语音记账，请授予后重试")
        }
    }

    /** 离线语音：识别文本后走既有解析路径。未授权时提示授予录音权限。 */
    private fun startVoice() {
        if (_uiState.value.isParsing) return
        if (!speechRecognizer.hasRecordAudioPermission()) {
            addAssistantMessage("需要录音权限才能语音记账，请授予后重试")
            return
        }
        if (!speechRecognizer.isAvailable()) {
            addAssistantMessage("当前设备不支持语音识别")
            return
        }
        _uiState.update { it.copy(isListening = true) }
        viewModelScope.launch {
            val text = try {
                speechRecognizer.recognize("zh-CN")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                addAssistantMessage("语音识别失败，请重试")
                null
            } finally {
                _uiState.update { it.copy(isListening = false) }
            }
            val heard = text?.trim().orEmpty()
            if (heard.isNotEmpty()) submitInput(heard) else addAssistantMessage("没听清，请再说一次")
        }
    }

    /** 快捷模板：直接以模板构建解析结果并弹出确认卡，免去 AI 解析与重复输入。 */
    private fun applyTemplate(template: QuickTemplate) {
        if (_uiState.value.isParsing) return
        val result = AiParseResult(
            recordable = true,
            type = template.type,
            amount = template.amount,
            amountRaw = template.amountRaw.ifBlank { formatMoney(template.amount) },
            categoryName = template.categoryName,
            date = _uiState.value.targetDate ?: today,
            tags = template.tags,
            note = template.note.ifBlank { template.title },
            isRefund = template.type == EntryType.REFUND,
            isReimbursed = false,
        )
        _uiState.update { s ->
            s.copy(
                pendingResult = result,
                pendingOriginalText = template.note.ifBlank { template.title },
            )
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
            handleParseOutcome(entryIntake.parse(trimmed), originalText = trimmed)
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
                val entry = entryIntake.confirm(
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
                        targetDate = null,
                        savedEntryDate = entry.date,
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

    private fun dismissCard() {
        _uiState.update { it.copy(pendingResult = null, pendingOriginalText = "") }
    }

    private fun processOcrImage(uri: Uri) {
        if (_uiState.value.isParsing) return
        _uiState.update { it.copy(isParsing = true) }
        viewModelScope.launch {
            try {
                val importedSource = when (val importOutcome = ocrImageImportUseCase(uri)) {
                    is OcrImageImportUseCase.Outcome.Imported -> importOutcome.source
                    is OcrImageImportUseCase.Outcome.ReadError -> {
                        logOcrError("无法复制用户选中的图片", uri, importOutcome.cause)
                        addAssistantMessage("无法访问所选图片，请重新选择一张本地图片")
                        return@launch
                    }
                }

                when (val outcome = entryIntake.parseOcr(importedSource)) {
                    is EntryIntake.OcrOutcome.RecognitionFailed -> {
                        addAssistantMessage("未能识别图片文字，请换张截图或手动输入")
                    }

                    is EntryIntake.OcrOutcome.ImageReadError -> {
                        logOcrError("无法读取 OCR 缓存图片", uri, outcome.cause)
                        addAssistantMessage("无法读取所选图片，请重新选择后重试")
                    }

                    is EntryIntake.OcrOutcome.RecognitionError -> {
                        logOcrError("图片文字识别失败", uri, outcome.cause)
                        addAssistantMessage("图片文字识别失败，请换张清晰截图后重试")
                    }

                    is EntryIntake.OcrOutcome.Parsed ->
                        handleParseOutcome(
                            outcome.parseOutcome,
                            // 优先用 AI 整理的简短备注，否则回退到识别原文
                            originalText = (outcome.parseOutcome as? EntryIntake.Outcome.Success)
                                ?.result?.note?.takeIf { it.isNotEmpty() }
                                ?: outcome.rawText,
                        )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                logOcrError("OCR 处理出现未预期错误", uri, e)
                addAssistantMessage("图片处理失败，请重新选择后重试")
            } finally {
                _uiState.update { it.copy(isParsing = false) }
            }
        }
    }

    private fun addAssistantMessage(text: String) {
        _uiState.update { state ->
            state.copy(messages = state.messages + AssistantMessage.Assistant(newMessageId(), text))
        }
    }

    private fun logOcrError(operation: String, uri: Uri, cause: Exception) {
        Log.w(
            OCR_LOG_TAG,
            "$operation: scheme=${uri.scheme}, authority=${uri.authority}, " +
                "error=${cause::class.java.simpleName}: ${cause.message}",
            cause,
        )
    }

    private companion object {
        const val OCR_LOG_TAG = "BeeCountOcr"
    }

    /** 处理 [EntryIntake.Outcome]，供文字输入和 OCR 两条路径共用。*/
    private fun handleParseOutcome(
        outcome: EntryIntake.Outcome,
        originalText: String,
        nonRecordableDefault: String = "我没听懂这笔账，换种说法试试？",
    ) {
        when (outcome) {
            is EntryIntake.Outcome.Success -> {
                val result = outcome.result
                _uiState.update { s ->
                    if (result.recordable) {
                        val targetDate = s.targetDate
                        s.copy(
                            pendingResult = if (targetDate != null) result.copy(date = targetDate) else result,
                            pendingOriginalText = originalText,
                        )
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

            EntryIntake.Outcome.KeyMissing -> {
                _uiState.update { s ->
                    s.copy(
                        messages = s.messages + AssistantMessage.Assistant(
                            newMessageId(),
                            "请先到底部「设置」里填写你的 DeepSeek API Key"
                        )
                    )
                }
            }

            is EntryIntake.Outcome.Error -> {
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
