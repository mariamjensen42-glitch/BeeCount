package com.cycling.beecount.domain.ai

import com.cycling.beecount.domain.model.EntryType
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEntryJsonDecoderTest {

    private val decoder = AiEntryJsonDecoder(Json { ignoreUnknownKeys = true })

    @Test
    fun `decodes valid expense json`() {
        val result = decoder.decode(
            """
            {"recordable": true, "type": "expense", "amount_raw": "30块", "amount": 30.0, "category": "交通", "date": "2026-08-14"}
            """.trimIndent()
        )

        assertTrue(result!!.recordable)
        assertEquals(EntryType.EXPENSE, result.type)
        assertEquals(30.0, result.amount!!, 0.001)
        assertEquals("30块", result.amountRaw)
        assertEquals("交通", result.categoryName)
        assertEquals(LocalDate.of(2026, 8, 14), result.date)
    }

    @Test
    fun `decodes valid income json`() {
        val result = decoder.decode(
            """
            {"recordable": true, "type": "income", "amount_raw": "500", "amount": 500.0, "category": "红包", "date": "2026-08-15"}
            """.trimIndent()
        )

        assertTrue(result!!.recordable)
        assertEquals(EntryType.INCOME, result.type)
    }

    @Test
    fun `decodes non recordable input with message`() {
        val result = decoder.decode(
            """{"recordable": false, "message": "你好呀！告诉我一笔收支就能帮你记账"}"""
        )

        assertFalse(result!!.recordable)
        assertEquals("你好呀！告诉我一笔收支就能帮你记账", result.message)
        assertNull(result.type)
    }

    @Test
    fun `rejects invalid type`() {
        val result = decoder.decode(
            """{"recordable": true, "type": "transfer", "amount_raw": "30", "amount": 30.0, "category": "交通", "date": "2026-08-14"}"""
        )
        assertNull(result)
    }

    @Test
    fun `rejects zero or negative amount`() {
        val negative = decoder.decode(
            """{"recordable": true, "type": "expense", "amount_raw": "-30", "amount": -30.0, "category": "交通", "date": "2026-08-14"}"""
        )
        val zero = decoder.decode(
            """{"recordable": true, "type": "expense", "amount_raw": "0", "amount": 0.0, "category": "交通", "date": "2026-08-14"}"""
        )
        assertNull(negative)
        assertNull(zero)
    }

    @Test
    fun `rejects malformed date`() {
        val result = decoder.decode(
            """{"recordable": true, "type": "expense", "amount_raw": "30", "amount": 30.0, "category": "交通", "date": "2026-13-45"}"""
        )
        assertNull(result)
    }

    @Test
    fun `rejects missing required fields`() {
        val noCategory = decoder.decode(
            """{"recordable": true, "type": "expense", "amount_raw": "30", "amount": 30.0, "date": "2026-08-14"}"""
        )
        val noAmountRaw = decoder.decode(
            """{"recordable": true, "type": "expense", "amount": 30.0, "category": "交通", "date": "2026-08-14"}"""
        )
        assertNull(noCategory)
        assertNull(noAmountRaw)
    }

    @Test
    fun `rejects non json text`() {
        assertNull(decoder.decode("not json at all"))
        assertNull(decoder.decode(""))
    }

    @Test
    fun `accepts amount with decimal and chinese unit converted by model`() {
        val result = decoder.decode(
            """{"recordable": true, "type": "expense", "amount_raw": "1万", "amount": 10000.0, "category": "购物", "date": "2026-08-14"}"""
        )
        assertTrue(result!!.recordable)
        assertEquals(10000.0, result.amount!!, 0.001)
    }

    @Test
    fun `decodes tags normalized to at most 3 distinct non blank`() {
        val result = decoder.decode(
            """{"recordable": true, "type": "expense", "amount_raw": "200", "amount": 200.0, "category": "购物", "date": "2026-08-14", "tags": [" 宠物 ", "宠物", "出差", "", "健身"]}"""
        )
        assertEquals(listOf("宠物", "出差", "健身"), result!!.tags)
    }

    @Test
    fun `decodes missing tags as empty list`() {
        val result = decoder.decode(
            """{"recordable": true, "type": "expense", "amount_raw": "30", "amount": 30.0, "category": "交通", "date": "2026-08-14"}"""
        )
        assertTrue(result!!.tags.isEmpty())
    }
}
