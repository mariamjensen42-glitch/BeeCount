package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.model.WeChatImportDraft
import com.cycling.beecount.domain.model.toEntry
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.TagRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * 用例：微信账单导入的去重预览与一次性入库（ADR 0012）。
 *
 * [preview] 在确认前展示去重结果（本文件将新增 vs 与已有重复）与分类分布；
 * [confirm] 按交易单号过滤掉已存在的账目后批量入库，返回实际插入与重复笔数。
 * 两者都只依赖 [WeChatImportDraft]，确认时重新计算去重，不信任预览时的快照。
 *
 * 本次实际插入的账目会统一挂上「微信」来源标签（首次导入时自动创建），
 * 可在账本页按标签筛选微信来源的账目；撤销导入时标签关联随账目级联清除。
 */
class ImportWeChatBillUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
    private val tagRepository: TagRepository,
) {

    suspend fun preview(draft: WeChatImportDraft): WeChatImportPreview {
        Timber.d("微信导入预览：草案 %d 条", draft.entries.size)
        val existing = entryRepository.findExistingSourceRefs(draft.entries.map { it.sourceRef })
        val newEntries = draft.entries.filter { it.sourceRef !in existing }
        Timber.d("微信导入预览：已有 %d 条去重命中，新增 %d 条", existing.size, newEntries.size)
        return WeChatImportPreview(
            from = newEntries.minOfOrNull { it.date } ?: draft.entries.minOfOrNull { it.date },
            to = newEntries.maxOfOrNull { it.date } ?: draft.entries.maxOfOrNull { it.date },
            expenseCount = newEntries.count { it.type == EntryType.EXPENSE },
            incomeCount = newEntries.count { it.type == EntryType.INCOME },
            neutralCount = newEntries.count { it.type == EntryType.NEUTRAL },
            skippedCount = draft.skippedCount,
            duplicateCount = draft.entries.size - newEntries.size,
            categoryDistribution = newEntries.groupingBy { it.categoryName }.eachCount()
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value },
        )
    }

    suspend fun confirm(draft: WeChatImportDraft): WeChatImportResult {
        Timber.d("微信导入确认开始：草案 %d 条", draft.entries.size)
        val existing = entryRepository.findExistingSourceRefs(draft.entries.map { it.sourceRef })
        val fresh = draft.entries.filter { it.sourceRef !in existing }
        Timber.d("微信导入确认：去重后新增 %d 条", fresh.size)
        val imported = if (fresh.isEmpty()) {
            0
        } else {
            val wechatTag = findOrCreateWeChatTag()
            entryRepository.addAllWithTag(fresh.map { it.toEntry() }, wechatTag)
        }
        Timber.i("微信导入确认完成：入库 %d 条、重复跳过 %d 条", imported, draft.entries.size - imported)
        return WeChatImportResult(
            imported = imported,
            duplicates = draft.entries.size - imported,
            // 撤销范围只含本次实际插入的单号：重复跳过的行是历史导入的，撤销时不能误删（ADR 0012）
            insertedRefs = fresh.map { it.sourceRef },
        )
    }

    /** 首次导入时创建「微信」来源标签，之后复用；无新账目插入时不创建 */
    private suspend fun findOrCreateWeChatTag(): Tag {
        val existing = tagRepository.observeAll().first().firstOrNull { it.name == WECHAT_TAG_NAME }
        if (existing != null) return existing
        val id = tagRepository.create(WECHAT_TAG_NAME, WECHAT_TAG_COLOR)
        return Tag(id = id, name = WECHAT_TAG_NAME, color = WECHAT_TAG_COLOR, isCustom = true)
    }

    private companion object {
        /** 来源标签名与色（微信品牌绿），见 CONTEXT.md「来源标签」 */
        const val WECHAT_TAG_NAME = "微信"
        const val WECHAT_TAG_COLOR = 0xFF07C160L
    }
}

/** 导入确认层展示的内容（ADR 0012）：账单时间范围、收支/中性笔数、跳过与重复、分类分布 */
data class WeChatImportPreview(
    val from: LocalDate?,
    val to: LocalDate?,
    val expenseCount: Int,
    val incomeCount: Int,
    val neutralCount: Int,
    val skippedCount: Int,
    val duplicateCount: Int,
    val categoryDistribution: List<Pair<String, Int>>,
)

/**
 * 导入结果（ADR 0012）：实际入库笔数、因交易单号重复跳过的笔数，
 * 以及本次实际插入的单号集合 [insertedRefs]（撤销本次导入的范围）。
 */
data class WeChatImportResult(
    val imported: Int,
    val duplicates: Int,
    val insertedRefs: List<String>,
)
