package com.cycling.beecount.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetMathTest {

    private fun entry(
        amount: Double,
        categoryName: String,
        date: LocalDate,
        type: EntryType = EntryType.EXPENSE,
    ) = Entry(type = type, amount = amount, amountRaw = "", categoryName = categoryName, date = date, note = "")

    private fun budget(
        cycle: BudgetCycle = BudgetCycle.MONTHLY,
        categoryName: String? = null,
        amount: Double = 1000.0,
        carryOver: Boolean = true,
        lengthDays: Int = 30,
        anchor: LocalDate? = null,
    ) = Budget(
        cycle = cycle,
        lengthDays = lengthDays,
        customAnchor = anchor,
        categoryName = categoryName,
        amount = amount,
        carryOver = carryOver,
    )

    @Test
    fun `月度周期从月初到月末`() {
        val period = BudgetMath.currentPeriod(budget(), LocalDate.of(2026, 7, 15))
        assertEquals(LocalDate.of(2026, 7, 1), period.start)
        assertEquals(LocalDate.of(2026, 7, 31), period.end)
    }

    @Test
    fun `周周期从周一起`() {
        // 2026-07-15 是周三
        val period = BudgetMath.currentPeriod(budget(cycle = BudgetCycle.WEEKLY), LocalDate.of(2026, 7, 15))
        assertEquals(LocalDate.of(2026, 7, 13), period.start)
        assertEquals(LocalDate.of(2026, 7, 19), period.end)
    }

    @Test
    fun `季度周期从季度首月一日`() {
        // 2026-08-10 -> Q3 (7,8,9)
        val period = BudgetMath.currentPeriod(budget(cycle = BudgetCycle.QUARTERLY), LocalDate.of(2026, 8, 10))
        assertEquals(LocalDate.of(2026, 7, 1), period.start)
        assertEquals(LocalDate.of(2026, 9, 30), period.end)
    }

    @Test
    fun `年度周期从一月一日`() {
        val period = BudgetMath.currentPeriod(budget(cycle = BudgetCycle.ANNUAL), LocalDate.of(2026, 5, 5))
        assertEquals(LocalDate.of(2026, 1, 1), period.start)
        assertEquals(LocalDate.of(2026, 12, 31), period.end)
    }

    @Test
    fun `自定义天数周期以锚点起算`() {
        val b = budget(cycle = BudgetCycle.CUSTOM_DAYS, lengthDays = 10, anchor = LocalDate.of(2026, 7, 1))
        // 2026-07-26 距锚点 25 天 -> 第 2 个周期 (21..30)
        val period = BudgetMath.currentPeriod(b, LocalDate.of(2026, 7, 26))
        assertEquals(LocalDate.of(2026, 7, 21), period.start)
        assertEquals(LocalDate.of(2026, 7, 30), period.end)
    }

    @Test
    fun `分类维度精确匹配与子分类前缀匹配`() {
        assertTrue(BudgetMath.matchesCategory("餐饮", "餐饮"))
        assertTrue(BudgetMath.matchesCategory("餐饮·外卖", "餐饮"))
        assertTrue(BudgetMath.matchesCategory("餐饮·外卖", null))
        assertFalse(BudgetMath.matchesCategory("餐饮窗", "餐饮")) // 不误伤同名前缀词
        assertFalse(BudgetMath.matchesCategory("购物", "餐饮"))
    }

    @Test
    fun `spent 排除例外日、中性与收入，只统计区间内对应分类支出`() {
        val entries = listOf(
            entry(50.0, "餐饮·外卖", LocalDate.of(2026, 7, 10)),
            entry(30.0, "餐饮", LocalDate.of(2026, 7, 11)),
            entry(200.0, "购物", LocalDate.of(2026, 7, 12)), // 不同分类
            entry(99.0, "餐饮·堂食", LocalDate.of(2026, 7, 13)), // 例外日，排除
            entry(500.0, "餐饮", LocalDate.of(2026, 6, 25)), // 区间外
            entry(88.0, "餐饮", LocalDate.of(2026, 7, 14), type = EntryType.INCOME), // 收入不计
            entry(77.0, "餐饮", LocalDate.of(2026, 7, 15), type = EntryType.NEUTRAL), // 中性不计
        )
        val period = BudgetPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
        val exceptions = setOf(LocalDate.of(2026, 7, 13))
        val spent = BudgetMath.spent(entries, period, "餐饮", exceptions)
        // 外卖50 + 餐饮30 = 80（外卖属于餐饮前缀）
        assertEquals(80.0, spent, 1e-6)
    }

    @Test
    fun `spent 总预算统计所有支出`() {
        val entries = listOf(
            entry(50.0, "餐饮", LocalDate.of(2026, 7, 5)),
            entry(30.0, "购物", LocalDate.of(2026, 7, 6)),
        )
        val period = BudgetPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
        assertEquals(80.0, BudgetMath.spent(entries, period, null, emptySet()), 1e-6)
    }

    @Test
    fun `spent 退款从支出中冲减`() {
        val entries = listOf(
            entry(50.0, "餐饮", LocalDate.of(2026, 7, 5)),
            entry(30.0, "餐饮", LocalDate.of(2026, 7, 6)),
            entry(20.0, "餐饮", LocalDate.of(2026, 7, 7), type = EntryType.REFUND),
        )
        val period = BudgetPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
        assertEquals(60.0, BudgetMath.spent(entries, period, "餐饮", emptySet()), 1e-6)
    }

    @Test
    fun `结转结入上期正向结余`() {
        val b = budget(amount = 1000.0, carryOver = true)
        val today = LocalDate.of(2026, 7, 15)
        // 上期（6 月）支出 300
        val spentOfPeriod: (BudgetPeriod) -> Double = { p ->
            if (p.start == LocalDate.of(2026, 6, 1)) 300.0 else 0.0
        }
        // 上期结余 = 1000 - 300 = 700
        assertEquals(700.0, BudgetMath.carryIn(b, today, spentOfPeriod), 1e-6)
    }

    @Test
    fun `超支不反向扣，结转记为零`() {
        val b = budget(amount = 1000.0, carryOver = true)
        val today = LocalDate.of(2026, 7, 15)
        val spentOfPeriod: (BudgetPeriod) -> Double = { p ->
            if (p.start == LocalDate.of(2026, 6, 1)) 1500.0 else 0.0
        }
        assertEquals(0.0, BudgetMath.carryIn(b, today, spentOfPeriod), 1e-6)
    }

    @Test
    fun `关闭结转则不结入`() {
        val b = budget(amount = 1000.0, carryOver = false)
        assertEquals(0.0, BudgetMath.carryIn(b, LocalDate.of(2026, 7, 15)) { 300.0 }, 1e-6)
    }

    @Test
    fun `剩余天数含今天`() {
        val period = BudgetPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
        assertEquals(17, BudgetMath.remainingDays(period, LocalDate.of(2026, 7, 15)))
        assertEquals(0, BudgetMath.remainingDays(period, LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun `progress 计算占比超支与日均`() {
        val base = 1000.0
        val p = BudgetProgress(
            budget = budget(amount = 1000.0),
            period = BudgetPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
            spent = 1200.0,
            base = base + 700.0, // 结转注入的 base 为 1700
            remainingDays = 17,
        )
        assertEquals(1200.0 / 1700.0, p.fraction, 1e-6)
        assertFalse(p.isOver) // 1200 < 1700 未超
        assertTrue(p.dailyAllowance > 0)
    }

    @Test
    fun `超支时 available 为负与 isOver 为真`() {
        val p = BudgetProgress(
            budget = budget(amount = 500.0),
            period = BudgetPeriod(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
            spent = 600.0,
            base = 500.0,
            remainingDays = 17,
        )
        assertTrue(p.isOver)
        assertEquals(100.0, p.overAmount, 1e-6)
        assertEquals(0.0, p.dailyAllowance, 1e-6)
    }
}
