package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.WeChatImportDraft
import com.cycling.beecount.domain.model.WeChatImportDraftEntry
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导入用例测试（ADR 0012）：去重预览、一次性入库与批量撤销，均走 FakeEntryRepository。
 */
class ImportWeChatBillUseCaseTest {


    private fun draft(vararg entries: WeChatImportDraftEntry, skipped: Int = 0) =
        WeChatImportDraft(entries = entries.toList(), skippedCount = skipped)

    private fun draftEntry(
        type: EntryType,
        categoryName: String,
        sourceRef: String,
        date: LocalDate = LocalDate.of(2026, 4, 10),
    ) = WeChatImportDraftEntry(
        type = type,
        amount = 10.0,
        amountRaw = "10",
        categoryName = categoryName,
        date = date,
        note = "08:00 · 测试",
        sourceRef = sourceRef,
    )

    @Test
    fun `预览计算新增与重复、分类分布与时间范围`() = runTest {
        val repo = FakeEntryRepository(
            entries = listOf(
                Entry(type = EntryType.EXPENSE, amount = 1.0, amountRaw = "1", categoryName = "餐饮",
                    date = LocalDate.of(2026, 4, 1), note = "已存在的", sourceRef = "DUP-1"),
            ),
        )
        val useCase = ImportWeChatBillUseCase(repo, FakeTagRepository(), SimilarityDetector(repo))
        val preview = useCase.preview(
            draft(
                draftEntry(EntryType.EXPENSE, "餐饮", "DUP-1", LocalDate.of(2026, 4, 1)),
                draftEntry(EntryType.EXPENSE, "餐饮", "NEW-1", LocalDate.of(2026, 4, 2)),
                draftEntry(EntryType.EXPENSE, "交通", "NEW-2", LocalDate.of(2026, 4, 3)),
                draftEntry(EntryType.NEUTRAL, "中性", "NEW-3", LocalDate.of(2026, 4, 4)),
                skipped = 2,
            ),
        )

        assertEquals(LocalDate.of(2026, 4, 2), preview.from)
        assertEquals(LocalDate.of(2026, 4, 4), preview.to)
        assertEquals(2, preview.expenseCount)
        assertEquals(0, preview.incomeCount)
        assertEquals(1, preview.neutralCount)
        assertEquals(1, preview.duplicateCount)
        assertEquals(2, preview.skippedCount)
        assertEquals(listOf("餐饮" to 1, "交通" to 1, "中性" to 1), preview.categoryDistribution)
    }

    @Test
    fun `确认导入只插入新单号并返回导入与重复笔数`() = runTest {
        val repo = FakeEntryRepository(
            entries = listOf(
                Entry(type = EntryType.EXPENSE, amount = 1.0, amountRaw = "1", categoryName = "餐饮",
                    date = LocalDate.of(2026, 4, 1), note = "已存在的", sourceRef = "DUP-1"),
            ),
        )
        val tagRepo = FakeTagRepository()
        val useCase = ImportWeChatBillUseCase(repo, tagRepo, SimilarityDetector(repo))
        val result = useCase.confirm(
            draft(
                draftEntry(EntryType.EXPENSE, "餐饮", "DUP-1"),
                draftEntry(EntryType.EXPENSE, "餐饮", "NEW-1"),
                draftEntry(EntryType.EXPENSE, "交通", "NEW-2"),
                draftEntry(EntryType.NEUTRAL, "中性", "NEW-3"),
            ),
        )

        assertEquals(3, result.imported)
        assertEquals(1, result.duplicates)
        // 撤销范围只含本次插入的单号，不含重复跳过（否则会误删历史导入的账目）
        assertEquals(listOf("NEW-1", "NEW-2", "NEW-3"), result.insertedRefs)
        assertEquals(4, repo.storedEntries.size)
        assertEquals(setOf("NEW-1", "NEW-2", "NEW-3"), repo.storedEntries.mapNotNull { it.sourceRef }.toSet() - "DUP-1")
        // 本次插入的账目统一挂「微信」来源标签，重复跳过的不重复打
        val newEntries = repo.storedEntries.filter { it.sourceRef != "DUP-1" }
        assertEquals(listOf("微信"), newEntries.map { it.tags.single().name }.distinct())
        assertEquals(listOf("微信"), tagRepo.tags.map { it.name })
        assertEquals(1, tagRepo.createCount)
    }

    @Test
    fun `全部重复时不再创建微信标签也不插入`() = runTest {
        val repo = FakeEntryRepository(
            entries = listOf(
                Entry(type = EntryType.EXPENSE, amount = 1.0, amountRaw = "1", categoryName = "餐饮",
                    date = LocalDate.of(2026, 4, 1), note = "已存在的", sourceRef = "DUP-1"),
            ),
        )
        val tagRepo = FakeTagRepository()
        val useCase = ImportWeChatBillUseCase(repo, tagRepo, SimilarityDetector(repo))
        val result = useCase.confirm(draft(draftEntry(EntryType.EXPENSE, "餐饮", "DUP-1")))

        assertEquals(0, result.imported)
        assertEquals(1, result.duplicates)
        assertEquals(1, repo.storedEntries.size)
        assertEquals(0, tagRepo.createCount)
    }

    @Test
    fun `撤销导入按单号集合删除并返回笔数`() = runTest {
        val repo = FakeEntryRepository(
            entries = listOf(
                Entry(type = EntryType.EXPENSE, amount = 1.0, amountRaw = "1", categoryName = "餐饮",
                    date = LocalDate.of(2026, 4, 1), note = "导入的", sourceRef = "WX-1"),
                Entry(type = EntryType.EXPENSE, amount = 2.0, amountRaw = "2", categoryName = "交通",
                    date = LocalDate.of(2026, 4, 2), note = "导入的", sourceRef = "WX-2"),
                Entry(type = EntryType.EXPENSE, amount = 3.0, amountRaw = "3", categoryName = "餐饮",
                    date = LocalDate.of(2026, 4, 3), note = "手动的"),
            ),
        )
        val useCase = UndoWeChatImportUseCase(repo)
        val deleted = useCase(listOf("WX-1", "WX-2"))

        assertEquals(2, deleted)
        assertEquals(listOf("手动的"), repo.storedEntries.map { it.note })
    }
}
