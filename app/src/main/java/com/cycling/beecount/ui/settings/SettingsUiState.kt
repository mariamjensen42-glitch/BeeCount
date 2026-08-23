package com.cycling.beecount.ui.settings

import android.net.Uri
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.model.WeChatImportDraft
import com.cycling.beecount.domain.usecase.WeChatImportPreview

/**
 * MVI 架构：设置页 UI 状态（ADR 0008）。
 * [apiKeyMasked] 为脱敏展示的 Key，空表示未配置。
 *
 * [weChatImport] 承载微信账单导入流程（ADR 0012）：Idle → Loading → Confirm；
 * [pendingWeChatUndo] 非空时表示刚完成一次导入、撤销窗口开启中（30 秒）。
 */
data class SettingsUiState(
    val apiKeyMasked: String = "",
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val transientMessage: String? = null,
    val transientError: String? = null,
    val weChatImport: WeChatImportUiState = WeChatImportUiState.Idle,
    val pendingWeChatUndo: PendingWeChatUndo? = null,
)

/**
 * 微信账单导入流程状态（ADR 0012）。
 * [Confirm] 持有预览与草稿：预览供确认层展示，草稿在点"导入"时重新跑去重后入库。
 */
sealed interface WeChatImportUiState {
    data object Idle : WeChatImportUiState

    data object Loading : WeChatImportUiState

    data class Confirm(
        val preview: WeChatImportPreview,
        val draft: WeChatImportDraft,
    ) : WeChatImportUiState
}

/** 一次导入完成后的撤销窗口数据（ADR 0012）：按交易单号集合批量删除 */
data class PendingWeChatUndo(
    val sourceRefs: List<String>,
    val imported: Int,
    val duplicates: Int,
)

/**
 * MVI 架构：设置页事件
 */
sealed interface SettingsEvent {
    data class SaveApiKey(val key: String) : SettingsEvent

    data object ClearApiKey : SettingsEvent

    data class CreateCategory(val name: String, val type: EntryType) : SettingsEvent

    data class CreateChildCategory(val parentId: Long, val name: String) : SettingsEvent

    data class RenameCategory(val id: Long, val name: String) : SettingsEvent

    /** 删除类别并把历史账目（含子分类）归并到 [targetId] */
    data class DeleteCategoryWithMerge(val id: Long, val targetId: Long) : SettingsEvent

    /** 调整分类父级：折为一级分类传 [parentId] = null */
    data class MoveCategoryParent(val id: Long, val parentId: Long?) : SettingsEvent

    data class UpdateCategoryIcon(val id: Long, val icon: String) : SettingsEvent

    data class UpdateCategoryColor(val id: Long, val color: Long) : SettingsEvent

    /** 手动排序序号；0 表示回到「按使用频率自动排序」 */
    data class UpdateCategorySortOrder(val id: Long, val sortOrder: Int) : SettingsEvent

    data class UpdateCategoryHidden(val id: Long, val isHidden: Boolean) : SettingsEvent

    data class RenameTag(val id: Long, val name: String) : SettingsEvent

    data class UpdateTagColor(val id: Long, val color: Long) : SettingsEvent

    data class DeleteTag(val id: Long) : SettingsEvent

    /** 清空全部账目（只清账目，类别/标签保留） */
    data object ClearAllEntries : SettingsEvent

    /** 清空现有账目后写入当前年度演示样本 */
    data object FillDemoData : SettingsEvent

    /** 文件选择器选中的微信账单，开始解析 */
    data class ImportWeChatBill(val uri: Uri) : SettingsEvent

    /** 确认层点"导入"，一次性入库 */
    data object ConfirmWeChatImport : SettingsEvent

    /** 关闭确认层（取消导入） */
    data object DismissWeChatImport : SettingsEvent

    /** 撤销窗口内点"撤销"，删除本次导入的全部账目 */
    data object UndoWeChatImport : SettingsEvent

    data object DismissMessage : SettingsEvent

    data object DismissError : SettingsEvent
}
