package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.EntryRepository
import javax.inject.Inject
import timber.log.Timber

/**
 * 用例：撤销一次微信账单导入（ADR 0012）。
 * 按本次导入的全部交易单号集合删除对应账目，返回删除笔数。
 */
class UndoWeChatImportUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    suspend operator fun invoke(sourceRefs: Collection<String>): Int {
        Timber.d("撤销微信导入开始：sourceRefs=%d 条", sourceRefs.size)
        val deleted = entryRepository.deleteBySourceRefs(sourceRefs)
        Timber.i("撤销微信导入完成：删除 %d 条", deleted)
        return deleted
    }
}
