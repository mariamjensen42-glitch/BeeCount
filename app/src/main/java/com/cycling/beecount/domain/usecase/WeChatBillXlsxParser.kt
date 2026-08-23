package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.WeChatBill
import com.cycling.beecount.domain.model.WeChatBillRow
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import timber.log.Timber

/**
 * 微信支付账单 xlsx 的最小解析器（ADR 0012）。
 *
 * xlsx 是 zip 包装的 OOXML：`xl/sharedStrings.xml` 存共享字符串表，`xl/worksheets/sheet1.xml`
 * 存单元格（字符串单元格 `t="s"` 引用共享表索引，数字单元格内联文本）。本解析器用
 * [DocumentBuilderFactory]（DOM）读这两个文件，按官方固定 11 列解析出行，零第三方依赖。
 *
 * 解析失败（缺文件、列头对不上、时间/金额无法解析）统一抛 [WeChatBillParseException]，
 * 由调用方呈现为"无法识别的账单格式"的优雅错误，不会崩溃。
 */
class WeChatBillXlsxParser @Inject constructor() {

    fun parse(input: InputStream): WeChatBill {
        Timber.d("开始解析微信账单 xlsx")
        val sharedStrings = mutableListOf<String>()
        var sheetDocument: Document? = null
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    // 先把条目读入内存再交给 DOM 解析：DOM 会关闭输入流，
                    // 直接传 zip 条目流会让后续 closeEntry 抛 "Stream closed"
                    "xl/sharedStrings.xml" -> sharedStrings += parseSharedStrings(zip.readBytes().inputStream())
                    "xl/worksheets/sheet1.xml" -> sheetDocument = parseSheet(zip.readBytes().inputStream())
                    // 其余部件（样式、主题、元数据等）不需要
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val sheet = sheetDocument ?: run {
            Timber.w("xlsx 中未找到 sheet1.xml，无法识别账单格式")
            throw WeChatBillParseException("无法识别的账单格式")
        }
        Timber.d("xlsx 解析完成：sharedStrings=%d 个", sharedStrings.size)
        return WeChatBill(rows = parseRows(sheet, sharedStrings))
    }

    /** 解析共享字符串表：每个 <si> 内的全部 <t> 文本拼接为一个字符串 */
    private fun parseSharedStrings(input: InputStream): List<String> {
        val document = newDocument(input)
        val siNodes = document.getElementsByTagName("si")
        return (0 until siNodes.length).map { i ->
            val si = siNodes.item(i) as Element
            val tNodes = si.getElementsByTagName("t")
            buildString {
                for (j in 0 until tNodes.length) append(tNodes.item(j).textContent)
            }
        }
    }

    private fun parseSheet(input: InputStream): Document = newDocument(input)

    /**
     * 从工作表解析流水行：先找到列头行（11 列与官方账单逐一匹配），列头之后的行为数据行。
     * 完全空白的行跳过。
     */
    private fun parseRows(sheet: Document, sharedStrings: List<String>): List<WeChatBillRow> {
        val rowElements = sheet.getElementsByTagName("row")
        var headerIndex = -1
        for (i in 0 until rowElements.length) {
            val row = rowElements.item(i) as Element
            if (isHeaderRow(row, sharedStrings)) {
                headerIndex = i
                break
            }
        }
        if (headerIndex < 0) {
            Timber.w("xlsx 中未找到匹配的 11 列头行")
            throw WeChatBillParseException("无法识别的账单格式")
        }
        Timber.d("找到列头行，index=%d，共 %d 行", headerIndex, rowElements.length)

        val rows = mutableListOf<WeChatBillRow>()
        for (i in headerIndex + 1 until rowElements.length) {
            val row = rowElements.item(i) as Element
            val cells = resolveCells(row, sharedStrings)
            val timeText = cells[COLUMN_TIME]?.trim().orEmpty()
            if (timeText.isEmpty()) continue // 空行
            val amountText = cells[COLUMN_AMOUNT]?.trim().orEmpty()
            val amount = amountText.toDoubleOrNull()
                ?: throw WeChatBillParseException("无法识别的账单金额：$amountText")
            val time = parseTime(timeText)
            rows += WeChatBillRow(
                time = time,
                type = cells[COLUMN_TYPE].orEmpty().trim(),
                counterparty = cells[COLUMN_COUNTERPARTY].orEmpty().trim(),
                goods = cells[COLUMN_GOODS].orEmpty().trim(),
                incomeExpense = cells[COLUMN_INCOME_EXPENSE].orEmpty().trim(),
                amountRaw = amountText,
                amount = amount,
                status = cells[COLUMN_STATUS].orEmpty().trim(),
                sourceRef = cells[COLUMN_SOURCE_REF].orEmpty().trim(),
            )
        }
        Timber.d("xlsx 数据行解析完成：%d 行", rows.size)
        if (rows.isEmpty()) Timber.w("xlsx 解析结果为 0 行")
        return rows
    }

