package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Count
import com.cycling.beecount.domain.repository.CounterRepository
import kotlinx.coroutines.flow.Flow

/**
 * 用例：订阅当前计数
 */
class GetCountUseCase(
    private val repository: CounterRepository,
) {
    operator fun invoke(): Flow<Count> = repository.count
}
