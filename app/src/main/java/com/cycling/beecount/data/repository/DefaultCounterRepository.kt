package com.cycling.beecount.data.repository

import com.cycling.beecount.data.datasource.CounterDataSource
import com.cycling.beecount.domain.model.Count
import com.cycling.beecount.domain.repository.CounterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 仓库实现：将 Data 层的数据源转换为 Domain 层模型
 */
class DefaultCounterRepository(
    private val dataSource: CounterDataSource,
) : CounterRepository {

    override val count: Flow<Count> = dataSource.count.map { Count(it) }

    override suspend fun increment() {
        dataSource.increment()
    }
}
