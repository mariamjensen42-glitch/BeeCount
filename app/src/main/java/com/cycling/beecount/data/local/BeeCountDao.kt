package com.cycling.beecount.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.cycling.beecount.domain.model.Entry
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

    /** 账本页：全部账目（时间倒序）+ 各自的标签 */
    @Query("SELECT * FROM entries ORDER BY date DESC, createdAt DESC")
    fun observeAllWithTags(): Flow<List<EntryWithTags>>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertEntryTag(entryTag: EntryTagEntity)

    /** 入库账目并写入标签关联，同事务（ADR 0007） */
    @Transaction
    suspend fun addWithTags(entry: EntryEntity, tagIds: List<Long>): Long {
        val id = insert(entry)
        tagIds.distinct().forEach { tagId ->
            insertEntryTag(EntryTagEntity(entryId = id, tagId = tagId))
        }
        return id
    }

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

/** 账目 + 其标签（多对多，经 entry_tags 关联） */
data class EntryWithTags(
    @Embedded val entry: EntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryTagEntity::class,
            parentColumn = "entryId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

fun EntryWithTags.toDomain(): Entry = Entry(
    id = entry.id,
    type = entry.type,
    amount = entry.amount,
    amountRaw = entry.amountRaw,
    categoryName = entry.categoryName,
    date = entry.date,
    note = entry.note,
    createdAt = entry.createdAt,
    tags = tags.map { it.toDomain() },
)

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY id")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE tags SET color = :color WHERE id = :id")
    suspend fun updateColor(id: Long, color: Long)

    /** 删除标签；entry_tags 关联行随外键级联删除，账目本身保留 */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY type, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long
}
