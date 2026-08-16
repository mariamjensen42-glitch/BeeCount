package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveEntriesOnUseCaseTest {

    @Test
    fun `returns entries on requested day with their tags`() = runTest {
        val date = LocalDate.of(2026, 8, 15)
        val taggedEntry = Entry(
            id = 5L,
            type = EntryType.EXPENSE,
            amount = 30.0,
            amountRaw = "30",
            categoryName = "交通",
            date = date,
            note = "地铁",
            tags = listOf(Tag(id = 3L, name = "出差", color = 0xFF64B5F6)),
        )
        val otherDayEntry = taggedEntry.copy(id = 6L, date = date.plusDays(1))
        val repository = FakeEntryRepository(listOf(taggedEntry, otherDayEntry))

        val entries = ObserveEntriesOnUseCase(repository)(date).first()

        assertEquals(listOf(taggedEntry), entries)
        assertEquals(listOf(3L), entries.single().tags.map { it.id })
    }
}
