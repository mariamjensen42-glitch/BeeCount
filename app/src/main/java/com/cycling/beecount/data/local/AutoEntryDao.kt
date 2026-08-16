package com.cycling.beecount.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 待确认草稿队列 DAO（ADR 0014）：队首 = 最早的草稿 */
@Dao
interface PendingDraftDao {

    @Query("SELECT * FROM pending_drafts ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingDraftEntity>>

    @Insert
    suspend fun insert(draft: PendingDraftEntity): Long

    @Query("DELETE FROM pending_drafts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** 已处理通知去重 DAO（ADR 0014）：唯一索引兜底并发重复投递，按留存条数裁剪 */
@Dao
interface ProcessedNotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: ProcessedNotificationEntity): Long

    @Query(
        """
        DELETE FROM processed_notifications
        WHERE id NOT IN (SELECT id FROM processed_notifications ORDER BY createdAt DESC LIMIT :keep)
        """
    )
    suspend fun prune(keep: Int)
}
