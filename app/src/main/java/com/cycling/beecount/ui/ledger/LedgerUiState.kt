package com.cycling.beecount.ui.ledger

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.Tag

/**
 * MVI 架构：账本页 UI 状态。
 * [entries] 为全部账目（时间倒序），[selectedTagIds] 为多标签交集筛选条件（ADR 0007）。
 */
data class LedgerUiState(
    val entries: List<Entry> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val showTagManage: Boolean = false,
    val transientError: String? = null,
)

/**
 * MVI 架构：账本页事件
 */
sealed interface LedgerEvent {
    /** 点选/取消一个标签（多选交集） */
    data class ToggleTag(val tagId: Long) : LedgerEvent

    data object ClearFilter : LedgerEvent

    data object OpenTagManage : LedgerEvent

    data object CloseTagManage : LedgerEvent

    data class RenameTag(val id: Long, val name: String) : LedgerEvent

    /** 更新标签颜色（对话框内点色点已算好下一个板色） */
    data class UpdateTagColor(val id: Long, val color: Long) : LedgerEvent

    data class DeleteTag(val id: Long) : LedgerEvent

    data object DismissError : LedgerEvent
}
