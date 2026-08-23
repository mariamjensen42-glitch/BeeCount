package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.EntryRepository
import javax.inject.Inject
import timber.log.Timber

/**
 * 用例：清空全部账目（ADR 0008）。
 * 只清账目，类别与标签保留——它们是持续使用的元数据。
 */
class ClearAllEntriesUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    suspend operator fun invoke() {
        Timber.w("执行清空全部账目")
        entryRepository.clearAll()
        Timber.i("已清空全部账目")
    }
}
