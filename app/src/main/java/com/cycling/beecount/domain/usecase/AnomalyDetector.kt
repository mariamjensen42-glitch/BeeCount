package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.AnomalyAlert
import com.cycling.beecount.domain.model.AnomalyKind
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 异常消费预警（本地统计，零云依赖）：新支出落库后，对比历史基线，
 * 单笔金额或当日累计显著偏离（> 均值 + K·标准差）即产出预警。
 *
 * 基线取最近 [lookbackMonths] 个月同类别（按一级类别）的支出样本：
 * - 单笔：entry.amount > mean + K·std
 * - 单日累计（含刚写入的 entry）：当日同类别合计 > 日均(= mean/30) + K·std
 */
@Singleton
class AnomalyDetector @Inject constructor(
    @Named("base") private val entryRepository: EntryRepository,
) {
    private val lookbackMonths = 3
    private val k = 2.5
    private val minSamples = 3

    suspend fun detect(entry: Entry): AnomalyAlert? {
        if (entry.type != EntryType.EXPENSE) return null
        return runCatching {
            val today = entry.date
            val start = today.minusMonths(lookbackMonths.toLong()).withDayOfMonth(1)
            val history = entryRepository.observeBetween(start, today).first()
                .filter { it.type == EntryType.EXPENSE }
            val cat = entry.categoryName.topLevel()
            val samples = history.filter { it.categoryName.topLevel() == cat }.map { it.amount }
            if (samples.size < minSamples) return@runCatching null
            val mean = samples.average()
            val std = samples.stdDev()
            if (std <= 0.0) return@runCatching null

            if (entry.amount > mean + k * std) {
                return@runCatching AnomalyAlert(
                    kind = AnomalyKind.SINGLE_LARGE,
                    category = cat,
                    amount = entry.amount,
                    baseline = mean,
                    stdDev = std,
                    date = today,
                    message = "「$cat」单笔 ${money(entry.amount)} 明显高于历史（均值 ${money(mean)}，标准差 ${money(std)}）",
                )
            }

            val dayTotal = history
                .filter { it.date == today && it.categoryName.topLevel() == cat }
                .sumOf { it.amount }
            val dayBaseline = mean / 30.0
            if (dayTotal > dayBaseline + k * std) {
                return@runCatching AnomalyAlert(
                    kind = AnomalyKind.DAY_TOTAL_HIGH,
                    category = cat,
                    amount = dayTotal,
                    baseline = dayBaseline,
                    stdDev = std,
                    date = today,
                    message = "「$cat」今日累计 ${money(dayTotal)} 超出历史日均 ${money(dayBaseline)}（标准差 ${money(std)}）",
                )
            }
            null
        }.getOrNull()
    }

    private fun String.topLevel(): String = substringBefore("·").trim().ifEmpty { this }

    private fun List<Double>.stdDev(): Double {
        if (size < 2) return 0.0
        val m = average()
        return kotlin.math.sqrt(sumOf { (it - m) * (it - m) } / size)
    }

    private fun money(v: Double): String = "¥%.2f".format(v)
}
