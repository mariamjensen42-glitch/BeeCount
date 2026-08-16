package com.cycling.beecount.notification

import android.content.Intent

/**
 * 自动记账确认/失败通知的深链（ADR 0014）：
 * - 确认提醒 → [showPendingDrafts] + [draftId]：打开助手页并展示该草稿的确认卡片
 *   （队列可能有多张，用草稿 id 定位，避免落到别的卡片）；
 * - 失败提醒 → [retryText]：打开助手页并预填支付通知原文，一键重试。
 * MainActivity 从 intent extras 解析（onCreate / onNewIntent），再交给 Compose 导航。
 */
data class AutoEntryDeepLink(
    val showPendingDrafts: Boolean,
    val draftId: Long?,
    val retryText: String?,
) {

    companion object {
        const val EXTRA_SHOW_PENDING = "auto_entry_show_pending"
        const val EXTRA_DRAFT_ID = "auto_entry_draft_id"
        const val EXTRA_RETRY_TEXT = "auto_entry_retry_text"

        fun from(intent: Intent?): AutoEntryDeepLink? {
            if (intent == null) return null
            val show = intent.getBooleanExtra(EXTRA_SHOW_PENDING, false)
            val draftId = if (intent.hasExtra(EXTRA_DRAFT_ID)) intent.getLongExtra(EXTRA_DRAFT_ID, -1L) else null
            val retry = intent.getStringExtra(EXTRA_RETRY_TEXT)
            return if (show || draftId != null || !retry.isNullOrBlank()) {
                AutoEntryDeepLink(show, draftId, retry)
            } else {
                null
            }
        }
    }
}
