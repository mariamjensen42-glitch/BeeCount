package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteEntryWithUndoUseCaseTest {

    private val entry = Entry(
        id = 42L,
        type = EntryType.EXPENSE,
        amount = 123.45,
        amountRaw = "123块4毛5",
        categoryName = "餐饮",
        date = LocalDate.of(2026, 8, 15),
        note = "和同事午餐",
        createdAt = 1_723_715_200_123L,
        tags = listOf(
            Tag(id = 7L, name = "出差", color = 0xFF64B5F6, isCustom = false),
            Tag(id = 9L, name = "大额", color = 0xFFD4A35A, isCustom = false),
        ),
    )

    @Test
    fun `deleting an entry returns complete snapshot with original tag ids`() = runTest {
        val repository = FakeEntryRepository(listOf(entry))

        val snapshot = DeleteEntryWithUndoUseCase(repository)(entry.id)

        assertEquals(entry, snapshot?.entry)
        assertEquals(listOf(7L, 9L), snapshot?.tagIds)
        assertNull(repository.storedEntries.firstOrNull { it.id == entry.id })
    }

    @Test
    fun `restore writes back original id fields and tag associations`() = runTest {
        val repository = FakeEntryRepository(listOf(entry))
        val delete = DeleteEntryWithUndoUseCase(repository)
        val restore = RestoreEntryUseCase(repository)

        val snapshot = requireNotNull(delete(entry.id))
        restore(snapshot)

        assertEquals(snapshot, repository.lastRestoredSnapshot)
        assertEquals(listOf(entry), repository.storedEntries)
    }
}
