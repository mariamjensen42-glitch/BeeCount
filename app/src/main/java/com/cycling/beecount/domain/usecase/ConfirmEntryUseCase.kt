package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.EntryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 用例：确认并入库一条账目。
 *
 * 若账目类别在类别表中不存在（用户手动填写的新类别），则先创建该自定义类别再入库。
 * 备注为记账时的用户原话（Q16：原文保留）。
 * 返回入库后的账目。
 */
class ConfirmEntryUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
    private val categoryRepository: CategoryRepository,
) {

    suspend operator fun invoke(
        result: AiParseResult,
        editedAmount: Double,
        editedCategoryName: String,
        originalText: String,
    ): Entry {
        require(result.recordable) { "recordable=false 的结果不能入库" }
        val type = requireNotNull(result.type) { "账目类型缺失" }
        val date = requireNotNull(result.date) { "账目日期缺失" }
        val amountRaw = requireNotNull(result.amountRaw) { "金额原文缺失" }

        val categoryName = editedCategoryName.trim()
        require(categoryName.isNotEmpty()) { "类别不能为空" }
        require(editedAmount > 0) { "金额必须大于 0" }

        // 若类别不存在则创建自定义类别（确认卡片内创建，Q13）
        val existing = categoryRepository.observeAll().first()
            .any { it.name == categoryName && it.type == type }
        if (!existing) {
            categoryRepository.create(name = categoryName, type = type)
        }

        val entry = Entry(
            type = type,
            amount = editedAmount,
            amountRaw = amountRaw,
            categoryName = categoryName,
            date = date,
            note = originalText,
        )
        val id = entryRepository.add(entry)
        return entry.copy(id = id)
    }
}
