package com.cycling.beecount.domain.usecase

import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微信账单 xlsx 解析器测试：用合成的最小 OOXML（sharedStrings + sheet1）验证
 * 行解析、列头校验、空行跳过与原始金额保真。
 */
class WeChatBillXlsxParserTest {

    private val parser = WeChatBillXlsxParser()

    @Test
    fun `解析出共享字符串单元格与数字单元格`() {
        val rows = listOf(
            HEADER_ROW,
            listOf("2026-04-28 18:58:48", "商户消费", "美团", "喜莹港茶餐厅（东涌店）", "支出", "14.23", "零钱通", "支付成功", "4200003084202604286514178878", "20260428185840", "/"),
            listOf("2026-04-17 19:58:20", "转账", "陈美丽", "转账备注:快递费", "收入", "13", "/", "已转入零钱通", "1000050001202604170235123486373", "/", "/"),
        )
        val bill = parser.parse(ByteArrayInputStream(buildXlsx(rows)))

        assertEquals(2, bill.rows.size)
        val first = bill.rows[0]
        assertEquals(LocalDateTime.of(2026, 4, 28, 18, 58, 48), first.time)
        assertEquals("商户消费", first.type)
        assertEquals("美团", first.counterparty)
        assertEquals("喜莹港茶餐厅（东涌店）", first.goods)
        assertEquals("支出", first.incomeExpense)
        assertEquals(14.23, first.amount, 0.001)
        assertEquals("14.23", first.amountRaw)
        assertEquals("支付成功", first.status)
        assertEquals("4200003084202604286514178878", first.sourceRef)
        assertEquals("陈美丽", bill.rows[1].counterparty)
        assertEquals("收入", bill.rows[1].incomeExpense)
        assertEquals(13.0, bill.rows[1].amount, 0.001)
        assertEquals("13", bill.rows[1].amountRaw) // 整数金额保真，不变成 13.0
    }

    @Test
    fun `跳过完全空白的行`() {
        val rows = listOf(
            HEADER_ROW,
            listOf("2026-04-01 08:00:00", "商户消费", "地铁", "乘车费用", "支出", "8", "零钱", "支付成功", "R1", "", ""),
            emptyList(),
            listOf("2026-04-02 09:00:00", "商户消费", "地铁", "乘车费用", "支出", "8", "零钱", "支付成功", "R2", "", ""),
        )
        val bill = parser.parse(ByteArrayInputStream(buildXlsx(rows)))
        assertEquals(2, bill.rows.size)
        assertEquals("R1", bill.rows[0].sourceRef)
        assertEquals("R2", bill.rows[1].sourceRef)
    }

    @Test
    fun `列头不匹配时抛无法识别的账单格式`() {
        val rows = listOf(
            listOf("时间", "类型", "对方", "商品", "收支", "金额", "方式", "状态", "单号", "商户", "备注"),
            listOf("2026-04-01 08:00:00", "商户消费", "地铁", "乘车费用", "支出", "8", "零钱", "支付成功", "R1", "", ""),
        )
        val e = assertThrows(WeChatBillParseException::class.java) { parser.parse(ByteArrayInputStream(buildXlsx(rows))) }
        assertEquals("无法识别的账单格式", e.message)
    }

    @Test
    fun `金额无法解析时抛无法识别的账单金额`() {
        val rows = listOf(
            HEADER_ROW,
            listOf("2026-04-01 08:00:00", "商户消费", "地铁", "乘车费用", "支出", "abc", "零钱", "支付成功", "R1", "", ""),
        )
        val e = assertThrows(WeChatBillParseException::class.java) { parser.parse(ByteArrayInputStream(buildXlsx(rows))) }
        assertTrue(e.message!!.contains("无法识别的账单金额"))
    }

    @Test
    fun `时间无法解析时抛无法识别的交易时间`() {
        val rows = listOf(
            HEADER_ROW,
            listOf("2026/04/01", "商户消费", "地铁", "乘车费用", "支出", "8", "零钱", "支付成功", "R1", "", ""),
        )
        val e = assertThrows(WeChatBillParseException::class.java) { parser.parse(ByteArrayInputStream(buildXlsx(rows))) }
        assertTrue(e.message!!.contains("无法识别的交易时间"))
    }

    @Test
    fun `缺少工作表时抛无法识别的账单格式`() {
        assertThrows(WeChatBillParseException::class.java) { parser.parse(ByteArrayInputStream(buildEmptyZip())) }
    }

    /** 构造最小 xlsx：共享字符串表 + 单张工作表。F 列（金额）存数字单元格，其余列存共享字符串。 */
    private fun buildXlsx(rows: List<List<String>>): ByteArray {
        val shared = mutableListOf<String>()
        val index = mutableMapOf<String, Int>()
        // 第一遍：收集所有非金额列文本为共享字符串
        rows.forEach { row ->
            row.forEachIndexed { columnIndex, value ->
                if (value.isNotEmpty() && columnIndex != AMOUNT_COLUMN && value !in index) {
                    index[value] = shared.size
                    shared += value
                }
            }
        }
        val sharedXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"${shared.size}\" uniqueCount=\"${shared.size}\">")
            shared.forEach { text -> append("<si><t>").append(xmlEscape(text)).append("</t></si>") }
            append("</sst>")
        }
        val sheetXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
            rows.forEachIndexed { rowIndex, row ->
                val rowNumber = rowIndex + 1
                append("<row r=\"$rowNumber\">")
                row.forEachIndexed { columnIndex, value ->
                    if (value.isEmpty()) return@forEachIndexed
                    val ref = "${COLUMN_LETTERS[columnIndex]}$rowNumber"
                    if (columnIndex == AMOUNT_COLUMN) {
                        append("<c r=\"$ref\"><v>$value</v></c>")
                    } else {
                        append("<c r=\"$ref\" t=\"s\"><v>${index[value]}</v></c>")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        return buildZip(sharedXml, sheetXml)
    }

    private fun buildEmptyZip(): ByteArray = buildZip("", "")

    private fun buildZip(sharedXml: String, sheetXml: String): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(sharedXml.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray())
            zip.closeEntry()
        }
        return buffer.toByteArray()
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private companion object {
        const val AMOUNT_COLUMN = 5 // F 列
        val COLUMN_LETTERS = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")
        val HEADER_ROW = listOf(
            "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
            "支付方式", "当前状态", "交易单号", "商户单号", "备注",
        )
    }
}
