package com.cycling.beecount.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.cycling.beecount.domain.model.Budget
import com.cycling.beecount.domain.model.BudgetCycle
import com.cycling.beecount.domain.model.BudgetException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 预算记录实体（ADR 预算）。[cycle] 存 [BudgetCycle.name]；[categoryName] 为 null 表示总预算，
 * 否则为一级分类叶名（账目快照）；[customAnchor] 仅自定义周期使用。
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cycle: String,
    val lengthDays: Int = 30,
    val customAnchor: LocalDate? = null,
    val categoryName: String? = null,
    val amount: Double,
    val carryOver: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 预算例外日实体：该日账目全额不计入预算消费 */
@Entity(tableName = "budget_exceptions")
data class BudgetExceptionEntity(
    @PrimaryKey val date: LocalDate,
)

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    cycle = BudgetCycle.valueOf(cycle),
    lengthDays = lengthDays,
    customAnchor = customAnchor,
    categoryName = categoryName,
    amount = amount,
    carryOver = carryOver,
    enabled = enabled,
    createdAt = createdAt,
)

fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    cycle = cycle.name,
    lengthDays = lengthDays,
    customAnchor = customAnchor,
    categoryName = categoryName,
    amount = amount,
    carryOver = carryOver,
    enabled = enabled,
    createdAt = createdAt,
)

fun BudgetException.toEntity(): BudgetExceptionEntity = BudgetExceptionEntity(date = date)

fun BudgetExceptionEntity.toDomain(): BudgetException = BudgetException(date = date)

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets ORDER BY id")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Query("UPDATE budgets SET amount = :amount WHERE id = :id")
    suspend fun updateAmount(id: Long, amount: Double)

    @Query("UPDATE budgets SET carryOver = :carryOver WHERE id = :id")
    suspend fun updateCarryOver(id: Long, carryOver: Boolean)

    @Query("UPDATE budgets SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT date FROM budget_exceptions")
    fun observeExceptions(): Flow<List<LocalDate>>

    @Insert
    suspend fun insertException(entity: BudgetExceptionEntity)

    @Query("DELETE FROM budget_exceptions WHERE date = :date")
    suspend fun deleteException(date: LocalDate)
}
