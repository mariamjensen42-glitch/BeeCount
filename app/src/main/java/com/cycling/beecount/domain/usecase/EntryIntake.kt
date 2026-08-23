package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.CATEGORY_PATH_SEPARATOR
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.model.nextTagColor
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.TagRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val OCR_MIN_TEXT_LENGTH = 15

/**
 * 记账入口深 module：把「用户原话 → 解析 → 确认/编辑 → 入库」的写入规则收敛在一个接口后。
 *
 * 与 `domain/query/EntryQuery`（只读查询）对照：`EntryIntake` 承担所有写动作——解析、确认入库、
 * 编辑入库，并统一「库外类别先建 + 库外标签注册取色 + 同事务写入」这一条领域规则。
 * 从而消除原先 ConfirmEntryUseCase / UpdateEntryUseCase / ManageTagUseCase 三处重复的
 * 标签取色与类别名校验。
 */
@Singleton
class EntryIntake @Inject constructor(
    private val entryRepository: EntryRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val decoder: com.cycling.beecount.domain.ai.AiEntryJsonDecoder,
    private val aiChatDataSource: com.cycling.beecount.data.datasource.AiChatDataSource,
    private val aiKeyRepository: com.cycling.beecount.domain.repository.AiKeyRepository,
    private val ocrTextRecognizer: OcrTextRecognizer,
    private val currentDate: () -> LocalDate,
) {

    /** OCR 解析结果。 */
    sealed interface OcrOutcome {
        data class Parsed(val parseOutcome: Outcome, val rawText: String) : OcrOutcome
        data object RecognitionFailed : OcrOutcome
        data class ImageReadError(val cause: Exception) : OcrOutcome
        data class RecognitionError(val cause: Exception) : OcrOutcome
    }

    /** 解析结果：与 ParseEntryUseCase.Outcome 保持一致，供外部统一消费。 */
    sealed interface Outcome {
        data class Success(val result: com.cycling.beecount.domain.model.AiParseResult) : Outcome
        data object KeyMissing : Outcome
        data class Error(val message: String) : Outcome
    }

    /** 把用户原话解析为账目草稿（原 ParseEntryUseCase）。 */

    suspend fun parse(
        input: String,
        isOcrInput: Boolean = false,
        referenceDate: LocalDate? = null,
    ): Outcome {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Outcome.Error("请输入要记账的内容")

        val key = aiKeyRepository.getKey()
        if (key.isNullOrBlank()) return Outcome.KeyMissing

        val categories = categoryRepository.observeAll().first()
        val tags = tagRepository.observeAll().first()
        val today = referenceDate ?: currentDate()
        val systemPrompt = buildSystemPrompt(categories, tags, today, isOcrInput)

        repeat(2) { attempt ->
            when (val result = aiChatDataSource.complete(key, systemPrompt, trimmed)) {
                is com.cycling.beecount.data.datasource.AiChatResult.Content -> {
                    val parsed = decoder.decode(result.text)
                    if (parsed == null) {
                        if (attempt == 0) return@repeat
                        return Outcome.Error("解析失败，请换种说法重新描述")
                    }
                    if (parsed.recordable) {
                        val date = parsed.date ?: return Outcome.Error("解析失败，请换种说法重新描述")
                        if (date.isAfter(today)) {
                            if (attempt == 0) return@repeat
                            return Outcome.Error("解析失败，请换种说法重新描述")
                        }
                    }
                    return Outcome.Success(parsed)
                }
                is com.cycling.beecount.data.datasource.AiChatResult.Failure -> when (result.reason) {
                    com.cycling.beecount.data.datasource.FailureReason.KEY_INVALID ->
                        return Outcome.Error("API Key 无效，请到设置里检查")
                    com.cycling.beecount.data.datasource.FailureReason.NETWORK -> {
                        if (attempt == 0) return@repeat
                        return Outcome.Error("网络开小差了，请稍后再试")
                    }
                }
            }
        }
        return Outcome.Error("网络开小差了，请稍后再试")
    }

    /** 从 [OcrImageSource] 提取文字并解析（替代原 OcrEntryUseCase，接口不泄漏 android.net.Uri）。 */

    suspend fun parseOcr(source: OcrImageSource): OcrOutcome {
        val rawText = try {
            ocrTextRecognizer.recognize(source)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return OcrOutcome.RecognitionError(e)
        }
        val meaningfulLength = rawText.filter { !it.isWhitespace() }.length
        if (meaningfulLength < OCR_MIN_TEXT_LENGTH) {
            return OcrOutcome.RecognitionFailed
        }
        return OcrOutcome.Parsed(parse(rawText, isOcrInput = true), rawText)
    }

    /** 确认并入库一条账目草稿（原 ConfirmEntryUseCase）。 */
    suspend fun confirm(
        result: com.cycling.beecount.domain.model.AiParseResult,
        editedAmount: Double,
        editedCategoryName: String,
        originalText: String,
        tags: List<String> = emptyList(),
    ): Entry {
        require(result.recordable) { "recordable=false 的结果不能入库" }
        val type = requireNotNull(result.type) { "账目类型缺失" }
        val date = requireNotNull(result.date) { "账目日期缺失" }
        require(!date.isAfter(currentDate())) { "账目日期不能晚于今天" }
        val amountRaw = requireNotNull(result.amountRaw) { "金额原文缺失" }

        val categoryName = validateCategoryName(editedCategoryName)
        require(editedAmount > 0) { "金额必须大于 0" }

        val resolvedTags = resolveTags(tags)
        val entry = Entry(
            type = type,
            amount = editedAmount,
            amountRaw = amountRaw,
            categoryName = categoryName,
            date = date,
            note = originalText,
            counterparty = result.counterparty,
        )
        val id = entryRepository.addWithTags(entry, resolvedTags.map { it.id })
        return entry.copy(id = id, tags = resolvedTags)
    }

    /** 编辑一条已入库账目并整体替换标签关联（原 UpdateEntryUseCase）。 */
    suspend fun update(
        entry: Entry,
        editedType: EntryType,
        editedAmount: Double,
        editedCategoryName: String,
        editedDate: LocalDate,
        editedNote: String,
        tagNames: List<String>,
        editedCounterparty: String? = null,
    ): Entry {
        require(entry.id > 0L) { "编辑账目必须已有入库 id" }
        require(!editedDate.isAfter(currentDate())) { "账目日期不能晚于今天" }

        val categoryName = validateCategoryName(editedCategoryName)
        require(editedAmount > 0) { "金额必须大于 0" }

        val resolvedTags = resolveTags(tagNames)
        val updated = entry.copy(
            type = editedType,
            amount = editedAmount,
            categoryName = categoryName,
            date = editedDate,
            note = editedNote,
            tags = resolvedTags,
            counterparty = editedCounterparty?.trim()?.takeIf { it.isNotEmpty() },
        )
        entryRepository.updateWithTags(updated, resolvedTags.map { it.id })
        return updated
    }

    /** 统一的类别名校验（原先仅 [ManageCategoryUseCase.requireName] 做了「不含分隔符」校验）。 */
    private fun validateCategoryName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "类别不能为空" }
        require(!trimmed.contains(CATEGORY_PATH_SEPARATOR)) {
            "类别名不能包含「$CATEGORY_PATH_SEPARATOR」"
        }
        return trimmed
    }

    /** 统一的标签解析：库内复用，库外自动注册并顺序取色（原先在 confirm/update 两处重复）。 */
    private suspend fun resolveTags(names: List<String>): List<Tag> {
        val knownTags = tagRepository.observeAll().first()
        val usedColors = knownTags.map { it.color }.toMutableSet()
        val resolved = mutableListOf<Tag>()
        for (name in names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
            val existing = knownTags.firstOrNull { it.name == name }
            if (existing != null) {
                resolved += existing
            } else {
                val color = nextTagColor(usedColors)
                val id = tagRepository.create(name = name, color = color)
                resolved += Tag(id = id, name = name, color = color, isCustom = true)
                usedColors += color
            }
        }
        return resolved
    }

    private fun buildSystemPrompt(
        categories: List<com.cycling.beecount.domain.model.Category>,
        tags: List<Tag>,
        today: LocalDate,
        isOcrInput: Boolean,
    ): String {
        val visible = categories.filter { !it.isHidden }
        val expenseCategories = visible.filter { it.type == EntryType.EXPENSE }
            .joinToString("、") { it.name }
        val incomeCategories = visible.filter { it.type == EntryType.INCOME }
            .joinToString("、") { it.name }
        val tagNames = tags.joinToString("、") { it.name }
        val todayText = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
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
            |  "counterparty": "交易对方名称"（仅当 recordable 为 true，且能从输入中识别出商家/转账方时返回，如"滴滴"“星巴克”；无法识别时省略该字段）,
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
            ${inputContextPrompt(isOcrInput)}
        """.trimMargin()
    }

    private fun inputContextPrompt(isOcrInput: Boolean): String {
        if (!isOcrInput) return ""
        return "\n|以下输入来自支付截图的 OCR 文字，请从中提取收支信息。\n|额外要求：在 JSON 中增加 \"note\" 字段，用一句不超过 20 字的中文描述这笔消费，需保留商品名称、数量、商家等关键信息（如\"打印纸5包\"、\"滴滴出行打车\"、\"星巴克拿铁1杯\"）；若无法判断则省略该字段。"
    }

    private fun weekdayName(day: java.time.DayOfWeek): String = when (day) {
        java.time.DayOfWeek.MONDAY -> "周一"
        java.time.DayOfWeek.TUESDAY -> "周二"
        java.time.DayOfWeek.WEDNESDAY -> "周三"
        java.time.DayOfWeek.THURSDAY -> "周四"
        java.time.DayOfWeek.FRIDAY -> "周五"
        java.time.DayOfWeek.SATURDAY -> "周六"
        java.time.DayOfWeek.SUNDAY -> "周日"
    }
}
