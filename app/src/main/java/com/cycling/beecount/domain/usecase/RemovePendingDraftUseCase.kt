package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.PendingDraftRepository
import javax.inject.Inject

/** 从待确认队列移除一条草稿（确认入库后 / 用户拒绝时，ADR 0014） */
class RemovePendingDraftUseCase @Inject constructor(
    private val repository: PendingDraftRepository,
) {
    suspend operator fun invoke(id: Long) = repository.remove(id)
}
