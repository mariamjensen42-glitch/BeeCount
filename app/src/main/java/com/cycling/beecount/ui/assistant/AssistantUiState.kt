package com.cycling.beecount.ui.assistant

import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.TodayTotals
import java.time.LocalDate

/**
 * MVI 架构：助手页 UI 状态
 *
 * 对话流由 [messages] 表示：用户输入、AI 消息、确认卡片、已记反馈都是其中一条。
 * [pendingResult] 是当前等待用户确认的解析结果草稿（含可编辑的标签）。
 */
data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val pendingResult: AiParseResult? = null,
    val pendingOriginalText: String = "",
    val categories: List<Category> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val todayEntries: List<Entry> = emptyList(),
    val todayTotals: TodayTotals = TodayTotals(),
    val today: LocalDate = LocalDate.now(),
    val hasApiKey: Boolean = false,
    val isParsing: Boolean = false,
    val showKeySetup: Boolean = false,
    val transientError: String? = null,
)

/**
 * 对话流中的一条消息
 */
sealed interface AssistantMessage {
    /** 稳定唯一标识，用于 LazyColumn 的 key */
    val id: Long

    /** 用户发送的原话 */
    data class User(override val id: Long, val text: String) : AssistantMessage

    /** 助手回复：非记账输入的回应，或解析失败的提示 */
    data class Assistant(override val id: Long, val text: String) : AssistantMessage

    /** 已确认入库的账目（带撤销能力） */
    data class Saved(override val id: Long, val entry: Entry) : AssistantMessage
}

/**
 * MVI 架构：UI 事件（用户意图）
 */
sealed interface AssistantEvent {
    /** 提交输入去解析 */
    data class SubmitInput(val text: String) : AssistantEvent

    /** 确认卡片上修改金额 */
    data class EditAmount(val amount: Double) : AssistantEvent

    /** 确认卡片上修改类别 */
    data class EditCategory(val name: String) : AssistantEvent

    /** 确认卡片上修改标签选择（整体替换） */
    data class EditTags(val tags: List<String>) : AssistantEvent

    /** 确认入库 */
    data object Confirm : AssistantEvent

    /** 取消当前草稿 */
    data object DismissCard : AssistantEvent

    /** 撤销一条已入库账目 */
    data class Undo(val entryId: Long) : AssistantEvent

    /** 保存 API Key */
    data class SaveApiKey(val key: String) : AssistantEvent

    /** 关闭 Key 设置 */
    data object CloseKeySetup : AssistantEvent

    /** 打开 Key 设置 */
    data object OpenKeySetup : AssistantEvent

    /** 清除瞬时错误提示 */
    data object DismissError : AssistantEvent
}
