package com.cycling.beecount.domain.usecase

import com.cycling.beecount.data.datasource.AiChatDataSource
import com.cycling.beecount.data.datasource.AiChatResult
import com.cycling.beecount.data.datasource.FailureReason
import com.cycling.beecount.domain.ai.AiEntryJsonDecoder
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
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

class EntryIntakeTest {

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
        override suspend fun createChild(parentId: Long, name: String): Long = 100L
        override suspend fun rename(id: Long, name: String) {}
        override suspend fun deleteWithMerge(id: Long, targetId: Long) {}
        override suspend fun moveParent(id: Long, parentId: Long?) {}
        override suspend fun updateIcon(id: Long, icon: String) {}
        override suspend fun updateColor(id: Long, color: Long) {}
        override suspend fun updateSortOrder(id: Long, sortOrder: Int) {}
        override suspend fun updateHidden(id: Long, isHidden: Boolean) {}
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

    private fun intake(
        repo: FakeEntryRepository = FakeEntryRepository(emptyList()),
        chat: AiChatDataSource,
        key: String? = "test-key",
    ) = EntryIntake(
        entryRepository = repo,
        categoryRepository = fakeCategoryRepository(),
        tagRepository = fakeTagRepository(),
        decoder = AiEntryJsonDecoder(Json { ignoreUnknownKeys = true }),
        aiChatDataSource = chat,
        aiKeyRepository = fakeKeyRepository(key),
        ocrTextRecognizer = OcrTextRecognizer { "" },
        currentDate = { today },
    )

    // ---------- parse ----------

