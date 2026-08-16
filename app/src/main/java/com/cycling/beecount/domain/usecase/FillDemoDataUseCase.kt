package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * 用例：用近五年的确定性样本替换全部账目，便于体验图表分析。
 * 样本不包含未来日期，保留用户的类别和标签元数据。
 */
class FillDemoDataUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {

    suspend operator fun invoke(today: LocalDate = LocalDate.now()) {
        entryRepository.replaceAll(buildEntries(today))
    }

    internal fun buildEntries(today: LocalDate): List<Entry> {
        val entries = mutableListOf<Entry>()
        val recurringExpenses = listOf(
            DemoPattern(1, EntryType.EXPENSE, 26.0, "餐饮", "早餐和咖啡"),
            DemoPattern(3, EntryType.EXPENSE, 18.0, "交通", "通勤地铁"),
            DemoPattern(5, EntryType.EXPENSE, 32.0, "餐饮", "工作日午餐"),
            DemoPattern(8, EntryType.EXPENSE, 49.0, "购物", "生活用品"),
            DemoPattern(11, EntryType.EXPENSE, 42.0, "娱乐", "周末电影"),
            DemoPattern(14, EntryType.EXPENSE, 16.0, "交通", "共享单车"),
            DemoPattern(17, EntryType.EXPENSE, 128.0, "餐饮", "朋友聚餐"),
            DemoPattern(20, EntryType.EXPENSE, 35.0, "医疗", "常用药品"),
            DemoPattern(22, EntryType.EXPENSE, 68.0, "购物", "线上购买"),
            DemoPattern(25, EntryType.EXPENSE, 88.0, "娱乐", "展览和演出"),
            DemoPattern(28, EntryType.EXPENSE, 30.0, "餐饮", "周末早午餐"),
        )
        val startYear = today.year - 4
        for (year in startYear..today.year) {
            val lastMonth = if (year == today.year) today.monthValue else 12
            for (month in 1..lastMonth) {
                val monthStart = LocalDate.of(year, month, 1)
                recurringExpenses.forEachIndexed { index, pattern ->
                    val date = monthStart.withDayOfMonth(pattern.day.coerceAtMost(monthStart.lengthOfMonth()))
                    if (date <= today) {
                        val amount = pattern.amount + (year - startYear) * 4.0 + month * (index % 4 + 1) * 2.5
                        entries += demoEntry(pattern.type, amount, pattern.category, date, pattern.note)
                    }
                }
                addMonthlyEntries(entries, monthStart, today, year - startYear)
            }
            addAnnualEntries(entries, year, today)
        }
        addDailyActivity(entries, startYear, today)
        return entries.take(DEMO_ENTRY_COUNT).mapIndexed { index, entry ->
            entry.copy(createdAt = entry.date.toEpochDay() * 86_400_000L + index)
        }
    }

    private fun addMonthlyEntries(
        entries: MutableList<Entry>,
        monthStart: LocalDate,
        today: LocalDate,
        yearOffset: Int,
    ) {
        fun add(day: Int, type: EntryType, amount: Double, category: String, note: String) {
            val date = monthStart.withDayOfMonth(day.coerceAtMost(monthStart.lengthOfMonth()))
            if (date <= today) entries += demoEntry(type, amount, category, date, note)
        }
        add(1, EntryType.EXPENSE, 2200.0 + yearOffset * 150.0, "居住", "房租")
        add(2, EntryType.EXPENSE, 68.0 + monthStart.monthValue * 2.0, "居住", "水电燃气")
        add(6, EntryType.EXPENSE, 25.0, "教育", "在线课程订阅")
        add(8, EntryType.INCOME, 8500.0 + yearOffset * 500.0 + monthStart.monthValue * 45.0, "工资", "本月工资")
        add(12, EntryType.EXPENSE, 58.0, "医疗", "健康用品")
        add(15, EntryType.EXPENSE, 35.0, "教育", "图书和学习资料")
        add(18, EntryType.EXPENSE, 45.0, "其他", "手机话费")
        add(21, EntryType.EXPENSE, 38.0, "娱乐", "音乐和视频订阅")
        if (monthStart.monthValue % 3 == 0) add(19, EntryType.INCOME, 320.0 + yearOffset * 25.0, "报销", "交通报销")
        if (monthStart.monthValue % 4 == 0) add(24, EntryType.EXPENSE, 260.0 + yearOffset * 20.0, "人情", "朋友生日礼物")
        if (monthStart.monthValue % 6 == 0) add(26, EntryType.EXPENSE, 520.0 + yearOffset * 60.0, "购物", "换季衣物")
    }

    private fun addAnnualEntries(entries: MutableList<Entry>, year: Int, today: LocalDate) {
        fun add(month: Int, day: Int, type: EntryType, amount: Double, category: String, note: String) {
            val date = LocalDate.of(year, month, day)
            if (date <= today) entries += demoEntry(type, amount, category, date, note)
        }
        add(1, 28, EntryType.INCOME, 1200.0, "红包", "新年红包")
        add(2, 8, EntryType.EXPENSE, 680.0, "人情", "春节礼金")
        add(5, 2, EntryType.EXPENSE, 780.0, "医疗", "年度体检")
        add(7, 12, EntryType.EXPENSE, 1800.0, "娱乐", "短途旅行")
        add(10, 1, EntryType.INCOME, 600.0, "理财", "理财收益")
        add(12, 20, EntryType.INCOME, 2800.0, "奖金", "年终奖金")
    }

    private fun addDailyActivity(entries: MutableList<Entry>, startYear: Int, today: LocalDate) {
        val dailyPatterns = listOf(
            DemoPattern(0, EntryType.EXPENSE, 12.0, "餐饮", "咖啡和早餐"),
            DemoPattern(0, EntryType.EXPENSE, 24.0, "餐饮", "日常用餐"),
            DemoPattern(0, EntryType.EXPENSE, 8.0, "交通", "通勤出行"),
            DemoPattern(0, EntryType.EXPENSE, 18.0, "购物", "便利店采购"),
            DemoPattern(0, EntryType.EXPENSE, 15.0, "娱乐", "数字内容"),
            DemoPattern(0, EntryType.EXPENSE, 10.0, "其他", "日常杂费"),
        )
        var date = LocalDate.of(startYear, 1, 1)
        var sequence = 0
        while (entries.size < DEMO_ENTRY_COUNT && date <= today) {
            val count = 6 + (date.dayOfMonth + date.monthValue) % 4
            repeat(count) { index ->
                if (entries.size < DEMO_ENTRY_COUNT) {
                    val pattern = dailyPatterns[(sequence + index) % dailyPatterns.size]
                    val amount = pattern.amount + (date.dayOfYear % 11) * 2.0 + index * 1.5
                    entries += demoEntry(pattern.type, amount, pattern.category, date, pattern.note)
                }
            }
            sequence += count
            date = date.plusDays(1)
        }
        check(entries.size == DEMO_ENTRY_COUNT) { "演示时间范围不足以生成 $DEMO_ENTRY_COUNT 笔账目" }
    }

    private fun demoEntry(
        type: EntryType,
        amount: Double,
        category: String,
        date: LocalDate,
        note: String,
    ) = Entry(
        type = type,
        amount = amount,
        amountRaw = amount.toString(),
        categoryName = category,
        date = date,
        note = note,
    )

    companion object {
        const val DEMO_ENTRY_COUNT = 9_000
    }

    private data class DemoPattern(
        val day: Int,
        val type: EntryType,
        val amount: Double,
        val category: String,
        val note: String,
    )
}
