package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.AiParseResult

/**
 * 自动记账提醒出口（ADR 0014）：解析成功发确认提醒（深链到确认卡片）、
 * 解析失败发低姿态提示（预填原文一键重试）。Android 实现在 notification 包。
 */
interface AutoEntryNotifier {

    /**
     * 解析成功：通知用户有一笔待确认草稿。
     * [draftId] 随通知深链携带，点通知落到该草稿对应的确认卡片（队列可能有多张）。
     */
    fun notifyDraftReady(packageName: String, draftId: Long, result: AiParseResult)

    /** 解析失败：低姿态提示，点开预填原文 */
    fun notifyParseFailed(originalText: String)
}
