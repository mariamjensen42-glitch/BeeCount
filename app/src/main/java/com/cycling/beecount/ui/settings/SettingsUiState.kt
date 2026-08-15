package com.cycling.beecount.ui.settings

import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag

/**
 * MVI 架构：设置页 UI 状态（ADR 0008）。
 * [apiKeyMasked] 为脱敏展示的 Key，空表示未配置。
 */
data class SettingsUiState(
    val apiKeyMasked: String = "",
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val transientError: String? = null,
)

/**
 * MVI 架构：设置页事件
 */
sealed interface SettingsEvent {
    data class SaveApiKey(val key: String) : SettingsEvent

    data object ClearApiKey : SettingsEvent

    data class CreateCategory(val name: String, val type: EntryType) : SettingsEvent

    data class RenameCategory(val id: Long, val name: String) : SettingsEvent

    data class DeleteCategory(val id: Long) : SettingsEvent

    data class RenameTag(val id: Long, val name: String) : SettingsEvent

    data class UpdateTagColor(val id: Long, val color: Long) : SettingsEvent

    data class DeleteTag(val id: Long) : SettingsEvent

    /** 清空全部账目（只清账目，类别/标签保留） */
    data object ClearAllEntries : SettingsEvent

    data object DismissError : SettingsEvent
}
