package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.WeChatImportDraft
import com.cycling.beecount.domain.model.WeChatImportDraftEntry
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 相似交易检测（纯本地，零云依赖）：微信账单重复导入时，找出与库内已有账目
 * 「金额 + 日期窗口 + 对方」都接近的草稿，提示可能重复（不自动删除，交用户/导入流程决策）。
 *
 * 与 [EntryRepository.findExistingSourceRefs] 的单号精确去重互补：本类处理「无单号或单号不同
 * 但明显是同一笔」的情况。匹配严格（金额近等 + 对方相等/包含 + 日期窗口内）以降低误判。
 */
@Singleton
class SimilarityDetector @Inject constructor(
    @Named("base") private val entryRepository: EntryRepository,
) {
    /** 金额容差（元） */
    private val amountTolerance = 0.01

    /** 日期窗口（天）：与库内账目日期相差不超过此值 */
    private val dateWindowDays = 2

    data class SimilarHit(
        val matchedId: Long,
        val matchedDate: LocalDate,
        val matchedAmount: Double,
        val matchedCounterparty: String?,
    )

    suspend fun findSimilar(draft: WeChatImportDraft): Map<String, List<SimilarHit>> {
        val all = entryRepository.observeAllWithTags().first()
            .filter { it.type == EntryType.EXPENSE || it.type == EntryType.INCOME || it.type == EntryType.REFUND }
        return draft.entries.mapNotNull { e ->
            val hits = all
                .filter { it.sourceRef != e.sourceRef }
                .filter { kotlin.math.abs(it.amount - e.amount) <= amountTolerance }
                .filter { ChronoUnit.DAYS.between(it.date, e.date).let { d -> d >= -dateWindowDays && d <= dateWindowDays } }
                .filter { counterpartyMatch(it.counterparty, e.counterparty) }
                .map { SimilarHit(it.id, it.date, it.amount, it.counterparty) }
            if (hits.isNotEmpty()) e.sourceRef to hits else null
        }.toMap()
    }

    private fun counterpartyMatch(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        return x == y || x.contains(y) || y.contains(x)
    }
}

/** 辅助：从相似命中映射里取出所有命中的草稿单号 */
fun Map<String, List<SimilarityDetector.SimilarHit>>.similarRefs(): Set<String> = keys
