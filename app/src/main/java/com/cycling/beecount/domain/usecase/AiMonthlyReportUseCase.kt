package com.cycling.beecount.domain.usecase

import com.cycling.beecount.data.datasource.AiChatDataSource
import com.cycling.beecount.data.datasource.AiChatResult
import com.cycling.beecount.data.datasource.FailureReason
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.GrowthAnalytics
import com.cycling.beecount.domain.model.MonthlyAnalytics
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.repository.AiKeyRepository
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * AI 月度财务报告用例：复用现有 DeepSeek 管线（[AiChatDataSource.complete]）生成中文月报。
 *
 * 架构铁律同 [AnswerQueryUseCase]：所有金额/笔数等数字由 [EntryQuery] 从数据库确定性计算，
 * 模型只负责把真实数据组织成通顺的中文总结——绝不编造金额。
 * 无 API Key 或网络两次失败均降级为本地模板报告（同一份真实数据），保证离线可用、不崩。
 */
@Singleton
class AiMonthlyReportUseCase @Inject constructor(
    private val entryQuery: EntryQuery,
    private val aiChatDataSource: AiChatDataSource,
    private val aiKeyRepository: AiKeyRepository,
) {

    sealed interface Result {
        /** 月报文本；[isLocal]=true 表示本地模板生成（无 Key / 网络失败降级），非 AI 生成 */
        data class Content(val text: String, val isLocal: Boolean = false) : Result
        data object KeyMissing : Result
        data class Error(val message: String) : Result
    }

    suspend fun generate(month: YearMonth): Result {
        val cur = entryQuery.buildMonth(month).first()
        val prev = entryQuery.buildMonth(month.minusMonths(1)).first()
        val growth = entryQuery.buildGrowth(month).first()

        val key = aiKeyRepository.getKey()
        if (key.isNullOrBlank()) {
            return Result.Content(buildLocalReport(month, cur, prev, growth), isLocal = true)
        }

        val facts = buildFacts(month, cur, prev, growth)
        repeat(2) { attempt ->
            when (val r = aiChatDataSource.complete(key, SYSTEM_PROMPT, facts)) {
                is AiChatResult.Content -> return Result.Content(r.text.trim())
                is AiChatResult.Failure -> when (r.reason) {
                    FailureReason.KEY_INVALID -> return Result.Error("API Key 无效，请到设置里检查")
                    FailureReason.NETWORK -> if (attempt == 0) return@repeat
                }
            }
        }
        // 两次网络失败 → 降级本地模板（同一份真实数据）
        return Result.Content(buildLocalReport(month, cur, prev, growth), isLocal = true)
    }

    /** 组装给模型的「真实数据」文本块：数字全部来自 DB，模型只负责组织语言 */
    private fun buildFacts(
        month: YearMonth,
        cur: MonthlyAnalytics,
        prev: MonthlyAnalytics,
        growth: GrowthAnalytics,
    ): String {
        val ym = month.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        val topCats = cur.categoryRanks.take(5)
            .joinToString("；") { "${it.name} ${money(it.amount)}" }
            .ifEmpty { "（无）" }
        val max = cur.maxDaily
        val maxDailyText = if (max != null && max.amount > 0.0) {
            "${month.atDay(max.day).format(DateTimeFormatter.ofPattern("M月d日"))} 单日支出 ${money(max.amount)}"
        } else "无"
        val s = growth.spendingStats
        val big = s.maxExpense?.let { "${it.categoryName} ${money(it.amount)}（${it.date.format(DateTimeFormatter.ofPattern("M月d日"))}）" } ?: "无"
        val rig = growth.rigidity
        val wk = growth.weekendVsWeekday
        return """
            |【${ym}月度财务真实数据】
            |总支出：${money(cur.expense)}（环比${change(cur.expense, prev.expense)}）
            |总收入：${money(cur.income)}（环比${change(cur.income, prev.income)}）
            |净收支：${money(cur.income - cur.expense)}（收入 − 支出）
            |记账笔数：${cur.entryCount} 笔（上月 ${prev.entryCount} 笔）
            |支出分类 Top5：$topCats
            |单日最高支出：$maxDailyText
            |最大单笔支出：$big
            |客单价：${money(s.avgTicket)}；中位数：${money(s.median)}；支出波动（变异系数）：${"%,.2f".format(Locale.CHINA, s.coefficientOfVariation)}
            |支出结构：刚性 ${rigidPct(rig.rigidRatio)} / 可选 ${rigidPct(rig.variableRatio)} / 冲动 ${rigidPct(rig.impulseRatio)}
            |周末日均 vs 工作日日均：超出 ${"%,.1f".format(Locale.CHINA, kotlin.math.abs(wk.extraPercent))}%${if (wk.extraPercent >= 0) "（周末更费钱）" else "（周末更省）"}
            |财务健康评分：${growth.health.total}/100（${growth.health.grade}）
            |（以上数字已扣除退款，请直接据此撰写，不要改动或补充任何金额）
        """.trimMargin()
    }

    /** 无 Key / 网络失败时的本地中文月报：完全由真实数据拼出，零模型依赖 */
    private fun buildLocalReport(
        month: YearMonth,
        cur: MonthlyAnalytics,
        prev: MonthlyAnalytics,
        growth: GrowthAnalytics,
    ): String {
        val ym = month.format(DateTimeFormatter.ofPattern("yyyy年M月"))
        if (cur.expense == 0.0 && cur.income == 0.0) {
            return "${ym}暂无收支记录，记几笔记账后再来生成月报吧～"
        }
        val sb = StringBuilder()
        sb.appendLine("${ym}月度财务报告（本地生成）")
        sb.appendLine("——")
        sb.appendLine("・总支出 ${money(cur.expense)}（环比${change(cur.expense, prev.expense)}），总收入 ${money(cur.income)}（环比${change(cur.income, prev.income)}）。")
        sb.appendLine("・净收支 ${money(cur.income - cur.expense)}，共记账 ${cur.entryCount} 笔。")
        if (cur.categoryRanks.isNotEmpty()) {
            val top = cur.categoryRanks.take(3).joinToString("、") { "${it.name} ${money(it.amount)}" }
            sb.appendLine("・支出主力：$top。")
        }
        val s = growth.spendingStats
        sb.appendLine("・客单价 ${money(s.avgTicket)}，支出波动${if (s.coefficientOfVariation > 0.8) "偏大" else "平稳"}。")
        sb.appendLine("・财务健康评分 ${growth.health.total}/100（${growth.health.grade}）。")
        val rig = growth.rigidity
        sb.appendLine("・支出结构：刚性 ${rigidPct(rig.rigidRatio)}、可选 ${rigidPct(rig.variableRatio)}、冲动 ${rigidPct(rig.impulseRatio)}。")
        sb.appendLine("——")
        sb.appendLine("提示：配置 DeepSeek API Key 后可生成更口语化、带建议的 AI 月报。")
        return sb.toString().trimEnd()
    }

    private fun change(cur: Double, prev: Double): String = when {
        prev <= 0.0 -> if (cur > 0) "新增" else "持平"
        else -> {
            val p = (cur - prev) / prev * 100
            if (p >= 0) "↑${"%,.1f".format(Locale.CHINA, p)}%" else "↓${"%,.1f".format(Locale.CHINA, -p)}%"
        }
    }

    private fun rigidPct(ratio: Float): String = "${(ratio * 100).toInt()}%"

    private fun money(amount: Double): String = "¥" + String.format(Locale.CHINA, "%,.2f", amount)

    companion object {
        private val SYSTEM_PROMPT = """
            |你是一个记账助手，负责把用户某月的真实财务数据写成一份亲切、有条理的中文月度报告。
            |要求：
            |- 用口语化中文，像朋友聊账单一样，不要堆砌术语；适当分段，重点数字加粗。
            |- 必须基于「真实数据」段落里的数字撰写，不得编造、不得修改任何金额。
            |- 报告建议包含：本月总览（收支、净结余）、与上月对比、花钱最多的类别、支出节奏/波动、健康评分点评、1-2 句贴心的省钱或理财小建议。
            |- 控制在 200 字以内，结尾加一句鼓励的话。
            |只输出报告正文，不要输出「根据数据」之类的元说明。
        """.trimMargin()
    }
}
