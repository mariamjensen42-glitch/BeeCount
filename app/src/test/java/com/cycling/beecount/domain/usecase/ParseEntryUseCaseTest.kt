package com.cycling.beecount.domain.usecase

import com.cycling.beecount.data.datasource.AiChatDataSource
import com.cycling.beecount.data.datasource.AiChatResult
import com.cycling.beecount.data.datasource.FailureReason
import com.cycling.beecount.domain.ai.AiEntryJsonDecoder
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.TagRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseEntryUseCaseTest {

    private val today = LocalDate.of(2026, 8, 15)

    private fun fakeKeyRepository(key: String? = "test-key") = object : AiKeyRepository {
        override fun observeKey(): Flow<String?> = flowOf(key)
        override suspend fun getKey(): String? = key
        override suspend fun saveKey(key: String) {}
        override suspend fun clearKey() {}
    }

    private fun fakeCategoryRepository() = object : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = flowOf(
            listOf(
                Category(id = 1, name = "餐饮", type = EntryType.EXPENSE),
                Category(id = 2, name = "交通", type = EntryType.EXPENSE),
                Category(id = 3, name = "红包", type = EntryType.INCOME),
            )
        )

        override suspend fun create(name: String, type: EntryType): Long = 99L
        override suspend fun rename(id: Long, name: String) {}
        override suspend fun delete(id: Long) {}
    }

    private fun fakeTagRepository() = object : TagRepository {
        override fun observeAll(): Flow<List<Tag>> = flowOf(
            listOf(
                Tag(id = 1, name = "旅行", color = 0xFF81C784, isCustom = false),
                Tag(id = 2, name = "出差", color = 0xFF64B5F6, isCustom = false),
            )
        )

        override suspend fun create(name: String, color: Long): Long = 88L
        override suspend fun rename(id: Long, name: String) {}
        override suspend fun updateColor(id: Long, color: Long) {}
        override suspend fun delete(id: Long) {}
    }

    private fun useCase(
        chat: AiChatDataSource,
        key: String? = "test-key",
    ) = ParseEntryUseCase(
        aiKeyRepository = fakeKeyRepository(key),
        categoryRepository = fakeCategoryRepository(),
        tagRepository = fakeTagRepository(),
        aiChatDataSource = chat,
        decoder = AiEntryJsonDecoder(Json { ignoreUnknownKeys = true }),
        currentDate = { today },
    )

    @Test
    fun `returns KeyMissing when no key configured`() = runTest {
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                error("should not be called")
        }
        val outcome = useCase(chat, key = null)("昨天打车花了30块")
        assertEquals(ParseEntryUseCase.Outcome.KeyMissing, outcome)
    }

    @Test
    fun `returns Success with parsed result on valid response`() = runTest {
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                AiChatResult.Content(
                    """{"recordable": true, "type": "expense", "amount_raw": "30块", "amount": 30.0, "category": "交通", "date": "2026-08-14"}"""
                )
        }
        val outcome = useCase(chat)("昨天打车花了30块")
        assertTrue(outcome is ParseEntryUseCase.Outcome.Success)
        val result = (outcome as ParseEntryUseCase.Outcome.Success).result
        assertTrue(result.recordable)
        assertEquals(30.0, result.amount!!, 0.001)
    }

    @Test
    fun `returns non recordable message for chitchat`() = runTest {
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                AiChatResult.Content("""{"recordable": false, "message": "你好呀！"}""")
        }
        val outcome = useCase(chat)("你好")
        assertTrue(outcome is ParseEntryUseCase.Outcome.Success)
        val result = (outcome as ParseEntryUseCase.Outcome.Success).result
        assertFalse(result.recordable)
        assertEquals("你好呀！", result.message)
    }

    @Test
    fun `retries once on malformed json then returns Error`() = runTest {
        var calls = 0
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                calls++
                return AiChatResult.Content("this is not json")
            }
        }
        val outcome = useCase(chat)("昨天打车花了30块")
        assertEquals(2, calls)
        assertTrue(outcome is ParseEntryUseCase.Outcome.Error)
    }

    @Test
    fun `retries once on network failure then returns Error`() = runTest {
        var calls = 0
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                calls++
                return AiChatResult.Failure(FailureReason.NETWORK)
            }
        }
        val outcome = useCase(chat)("昨天打车花了30块")
        assertEquals(2, calls)
        assertTrue(outcome is ParseEntryUseCase.Outcome.Error)
    }

    @Test
    fun `does not retry on invalid key`() = runTest {
        var calls = 0
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                calls++
                return AiChatResult.Failure(FailureReason.KEY_INVALID)
            }
        }
        val outcome = useCase(chat)("昨天打车花了30块")
        assertEquals(1, calls)
        assertTrue(outcome is ParseEntryUseCase.Outcome.Error)
    }

    @Test
    fun `retries when parsed date is in the future`() = runTest {
        var calls = 0
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                calls++
                val date = if (calls == 1) "2026-08-16" else "2026-08-14"
                return AiChatResult.Content(
                    """{"recordable": true, "type": "expense", "amount_raw": "30", "amount": 30.0, "category": "交通", "date": "$date"}"""
                )
            }
        }
        val outcome = useCase(chat)("昨天打车花了30块")
        assertEquals(2, calls)
        assertTrue(outcome is ParseEntryUseCase.Outcome.Success)
        assertEquals(
            LocalDate.of(2026, 8, 14),
            (outcome as ParseEntryUseCase.Outcome.Success).result.date,
        )
    }

    @Test
    fun `isOcrInput=true appends ocr context to system prompt`() = runTest {
        var capturedPrompt = ""
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                capturedPrompt = systemPrompt
                return AiChatResult.Content("""{"recordable": false, "message": "ok"}""")
            }
        }
        useCase(chat)("交易金额\n¥38.50\n收款方\n滴滴出行", isOcrInput = true)
        assertTrue(capturedPrompt.contains("OCR 文字"))
    }

    @Test
    fun `isOcrInput=false does not append ocr context to system prompt`() = runTest {
        var capturedPrompt = ""
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                capturedPrompt = systemPrompt
                return AiChatResult.Content("""{"recordable": false, "message": "ok"}""")
            }
        }
        useCase(chat)("昨天打车花了30块")
        assertFalse(capturedPrompt.contains("OCR 文字"))
    }
}
