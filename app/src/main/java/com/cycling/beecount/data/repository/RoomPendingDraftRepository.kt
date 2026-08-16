package com.cycling.beecount.data.repository

import com.cycling.beecount.data.local.PendingDraftDao
import com.cycling.beecount.data.local.PendingDraftEntity
import com.cycling.beecount.data.local.toDomain
import com.cycling.beecount.data.local.toEntity
import com.cycling.beecount.domain.model.PendingDraft
import com.cycling.beecount.domain.repository.PendingDraftRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 待确认草稿队列的 Room 实现（ADR 0014） */
@Singleton
class RoomPendingDraftRepository @Inject constructor(
    private val dao: PendingDraftDao,
) : PendingDraftRepository {

    override fun observePending(): Flow<List<PendingDraft>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun add(draft: PendingDraft): Long = dao.insert(draft.toEntity())

    override suspend fun remove(id: Long) = dao.deleteById(id)
}
