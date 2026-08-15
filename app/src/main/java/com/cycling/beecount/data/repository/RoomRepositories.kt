package com.cycling.beecount.data.repository

import com.cycling.beecount.data.local.CategoryDao
import com.cycling.beecount.data.local.CategoryEntity
import com.cycling.beecount.data.local.EntryDao
import com.cycling.beecount.data.local.EntryEntity
import com.cycling.beecount.data.local.TagDao
import com.cycling.beecount.data.local.TagEntity
import com.cycling.beecount.data.local.toDomain
import com.cycling.beecount.data.local.toEntity
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.TagRepository
import com.cycling.beecount.domain.repository.TodayTotals
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomEntryRepository @Inject constructor(
    private val entryDao: EntryDao,
) : EntryRepository {

    override fun observeEntriesOn(date: LocalDate): Flow<List<Entry>> =
        entryDao.observeEntriesOn(date).map { list -> list.map { it.toDomain() } }

    override fun observeTotalsOn(date: LocalDate): Flow<TodayTotals> =
        entryDao.observeTotalsOn(date).map { it.toDomain() }

    override fun observeAllWithTags(): Flow<List<Entry>> =
        entryDao.observeAllWithTags().map { list -> list.map { it.toDomain() } }

    override suspend fun add(entry: Entry): Long =
        entryDao.insert(entry.toEntity())

    override suspend fun addWithTags(entry: Entry, tagIds: List<Long>): Long =
        entryDao.addWithTags(entry.toEntity(), tagIds)

    override suspend fun delete(id: Long) {
        entryDao.deleteById(id)
    }
}

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun create(name: String, type: EntryType): Long {
        val entity = CategoryEntity(name = name, type = type, isCustom = true)
        return categoryDao.insert(entity)
    }
}

@Singleton
class RoomTagRepository @Inject constructor(
    private val tagDao: TagDao,
) : TagRepository {

    override fun observeAll(): Flow<List<Tag>> =
        tagDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun create(name: String, color: Long): Long =
        tagDao.insert(TagEntity(name = name, color = color, isCustom = true))

    override suspend fun rename(id: Long, name: String) = tagDao.rename(id, name)

    override suspend fun updateColor(id: Long, color: Long) = tagDao.updateColor(id, color)

    override suspend fun delete(id: Long) = tagDao.deleteById(id)
}
