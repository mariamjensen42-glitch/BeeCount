package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.BudgetForecast
import com.cycling.beecount.domain.model.BudgetProgress
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 预算执行预测（本地线性外推，零云依赖）：对每条预算进度，按当前日均外推到周期末，
 * 判断是否将超支。纯函数，不触及网络/AI；如需更自然的中文叙述，可后续选接 AI 润色。
 */
@Singleton
class BudgetForecastUseCase @Inject constructor() {

    fun forecast(progress: List<BudgetProgress>): List<BudgetForecast> = progress.map { compute(it) }

    private fun compute(p: BudgetProgress): BudgetForecast {
        val totalDays = ChronoUnit.DAYS.between(p.period.start, p.period.end) + 1
        val elapsed = (totalDays - p.remainingDays).coerceAtLeast(0)
        // 周期至今实际日均；第一天（elapsed=0）无历史，按已花保守预估
        val dailyAvg = if (elapsed > 0) p.spent / elapsed else p.spent
        val projectedSpent = if (elapsed > 0) dailyAvg * totalDays else p.spent
        val projectedOver = (projectedSpent - p.base).coerceAtLeast(0.0)
        val willOver = projectedSpent > p.base
        val message = if (willOver) {
            "按当前节奏，预计周期末超支 ¥${money(projectedOver)}（预估支出 ¥${money(projectedSpent)} / 预算 ¥${money(p.base)}）"
        } else {
            "预计周期末支出 ¥${money(projectedSpent)}，尚余 ¥${money(p.base - projectedSpent)}"
        }
        return BudgetForecast(
            progress = p,
            projectedSpent = projectedSpent,
            projectedOver = projectedOver,
            willOver = willOver,
            message = message,
        )
    }

    private fun money(v: Double): String = "%.2f".format(v)
}
