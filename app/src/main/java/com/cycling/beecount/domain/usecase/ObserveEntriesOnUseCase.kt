package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 用例：观察某一天的账目列表
 */
class ObserveEntriesOnUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    operator fun invoke(date: LocalDate): Flow<List<Entry>> = entryRepository.observeEntriesOn(date)
}
