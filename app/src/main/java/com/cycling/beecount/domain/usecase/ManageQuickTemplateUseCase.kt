package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.QuickTemplate
import com.cycling.beecount.domain.repository.QuickTemplateRepository
import javax.inject.Inject

/**
 * 用例：快捷模板管理（新建/改名/改价/删除）。数据存于 DataStore（见 [QuickTemplateRepository]）。
 */
class ManageQuickTemplateUseCase @Inject constructor(
    private val quickTemplateRepository: QuickTemplateRepository,
) {

    suspend fun create(template: QuickTemplate): Long {
        require(template.title.trim().isNotEmpty()) { "模板标题不能为空" }
        require(template.categoryName.trim().isNotEmpty()) { "类别不能为空" }
        require(template.amount > 0) { "金额必须大于 0" }
        return quickTemplateRepository.add(
            template.copy(
                title = template.title.trim(),
                categoryName = template.categoryName.trim(),
                note = template.note.trim(),
            ),
        )
    }

    suspend fun update(template: QuickTemplate) {
        require(template.title.trim().isNotEmpty()) { "模板标题不能为空" }
        require(template.categoryName.trim().isNotEmpty()) { "类别不能为空" }
        require(template.amount > 0) { "金额必须大于 0" }
        quickTemplateRepository.update(
            template.copy(
                title = template.title.trim(),
                categoryName = template.categoryName.trim(),
                note = template.note.trim(),
            ),
        )
    }

    suspend fun delete(id: Long) = quickTemplateRepository.delete(id)
}
