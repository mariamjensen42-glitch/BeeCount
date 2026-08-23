package com.cycling.beecount.ui.ledger

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerFiltersTest {

    private fun entry(
        id: Long,
        type: EntryType,
        amount: Double,
        categoryName: String,
        note: String,
        date: LocalDate = LocalDate.now(),
        counterparty: String? = null,
        tags: List<Tag> = emptyList(),
    ) = Entry(
        id = id,
        type = type,
        amount = amount,
        amountRaw = amount.toString(),
        categoryName = categoryName,
        date = date,
        note = note,
        tags = tags,
        counterparty = counterparty,
    )

    private val entry1 = entry(
        id = 1,
        type = EntryType.EXPENSE,
        amount = 120.0,
        categoryName = "餐饮",
        note = "昨天和同事吃饭",
        date = LocalDate.now(),
        counterparty = "海底捞",
    )
    private val entry2 = entry(
        id = 2,
        type = EntryType.EXPENSE,
        amount = 35.0,
        categoryName = "交通",
        note = "打车去机场",
        date = LocalDate.now().minusDays(1),
        counterparty = "滴滴出行",
    )
    private val entry3 = entry(
        id = 3,
        type = EntryType.INCOME,
        amount = 2000.0,
        categoryName = "工资",
        note = "工资到账",
        date = LocalDate.now().minusDays(2),
        counterparty = null,
    )

    @Test
    fun `empty filters match everything`() {
        assertTrue(matchesFilters(entry1, LedgerFilters()))
        assertTrue(matchesFilters(entry2, LedgerFilters()))
        assertTrue(matchesFilters(entry3, LedgerFilters()))
    }

    @Test
    fun `keyword fuzzy matches note counterparty category or amountRaw`() {
        assertTrue(matchesFilters(entry1, LedgerFilters(keyword = "同事")))
        assertTrue(matchesFilters(entry1, LedgerFilters(keyword = "海底捞")))
        assertTrue(matchesFilters(entry1, LedgerFilters(keyword = "餐饮")))
        assertTrue(matchesFilters(entry1, LedgerFilters(keyword = "120")))
        assertFalse(matchesFilters(entry1, LedgerFilters(keyword = "不存在的词")))
    }

    @Test
    fun `date range today`() {
        assertTrue(matchesFilters(entry1, LedgerFilters(dateRange = LedgerDateRange.Today)))
        assertFalse(matchesFilters(entry2, LedgerFilters(dateRange = LedgerDateRange.Today)))
    }

    @Test
    fun `custom date range`() {
        val start = LocalDate.now().minusDays(1)
        val end = LocalDate.now()
        val filters = LedgerFilters(dateRange = LedgerDateRange.Custom(start, end))
        assertTrue(matchesFilters(entry1, filters))
        assertTrue(matchesFilters(entry2, filters))
        assertFalse(matchesFilters(entry3, filters))
    }

    @Test
    fun `category filter`() {
        assertTrue(matchesFilters(entry1, LedgerFilters(categoryName = "餐饮")))
        assertFalse(matchesFilters(entry1, LedgerFilters(categoryName = "交通")))
    }

    @Test
    fun `amount range filter`() {
        assertTrue(matchesFilters(entry1, LedgerFilters(minAmount = 100.0, maxAmount = 150.0)))
        assertFalse(matchesFilters(entry1, LedgerFilters(minAmount = 200.0)))
        assertFalse(matchesFilters(entry1, LedgerFilters(maxAmount = 100.0)))
    }

    @Test
    fun `counterparty filter`() {
        assertTrue(matchesFilters(entry1, LedgerFilters(counterparty = "海底捞")))
        assertFalse(matchesFilters(entry1, LedgerFilters(counterparty = "滴滴出行")))
    }

    @Test
    fun `entry type filter`() {
        assertTrue(matchesFilters(entry1, LedgerFilters(type = EntryType.EXPENSE)))
        assertTrue(matchesFilters(entry3, LedgerFilters(type = EntryType.INCOME)))
        assertFalse(matchesFilters(entry1, LedgerFilters(type = EntryType.INCOME)))
    }

    @Test
    fun `combined filters use AND logic`() {
        val filters = LedgerFilters(
            categoryName = "餐饮",
            minAmount = 100.0,
            counterparty = "海底捞",
        )
        assertTrue(matchesFilters(entry1, filters))
        assertFalse(matchesFilters(entry2, filters))
    }

    @Test
    fun `tag filter uses intersection`() {
        val tagTravel = Tag(id = 10L, name = "旅行", color = 0xFF, isCustom = true)
        val tagExpense = Tag(id = 11L, name = "出差", color = 0xFF, isCustom = true)
        val withBoth = entry1.copy(tags = listOf(tagTravel, tagExpense))
        val withOnlyOne = entry2.copy(tags = listOf(tagTravel))

        assertTrue(filterEntriesByTags(listOf(withBoth, withOnlyOne), setOf(10L, 11L)).size == 1)
        assertTrue(filterEntriesByTags(listOf(withBoth, withOnlyOne), setOf(10L)).size == 2)
    }
}
