package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.EntrySnapshot
import javax.inject.Inject
import timber.log.Timber

/** Deletes an entry and returns the snapshot needed to undo that deletion. */
class DeleteEntryWithUndoUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    suspend operator fun invoke(entryId: Long): EntrySnapshot? {
        Timber.d("删除账目，entryId=%s", entryId)
        val snapshot = entryRepository.deleteWithSnapshot(entryId)
        if (snapshot == null) {
            Timber.w("删除账目未找到快照，entryId=%s", entryId)
        } else {
            Timber.i("账目已删除，entryId=%s，快照可撤销", entryId)
        }
        return snapshot
    }
}
