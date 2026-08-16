package com.cycling.beecount.domain.repository

/**
 * 已处理通知仓库（ADR 0014）：持久化去重键（包名 + notification key + 文本），
 * 同一通知重复投递（系统重发、NLS 重启后重放）直接跳过；键含文本——原地更新文本变算新通知。
 */
interface ProcessedNotificationRepository {

    /**
     * 记录一次处理；若同 key 同文本已存在返回 true（重复投递），首次记录返回 false。
     * 返回后由实现自行按留存裁剪。
     */
    suspend fun markProcessedOrAlreadySeen(packageName: String, notifyKey: String, text: String): Boolean
}
