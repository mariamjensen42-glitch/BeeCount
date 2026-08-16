package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.PendingDraft
import kotlinx.coroutines.flow.Flow

/**
 * 待确认草稿仓库（ADR 0014）：通知记账解析成功的草稿先落队列，
 * 确认/拒绝后移除；助手页观察队列把队首草稿展示为确认卡片。
 */
interface PendingDraftRepository {
    fun observePending(): Flow<List<PendingDraft>>

    suspend fun add(draft: PendingDraft): Long

    suspend fun remove(id: Long)
}
