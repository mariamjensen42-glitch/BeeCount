package com.cycling.beecount.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 自动记账设置仓库（ADR 0014）：总开关 + 失败提示节流时间戳（按包名）。
 * 开关关闭 = NLS 不处理任何通知；开启但缺前置（Key/权限）在设置页开关处引导。
 */
interface AutoEntrySettingsRepository {
    fun observeEnabled(): Flow<Boolean>

    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)

    /** 最近一次"解析失败低姿态提示"的时间戳（毫秒），用于同渠道 15 分钟节流 */
    suspend fun lastFailureAt(packageName: String): Long

    suspend fun setLastFailureAt(packageName: String, timeMillis: Long)
}
