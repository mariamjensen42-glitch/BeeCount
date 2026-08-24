package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.WeChatBill
import com.cycling.beecount.domain.model.WeChatBillRow
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 微信账单分类器测试（ADR 0012）：退款→REFUND、中性交易→跳过、
 * 收支分流、关键词映射兜底「其他」、备注组装。
 */
class WeChatBillClassifierTest {

    private val classifier = WeChatBillClassifier()

    @Test
    fun `商户消费按商品关键词映射为餐饮`() {
        val draft = classify(row(
            type = "商户消费",
            counterparty = "美团",
            goods = "喜莹港茶餐厅（东涌店）-美团App-260428112007000013",
            incomeExpense = "支出",
            amount = "14.23",
            status = "支付成功",
            sourceRef = "T1",
        ))
        val entry = draft.entries.single()
        assertEquals(EntryType.EXPENSE, entry.type)
        assertEquals("餐饮", entry.categoryName)
        assertEquals("18:58 · 美团 · 喜莹港茶餐厅（东涌店）-美团App-260428112007000013", entry.note)
        assertEquals("14.23", entry.amountRaw)
    }

    @Test
    fun `顺丰运费映射为快递物流`() {
        val draft = classify(row(
            goods = "散单运费-顺丰速运",
            incomeExpense = "支出",
            status = "支付成功",
            sourceRef = "T2",
        ))
        assertEquals("快递物流", draft.entries.single().categoryName)
    }

    @Test
    fun `地铁映射为交通，货拉拉映射为其他`() {
        val metro = classify(row(counterparty = "广州地铁城市通乘车", goods = "2026-04-09 17:08乘车费用", incomeExpense = "支出", status = "支付成功", sourceRef = "T3"))
        val huolala = classify(row(goods = "货拉拉费用", incomeExpense = "支出", status = "支付成功", sourceRef = "T4"))
        // "货拉拉车费后置支付"不因含"车费"二字误入交通（车费关键词已从交通规则移除）
        val huolalaRide = classify(row(goods = "货拉拉车费后置支付", incomeExpense = "支出", status = "支付成功", sourceRef = "T4b"))
        assertEquals("交通", metro.entries.single().categoryName)
        assertEquals("其他", huolala.entries.single().categoryName)
        assertEquals("其他", huolalaRide.entries.single().categoryName)
    }

    @Test
    fun `未知商户落到其他`() {
        val draft = classify(row(goods = "某某咨询服务费", incomeExpense = "支出", status = "支付成功", sourceRef = "T5"))
        assertEquals("其他", draft.entries.single().categoryName)
    }

    @Test
    fun `微信红包收入映射为红包类别`() {
        val draft = classify(row(
            type = "微信红包",
            counterparty = "罗工",
            goods = "/",
            incomeExpense = "收入",
            status = "已存入零钱",
            sourceRef = "T6",
        ))
        val entry = draft.entries.single()
        assertEquals(EntryType.INCOME, entry.type)
        assertEquals("红包", entry.categoryName)
        assertEquals("18:58 · 罗工", entry.note) // "/" 占位被跳过
    }

    @Test
    fun `转账收入映射为其他`() {
        val draft = classify(row(
            type = "转账",
            counterparty = "汪总  汪付松（老板）",
            goods = "转账备注:微信转账",
            incomeExpense = "收入",
            status = "对方已收钱",
            sourceRef = "T7",
        ))
        val entry = draft.entries.single()
        assertEquals(EntryType.INCOME, entry.type)
        assertEquals("其他", entry.categoryName)
    }

    @Test
    fun `已全额退款的消费记为退款`() {
        val draft = classify(row(
            type = "商户消费",
            counterparty = "货拉拉",
            goods = "货拉拉费用",
            incomeExpense = "支出",
            amount = "483.46",
            status = "已全额退款",
            sourceRef = "T8",
        ))
        val entry = draft.entries.single()
        assertEquals(EntryType.REFUND, entry.type)
        assertEquals("其他", entry.categoryName)
        assertEquals(483.46, entry.amount, 0.001)
    }

    @Test
    fun `退款收入行记为退款`() {
        val draft = classify(row(
            type = "货拉拉-退款",
            counterparty = "货拉拉",
            goods = "货拉拉",
            incomeExpense = "收入",
            amount = "420.4",
            status = "已全额退款",
            sourceRef = "T9",
        ))
        val entry = draft.entries.single()
        assertEquals(EntryType.REFUND, entry.type)
        assertEquals("其他", entry.categoryName)
    }

    @Test
    fun `转账-退款记为退款而非收入`() {
        val draft = classify(row(
            type = "转账-退款",
            counterparty = "/",
            goods = "转账备注:微信转账",
            incomeExpense = "收入",
            amount = "56",
            status = "已全额退款",
            sourceRef = "T10",
        ))
        val entry = draft.entries.single()
        assertEquals(EntryType.REFUND, entry.type)
    }

    @Test
    fun `充值提现零钱通存取等中性交易跳过并计数`() {
        val draft = classifier.classify(
            WeChatBill(
                rows = listOf(
                    row(type = "充值", incomeExpense = "中性", status = "已存入零钱", sourceRef = "N1"),
                    row(type = "提现", incomeExpense = "中性", status = "已存入银行", sourceRef = "N2"),
                    row(type = "零钱通存取", incomeExpense = "", status = "支付成功", sourceRef = "N3"),
                    row(type = "信用卡还款", incomeExpense = "中性", status = "还款成功", sourceRef = "N4"),
                ),
            ),
        )
        assertEquals(0, draft.entries.size)
        assertEquals(4, draft.skippedCount)
    }

    @Test
    fun `无交易单号的行跳过`() {
        val draft = classify(row(
            goods = "货拉拉费用",
            incomeExpense = "支出",
            status = "支付成功",
            sourceRef = "",
        ))
        assertEquals(0, draft.entries.size)
        assertEquals(1, draft.skippedCount)
    }

    private fun classify(row: WeChatBillRow): com.cycling.beecount.domain.model.WeChatImportDraft =
        classifier.classify(WeChatBill(rows = listOf(row)))

    private fun row(
        time: LocalDateTime = LocalDateTime.of(2026, 4, 28, 18, 58, 48),
        type: String = "商户消费",
        counterparty: String = "美团",
        goods: String = "喜莹港茶餐厅（东涌店）",
        incomeExpense: String = "支出",
        amount: String = "14.23",
        status: String = "支付成功",
        sourceRef: String = "T-DEFAULT",
    ) = WeChatBillRow(
        time = time,
        type = type,
        counterparty = counterparty,
        goods = goods,
        incomeExpense = incomeExpense,
        amountRaw = amount,
        amount = amount.toDouble(),
        status = status,
        sourceRef = sourceRef,
    )
}
