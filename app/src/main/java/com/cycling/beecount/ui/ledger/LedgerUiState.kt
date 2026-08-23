package com.cycling.beecount.ui.ledger

import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import java.time.LocalDate

/**
 * MVI 架构：账本页 UI 状态。
 * [entries] 为全部账目（时间倒序），[selectedTagIds] 为多标签交集筛选条件（ADR 0007）。
 * [filters] 为综合筛选条件（关键词/日期/分类/金额/对方/收支类型），[showFilters] 控制筛选面板展开。
 */
data class LedgerUiState(
    val entries: List<Entry> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val allCategories: List<Category> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val showTagManage: Boolean = false,
    val editingEntry: Entry? = null,
    val transientError: String? = null,
    val filters: LedgerFilters = LedgerFilters(),
    val showFilters: Boolean = false,
)

/** 综合筛选条件：各项为空时不过滤；多条件为「与」组合 */
data class LedgerFilters(
    /** 全局关键词，模糊匹配备注、交易对方、类别、金额原文 */
    val keyword: String = "",
    val dateRange: LedgerDateRange? = null,
    val categoryName: String? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val counterparty: String? = null,
    val type: EntryType? = null,
) {
    val isActive: Boolean
        get() = keyword.isNotBlank() || dateRange != null || categoryName != null ||
            minAmount != null || maxAmount != null || counterparty != null || type != null
}

/** 日期范围筛选 */
sealed interface LedgerDateRange {
    data object Today : LedgerDateRange
    data object ThisWeek : LedgerDateRange
    data object ThisMonth : LedgerDateRange
    data class Custom(val start: LocalDate, val end: LocalDate) : LedgerDateRange
}

/**
 * MVI 架构：账本页事件
 */
sealed interface LedgerEvent {
    /** 点选/取消一个标签（多选交集） */
    data class ToggleTag(val tagId: Long) : LedgerEvent

    data object ClearFilter : LedgerEvent

    /** 切换综合筛选面板展开/收起 */
    data object ToggleShowFilters : LedgerEvent

    /** 重置全部综合筛选条件 */
    data object ResetFilters : LedgerEvent

    /** 设置关键词 */
    data class SetKeyword(val keyword: String) : LedgerEvent

    /** 设置日期范围 */
    data class SetDateRange(val dateRange: LedgerDateRange?) : LedgerEvent

    /** 设置分类（null 表示全部） */
    data class SetCategory(val categoryName: String?) : LedgerEvent

    /** 设置金额区间（null 表示不限；min/max 至少一个非空才有意义） */
    data class SetAmountRange(val min: Double?, val max: Double?) : LedgerEvent

    /** 设置交易对方（null 表示全部） */
    data class SetCounterparty(val counterparty: String?) : LedgerEvent

    /** 设置收支类型（null 表示全部） */
    data class SetEntryType(val type: com.cycling.beecount.domain.model.EntryType?) : LedgerEvent

    data object OpenTagManage : LedgerEvent

    data object CloseTagManage : LedgerEvent

    data class RenameTag(val id: Long, val name: String) : LedgerEvent

    /** 更新标签颜色（对话框内点色点已算好下一个板色） */
    data class UpdateTagColor(val id: Long, val color: Long) : LedgerEvent

    data class DeleteTag(val id: Long) : LedgerEvent

    data class OpenEditEntry(val entry: Entry) : LedgerEvent

    data object CloseEditEntry : LedgerEvent

    data class SaveEditEntry(
        val entryId: Long,
        val editedType: com.cycling.beecount.domain.model.EntryType,
        val editedAmount: Double,
        val editedCategoryName: String,
        val editedDate: java.time.LocalDate,
        val editedNote: String,
        val tagNames: List<String>,
        val editedCounterparty: String? = null,
    ) : LedgerEvent

    data object DismissError : LedgerEvent
}
