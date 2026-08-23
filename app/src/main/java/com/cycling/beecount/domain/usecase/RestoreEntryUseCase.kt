package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.EntrySnapshot
import javax.inject.Inject
import timber.log.Timber

/** Restores an entry and its original tag associations from a deletion snapshot. */
class RestoreEntryUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    suspend operator fun invoke(snapshot: EntrySnapshot) {
        Timber.d("恢复账目，entryId=%s", snapshot.entry.id)
        entryRepository.restoreSnapshot(snapshot)
        Timber.i("账目已恢复，entryId=%s", snapshot.entry.id)
    }
}
