package com.cycling.beecount.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 已处理通知表（ADR 0014）：持久化去重键（包名 + notification key + 文本，唯一索引），
 * 文本参与去重以覆盖通知原地更新。按留存裁剪。
 */
@Entity(
    tableName = "processed_notifications",
    indices = [Index(value = ["packageName", "notifyKey", "text"], unique = true)],
)
data class ProcessedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val notifyKey: String,
    val text: String,
    val createdAt: Long,
)
