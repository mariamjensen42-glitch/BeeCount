package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.model.TimeSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthAggregatorTest {

    private fun entry(
        id: Long,
        type: EntryType,
        amount: Double,
        category: String,
        date: LocalDate,
        hour: Int = 10,
    ): Entry {
        val createdAt = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0))
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Entry(
            id = id,
            type = type,
            amount = amount,
            amountRaw = "$amount",
            categoryName = category,
            date = date,
            note = "",
            createdAt = createdAt,
        )
    }

    // ---------- categoryCounts (176) ----------

    @Test
    fun `categoryCounts counts only expense entries by category descending`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 30.0, "餐饮", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 20.0, "餐饮", LocalDate.of(2026, 8, 2)),
            entry(3, EntryType.EXPENSE, 15.0, "交通", LocalDate.of(2026, 8, 3)),
            entry(4, EntryType.INCOME, 500.0, "红包", LocalDate.of(2026, 8, 4)),
            entry(5, EntryType.REFUND, 10.0, "餐饮", LocalDate.of(2026, 8, 5)),
        )
        val counts = GrowthAggregator.categoryCounts(entries)
        assertEquals(listOf("餐饮", "交通"), counts.map { it.name })
        assertEquals(2, counts[0].count)
        assertEquals(1, counts[1].count)
    }

    // ---------- spendingStats (176-180) ----------

    @Test
    fun `spendingStats computes count avgTicket median and max`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 10.0, "餐饮", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 20.0, "餐饮", LocalDate.of(2026, 8, 2)),
            entry(3, EntryType.EXPENSE, 30.0, "交通", LocalDate.of(2026, 8, 3)),
            entry(4, EntryType.INCOME, 500.0, "红包", LocalDate.of(2026, 8, 4)),
        )
        val stats = GrowthAggregator.spendingStats(entries)
        assertEquals(3, stats.expenseCount)
        assertEquals(20.0, stats.avgTicket, 0.001)
        assertEquals(20.0, stats.median, 0.001)
        assertEquals(30.0, stats.maxExpense?.amount ?: 0.0, 0.001)
        assertEquals(500.0, stats.maxIncome?.amount ?: 0.0, 0.001)
    }

    @Test
    fun `spendingStats median averages the middle two for even count`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 10.0, "餐饮", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 30.0, "交通", LocalDate.of(2026, 8, 3)),
        )
        val stats = GrowthAggregator.spendingStats(entries)
        assertEquals(2, stats.expenseCount)
        assertEquals(20.0, stats.median, 0.001)
    }

    @Test
    fun `spendingStats variance and stddev for known values`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 10.0, "餐饮", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 20.0, "餐饮", LocalDate.of(2026, 8, 2)),
            entry(3, EntryType.EXPENSE, 30.0, "交通", LocalDate.of(2026, 8, 3)),
        )
        // 均值 20，方差 = ((10-20)^2 + 0 + (30-20)^2)/3 = 200/3 ≈ 66.667
        val stats = GrowthAggregator.spendingStats(entries)
        assertEquals(66.667, stats.variance, 0.001)
        assertEquals(8.1649, stats.stdDev, 0.001)
        // 变异系数 = stddev / avg = 8.1649 / 20
        assertEquals(0.4082, stats.coefficientOfVariation, 0.001)
    }

    @Test
    fun `spendingStats is empty and zero when no expense`() {
        val entries = listOf(
            entry(1, EntryType.INCOME, 500.0, "红包", LocalDate.of(2026, 8, 4)),
        )
        val stats = GrowthAggregator.spendingStats(entries)
        assertEquals(0, stats.expenseCount)
        assertEquals(0.0, stats.avgTicket, 0.001)
        assertEquals(0.0, stats.median, 0.001)
        assertEquals(0.0, stats.variance, 0.001)
        assertEquals(0.0, stats.stdDev, 0.001)
        assertEquals(null, stats.maxExpense)
    }

    // ---------- timeSlots (182) ----------

    @Test
    fun `timeSlots group expenses by createdAt hour into morning afternoon evening`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 30.0, "餐饮", LocalDate.of(2026, 8, 1), hour = 9),
            entry(2, EntryType.EXPENSE, 50.0, "餐饮", LocalDate.of(2026, 8, 2), hour = 14),
            entry(3, EntryType.EXPENSE, 20.0, "交通", LocalDate.of(2026, 8, 3), hour = 20),
            entry(4, EntryType.INCOME, 500.0, "红包", LocalDate.of(2026, 8, 4), hour = 10),
        )
        val slots = GrowthAggregator.timeSlots(entries).associateBy { it.slot }
        assertEquals(30.0, slots[TimeSlot.MORNING]?.amount ?: 0.0, 0.001)
        assertEquals(50.0, slots[TimeSlot.AFTERNOON]?.amount ?: 0.0, 0.001)
        assertEquals(20.0, slots[TimeSlot.EVENING]?.amount ?: 0.0, 0.001)
        assertEquals(0.3f, slots[TimeSlot.MORNING]?.fraction ?: 0f, 0.001f)
        assertEquals(0.5f, slots[TimeSlot.AFTERNOON]?.fraction ?: 0f, 0.001f)
        assertEquals(0.2f, slots[TimeSlot.EVENING]?.fraction ?: 0f, 0.001f)
    }

    // ---------- weekdayStats / weekendVsWeekday (181) ----------

    @Test
    fun `weekendVsWeekday compares per day average spending`() {
        // 2026-08-01 是周六，08-03 是周一
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 100.0, "餐饮", LocalDate.of(2026, 8, 1)),  // Sat
            entry(2, EntryType.EXPENSE, 100.0, "交通", LocalDate.of(2026, 8, 1)),  // Sat
            entry(3, EntryType.EXPENSE, 50.0, "餐饮", LocalDate.of(2026, 8, 3)),   // Mon
        )
        val wv = GrowthAggregator.weekendVsWeekday(entries)
        assertEquals(200.0, wv.weekendExpense, 0.001)
        assertEquals(200.0, wv.weekendPerDay, 0.001)
        assertEquals(50.0, wv.weekdayExpense, 0.001)
        assertEquals(50.0, wv.weekdayPerDay, 0.001)
        // (200 - 50) / 50 * 100 = 300
        assertEquals(300.0, wv.extraPercent, 0.001)
    }

    @Test
    fun `weekdayStats aggregates to day of week with expense net of refund`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 100.0, "餐饮", LocalDate.of(2026, 8, 1)),   // Sat
            entry(2, EntryType.REFUND, 30.0, "餐饮", LocalDate.of(2026, 8, 1)),     // Sat
        )
        val saturday = GrowthAggregator.weekdayStats(entries).first { it.dayOfWeek == java.time.DayOfWeek.SATURDAY }
        assertEquals(70.0, saturday.expense, 0.001)
        assertEquals(1, saturday.count)
    }

    // ---------- rigidity (183-185) ----------

    @Test
    fun `rigidity classifies rigid variable and impulse by category leaf`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 2000.0, "居住", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 300.0, "医疗", LocalDate.of(2026, 8, 2)),
            entry(3, EntryType.EXPENSE, 500.0, "购物", LocalDate.of(2026, 8, 3)),
            entry(4, EntryType.EXPENSE, 200.0, "娱乐", LocalDate.of(2026, 8, 4)),
            entry(5, EntryType.EXPENSE, 100.0, "其他", LocalDate.of(2026, 8, 5)),
        )
        val rig = GrowthAggregator.rigidity(entries)
        assertEquals(3100.0, rig.totalExpense, 0.001)
        // 刚性 = 居住 2000 + 医疗 300 = 2300
        assertEquals(2300.0, rig.rigidExpense, 0.001)
        assertEquals(2300 / 3100.0, rig.rigidRatio.toDouble(), 0.001)
        // 可变 = 购物 500 + 娱乐 200 + 其他 100 = 800
        assertEquals(800.0, rig.variableExpense, 0.001)
        // 冲动 = 购物 500 + 娱乐 200 = 700
        assertEquals(700.0, rig.impulseExpense, 0.001)
        assertEquals(700 / 3100.0, rig.impulseRatio.toDouble(), 0.001)
    }

    @Test
    fun `rigidity treats child categories by leaf name`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 1000.0, "居住·房租", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 200.0, "居住·水电", LocalDate.of(2026, 8, 2)),
        )
        val rig = GrowthAggregator.rigidity(entries)
        assertEquals(1200.0, rig.rigidExpense, 0.001)
    }

    // ---------- netAssetTrend (186-187) ----------

    @Test
    fun `netAssetTrend accumulates income minus expense daily`() {
        val entries = listOf(
            entry(1, EntryType.INCOME, 1000.0, "工资", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 300.0, "购物", LocalDate.of(2026, 8, 2)),
            entry(3, EntryType.EXPENSE, 200.0, "餐饮", LocalDate.of(2026, 8, 3)),
        )
        val trend = GrowthAggregator.netAssetTrend(entries)
        assertEquals(3, trend.points.size)
        assertEquals(1000.0, trend.points[0].netAsset, 0.001)
        assertEquals(700.0, trend.points[1].netAsset, 0.001)
        assertEquals(500.0, trend.points[2].netAsset, 0.001)
        // 净资产为正，负债率代理为 0
        assertEquals(0f, trend.points[2].deficitRatio, 0.001f)
    }

    @Test
    fun `netAssetTrend refunds are added back as net cash inflow`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 100.0, "购物", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.REFUND, 40.0, "购物", LocalDate.of(2026, 8, 2)),
        )
        val trend = GrowthAggregator.netAssetTrend(entries)
        assertEquals(-100.0, trend.points[0].netAsset, 0.001)
        assertEquals(-60.0, trend.points[1].netAsset, 0.001)
    }

    @Test
    fun `netAssetTrend deficit ratio is positive when cumulative spend exceeds income`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 500.0, "购物", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 500.0, "餐饮", LocalDate.of(2026, 8, 2)),
            entry(3, EntryType.INCOME, 300.0, "红包", LocalDate.of(2026, 8, 3)),
        )
        val trend = GrowthAggregator.netAssetTrend(entries)
        val last = trend.points.last()
        assertEquals(-700.0, last.netAsset, 0.001)
        // 累计支出 1000，超支 700 → 负债率 0.7
        assertEquals(0.7f, last.deficitRatio, 0.001f)
    }

    // ---------- health (188) ----------

    @Test
    fun `health scores higher with more savings and lower volatility`() {
        val better = listOf(
            entry(1, EntryType.INCOME, 10000.0, "工资", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 5000.0, "居住", LocalDate.of(2026, 8, 2)),
            entry(3, EntryType.EXPENSE, 500.0, "餐饮", LocalDate.of(2026, 8, 3)),
        )
        val worse = listOf(
            entry(1, EntryType.INCOME, 10000.0, "工资", LocalDate.of(2026, 8, 1)),
            entry(2, EntryType.EXPENSE, 9000.0, "购物", LocalDate.of(2026, 8, 2)),
            entry(3, EntryType.EXPENSE, 9000.0, "娱乐", LocalDate.of(2026, 8, 3)),
        )
        val betterScore = GrowthAggregator.health(better, 31).total
        val worseScore = GrowthAggregator.health(worse, 31).total
        assertTrue(betterScore > worseScore)
        assertEquals(5, GrowthAggregator.health(better, 31).metrics.size)
    }

    @Test
    fun `health is zero-scaled and bounded to 100`() {
        // 收入 0 → 储蓄率 0；全收入、无支出 → 各维度封顶但覆盖率受 periodDays 影响
        val rich = listOf(
            entry(1, EntryType.INCOME, 10000.0, "工资", LocalDate.of(2026, 8, 1)),
        )
        val score = GrowthAggregator.health(rich, 31)
        assertTrue(score.total in 0..100)
        assertTrue(score.grade.isNotEmpty())
    }

    // ---------- tagCloud (标签云统计视图) ----------

    @Test
    fun `tagCloud aggregates expense amount per tag sorted descending`() {
        val food = Tag(id = 1, name = "餐饮", color = 0xFF81C784)
        val transport = Tag(id = 2, name = "出行", color = 0xFF64B5F6)
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 30.0, "早餐", LocalDate.of(2026, 8, 1)).copy(tags = listOf(food)),
            entry(2, EntryType.EXPENSE, 60.0, "打车", LocalDate.of(2026, 8, 2)).copy(tags = listOf(transport)),
            entry(3, EntryType.EXPENSE, 20.0, "午餐", LocalDate.of(2026, 8, 3)).copy(tags = listOf(food)),
            entry(4, EntryType.INCOME, 500.0, "红包", LocalDate.of(2026, 8, 4)).copy(tags = listOf(food)),
        )
        val cloud = GrowthAggregator.tagCloud(entries)
        // 收入不计入；按支出金额降序：出行 60 > 餐饮 50（餐饮 30+20）
        assertEquals(listOf(transport.name, food.name), cloud.map { it.name })
        assertEquals(60.0, cloud[0].amount, 0.001)
        assertEquals(2, cloud[1].count)
        assertEquals(0xFF81C784, cloud[1].color)
    }

    @Test
    fun `tagCloud is empty when no expense has a tag`() {
        val entries = listOf(
            entry(1, EntryType.EXPENSE, 30.0, "早餐", LocalDate.of(2026, 8, 1)),
        )
        assertEquals(emptyList<Any>(), GrowthAggregator.tagCloud(entries))
    }

    // ---------- annualReport (189) ----------

    @Test
    fun `annualReport builds overview habit and advice sections`() {
        val entries = listOf(
            entry(1, EntryType.INCOME, 100000.0, "工资", LocalDate.of(2026, 1, 5)),
            entry(2, EntryType.EXPENSE, 20000.0, "居住", LocalDate.of(2026, 1, 6)),
            entry(3, EntryType.EXPENSE, 30000.0, "购物", LocalDate.of(2026, 7, 6)),
            entry(4, EntryType.EXPENSE, 1000.0, "餐饮", LocalDate.of(2026, 7, 6)),
        )
        val analytics = GrowthAggregator.buildAnalysis(entries, 365)
        val income = AnalyticsAggregator.totals(entries).income
        val report = GrowthAggregator.annualReport(2026, analytics, income, AnalyticsAggregator.totals(entries).expense)
        assertEquals(2026, report.year)
        assertTrue(report.sections.any { it.title == "收支概览" })
        assertTrue(report.sections.any { it.title == "消费习惯" })
        assertTrue(report.sections.any { it.title == "年度健康评分" })
        assertTrue(report.sections.any { it.title == "复盘建议" })
    }
}
