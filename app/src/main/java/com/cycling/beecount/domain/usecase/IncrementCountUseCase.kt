package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.CounterRepository

/**
 * 用例：计数加一
 */
class IncrementCountUseCase(
    private val repository: CounterRepository,
) {
    suspend operator fun invoke() {
        repository.increment()
    }
}
