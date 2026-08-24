package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.WeChatBill
import com.cycling.beecount.domain.model.WeChatBillRow
import com.cycling.beecount.domain.model.WeChatImportDraft
import com.cycling.beecount.domain.model.WeChatImportDraftEntry
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import timber.log.Timber

/**
 * 微信账单行的分类器（ADR 0012）：把原始流水行翻译成可入库的账目草稿。
 *
 * 规则（顺序敏感）：
 * 1. 退款交易（状态或类型含"退款"）→ 退款记录（红字冲销），类别按关键词映射到支出类别，
 *    统计时从对应类别/日期支出中扣减；
 * 2. 中性交易（充值/提现/理财通购买/零钱通存取/信用卡还款）→ 跳过并计数；
 * 3. 其余按「收/支」列定为支出/收入，类别由 [keywordCategory] 关键词映射决定（匹配
 *    交易类型 + 交易对方 + 商品，红包行由类型命中），兜底「其他」。
 *
 * 备注 = `HH:mm · 交易对方 · 商品`，跳过 "/" 占位与空段，商品原文不裁剪。
 */
class WeChatBillClassifier @Inject constructor() {

    fun classify(bill: WeChatBill): WeChatImportDraft {
        Timber.d("开始分类微信账单行，共 %d 行", bill.rows.size)
        val entries = mutableListOf<WeChatImportDraftEntry>()
        var skipped = 0
        bill.rows.forEach { row ->
            when (val decision = decide(row)) {
                is Decision.Skip -> {
                    skipped++
                    Timber.d("跳过账单行：time=%s，type=%s，incomeExpense=%s，sourceRef=%s",
                        row.time, row.type, row.incomeExpense, row.sourceRef)
                }
                is Decision.Import -> {
                    entries += decision.toDraftEntry()
                    Timber.d("导入账单行：time=%s，type=%s，category=%s，amount=%s，sourceRef=%s",
                        row.time, decision.type, decision.categoryName, row.amount, row.sourceRef)
                }
            }
        }
        Timber.i("微信账单分类完成：共 %d 行、导入 %d 条、跳过 %d 条",
            bill.rows.size, entries.size, skipped)
        return WeChatImportDraft(entries = entries, skippedCount = skipped)
    }

    private fun decide(row: WeChatBillRow): Decision {
        if (row.sourceRef.isBlank()) return Decision.Skip // 无交易单号无法去重/撤销，跳过
        if (row.status.contains("退款") || row.type.contains("退款")) {
            return Decision.Import(
                type = EntryType.REFUND,
                categoryName = keywordCategory(row, EXPENSE_RULES, "其他"),
                row = row,
            )
        }
        if (row.type in NEUTRAL_TRANSACTION_TYPES || row.incomeExpense == "中性" || row.incomeExpense.isBlank()) {
            return Decision.Skip
        }
        return when (row.incomeExpense) {
            "支出" -> Decision.Import(
                type = EntryType.EXPENSE,
                categoryName = keywordCategory(row, EXPENSE_RULES, "其他"),
                row = row,
            )
            "收入" -> Decision.Import(
                type = EntryType.INCOME,
                categoryName = keywordCategory(row, INCOME_RULES, "其他"),
                row = row,
            )
            else -> Decision.Skip
        }
    }

    /** 关键词映射：匹配「交易类型 + 交易对方 + 商品」，命中第一条规则即返回类别，未命中回退 [fallback] */
    private fun keywordCategory(row: WeChatBillRow, rules: List<CategoryRule>, fallback: String): String {
        val haystack = "${row.type} ${row.counterparty} ${row.goods}"
        return rules.firstOrNull { rule -> rule.keywords.any { haystack.contains(it) } }
            ?.category
            ?: fallback
    }

    private fun Decision.Import.toDraftEntry(): WeChatImportDraftEntry = WeChatImportDraftEntry(
        type = type,
        amount = row.amount,
        amountRaw = row.amountRaw,
        categoryName = categoryName,
        date = row.time.toLocalDate(),
        note = buildNote(row),
        sourceRef = row.sourceRef,
        counterparty = row.counterparty.takeUnless { it.isBlank() || it == "/" },
    )

    /** 备注 = `HH:mm · 交易对方 · 商品`，跳过 "/" 占位与空段 */
    private fun buildNote(row: WeChatBillRow): String = listOf(
        row.time.toLocalTime().format(TIME_FORMAT),
        row.counterparty.takeUnless { it.isBlank() || it == "/" },
        row.goods.takeUnless { it.isBlank() || it == "/" },
    ).filterNotNull().joinToString(" · ")

    private sealed interface Decision {
        data object Skip : Decision

        data class Import(
            val type: EntryType,
            val categoryName: String,
            val row: WeChatBillRow,
        ) : Decision
    }

    private data class CategoryRule(val keywords: List<String>, val category: String)

    private companion object {
        /** 中性记录的保留类别名，不参与分类排行（ADR 0012） */
        const val NEUTRAL_CATEGORY = "中性"

        /** 微信账单中的中性交易类型：自账户间移动，不导入 */
        val NEUTRAL_TRANSACTION_TYPES = setOf(
            "充值", "提现", "理财通购买", "零钱通存取", "信用卡还款",
        )

        val EXPENSE_RULES = listOf(
            CategoryRule(
                keywords = listOf("茶餐厅", "餐厅", "烧腊", "汤粉", "猪脚饭", "私房菜", "茶饮", "奶茶",
                    "快餐", "外卖", "家常菜", "火锅", "烧烤", "饺子", "汉堡", "咖啡", "早餐", "午餐", "晚餐"),
                category = "餐饮",
            ),
            CategoryRule(
                keywords = listOf("地铁", "乘车", "打车", "滴滴", "出租", "公交", "高铁",
                    "火车", "机票", "停车", "加油", "高速", "充电"),
                category = "交通",
            ),
            CategoryRule(
                keywords = listOf("顺丰", "速运", "快递", "物流", "运费"),
                category = "快递物流",
            ),
            CategoryRule(
                keywords = listOf("超市", "便利店", "画材", "文具", "百货", "商城", "淘宝", "天猫",
                    "京东", "拼多多", "购物", "零食", "水果"),
                category = "购物",
            ),
            CategoryRule(
                keywords = listOf("房租", "物业", "水电", "燃气", "宽带", "维修", "家具", "家电"),
                category = "居住",
            ),
            CategoryRule(
                keywords = listOf("电影", "KTV", "游戏", "视频", "会员", "音乐", "演出", "门票", "网吧"),
                category = "娱乐",
            ),
            CategoryRule(
                keywords = listOf("医院", "药", "诊所", "挂号", "体检"),
                category = "医疗",
            ),
            CategoryRule(
                keywords = listOf("学费", "课程", "培训", "辅导"),
                category = "教育",
            ),
            CategoryRule(
                keywords = listOf("随礼", "份子", "红包"),
                category = "人情",
            ),
        )

        val INCOME_RULES = listOf(
            CategoryRule(keywords = listOf("红包", "微信红包"), category = "红包"),
        )

        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
