package com.cycling.beecount.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * DeepSeek API Key 仓库：用户自带 Key，本地 DataStore 明文存储（见 ADR-0001）。
 */
interface AiKeyRepository {
    /** 观察当前 Key（未配置时为 null） */
    fun observeKey(): Flow<String?>

    /** 读取当前 Key，未配置时返回 null */
    suspend fun getKey(): String?

    suspend fun saveKey(key: String)

    suspend fun clearKey()
}
