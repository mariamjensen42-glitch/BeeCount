package com.cycling.beecount.ui.assistant

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.PendingDraft
import com.cycling.beecount.domain.model.toAiParseResult
import com.cycling.beecount.domain.usecase.ConfirmEntryUseCase
import com.cycling.beecount.domain.usecase.ObserveCategoriesUseCase
import com.cycling.beecount.domain.usecase.ObserveEntriesOnUseCase
import com.cycling.beecount.domain.usecase.ObservePendingDraftsUseCase
import com.cycling.beecount.domain.usecase.ObserveTagsUseCase
import com.cycling.beecount.domain.usecase.ObserveTotalsOnUseCase
import com.cycling.beecount.domain.usecase.OcrEntryUseCase
import com.cycling.beecount.domain.usecase.OcrImageImportUseCase
import com.cycling.beecount.domain.usecase.ParseEntryUseCase
import com.cycling.beecount.domain.usecase.RemovePendingDraftUseCase
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
    private val ocrImageImportUseCase: OcrImageImportUseCase,
    private val ocrEntryUseCase: OcrEntryUseCase,
    private val observePendingDrafts: ObservePendingDraftsUseCase,
    private val removePendingDraft: RemovePendingDraftUseCase,
    observeEntriesOn: ObserveEntriesOnUseCase,
    observeTotalsOn: ObserveTotalsOnUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeTags: ObserveTagsUseCase,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    /** 消息唯一 id 生成器（LazyColumn key 需稳定唯一） */
    private var nextMessageId = 0L

    /** 当前确认卡片对应的待确认草稿 id（ADR 0014）：非空时卡片来自自动记账队列 */
    private var displayedDraftId: Long? = null

    /** 深链定位的草稿 id：点确认通知后优先展示这张卡片（ADR 0014，只生效一次） */
    private var preferredDraftId: Long? = null

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
        viewModelScope.launch {
            observePendingDrafts().collect { drafts -> onPendingDraftsChanged(drafts) }
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
            is AssistantEvent.PreferDraft -> preferredDraftId = event.draftId
        }
    }

    private fun submitInput(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isParsing) return
        releaseDisplayedDraft()
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
                        targetDate = null,
                        savedEntryDate = entry.date,
                    )
                }
                removeDisplayedDraft()
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

    /**
     * 待确认草稿队列变化（ADR 0014）：队首草稿在确认卡片槽空闲时展示。
     * 当前草稿被移除（确认/拒绝）后自动展示下一张；手动输入/OCR 解析会先让出槽位（草稿回队列等待）。
     */
    private fun onPendingDraftsChanged(drafts: List<PendingDraft>) {
        val displayed = displayedDraftId
        if (drafts.isEmpty()) {
            if (displayed != null) {
                displayedDraftId = null
                _uiState.update { it.copy(pendingResult = null, pendingOriginalText = "") }
            }
            return
        }
        if (displayed != null) {
            // 当前草稿仍在队列 → 保持（可能是手动卡片覆盖了展示，不动）；已被移除 → 展示队首
            if (drafts.none { it.id == displayed }) {
                displayedDraftId = null
                if (_uiState.value.pendingResult == null) showDraft(drafts.first())
            }
            return
        }
        if (_uiState.value.pendingResult == null) {
            val target = preferredDraftId
                ?.let { pid -> drafts.firstOrNull { it.id == pid } }
                ?: drafts.first()
            preferredDraftId = null
            showDraft(target)
        }
    }

    private fun showDraft(draft: PendingDraft) {
        displayedDraftId = draft.id
        _uiState.update { s ->
            s.copy(
                pendingResult = draft.toAiParseResult(),
                // 卡片备注优先 AI 提炼的简短描述，否则回退支付通知原文
                pendingOriginalText = draft.note?.takeIf { it.isNotBlank() } ?: draft.originalText,
            )
        }
    }

    /** 取消当前卡片：若来自草稿队列则一并移除（用户拒绝这笔草稿） */
    private fun dismissCard() {
        removeDisplayedDraft()
        _uiState.update { it.copy(pendingResult = null, pendingOriginalText = "") }
    }

    /** 手动/OCR 解析接管卡片槽：正在展示的草稿让位回队列，不删除 */
    private fun releaseDisplayedDraft() {
        displayedDraftId = null
    }

    private fun removeDisplayedDraft() {
        val id = displayedDraftId ?: return
        displayedDraftId = null
        viewModelScope.launch { removePendingDraft(id) }
    }

    private fun processOcrImage(uri: Uri) {
        if (_uiState.value.isParsing) return
        releaseDisplayedDraft()
        _uiState.update { it.copy(isParsing = true) }
        viewModelScope.launch {
            try {
                val importedUri = when (val importOutcome = ocrImageImportUseCase(uri)) {
                    is OcrImageImportUseCase.Outcome.Imported -> importOutcome.uri
                    is OcrImageImportUseCase.Outcome.ReadError -> {
                        logOcrError("无法复制用户选中的图片", uri, importOutcome.cause)
                        addAssistantMessage("无法访问所选图片，请重新选择一张本地图片")
                        return@launch
                    }
                }

                when (val outcome = ocrEntryUseCase(importedUri)) {
                    is OcrEntryUseCase.Outcome.RecognitionFailed -> {
                        addAssistantMessage("未能识别图片文字，请换张截图或手动输入")
                    }

                    is OcrEntryUseCase.Outcome.ImageReadError -> {
                        logOcrError("无法读取 OCR 缓存图片", importedUri, outcome.cause)
                        addAssistantMessage("无法读取所选图片，请重新选择后重试")
                    }

                    is OcrEntryUseCase.Outcome.RecognitionError -> {
                        logOcrError("图片文字识别失败", importedUri, outcome.cause)
                        addAssistantMessage("图片文字识别失败，请换张清晰截图后重试")
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

    /** 处理 [ParseEntryUseCase.Outcome]，供文字输入和 OCR 两条路径共用。*/
    private fun handleParseOutcome(
        outcome: ParseEntryUseCase.Outcome,
        originalText: String,
        nonRecordableDefault: String = "我没听懂这笔账，换种说法试试？",
    ) {
        when (outcome) {
            is ParseEntryUseCase.Outcome.Success -> {
                val result = outcome.result
                // 手动/OCR 解析结果接管卡片槽，草稿让位回队列（ADR 0014）
                releaseDisplayedDraft()
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
