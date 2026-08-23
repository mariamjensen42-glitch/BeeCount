package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Budget
import com.cycling.beecount.domain.model.BudgetException
import com.cycling.beecount.domain.repository.BudgetRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** 预算的增删改与预算例外日管理。 */
@Singleton
class ManageBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    fun observeExceptions(): Flow<List<BudgetException>> = repository.observeExceptions()

    suspend fun create(budget: Budget): Long = repository.create(budget)

    suspend fun updateAmount(id: Long, amount: Double) = repository.updateAmount(id, amount)

    suspend fun updateCarryOver(id: Long, carryOver: Boolean) = repository.updateCarryOver(id, carryOver)

    suspend fun updateEnabled(id: Long, enabled: Boolean) = repository.updateEnabled(id, enabled)

    suspend fun delete(id: Long) = repository.delete(id)

    suspend fun addException(date: LocalDate) = repository.addException(date)

    suspend fun removeException(date: LocalDate) = repository.removeException(date)
}
