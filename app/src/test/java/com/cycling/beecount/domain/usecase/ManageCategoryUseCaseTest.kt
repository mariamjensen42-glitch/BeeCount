package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageCategoryUseCaseTest {

    @Test
    fun `create 会去除首尾空白`() = runTest {
        val repo = RecordingStub()
        val useCase = ManageCategoryUseCase(repo)
        useCase.create("  餐饮  ", EntryType.EXPENSE)
        assertEquals("餐饮", repo.createdName)
    }

    @Test
    fun `类别名包含路径分隔符时拒绝`() = runTest {
        val useCase = ManageCategoryUseCase(RecordingStub())
        val thrown = runCatching { useCase.create("外卖·夜宵", EntryType.EXPENSE) }
        assertTrue(thrown.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `空类别名拒绝`() = runTest {
        val useCase = ManageCategoryUseCase(RecordingStub())
        val thrown = runCatching { useCase.create("   ", EntryType.EXPENSE) }
        assertTrue(thrown.exceptionOrNull() is IllegalArgumentException)
    }
}

/** 最小替身：记录 create 入参，其余方法空实现 */
private class RecordingStub : CategoryRepository {
    var createdName: String = ""
    override fun observeAll() = flowOf<List<Category>>(emptyList())
    override suspend fun create(name: String, type: EntryType): Long {
        createdName = name
        return 42L
    }

    override suspend fun createChild(parentId: Long, name: String): Long = 43L
    override suspend fun rename(id: Long, name: String) {}
    override suspend fun deleteWithMerge(id: Long, targetId: Long) {}
    override suspend fun moveParent(id: Long, parentId: Long?) {}
    override suspend fun updateIcon(id: Long, icon: String) {}
    override suspend fun updateColor(id: Long, color: Long) {}
    override suspend fun updateSortOrder(id: Long, sortOrder: Int) {}
    override suspend fun updateHidden(id: Long, isHidden: Boolean) {}
}
