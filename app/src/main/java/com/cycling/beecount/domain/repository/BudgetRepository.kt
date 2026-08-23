package com.cycling.beecount.domain.repository

import com.cycling.beecount.domain.model.Budget
import com.cycling.beecount.domain.model.BudgetException
import com.cycling.beecount.domain.model.BudgetProgress
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** 预算与例外的读写仓库。 */
interface BudgetRepository {
    fun observeBudgets(): Flow<List<Budget>>
    fun observeExceptions(): Flow<List<BudgetException>>

    /** 实时预算进度：combine 预算/例外/账目，计算每条预算当前周期统计。 */
    fun observeProgress(today: LocalDate): Flow<List<BudgetProgress>>

    suspend fun create(budget: Budget): Long
    suspend fun updateAmount(id: Long, amount: Double)
    suspend fun updateCarryOver(id: Long, carryOver: Boolean)
    suspend fun updateEnabled(id: Long, enabled: Boolean)
    suspend fun delete(id: Long)
    suspend fun addException(date: LocalDate)
    suspend fun removeException(date: LocalDate)
}
