package com.cycling.beecount.domain.usecase

import com.cycling.beecount.data.datasource.AiChatDataSource
import com.cycling.beecount.data.datasource.AiChatResult
import com.cycling.beecount.data.datasource.FailureReason
import com.cycling.beecount.domain.ai.AiEntryJsonDecoder
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.TagRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * 用例：把用户原话解析为账目草稿。
 *
 * 流程：取 Key → 取类别 → 构造 prompt（注入当前日期）→ 调用模型 → 解析 JSON → 校验。
 * 网络错误或解析结果非法时自动重试一次（Q6b）；Key 无效不重试。
 */
class ParseEntryUseCase @Inject constructor(
    private val aiKeyRepository: AiKeyRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val aiChatDataSource: AiChatDataSource,
    private val decoder: AiEntryJsonDecoder,
    private val currentDate: () -> LocalDate,
) {

    sealed interface Outcome {
        /** 解析成功（可能是 recordable=false 的非记账输入） */
        data class Success(val result: AiParseResult) : Outcome

        /** 尚未配置 API Key */
        data object KeyMissing : Outcome

        /** 解析失败，message 为用户可见提示 */
        data class Error(val message: String) : Outcome
    }

    suspend operator fun invoke(input: String, isOcrInput: Boolean = false): Outcome {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Outcome.Error("请输入要记账的内容")

        val key = aiKeyRepository.getKey()
        if (key.isNullOrBlank()) return Outcome.KeyMissing

        val categories = categoryRepository.observeAll().first()
        val tags = tagRepository.observeAll().first()
        val today = currentDate()
        val systemPrompt = buildSystemPrompt(categories, tags, today, isOcrInput)

        // 最多尝试 2 次：原始请求 + 失败重试一次
        repeat(2) { attempt ->
            val result = aiChatDataSource.complete(key, systemPrompt, trimmed)
            when (result) {
                is AiChatResult.Content -> {
                    val parsed = decoder.decode(result.text)
                    if (parsed == null) {
                        // 模型输出非预期 JSON，重试一次
                        if (attempt == 0) return@repeat
                        return Outcome.Error("解析失败，请换种说法重新描述")
                    }
                    if (parsed.recordable) {
                        val date = parsed.date ?: return Outcome.Error("解析失败，请换种说法重新描述")
                        if (date.isAfter(today)) {
                            // 未来日期：多半是换算错位，重试一次
                            if (attempt == 0) return@repeat
                            return Outcome.Error("解析失败，请换种说法重新描述")
                        }
                    }
                    return Outcome.Success(parsed)
                }

                is AiChatResult.Failure -> when (result.reason) {
                    FailureReason.KEY_INVALID ->
                        return Outcome.Error("API Key 无效，请到设置里检查")

                    FailureReason.NETWORK -> {
                        if (attempt == 0) return@repeat
                        return Outcome.Error("网络开小差了，请稍后再试")
                    }
                }
            }
        }
        return Outcome.Error("网络开小差了，请稍后再试")
    }

    private fun buildSystemPrompt(categories: List<Category>, tags: List<Tag>, today: LocalDate, isOcrInput: Boolean = false): String {
        val expenseCategories = categories.filter { it.type == com.cycling.beecount.domain.model.EntryType.EXPENSE }
            .joinToString("、") { it.name }
        val incomeCategories = categories.filter { it.type == com.cycling.beecount.domain.model.EntryType.INCOME }
            .joinToString("、") { it.name }
        val tagNames = tags.joinToString("、") { it.name }
        val todayText = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return """
            |你是一个记账助手。用户会用一句自然语言描述一笔收支，你需要把这句话解析为 JSON 并输出。
            |
            |输出必须严格符合以下 JSON 格式（不要输出任何其他文字，包括 Markdown 代码块）：
            |{
            |  "recordable": true 或 false,
            |  "type": "expense" 或 "income"（仅当 recordable 为 true）,
            |  "amount_raw": "金额原文，如 30块 或 1万"（仅当 recordable 为 true）,
            |  "amount": 以元为单位的数字，如 30.0 或 10000.0（仅当 recordable 为 true，须将万/千换算为元）,
            |  "category": "类别名"（仅当 recordable 为 true，从下方类别列表中选择最合适的）,
            |  "date": "绝对日期 YYYY-MM-DD"（仅当 recordable 为 true，相对时间须换算为绝对日期）,
            |  "tags": ["标签名1", "标签名2"]（仅当 recordable 为 true，最多 3 个，从下方标签列表中选择最贴合的；没有合适的给空数组）,
            |  "message": "对用户的简短回应"（仅当 recordable 为 false）
            |}
            |
            |判断规则：
            |- 能解析为一笔收支的输入，recordable 为 true。中文金额支持万/千/块/元/毛等表达。
            |- 闲聊、问候、查询等无法记为一笔收支的输入，recordable 为 false，并在 message 中简短回应。
            |
            |今天是 $todayText（${weekdayName(today.dayOfWeek)}）。请把"昨天""上周五"等相对时间换算为绝对日期。
            |
            |支出类别：$expenseCategories
            |收入类别：$incomeCategories
            |标签（可选，最多选 3 个）：$tagNames
            |
            |示例输入：昨天打车花了30块
            |示例输出：{"recordable": true, "type": "expense", "amount_raw": "30块", "amount": 30.0, "category": "交通", "date": "$todayText", "tags": []}
            |
            |示例输入：周末给猫买了200的猫粮
            |示例输出：{"recordable": true, "type": "expense", "amount_raw": "200", "amount": 200.0, "category": "购物", "date": "$todayText", "tags": ["宠物"]}
            |
            |示例输入：你好
            |示例输出：{"recordable": false, "message": "你好呀！告诉我一笔收支就能帮你记账，比如：昨天打车花了30块"}
            |${if (isOcrInput) "\n|以下输入来自支付截图的 OCR 文字，请从中提取收支信息。" else ""}
        """.trimMargin()
    }

    private fun weekdayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}
