package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.EntryRepository
import javax.inject.Inject
import timber.log.Timber

/**
 * 用例：撤销（删除）一条已入库的账目（Q21）。
 */
class UndoEntryUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    suspend operator fun invoke(entryId: Long) {
        Timber.d("撤销账目，entryId=%s", entryId)
        entryRepository.delete(entryId)
        Timber.i("账目已撤销，entryId=%s", entryId)
    }
}
