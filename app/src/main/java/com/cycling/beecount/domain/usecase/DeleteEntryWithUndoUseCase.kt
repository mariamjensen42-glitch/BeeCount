package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.EntrySnapshot
import javax.inject.Inject

/** Deletes an entry and returns the snapshot needed to undo that deletion. */
class DeleteEntryWithUndoUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    suspend operator fun invoke(entryId: Long): EntrySnapshot? =
        entryRepository.deleteWithSnapshot(entryId)
}