    private fun isHeaderRow(row: Element, sharedStrings: List<String>): Boolean {
        val cells = resolveCells(row, sharedStrings)
        return HEADERS.indices.all { index ->
            cells[COLUMN_LETTERS[index]]?.trim() == HEADERS[index]
        }
    }

    /** 把一行的单元格解析为 列字母 → 单元格文本 的映射；缺失单元格为空 */
    private fun resolveCells(row: Element, sharedStrings: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val cells = row.getElementsByTagName("c")
        for (i in 0 until cells.length) {
            val cell = cells.item(i) as Element
            val ref = cell.getAttribute("r")
            val column = ref.takeWhile { it.isLetter() }
            if (column.isEmpty()) continue
            val type = cell.getAttribute("t")
            val value = when (type) {
                "s" -> cell.getElementsByTagName("v").item(0)?.textContent?.toIntOrNull()
                    ?.let { sharedStrings.getOrNull(it) }
                    .orEmpty()
                "str", "inlineStr" -> cell.getElementsByTagName("t").item(0)?.textContent.orEmpty()
                else -> cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
            }
            result[column] = value
        }
        return result
    }

    /**
     * 交易时间：微信账单存的是 Excel 序列日期（自 1899-12-30 起的天数，如 46140.7908333333 =
     * 2026-04-28 18:58:48），也有直接存格式化字符串的版本，两种都接受。
     */
    private fun parseTime(text: String): LocalDateTime {
        val formatted = runCatching { LocalDateTime.parse(text, TIME_FORMAT) }.getOrNull()
        if (formatted != null) return formatted
        val serial = text.toDoubleOrNull()
            ?: throw WeChatBillParseException("无法识别的交易时间：$text")
        val totalSeconds = Math.round(serial * SECONDS_PER_DAY)
        return EXCEL_EPOCH
            .plusDays(totalSeconds / SECONDS_PER_DAY)
            .plusSeconds(totalSeconds % SECONDS_PER_DAY)
    }

    private fun newDocument(input: InputStream): Document = try {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
    } catch (e: Exception) {
        // 文件不是合法 XML（或完全空文件）时统一按不可识别的账单处理
        Timber.w(e, "xlsx 内部 XML 解析失败：%s", e.message)
        throw WeChatBillParseException("无法识别的账单格式")
    }

    private companion object {
        /** 官方账单 11 列，顺序固定（第 18 行列头） */
        const val COLUMN_TIME = "A"          // 交易时间
        const val COLUMN_TYPE = "B"          // 交易类型
        const val COLUMN_COUNTERPARTY = "C"  // 交易对方
        const val COLUMN_GOODS = "D"         // 商品
        const val COLUMN_INCOME_EXPENSE = "E" // 收/支
        const val COLUMN_AMOUNT = "F"        // 金额(元)
        const val COLUMN_STATUS = "H"        // 当前状态
        const val COLUMN_SOURCE_REF = "I"    // 交易单号

        val COLUMN_LETTERS = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")
        val HEADERS = listOf(
            "交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)",
            "支付方式", "当前状态", "交易单号", "商户单号", "备注",
        )
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val EXCEL_EPOCH: LocalDateTime = LocalDateTime.of(1899, 12, 30, 0, 0)
        const val SECONDS_PER_DAY = 86_400L
    }
}

/** 微信账单解析失败：文件不是可识别的微信支付账单格式 */
class WeChatBillParseException(message: String) : Exception(message)
