package com.cycling.beecount.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.EntrySnapshot
import com.cycling.beecount.domain.repository.TodayTotals
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Transaction
    @Query("SELECT * FROM entries WHERE date = :date ORDER BY createdAt DESC")
    fun observeEntriesOn(date: LocalDate): Flow<List<EntryWithTags>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income
        FROM entries WHERE date = :date
        """
    )
    fun observeTotalsOn(date: LocalDate): Flow<TotalsRow>

    /** 一次性区间汇总（支出/收入合计，中性记录不计）：桌面小组件用（ADR 0013） */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS expense,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS income
        FROM entries WHERE date BETWEEN :start AND :end
        """
    )
    suspend fun totalsBetween(start: LocalDate, end: LocalDate): TotalsRow

    /** 账本页：全部账目（时间倒序）+ 各自的标签 */
    @Transaction
    @Query("SELECT * FROM entries ORDER BY date DESC, createdAt DESC")
    fun observeAllWithTags(): Flow<List<EntryWithTags>>

    /** 图表页：观察 [start, end] 区间内的账目（时间正序，带各自标签，ADR 0009） */
    @Transaction
    @Query("SELECT * FROM entries WHERE date BETWEEN :start AND :end ORDER BY date ASC, createdAt ASC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<EntryWithTags>>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)

    /** 批量插入并忽略 sourceRef 唯一索引冲突（ADR 0012 导入去重兜底），返回各行的 rowId，冲突行为 -1 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnoreConflict(entries: List<EntryEntity>): List<Long>

    /** 批量插入并给本次实际插入的账目挂统一标签，同事务（ADR 0012 微信来源标签） */
    @Transaction
    suspend fun insertAllWithTag(entries: List<EntryEntity>, tagId: Long): Int {
        val rowIds = insertAllIgnoreConflict(entries)
        var inserted = 0
        rowIds.forEach { rowId ->
            if (rowId > 0) {
                insertEntryTag(EntryTagEntity(entryId = rowId, tagId = tagId))
                inserted++
            }
        }
        return inserted
    }

    /** 微信账单导入去重：返回 [refs] 中已存在于库里的交易单号 */
    @Query("SELECT sourceRef FROM entries WHERE sourceRef IN (:refs) AND sourceRef IS NOT NULL")
    suspend fun existingSourceRefs(refs: List<String>): List<String>

    /** 撤销一次导入：按本次导入的交易单号集合删除账目（ADR 0012），返回删除笔数 */
    @Query("DELETE FROM entries WHERE sourceRef IN (:refs)")
    suspend fun deleteBySourceRefs(refs: List<String>): Int

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

    @Transaction
    suspend fun deleteWithSnapshot(id: Long): EntrySnapshotRow? {
        val entry = findWithTagsById(id) ?: return null
        deleteById(id)
        return entry.toSnapshotRow()
    }

    @Transaction
    suspend fun restoreSnapshot(snapshot: EntrySnapshotRow) {
        insert(snapshot.entry)
        existingTagIds(snapshot.tagIds).forEach { tagId ->
            insertEntryTag(EntryTagEntity(entryId = snapshot.entry.id, tagId = tagId))
        }
    }

    @Query("SELECT id FROM tags WHERE id IN (:tagIds)")
    suspend fun existingTagIds(tagIds: List<Long>): List<Long>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun findWithTagsById(id: Long): EntryWithTags?

    /** 原子替换所有账目，供演示数据等完整数据集使用。 */
    @Transaction
    suspend fun replaceAll(entries: List<EntryEntity>) {
        clearAll()
        insertAll(entries)
    }

    /** 清空全部账目（ADR 0008：只清账目，保留类别/标签；entry_tags 随外键级联清除） */
    @Query("DELETE FROM entries")
    suspend fun clearAll()

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

data class EntrySnapshotRow(
    val entry: EntryEntity,
    val tags: List<TagEntity>,
    val tagIds: List<Long>,
)

fun EntryWithTags.toSnapshotRow(): EntrySnapshotRow = EntrySnapshotRow(
    entry = entry,
    tags = tags,
    tagIds = tags.map { it.id },
)

fun EntrySnapshotRow.toDomain(): EntrySnapshot = EntrySnapshot(
    entry = Entry(
        id = entry.id,
        type = entry.type,
        amount = entry.amount,
        amountRaw = entry.amountRaw,
        categoryName = entry.categoryName,
        date = entry.date,
        note = entry.note,
        createdAt = entry.createdAt,
        tags = tags.map { it.toDomain() },
        sourceRef = entry.sourceRef,
    ),
    tagIds = tagIds,
)

fun EntrySnapshot.toLocal(): EntrySnapshotRow = EntrySnapshotRow(
    entry = entry.toEntity(),
    tags = entry.tags.map { it.toEntity() },
    tagIds = tagIds,
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
    sourceRef = entry.sourceRef,
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

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** 删除类别：账目存的是类别名快照，已有账目不受影响 */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}
