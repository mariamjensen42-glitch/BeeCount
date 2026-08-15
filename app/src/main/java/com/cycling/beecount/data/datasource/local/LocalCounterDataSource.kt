package com.cycling.beecount.data.datasource.local

import com.cycling.beecount.data.datasource.CounterDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地数据源示例实现：目前用内存 StateFlow 模拟，
 * 后续可替换为 Room / DataStore 实现。
 */
class LocalCounterDataSource : CounterDataSource {

    private val _count = MutableStateFlow(0)

    override val count: Flow<Int> = _count.asStateFlow()

    override suspend fun increment() {
        _count.value += 1
    }
}
