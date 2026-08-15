package com.cycling.beecount.data.datasource

import kotlinx.coroutines.flow.Flow

/**
 * 数据源接口：Data 层内部定义，用于解耦数据来源
 * （本地 Room / DataStore 或远程网络接口）
 */
interface CounterDataSource {
    val count: Flow<Int>
    suspend fun increment()
}
