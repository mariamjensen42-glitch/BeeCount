package com.cycling.beecount.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 预算周期类型。
 *
 * 一条预算记录属于且仅属于一个周期；不同周期类型（月度总预算 + 月度餐饮 + 年度旅行…）可同时存在。
 * [CUSTOM_DAYS] 表示自定义天数周期，周期长度由 [Budget.lengthDays] 决定。固定日历周期（周/月/季/年）
 * 的周期区间由日期本身决定；自定义周期以 [Budget.customAnchor] 为锚点向前/向后推算。
 * 预算消费口径：只统计支出 (EXPENSE)，中性与收入不计入；分类维度按类别名快照匹配。
 */
enum class BudgetCycle(val label: String) {
    WEEKLY("每周"),
    MONTHLY("每月"),
    QUARTERLY("每季"),
    ANNUAL("每年"),
    CUSTOM_DAYS("自定义"),
}

/**
 * 预算：一条「周期 × 维度 × 金额」的记录。
 *
 * - [cycle]：周期类型。
 * - [lengthDays]：[cycle] 为 [BudgetCycle.CUSTOM_DAYS] 时的天数（其余周期忽略）。
 * - [customAnchor]：自定义周期的锚点起始日（仅 CUSTOM_DAYS 使用），周期 = [anchor, anchor+lengthDays)。
 * - [categoryName]：维度。`null` 表示总预算；否则为该类别的账目快照名——**只支持一级分类**（存叶名），
 *   匹配时覆盖该一级分类及其全部子分类（「一级分类 · 任意」前缀匹配），路径分隔符见
 *   [CATEGORY_PATH_SEPARATOR]。
 * - [carryOver]：是否把上一周期的正向结余滚入当前周期（超支抹平、不反向扣）。
 * - [enabled]：是否启用，关闭的预算不参与任何统计。
 */
