package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.PendingDraft
import com.cycling.beecount.domain.repository.AutoEntrySettingsRepository
import com.cycling.beecount.domain.repository.PendingDraftRepository
import com.cycling.beecount.domain.repository.ProcessedNotificationRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEntryUseCaseTest {

    private val wechat = "com.tencent.mm"
    private val date = LocalDate.of(2026, 8, 16)

    private class FakeProcessedRepository : ProcessedNotificationRepository {
        val seen = mutableSetOf<String>()
        override suspend fun markProcessedOrAlreadySeen(
            packageName: String,
            notifyKey: String,
            text: String,
        ): Boolean = !seen.add("$notifyKey|$text")
    }

    private class FakePendingRepository : PendingDraftRepository {
        val drafts = mutableListOf<PendingDraft>()
        override fun observePending(): Flow<List<PendingDraft>> = flowOf(drafts.toList())
        override suspend fun add(draft: PendingDraft): Long {
            drafts += draft.copy(id = (drafts.size + 1).toLong())
            return drafts.last().id
        }

        override suspend fun remove(id: Long) {
            drafts.removeAll { it.id == id }
        }
    }

    private class FakeSettingsRepository : AutoEntrySettingsRepository {
        private val failures = mutableMapOf<String, Long>()
        override fun observeEnabled(): Flow<Boolean> = flowOf(true)
        override suspend fun isEnabled(): Boolean = true
        override suspend fun setEnabled(enabled: Boolean) {}
        override suspend fun lastFailureAt(packageName: String): Long = failures[packageName] ?: 0L
        override suspend fun setLastFailureAt(packageName: String, timeMillis: Long) {
            failures[packageName] = timeMillis
        }
    }

    private class FakeNotifier : AutoEntryNotifier {
        var draftNotices = 0
        var failedNotices = 0
        var lastFailedText: String? = null
        var lastDraftId: Long? = null
        override fun notifyDraftReady(packageName: String, draftId: Long, result: AiParseResult) {
            draftNotices++
            lastDraftId = draftId
        }

        override fun notifyParseFailed(originalText: String) {
            failedNotices++
            lastFailedText = originalText
        }
    }

    private fun gate(allow: Boolean): NotificationGate =
        if (allow) NotificationGate() else NotificationGate(allowedPackages = emptySet())

    /** 记录调用参数，可按需返回指定结果 */
    private fun parseUseCase(vararg outcomes: ParseEntryUseCase.Outcome): Pair<ParseEntryUseCase, () -> List<Triple<String, Boolean, LocalDate?>>> {
        val calls = mutableListOf<Triple<String, Boolean, LocalDate?>>()
        val fake = object : ParseEntryUseCaseStub() {
            override suspend fun invoke(
                input: String,
                isOcrInput: Boolean,
                isNotificationInput: Boolean,
                referenceDate: LocalDate?,
            ): ParseEntryUseCase.Outcome {
                calls += Triple(input, isNotificationInput, referenceDate)
                return outcomes[calls.size - 1]
            }
        }
        return fake to { calls.toList() }
    }

    @Test
    fun `gate rejects notification - nothing happens`() = runTest {
        val processed = FakeProcessedRepository()
        val pending = FakePendingRepository()
        val notifier = FakeNotifier()
        val (parse, calls) = parseUseCase()
        val useCase = NotificationEntryUseCase(
            gate = gate(allow = false),
            processedNotificationRepository = processed,
            pendingDraftRepository = pending,
            autoEntrySettingsRepository = FakeSettingsRepository(),
            parseEntryUseCase = parse,
            notifier = notifier,
        )

        useCase.handle(wechat, "key-1", "微信支付", "你已成功支付15.00元", date)

        assertEquals(0, calls().size)
        assertTrue(pending.drafts.isEmpty())
        assertEquals(0, notifier.draftNotices)
        assertEquals(0, notifier.failedNotices)
    }

    @Test
    fun `successful parse adds draft and notifies`() = runTest {
        val pending = FakePendingRepository()
        val notifier = FakeNotifier()
        val success = ParseEntryUseCase.Outcome.Success(
            AiParseResult(
                recordable = true,
                type = EntryType.EXPENSE,
                amount = 15.0,
                amountRaw = "15.00元",
                categoryName = "餐饮",
                date = date,
                note = "蜜雪冰城",
            )
        )
        val (parse, calls) = parseUseCase(success)

        val useCase = NotificationEntryUseCase(
            gate = gate(allow = true),
            processedNotificationRepository = FakeProcessedRepository(),
            pendingDraftRepository = pending,
            autoEntrySettingsRepository = FakeSettingsRepository(),
            parseEntryUseCase = parse,
            notifier = notifier,
        )

        useCase.handle(wechat, "key-1", "微信支付", "你已成功支付15.00元\n蜜雪冰城", date)

        assertEquals(1, pending.drafts.size)
        val draft = pending.drafts.first()
        assertEquals(15.0, draft.amount, 0.001)
        assertEquals(date, draft.date)
        assertTrue(draft.originalText.contains("15.00元"))
        assertEquals(1, notifier.draftNotices)
        // 确认通知携带草稿 id，深链点开落到这张卡片
        assertEquals(draft.id, notifier.lastDraftId)
        // 通知流标记 isNotificationInput，并以通知时间日期为参照日
        val call = calls().single()
        assertTrue(call.second)
        assertEquals(date, call.third)
    }

    @Test
    fun `duplicate delivery is skipped`() = runTest {
        val processed = FakeProcessedRepository()
        val pending = FakePendingRepository()
        val notifier = FakeNotifier()
        val success = ParseEntryUseCase.Outcome.Success(
            AiParseResult(
                recordable = true,
                type = EntryType.EXPENSE,
                amount = 15.0,
                amountRaw = "15.00元",
                categoryName = "餐饮",
                date = date,
            )
        )
        val (parse, calls) = parseUseCase(success, success)

        val useCase = NotificationEntryUseCase(
            gate = gate(allow = true),
            processedNotificationRepository = processed,
            pendingDraftRepository = pending,
            autoEntrySettingsRepository = FakeSettingsRepository(),
            parseEntryUseCase = parse,
            notifier = notifier,
        )

        useCase.handle(wechat, "key-1", "微信支付", "你已成功支付15.00元", date)
        useCase.handle(wechat, "key-1", "微信支付", "你已成功支付15.00元", date)

        assertEquals(1, calls().size)
        assertEquals(1, pending.drafts.size)
        assertEquals(1, notifier.draftNotices)
    }

    @Test
    fun `non recordable parse is silently ignored`() = runTest {
        val pending = FakePendingRepository()
        val notifier = FakeNotifier()
        val nonRecordable = ParseEntryUseCase.Outcome.Success(
            AiParseResult(recordable = false, message = "不是收支")
        )
        val (parse, _) = parseUseCase(nonRecordable)

        val useCase = NotificationEntryUseCase(
            gate = gate(allow = true),
            processedNotificationRepository = FakeProcessedRepository(),
            pendingDraftRepository = pending,
            autoEntrySettingsRepository = FakeSettingsRepository(),
            parseEntryUseCase = parse,
            notifier = notifier,
        )

        useCase.handle(wechat, "key-1", "微信支付", "你已成功支付15.00元", date)

        assertTrue(pending.drafts.isEmpty())
        assertEquals(0, notifier.draftNotices)
        assertEquals(0, notifier.failedNotices)
    }

    @Test
    fun `parse failure notifies with throttle`() = runTest {
        val pending = FakePendingRepository()
        val notifier = FakeNotifier()
        val settings = FakeSettingsRepository()
        val error = ParseEntryUseCase.Outcome.Error("网络开小差了，请稍后再试")
        val (parse, _) = parseUseCase(error, error, error)

        val useCase = NotificationEntryUseCase(
            gate = gate(allow = true),
            processedNotificationRepository = FakeProcessedRepository(),
            pendingDraftRepository = pending,
            autoEntrySettingsRepository = settings,
            parseEntryUseCase = parse,
            notifier = notifier,
        )

        // 同一条通知重复投递会被去重拦下，这里模拟三条不同的通知连续失败
        useCase.handle(wechat, "key-1", "微信支付", "你已成功支付15.00元", date)
        useCase.handle(wechat, "key-2", "微信支付", "你已成功支付20.00元", date)
        useCase.handle(wechat, "key-3", "微信支付", "你已成功支付25.00元", date)

        // 15 分钟节流：同一包名只提示一次
        assertEquals(1, notifier.failedNotices)
        assertNotNull(notifier.lastFailedText)
        assertFalse(notifier.lastFailedText.isNullOrBlank())
        assertTrue(pending.drafts.isEmpty())
    }

    @Test
    fun `key missing is silent`() = runTest {
        val pending = FakePendingRepository()
        val notifier = FakeNotifier()
        val (parse, _) = parseUseCase(ParseEntryUseCase.Outcome.KeyMissing)

        val useCase = NotificationEntryUseCase(
            gate = gate(allow = true),
            processedNotificationRepository = FakeProcessedRepository(),
            pendingDraftRepository = pending,
            autoEntrySettingsRepository = FakeSettingsRepository(),
            parseEntryUseCase = parse,
            notifier = notifier,
        )

        useCase.handle(wechat, "key-1", "微信支付", "你已成功支付15.00元", date)

        assertTrue(pending.drafts.isEmpty())
        assertEquals(0, notifier.draftNotices)
        assertEquals(0, notifier.failedNotices)
    }

    /** 可覆写 invoke 的最小 ParseEntryUseCase 替身（构造参数用 dummy 填） */
    private open class ParseEntryUseCaseStub : ParseEntryUseCase(
        aiKeyRepository = object : com.cycling.beecount.domain.repository.AiKeyRepository {
            override fun observeKey(): Flow<String?> = flowOf(null)
            override suspend fun getKey(): String? = null
            override suspend fun saveKey(key: String) {}
            override suspend fun clearKey() {}
        },
        categoryRepository = object : com.cycling.beecount.domain.repository.CategoryRepository {
            override fun observeAll(): Flow<List<com.cycling.beecount.domain.model.Category>> =
                MutableStateFlow(emptyList())
            override suspend fun create(name: String, type: EntryType): Long = 0L
            override suspend fun rename(id: Long, name: String) {}
            override suspend fun delete(id: Long) {}
        },
        tagRepository = object : com.cycling.beecount.domain.repository.TagRepository {
            override fun observeAll(): Flow<List<com.cycling.beecount.domain.model.Tag>> =
                MutableStateFlow(emptyList())
            override suspend fun create(name: String, color: Long): Long = 0L
            override suspend fun rename(id: Long, name: String) {}
            override suspend fun updateColor(id: Long, color: Long) {}
            override suspend fun delete(id: Long) {}
        },
        aiChatDataSource = object : com.cycling.beecount.data.datasource.AiChatDataSource {
            override suspend fun complete(
                apiKey: String,
                systemPrompt: String,
                userPrompt: String,
            ): com.cycling.beecount.data.datasource.AiChatResult =
                error("stub should not be called")
        },
        decoder = com.cycling.beecount.domain.ai.AiEntryJsonDecoder(
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        ),
        currentDate = { LocalDate.now() },
    ) {
        override suspend fun invoke(
            input: String,
            isOcrInput: Boolean,
            isNotificationInput: Boolean,
            referenceDate: LocalDate?,
        ): ParseEntryUseCase.Outcome = error("stub invoke")
    }
}
