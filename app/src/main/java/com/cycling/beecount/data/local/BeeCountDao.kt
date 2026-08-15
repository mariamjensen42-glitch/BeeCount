package com.cycling.beecount.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.TodayTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries WHERE date = :date ORDER BY createdAt DESC")
    fun observeEntriesOn(date: LocalDate): Flow<List<EntryEntity>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income
        FROM entries WHERE date = :date
        """
    )
    fun observeTotalsOn(date: LocalDate): Flow<TotalsRow>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    data class TotalsRow(
        val expense: Double,
        val income: Double,
    )
}

fun EntryDao.TotalsRow.toDomain(): TodayTotals = TodayTotals(
    expense = expense,
    income = income,
)

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY type, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long
}
