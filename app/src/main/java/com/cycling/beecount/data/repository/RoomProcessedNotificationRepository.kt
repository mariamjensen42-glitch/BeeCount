package com.cycling.beecount.data.repository

import com.cycling.beecount.data.local.ProcessedNotificationDao
import com.cycling.beecount.data.local.ProcessedNotificationEntity
import com.cycling.beecount.domain.repository.ProcessedNotificationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已处理通知去重的 Room 实现（ADR 0014）。
 * 唯一索引兜底并发重复投递：并发插入时后到者返回 -1，判定为重复。
 */
@Singleton
class RoomProcessedNotificationRepository @Inject constructor(
    private val dao: ProcessedNotificationDao,
) : ProcessedNotificationRepository {

    override suspend fun markProcessedOrAlreadySeen(packageName: String, notifyKey: String, text: String): Boolean {
        val rowId = dao.insert(
            ProcessedNotificationEntity(
                packageName = packageName,
                notifyKey = notifyKey,
                text = text,
                createdAt = System.currentTimeMillis(),
            )
        )
        if (rowId > 0) {
            // 只在成功插入时裁剪一次，避免每次重复投递都触发
            dao.prune(MAX_KEEP)
        }
        return rowId == -1L
    }

    private companion object {
        /** 留存最近 500 条去重键，覆盖数月的通知量级 */
        const val MAX_KEEP = 500
    }
}
