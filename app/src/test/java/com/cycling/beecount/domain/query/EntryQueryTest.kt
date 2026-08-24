package com.cycling.beecount.domain.query

import com.cycling.beecount.domain.model.Budget
import com.cycling.beecount.domain.model.BudgetException
import com.cycling.beecount.domain.model.BudgetProgress
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.model.QuickTemplate
import com.cycling.beecount.domain.repository.BudgetRepository
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.QuickTemplateRepository
import com.cycling.beecount.domain.repository.TagRepository
import com.cycling.beecount.domain.usecase.FakeEntryRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryQueryTest {

    private val month = YearMonth.of(2026, 8)
    private val year = 2026

    /** 只实现 observe 相关的 CategoryRepository，供 EntryQuery 构造使用 */
    private class StubCategoryRepository(private val categories: List<Category> = emptyList()) : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = flowOf(categories)
        override suspend fun create(name: String, type: EntryType) = error("not used")
        override suspend fun createChild(parentId: Long, name: String) = error("not used")
        override suspend fun rename(id: Long, name: String) = error("not used")
        override suspend fun deleteWithMerge(id: Long, targetId: Long) = error("not used")
        override suspend fun moveParent(id: Long, parentId: Long?) = error("not used")
        override suspend fun updateIcon(id: Long, icon: String) = error("not used")
        override suspend fun updateColor(id: Long, color: Long) = error("not used")
        override suspend fun updateSortOrder(id: Long, sortOrder: Int) = error("not used")
        override suspend fun updateHidden(id: Long, isHidden: Boolean) = error("not used")
    }

    /** 只实现 observe 相关的 TagRepository，供 EntryQuery 构造使用 */
    private class StubTagRepository(private val tags: List<Tag> = emptyList()) : TagRepository {
        override fun observeAll(): Flow<List<Tag>> = flowOf(tags)
        override suspend fun create(name: String, color: Long) = error("not used")
        override suspend fun rename(id: Long, name: String) = error("not used")
        override suspend fun updateColor(id: Long, color: Long) = error("not used")
        override suspend fun delete(id: Long) = error("not used")
    }

    /** 只实现 observe 相关的 BudgetRepository，供 EntryQuery 构造使用 */
    private class StubBudgetRepository(
        private val budgets: List<Budget> = emptyList(),
        private val progress: List<BudgetProgress> = emptyList(),
    ) : BudgetRepository {
        override fun observeBudgets(): Flow<List<Budget>> = flowOf(budgets)
        override fun observeExceptions(): Flow<List<BudgetException>> = flowOf(emptyList())
        override fun observeProgress(today: LocalDate): Flow<List<BudgetProgress>> = flowOf(progress)
        override suspend fun create(budget: Budget) = error("not used")
        override suspend fun updateAmount(id: Long, amount: Double) = error("not used")
        override suspend fun updateCarryOver(id: Long, carryOver: Boolean) = error("not used")
        override suspend fun updateEnabled(id: Long, enabled: Boolean) = error("not used")
        override suspend fun delete(id: Long) = error("not used")
        override suspend fun addException(date: LocalDate) = error("not used")
        override suspend fun removeException(date: LocalDate) = error("not used")
    }

    /** 只实现 observe 相关的 QuickTemplateRepository，供 EntryQuery 构造使用 */
    private class StubQuickTemplateRepository(
        private val templates: List<QuickTemplate> = emptyList(),
    ) : QuickTemplateRepository {
        override fun observeAll(): Flow<List<QuickTemplate>> = flowOf(templates)
        override suspend fun add(template: QuickTemplate): Long = error("not used")
        override suspend fun update(template: QuickTemplate) = error("not used")
        override suspend fun delete(id: Long) = error("not used")
    }

    private fun query(repo: FakeEntryRepository) = EntryQuery(
        entryRepository = repo,
        categoryRepository = StubCategoryRepository(),
        tagRepository = StubTagRepository(),
        budgetRepository = StubBudgetRepository(),
        quickTemplateRepository = StubQuickTemplateRepository(),
    )

    private fun monthEntry(id: Long, type: EntryType, amount: Double, category: String, day: Int) = Entry(
        id = id,
        type = type,
        amount = amount,
        amountRaw = "$amount",
        categoryName = category,
        date = month.atDay(day),
        note = "",
    )

    // ---------- buildCalendar ----------

    @Test
    fun `buildCalendar zero fills every day and aggregates daily cash flow`() = runTest {
        val result = query(FakeEntryRepository(listOf(
            monthEntry(1, EntryType.EXPENSE, 20.0, "餐饮", 1),
            monthEntry(2, EntryType.EXPENSE, 35.0, "餐饮", 1),
            monthEntry(3, EntryType.INCOME, 500.0, "红包", 2),
        ))).buildCalendar(month).first()

        assertEquals(31, result.days.size)
        assertEquals(55.0, result.days[0].expense, 0.001)
        assertEquals(0.0, result.days[0].income, 0.001)
        assertEquals(2, result.days[0].entryCount)
        assertEquals(0.0, result.days[1].expense, 0.001)
        assertEquals(500.0, result.days[1].income, 0.001)
        assertEquals(0, result.days[2].entryCount)
        assertEquals(55.0, result.expense, 0.001)
        assertEquals(500.0, result.income, 0.001)
    }

    @Test
    fun `buildCalendar queries the selected calendar month`() = runTest {
        val repo = FakeEntryRepository(emptyList())
        query(repo).buildCalendar(month).first()
        assertEquals(LocalDate.of(2026, 8, 1), repo.observedStart)
        assertEquals(LocalDate.of(2026, 8, 31), repo.observedEnd)
    }

    // ---------- buildMonth ----------

    @Test
    fun `buildMonth queries the whole month range`() = runTest {
        val repo = FakeEntryRepository(emptyList())
        query(repo).buildMonth(month).first()
        assertEquals(LocalDate.of(2026, 8, 1), repo.observedStart)
        assertEquals(LocalDate.of(2026, 8, 31), repo.observedEnd)
    }

    @Test
    fun `buildMonth aggregates expense income and entry count`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                monthEntry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                monthEntry(2, EntryType.EXPENSE, 45.0, "餐饮", 1),
                monthEntry(3, EntryType.INCOME, 500.0, "红包", 2),
            )
        )
        val result = query(repo).buildMonth(month).first()
        assertEquals(75.0, result.expense, 0.001)
        assertEquals(500.0, result.income, 0.001)
        assertEquals(3, result.entryCount)
    }

    @Test
    fun `buildMonth ranks categories by expense descending and excludes income`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                monthEntry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                monthEntry(2, EntryType.EXPENSE, 45.0, "餐饮", 1),
                monthEntry(3, EntryType.EXPENSE, 20.0, "交通", 2),
                monthEntry(4, EntryType.INCOME, 500.0, "红包", 2),
            )
        )
        val result = query(repo).buildMonth(month).first()
        assertEquals(listOf("餐饮", "交通"), result.categoryRanks.map { it.name })
        assertEquals(75.0, result.categoryRanks[0].amount, 0.001)
        assertEquals(20.0, result.categoryRanks[1].amount, 0.001)
    }

    @Test
    fun `buildMonth fills every day of month with zero for days without spending`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                monthEntry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                monthEntry(2, EntryType.EXPENSE, 20.0, "交通", 15),
            )
        )
        val result = query(repo).buildMonth(month).first()
        assertEquals(31, result.dailyExpense.size)
        assertEquals(1, result.dailyExpense[0].day)
        assertEquals(30.0, result.dailyExpense[0].amount, 0.001)
        assertEquals(0.0, result.dailyExpense[1].amount, 0.001)
        assertEquals(20.0, result.dailyExpense[14].amount, 0.001)
        assertEquals(0.0, result.dailyExpense[30].amount, 0.001)
    }

    @Test
    fun `buildMonth max daily is the day with highest spending and null when no expense`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                monthEntry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                monthEntry(2, EntryType.EXPENSE, 20.0, "交通", 15),
            )
        )
        val result = query(repo).buildMonth(month).first()
        assertEquals(1, result.maxDaily?.day)
        assertEquals(30.0, result.maxDaily?.amount ?: 0.0, 0.001)

        val incomeOnly = FakeEntryRepository(
            listOf(monthEntry(3, EntryType.INCOME, 500.0, "红包", 2))
        )
        val empty = query(incomeOnly).buildMonth(month).first()
        assertNull(empty.maxDaily)
        assertTrue(empty.dailyExpense.all { it.amount == 0.0 })
    }

    // ---------- buildAnnual ----------

    private fun yearEntry(id: Long, type: EntryType, amount: Double, category: String, month: Int, day: Int) = Entry(
        id = id,
        type = type,
        amount = amount,
        amountRaw = "$amount",
        categoryName = category,
        date = LocalDate.of(year, month, day),
        note = "",
    )

    private val yearlyEntries = listOf(
        yearEntry(1, EntryType.EXPENSE, 100.0, "餐饮", 1, 5),
        yearEntry(2, EntryType.EXPENSE, 50.0, "交通", 1, 20),
        yearEntry(3, EntryType.EXPENSE, 300.0, "购物", 3, 10),
        yearEntry(4, EntryType.INCOME, 1000.0, "红包", 3, 10),
        yearEntry(5, EntryType.EXPENSE, 20.0, "餐饮", 12, 1),
    )

    @Test
    fun `buildAnnual queries the whole year range`() = runTest {
        val repo = FakeEntryRepository(yearlyEntries)
        query(repo).buildAnnual(year).first()
        assertEquals(LocalDate.of(2026, 1, 1), repo.observedStart)
        assertEquals(LocalDate.of(2026, 12, 31), repo.observedEnd)
    }

    @Test
    fun `buildAnnual aggregates year totals and entry count`() = runTest {
        val result = query(FakeEntryRepository(yearlyEntries)).buildAnnual(year).first()
        assertEquals(470.0, result.expense, 0.001)
        assertEquals(1000.0, result.income, 0.001)
        assertEquals(5, result.entryCount)
    }

    @Test
    fun `buildAnnual produces twelve monthly points zero filled`() = runTest {
        val result = query(FakeEntryRepository(yearlyEntries)).buildAnnual(year).first()
        assertEquals(12, result.monthlyExpense.size)
        assertEquals(150.0, result.monthlyExpense[0].amount, 0.001)
        assertEquals(0.0, result.monthlyExpense[1].amount, 0.001)
        assertEquals(300.0, result.monthlyExpense[2].amount, 0.001)
        assertEquals(20.0, result.monthlyExpense[11].amount, 0.001)
    }

    @Test
    fun `buildAnnual builds a zero filled daily heatmap with expense and all entry counts`() = runTest {
        val result = query(FakeEntryRepository(yearlyEntries)).buildAnnual(year).first()

        assertEquals(365, result.dailyHeatmap.size)
        assertEquals(LocalDate.of(2026, 1, 1), result.dailyHeatmap.first().date)
        assertEquals(LocalDate.of(2026, 12, 31), result.dailyHeatmap.last().date)

        val januaryFifth = result.dailyHeatmap.first { it.date == LocalDate.of(2026, 1, 5) }
        assertEquals(100.0, januaryFifth.expense, 0.001)
        assertEquals(1, januaryFifth.entryCount)
        assertTrue(januaryFifth.hasEntries)

        val marchTenth = result.dailyHeatmap.first { it.date == LocalDate.of(2026, 3, 10) }
        assertEquals(300.0, marchTenth.expense, 0.001)
        assertEquals(2, marchTenth.entryCount)
        assertTrue(marchTenth.hasEntries)

        val emptyDay = result.dailyHeatmap.first { it.date == LocalDate.of(2026, 2, 1) }
        assertEquals(0.0, emptyDay.expense, 0.001)
        assertEquals(0, emptyDay.entryCount)
        assertTrue(!emptyDay.hasEntries)
    }

    @Test
    fun `buildAnnual daily heatmap includes leap day`() = runTest {
        val leapYear = 2024
        val leapDayEntry = Entry(
            id = 1,
            type = EntryType.INCOME,
            amount = 100.0,
            amountRaw = "100.0",
            categoryName = "红包",
            date = LocalDate.of(leapYear, 2, 29),
            note = "",
        )

        val result = query(FakeEntryRepository(listOf(leapDayEntry))).buildAnnual(leapYear).first()

        assertEquals(366, result.dailyHeatmap.size)
        val leapDay = result.dailyHeatmap.first { it.date == LocalDate.of(leapYear, 2, 29) }
        assertEquals(0.0, leapDay.expense, 0.001)
        assertEquals(1, leapDay.entryCount)
        assertTrue(leapDay.hasEntries)
    }

    @Test
    fun `buildAnnual ranks categories across the year by expense descending`() = runTest {
        val result = query(FakeEntryRepository(yearlyEntries)).buildAnnual(year).first()
        assertEquals(listOf("购物", "餐饮", "交通"), result.categoryRanks.map { it.name })
    }

    @Test
    fun `buildAnnual highlights busiest month biggest entry and avg daily spending`() = runTest {
        val result = query(FakeEntryRepository(yearlyEntries)).buildAnnual(year).first()
        assertEquals(2026, result.highlights.busiestMonth?.year)
        assertEquals(3, result.highlights.busiestMonth?.monthValue)
        assertEquals(300.0, result.highlights.busiestAmount, 0.001)
        assertEquals("购物", result.highlights.biggestEntry?.categoryName)
        assertEquals(300.0, result.highlights.biggestEntry?.amount ?: 0.0, 0.001)
        // 支出 470 ÷ 有支出的 4 天（1/5、1/20、3/10、12/1）
        assertEquals(117.5, result.highlights.avgDailyExpense, 0.001)
    }

    @Test
    fun `buildAnnual highlights are empty when the year has no expense`() = runTest {
        val repo = FakeEntryRepository(
            listOf(yearEntry(1, EntryType.INCOME, 1000.0, "红包", 6, 1))
        )
        val result = query(repo).buildAnnual(year).first()
        assertNull(result.highlights.busiestMonth)
        assertNull(result.highlights.biggestEntry)
        assertEquals(0.0, result.highlights.avgDailyExpense, 0.001)
        assertEquals(1000.0, result.income, 0.001)
    }

    // ---------- buildGrowth (模块 G) ----------

    @Test
    fun `buildGrowth month queries month range and aggregates spending stats`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                monthEntry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
                monthEntry(2, EntryType.EXPENSE, 30.0, "餐饮", 2),
                monthEntry(3, EntryType.INCOME, 500.0, "红包", 3),
            )
        )
        val result = query(repo).buildGrowth(month).first()
        assertEquals(LocalDate.of(2026, 8, 1), repo.observedStart)
        assertEquals(LocalDate.of(2026, 8, 31), repo.observedEnd)
        assertEquals(2, result.spendingStats.expenseCount)
        assertEquals(30.0, result.spendingStats.avgTicket, 0.001)
        assertEquals(30.0, result.spendingStats.median, 0.001)
        assertEquals(listOf("餐饮"), result.spendingStats.perCategoryCounts.map { it.name })
        assertEquals(30.0, result.spendingStats.maxExpense?.amount ?: 0.0, 0.001)
        assertEquals(500.0, result.spendingStats.maxIncome?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `buildGrowth year queries whole year range`() = runTest {
        val repo = FakeEntryRepository(yearlyEntries)
        query(repo).buildGrowth(year).first()
        assertEquals(LocalDate.of(2026, 1, 1), repo.observedStart)
        assertEquals(LocalDate.of(2026, 12, 31), repo.observedEnd)
    }

    @Test
    fun `buildGrowth computes rigidity and weekday habits`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                // 2026-01-06 周二（工作日），2026-01-10 周六（周末）
                yearEntry(1, EntryType.EXPENSE, 2000.0, "居住", 1, 6),
                yearEntry(2, EntryType.EXPENSE, 500.0, "购物", 1, 10),
            )
        )
        val result = query(repo).buildGrowth(year).first()
        // 居住 = 刚性；购物 = 可变 + 冲动（购物在冲动集合内）
        assertEquals(2000.0, result.rigidity.rigidExpense, 0.001)
        assertEquals(500.0, result.rigidity.variableExpense, 0.001)
        assertEquals(500.0, result.rigidity.impulseExpense, 0.001)
        assertEquals(7, result.weekdayStats.size)
        assertEquals(2000.0, result.weekendVsWeekday.weekdayExpense, 0.001)
        assertEquals(500.0, result.weekendVsWeekday.weekendExpense, 0.001)
    }

    @Test
    fun `buildNetAssetTrend returns cumulative net asset over full history`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                yearEntry(1, EntryType.INCOME, 1000.0, "工资", 1, 5),
                yearEntry(2, EntryType.EXPENSE, 300.0, "购物", 1, 10),
            )
        )
        val trend = query(repo).buildNetAssetTrend().first()
        // 趋势从最早账日（1/5）铺到最晚账日（1/10），每天一个点
        assertEquals(6, trend.points.size)
        assertEquals(1000.0, trend.points[0].netAsset, 0.001)
        assertEquals(700.0, trend.points[5].netAsset, 0.001)
    }

    // ---------- observe forwarding ----------

    @Test
    fun `observeDay forwards to repository`() = runTest {
        val repo = FakeEntryRepository(listOf(monthEntry(1, EntryType.EXPENSE, 20.0, "餐饮", 1)))
        val result = query(repo).observeDay(month.atDay(1)).first()
        assertEquals(1, result.size)
        assertEquals(20.0, result[0].amount, 0.001)
    }

    @Test
    fun `observeDay returns entries on requested day with their tags`() = runTest {
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
        val repo = FakeEntryRepository(listOf(taggedEntry, otherDayEntry))

        val entries = query(repo).observeDay(date).first()

        assertEquals(listOf(taggedEntry), entries)
        assertEquals(listOf(3L), entries.single().tags.map { it.id })
    }

    @Test
    fun `observeRange forwards to repository`() = runTest {
        val repo = FakeEntryRepository(listOf(
            monthEntry(1, EntryType.EXPENSE, 30.0, "餐饮", 1),
            monthEntry(2, EntryType.EXPENSE, 45.0, "餐饮", 15),
            monthEntry(3, EntryType.INCOME, 500.0, "红包", 20),
        ))
        val result = query(repo).observeRange(month.atDay(1), month.atDay(15)).first()
        assertEquals(2, result.size)
    }

    @Test
    fun `observeAllWithTags forwards to repository`() = runTest {
        val repo = FakeEntryRepository(listOf(monthEntry(1, EntryType.EXPENSE, 20.0, "餐饮", 1)))
        val result = query(repo).observeAllWithTags().first()
        assertEquals(1, result.size)
    }

    @Test
    fun `observeCategories and observeTags forward from stub repos`() = runTest {
        val category = Category(id = 1, name = "餐饮", type = EntryType.EXPENSE, isCustom = false)
        val tag = Tag(id = 1, name = "打车", color = 0xFF0000, isCustom = true)
        val query = EntryQuery(
            entryRepository = FakeEntryRepository(emptyList()),
            categoryRepository = StubCategoryRepository(listOf(category)),
            tagRepository = StubTagRepository(listOf(tag)),
            budgetRepository = StubBudgetRepository(),
            quickTemplateRepository = StubQuickTemplateRepository(),
        )
        assertEquals(listOf(category), query.observeCategories().first())
        assertEquals(listOf(tag), query.observeTags().first())
    }

    @Test
    fun `observeBudgets and observeBudgetProgress forward from stub repo`() = runTest {
        val budget = Budget(cycle = com.cycling.beecount.domain.model.BudgetCycle.MONTHLY, amount = 1000.0)
        val progress = BudgetProgress(
            budget = budget,
            period = com.cycling.beecount.domain.model.BudgetPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
            spent = 50.0,
            base = 1000.0,
            remainingDays = 31,
        )
        val repo = StubBudgetRepository(listOf(budget), listOf(progress))
        val query = EntryQuery(
            entryRepository = FakeEntryRepository(emptyList()),
            categoryRepository = StubCategoryRepository(),
            tagRepository = StubTagRepository(),
            budgetRepository = repo,
            quickTemplateRepository = StubQuickTemplateRepository(),
        )
        assertEquals(listOf(budget), query.observeBudgets().first())
        assertEquals(listOf(progress), query.observeBudgetProgress(LocalDate.of(2026, 8, 1)).first())
    }
}
