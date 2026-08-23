package com.cycling.beecount.data.repository

import com.cycling.beecount.data.local.BudgetDao
import com.cycling.beecount.data.local.EntryDao
import com.cycling.beecount.data.local.EntryLightRow
import com.cycling.beecount.data.local.toDomain
import com.cycling.beecount.data.local.toEntity
import com.cycling.beecount.domain.model.Budget
import com.cycling.beecount.domain.model.BudgetCycle
import com.cycling.beecount.domain.model.BudgetException
import com.cycling.beecount.domain.model.BudgetMath
import com.cycling.beecount.domain.model.BudgetProgress
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.BudgetRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class RoomBudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val entryDao: EntryDao,
) : BudgetRepository {

    override fun observeBudgets(): Flow<List<Budget>> =
        budgetDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeExceptions(): Flow<List<BudgetException>> =
        budgetDao.observeExceptions().map { list -> list.map { BudgetException(it) } }

    override suspend fun create(budget: Budget): Long {
        val anchor = when {
            budget.cycle == BudgetCycle.CUSTOM_DAYS && budget.customAnchor == null ->
                LocalDate.now()
            else -> budget.customAnchor
        }
        return budgetDao.insert(budget.copy(customAnchor = anchor).toEntity())
    }

    override suspend fun updateAmount(id: Long, amount: Double) = budgetDao.updateAmount(id, amount)

    override suspend fun updateCarryOver(id: Long, carryOver: Boolean) = budgetDao.updateCarryOver(id, carryOver)

    override suspend fun updateEnabled(id: Long, enabled: Boolean) = budgetDao.updateEnabled(id, enabled)

    override suspend fun delete(id: Long) = budgetDao.deleteById(id)

    override suspend fun addException(date: LocalDate) = budgetDao.insertException(BudgetException(date).toEntity())

    override suspend fun removeException(date: LocalDate) = budgetDao.deleteException(date)

    /** 实时预算进度：budgets × 例外日 × 账目做 combine，据此计算每条预算当前周期的消费/结余/日均。 */
    override fun observeProgress(today: LocalDate): Flow<List<BudgetProgress>> =
        combine(
            budgetDao.observeAll(),
            budgetDao.observeExceptions(),
            entryDao.observeLightAll(),
        ) { budgetEntities, exceptionDates, rows ->
            val exceptions = exceptionDates.toSet()
            val entries = rows.map { it.toDomainEntry() }
            budgetEntities.mapNotNull { entity ->
                val budget = entity.toDomain()
                // 仅当预算周期已在本日或更早开始才计（自定义周期锚点晚于今日的跳过）
                val period = BudgetMath.periodOf(budget, today)
                    ?: return@mapNotNull null
                val spent = BudgetMath.spent(entries, period, budget.categoryName, exceptions)
                val carryIn = if (budget.carryOver) {
                    BudgetMath.carryIn(budget, today) { p ->
                        BudgetMath.spent(entries, p, budget.categoryName, exceptions)
                    }
                } else 0.0
                val base = budget.amount + carryIn
                BudgetProgress(
                    budget = budget,
                    period = period,
                    spent = spent,
                    base = base,
                    remainingDays = BudgetMath.remainingDays(period, today),
                )
            }
        }
}

private fun EntryLightRow.toDomainEntry(): Entry = Entry(
    type = EntryType.valueOf(type),
    amount = amount,
    amountRaw = "",
    categoryName = categoryName,
    date = date,
    note = "",
)
