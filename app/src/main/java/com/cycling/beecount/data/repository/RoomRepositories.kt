package com.cycling.beecount.data.repository

import com.cycling.beecount.data.local.CategoryDao
import com.cycling.beecount.data.local.CategoryEntity
import com.cycling.beecount.data.local.EntryDao
import com.cycling.beecount.data.local.EntryEntity
import com.cycling.beecount.data.local.TagDao
import com.cycling.beecount.data.local.TagEntity
import com.cycling.beecount.data.local.toDomain
import com.cycling.beecount.data.local.toEntity
import com.cycling.beecount.data.local.toLocal
import com.cycling.beecount.data.local.toSnapshotRow
import com.cycling.beecount.domain.model.CATEGORY_PATH_SEPARATOR
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.EntrySnapshot
import com.cycling.beecount.domain.repository.TagRepository
import com.cycling.beecount.domain.repository.TodayTotals
import com.cycling.beecount.domain.model.sortCategories
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    override fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<Entry>> =
        entryDao.observeBetween(start, end).map { list -> list.map { it.toDomain() } }

    override suspend fun add(entry: Entry): Long =
        entryDao.insert(entry.toEntity())

    override suspend fun addWithTags(entry: Entry, tagIds: List<Long>): Long =
        entryDao.addWithTags(entry.toEntity(), tagIds)

    override suspend fun updateWithTags(entry: Entry, tagIds: List<Long>) {
        entryDao.updateWithTags(entry.toEntity(), tagIds)
    }

    override suspend fun delete(id: Long) {
        entryDao.deleteById(id)
    }

    override suspend fun deleteWithSnapshot(id: Long): EntrySnapshot? =
        entryDao.deleteWithSnapshot(id)?.toDomain()

    override suspend fun restoreSnapshot(snapshot: EntrySnapshot) {
        entryDao.restoreSnapshot(snapshot.toLocal())
    }

    override suspend fun replaceAll(entries: List<Entry>) {
        entryDao.replaceAll(entries.map { it.toEntity() })
    }

    override suspend fun clearAll() {
        entryDao.clearAll()
    }

    override suspend fun findExistingSourceRefs(refs: Collection<String>): Set<String> {
        if (refs.isEmpty()) return emptySet()
        return entryDao.existingSourceRefs(refs.toList()).toSet()
    }

    override suspend fun addAll(entries: List<Entry>): Int {
        if (entries.isEmpty()) return 0
        return entryDao.insertAllIgnoreConflict(entries.map { it.toEntity() }).count { it > 0 }
    }

    override suspend fun addAllWithTag(entries: List<Entry>, tag: Tag): Int {
        if (entries.isEmpty()) return 0
        return entryDao.insertAllWithTag(entries.map { it.toEntity() }, tag.id)
    }

    override suspend fun deleteBySourceRefs(refs: Collection<String>): Int {
        if (refs.isEmpty()) return 0
        return entryDao.deleteBySourceRefs(refs.toList())
    }
}

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val entryDao: EntryDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        combine(categoryDao.observeAll(), entryDao.observeCategoryUsageCounts()) { entities, usage ->
            val counts = usage.associate { it.name to it.cnt }
            sortCategories(entities.map { it.toDomain() }) { counts[it.name] ?: 0 }
        }

    override suspend fun create(name: String, type: EntryType): Long {
        val entity = CategoryEntity(name = name, type = type, isCustom = true)
        return categoryDao.insert(entity)
    }

    override suspend fun createChild(parentId: Long, name: String): Long {
        val parent = categoryDao.getById(parentId)
        requireNotNull(parent) { "父分类不存在" }
        require(parent.parentId == null) { "仅一级分类可承载子分类" }
        val childName = buildChildName(parent.name, name)
        return categoryDao.insert(
            CategoryEntity(
                name = childName,
                type = parent.type,
                isCustom = true,
                parentId = parent.id,
                color = defaultChildColor(parent),
            ),
        )
    }

    override suspend fun rename(id: Long, name: String) {
        val category = categoryDao.getById(id) ?: return
        val newLeaf = name.trim()
        if (category.parentId == null) {
            // 一级分类改名：自身账目快照精确改写 + 子分类记录名与账目快照前缀级联改写
            categoryDao.rename(id, newLeaf)
            entryDao.remapCategoryNameExact(category.name, newLeaf)
            if (category.name != newLeaf) {
                val oldPrefix = category.name + CATEGORY_PATH_SEPARATOR
                val newPrefix = newLeaf + CATEGORY_PATH_SEPARATOR
                // 子分类行名改为「新父名·叶子」
                categoryDao.getAll()
                    .filter { it.parentId == category.id }
                    .forEach { child ->
                        categoryDao.rename(
                            child.id,
                            newPrefix + child.name.substringAfter(CATEGORY_PATH_SEPARATOR),
                        )
                    }
                entryDao.remapCategoryNamePrefix(oldPrefix, newPrefix)
            }
        } else {
            // 子分类改名：保留父前缀，仅换叶子
            val parentPrefix = category.name.substringBeforeLast(CATEGORY_PATH_SEPARATOR)
            val newFull = buildChildName(parentPrefix, newLeaf)
            categoryDao.rename(id, newFull)
            entryDao.remapCategoryNameExact(category.name, newFull)
        }
    }

    override suspend fun deleteWithMerge(id: Long, targetId: Long) {
        val all = categoryDao.getAll()
        val category = all.firstOrNull { it.id == id } ?: return
        val target = all.firstOrNull { it.id == targetId }
            ?: throw IllegalArgumentException("目标分类不存在")
        if (category.id == target.id) {
            // 目标即自身：不允许归并到自身（退化为仅删除分类记录）
            throw IllegalArgumentException("目标分类不能与要删除的分类相同")
        }
        // 收集被删分类及其所有子分类的全名，逐一归并到 target.name
        val doomed = mutableListOf(category)
        if (category.parentId == null) {
            doomed += all.filter { it.parentId == category.id }
        }
        doomed.forEach { c ->
            entryDao.remapCategoryNameExact(c.name, target.name)
        }
        doomed.forEach { c -> categoryDao.deleteById(c.id) }
    }

    override suspend fun moveParent(id: Long, parentId: Long?) {
        val category = categoryDao.getById(id) ?: return
        val newParent = parentId?.let { categoryDao.getById(it) }
        if (parentId == null) {
            // 子分类上移为一级分类：去掉路径前缀
            val newName = category.name.substringAfterLast(CATEGORY_PATH_SEPARATOR)
            val sameTypeName = categoryDao.getAll()
                .firstOrNull {
                    it.type == category.type && it.parentId == null && it.id != category.id &&
                        it.name == newName
                }
            require(sameTypeName == null) { "已存在同名一级分类：「$newName」" }
            categoryDao.updateParent(id, null)
            categoryDao.rename(id, newName)
            entryDao.remapCategoryNameExact(category.name, newName)
        } else {
            requireNotNull(newParent) { "目标父分类不存在" }
            require(newParent.parentId == null) { "仅一级分类可承载子分类" }
            require(newParent.id != category.id) { "不能把分类移动到自身下" }
            val newName = buildChildName(newParent.name, category.name.substringAfterLast(CATEGORY_PATH_SEPARATOR))
            categoryDao.updateParent(id, newParent.id)
            categoryDao.rename(id, newName)
            entryDao.remapCategoryNameExact(category.name, newName)
        }
    }

    override suspend fun updateIcon(id: Long, icon: String) = categoryDao.updateIcon(id, icon)

    override suspend fun updateColor(id: Long, color: Long) = categoryDao.updateColor(id, color)

    override suspend fun updateSortOrder(id: Long, sortOrder: Int) = categoryDao.updateSortOrder(id, sortOrder)

    override suspend fun updateHidden(id: Long, isHidden: Boolean) = categoryDao.updateHidden(id, isHidden)

    private fun buildChildName(parentName: String, leaf: String): String =
        parentName + CATEGORY_PATH_SEPARATOR + leaf.trim()

    /** 子分类默认继承父分类颜色，视觉上从属清晰 */
    private fun defaultChildColor(parent: CategoryEntity): Long = parent.color
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
