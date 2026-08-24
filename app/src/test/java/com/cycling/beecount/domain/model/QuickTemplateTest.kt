package com.cycling.beecount.domain.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickTemplateTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes and deserializes a quick template`() {
        val template = QuickTemplate(
            id = 3L,
            title = "早餐",
            categoryName = "餐饮",
            amount = 5.0,
            type = EntryType.EXPENSE,
            amountRaw = "5元",
            note = "豆浆油条",
            tags = listOf("日常"),
        )
        val encoded = json.encodeToString(ListSerializer(QuickTemplate.serializer()), listOf(template))
        val decoded = json.decodeFromString(ListSerializer(QuickTemplate.serializer()), encoded)
        assertEquals(listOf(template), decoded)
    }

    @Test
    fun `defaults to expense with empty tags`() {
        val template = QuickTemplate(id = 1L, title = "地铁", categoryName = "交通", amount = 4.0)
        assertEquals(EntryType.EXPENSE, template.type)
        assertEquals(emptyList<String>(), template.tags)
        assertEquals("", template.amountRaw)
        assertEquals("", template.note)
    }
}
