package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.EntrySnapshot
import javax.inject.Inject

/** Restores an entry and its original tag associations from a deletion snapshot. */
class RestoreEntryUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    suspend operator fun invoke(snapshot: EntrySnapshot) {
        entryRepository.restoreSnapshot(snapshot)
    }
}
