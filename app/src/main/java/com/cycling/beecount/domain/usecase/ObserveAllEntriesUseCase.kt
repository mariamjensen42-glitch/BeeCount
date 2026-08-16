package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.repository.EntryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 用例：观察账本页全部账目（时间倒序，带各自标签）。
 */
class ObserveAllEntriesUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    operator fun invoke(): Flow<List<Entry>> = entryRepository.observeAllWithTags()
}