data class Budget(
    val id: Long = 0L,
    val cycle: BudgetCycle,
    val lengthDays: Int = 30,
    val customAnchor: LocalDate? = null,
    val categoryName: String? = null,
    val amount: Double,
    val carryOver: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 预算例外：该日期的账目全额不计入预算消费（如旅行、搬家等集中消费日）。 */
data class BudgetException(
    val date: LocalDate,
)

/** 预算周期区间（闭区间 [start, end]，含端点） */
data class BudgetPeriod(
    val start: LocalDate,
    val end: LocalDate,
)

/** 预算进度：一条预算在指定日期的实时统计。 */
data class BudgetProgress(
    val budget: Budget,
    val period: BudgetPeriod,
    /** 本周期（含例外豁免）实际支出累加 */
    val spent: Double,
    /** 周期起始可用额 = 基础额 + 上期正向结余 */
    val base: Double,
    /** 剩余天数（含今天，到今天算起往周期末） */
    val remainingDays: Long,
) {
    /** 周期内可花额度（扣除已花） */
    val available: Double get() = base - spent
    /** 剩余日均 = 可花 / 剩余天数，超支或剩余天数≤0 时为 0 */
    val dailyAllowance: Double
        get() = if (remainingDays > 0 && available > 0) available / remainingDays.toDouble() else 0.0
    /** 是否超支 */
    val isOver: Boolean get() = spent > base
    /** 超支金额（>0 时超支） */
    val overAmount: Double get() = (spent - base).coerceAtLeast(0.0)
    /** 进度占比（0..1+，内部不封顶，由 UI 控制高低提示） */
    val fraction: Double get() = if (base > 0) spent / base else if (spent > 0) 1.0 else 0.0
}

/**
 * 预算数学：周期区间、消费匹配、结转与进度。全部为纯函数便于单测。
 */
object BudgetMath {

    /** 该周期是否已在 [date] 时开始（即 date ≥ start） */
    fun isActive(budget: Budget, date: LocalDate): Boolean = periodOf(budget, date) != null

    /**
     * 预算在 [date] 所在周期的区间；预算是未来才首次建立（date 早于锚点/周期起点）时返回 null。
     */
    fun periodOf(budget: Budget, date: LocalDate): BudgetPeriod? {
        val p = currentPeriod(budget, date)
        return if (date < p.start) null else p
    }

    /** 当前周期的真正区间（即使 date 早于周期起点也返回该最近周期，用于之前周期追溯）。 */
    fun currentPeriod(budget: Budget, date: LocalDate): BudgetPeriod {
        return when (budget.cycle) {
            BudgetCycle.WEEKLY -> {
                val start = date.with(DayOfWeek.MONDAY)
                BudgetPeriod(start, start.plusDays(6))
            }
            BudgetCycle.MONTHLY -> {
                val start = date.withDayOfMonth(1)
                BudgetPeriod(start, start.lengthOfMonth().let { start.plusDays(it.toLong() - 1) })
            }
            BudgetCycle.QUARTERLY -> {
                val month = date.monthValue
                val qStartMonth = ((month - 1) / 3) * 3 + 1
                val start = date.withMonth(qStartMonth).withDayOfMonth(1)
                BudgetPeriod(start, start.plusMonths(3).minusDays(1))
            }
            BudgetCycle.ANNUAL -> {
                val start = date.withDayOfYear(1)
                BudgetPeriod(start, start.plusYears(1).minusDays(1))
            }
            BudgetCycle.CUSTOM_DAYS -> {
                val anchor = budget.customAnchor ?: date
                val n = budget.lengthDays.coerceAtLeast(1).toLong()
                val daysSince = ChronoUnit.DAYS.between(anchor, date)
                val start = anchor.plusDays(Math.floorDiv(daysSince, n) * n)
                BudgetPeriod(start, start.plusDays(n - 1))
            }
        }
    }

    /** 返回 [before] 所在周期往前第 [offset] 个周期（offset=1 表示上一个周期，offset=0 为 [before] 所在周期）。 */
    fun periodBefore(budget: Budget, before: LocalDate, offset: Int): BudgetPeriod {
        require(offset >= 0)
        var period = currentPeriod(budget, before)
        repeat(offset) {
            period = previousPeriod(budget, period)
        }
        return period
    }

    private fun previousPeriod(budget: Budget, p: BudgetPeriod): BudgetPeriod {
        return when (budget.cycle) {
            BudgetCycle.WEEKLY -> BudgetPeriod(p.start.minusWeeks(1), p.end.minusWeeks(1))
            BudgetCycle.MONTHLY -> BudgetPeriod(p.start.minusMonths(1), p.end.minusMonths(1))
            BudgetCycle.QUARTERLY -> BudgetPeriod(p.start.minusMonths(3), p.end.minusMonths(3))
            BudgetCycle.ANNUAL -> BudgetPeriod(p.start.minusYears(1), p.end.minusYears(1))
            BudgetCycle.CUSTOM_DAYS -> {
                val n = budget.lengthDays.coerceAtLeast(1).toLong()
                BudgetPeriod(p.start.minusDays(n), p.end.minusDays(n))
            }
        }
    }

    /** 该账目类别名是否命中预算维度。null 维度（总预算）恒命中；分类维度按「一级分类·任意」前缀匹配。 */
    fun matchesCategory(entryCategoryName: String, budgetCategoryName: String?): Boolean {
        if (budgetCategoryName == null) return true
        if (entryCategoryName == budgetCategoryName) return true
        // 父级分类预算也命中其所有子分类「父·…」
        return entryCategoryName.length > budgetCategoryName.length &&
            entryCategoryName.startsWith(budgetCategoryName + CATEGORY_PATH_SEPARATOR)
    }

    /**
     * 统计 [period] 内命中维度且非例外日的支出。
     * [entries] 只需包含该周期内的账目；[exceptionDates] 为该预算共享的例外日集合。
     */
    fun spent(
        entries: List<Entry>,
        period: BudgetPeriod,
        budgetCategoryName: String?,
        exceptionDates: Set<LocalDate>,
    ): Double = entries
        .filter { it.type == EntryType.EXPENSE }
        .filter { !it.date.isBefore(period.start) && !it.date.isAfter(period.end) }
        .filter { it.date !in exceptionDates }
        .filter { matchesCategory(it.categoryName, budgetCategoryName) }
        .sumOf { it.amount }

    /**
     * 当前周期结转基数：上一周期「基础额 − 支出」的正向结余（≥0），超支抹平不反向扣。
     * [carryOver] 关闭、或该预算尚未进入任何周期时返回 0。
     * [spentOfPeriod] 由调用方喂入（可复用已加载的 entries），避免重复查询。
     */
    fun carryIn(budget: Budget, date: LocalDate, spentOfPeriod: (BudgetPeriod) -> Double): Double {
        if (!budget.carryOver) return 0.0
        val prev = previousPeriod(budget, currentPeriod(budget, date))
        return (budget.amount - spentOfPeriod(prev)).coerceAtLeast(0.0)
    }

    /** 剩余天数：从今天（含）到周期末（含）的天数；今天早于周期起点时视为整个周期。 */
    fun remainingDays(period: BudgetPeriod, today: LocalDate): Long {
        if (today.isAfter(period.end)) return 0
        val start = if (today.isBefore(period.start)) period.start else today
        return ChronoUnit.DAYS.between(start, period.end) + 1
    }
}
