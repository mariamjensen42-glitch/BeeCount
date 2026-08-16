package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.PendingDraft
import com.cycling.beecount.domain.repository.PendingDraftRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** 观察待确认草稿队列（ADR 0014）：队首草稿由助手页展示为确认卡片 */
class ObservePendingDraftsUseCase @Inject constructor(
    private val repository: PendingDraftRepository,
) {
    operator fun invoke(): Flow<List<PendingDraft>> = repository.observePending()
}