    @Test
    fun `parse returns KeyMissing when no key configured`() = runTest {
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                error("should not be called")
        }
        val outcome = intake(chat = chat, key = null).parse("昨天打车花了30块")
        assertEquals(EntryIntake.Outcome.KeyMissing, outcome)
    }

    @Test
    fun `parse returns Success with parsed result on valid response`() = runTest {
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                AiChatResult.Content(
                    """{"recordable": true, "type": "expense", "amount_raw": "30块", "amount": 30.0, "category": "交通", "date": "2026-08-14"}"""
                )
        }
        val outcome = intake(chat = chat).parse("昨天打车花了30块")
        assertTrue(outcome is EntryIntake.Outcome.Success)
        val result = (outcome as EntryIntake.Outcome.Success).result
        assertTrue(result.recordable)
        assertEquals(30.0, result.amount!!, 0.001)
    }

    @Test
    fun `parse returns non recordable message for chitchat`() = runTest {
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                AiChatResult.Content("""{"recordable": false, "message": "你好呀！"}""")
        }
        val outcome = intake(chat = chat).parse("你好")
        assertTrue(outcome is EntryIntake.Outcome.Success)
        val result = (outcome as EntryIntake.Outcome.Success).result
        assertFalse(result.recordable)
        assertEquals("你好呀！", result.message)
    }

    @Test
    fun `parse retries once on malformed json then returns Error`() = runTest {
        var calls = 0
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                calls++
                return AiChatResult.Content("this is not json")
            }
        }
        val outcome = intake(chat = chat).parse("昨天打车花了30块")
        assertEquals(2, calls)
        assertTrue(outcome is EntryIntake.Outcome.Error)
    }

    @Test
    fun `parse retries once on network failure then returns Error`() = runTest {
        var calls = 0
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                calls++
                return AiChatResult.Failure(FailureReason.NETWORK)
            }
        }
        val outcome = intake(chat = chat).parse("昨天打车花了30块")
        assertEquals(2, calls)
        assertTrue(outcome is EntryIntake.Outcome.Error)
    }

    @Test
    fun `parse does not retry on invalid key`() = runTest {
        var calls = 0
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                calls++
                return AiChatResult.Failure(FailureReason.KEY_INVALID)
            }
        }
        val outcome = intake(chat = chat).parse("昨天打车花了30块")
        assertEquals(1, calls)
        assertTrue(outcome is EntryIntake.Outcome.Error)
    }

    @Test
    fun `parse retries when parsed date is in the future`() = runTest {
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
        val outcome = intake(chat = chat).parse("昨天打车花了30块")
        assertEquals(2, calls)
        assertTrue(outcome is EntryIntake.Outcome.Success)
        assertEquals(
            LocalDate.of(2026, 8, 14),
            (outcome as EntryIntake.Outcome.Success).result.date,
        )
    }

    @Test
    fun `parse with ocr input appends ocr context to system prompt`() = runTest {
        var capturedPrompt = ""
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                capturedPrompt = systemPrompt
                return AiChatResult.Content("""{"recordable": false, "message": "ok"}""")
            }
        }
        intake(chat = chat).parse("交易金额\n¥38.50\n收款方\n滴滴出行", isOcrInput = true)
        assertTrue(capturedPrompt.contains("OCR 文字"))
    }

    @Test
    fun `parse without ocr input does not append ocr context`() = runTest {
        var capturedPrompt = ""
        val chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult {
                capturedPrompt = systemPrompt
                return AiChatResult.Content("""{"recordable": false, "message": "ok"}""")
            }
        }
        intake(chat = chat).parse("昨天打车花了30块")
        assertFalse(capturedPrompt.contains("OCR 文字"))
    }

    // ---------- update ----------

    @Test
    fun `update preserves id amountRaw and createdAt`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                Entry(
                    id = 7,
                    type = EntryType.EXPENSE,
                    amount = 30.0,
                    amountRaw = "30",
                    categoryName = "交通",
                    date = today.minusDays(1),
                    note = "打车",
                    createdAt = 123L,
                    tags = listOf(Tag(id = 1, name = "旅行", color = 0xFF81C784, isCustom = false)),
                    sourceRef = null,
                )
            )
        )
        val i = intake(repo = repo, chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                error("not used")
        })

        val updated = i.update(
            entry = repo.storedEntries.single(),
            editedType = EntryType.INCOME,
            editedAmount = 50.0,
            editedCategoryName = "红包",
            editedDate = today,
            editedNote = "老板红包",
            tagNames = listOf("旅行", "出差"),
        )

        assertEquals(7, updated.id)
        assertEquals(EntryType.INCOME, updated.type)
        assertEquals(50.0, updated.amount, 0.001)
        assertEquals("红包", updated.categoryName)
        assertEquals(today, updated.date)
        assertEquals("老板红包", updated.note)
        assertEquals(123L, updated.createdAt)
        assertEquals("30", updated.amountRaw)
        assertEquals(2, repo.storedEntries.single().tags.size)
        assertEquals(2, repo.storedEntries.single().tags.map { it.name }.toSet().size)
    }

    @Test
    fun `update creates missing category`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                Entry(
                    id = 1,
                    type = EntryType.EXPENSE,
                    amount = 10.0,
                    amountRaw = "10",
                    categoryName = "餐饮",
                    date = today,
                    note = "午饭",
                )
            )
        )
        val i = intake(repo = repo, chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                error("not used")
        })

        val updated = i.update(
            entry = repo.storedEntries.single(),
            editedType = EntryType.EXPENSE,
            editedAmount = 20.0,
            editedCategoryName = "咖啡",
            editedDate = today,
            editedNote = "咖啡",
            tagNames = emptyList(),
        )

        assertEquals("咖啡", updated.categoryName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update rejects future date`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                Entry(
                    id = 1,
                    type = EntryType.EXPENSE,
                    amount = 10.0,
                    amountRaw = "10",
                    categoryName = "餐饮",
                    date = today,
                    note = "午饭",
                )
            )
        )
        val i = intake(repo = repo, chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                error("not used")
        })

        i.update(
            entry = repo.storedEntries.single(),
            editedType = EntryType.EXPENSE,
            editedAmount = 20.0,
            editedCategoryName = "餐饮",
            editedDate = today.plusDays(1),
            editedNote = "未来",
            tagNames = emptyList(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update rejects non-positive amount`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                Entry(
                    id = 1,
                    type = EntryType.EXPENSE,
                    amount = 10.0,
                    amountRaw = "10",
                    categoryName = "餐饮",
                    date = today,
                    note = "午饭",
                )
            )
        )
        val i = intake(repo = repo, chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                error("not used")
        })

        i.update(
            entry = repo.storedEntries.single(),
            editedType = EntryType.EXPENSE,
            editedAmount = 0.0,
            editedCategoryName = "餐饮",
            editedDate = today,
            editedNote = "零",
            tagNames = emptyList(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update rejects category name containing separator`() = runTest {
        val repo = FakeEntryRepository(
            listOf(
                Entry(
                    id = 1,
                    type = EntryType.EXPENSE,
                    amount = 10.0,
                    amountRaw = "10",
                    categoryName = "餐饮",
                    date = today,
                    note = "午饭",
                )
            )
        )
        val i = intake(repo = repo, chat = object : AiChatDataSource {
            override suspend fun complete(apiKey: String, systemPrompt: String, userPrompt: String): AiChatResult =
                error("not used")
        })

        i.update(
            entry = repo.storedEntries.single(),
            editedType = EntryType.EXPENSE,
            editedAmount = 20.0,
            editedCategoryName = "餐饮·外卖",
            editedDate = today,
            editedNote = "午饭",
            tagNames = emptyList(),
        )
    }
}
