package com.cycling.beecount.domain.usecase

/**
 * 通知闸门（ADR 0014）：通知进入解析前的离线确定性过滤。
 *
 * 条件：包名在白名单（微信/支付宝）+ 通知文本同时含金额模式与支付关键词。
 * 闸门不过的静默丢弃，不进解析也不打扰用户；解析失败的低姿态提示只针对闸门过了的情况。
 * 纯逻辑、无 Android 依赖，可单测。
 */
class NotificationGate(
    private val allowedPackages: Set<String> = DEFAULT_PACKAGES,
) {

    fun shouldProcess(packageName: String, title: String, text: String): Boolean {
        if (packageName !in allowedPackages) return false
        val fullText = listOf(title, text).filter { it.isNotBlank() }.joinToString(" ")
        return AMOUNT_REGEX.containsMatchIn(fullText) && KEYWORD_REGEX.containsMatchIn(fullText)
    }

    companion object {
        /** v1 只监听微信与支付宝（ADR 0014）：银行会与它们对同一笔交易重复发通知且跨包名无法去重 */
        val DEFAULT_PACKAGES: Set<String> = setOf(
            "com.tencent.mm",            // 微信
            "com.eg.android.AlipayGphone", // 支付宝
        )

        /** 金额模式：`15.00元` / `¥38.5` / `20块` 等，兼容币种符号前缀与后缀 */
        private val AMOUNT_REGEX = Regex("""(?:[¥￥]\s*\d+(?:\.\d+)?|\d+(?:\.\d+)?\s*(?:元|块))""")

        /** 支付关键词：支出（支付/付款/消费/扣款/已付）与收入（到账/收款/红包/转账/已收）两侧都覆盖 */
        private val KEYWORD_REGEX = Regex("支付|付款|消费|扣款|到账|收款|红包|转账|已付|已收")
    }
}
