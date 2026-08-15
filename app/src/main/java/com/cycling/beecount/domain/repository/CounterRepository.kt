package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.Count
import kotlinx.coroutines.flow.Flow

/**
 * 计数仓库接口：由 Domain 层定义，Data 层负责实现，
 * 依赖方向从外层指向内层。
 */
interface CounterRepository {
    val count: Flow<Count>
    suspend fun increment()
}
